# Real run: TrainTicket (Istio defaults on real manifests)

Issue #28. Testbed choice: ADR 0005 (secondary testbed). The full generated report is in [`trainticket-report.md`](trainticket-report.md).

## Setup

| Input | Value |
| --- | --- |
| FudanSELab/train-ticket | `313886e99befb94be6cd45f085c98e0019f59829` (1 Nov 2022), Spring Boot 2.x, Spring Cloud Hoxton.SR12 |
| Plimsoll | `40205f9` (main after #46) |

```bash
git clone https://github.com/FudanSELab/train-ticket.git
git -C train-ticket checkout 313886e99befb94be6cd45f085c98e0019f59829
./mvnw -q -DskipTests package
java -jar plimsoll-cli/target/plimsoll.jar analyze --format=MARKDOWN train-ticket
```

No file in TrainTicket was changed and no `edges-hint.yaml` was added. Exit code 1. Two runs gave byte-identical output. The run takes about 5 s.

The first run, before #46, named every callee `${..._HOST:ts-...}` after the unresolved placeholder in the gateway's `lb://` URIs. No callee matched its module, so idempotency stayed `UNKNOWN` and the report was `0 error, 0 warn, 78 info`. #46 resolves a placeholder to its default. Everything below is after #46.

## Result

**34 error, 0 warn, 78 info.** The graph has one entry (`ts-gateway-service`) and 39 edges, one per gateway route, to 38 callees. Each edge has two layers:

1. `spring-cloud-gateway`: no `Retry` filter on any route, so 1 try.
2. `istio`: three Istio manifests were found and none has a VirtualService for these hosts, so every callee gets Istio's default policy (2 retries, so 3 tries).

| Rule | Count | Detail |
| --- | --- | --- |
| A1 | none above the limit | ×3 on 37 callees, ×6 on `ts-auth-service`, ×1 on the gateway |
| A3 `ERROR` | 34 | POST routes retried 3 times by the Istio default |
| A2a `INFO` | 39 | cannot evaluate: gateway deadline and Istio per-try timeout `UNKNOWN` |
| A2b `INFO` | 39 | cannot evaluate: callee deadline and both per-try timeouts `UNKNOWN` |
| A4, A5, C1 | none | Istio backs off (25 ms base, jittered); only one layer retries; no mechanism from `kb/detectors` was found |

Coverage: 44 modules read, 3 Istio manifests read, 39 edges searched for `spring-cloud-gateway` and `istio`. There are 39 sources not read, all of them the `${..._HOST:...}` placeholders in the gateway routes.

After #37 (client calls in code) the findings are unchanged; the report gains 146 notes. The services call each other through `@LoadBalanced RestTemplate`s, but build the host with a helper (`getServiceUrl("ts-order-service")` returning `"http://" + serviceName`) that the scanner does not fold, so each request is a "host computed at run time" note instead of an edge (#55).

## Values used and their provenance

| Value | Result | Source | Evidence |
| --- | --- | --- | --- |
| Gateway `totalAttempts` | 1 | `EXPLICIT` | no `Retry` filter on the route or in `default-filters` (`ts-gateway-service/src/main/resources/application.yml`) |
| Istio `totalAttempts` | 3 | `LIBRARY_DEFAULT` | no VirtualService for the host; Istio 1.31 default, attempts 2 + 1 (`kb/defaults/istio.yaml`, verified by doc) |
| Istio `backoff`, `jitter` | 25 ms, true | `LIBRARY_DEFAULT` | same KB entries; Envoy's fully jittered back-off |
| Istio `multiplier`, `maxBackoff` | `UNKNOWN` | | Envoy's back-off has no fixed multiplier or cap on the minimum wait |
| `idempotent` (34 routes) | false | `LIBRARY_DEFAULT` | a `POST` mapping of the callee reached by the route, e.g. `OrderController.java:37`; RFC 9110 (ADR 0008) |
| Callee names | e.g. `ts-order-service` | placeholder default | `lb://${ORDER_SERVICE_HOST:ts-order-service}`; a note says setting the variable changes the callee (#46) |
| `entry` (gateway) | true | ADR 0009 | a Gateway with routes is an entry |

### What stayed `UNKNOWN`

| Value | Why |
| --- | --- |
| `deadline` of every service | no `edges-hint.yaml`; ADR 0009 takes deadlines only from hints |
| Gateway `perTryTimeout` | `spring.cloud.gateway.httpclient.response-timeout` is not set and the KB has no sourced default (#38) |
| Istio `perTryTimeout` | no VirtualService, so no route timeout and no per-try timeout |
| `idempotent` on 5 routes | `ts-avatar-service` is Python (no module); `cancel`, `execute`, `train-food` and `verification-code` have only GET mappings, so they get no A3 finding, as expected |

## Hand check

Checked against the TrainTicket sources and manifests at the pinned commit.

| Finding | Verdict | Notes |
| --- | --- | --- |
| Istio default on every callee | correct for the input, but see the deployment caveat | `virtual-services-fault.yaml` routes only `ts-voucher-service`, which no collected edge calls. `trainticket-gateway.yaml` binds the ingress gateway only and is skipped (it does not apply to mesh traffic). No manifest sets `retries`, `timeout` or `outlierDetection`. |
| A3 on 34 POST routes | true positive in form, low risk in practice | POSTs do get up to 3 tries under the Istio default. But the default retries only on `connect-failure` and `refused-stream`, both before the upstream processes the request, and on `unavailable` and `cancelled`, gRPC statuses a REST call does not return. A duplicate side effect is therefore unlikely. A3 does not look at `retryOn` yet (comment on #24). |
| A1 ×3 per callee | correct | 1 (gateway) × 3 (Istio) per route. |
| A1 ×6 on `ts-auth-service` | overstated | Two routes (`auth`, `auth-user`) go to it. A request matches only one route, so the bound is ×3 (#48). The two A3 findings on these routes have the same location and cannot be told apart in the report (#48). |
| A2a / A2b `INFO` | correct to stay silent | No deadline or timeout is configured anywhere. |
| C1 none | correct | No Resilience4j, Spring Retry, Feign, Hystrix or other listed mechanism in the poms or the code. |

**Deployment caveat.** The Istio manifests in `deployment/kubernetes-manifests/k8s-with-istio` (May 2021, images `0.0.4`) deploy the older architecture, which has no `ts-gateway-service`; there, `ts-ui-dashboard` routes requests. The gateway was added in August 2022 and is deployed by `quickstart-k8s`, which has no Istio. Plimsoll merges all manifests under the directory with all the code, so the 34 A3 findings describe a combination that the repository never deploys: the current gateway inside the 2021 mesh. If that gateway ran in a mesh with sidecar injection, the findings would hold. Filed as #49.

## Gaps (false negatives)

| Gap | Where in TrainTicket | Follow-up |
| --- | --- | --- |
| Service-to-service calls in code are not edges | `RestTemplate` calls in 70 files, e.g. preserve → order, seat, travel, … | #37 |
| Gateway HTTP client timeouts | `response-timeout` not set | #38 |
| Condition-aware A3 and A1 | Istio default `retryOn` | #24 |
| Version of the libraries | Gateway 2.2.x (Hoxton) and Istio `v1alpha3`, while the KB covers Gateway 5.0.0 and Istio 1.31 | #51 |
| Modules that are not services | `old-docs/Json2Shiviz`, `old-docs/Lib/ms-monitoring-core`, `ts-common` (a library) | #50 |

## Takeaways

- The Istio collector works end to end on real manifests. It skips gateway-bound VirtualServices, notes namespace-less short hosts and routes no edge calls, and applies the sourced mesh default everywhere else.
- The run found a real collector bug (placeholders in route URIs, #46), now fixed, and an A1 modelling flaw with alternative routes (#48).
- The largest risk on a real repository is not the math but the input: several deployment variants in one repository (#49), and library versions other than the ones the KB was checked against (#51).
