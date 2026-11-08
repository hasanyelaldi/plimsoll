# Plimsoll

> Every ship has a Plimsoll line. Your service graph should too.

Plimsoll finds where retry, timeout and circuit-breaker settings across a service graph go past safe limits. It reads the real configuration (Java code, Spring YAML, Istio manifests), builds the call graph, and reports problems such as a ×27 retry storm three hops down or a retry budget that cannot fit in the caller's deadline.

Every number in a report is computed by deterministic code, and every value it uses says where it came from (`file:line` or a library's documentation). Reports are meant to be attached to architecture decision records as evidence.

**Status:** v0.1 in preparation (target 15 Nov 2026). The CLI works on hand-written specs and on Spring Boot / Spring Cloud / Istio projects; see [Known limitations](#known-limitations) before trusting a report.

## Build and run

Requires Java 21. There is no published release yet; build from source:

```bash
./mvnw -q -DskipTests package
java -jar plimsoll-cli/target/plimsoll.jar analyze examples/checkout.yaml
```

```
Usage: plimsoll analyze [-hV] [--fail-on=<severity>] [--format=<format>]
                        [--max-amp=<n>] <spec-or-dir>
      <spec-or-dir>          YAML spec file, or a directory of service modules
      --fail-on=<severity>   Exit with 1 if a finding is at least this severe:
                               ERROR, WARN, INFO (default: ERROR).
      --format=<format>      Report format: TEXT, MARKDOWN (default: TEXT).
      --max-amp=<n>          A1 amplification limit (default: 10).
```

| Exit code | Meaning |
| --- | --- |
| `0` | no finding at or above `--fail-on` |
| `1` | at least one finding at or above `--fail-on` |
| `2` | invalid input |

The same input always gives a byte-identical report, so it can be committed and diffed.

## Two kinds of input

### A hand-written spec

A YAML file that describes services and edges directly. Useful for design reviews before any code exists.

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

- Durations are strings (`300ms`, `1s`). `perTryTimeout` and `overallTimeout` also take `unlimited`.
- Layer `kind` is `resilience4j` (attempts as `maxAttempts`), `istio` (`attempts`, retries only), `http-client` or `generic` (`totalAttempts`). See [Attempt counting](#attempt-counting).
- A layer also takes `perTryTimeout`, `backoff`, `multiplier`, `maxBackoff`, `jitter`, `retryOn` and `overallTimeout`; an edge takes `operation`, `idempotent`, `fanout` and `choice`.
- Layers are listed outermost first.
- An absent `multiplier` means fixed backoff, an absent `fanout` means 1; any other absent field is `UNKNOWN`.
- `choice: <name>` marks edges from one service that are alternatives (only one is taken per request, [ADR 0012](docs/adr/0012-alternative-edges-in-amplification.md)).

### A directory of services

```bash
java -jar plimsoll-cli/target/plimsoll.jar analyze path/to/repo
```

Plimsoll finds the service modules and Istio manifests under the directory and collects:

| Source | What becomes part of the graph |
| --- | --- |
| Resilience4j (`application.yml`, `@Retry`, `@TimeLimiter`) | retry and timeout layers on edges named in `edges-hint.yaml` |
| Spring Cloud Gateway | routes to `lb://` services become edges, with their `Retry` filters and response timeouts |
| HTTP clients | WebClient / RestClient / RestTemplate requests to a known service through a `@LoadBalanced` client become edges; Spring Cloud LoadBalancer retries become a layer |
| Istio `VirtualService`, `DestinationRule` | retry and timeout layers, including Istio's implicit default retry policy |

Several repositories are analysed by checking them out side by side under one directory and analysing that directory.

Code does not always say where a request goes or how long a service may work on one request. An optional `edges-hint.yaml` in a service module fills that in:

```yaml
service: api-gateway
deadline: 10s                                     # how long the service works on one request
entry: true                                       # overrides the entry rule
methods:
  - method: com.example.CustomersClient#getOwner  # fully qualified class # method name
    to: customers-service
    operation: GET /owners/{id}                   # defaults to Class#method
    idempotent: true                              # UNKNOWN when absent
    fanout: 1                                     # calls per request, UNKNOWN when absent
    client: webclient-loadbalanced                # or restclient
operations:                                       # facts about operations the service calls
  - to: customers-service
    operation: POST /owners
    idempotent: true                              # e.g. a POST protected by an idempotency key
```

Without a hint, a service's deadline is `UNKNOWN` (it is never guessed from timeouts), a Spring Cloud Gateway with routes is the entry, and fanout is `UNKNOWN`. See [ADR 0009](docs/adr/0009-deadline-entry-fanout-in-collected-graphs.md).

After the findings, a collected report has a **Coverage** section: the files read per service, the layer kinds searched and found per edge, and the configuration that was *not* read (config server, profile documents, other config files, unresolved placeholders). A **Notes** section lists what collectors saw but could not turn into the model.

## Example

`plimsoll analyze examples/checkout.yaml` (exit code 1):

```
Plimsoll analysis of checkout.yaml

Amplification per service (limit 10)
  gateway   ×1
  ledger    ×27
  orders    ×3
  payments  ×9

Findings
  ERROR  PLM-A1   ledger  ×27 (orders ×3, payments ×9), limit 10
          gateway→orders layer 1 (resilience4j) totalAttempts = 3 [EXPLICIT: checkout.yaml:9 (maxAttempts)]
          orders→payments layer 1 (istio) totalAttempts = 3 [EXPLICIT: checkout.yaml:11 (attempts 2 + 1)]
          payments→ledger layer 1 (resilience4j) totalAttempts = 3 [EXPLICIT: checkout.yaml:13 (maxAttempts)]
  ERROR  PLM-A3   orders→payments  non-idempotent call retried, 3 tries
          orders→payments idempotent = false [EXPLICIT: checkout.yaml:10]
          orders→payments layer 1 (istio) totalAttempts = 3 [EXPLICIT: checkout.yaml:11 (attempts 2 + 1)]
  ...

Coverage
  Source: hand-written spec; every layer is taken as written, nothing was collected

Summary: 2 error, 7 warn, 2 info
```

All findings, without the evidence lines:

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

Runs on real projects, with a hand check of every finding: [spring-petclinic-microservices](findings/petclinic.md) and [TrainTicket](findings/trainticket.md).

## Rules

| Rule | Checks | Severity |
| --- | --- | --- |
| **PLM-A1** Amplification | How many requests one entry request can cause at each service, assuming every try fails and the failure propagates upward. The attempts of an edge's layers multiply; paths through different callers add up; alternatives (`choice`) take the largest. A cycle reachable from an entry is `UNBOUNDED`. | `ERROR` above `--max-amp` |
| **PLM-A2a** Retry budget exceeds the caller's deadline | Time for all tries and backoffs, `k × perTry + Σ backoff`, against the caller's deadline; reports how many tries actually fit. | `WARN` |
| **PLM-A2b** Abandoned work | The callee may work longer (its deadline) than the caller waits for one try, so the callee keeps working after the caller gave up. | `WARN` if the edge retries, else `INFO` |
| **PLM-A3** Retry on a non-idempotent edge | A call that is not idempotent (e.g. `POST`, per RFC 9110) is retried. | `ERROR` |
| **PLM-A4** Backoff hygiene | Retries with zero backoff; retries without jitter. | `WARN`; `INFO` |
| **PLM-A5** Layered retries | One edge retried by two or more layers (e.g. Resilience4j and Istio); one path with retries on two or more edges. | `WARN` |
| **PLM-C1** Mechanism detected but not modelled | A retry, timeout or breaker mechanism no collector models yet, found by the patterns in [`kb/detectors/mechanisms.yaml`](kb/detectors/mechanisms.yaml). Only collected directories produce it. | `WARN` |

Findings are sorted by severity, then rule, then location. A finding that used an `UNKNOWN` value is computed with what is known, tagged `depends-on-unknown`, and names the unknown value; nothing is guessed.

### Attempt counting

Libraries count differently. Plimsoll normalizes every layer to `totalAttempts`, which always includes the first call:

| Source | Field | `totalAttempts` |
| --- | --- | --- |
| Resilience4j Retry | `maxAttempts` (includes the first call) | `maxAttempts` |
| Istio VirtualService | `retries.attempts` (retries only) | `attempts + 1` |
| Spring Cloud Gateway `Retry` filter | `retries` (retries only) | `retries + 1` |
| Spring Cloud LoadBalancer | `max-retries-on-same-service-instance`, `max-retries-on-next-service-instance` | `(same + 1) × (next + 1)` |
| `generic` layer | `totalAttempts` | as written |

These semantics are not taken from documentation alone: oracle tests run the real Resilience4j, Spring Cloud Gateway and Spring Cloud LoadBalancer in test scope and count the calls.

## Provenance

Every value in a finding carries its source:

| Source | Meaning | Evidence |
| --- | --- | --- |
| `EXPLICIT` | written in configuration or code | `file:line` |
| `LIBRARY_DEFAULT` | the library's default, because nothing was configured | library, version, documentation URL |
| `INFERRED` | a proposal, never used silently (not produced in v0.1) | — |
| `UNKNOWN` | not known; shown as `UNKNOWN` in the report | why it is unknown |

Library defaults come from the knowledge base in [`kb/defaults/`](kb/defaults/), one YAML file per library. Each entry has a source URL and says how it was verified (`doc`, `source`, `oracle` or `none`). A default with no sourced entry is `UNKNOWN`, never invented.

## Known limitations

v0.1 gives an upper bound from static configuration. Before acting on a report, know that:

- **Fail-close bound.** A1 assumes every try fails and every failure propagates. It does not intersect the retry conditions of stacked layers (`retryOn`, status codes, exceptions), so it can overstate amplification ([#24](https://github.com/hasanyelaldi/plimsoll/issues/24)).
- **Circuit breakers are detected, not modelled.** `@CircuitBreaker`, Spring Cloud CircuitBreaker and Istio outlier detection do not reduce amplification in the report ([#18](https://github.com/hasanyelaldi/plimsoll/issues/18), [#22](https://github.com/hasanyelaldi/plimsoll/issues/22)). A breaker's TimeLimiter is not used to cut a try in A2a.
- **Library versions are not matched.** Defaults come from one version per library (for example Istio 1.31, Spring Cloud Gateway 5.0); an older project gets those defaults ([#51](https://github.com/hasanyelaldi/plimsoll/issues/51)).
- **Fanout is unknown without a hint.** In a collected graph A1 counts an edge without `fanout` as 1 and reports "at least ×n".
- **Not every request becomes an edge.** Only requests through a `@LoadBalanced` client to a known service are edges; plain clients, `DiscoveryClient` lookups and hosts built by helper methods ([#55](https://github.com/hasanyelaldi/plimsoll/issues/55)) are listed as notes. Requests made at startup are counted as per-request edges ([#54](https://github.com/hasanyelaldi/plimsoll/issues/54)).
- **Blocking LoadBalancer retries** (RestTemplate, RestClient) have `UNKNOWN` attempts: there is no oracle test for that path yet.
- **Istio:** the mesh-wide `defaultHttpRetryPolicy` in MeshConfig is not read; when several HTTP routes of a host disagree, the differing fields are `UNKNOWN`; services are matched on the first label of the host; every callee is assumed to be in the mesh once Istio manifests are present. Ambient mode and Gateway API are not supported ([#27](https://github.com/hasanyelaldi/plimsoll/issues/27)).
- **Configuration not read:** config server, profile-specific documents, other config files and unresolved placeholders. The Coverage section lists each one it skipped.
- **Large graphs:** A5 enumerates simple paths, which grows exponentially on wide graphs.
- Text and Markdown output only; SARIF and a GitHub Action are planned for v0.2.

Open issues labelled [`later`](https://github.com/hasanyelaldi/plimsoll/issues?q=is%3Aopen+label%3Alater) track the rest.

## Related work

- [retryamp](https://github.com/rashedhasan090/retryamp): the same graph math over a hand-written spec.
- Mehan and Saluja, [arXiv 2608.25403](https://arxiv.org/html/2608.25403): a retry amplification factor, from a regex-based study of Python code.
- [RPCShield](https://github.com/uber-research/rpcshield) (Uber, SOSP '26): fail-open / fail-close static analysis for Go.
- [WASABI](https://www.microsoft.com/en-us/research/?p=1068147) (SOSP '24): LLM-assisted detection of retry bugs inside code.
- Mendonça, Aderaldo, Cámara and Garlan (ICSA 2020): a PRISM model of Retry and Circuit Breaker, followed by ResilienceBench.

Plimsoll's contribution is not the graph math but the extraction from real configuration, library semantics verified against the real libraries, and provenance on every value.

## Decisions

Architecture decisions are recorded in [`docs/adr/`](docs/adr/) (MADR format).

## License

Apache-2.0, see [LICENSE](LICENSE).
