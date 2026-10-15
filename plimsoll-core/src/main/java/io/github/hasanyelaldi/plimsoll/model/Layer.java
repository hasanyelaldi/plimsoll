package io.github.hasanyelaldi.plimsoll.model;

import java.time.Duration;

/** One resilience layer on an edge. {@code kind}: resilience4j | istio | http-client | generic. */
public record Layer(String kind, RetryPolicy retry, Val<Duration> overallTimeout) {}
