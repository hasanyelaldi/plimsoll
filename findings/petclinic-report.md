# Plimsoll analysis of `spring-petclinic-microservices`

**3 error, 5 warn, 10 info**

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
| WARN | PLM-C1 | api-gateway | Spring Cloud CircuitBreaker (programmatic) detected, not modelled; edges from api-gateway may carry an unseen timeout layer |
| WARN | PLM-C1 | genai-service | Spring AI retry detected, not modelled; edges from genai-service may carry an unseen retry layer |
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

### WARN PLM-C1 api-gateway

- api-gateway Spring Cloud CircuitBreaker (programmatic) = type org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory [EXPLICIT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:85; https://docs.spring.io/spring-cloud-circuitbreaker/reference/]
- api-gateway Spring Cloud CircuitBreaker (programmatic) = type org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory [EXPLICIT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/boundary/web/ApiGatewayController.java:44; https://docs.spring.io/spring-cloud-circuitbreaker/reference/]

### WARN PLM-C1 genai-service

- genai-service Spring AI retry = dependency org.springframework.ai:spring-ai-starter-model-* [EXPLICIT: spring-petclinic-genai-service/pom.xml:27; https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html]

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

## Coverage

Source: collected from 8 service modules.

### Sources read

| Service | Sources |
| --- | --- |
| admin-server | spring-petclinic-admin-server/pom.xml, spring-petclinic-admin-server/src/main/java/, spring-petclinic-admin-server/src/main/resources/application.yml |
| api-gateway | spring-petclinic-api-gateway/pom.xml, spring-petclinic-api-gateway/src/main/java/, spring-petclinic-api-gateway/src/main/resources/application.yml |
| customers-service | spring-petclinic-customers-service/pom.xml, spring-petclinic-customers-service/src/main/java/, spring-petclinic-customers-service/src/main/resources/application.yml |
| discovery-server | spring-petclinic-discovery-server/pom.xml, spring-petclinic-discovery-server/src/main/java/, spring-petclinic-discovery-server/src/main/resources/application.yml |
| genai-service | spring-petclinic-genai-service/pom.xml, spring-petclinic-genai-service/src/main/java/, spring-petclinic-genai-service/src/main/resources/application.yml |
| spring-petclinic-config-server | spring-petclinic-config-server/pom.xml, spring-petclinic-config-server/src/main/java/, spring-petclinic-config-server/src/main/resources/application.yml |
| vets-service | spring-petclinic-vets-service/pom.xml, spring-petclinic-vets-service/src/main/java/, spring-petclinic-vets-service/src/main/resources/application.yml |
| visits-service | spring-petclinic-visits-service/pom.xml, spring-petclinic-visits-service/src/main/java/, spring-petclinic-visits-service/src/main/resources/application.yml |

Istio manifests: none.

### Layer kinds searched

| Edge | Operation | Searched | Found |
| --- | --- | --- | --- |
| api-gateway→customers-service | /api/customer/** | spring-cloud-gateway | spring-cloud-gateway |
| api-gateway→genai-service | /api/genai/** | spring-cloud-gateway | spring-cloud-gateway |
| api-gateway→vets-service | /api/vet/** | spring-cloud-gateway | spring-cloud-gateway |
| api-gateway→visits-service | /api/visit/** | spring-cloud-gateway | spring-cloud-gateway |

### Sources not read

| Service | Kind | Reference | Evidence |
| --- | --- | --- | --- |
| admin-server | config-server | `optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888/}` | spring-petclinic-admin-server/src/main/resources/application.yml:5 |
| admin-server | profile-document | `spring.config.activate.on-profile = docker` | spring-petclinic-admin-server/src/main/resources/application.yml:9 |
| api-gateway | config-server | `optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888/}` | spring-petclinic-api-gateway/src/main/resources/application.yml:5 |
| api-gateway | profile-document | `spring.config.activate.on-profile = docker` | spring-petclinic-api-gateway/src/main/resources/application.yml:49 |
| customers-service | config-server | `optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888/}` | spring-petclinic-customers-service/src/main/resources/application.yml:5 |
| customers-service | profile-document | `spring.config.activate.on-profile = docker` | spring-petclinic-customers-service/src/main/resources/application.yml:9 |
| discovery-server | config-server | `optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888/}` | spring-petclinic-discovery-server/src/main/resources/application.yml:5 |
| discovery-server | profile-document | `spring.config.activate.on-profile = docker` | spring-petclinic-discovery-server/src/main/resources/application.yml:16 |
| genai-service | profile | `spring.profiles.active = production` | spring-petclinic-genai-service/src/main/resources/application.yml:7 |
| genai-service | config-import | `optional:classpath:/creds.yaml` | spring-petclinic-genai-service/src/main/resources/application.yml:9 |
| genai-service | config-server | `optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888/}` | spring-petclinic-genai-service/src/main/resources/application.yml:9 |
| genai-service | profile-document | `spring.config.activate.on-profile = docker` | spring-petclinic-genai-service/src/main/resources/application.yml:41 |
| vets-service | config-server | `optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888/}` | spring-petclinic-vets-service/src/main/resources/application.yml:5 |
| vets-service | profile | `spring.profiles.active = production` | spring-petclinic-vets-service/src/main/resources/application.yml:9 |
| vets-service | profile-document | `spring.config.activate.on-profile = docker` | spring-petclinic-vets-service/src/main/resources/application.yml:12 |
| visits-service | config-server | `optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888/}` | spring-petclinic-visits-service/src/main/resources/application.yml:5 |
| visits-service | profile-document | `spring.config.activate.on-profile = docker` | spring-petclinic-visits-service/src/main/resources/application.yml:9 |

## Notes

- api-gateway: entry, Spring Cloud Gateway routing to customers-service, genai-service, vets-service, visits-service (ADR 0009)
- api-gateway: route customers-service: CircuitBreaker listed before Retry, so it wraps all tries; a TimeLimiter timeout ends the request without a retry (the breaker answers 504, which Retry does not repeat)
- api-gateway: route genai-service: CircuitBreaker listed before Retry, so it wraps all tries; a TimeLimiter timeout ends the request without a retry (the breaker answers 504, which Retry does not repeat)
- api-gateway: route vets-service: CircuitBreaker listed before Retry, so it wraps all tries; a TimeLimiter timeout ends the request without a retry (the breaker answers 504, which Retry does not repeat)
- api-gateway: route visits-service: CircuitBreaker listed before Retry, so it wraps all tries; a TimeLimiter timeout ends the request without a retry (the breaker answers 504, which Retry does not repeat)
- api-gateway: spring-petclinic-api-gateway/src/main/resources/application.yml:11: CircuitBreaker 'defaultCircuitBreaker' (default-filters[0]) on route customers-service is not modelled; its TimeLimiter is UNKNOWN
- api-gateway: spring-petclinic-api-gateway/src/main/resources/application.yml:11: CircuitBreaker 'defaultCircuitBreaker' (default-filters[0]) on route genai-service is not modelled; its TimeLimiter is UNKNOWN
- api-gateway: spring-petclinic-api-gateway/src/main/resources/application.yml:11: CircuitBreaker 'defaultCircuitBreaker' (default-filters[0]) on route vets-service is not modelled; its TimeLimiter is UNKNOWN
- api-gateway: spring-petclinic-api-gateway/src/main/resources/application.yml:11: CircuitBreaker 'defaultCircuitBreaker' (default-filters[0]) on route visits-service is not modelled; its TimeLimiter is UNKNOWN
- api-gateway: spring-petclinic-api-gateway/src/main/resources/application.yml:45: CircuitBreaker 'genaiCircuitBreaker' (route genai-service filters[1]) on route genai-service is not modelled; its TimeLimiter is UNKNOWN
