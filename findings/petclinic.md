# First real run: spring-petclinic-microservices

Issue #9. Testbed choice: ADR 0005. The full generated report is in [`petclinic-report.md`](petclinic-report.md).

## Setup

| Input | Value |
| --- | --- |
| spring-petclinic-microservices | `1d76b00d683e86b62867a6bf59530f8ab301244f` (7 Oct 2026), Spring Boot 4.0.1, Spring Cloud 2025.1.0 (Gateway 5.0.0) |
| spring-petclinic-microservices-config | `323993ce2519c6d02df63e08bf4458d123d3b611` (2 May 2026), read by hand only |
| Plimsoll | `3851edd` (main after #35); report regenerated after #23 (PLM-C1, Coverage section) after #37 (client calls in code), #38 (unlimited timeouts, ADR 0011) and #48 (alternative routes, ADR 0012), same command |

```bash
git clone https://github.com/spring-petclinic/spring-petclinic-microservices.git
git -C spring-petclinic-microservices checkout 1d76b00d683e86b62867a6bf59530f8ab301244f
./mvnw -q -DskipTests package
java -jar plimsoll-cli/target/plimsoll.jar analyze --format=MARKDOWN spring-petclinic-microservices
```

No file in petclinic was changed and no `edges-hint.yaml` was added: this is what Plimsoll finds on the repository as it is. Exit code 1. Two runs gave byte-identical output.

## Result

After #37: **3 error, 5 warn, 16 info.** The graph has one entry (`api-gateway`) and seven edges: four from Gateway routes and three from `@LoadBalanced` WebClient calls found in code. The first run (before #37) gave 3 error, 5 warn, 10 info on the four route edges only; the six new findings are all INFO.

| Service | Amplification |
| --- | --- |
| api-gateway | ×1 |
| customers-service | at least ×3 |
| genai-service | ×2 |
| vets-service | at least ×2 |
| visits-service | at least ×3 |

"At least": the edges found in code have no fanout (ADR 0009: fanout comes only from a hint), so A1 counts them as 1 and reports INFO with `depends-on-unknown` instead of a plain number.

| Severity | Rule | Location | Detail |
| --- | --- | --- | --- |
| ERROR | A3 | api-gateway→customers-service | non-idempotent call retried, 2 tries |
| ERROR | A3 | api-gateway→genai-service | non-idempotent call retried, 2 tries |
| ERROR | A3 | api-gateway→visits-service | non-idempotent call retried, 2 tries |
| WARN | A4 | api-gateway→customers-service, genai-service, visits-service | zero backoff |
| WARN | C1 | api-gateway | Spring Cloud CircuitBreaker (programmatic) detected, not modelled |
| WARN | C1 | genai-service | Spring AI retry detected, not modelled |
| INFO | A1 | customers-service, visits-service | at least ×3 (fanout of a code edge `UNKNOWN`) |
| INFO | A1 | vets-service | at least ×2 (genai-service ×2); max over 2 alternatives of routes of api-gateway |
| INFO | A2a | the three retried route edges | cannot evaluate (gateway deadline `UNKNOWN`) |
| INFO | A2b | all seven edges | cannot evaluate (callee deadline `UNKNOWN`) |
| INFO | A4 | the three retried route edges | no jitter |

### Edges found in code (#37)

| Edge | Operation | Call site | How the host was found |
| --- | --- | --- | --- |
| api-gateway→customers-service | `GET /owners/{ownerId}` | `CustomersServiceClient.java:36` | string literal `http://customers-service/owners/{ownerId}` |
| api-gateway→visits-service | `GET /pets/visits` | `VisitsServiceClient.java:44` | field `hostname` initialised to `http://visits-service/`; the evidence names its reassignment in `setHostname` (line 55), which only tests call |
| genai-service→vets-service | `GET /vets` | `VectorStoreController.java:68` | local variable `vetsHostname` |

All three clients are the `@LoadBalanced WebClient.Builder` beans of their module (`ApiGatewayApplication.java:60`, `AIBeanConfiguration.java:26`), traced from the constructor parameter. LoadBalancer retries are off (`spring.cloud.loadbalancer.retry.enabled` not set), so each adds an `http-client` layer with 1 try. The three `AIDataProvider` calls to customers-service (`GET /owners`, `POST /owners`, `POST /owners/{id}/pets`) take their host from `DiscoveryClient.getInstances("customers-service")` at run time and use a plain `RestClient`: they are listed as notes with `file:line`, not edges.

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
| `perTryTimeout` (gateway routes) | unlimited | `LIBRARY_DEFAULT` | `httpclient.response-timeout` and route `metadata.response-timeout` not set; gateway 5.0.x has no default (`GatewayTimeoutOracleTest`, ADR 0011) |
| `perTryTimeout` (code edges) | unlimited | `LIBRARY_DEFAULT` | `WebClient.builder()` with Reactor Netty sets no response timeout (`HttpClientTimeoutOracleTest`) |
| Retry `timeout` (overall) | unlimited | `LIBRARY_DEFAULT` | Retry `timeout` not set; gateway Retry docs |

### What stayed `UNKNOWN`

| Value | Why |
| --- | --- |
| `deadline` of every service | no `edges-hint.yaml` anywhere; ADR 0009 takes deadlines only from hints |
| TimeLimiter of `defaultCircuitBreaker` and `genaiCircuitBreaker` | set in code (10 s in `ApiGatewayApplication.java:88`), not read (#18) |

Since #38 the per-try timeouts are known (unlimited), so the deadlines are the only missing A2 inputs and A2a and A2b name only them. What-if, not part of the report above: with just `deadline: 10s` in an `edges-hint.yaml` for api-gateway, A2a reports `WARN budget unlimited > deadline 10 s, 0 of 2 tries fit (no per-try timeout)` on the three retried routes (3 error, 8 warn, 13 info). That is the real behaviour: a try that hangs is never cut short by the gateway's HTTP client, so the `Retry` filter cannot run; only the 10 s TimeLimiter of `defaultCircuitBreaker` ends it, and that timeout is not retried (#31).

## Hand check

Each finding was checked against the petclinic sources at the pinned commit and against the config repository.

| Finding | Verdict | Notes |
| --- | --- | --- |
| A1 ×2 on customers, genai, visits; ×1 on vets | correct for the collected graph | Upper bound over the worst operation of the route: POST is retried once, GET is not. The real graph has more edges (see gaps), so these are lower than the true bound. |
| A3 on customers, genai, visits | true positive, with a nuance | The filter retries POST on `503` and on the default exceptions `IOException` and `TimeoutException`. A `503` usually means the request was not processed, but a POST that fails with an `IOException` after it was sent can be applied twice (duplicate owner, pet, visit). For genai, a retried `POST /chatclient` also repeats a paid LLM call. A3 does not yet say which trigger makes the retry unsafe (comment on #24). |
| A3 absent on vets | correct | vets-service has no POST; the Retry filter does not apply to its GETs. |
| A4 zero backoff, no jitter | true positive | No `backoff` arguments on the filter; the config repository does not override them. |
| A2a / A2b INFO | correct to stay silent | No deadline is configured anywhere; the response timeouts are known to be absent (#38). |
| C1 on api-gateway and genai-service | true positive | `ReactiveResilience4JCircuitBreakerFactory` in `ApiGatewayApplication.java:85` and `ApiGatewayController.java:44` (#18); a Spring AI model starter in `spring-petclinic-genai-service/pom.xml:27` (#19). |
| Sources not read: config server, profile documents, `profiles.active`, `creds.yaml` | harmless | The skipped `docker` documents only import the config server; the config repository has no retry, timeout or breaker settings. |

No false positives were found.

## Gaps (false negatives)

| Gap | Where in petclinic | Follow-up |
| --- | --- | --- |
| Calls whose host is computed at run time are notes, not edges | genai → customers (`AIDataProvider`, `DiscoveryClient`, incl. two POSTs) | — (by design in #37) |
| Programmatic Spring Cloud CircuitBreaker and its 10 s TimeLimiter | `ApiGatewayApplication.java:85-88`, `ApiGatewayController` (`getOwnerDetails` breaker) | #18 |
| Breakers in the model | `CircuitBreaker` default filter and `genaiCircuitBreaker` with fallbacks; the order is known since #31 (listed before `Retry`, so it wraps all tries, and a timeout is not retried) | #22 |
| Spring AI retry to the LLM provider | genai-service (`spring.ai.retry.*` not set, so the Spring AI defaults apply) | #19 |
| Condition-aware A3 (which status or exception triggers the retry) | `statuses: SERVICE_UNAVAILABLE` plus default exceptions | #24 |

The Coverage section of the report lists the files read for each of the 8 modules, the layer kinds searched on each of the 7 edges (`spring-cloud-gateway` on routes, `http-client` on code edges; there is no edges hint and no Istio manifest) and the 17 sources not read (#23).

Not gaps: Spring Cloud LoadBalancer retries stay off (`spring.cloud.loadbalancer.retry.enabled` is not set), so the `@LoadBalanced` clients add ×1 layers, as expected before #37.

### Where the code edges overstate (#37 hand check)

| Value | Why it is higher than the real per-request bound | Follow-up |
| --- | --- | --- |
| customers-service, visits-service at least ×3 | Since #48 the gateway routes are alternatives (ADR 0012), but the composite `GET /api/gateway/owners/{id}` path (×1) comes from the gateway's own controller, not a route, and is still added to the route (×2, POST retried). Each request takes one of them, so the real bound is ×2. | #58 (`later`) |
| vets-service at least ×2 | Was ×3 before #48 (vets route ×1 plus genai route ×2 summed); now the max of the two routes. genai-service→vets-service runs once at startup (`@EventListener` on `ApplicationStartedEvent`), not per request; without it vets-service is ×1. | #54 (`later`) |

Both are upper bounds that stay safe (never lower than the real value); no false positive at WARN or ERROR follows from them, since A1 stays below the limit of 10.

## Takeaways

- The collectors, idempotency and ADR 0009 rules work end to end on a real repository with no hand-written input, and every reported number traces to a `file:line` or a sourced default.
- On petclinic, the most useful finding is A3: POSTs retried by a gateway-wide default filter.
- Most of petclinic's resilience lives in code (programmatic breakers, client calls), not in YAML. That is where the v0.1 collectors see least.
