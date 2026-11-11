# Changelog

All notable changes to Plimsoll are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Before 1.0, a minor version may change
the report format and the rules.

## [Unreleased]

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

[Unreleased]: https://github.com/hasanyelaldi/plimsoll/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/hasanyelaldi/plimsoll/releases/tag/v0.1.0
