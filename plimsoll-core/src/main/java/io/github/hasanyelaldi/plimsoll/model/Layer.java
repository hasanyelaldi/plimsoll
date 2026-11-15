package io.github.hasanyelaldi.plimsoll.model;

import java.time.Duration;

/**
 * One resilience layer on an edge. {@code kind}: resilience4j | spring-cloud-gateway | istio | http-client | generic.
 * {@code conditions} are the failure classes it retries and passes on (ADR 0014).
 */
public record Layer(String kind, RetryPolicy retry, Val<Duration> overallTimeout, RetryConditions conditions) {

    /** A layer whose retry conditions are not known: it retries every class and may pass on any class. */
    public Layer(String kind, RetryPolicy retry, Val<Duration> overallTimeout) {
        this(kind, retry, overallTimeout, RetryConditions.unknown("retry conditions of the " + kind
                + " layer are not collected yet"));
    }
}
