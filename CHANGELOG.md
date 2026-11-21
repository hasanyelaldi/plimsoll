# Changelog

All notable changes to Plimsoll are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Before 1.0, a minor version may change
the report format and the rules.

## [Unreleased]

### Analysis

- PLM-C2 Declared configuration differs from what runs (ADR 0016, #25): `WARN`, one finding per service and
  setting, with the declared value and the one that runs, each with its source. No input produces it yet: the
  readers for `/actuator/configprops` and Envoy route dumps follow.
- Failure classes (ADR 0014, #24): a layer records which failures it retries and what it passes on once it gives up.
  Istio's `retryOn` conditions and its 503/504 answers are mapped from the Envoy source of Istio 1.31's proxy;
  Spring Cloud Gateway's `Retry` conditions and its answers (500 after a connection failure or reset, 503 for a
  service without instances, 504 after a response timeout) from oracle tests on the real gateway; Spring Cloud
  LoadBalancer's retryable exceptions and status codes, and Resilience4j's default of retrying every exception, from
  oracle tests too.
- PLM-A3 reads them (#24): a non-idempotent edge is `ERROR` when a failure the callee may have processed is retried,
  with the most tries over those classes and, when every layer's conditions are known, a class that causes it
  (e.g. "2 tries (on reset)"). An edge retried only when the request was not sent is `INFO`, "retried only when the
  request was not sent, n tries". The finding lists the known conditions with their sources. Unknown conditions
  retry every class, so the finding is as before. On TrainTicket the 34 POST routes under the Istio default are now
  `INFO`.
- PLM-A1 adds the figure per failure class at a service (#24), e.g. "by failure class at c: ×15 for not-sent, ×5
  for any other class", when every layer on the edges into it has known conditions and some class is below the
  bound. Above those edges the class is unknown, so they keep their bound; the severity stays on the bound. On
  petclinic, customers-service and visits-service show ×2 for a 4xx status.

### Collectors

- Resilience4j: `@CircuitBreaker` becomes the layer's breaker (ADR 0015, #22), with its settings from
  `resilience4j.circuitbreaker.instances` and `configs` and its position from the aspect orders (by default Retry
  wraps the breaker, so it counts each try). Defaults are in `kb/defaults/resilience4j-circuitbreaker.yaml`; how
  Spring Boot resolves an instance (its `baseConfig`, a config of its own name, `configs.default`) is checked by
  `CircuitBreakerConfigOracleTest`. A wait in the open state with exponential backoff or randomized wait is
  `UNKNOWN`, and one below 1 ms is invalid input, as Resilience4j refuses to start. The "not modelled yet" note is gone.
- Spring Cloud Gateway: a route's first `CircuitBreaker` filter becomes the breaker of its outermost gateway layer
  (ADR 0015, #22). Its position comes from the filter order (listed before `Retry`: it wraps all tries; after: it
  counts each try; no `Retry`: `UNKNOWN`), as the gateway oracle checks. Its name is the `name` argument, or the first
  argument of the shortcut form `CircuitBreaker=cb` (oracle). Its settings come from Spring Cloud CircuitBreaker
  (#18, below). A further breaker on the same route is a note. On petclinic every route lists
  `defaultCircuitBreaker`; the findings are unchanged.
- Spring Cloud CircuitBreaker (#18): the gateway's `Customizer<ReactiveResilience4JCircuitBreakerFactory>` beans are
  read (`configureDefault`, `configure(..., ids)`, `CircuitBreakerConfig` and `TimeLimiterConfig` as `ofDefaults()` or
  `custom()...build()` with literal values), together with Resilience4j instances and configs named like the id and
  `spring.cloud.circuitbreaker.resilience4j.disable-time-limiter(-map)`. They give gateway breakers their settings.
  Each `CircuitBreaker` filter's TimeLimiter caps the overall timeout of a `Retry` listed after it and the per-try
  timeout otherwise. What cannot be followed is `UNKNOWN` and named. The resolution order is checked against the
  real factory by `SpringCloudCircuitBreakerOracleTest`. On petclinic the breakers get `CircuitBreakerConfig.ofDefaults()`
  and the 10 s TimeLimiter; the findings are unchanged.
- Spring Cloud CircuitBreaker call sites (#18): `create("id")` on a reactive factory and its `run(...)`, inline or
  through a local, followed through `transform(it -> ...)` and locals to the requests it wraps: those written inside
  it, and those of a `receiver.method(...)` it calls (one level, as the client call scanner names methods). Each such
  request's edge gets a `resilience4j` layer outside the HTTP client: one try, the breaker, and its TimeLimiter as
  per-try and overall timeout; it passes errors on as they are (`givesUpAs`, oracle). A `run` fallback is noted. The
  blocking factories and `create(id, group)` are noted, not modelled, and stay in PLM-C1; the reactive factories
  left PLM-C1. On petclinic, `getOwnerDetails` wraps api-gateway→visits-service (GET /pets/visits) and the C1 warning
  on api-gateway is gone (3 error, 4 warn, 16 info).

### Spec format

- `retryOn` lists failure classes (`not-sent`, `reset`, `timeout`, `status-503`, or a status code such as `503`);
  any other name is invalid input (#24).
- A layer may have a `circuitBreaker` (name, position relative to the layer's retry, failure rate threshold,
  sliding window, minimum calls, wait in the open state); an absent field is `UNKNOWN` (ADR 0015, #22).

### CLI and report

- Text and Markdown reports list the circuit breakers after the findings, each value with its source (#22). No rule
  reads them yet, and SARIF output is unchanged.
- SARIF: `tool.driver` carries the Plimsoll version (`version`, `semanticVersion`), so code scanning can tell when
  results changed with the tool (#85).

### Documentation

- README links the [plimsoll-demo](https://github.com/hasanyelaldi/plimsoll-demo) repository and explains the
  fingerprint warning `upload-sarif` logs; ADR 0013 records it (#85).

### Build

- CI also runs the GitHub Action with the last release, downloaded from Maven Central, so its SHA-256 and signature
  checks run on Linux and macOS (#83).

## [0.2.0] - 2026-11-12

Plimsoll in CI: SARIF output for code scanning and a GitHub Action that runs the analysis and uploads it.

### CLI and report

- `--format=SARIF`: SARIF 2.1.0 for code scanning; a finding is placed at the first `file:line` among its values
  (ADR 0013, #75). Each result has its own `primaryLocationLineHash`, so findings that share a line stay separate
  alerts (#76).
- GitHub Action (`uses: hasanyelaldi/plimsoll@v0.2.0`): downloads the release jar, checks its SHA-256 and signature,
  runs the analysis and uploads SARIF to code scanning; fails on findings at or above `fail-on` after the upload (#76).

### Build

- CI runs the GitHub Action on Linux and macOS (#76).
- `plimsoll --version` prints the project version from the POM instead of a hard-coded string (#73).

## [0.1.0] - 2026-11-08

First release: static analysis of retry, timeout and amplification settings across a service graph, from a
hand-written spec or from the configuration of a directory of services.

### Analysis

- Rules PLM-A1 (amplification, with `UNBOUNDED` for cycles), PLM-A2a (retry budget exceeds the caller's deadline),
  PLM-A2b (abandoned work), PLM-A3 (retry on a non-idempotent edge), PLM-A4 (zero backoff, no jitter) and PLM-A5
  (layered retries on one edge or one path) (#2, #3).
- Several layers on one edge, outermost first; attempts multiply across layers (#7).
- Alternative edges of one routing decision (`choice`): A1 takes the largest within a choice and adds up across choices
  (ADR 0012, #48).
- An absent timeout is the known value `unlimited`, not `UNKNOWN` (ADR 0011, #38).
- PLM-C1: retry, timeout and breaker mechanisms no collector models (Spring Retry, Failsafe, Feign, Hystrix, gRPC,
  OkHttp, Apache HttpClient 5, Reactor retry, MicroProfile Fault Tolerance, Spring AI and others) are reported with
  `file:line`, from the patterns in `kb/detectors/mechanisms.yaml` (#23).
- Every value carries its provenance (`EXPLICIT`, `LIBRARY_DEFAULT`, `INFERRED`, `UNKNOWN`) and every finding lists the
  values it used; a finding that used an unknown value is tagged `depends-on-unknown` (ADR 0003).

### Collectors

- Resilience4j: Retry and TimeLimiter from `application.yml` and annotations, with `edges-hint.yaml` naming the callee
  of each client method (#5).
- Istio: `VirtualService` retries and timeouts, `DestinationRule`, and Istio's implicit default retry policy (#6).
- Spring Cloud Gateway: routes to `lb://` services become edges with their `Retry` filters, response timeouts and
  route metadata; the routes of a gateway are one choice; `${NAME:default}` placeholders in route URIs resolve to
  their default (#17, #38, #46, #48).
- HTTP clients: Spring Cloud LoadBalancer retries and client timeouts (#8, #38); WebClient, RestClient and RestTemplate
  requests to a known service through a `@LoadBalanced` client become edges (#37).
- Edge idempotency from the HTTP method, per RFC 9110, as a `LIBRARY_DEFAULT` (ADR 0008, #20).
- Deadline, entry and fanout in collected graphs, from `edges-hint.yaml` (ADR 0009, #21).
- Several repositories analysed by checking them out side by side under one directory (ADR 0010, #13).

### Library semantics

Oracle tests run the real libraries in test scope and pin the behaviour Plimsoll encodes:

- Resilience4j: attempt counting, decorator order, configuration binding, TimeLimiter order (#4, #5).
- Spring Cloud Gateway: Retry attempt counting and defaults, filter order (a CircuitBreaker before Retry wraps all
  tries), a TimeLimiter timeout is never retried, response timeouts, first matching route only (#31, #38, #48).
- Spring Cloud CircuitBreaker: how the Resilience4j factory picks an id's breaker and TimeLimiter; a breaker runs the
  call once and passes its error on (#18).
- Spring Cloud LoadBalancer: retry counting; exceptions retried for every method, status codes for GET only (#8).
- Spring: duration parsing, annotation order, HTTP client timeouts.

Library defaults are in `kb/defaults/`, one file per library, each entry with its source and how it was verified.

### CLI and report

- `plimsoll analyze <spec-or-dir>` with `--max-amp`, `--fail-on` and `--format` (`TEXT`, `MARKDOWN`); exit codes 0, 1,
  2 (#2, #35).
- A Coverage section (files read per service, layer kinds searched and found per edge, configuration not read) and a
  Notes section for collected directories (#23).
- Deterministic output: the same input gives a byte-identical report.
- One executable jar, `plimsoll.jar`.

### Documentation

- README with usage, rules, provenance and known limitations (#60).
- ADRs 0001–0012 in `docs/adr/` (#62).
- Testbed runs with a hand check of every finding: spring-petclinic-microservices (`findings/petclinic.md`, #9) and
  TrainTicket (`findings/trainticket.md`, #28).

### Build

- Java 21, Maven multi-module build, CI on every push and pull request (#1).
- `release` profile for Maven Central through the Central Portal (#64).

[Unreleased]: https://github.com/hasanyelaldi/plimsoll/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/hasanyelaldi/plimsoll/releases/tag/v0.2.0
[0.1.0]: https://github.com/hasanyelaldi/plimsoll/releases/tag/v0.1.0
