package io.github.hasanyelaldi.plimsoll.collect.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Oracle for Spring Cloud LoadBalancer retries on a {@code @LoadBalanced WebClient.Builder}, the client
 * spring-petclinic-microservices uses. Two instances of {@code backend} come from the simple discovery client; the
 * exchange function behind the load-balancer filter records every request and fails it, so the record counts the
 * tries. No network, no waiting: backoff is disabled by default.
 */
class LoadBalancerRetryOracleTest {

    /** Records the URI of every try and fails it with an IOException, or answers with {@code status}. */
    static final class Backend {
        final List<URI> tries = new CopyOnWriteArrayList<>();
        HttpStatus status;

        ExchangeFunction exchange() {
            return request -> {
                tries.add(request.url());
                return status == null
                        ? Mono.error(new IOException("connection refused"))
                        : Mono.just(ClientResponse.create(status).build());
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class App {
        @Bean
        Backend backend() {
            return new Backend();
        }

        @Bean
        @LoadBalanced
        WebClient.Builder loadBalancedWebClientBuilder(Backend backend) {
            return WebClient.builder().exchangeFunction(backend.exchange());
        }
    }

    private static List<URI> tries(HttpMethod method, HttpStatus status, String... extraProperties) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class)
                .web(WebApplicationType.NONE)
                .properties(GatewayRetryOracleTest.GATEWAY_OFF)
                .properties(
                        "spring.main.banner-mode=off",
                        "spring.config.location=optional:classpath:none.yml",
                        "spring.cloud.compatibility-verifier.enabled=false",
                        "spring.cloud.loadbalancer.cache.enabled=false",
                        "spring.cloud.discovery.client.simple.instances.backend[0].uri=http://backend-1:8080",
                        "spring.cloud.discovery.client.simple.instances.backend[1].uri=http://backend-2:8080")
                .properties(extraProperties)
                .run()) {
            Backend backend = context.getBean(Backend.class);
            backend.status = status;
            WebClient client = context.getBean(WebClient.Builder.class).build();
            client.method(method).uri("http://backend/owners/1").retrieve().toBodilessEntity()
                    .onErrorResume(e -> Mono.empty())
                    .block(Duration.ofSeconds(10));
            return backend.tries;
        }
    }

    @Test
    void noRetryUnlessRetryIsEnabledExplicitly() {
        assertThat(tries(HttpMethod.GET, null)).hasSize(1);
    }

    @Test
    void enabledRetryTriesTheNextInstanceOnceForGet() {
        List<URI> tries = tries(HttpMethod.GET, null, "spring.cloud.loadbalancer.retry.enabled=true");
        assertThat(tries).hasSize(2);
        assertThat(tries.get(0).getHost()).isNotEqualTo(tries.get(1).getHost());
    }

    @Test
    void exceptionsAreRetriedWhateverTheMethod() {
        // canRetryOnMethod (GET or retry-on-all-operations) is consulted for status codes only
        assertThat(tries(HttpMethod.POST, null, "spring.cloud.loadbalancer.retry.enabled=true")).hasSize(2);
    }

    @Test
    void totalTriesAreSameInstanceTriesTimesNextInstanceTries() {
        assertThat(tries(HttpMethod.GET, null, "spring.cloud.loadbalancer.retry.enabled=true",
                "spring.cloud.loadbalancer.retry.max-retries-on-same-service-instance=1",
                "spring.cloud.loadbalancer.retry.max-retries-on-next-service-instance=2")).hasSize(2 * 3);
    }

    @Test
    void statusCodesAreNotRetriedByDefault() {
        assertThat(tries(HttpMethod.GET, HttpStatus.SERVICE_UNAVAILABLE, "spring.cloud.loadbalancer.retry.enabled=true"))
                .hasSize(1);
    }

    @Test
    void retryableStatusCodesAreRetriedForGetOnly() {
        String[] props = {"spring.cloud.loadbalancer.retry.enabled=true",
                "spring.cloud.loadbalancer.retry.retryable-status-codes=503"};
        assertThat(tries(HttpMethod.GET, HttpStatus.SERVICE_UNAVAILABLE, props)).hasSize(2);
        assertThat(tries(HttpMethod.POST, HttpStatus.SERVICE_UNAVAILABLE, props)).hasSize(1);
    }

    @Test
    void retryOnAllOperationsRetriesStatusCodesForPost() {
        assertThat(tries(HttpMethod.POST, HttpStatus.SERVICE_UNAVAILABLE, "spring.cloud.loadbalancer.retry.enabled=true",
                "spring.cloud.loadbalancer.retry.retryable-status-codes=503",
                "spring.cloud.loadbalancer.retry.retry-on-all-operations=true")).hasSize(2);
    }
}
