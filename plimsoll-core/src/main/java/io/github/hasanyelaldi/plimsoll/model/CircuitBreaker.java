package io.github.hasanyelaldi.plimsoll.model;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * A circuit breaker on a layer (ADR 0015). No rule reads it yet; the report lists it.
 *
 * @param name the instance name
 * @param position where the breaker sits relative to its layer's retry
 * @param failureRateThreshold percentage of failed calls in the window that opens the breaker
 * @param slidingWindowType whether the window counts calls or seconds
 * @param slidingWindowSize the window, in calls or seconds as {@code slidingWindowType} says
 * @param minimumNumberOfCalls calls in the window before the failure rate is computed
 * @param waitDurationInOpenState how long the breaker stays open before trial calls
 */
public record CircuitBreaker(Val<String> name, Val<Position> position, Val<Double> failureRateThreshold,
        Val<WindowType> slidingWindowType, Val<Integer> slidingWindowSize, Val<Integer> minimumNumberOfCalls,
        Val<Duration> waitDurationInOpenState) {

    /** The breaker's place relative to the retry of its own layer. */
    public enum Position {
        /** Outside the retry: one breaker call wraps all tries. */
        WRAPS_ALL_TRIES("wraps-all-tries"),
        /** Inside the retry: the breaker counts, and can reject, each try. */
        EACH_TRY("each-try");

        private final String id;

        Position(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Optional<Position> parse(String text) {
            return Arrays.stream(values()).filter(p -> p.id.equals(text)).findFirst();
        }

        @Override
        public String toString() {
            return id;
        }
    }

    /** Resilience4j's sliding window types. */
    public enum WindowType {
        COUNT_BASED("count-based", "calls"),
        TIME_BASED("time-based", "s");

        private final String id;
        private final String unit;

        WindowType(String id, String unit) {
            this.id = id;
            this.unit = unit;
        }

        public String id() {
            return id;
        }

        /** What {@code slidingWindowSize} counts: calls or seconds. */
        public String unit() {
            return unit;
        }

        public static Optional<WindowType> parse(String text) {
            return Arrays.stream(values()).filter(t -> t.id.equals(text)).findFirst();
        }

        @Override
        public String toString() {
            return id;
        }
    }
}
