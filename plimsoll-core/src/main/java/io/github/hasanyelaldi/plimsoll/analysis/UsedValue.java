package io.github.hasanyelaldi.plimsoll.analysis;

import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.util.function.Function;

/** A value a finding relied on, with where it came from. {@code display} is "UNKNOWN" for unknown values. */
public record UsedValue(String name, String display, Source source, String evidence) {

    public static <T> UsedValue of(String name, Val<T> val, Function<T, String> format) {
        return new UsedValue(name, val.isKnown() ? format.apply(val.value()) : "UNKNOWN", val.source(), val.evidence());
    }

    public static <T> UsedValue of(String name, Val<T> val) {
        return of(name, val, String::valueOf);
    }
}
