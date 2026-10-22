package io.github.hasanyelaldi.plimsoll.model;

import java.util.Comparator;

/**
 * A retry, timeout or breaker mechanism found in a service's code, build file or configuration that no collector
 * models. The graph may therefore miss a layer on the edges from {@code service}.
 *
 * @param mechanism e.g. {@code Spring Retry}
 * @param effect the kind of layer it can add: {@code retry}, {@code timeout} or {@code breaker}
 * @param pattern what matched, e.g. {@code annotation org.springframework.retry.annotation.Retryable}
 * @param evidence {@code file:line} of the match
 * @param source documentation of the mechanism
 */
public record Detection(String service, String mechanism, String effect, String pattern, String evidence,
        String source) {

    /** Service, then mechanism, then evidence, then pattern. */
    public static final Comparator<Detection> ORDER = Comparator.comparing(Detection::service)
            .thenComparing(Detection::mechanism)
            .thenComparing(Detection::evidence, Evidence.ORDER)
            .thenComparing(Detection::pattern);
}
