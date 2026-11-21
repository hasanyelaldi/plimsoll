# Plimsoll analysis of `spring-petclinic-microservices`

**3 error, 4 warn, 16 info**

## Amplification per service

Limit: 10

| Service | Amplification |
| --- | --- |
| api-gateway | ×1 |
| customers-service | at least ×3 |
| genai-service | ×2 |
| vets-service | at least ×2 |
| visits-service | at least ×3 |

## Findings

| Severity | Rule | Location | Detail |
| --- | --- | --- | --- |
| ERROR | PLM-A3 | api-gateway→customers-service (/api/customer/**) | non-idempotent call retried, 2 tries (on reset) |
| ERROR | PLM-A3 | api-gateway→genai-service | non-idempotent call retried, 2 tries (on reset) |
| ERROR | PLM-A3 | api-gateway→visits-service (/api/visit/**) | non-idempotent call retried, 2 tries (on reset) |
| WARN | PLM-A4 | api-gateway→customers-service (/api/customer/**) | zero backoff |
| WARN | PLM-A4 | api-gateway→genai-service | zero backoff |
| WARN | PLM-A4 | api-gateway→visits-service (/api/visit/**) | zero backoff |
| WARN | PLM-C1 | genai-service | Spring AI retry detected, not modelled; edges from genai-service may carry an unseen retry layer |
| INFO | PLM-A1 | customers-service | at least ×3, limit 10; by failure class at customers-service: ×2 for status-409 or status-429 or status-4xx-other, ×3 for any other class; the real value may be higher |
| INFO | PLM-A1 | vets-service | at least ×2 (genai-service ×2), limit 10; max over 2 alternatives of routes of api-gateway; the real value may be higher |
| INFO | PLM-A1 | visits-service | at least ×3, limit 10; by failure class at visits-service: ×2 for status-409 or status-429 or status-4xx-other, ×3 for any other class; the real value may be higher |
| INFO | PLM-A2a | api-gateway→customers-service (/api/customer/**) | cannot evaluate: api-gateway deadline UNKNOWN |
| INFO | PLM-A2a | api-gateway→genai-service | cannot evaluate: api-gateway deadline UNKNOWN |
| INFO | PLM-A2a | api-gateway→visits-service (/api/visit/**) | cannot evaluate: api-gateway deadline UNKNOWN |
| INFO | PLM-A2b | api-gateway→customers-service (/api/customer/**) | cannot evaluate: customers-service deadline UNKNOWN |
| INFO | PLM-A2b | api-gateway→customers-service (GET /owners/{ownerId}) | cannot evaluate: customers-service deadline UNKNOWN |
| INFO | PLM-A2b | api-gateway→genai-service | cannot evaluate: genai-service deadline UNKNOWN |
| INFO | PLM-A2b | api-gateway→vets-service | cannot evaluate: vets-service deadline UNKNOWN |
| INFO | PLM-A2b | api-gateway→visits-service (/api/visit/**) | cannot evaluate: visits-service deadline UNKNOWN |
| INFO | PLM-A2b | api-gateway→visits-service (GET /pets/visits) | cannot evaluate: visits-service deadline UNKNOWN |
| INFO | PLM-A2b | genai-service→vets-service | cannot evaluate: vets-service deadline UNKNOWN |
| INFO | PLM-A4 | api-gateway→customers-service (/api/customer/**) | no jitter |
| INFO | PLM-A4 | api-gateway→genai-service | no jitter |
| INFO | PLM-A4 | api-gateway→visits-service (/api/visit/**) | no jitter |

## Values used

### ERROR PLM-A3 api-gateway→customers-service (/api/customer/**)

- api-gateway→customers-service (/api/customer/**) idempotent = false [LIBRARY_DEFAULT: POST (customers-service /owners, spring-petclinic-customers-service/src/main/java/org/springframework/samples/petclinic/customers/web/OwnerResource.java:57); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- api-gateway→customers-service (/api/customer/**) layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→customers-service (/api/customer/**) layer 1 (spring-cloud-gateway) retried = not-sent, reset, timeout, status-500, status-502, status-503, status-504, status-5xx-other [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): spring-petclinic-api-gateway/src/main/resources/application.yml:18 (statuses); series not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by oracle), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html; exceptions not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by oracle), https://github.com/spring-cloud/spring-cloud-gateway/blob/v5.0.3/spring-cloud-gateway-server-webflux/src/main/java/org/springframework/cloud/gateway/filter/factory/RetryGatewayFilterFactory.java; spring-petclinic-api-gateway/src/main/resources/application.yml:19 (methods); classes: spring-cloud-gateway 5.0.3 default (verified by doc), https://docs.spring.io/spring-framework/docs/7.0.x/javadoc-api/org/springframework/http/HttpStatus.html; spring-cloud-gateway 5.0.3 default (verified by oracle), https://github.com/hasanyelaldi/plimsoll/blob/main/plimsoll-collectors/src/test/java/io/github/hasanyelaldi/plimsoll/collect/oracle/GatewayRetryOracleTest.java]

### ERROR PLM-A3 api-gateway→genai-service

- api-gateway→genai-service idempotent = false [LIBRARY_DEFAULT: POST (genai-service /chatclient, spring-petclinic-genai-service/src/main/java/org/springframework/samples/petclinic/genai/PetclinicChatClient.java:57); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- api-gateway→genai-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→genai-service layer 1 (spring-cloud-gateway) retried = not-sent, reset, timeout, status-500, status-502, status-503, status-504, status-5xx-other [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): spring-petclinic-api-gateway/src/main/resources/application.yml:18 (statuses); series not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by oracle), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html; exceptions not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by oracle), https://github.com/spring-cloud/spring-cloud-gateway/blob/v5.0.3/spring-cloud-gateway-server-webflux/src/main/java/org/springframework/cloud/gateway/filter/factory/RetryGatewayFilterFactory.java; spring-petclinic-api-gateway/src/main/resources/application.yml:19 (methods); classes: spring-cloud-gateway 5.0.3 default (verified by doc), https://docs.spring.io/spring-framework/docs/7.0.x/javadoc-api/org/springframework/http/HttpStatus.html; spring-cloud-gateway 5.0.3 default (verified by oracle), https://github.com/hasanyelaldi/plimsoll/blob/main/plimsoll-collectors/src/test/java/io/github/hasanyelaldi/plimsoll/collect/oracle/GatewayRetryOracleTest.java]

### ERROR PLM-A3 api-gateway→visits-service (/api/visit/**)

- api-gateway→visits-service (/api/visit/**) idempotent = false [LIBRARY_DEFAULT: POST (visits-service /owners/*/pets/{petId}/visits, spring-petclinic-visits-service/src/main/java/org/springframework/samples/petclinic/visits/web/VisitResource.java:56); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- api-gateway→visits-service (/api/visit/**) layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→visits-service (/api/visit/**) layer 1 (spring-cloud-gateway) retried = not-sent, reset, timeout, status-500, status-502, status-503, status-504, status-5xx-other [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): spring-petclinic-api-gateway/src/main/resources/application.yml:18 (statuses); series not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by oracle), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html; exceptions not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by oracle), https://github.com/spring-cloud/spring-cloud-gateway/blob/v5.0.3/spring-cloud-gateway-server-webflux/src/main/java/org/springframework/cloud/gateway/filter/factory/RetryGatewayFilterFactory.java; spring-petclinic-api-gateway/src/main/resources/application.yml:19 (methods); classes: spring-cloud-gateway 5.0.3 default (verified by doc), https://docs.spring.io/spring-framework/docs/7.0.x/javadoc-api/org/springframework/http/HttpStatus.html; spring-cloud-gateway 5.0.3 default (verified by oracle), https://github.com/hasanyelaldi/plimsoll/blob/main/plimsoll-collectors/src/test/java/io/github/hasanyelaldi/plimsoll/collect/oracle/GatewayRetryOracleTest.java]

### WARN PLM-A4 api-gateway→customers-service (/api/customer/**)

- api-gateway→customers-service (/api/customer/**) layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→customers-service (/api/customer/**) layer 1 (spring-cloud-gateway) backoff = 0 ms [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]

### WARN PLM-A4 api-gateway→genai-service

- api-gateway→genai-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→genai-service layer 1 (spring-cloud-gateway) backoff = 0 ms [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]

### WARN PLM-A4 api-gateway→visits-service (/api/visit/**)

- api-gateway→visits-service (/api/visit/**) layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→visits-service (/api/visit/**) layer 1 (spring-cloud-gateway) backoff = 0 ms [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]

### WARN PLM-C1 genai-service

- genai-service Spring AI retry = dependency org.springframework.ai:spring-ai-starter-model-* [EXPLICIT: spring-petclinic-genai-service/pom.xml:27; https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html]

### INFO PLM-A1 customers-service

- api-gateway→customers-service (/api/customer/**) layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→customers-service (/api/customer/**) choice = routes of api-gateway [EXPLICIT: spring-petclinic-api-gateway/src/main/resources/application.yml:21: a request takes the first route whose predicates match (ADR 0012)]
- api-gateway→customers-service (GET /owners/{ownerId}) layer 1 (http-client) totalAttempts = 1 [LIBRARY_DEFAULT: @LoadBalanced WebClient (@LoadBalanced WebClient.Builder bean at spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:60, injected at parameter webClientBuilder at spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/application/CustomersServiceClient.java:31): LoadBalancer retry off; spring.cloud.loadbalancer.retry.enabled not set; spring-cloud-loadbalancer 5.0.0, 5.0.3 default (verified by oracle), https://github.com/spring-cloud/spring-cloud-commons/blob/v5.0.3/spring-cloud-commons/src/main/java/org/springframework/cloud/client/loadbalancer/reactive/ReactorLoadBalancerClientAutoConfiguration.java]
- api-gateway→customers-service (GET /owners/{ownerId}) fanout = UNKNOWN [UNKNOWN: found in code at spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/application/CustomersServiceClient.java:36; no edges-hint.yaml entry gives its fanout (ADR 0009)]
- api-gateway→customers-service (/api/customer/**) layer 1 (spring-cloud-gateway) retried = not-sent, reset, timeout, status-500, status-502, status-503, status-504, status-5xx-other [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): spring-petclinic-api-gateway/src/main/resources/application.yml:18 (statuses); series not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by oracle), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html; exceptions not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by oracle), https://github.com/spring-cloud/spring-cloud-gateway/blob/v5.0.3/spring-cloud-gateway-server-webflux/src/main/java/org/springframework/cloud/gateway/filter/factory/RetryGatewayFilterFactory.java; spring-petclinic-api-gateway/src/main/resources/application.yml:19 (methods); classes: spring-cloud-gateway 5.0.3 default (verified by doc), https://docs.spring.io/spring-framework/docs/7.0.x/javadoc-api/org/springframework/http/HttpStatus.html; spring-cloud-gateway 5.0.3 default (verified by oracle), https://github.com/hasanyelaldi/plimsoll/blob/main/plimsoll-collectors/src/test/java/io/github/hasanyelaldi/plimsoll/collect/oracle/GatewayRetryOracleTest.java]
- **depends-on-unknown: api-gateway→customers-service (GET /owners/{ownerId}) fanout**

### INFO PLM-A1 vets-service

- api-gateway→vets-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15) repeats only POST (spring-petclinic-api-gateway/src/main/resources/application.yml:19 (methods)); route vets-service serves vets-service GET /vets at spring-petclinic-vets-service/src/main/java/org/springframework/samples/petclinic/vets/web/VetResource.java:44]
- api-gateway→vets-service choice = routes of api-gateway [EXPLICIT: spring-petclinic-api-gateway/src/main/resources/application.yml:21: a request takes the first route whose predicates match (ADR 0012)]
- api-gateway→genai-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→genai-service choice = routes of api-gateway [EXPLICIT: spring-petclinic-api-gateway/src/main/resources/application.yml:21: a request takes the first route whose predicates match (ADR 0012)]
- genai-service→vets-service layer 1 (http-client) totalAttempts = 1 [LIBRARY_DEFAULT: @LoadBalanced WebClient (@LoadBalanced WebClient.Builder bean at spring-petclinic-genai-service/src/main/java/org/springframework/samples/petclinic/genai/AIBeanConfiguration.java:26, injected at parameter webClientBuilder at spring-petclinic-genai-service/src/main/java/org/springframework/samples/petclinic/genai/VectorStoreController.java:44): LoadBalancer retry off; spring.cloud.loadbalancer.retry.enabled not set; spring-cloud-loadbalancer 5.0.0, 5.0.3 default (verified by oracle), https://github.com/spring-cloud/spring-cloud-commons/blob/v5.0.3/spring-cloud-commons/src/main/java/org/springframework/cloud/client/loadbalancer/reactive/ReactorLoadBalancerClientAutoConfiguration.java]
- genai-service→vets-service fanout = UNKNOWN [UNKNOWN: found in code at spring-petclinic-genai-service/src/main/java/org/springframework/samples/petclinic/genai/VectorStoreController.java:68; no edges-hint.yaml entry gives its fanout (ADR 0009)]
- **depends-on-unknown: genai-service→vets-service fanout**

### INFO PLM-A1 visits-service

- api-gateway→visits-service (/api/visit/**) layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→visits-service (/api/visit/**) choice = routes of api-gateway [EXPLICIT: spring-petclinic-api-gateway/src/main/resources/application.yml:21: a request takes the first route whose predicates match (ADR 0012)]
- api-gateway→visits-service (GET /pets/visits) layer 1 (resilience4j) totalAttempts = 1 [EXPLICIT: Spring Cloud CircuitBreaker 'getOwnerDetails' (spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/boundary/web/ApiGatewayController.java:60) wraps visitsServiceClient.getVisitsForPets(owner.getPetIds()), which runs it once (SpringCloudCircuitBreakerOracleTest)]
- api-gateway→visits-service (GET /pets/visits) layer 2 (http-client) totalAttempts = 1 [LIBRARY_DEFAULT: @LoadBalanced WebClient (@LoadBalanced WebClient.Builder bean at spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:60, injected at parameter webClientBuilder at spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/application/VisitsServiceClient.java:38): LoadBalancer retry off; spring.cloud.loadbalancer.retry.enabled not set; spring-cloud-loadbalancer 5.0.0, 5.0.3 default (verified by oracle), https://github.com/spring-cloud/spring-cloud-commons/blob/v5.0.3/spring-cloud-commons/src/main/java/org/springframework/cloud/client/loadbalancer/reactive/ReactorLoadBalancerClientAutoConfiguration.java]
- api-gateway→visits-service (GET /pets/visits) fanout = UNKNOWN [UNKNOWN: found in code at spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/application/VisitsServiceClient.java:44; no edges-hint.yaml entry gives its fanout (ADR 0009)]
- api-gateway→visits-service (/api/visit/**) layer 1 (spring-cloud-gateway) retried = not-sent, reset, timeout, status-500, status-502, status-503, status-504, status-5xx-other [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): spring-petclinic-api-gateway/src/main/resources/application.yml:18 (statuses); series not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by oracle), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html; exceptions not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by oracle), https://github.com/spring-cloud/spring-cloud-gateway/blob/v5.0.3/spring-cloud-gateway-server-webflux/src/main/java/org/springframework/cloud/gateway/filter/factory/RetryGatewayFilterFactory.java; spring-petclinic-api-gateway/src/main/resources/application.yml:19 (methods); classes: spring-cloud-gateway 5.0.3 default (verified by doc), https://docs.spring.io/spring-framework/docs/7.0.x/javadoc-api/org/springframework/http/HttpStatus.html; spring-cloud-gateway 5.0.3 default (verified by oracle), https://github.com/hasanyelaldi/plimsoll/blob/main/plimsoll-collectors/src/test/java/io/github/hasanyelaldi/plimsoll/collect/oracle/GatewayRetryOracleTest.java]
- api-gateway→visits-service (GET /pets/visits) layer 2 (http-client) gives up as = not-sent as not-sent, status-503; reset as reset; timeout as timeout [LIBRARY_DEFAULT: spring-cloud-loadbalancer 5.0.3 default (verified by oracle), https://github.com/hasanyelaldi/plimsoll/blob/main/plimsoll-collectors/src/test/java/io/github/hasanyelaldi/plimsoll/collect/oracle/LoadBalancerRetryOracleTest.java]
- **depends-on-unknown: api-gateway→visits-service (GET /pets/visits) fanout**

### INFO PLM-A2a api-gateway→customers-service (/api/customer/**)

- api-gateway→customers-service (/api/customer/**) layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→customers-service (/api/customer/**) layer 1 (spring-cloud-gateway) perTryTimeout = unlimited [LIBRARY_DEFAULT: spring.cloud.gateway.server.webflux.httpclient.response-timeout not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by oracle), https://github.com/spring-cloud/spring-cloud-gateway/blob/v5.0.3/spring-cloud-gateway-server-webflux/src/main/java/org/springframework/cloud/gateway/config/HttpClientProperties.java]
- api-gateway→customers-service (/api/customer/**) layer 1 (spring-cloud-gateway) backoff = 0 ms [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]
- api-gateway→customers-service (/api/customer/**) layer 1 (spring-cloud-gateway) multiplier = 1.0 [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]
- api-gateway→customers-service (/api/customer/**) layer 1 (spring-cloud-gateway) maxBackoff = 0 ms [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]
- api-gateway deadline = UNKNOWN [UNKNOWN: api-gateway has no edges hint; set 'deadline:' in the edges-hint.yaml of api-gateway (ADR 0009)]
- **depends-on-unknown: api-gateway deadline**

### INFO PLM-A2a api-gateway→genai-service

- api-gateway→genai-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→genai-service layer 1 (spring-cloud-gateway) perTryTimeout = 10 s [EXPLICIT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:88: timeoutDuration(Duration.ofSeconds(10)) in configureDefault of @Bean defaultCustomizer (TimeLimiter of Spring Cloud CircuitBreaker 'genaiCircuitBreaker' takes the Customizer's configureDefault)]
- api-gateway→genai-service layer 1 (spring-cloud-gateway) backoff = 0 ms [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]
- api-gateway→genai-service layer 1 (spring-cloud-gateway) multiplier = 1.0 [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]
- api-gateway→genai-service layer 1 (spring-cloud-gateway) maxBackoff = 0 ms [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]
- api-gateway deadline = UNKNOWN [UNKNOWN: api-gateway has no edges hint; set 'deadline:' in the edges-hint.yaml of api-gateway (ADR 0009)]
- **depends-on-unknown: api-gateway deadline**

### INFO PLM-A2a api-gateway→visits-service (/api/visit/**)

- api-gateway→visits-service (/api/visit/**) layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→visits-service (/api/visit/**) layer 1 (spring-cloud-gateway) perTryTimeout = unlimited [LIBRARY_DEFAULT: spring.cloud.gateway.server.webflux.httpclient.response-timeout not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by oracle), https://github.com/spring-cloud/spring-cloud-gateway/blob/v5.0.3/spring-cloud-gateway-server-webflux/src/main/java/org/springframework/cloud/gateway/config/HttpClientProperties.java]
- api-gateway→visits-service (/api/visit/**) layer 1 (spring-cloud-gateway) backoff = 0 ms [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]
- api-gateway→visits-service (/api/visit/**) layer 1 (spring-cloud-gateway) multiplier = 1.0 [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]
- api-gateway→visits-service (/api/visit/**) layer 1 (spring-cloud-gateway) maxBackoff = 0 ms [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): no backoff, retries follow each other without waiting; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]
- api-gateway deadline = UNKNOWN [UNKNOWN: api-gateway has no edges hint; set 'deadline:' in the edges-hint.yaml of api-gateway (ADR 0009)]
- **depends-on-unknown: api-gateway deadline**

### INFO PLM-A2b api-gateway→customers-service (/api/customer/**)

- customers-service deadline = UNKNOWN [UNKNOWN: customers-service has no edges hint; set 'deadline:' in the edges-hint.yaml of customers-service (ADR 0009)]
- api-gateway→customers-service (/api/customer/**) layer 1 (spring-cloud-gateway) perTryTimeout = unlimited [LIBRARY_DEFAULT: spring.cloud.gateway.server.webflux.httpclient.response-timeout not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by oracle), https://github.com/spring-cloud/spring-cloud-gateway/blob/v5.0.3/spring-cloud-gateway-server-webflux/src/main/java/org/springframework/cloud/gateway/config/HttpClientProperties.java]
- api-gateway→customers-service (/api/customer/**) layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- **depends-on-unknown: customers-service deadline**

### INFO PLM-A2b api-gateway→customers-service (GET /owners/{ownerId})

- customers-service deadline = UNKNOWN [UNKNOWN: customers-service has no edges hint; set 'deadline:' in the edges-hint.yaml of customers-service (ADR 0009)]
- api-gateway→customers-service (GET /owners/{ownerId}) layer 1 (http-client) perTryTimeout = unlimited [LIBRARY_DEFAULT: @LoadBalanced WebClient (@LoadBalanced WebClient.Builder bean at spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:60, injected at parameter webClientBuilder at spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/application/CustomersServiceClient.java:31): response timeout of WebClient.builder() with Reactor Netty; spring-web 7.0.2, 7.0.9 default (verified by oracle), https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-web/src/main/java/org/springframework/http/client/reactive/ReactorClientHttpConnector.java]
- api-gateway→customers-service (GET /owners/{ownerId}) layer 1 (http-client) totalAttempts = 1 [LIBRARY_DEFAULT: @LoadBalanced WebClient (@LoadBalanced WebClient.Builder bean at spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:60, injected at parameter webClientBuilder at spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/application/CustomersServiceClient.java:31): LoadBalancer retry off; spring.cloud.loadbalancer.retry.enabled not set; spring-cloud-loadbalancer 5.0.0, 5.0.3 default (verified by oracle), https://github.com/spring-cloud/spring-cloud-commons/blob/v5.0.3/spring-cloud-commons/src/main/java/org/springframework/cloud/client/loadbalancer/reactive/ReactorLoadBalancerClientAutoConfiguration.java]
- **depends-on-unknown: customers-service deadline**

### INFO PLM-A2b api-gateway→genai-service

- genai-service deadline = UNKNOWN [UNKNOWN: genai-service has no edges hint; set 'deadline:' in the edges-hint.yaml of genai-service (ADR 0009)]
- api-gateway→genai-service layer 1 (spring-cloud-gateway) perTryTimeout = 10 s [EXPLICIT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:88: timeoutDuration(Duration.ofSeconds(10)) in configureDefault of @Bean defaultCustomizer (TimeLimiter of Spring Cloud CircuitBreaker 'genaiCircuitBreaker' takes the Customizer's configureDefault)]
- api-gateway→genai-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- **depends-on-unknown: genai-service deadline**

### INFO PLM-A2b api-gateway→vets-service

- vets-service deadline = UNKNOWN [UNKNOWN: vets-service has no edges hint; set 'deadline:' in the edges-hint.yaml of vets-service (ADR 0009)]
- api-gateway→vets-service layer 1 (spring-cloud-gateway) perTryTimeout = unlimited [LIBRARY_DEFAULT: spring.cloud.gateway.server.webflux.httpclient.response-timeout not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by oracle), https://github.com/spring-cloud/spring-cloud-gateway/blob/v5.0.3/spring-cloud-gateway-server-webflux/src/main/java/org/springframework/cloud/gateway/config/HttpClientProperties.java]
- api-gateway→vets-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15) repeats only POST (spring-petclinic-api-gateway/src/main/resources/application.yml:19 (methods)); route vets-service serves vets-service GET /vets at spring-petclinic-vets-service/src/main/java/org/springframework/samples/petclinic/vets/web/VetResource.java:44]
- **depends-on-unknown: vets-service deadline**

### INFO PLM-A2b api-gateway→visits-service (/api/visit/**)

- visits-service deadline = UNKNOWN [UNKNOWN: visits-service has no edges hint; set 'deadline:' in the edges-hint.yaml of visits-service (ADR 0009)]
- api-gateway→visits-service (/api/visit/**) layer 1 (spring-cloud-gateway) perTryTimeout = unlimited [LIBRARY_DEFAULT: spring.cloud.gateway.server.webflux.httpclient.response-timeout not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by oracle), https://github.com/spring-cloud/spring-cloud-gateway/blob/v5.0.3/spring-cloud-gateway-server-webflux/src/main/java/org/springframework/cloud/gateway/config/HttpClientProperties.java]
- api-gateway→visits-service (/api/visit/**) layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- **depends-on-unknown: visits-service deadline**

### INFO PLM-A2b api-gateway→visits-service (GET /pets/visits)

- visits-service deadline = UNKNOWN [UNKNOWN: visits-service has no edges hint; set 'deadline:' in the edges-hint.yaml of visits-service (ADR 0009)]
- api-gateway→visits-service (GET /pets/visits) layer 1 (resilience4j) perTryTimeout = 10 s [EXPLICIT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:88: timeoutDuration(Duration.ofSeconds(10)) in configureDefault of @Bean defaultCustomizer (TimeLimiter of Spring Cloud CircuitBreaker 'getOwnerDetails' takes the Customizer's configureDefault)]
- api-gateway→visits-service (GET /pets/visits) layer 2 (http-client) perTryTimeout = unlimited [LIBRARY_DEFAULT: @LoadBalanced WebClient (@LoadBalanced WebClient.Builder bean at spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:60, injected at parameter webClientBuilder at spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/application/VisitsServiceClient.java:38): response timeout of WebClient.builder() with Reactor Netty; spring-web 7.0.2, 7.0.9 default (verified by oracle), https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-web/src/main/java/org/springframework/http/client/reactive/ReactorClientHttpConnector.java]
- api-gateway→visits-service (GET /pets/visits) layer 1 (resilience4j) totalAttempts = 1 [EXPLICIT: Spring Cloud CircuitBreaker 'getOwnerDetails' (spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/boundary/web/ApiGatewayController.java:60) wraps visitsServiceClient.getVisitsForPets(owner.getPetIds()), which runs it once (SpringCloudCircuitBreakerOracleTest)]
- api-gateway→visits-service (GET /pets/visits) layer 2 (http-client) totalAttempts = 1 [LIBRARY_DEFAULT: @LoadBalanced WebClient (@LoadBalanced WebClient.Builder bean at spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:60, injected at parameter webClientBuilder at spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/application/VisitsServiceClient.java:38): LoadBalancer retry off; spring.cloud.loadbalancer.retry.enabled not set; spring-cloud-loadbalancer 5.0.0, 5.0.3 default (verified by oracle), https://github.com/spring-cloud/spring-cloud-commons/blob/v5.0.3/spring-cloud-commons/src/main/java/org/springframework/cloud/client/loadbalancer/reactive/ReactorLoadBalancerClientAutoConfiguration.java]
- **depends-on-unknown: visits-service deadline**

### INFO PLM-A2b genai-service→vets-service

- vets-service deadline = UNKNOWN [UNKNOWN: vets-service has no edges hint; set 'deadline:' in the edges-hint.yaml of vets-service (ADR 0009)]
- genai-service→vets-service layer 1 (http-client) perTryTimeout = unlimited [LIBRARY_DEFAULT: @LoadBalanced WebClient (@LoadBalanced WebClient.Builder bean at spring-petclinic-genai-service/src/main/java/org/springframework/samples/petclinic/genai/AIBeanConfiguration.java:26, injected at parameter webClientBuilder at spring-petclinic-genai-service/src/main/java/org/springframework/samples/petclinic/genai/VectorStoreController.java:44): response timeout of WebClient.builder() with Reactor Netty; spring-web 7.0.2, 7.0.9 default (verified by oracle), https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-web/src/main/java/org/springframework/http/client/reactive/ReactorClientHttpConnector.java]
- genai-service→vets-service layer 1 (http-client) totalAttempts = 1 [LIBRARY_DEFAULT: @LoadBalanced WebClient (@LoadBalanced WebClient.Builder bean at spring-petclinic-genai-service/src/main/java/org/springframework/samples/petclinic/genai/AIBeanConfiguration.java:26, injected at parameter webClientBuilder at spring-petclinic-genai-service/src/main/java/org/springframework/samples/petclinic/genai/VectorStoreController.java:44): LoadBalancer retry off; spring.cloud.loadbalancer.retry.enabled not set; spring-cloud-loadbalancer 5.0.0, 5.0.3 default (verified by oracle), https://github.com/spring-cloud/spring-cloud-commons/blob/v5.0.3/spring-cloud-commons/src/main/java/org/springframework/cloud/client/loadbalancer/reactive/ReactorLoadBalancerClientAutoConfiguration.java]
- **depends-on-unknown: vets-service deadline**

### INFO PLM-A4 api-gateway→customers-service (/api/customer/**)

- api-gateway→customers-service (/api/customer/**) layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→customers-service (/api/customer/**) layer 1 (spring-cloud-gateway) jitter = false [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): jitter not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]

### INFO PLM-A4 api-gateway→genai-service

- api-gateway→genai-service layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→genai-service layer 1 (spring-cloud-gateway) jitter = false [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): jitter not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]

### INFO PLM-A4 api-gateway→visits-service (/api/visit/**)

- api-gateway→visits-service (/api/visit/**) layer 1 (spring-cloud-gateway) totalAttempts = 2 [EXPLICIT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): retries 1 + 1; spring-petclinic-api-gateway/src/main/resources/application.yml:17]
- api-gateway→visits-service (/api/visit/**) layer 1 (spring-cloud-gateway) jitter = false [LIBRARY_DEFAULT: Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15): jitter not set; spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc), https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html]

## Circuit breakers

### api-gateway→vets-service spring-cloud-gateway

- name = defaultCircuitBreaker [EXPLICIT: spring-petclinic-api-gateway/src/main/resources/application.yml:13]
- position = wraps-all-tries [EXPLICIT: CircuitBreaker (default-filters[0], spring-petclinic-api-gateway/src/main/resources/application.yml:11) is listed before Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15); filters run in list order, default-filters first]
- failureRateThreshold = 50% [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'defaultCircuitBreaker' takes the Customizer's configureDefault)]
- slidingWindowType = count-based [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'defaultCircuitBreaker' takes the Customizer's configureDefault)]
- slidingWindowSize = 100 calls [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'defaultCircuitBreaker' takes the Customizer's configureDefault)]
- minimumNumberOfCalls = 100 [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'defaultCircuitBreaker' takes the Customizer's configureDefault)]
- waitDurationInOpenState = 60 s [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'defaultCircuitBreaker' takes the Customizer's configureDefault)]

### api-gateway→visits-service (/api/visit/**) spring-cloud-gateway

- name = defaultCircuitBreaker [EXPLICIT: spring-petclinic-api-gateway/src/main/resources/application.yml:13]
- position = wraps-all-tries [EXPLICIT: CircuitBreaker (default-filters[0], spring-petclinic-api-gateway/src/main/resources/application.yml:11) is listed before Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15); filters run in list order, default-filters first]
- failureRateThreshold = 50% [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'defaultCircuitBreaker' takes the Customizer's configureDefault)]
- slidingWindowType = count-based [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'defaultCircuitBreaker' takes the Customizer's configureDefault)]
- slidingWindowSize = 100 calls [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'defaultCircuitBreaker' takes the Customizer's configureDefault)]
- minimumNumberOfCalls = 100 [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'defaultCircuitBreaker' takes the Customizer's configureDefault)]
- waitDurationInOpenState = 60 s [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'defaultCircuitBreaker' takes the Customizer's configureDefault)]

### api-gateway→customers-service (/api/customer/**) spring-cloud-gateway

- name = defaultCircuitBreaker [EXPLICIT: spring-petclinic-api-gateway/src/main/resources/application.yml:13]
- position = wraps-all-tries [EXPLICIT: CircuitBreaker (default-filters[0], spring-petclinic-api-gateway/src/main/resources/application.yml:11) is listed before Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15); filters run in list order, default-filters first]
- failureRateThreshold = 50% [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'defaultCircuitBreaker' takes the Customizer's configureDefault)]
- slidingWindowType = count-based [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'defaultCircuitBreaker' takes the Customizer's configureDefault)]
- slidingWindowSize = 100 calls [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'defaultCircuitBreaker' takes the Customizer's configureDefault)]
- minimumNumberOfCalls = 100 [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'defaultCircuitBreaker' takes the Customizer's configureDefault)]
- waitDurationInOpenState = 60 s [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'defaultCircuitBreaker' takes the Customizer's configureDefault)]

### api-gateway→genai-service spring-cloud-gateway

- name = defaultCircuitBreaker [EXPLICIT: spring-petclinic-api-gateway/src/main/resources/application.yml:13]
- position = wraps-all-tries [EXPLICIT: CircuitBreaker (default-filters[0], spring-petclinic-api-gateway/src/main/resources/application.yml:11) is listed before Retry (default-filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:15); filters run in list order, default-filters first]
- failureRateThreshold = 50% [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'defaultCircuitBreaker' takes the Customizer's configureDefault)]
- slidingWindowType = count-based [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'defaultCircuitBreaker' takes the Customizer's configureDefault)]
- slidingWindowSize = 100 calls [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'defaultCircuitBreaker' takes the Customizer's configureDefault)]
- minimumNumberOfCalls = 100 [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'defaultCircuitBreaker' takes the Customizer's configureDefault)]
- waitDurationInOpenState = 60 s [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'defaultCircuitBreaker' takes the Customizer's configureDefault)]

### api-gateway→visits-service (GET /pets/visits) resilience4j

- name = getOwnerDetails [EXPLICIT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/boundary/web/ApiGatewayController.java:60]
- position = UNKNOWN [UNKNOWN: Spring Cloud CircuitBreaker 'getOwnerDetails' (spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/boundary/web/ApiGatewayController.java:60) wraps visitsServiceClient.getVisitsForPets(owner.getPetIds()): the layer tries once, so both positions count the same calls]
- failureRateThreshold = 50% [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'getOwnerDetails' takes the Customizer's configureDefault)]
- slidingWindowType = count-based [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'getOwnerDetails' takes the Customizer's configureDefault)]
- slidingWindowSize = 100 calls [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'getOwnerDetails' takes the Customizer's configureDefault)]
- minimumNumberOfCalls = 100 [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'getOwnerDetails' takes the Customizer's configureDefault)]
- waitDurationInOpenState = 60 s [LIBRARY_DEFAULT: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/ApiGatewayApplication.java:87: CircuitBreakerConfig.ofDefaults() in configureDefault of @Bean defaultCustomizer; resilience4j-circuitbreaker 2.4.0 default (verified by oracle), https://resilience4j.readme.io/docs/circuitbreaker (Spring Cloud CircuitBreaker 'getOwnerDetails' takes the Customizer's configureDefault)]

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
| api-gateway→customers-service | GET /owners/{ownerId} | resilience4j, http-client | http-client |
| api-gateway→genai-service | /api/genai/** | spring-cloud-gateway | spring-cloud-gateway |
| api-gateway→vets-service | /api/vet/** | spring-cloud-gateway | spring-cloud-gateway |
| api-gateway→visits-service | /api/visit/** | spring-cloud-gateway | spring-cloud-gateway |
| api-gateway→visits-service | GET /pets/visits | resilience4j, http-client | resilience4j, http-client |
| genai-service→vets-service | GET /vets | resilience4j, http-client | http-client |

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
- api-gateway: route customers-service: a TimeLimiter timeout of CircuitBreaker 'defaultCircuitBreaker' ends the request without a retry (the breaker answers 504, which Retry does not repeat)
- api-gateway: route customers-service: the TimeLimiter (10 s) of CircuitBreaker 'defaultCircuitBreaker' (default-filters[0], spring-petclinic-api-gateway/src/main/resources/application.yml:11) bounds all tries of Retry (default-filters[1]), so it caps that layer's overall timeout
- api-gateway: route genai-service: a TimeLimiter timeout of CircuitBreaker 'defaultCircuitBreaker' ends the request without a retry (the breaker answers 504, which Retry does not repeat)
- api-gateway: route genai-service: the TimeLimiter (10 s) of CircuitBreaker 'defaultCircuitBreaker' (default-filters[0], spring-petclinic-api-gateway/src/main/resources/application.yml:11) bounds all tries of Retry (default-filters[1]), so it caps that layer's overall timeout
- api-gateway: route genai-service: the TimeLimiter (10 s) of CircuitBreaker 'genaiCircuitBreaker' (route genai-service filters[1], spring-petclinic-api-gateway/src/main/resources/application.yml:45) bounds each try of Retry (default-filters[1]), so it caps the per-try timeout
- api-gateway: route vets-service: a TimeLimiter timeout of CircuitBreaker 'defaultCircuitBreaker' ends the request without a retry (the breaker answers 504, which Retry does not repeat)
- api-gateway: route vets-service: the TimeLimiter (10 s) of CircuitBreaker 'defaultCircuitBreaker' (default-filters[0], spring-petclinic-api-gateway/src/main/resources/application.yml:11) bounds all tries of Retry (default-filters[1]), so it caps that layer's overall timeout
- api-gateway: route visits-service: a TimeLimiter timeout of CircuitBreaker 'defaultCircuitBreaker' ends the request without a retry (the breaker answers 504, which Retry does not repeat)
- api-gateway: route visits-service: the TimeLimiter (10 s) of CircuitBreaker 'defaultCircuitBreaker' (default-filters[0], spring-petclinic-api-gateway/src/main/resources/application.yml:11) bounds all tries of Retry (default-filters[1]), so it caps that layer's overall timeout
- api-gateway: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/application/VisitsServiceClient.java:44: the TimeLimiter (10 s) of Spring Cloud CircuitBreaker 'getOwnerDetails' (spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/boundary/web/ApiGatewayController.java:60) wraps visitsServiceClient.getVisitsForPets(owner.getPetIds()) bounds the request to visits-service
- api-gateway: spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/boundary/web/ApiGatewayController.java:60: the run of Spring Cloud CircuitBreaker 'getOwnerDetails' (spring-petclinic-api-gateway/src/main/java/org/springframework/samples/petclinic/api/boundary/web/ApiGatewayController.java:60) wraps visitsServiceClient.getVisitsForPets(owner.getPetIds()) has a fallback (throwable -> emptyVisitsForPets()), so a failure there is answered, not passed on; the analysis still counts it as passed on (fail-close)
- api-gateway: spring-petclinic-api-gateway/src/main/resources/application.yml:45: CircuitBreaker 'genaiCircuitBreaker' (route genai-service filters[1]) on route genai-service is a further breaker; a layer carries one (ADR 0015), so only CircuitBreaker 'defaultCircuitBreaker' (default-filters[0]) is listed
- genai-service: spring-petclinic-genai-service/src/main/java/org/springframework/samples/petclinic/genai/AIDataProvider.java:44: org.springframework.samples.petclinic.genai.AIDataProvider#getAllOwners calls GET {?}/owners through RestClient with a host computed at run time (getCustomerServiceUri() + "/owners"); not an edge
- genai-service: spring-petclinic-genai-service/src/main/java/org/springframework/samples/petclinic/genai/AIDataProvider.java:72: org.springframework.samples.petclinic.genai.AIDataProvider#addPetToOwner calls POST {?}/owners/{ownerId}/pets through RestClient with a host computed at run time (getCustomerServiceUri() + "/owners/" + ownerId + "/pets"); not an edge
- genai-service: spring-petclinic-genai-service/src/main/java/org/springframework/samples/petclinic/genai/AIDataProvider.java:81: org.springframework.samples.petclinic.genai.AIDataProvider#addOwnerToPetclinic calls POST {?}/owners through RestClient with a host computed at run time (getCustomerServiceUri() + "/owners"); not an edge
