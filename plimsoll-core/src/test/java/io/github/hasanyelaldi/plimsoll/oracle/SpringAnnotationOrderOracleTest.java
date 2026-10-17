package io.github.hasanyelaldi.plimsoll.oracle;

import static io.github.hasanyelaldi.plimsoll.oracle.DecoratorOrderOracleTest.BREAKER_WINDOW;
import static io.github.hasanyelaldi.plimsoll.oracle.DecoratorOrderOracleTest.MAX_ATTEMPTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Oracle for Retry × CircuitBreaker composition under the Spring Boot annotations, the form
 * collectors will see in application code. Same probe as {@link DecoratorOrderOracleTest}.
 *
 * <p>Needs resilience4j-spring-boot4 (2.4.0+); skip with {@code -DnoSpringOracle} on older versions.
 */
class SpringAnnotationOrderOracleTest {

    public static class Backend {
        private final AtomicInteger calls = new AtomicInteger();

        // Read through a method: the bean is a CGLIB proxy whose own fields are not initialized.
        public int calls() {
            return calls.get();
        }

        @Retry(name = "backend")
        @CircuitBreaker(name = "backend")
        public String call() {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
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

    private static int annotatedCalls(Class<? extends Throwable> thrown, String... extraProperties) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(App.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.main.banner-mode=off",
                        "resilience4j.retry.instances.backend.max-attempts=" + MAX_ATTEMPTS,
                        "resilience4j.retry.instances.backend.wait-duration=0ms",
                        "resilience4j.circuitbreaker.instances.backend.sliding-window-size=" + BREAKER_WINDOW,
                        "resilience4j.circuitbreaker.instances.backend.minimum-number-of-calls=" + BREAKER_WINDOW,
                        "resilience4j.circuitbreaker.instances.backend.failure-rate-threshold=50")
                .properties(extraProperties);
        try (ConfigurableApplicationContext context = builder.run()) {
            Backend backend = context.getBean(Backend.class);
            assertThatThrownBy(backend::call).isInstanceOf(thrown);
            return backend.calls();
        }
    }

    @Test
    void annotationsDefaultToRetryOutsideCircuitBreaker() {
        assertThat(annotatedCalls(CallNotPermittedException.class)).isEqualTo(BREAKER_WINDOW);
    }

    @Test
    void aspectOrderPropertiesInvertTheComposition() {
        // Lower order = higher precedence = outer aspect. Defaults: retry MAX-5, circuit breaker MAX-4.
        assertThat(annotatedCalls(IllegalStateException.class,
                "resilience4j.retry.retry-aspect-order=" + (Integer.MAX_VALUE - 3),
                "resilience4j.circuitbreaker.circuit-breaker-aspect-order=" + (Integer.MAX_VALUE - 4)))
                .isEqualTo(MAX_ATTEMPTS);
    }
}
