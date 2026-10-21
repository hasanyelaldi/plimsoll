# CLAUDE.md — Plimsoll

> Every ship has a Plimsoll line. Your service graph should too.

Plimsoll finds where retry, timeout and circuit-breaker settings across a service graph go past safe limits. It computes this from real configuration (code, Spring YAML, Istio manifests), not from hand-written models. Later it will answer performance what-if questions on the same graph. Its reports are meant to be attached to ADRs as evidence.

Status: pre-v0.1, started 14 Oct 2026. License: Apache-2.0.

## Conventions

- Code, identifiers, comments, commit messages, docs and issues are in English.
- Keep each change small, runnable and tied to one issue.
- Before a change that touches more than 3 files or adds a dependency, propose a short plan and wait for approval.
- Stay inside the current milestone. New ideas become issues labelled `later` instead of code.
- Only contribute code you have the right to release under Apache-2.0.

## Non-negotiable principles

1. **The deterministic core computes; LLMs only propose.** Every number in a report (×27 amplification, a timeout budget) comes from code in `plimsoll-core`. No LLM calls in the core, the analyzers or the tests.
2. **Every value carries provenance.** `EXPLICIT` (from config, with `file:line`), `LIBRARY_DEFAULT` (library, version, doc URL), `INFERRED` (an LLM proposal, never used silently) or `UNKNOWN`. Never invent a library default: if the knowledge base has no sourced entry, the value is `UNKNOWN`.
3. **Library semantics are tested against the real library.** Before encoding how a library behaves (attempt counting, decorator order, timeouts), write an oracle test that runs the real library in test scope and asserts that behaviour.
4. **Findings explain themselves.** Each finding lists the values it used and where each one came from.
5. **Deterministic output.** Same input, byte-identical report. No timestamps or unordered collections in output; sort by severity, then rule id, then location.

## Stack and commands

Java 21, Maven multi-module build with the Maven Wrapper (`./mvnw`). All versions live as `<properties>` in the parent `pom.xml` and are managed in `<dependencyManagement>` / `<pluginManagement>`; child POMs never repeat a version.
Libraries: picocli, Jackson (YAML), JGraphT, JavaParser (collectors), JUnit 5, AssertJ, jqwik. Resilience4j is a **test-scope** dependency only, for oracle tests, with its version in the `resilience4j.version` property so CI can run a version matrix. Use the latest stable versions. Ask before adding anything else.

The CLI module builds a single executable jar (`maven-shade-plugin`, `Main-Class` set, `finalName` = `plimsoll`).

```bash
./mvnw verify                                   # compile + all tests (run before every commit)
./mvnw test                                     # tests only
./mvnw -pl plimsoll-core test -Dtest=AmplificationTest   # one test class
./mvnw -q -DskipTests package && java -jar plimsoll-cli/target/plimsoll.jar analyze examples/checkout.yaml
./mvnw test -Dresilience4j.version=<x.y.z>      # oracle tests against another Resilience4j version
```

CI (GitHub Actions): `./mvnw -B verify` on every push and pull request, on Java 21. Publishing to Maven Central (at the v0.1 release, not before) uses Sonatype's Central Portal with its Maven plugin.

## Layout

```
pom.xml           parent POM: modules, versions, plugin management
plimsoll-core/    model, normalization, engine (effective policy), analysis (rules), io (spec loading)
plimsoll-collectors/  read real config into the model: Spring Boot properties, Resilience4j (application.yml + annotations, edges-hint.yaml), HTTP clients (`client:` in edges-hint.yaml, Spring Cloud LoadBalancer retries), Istio (VirtualService, DestinationRule manifests), Spring Cloud Gateway (routes to `lb://` services and their `Retry` filters become edges); `GraphAssembler` merges them onto edges, layers outermost first (resilience4j, http-client, istio); `ServiceAssembler` builds the services (deadline, entry) per ADR 0009
plimsoll-cli/     picocli entry point: `plimsoll analyze <spec-or-dir>`
examples/         hand-written specs; checkout.yaml is the canonical demo and acceptance test
docs/adr/         decisions in MADR format (0001–0009 below)
kb/defaults/      library defaults knowledge base, one YAML file per library
```

Base package: `io.github.hasanyelaldi.plimsoll`. Collectors live in `plimsoll-collectors` (package `…plimsoll.collect`); its differential oracles bind the same fixture with real Spring Boot. With Istio manifests given, every callee is assumed to be in the mesh; a host with several HTTP routes keeps the fields they agree on, the rest is `UNKNOWN`. Maven coordinates: `io.github.hasanyelaldi:plimsoll-core`, `plimsoll-collectors`, `plimsoll-cli`.

## Domain model

```java
enum Source { EXPLICIT, LIBRARY_DEFAULT, INFERRED, UNKNOWN }
record Val<T>(T value, Source source, String evidence) {}   // evidence: file:line or doc URL

record RetryPolicy(Val<Integer> totalAttempts,              // always counts the first call
                   Val<Duration> perTryTimeout,
                   Val<Duration> initialBackoff, Val<Double> multiplier, Val<Duration> maxBackoff,
                   Val<Boolean> jitter, Val<Set<String>> retryOn) {}
record Layer(String kind, RetryPolicy retry, Val<Duration> overallTimeout) {}  // resilience4j | spring-cloud-gateway | istio | http-client | generic
record Edge(String from, String to, String operation, Val<Boolean> idempotent,
            Val<Integer> fanout, List<Layer> layers) {}                        // layers: outermost first
record Service(String name, Val<Duration> deadline, boolean entry) {}
record ServiceGraph(Map<String, Service> services, List<Edge> edges) {}
```

Normalize attempt counts at the boundary. This mismatch is the first thing the tool must get right:

| Source | Field | Meaning | `totalAttempts` |
| --- | --- | --- | --- |
| Resilience4j Retry | `maxAttempts` | includes the initial call (default 3, `waitDuration` 500 ms) | `maxAttempts` |
| Istio VirtualService | `retries.attempts` | retries only | `attempts + 1` |
| Spring Cloud Gateway `Retry` filter | `retries` | retries only (default 3, GET, 5XX, no backoff) | `retries + 1` |
| Spring Cloud LoadBalancer (`@LoadBalanced` WebClient) | `max-retries-on-same-service-instance`, `max-retries-on-next-service-instance` | retries only; off unless `retry.enabled: true` is set | `(same + 1) × (next + 1)` |
| Hand-written `generic` layer | `totalAttempts` | as named | value |

The Resilience4j row is checked by `RetryAttemptCountingOracleTest`; the Istio row against the v1.31 reference (`kb/defaults/istio.yaml`); the LoadBalancer row by `LoadBalancerRetryOracleTest` (exceptions are retried for every HTTP method, status codes for GET only). The Gateway row is read from the docs and the `RetryGatewayFilterFactory` source (5.0.0), with no oracle yet; whether a `CircuitBreaker` filter listed before `Retry` wraps all tries or each try stays `UNKNOWN` until one exists.

## Analyzer rules (v0.1)

Rule ids `PLM-A1` … `PLM-A5`. Severities: `ERROR`, `WARN`, `INFO`.

- **A1 Amplification.** Upper bound, assuming every try fails and failure propagates upward (fail-close). For each entry `s`, in topological order: `L(s) = 1`, `L(v) = Σ over edges u→v of L(u) × E(u→v) × fanout(u→v)`, where `E` is the product of `totalAttempts` across the edge's layers. Diamonds are handled by the sum. Report `L(v)` per service; `ERROR` if above the limit (default 10, `--max-amp`). A cycle reachable from an entry gives `ERROR UNBOUNDED`, and the analysis still terminates.
- **A2a Retry budget exceeds the caller's deadline.** `W_k = k × perTry + Σ_{j=1..k-1} min(initial × multiplier^(j-1), maxBackoff)`. If `W_n > deadline(caller)`: `WARN`, "only k of n tries fit", where k is the largest with `W_k ≤ deadline`.
- **A2b Abandoned work.** `deadline(callee) > perTry(edge)`: the caller gives up while the callee keeps working. `WARN` if the edge retries, `INFO` otherwise.
- **A3 Retry on a non-idempotent edge.** `ERROR`.
- **A4 Backoff hygiene.** Retries with zero backoff: `WARN`. Retries without jitter: `INFO`.
- **A5 Layered retries.** One edge with ≥ 2 layers where `totalAttempts > 1`: `WARN`. One path with retries on ≥ 2 edges: `WARN`.

Unknown inputs: compute with what is known, tag the finding `depends-on-unknown` and name the unknown value. Never substitute a guess.

Exit codes: `0` no finding at or above `--fail-on` (default `ERROR`), `1` otherwise, `2` invalid input.

## Spec format and acceptance test

Durations are strings (`300ms`, `1s`). In a hand-written spec an absent `multiplier` means fixed backoff (1.0) and an absent `fanout` means 1; any other absent field is `UNKNOWN`.

In a collected graph (ADR 0009): `deadline` comes only from `deadline:` in the service's own `edges-hint.yaml`, never from its callers' or its own outbound timeouts, else `UNKNOWN`; a Spring Cloud Gateway with routes is an entry unless its hint says `entry: false`; `fanout` comes from the hint method entry, else `UNKNOWN` (A1 counts it as 1 and tags `depends-on-unknown`), and a gateway route has fanout 1.

```yaml
# examples/checkout.yaml
services:
  gateway:  { entry: true, deadline: 3s }
  orders:   { deadline: 2s }
  payments: { deadline: 2s }
  ledger:   { deadline: 500ms }
edges:
  - { from: gateway,  to: orders,
      layers: [ { kind: resilience4j, maxAttempts: 3, perTryTimeout: 1s,    backoff: 100ms, jitter: false } ] }
  - { from: orders,   to: payments, idempotent: false,
      layers: [ { kind: istio,        attempts: 2,    perTryTimeout: 800ms, backoff: 25ms,  jitter: true } ] }
  - { from: payments, to: ledger,
      layers: [ { kind: resilience4j, maxAttempts: 3, perTryTimeout: 300ms, backoff: 0ms,   jitter: false } ] }
```

`plimsoll analyze examples/checkout.yaml` must report exactly:

| Rule | Location | Severity | Detail |
| --- | --- | --- | --- |
| A1 | ledger | ERROR | ×27 (orders ×3, payments ×9), limit 10 |
| A3 | orders→payments | ERROR | non-idempotent call retried, 3 tries |
| A2a | gateway→orders | WARN | budget 3.2 s > deadline 3 s, 2 of 3 tries fit |
| A2a | orders→payments | WARN | budget 2.45 s > deadline 2 s, 2 of 3 tries fit |
| A2b | gateway→orders | WARN | callee deadline 2 s > per-try 1 s |
| A2b | orders→payments | WARN | callee deadline 2 s > per-try 800 ms |
| A2b | payments→ledger | WARN | callee deadline 500 ms > per-try 300 ms |
| A4 | payments→ledger | WARN | zero backoff |
| A5 | gateway→orders→payments→ledger | WARN | retries on 3 edges of one path |
| A4 | gateway→orders | INFO | no jitter |
| A4 | payments→ledger | INFO | no jitter |

Summary line: `2 error, 7 warn, 2 info`. Exit code 1.

## Testing

- Every rule gets positive, negative and edge-case tests. Required cases: single edge ×3; three-hop chain ×27; Istio `attempts: 2` → 3 total; diamond (A→B→D, A→C→D) sums both paths; cycle → `UNBOUNDED` and terminates; Resilience4j 3 × Istio 3 on one edge → ×12 plus A5; budget overflow → "2 of 3 fit"; non-idempotent retry → `ERROR`; an `UNKNOWN` value shows as `UNKNOWN` in the report.
- Property test (jqwik): the amplification of a chain equals the product of its attempts.
- Oracle tests live in `plimsoll-core/src/test/java/.../oracle`. They run real Resilience4j with a failing supplier and count calls; use a fake clock or zero waits, never real sleeps.
- No network, no LLM, no sleeping in tests.

## Current milestone: v0.1 (target 15 Nov 2026)

Work comes from the issues in the [v0.1 milestone](https://github.com/hasanyelaldi/plimsoll/milestone/1), taken in issue-number order.

Out of scope until the maintainer says otherwise: the LLM layer, OpenTelemetry ingestion (v0.2), SARIF and the GitHub Action (v0.2), an HTML report, Linkerd, queue consumers, cloud SDKs, the dynamic fault-injection harness, performance what-if (module 2).

## Defaults knowledge base

`kb/defaults/<library>.yaml`, one entry per parameter:

```yaml
- library: resilience4j-retry
  versions: unverified
  parameter: maxAttempts
  value: 3
  unit: attempts-total
  source: https://resilience4j.readme.io/docs/retry
  verified_by: none        # doc | source | oracle | none
```

Seed entries, all `verified_by: none` until checked against primary sources:

- Resilience4j Retry: `maxAttempts` 3 (includes the first call), `waitDuration` 500 ms.
- Spring Cloud Gateway Retry: `retries` 3 (retries only), series 5XX, methods GET, `IOException` and `TimeoutException`, backoff and jitter disabled, timeout unlimited.
- Istio: default policy of 2 retries on `connect-failure,refused-stream,unavailable,cancelled`, 25 ms base interval (checked against the v1.31 reference).
- Spring AI: `spring.ai.retry.max-attempts` 10, initial backoff 2 s, multiplier 5, max backoff 3 min.

## Decisions (docs/adr, MADR)

- 0001 Java 21 + Maven; Python only for empirical-study analysis notebooks. Maven over Gradle because Sonatype has no official Gradle plugin for the Central Portal, and Maven Central download numbers are adoption evidence.
- 0002 LLM proposes, deterministic core computes; no LLM before month 4, and only with a measured gold set.
- 0003 Provenance-carrying values plus a semantic oracle per supported library version.
- 0004 v0.1 scope: Resilience4j + Istio + one HTTP client.
- 0005 Testbeds: spring-petclinic-microservices (primary), TrainTicket (secondary).
- 0006 Apache-2.0, developed in the open; name Plimsoll (after the load line marking a ship's safe limit).
- 0007 Positioning: the novelty is extraction from real config, verified semantics and measurement, not the graph math.
- 0008 HTTP method idempotency (RFC 9110) is a `LIBRARY_DEFAULT`.
- 0009 Deadline, entry and fanout in collected graphs.

## Related work (cite it, do not duplicate it)

- [retryamp](https://github.com/rashedhasan090/retryamp): the same graph math over a hand-written spec. A possible export target.
- [Mehan and Saluja, arXiv 2608.25403](https://arxiv.org/html/2608.25403): retry amplification factor; Python-only regex study.
- [RPCShield](https://github.com/uber-research/rpcshield) (Uber, SOSP '26): fail-open / fail-close static analysis for Go.
- [WASABI](https://www.microsoft.com/en-us/research/?p=1068147) (SOSP '24): LLM-assisted detection of retry bugs inside code.
- Mendonça, Aderaldo, Cámara, Garlan, ICSA 2020: PRISM model of Retry and Circuit Breaker; followed by ResilienceBench.

## Git

Conventional Commits (`feat(core): …`, `test(analysis): …`). One logical change per commit. Run `./mvnw verify` before committing.