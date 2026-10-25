# First real run: spring-petclinic-microservices

Issue #9. Testbed choice: ADR 0005. The full generated report is in [`petclinic-report.md`](petclinic-report.md).

## Setup

| Input | Value |
| --- | --- |
| spring-petclinic-microservices | `1d76b00d683e86b62867a6bf59530f8ab301244f` (7 Oct 2026), Spring Boot 4.0.1, Spring Cloud 2025.1.0 (Gateway 5.0.0) |
| spring-petclinic-microservices-config | `323993ce2519c6d02df63e08bf4458d123d3b611` (2 May 2026), read by hand only |
| Plimsoll | `3851edd` (main after #35); report regenerated after #23 (PLM-C1, Coverage section), same command |

```bash
git clone https://github.com/spring-petclinic/spring-petclinic-microservices.git
git -C spring-petclinic-microservices checkout 1d76b00d683e86b62867a6bf59530f8ab301244f
./mvnw -q -DskipTests package
java -jar plimsoll-cli/target/plimsoll.jar analyze --format=MARKDOWN spring-petclinic-microservices
```

No file in petclinic was changed and no `edges-hint.yaml` was added: this is what Plimsoll finds on the repository as it is. Exit code 1. Two runs gave byte-identical output.

## Result

**3 error, 5 warn, 10 info.** The graph has one entry (`api-gateway`) and four edges, all from Gateway routes.

| Service | Amplification |
| --- | --- |
| api-gateway | ×1 |
| customers-service | ×2 |
| genai-service | ×2 |
| vets-service | ×1 |
| visits-service | ×2 |

| Severity | Rule | Location | Detail |
| --- | --- | --- | --- |
| ERROR | A3 | api-gateway→customers-service | non-idempotent call retried, 2 tries |
| ERROR | A3 | api-gateway→genai-service | non-idempotent call retried, 2 tries |
| ERROR | A3 | api-gateway→visits-service | non-idempotent call retried, 2 tries |
| WARN | A4 | api-gateway→customers-service, genai-service, visits-service | zero backoff |
| WARN | C1 | api-gateway | Spring Cloud CircuitBreaker (programmatic) detected, not modelled |
| WARN | C1 | genai-service | Spring AI retry detected, not modelled |
| INFO | A2a | same three edges | cannot evaluate (per-try timeout, gateway deadline `UNKNOWN`) |
| INFO | A2b | all four edges | cannot evaluate (per-try timeout, callee deadline `UNKNOWN`) |
| INFO | A4 | same three edges | no jitter |

## Values used and their provenance

All four edges come from one `Retry` default filter (`spring-petclinic-api-gateway/src/main/resources/application.yml:15`) applied to every route.

| Value | Result | Source | Evidence |
| --- | --- | --- | --- |
| `totalAttempts` (customers, genai, visits) | 2 | `EXPLICIT` | `retries: 1` at `application.yml:17`, normalised as retries + 1 |
| `totalAttempts` (vets) | 1 | `EXPLICIT` | `methods: POST` at `application.yml:19`; vets-service serves only `GET /vets` (`VetResource.java:44`) |
| `idempotent` (customers) | false | `LIBRARY_DEFAULT` | `POST /owners`, `OwnerResource.java:57`; RFC 9110 §9.2.2 (ADR 0008) |
| `idempotent` (genai) | false | `LIBRARY_DEFAULT` | `POST /chatclient`, `PetclinicChatClient.java:57` |
| `idempotent` (visits) | false | `LIBRARY_DEFAULT` | `POST /owners/*/pets/{petId}/visits`, `VisitResource.java:56` |
| `backoff`, `multiplier`, `maxBackoff` | 0 ms, 1.0, 0 ms | `LIBRARY_DEFAULT` | Gateway 5.0.0 Retry docs: backoff disabled |
| `jitter` | false | `LIBRARY_DEFAULT` | Gateway 5.0.0 Retry docs |
| `entry` (api-gateway) | true | ADR 0009 | a Gateway with routes is an entry |
| `fanout` (gateway routes) | 1 | ADR 0009 | a gateway route has fanout 1 |

### What stayed `UNKNOWN`

| Value | Why |
| --- | --- |
| `perTryTimeout` on every gateway edge | `spring.cloud.gateway.server.webflux.httpclient.response-timeout` is not set and the KB has no sourced default for it (#38) |
| `deadline` of every service | no `edges-hint.yaml` anywhere; ADR 0009 takes deadlines only from hints |
| Breaker/Retry nesting on every route | `CircuitBreaker` is listed before `Retry`; whether it wraps all tries or each try needs the Gateway oracle (#31) |
| TimeLimiter of `defaultCircuitBreaker` and `genaiCircuitBreaker` | set in code (10 s in `ApiGatewayApplication.java:88`), not read (#18) |

Because both A2 inputs are `UNKNOWN`, A2a and A2b give only "cannot evaluate" INFO findings. With a deadline hint per service and #38 they would become real checks.

## Hand check

Each finding was checked against the petclinic sources at the pinned commit and against the config repository.

| Finding | Verdict | Notes |
| --- | --- | --- |
| A1 ×2 on customers, genai, visits; ×1 on vets | correct for the collected graph | Upper bound over the worst operation of the route: POST is retried once, GET is not. The real graph has more edges (see gaps), so these are lower than the true bound. |
| A3 on customers, genai, visits | true positive, with a nuance | The filter retries POST on `503` and on the default exceptions `IOException` and `TimeoutException`. A `503` usually means the request was not processed, but a POST that fails with an `IOException` after it was sent can be applied twice (duplicate owner, pet, visit). For genai, a retried `POST /chatclient` also repeats a paid LLM call. A3 does not yet say which trigger makes the retry unsafe (comment on #24). |
| A3 absent on vets | correct | vets-service has no POST; the Retry filter does not apply to its GETs. |
| A4 zero backoff, no jitter | true positive | No `backoff` arguments on the filter; the config repository does not override them. |
| A2a / A2b INFO | correct to stay silent | No deadline or response timeout is configured anywhere. |
| C1 on api-gateway and genai-service | true positive | `ReactiveResilience4JCircuitBreakerFactory` in `ApiGatewayApplication.java:85` and `ApiGatewayController.java:44` (#18); a Spring AI model starter in `spring-petclinic-genai-service/pom.xml:27` (#19). |
| Sources not read: config server, profile documents, `profiles.active`, `creds.yaml` | harmless | The skipped `docker` documents only import the config server; the config repository has no retry, timeout or breaker settings. |

No false positives were found.

## Gaps (false negatives)

| Gap | Where in petclinic | Follow-up |
| --- | --- | --- |
| Calls made from code are not edges without a hint file | gateway → customers and visits (`CustomersServiceClient`, `VisitsServiceClient`); genai → vets (`VectorStoreController`); genai → customers (`AIDataProvider`, incl. two POSTs) | #37 |
| Gateway HTTP client timeouts | `response-timeout` and `connect-timeout` defaults | #38 |
| Programmatic Spring Cloud CircuitBreaker and its 10 s TimeLimiter | `ApiGatewayApplication.java:85-88`, `ApiGatewayController` (`getOwnerDetails` breaker) | #18 |
| Breakers in the model, filter order | `CircuitBreaker` default filter and `genaiCircuitBreaker` with fallbacks | #22, #31 |
| Spring AI retry to the LLM provider | genai-service (`spring.ai.retry.*` not set, so the Spring AI defaults apply) | #19 |
| Condition-aware A3 (which status or exception triggers the retry) | `statuses: SERVICE_UNAVAILABLE` plus default exceptions | #24 |

The Coverage section of the report lists the files read for each of the 8 modules, the layer kinds searched on each of the 4 edges (only `spring-cloud-gateway`: there is no edges hint and no Istio manifest) and the 17 sources not read (#23).

Not gaps: Spring Cloud LoadBalancer retries stay off (`spring.cloud.loadbalancer.retry.enabled` is not set), so the `@LoadBalanced` clients would add ×1 edges once #37 lands.

## Takeaways

- The collectors, idempotency and ADR 0009 rules work end to end on a real repository with no hand-written input, and every reported number traces to a `file:line` or a sourced default.
- On petclinic, the most useful finding is A3: POSTs retried by a gateway-wide default filter.
- Most of petclinic's resilience lives in code (programmatic breakers, client calls), not in YAML. That is where the v0.1 collectors see least.
