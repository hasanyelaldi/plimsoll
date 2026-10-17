package io.github.hasanyelaldi.plimsoll.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Oracle for Retry × CircuitBreaker composition with programmatic decoration, asserted by counting
 * calls. {@link SpringAnnotationOrderOracleTest} applies the same probe to the Spring Boot annotations.
 *
 * <p>Probe: Retry with 5 attempts, CircuitBreaker that opens after 2 failed calls. If Retry is
 * outermost, every retry passes through the breaker: 2 real calls, then the open breaker rejects
 * the remaining tries. If CircuitBreaker is outermost, one breaker call wraps all 5 retries.
 */
class DecoratorOrderOracleTest {

    static final int MAX_ATTEMPTS = 5;
    static final int BREAKER_WINDOW = 2;

    private static RetryConfig retryConfig() {
        return RetryConfig.custom().maxAttempts(MAX_ATTEMPTS).waitDuration(Duration.ZERO).build();
    }

    private static CircuitBreakerConfig breakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(BREAKER_WINDOW)
                .minimumNumberOfCalls(BREAKER_WINDOW)
                .failureRateThreshold(50)
                .build();
    }

    private static Supplier<String> failing(AtomicInteger calls) {
        return () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        };
    }

    @Test
    void retryOutsideBreakerStopsCallingOnceTheBreakerOpens() {
        AtomicInteger calls = new AtomicInteger();
        var retry = Retry.of("oracle", retryConfig());
        var breaker = CircuitBreaker.of("oracle", breakerConfig());
        Supplier<String> decorated = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(breaker, failing(calls)));

        assertThatThrownBy(decorated::get).isInstanceOf(CallNotPermittedException.class);
        assertThat(calls.get()).isEqualTo(BREAKER_WINDOW);
    }

    @Test
    void breakerOutsideRetryLetsEveryRetryThrough() {
        AtomicInteger calls = new AtomicInteger();
        var retry = Retry.of("oracle", retryConfig());
        var breaker = CircuitBreaker.of("oracle", breakerConfig());
        Supplier<String> decorated = CircuitBreaker.decorateSupplier(breaker,
                Retry.decorateSupplier(retry, failing(calls)));

        assertThatThrownBy(decorated::get).isInstanceOf(IllegalStateException.class);
        assertThat(calls.get()).isEqualTo(MAX_ATTEMPTS);
    }
}
