package io.github.hasanyelaldi.plimsoll.model;

import java.util.Objects;

/**
 * A value together with its provenance.
 *
 * @param evidence {@code file:line} or doc URL; for {@link Source#UNKNOWN} it says what is missing
 */
public record Val<T>(T value, Source source, String evidence) {

    public Val {
        Objects.requireNonNull(source, "source");
        if ((source == Source.UNKNOWN) != (value == null)) {
            throw new IllegalArgumentException("value is null if and only if source is UNKNOWN");
        }
    }

    public static <T> Val<T> explicit(T value, String evidence) {
        return new Val<>(value, Source.EXPLICIT, evidence);
    }

    public static <T> Val<T> unknown(String evidence) {
        return new Val<>(null, Source.UNKNOWN, evidence);
    }

    public boolean isKnown() {
        return source != Source.UNKNOWN;
    }
}
