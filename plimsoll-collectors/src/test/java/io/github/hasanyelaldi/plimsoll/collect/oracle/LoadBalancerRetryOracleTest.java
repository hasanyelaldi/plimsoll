package io.github.hasanyelaldi.plimsoll.collect.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.handler.timeout.ReadTimeoutException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

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
        /** When set, every try fails with it instead of the plain IOException. */
        Throwable error;

        ExchangeFunction exchange() {
            return request -> {
                tries.add(request.url());
                if (status != null) {
                    return Mono.just(ClientResponse.create(status).build());
                }
                return Mono.error(error != null ? error : new IOException("connection refused"));
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

    private record Outcome(int tries, Throwable error, HttpStatus status) {}

    /** One GET through the load-balanced client whose tries fail with {@code error}; what the caller gets back. */
    private static Outcome call(String service, Throwable error, String... extraProperties) {
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
            backend.error = error;
            WebClient client = context.getBean(WebClient.Builder.class).build();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            HttpStatus status = client.get().uri("http://" + service + "/owners/1")
                    .exchangeToMono(r -> Mono.just(HttpStatus.valueOf(r.statusCode().value())))
                    .doOnError(failure::set).onErrorResume(e -> Mono.empty())
                    .block(Duration.ofSeconds(10));
            return new Outcome(backend.tries.size(), failure.get(), status);
        }
    }

    /** What the Reactor Netty connector emits: the connection failure wrapped in a WebClientRequestException. */
    private static Throwable requestException(Throwable cause) {
        return new WebClientRequestException(cause, HttpMethod.GET, URI.create("http://backend-1:8080/owners/1"),
                new HttpHeaders());
    }

    private static final String ON = "spring.cloud.loadbalancer.retry.enabled=true";

    // ---- failure classes (ADR 0014) ---------------------------------------

    /** The retryable exceptions are checked on the error and on its cause, so the connector's wrapper is retried. */
    @Test
    void connectionFailuresAndResetsAreRetriedThroughTheRequestException() {
        assertThat(call("backend", requestException(new ConnectException("Connection refused")), ON).tries())
                .isEqualTo(2);
        assertThat(call("backend", requestException(PrematureCloseException.TEST_EXCEPTION), ON).tries())
                .isEqualTo(2);
    }

    /**
     * A {@code java.util.concurrent.TimeoutException} is retried; Reactor Netty's response timeout, a
     * {@code ReadTimeoutException} that is no IOException, is not.
     */
    @Test
    void onlyAJavaTimeoutIsRetried() {
        assertThat(call("backend", new TimeoutException("Did not observe any item"), ON).tries()).isEqualTo(2);
        assertThat(call("backend", requestException(ReadTimeoutException.INSTANCE), ON).tries()).isEqualTo(1);
    }

    /**
     * Once it gives up, the caller gets the last try's error, as it is or inside Reactor's RetryExhaustedException
     * (once per retry spec that gave up): the error is passed on.
     */
    @Test
    void givingUpPassesTheLastErrorOnAsTheCause() {
        Throwable last = requestException(new ConnectException("Connection refused"));
        for (Outcome o : List.of(call("backend", last, ON), call("backend", last))) {
            Throwable e = o.error();
            while (Exceptions.isRetryExhausted(e)) {
                e = e.getCause();
            }
            assertThat(e).isSameAs(last);
        }
    }

    /** A service without instances: nothing is sent, the caller gets a 503 response, and it is not retried. */
    @Test
    void aServiceWithoutInstancesAnswers503() {
        Outcome o = call("nobody", null, ON);
        assertThat(o.tries()).isZero();
        assertThat(o.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
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
