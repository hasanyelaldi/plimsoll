# Plimsoll analysis of `spring-petclinic-microservices`

**3 error, 3 warn, 10 info**

## Amplification per service

Limit: 10

| Service | Amplification |
| --- | --- |
| api-gateway | ×1 |
| customers-service | ×2 |
| genai-service | ×2 |
| vets-service | ×1 |
| visits-service | ×2 |

## Findings

| Severity | Rule | Location | Detail |
| --- | --- | --- | --- |
| ERROR | PLM-A3 | api-gateway→customers-service | non-idempotent call retried, 2 tries |
| ERROR | PLM-A3 | api-gateway→genai-service | non-idempotent call retried, 2 tries |
| ERROR | PLM-A3 | api-gateway→visits-service | non-idempotent call retried, 2 tries |
| WARN | PLM-A4 | api-gateway→customers-service | zero backoff |
| WARN | PLM-A4 | api-gateway→genai-service | zero backoff |
| WARN | PLM-A4 | api-gateway→visits-service | zero backoff |
| INFO | PLM-A2a | api-gateway→customers-service | cannot evaluate: api-gateway→customers-service layer 1 (spring-cloud-gateway) perTryTimeout, api-gateway deadline UNKNOWN |
| INFO | PLM-A2a | api-gateway→genai-service | cannot evaluate: api-gateway→genai-service layer 1 (spring-cloud-gateway) perTryTimeout, api-gateway deadline UNKNOWN |
| INFO | PLM-A2a | api-gateway→visits-service | cannot evaluate: api-gateway→visits-service layer 1 (spring-cloud-gateway) perTryTimeout, api-gateway deadline UNKNOWN |
| INFO | PLM-A2b | api-gateway→customers-service | cannot evaluate: customers-service deadline, api-gateway→customers-service layer 1 (spring-cloud-gateway) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | api-gateway→genai-service | cannot evaluate: genai-service deadline, api-gateway→genai-service layer 1 (spring-cloud-gateway) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | api-gateway→vets-service | cannot evaluate: vets-service deadline, api-gateway→vets-service layer 1 (spring-cloud-gateway) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | api-gateway→visits-service | cannot evaluate: visits-service deadline, api-gateway→visits-service layer 1 (spring-cloud-gateway) perTryTimeout UNKNOWN |
| INFO | PLM-A4 | api-gateway→customers-service | no jitter |
| INFO | PLM-A4 | api-gateway→genai-service | no jitter |
| INFO | PLM-A4 | api-gateway→visits-service | no jitter |

## Values used

### ERROR PLM-A3 api-gateway→customers-service

- api-gateway→customers-service idempotent = false [LIBRARY_DEFAULT: POST (customers-service /owners, spring-petclinic-customers-service/src/main/java/org/springframework/samples/petclinic/customers/web/OwnerResource.java:57); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- api-gateway→customers-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]

### ERROR PLM-A3 api-gateway→genai-service

- api-gateway→genai-service idempotent = false [LIBRARY_DEFAULT: POST (genai-service /chatclient, spring-petclinic-genai-service/src/main/java/org/springframework/samples/petclinic/genai/PetclinicChatClient.java:57); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- api-gateway→genai-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]

### ERROR PLM-A3 api-gateway→visits-service

- api-gateway→visits-service idempotent = false [LIBRARY_DEFAULT: POST (visits-service /owners/*/pets/{petId}/visits, spring-petclinic-visits-service/src/main/java/org/springframework/samples/petclinic/visits/web/VisitResource.java:56); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- api-gateway→visits-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]

### WARN PLM-A4 api-gateway→customers-service

- api-gateway→customers-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→customers-service layer 1 (spring-cloud-gateway) backoff = 0 ms [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]

### WARN PLM-A4 api-gateway→genai-service

- api-gateway→genai-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→genai-service layer 1 (spring-cloud-gateway) backoff = 0 ms [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]

### WARN PLM-A4 api-gateway→visits-service

- api-gateway→visits-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→visits-service layer 1 (spring-cloud-gateway) backoff = 0 ms [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]

### INFO PLM-A2a api-gateway→customers-service

- api-gateway→customers-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→customers-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.server.webflux.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- api-gateway→customers-service layer 1 (spring-cloud-gateway) backoff = 0 ms [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]
- api-gateway→customers-service layer 1 (spring-cloud-gateway) multiplier = 1.0 [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]
- api-gateway→customers-service layer 1 (spring-cloud-gateway) maxBackoff = 0 ms [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]
- api-gateway deadline = UNKNOWN [UNKNOWN: api-gateway has no edges hint; set 'deadline:' in the edges-hint.yaml of api-gateway (ADR 0009)]
- **depends-on-unknown: api-gateway deadline, api-gateway→customers-service layer 1 (spring-cloud-gateway) perTryTimeout**

### INFO PLM-A2a api-gateway→genai-service

- api-gateway→genai-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→genai-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.server.webflux.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- api-gateway→genai-service layer 1 (spring-cloud-gateway) backoff = 0 ms [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]
- api-gateway→genai-service layer 1 (spring-cloud-gateway) multiplier = 1.0 [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]
- api-gateway→genai-service layer 1 (spring-cloud-gateway) maxBackoff = 0 ms [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]
- api-gateway deadline = UNKNOWN [UNKNOWN: api-gateway has no edges hint; set 'deadline:' in the edges-hint.yaml of api-gateway (ADR 0009)]
- **depends-on-unknown: api-gateway deadline, api-gateway→genai-service layer 1 (spring-cloud-gateway) perTryTimeout**

### INFO PLM-A2a api-gateway→visits-service

- api-gateway→visits-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→visits-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.server.webflux.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- api-gateway→visits-service layer 1 (spring-cloud-gateway) backoff = 0 ms [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]
- api-gateway→visits-service layer 1 (spring-cloud-gateway) multiplier = 1.0 [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]
- api-gateway→visits-service layer 1 (spring-cloud-gateway) maxBackoff = 0 ms [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]
- api-gateway deadline = UNKNOWN [UNKNOWN: api-gateway has no edges hint; set 'deadline:' in the edges-hint.yaml of api-gateway (ADR 0009)]
- **depends-on-unknown: api-gateway deadline, api-gateway→visits-service layer 1 (spring-cloud-gateway) perTryTimeout**

### INFO PLM-A2b api-gateway→customers-service

- customers-service deadline = UNKNOWN [UNKNOWN: customers-service has no edges hint; set 'deadline:' in the edges-hint.yaml of customers-service (ADR 0009)]
- api-gateway→customers-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.server.webflux.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- api-gateway→customers-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- **depends-on-unknown: api-gateway→customers-service layer 1 (spring-cloud-gateway) perTryTimeout, customers-service deadline**

### INFO PLM-A2b api-gateway→genai-service

- genai-service deadline = UNKNOWN [UNKNOWN: genai-service has no edges hint; set 'deadline:' in the edges-hint.yaml of genai-service (ADR 0009)]
- api-gateway→genai-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.server.webflux.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- api-gateway→genai-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- **depends-on-unknown: api-gateway→genai-service layer 1 (spring-cloud-gateway) perTryTimeout, genai-service deadline**

### INFO PLM-A2b api-gateway→vets-service

- vets-service deadline = UNKNOWN [UNKNOWN: vets-service has no edges hint; set 'deadline:' in the edges-hint.yaml of vets-service (ADR 0009)]
- api-gateway→vets-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.server.webflux.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- api-gateway→vets-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15) repeats only POST (spring-petclinic-api-gateway/src/main/resources/application.yml:19 (methods)); route vets-service serves vets-service GET /vets at spring-petclinic-vets-service/src/main/java/org/springframework/samples/petclinic/vets/web/VetResource.java:44]
- **depends-on-unknown: api-gateway→vets-service layer 1 (spring-cloud-gateway) perTryTimeout, vets-service deadline**

### INFO PLM-A2b api-gateway→visits-service

- visits-service deadline = UNKNOWN [UNKNOWN: visits-service has no edges hint; set 'deadline:' in the edges-hint.yaml of visits-service (ADR 0009)]
- api-gateway→visits-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.server.webflux.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- api-gateway→visits-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- **depends-on-unknown: api-gateway→visits-service layer 1 (spring-cloud-gateway) perTryTimeout, visits-service deadline**

### INFO PLM-A4 api-gateway→customers-service

- api-gateway→customers-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→customers-service layer 1 (spring-cloud-gateway) jitter = false [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): jitter not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]

### INFO PLM-A4 api-gateway→genai-service

- api-gateway→genai-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→genai-service layer 1 (spring-cloud-gateway) jitter = false [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): jitter not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]

### INFO PLM-A4 api-gateway→visits-service

- api-gateway→visits-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→visits-service layer 1 (spring-cloud-gateway) jitter = false [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): jitter not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]

## Notes

- admin-server: spring-petclinic-admin-server/src/main/resources/application.yml:9: document skipped, it is activated by spring.config.activate.on-profile (only the default profile is read)
- api-gateway: entry, Spring Cloud Gateway routing to customers-service, genai-service, vets-service, visits-service (ADR 0009)
- api-gateway: route customers-service: CircuitBreaker and Retry both apply; whether the breaker wraps all tries or each try is UNKNOWN (not checked against the real gateway yet)
- api-gateway: route genai-service: CircuitBreaker and Retry both apply; whether the breaker wraps all tries or each try is UNKNOWN (not checked against the real gateway yet)
- api-gateway: route vets-service: CircuitBreaker and Retry both apply; whether the breaker wraps all tries or each try is UNKNOWN (not checked against the real gateway yet)
- api-gateway: route visits-service: CircuitBreaker and Retry both apply; whether the breaker wraps all tries or each try is UNKNOWN (not checked against the real gateway yet)
- api-gateway: spring-petclinic-api-gateway/src/main/resources/application.yml:11: CircuitBreaker 'defaultCircuitBreaker' (default-filters[0]) on route customers-service is not modelled; its TimeLimiter is UNKNOWN
- api-gateway: spring-petclinic-api-gateway/src/main/resources/application.yml:11: CircuitBreaker 'defaultCircuitBreaker' (default-filters[0]) on route genai-service is not modelled; its TimeLimiter is UNKNOWN
- api-gateway: spring-petclinic-api-gateway/src/main/resources/application.yml:11: CircuitBreaker 'defaultCircuitBreaker' (default-filters[0]) on route vets-service is not modelled; its TimeLimiter is UNKNOWN
- api-gateway: spring-petclinic-api-gateway/src/main/resources/application.yml:11: CircuitBreaker 'defaultCircuitBreaker' (default-filters[0]) on route visits-service is not modelled; its TimeLimiter is UNKNOWN
- api-gateway: spring-petclinic-api-gateway/src/main/resources/application.yml:45: CircuitBreaker 'genaiCircuitBreaker' (route genai-service filters[1]) on route genai-service is not modelled; its TimeLimiter is UNKNOWN
- api-gateway: spring-petclinic-api-gateway/src/main/resources/application.yml:49: document skipped, it is activated by spring.config.activate.on-profile (only the default profile is read)
- customers-service: spring-petclinic-customers-service/src/main/resources/application.yml:9: document skipped, it is activated by spring.config.activate.on-profile (only the default profile is read)
- discovery-server: spring-petclinic-discovery-server/src/main/resources/application.yml:16: document skipped, it is activated by spring.config.activate.on-profile (only the default profile is read)
- genai-service: spring-petclinic-genai-service/src/main/resources/application.yml:41: document skipped, it is activated by spring.config.activate.on-profile (only the default profile is read)
- genai-service: spring-petclinic-genai-service/src/main/resources/application.yml:7: spring.profiles.active activates profiles; their documents and files are not applied
- vets-service: spring-petclinic-vets-service/src/main/resources/application.yml:12: document skipped, it is activated by spring.config.activate.on-profile (only the default profile is read)
- vets-service: spring-petclinic-vets-service/src/main/resources/application.yml:9: spring.profiles.active activates profiles; their documents and files are not applied
- visits-service: spring-petclinic-visits-service/src/main/resources/application.yml:9: document skipped, it is activated by spring.config.activate.on-profile (only the default profile is read)
