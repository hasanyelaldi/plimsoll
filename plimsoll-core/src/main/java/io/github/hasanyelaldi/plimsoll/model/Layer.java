package io.github.hasanyelaldi.plimsoll.model;

import java.time.Duration;

/**
 * One resilience layer on an edge. {@code kind}: resilience4j | spring-cloud-gateway | istio | http-client | generic.
 * {@code conditions} are the failure classes it retries and passes on (ADR 0014). {@code breaker} is its circuit
 * breaker, or null when it has none (ADR 0015).
 */
public record Layer(String kind, RetryPolicy retry, Val<Duration> overallTimeout, RetryConditions conditions,
        CircuitBreaker breaker) {

    /** A layer without a circuit breaker. */
    public Layer(String kind, RetryPolicy retry, Val<Duration> overallTimeout, RetryConditions conditions) {
        this(kind, retry, overallTimeout, conditions, null);
    }

    /** A layer whose retry conditions are not known: it retries every class and may pass on any class. */
    public Layer(String kind, RetryPolicy retry, Val<Duration> overallTimeout) {
        this(kind, retry, overallTimeout, RetryConditions.unknown("retry conditions of the " + kind
                + " layer are not collected yet"));
    }
}
