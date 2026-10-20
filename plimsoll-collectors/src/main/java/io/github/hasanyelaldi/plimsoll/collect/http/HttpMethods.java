package io.github.hasanyelaldi.plimsoll.collect.http;

import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * HTTP request methods and their idempotency, from {@code kb/defaults/http.yaml} (RFC 9110 §9.2.2, RFC 5789 for
 * PATCH). The standard is recorded as {@link Source#LIBRARY_DEFAULT}: explicit configuration overrides it, and a
 * method without a verified entry stays UNKNOWN.
 */
public final class HttpMethods {

    static final String KB = "http";
    public static final Set<String> METHODS = Set.of("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS", "TRACE",
            "PATCH");

    private HttpMethods() {}

    /** The method an operation such as {@code GET /owners/{id}} starts with, if it starts with one. */
    public static Optional<String> ofOperation(String operation) {
        if (operation == null) {
            return Optional.empty();
        }
        String first = operation.strip().split("\\s+", 2)[0];
        return METHODS.contains(first) ? Optional.of(first) : Optional.empty();
    }

    /** {@code name} in upper case if it is a method, e.g. {@code post} or {@code RequestMethod.POST}. */
    public static Optional<String> normalize(String name) {
        String upper = name.substring(name.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
        return METHODS.contains(upper) ? Optional.of(upper) : Optional.empty();
    }

    /** Whether a request with {@code method} is idempotent; {@code where} says where the method was found. */
    public static Val<Boolean> idempotent(String method, String where, DefaultsKb kb) {
        return kb.defaultOr(KB, "idempotent." + method, Boolean::valueOf, method + " (" + where + ")");
    }

    /**
     * Idempotent if every method is; not idempotent, citing the first non-idempotent one, if any is not; UNKNOWN
     * if one of them is UNKNOWN and none is known not to be.
     */
    public static Val<Boolean> all(List<Val<Boolean>> each, String noneWhy) {
        if (each.isEmpty()) {
            return Val.unknown(noneWhy);
        }
        for (Val<Boolean> v : each) {
            if (v.isKnown() && !v.value()) {
                return v;
            }
        }
        for (Val<Boolean> v : each) {
            if (!v.isKnown()) {
                return v;
            }
        }
        Source source = each.stream().allMatch(v -> v.source() == Source.EXPLICIT) ? Source.EXPLICIT
                : Source.LIBRARY_DEFAULT;
        return new Val<>(true, source, String.join("; ", each.stream().map(Val::evidence).toList()));
    }
}
