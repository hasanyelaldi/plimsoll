package io.github.hasanyelaldi.plimsoll.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Oracle for the Resilience4j row of the attempt normalization table: {@code maxAttempts} counts the
 * initial call, so {@code totalAttempts = maxAttempts}. Runs the real library; waits are zero.
 */
class RetryAttemptCountingOracleTest {

    private static int callsUntilGiveUp(RetryConfig config) {
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> failing = () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        };
        Supplier<String> decorated = Retry.decorateSupplier(Retry.of("oracle", config), failing);
        assertThatThrownBy(decorated::get).isInstanceOf(IllegalStateException.class);
        return calls.get();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 10})
    void failingSupplierIsCalledExactlyMaxAttemptsTimesInTotal(int maxAttempts) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(Duration.ZERO)
                .build();

        assertThat(callsUntilGiveUp(config)).isEqualTo(maxAttempts);
    }

    @Test
    void defaultMaxAttemptsCountsTheFirstCall() {
        RetryConfig defaults = RetryConfig.ofDefaults();
        assertThat(defaults.getMaxAttempts()).isEqualTo(3);

        // Builder default attempts, without the default 500 ms wait. (RetryConfig.from(defaults) cannot
        // override the wait before 2.3.0: it rejects a second interval function.)
        RetryConfig noWait = RetryConfig.custom().waitDuration(Duration.ZERO).build();
        assertThat(callsUntilGiveUp(noWait)).isEqualTo(3);
    }

    @Test
    void defaultWaitDurationIsFixed500msBetweenTries() {
        IntervalBiFunction<Object> interval = RetryConfig.ofDefaults().getIntervalBiFunction();
        Either<Throwable, Object> failure = Either.left(new IllegalStateException("boom"));

        // The interval function gives the wait before retry n; read it instead of sleeping.
        assertThat(interval.apply(1, failure)).isEqualTo(500L);
        assertThat(interval.apply(2, failure)).isEqualTo(500L);
    }

    @Test
    void successStopsRetrying() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> failsTwice = () -> {
            if (calls.incrementAndGet() < 3) {
                throw new IllegalStateException("boom");
            }
            return "ok";
        };
        RetryConfig config = RetryConfig.custom().maxAttempts(5).waitDuration(Duration.ZERO).build();

        assertThat(Retry.decorateSupplier(Retry.of("oracle", config), failsTwice).get()).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }
}
