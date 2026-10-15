package io.github.hasanyelaldi.plimsoll.model;

import java.time.Duration;
import java.util.Set;

/** Retry settings of one layer. {@code totalAttempts} always counts the first call. */
public record RetryPolicy(
        Val<Integer> totalAttempts,
        Val<Duration> perTryTimeout,
        Val<Duration> initialBackoff,
        Val<Double> multiplier,
        Val<Duration> maxBackoff,
        Val<Boolean> jitter,
        Val<Set<String>> retryOn) {}
