package io.github.hasanyelaldi.plimsoll.collect.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.spring6.timelimiter.configure.IllegalReturnTypeException;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Oracle for {@code @Retry} × {@code @TimeLimiter} under the Spring Boot annotations. Each try returns a future that
 * never completes, so only the TimeLimiter ends it: if Retry is the outer aspect every try times out and is retried
 * (the timeout is per try); if TimeLimiter is outer, the first timeout ends the whole call.
 *
 * <p>No sleeps: the 20 ms timeouts are the only waiting.
 */
class TimeLimiterOrderOracleTest {

    private static final int MAX_ATTEMPTS = 3;

    public static class Backend {
        private final AtomicInteger calls = new AtomicInteger();

        public int calls() {
            return calls.get();
        }

        @Retry(name = "backend")
        @TimeLimiter(name = "backend")
        public CompletableFuture<String> hang() {
            calls.incrementAndGet();
            return new CompletableFuture<>();
        }

        @TimeLimiter(name = "backend")
        public String sync() {
            calls.incrementAndGet();
            return "ok";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class App {
        @Bean
        Backend backend() {
            return new Backend();
        }
    }

    private static ConfigurableApplicationContext start(String... extraProperties) {
        return new SpringApplicationBuilder(App.class)
                .web(WebApplicationType.NONE)
                .properties(GatewayRetryOracleTest.GATEWAY_OFF)
                .properties(
                        "spring.main.banner-mode=off",
                        "spring.config.location=optional:classpath:none.yml",
                        "resilience4j.retry.instances.backend.max-attempts=" + MAX_ATTEMPTS,
                        "resilience4j.retry.instances.backend.wait-duration=0ms",
                        "resilience4j.timelimiter.instances.backend.timeout-duration=20ms")
                .properties(extraProperties)
                .run();
    }

    private static int callsUntilTimeout(String... extraProperties) {
        try (ConfigurableApplicationContext context = start(extraProperties)) {
            Backend backend = context.getBean(Backend.class);
            assertThatThrownBy(() -> backend.hang().get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(TimeoutException.class);
            return backend.calls();
        }
    }

    @Test
    void retryWrapsTimeLimiterByDefaultSoTheTimeoutIsPerTry() {
        assertThat(callsUntilTimeout()).isEqualTo(MAX_ATTEMPTS);
    }

    @Test
    void aspectOrderPropertiesMakeTheTimeoutOverall() {
        // Lower order = outer aspect. Defaults: retry MAX-5, time limiter MAX-2.
        assertThat(callsUntilTimeout("resilience4j.retry.retry-aspect-order=" + (Integer.MAX_VALUE - 1))).isEqualTo(1);
    }

    @Test
    void timeLimiterRejectsMethodsThatDoNotReturnACompletionStage() {
        try (ConfigurableApplicationContext context = start()) {
            Backend backend = context.getBean(Backend.class);
            assertThatThrownBy(backend::sync).isInstanceOf(IllegalReturnTypeException.class);
            assertThat(backend.calls()).isZero();
        }
    }
}
