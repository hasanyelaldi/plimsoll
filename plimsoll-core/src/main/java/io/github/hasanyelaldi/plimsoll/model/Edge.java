package io.github.hasanyelaldi.plimsoll.model;

import java.util.List;
import java.util.Objects;

/**
 * A call from one service to another. {@code layers} are listed outermost first.
 *
 * @param fanout how many times one request to {@code from} calls {@code to} on this operation; at least 1 when known
 * @param choice the routing decision this edge is one alternative of, e.g. {@code routes of api-gateway}, with the
 *               evidence for it; null when the edge is not an alternative. Edges from one service with the same
 *               choice are mutually exclusive: one request to {@code from} takes at most one of them (ADR 0012).
 */
public record Edge(String from, String to, String operation, Val<Boolean> idempotent, Val<Integer> fanout,
        List<Layer> layers, Val<String> choice) {

    /** An edge that is not an alternative of a routing decision. */
    public Edge(String from, String to, String operation, Val<Boolean> idempotent, Val<Integer> fanout,
            List<Layer> layers) {
        this(from, to, operation, idempotent, fanout, layers, null);
    }

    public Edge {
        if (choice != null && !choice.isKnown()) {
            throw new IllegalArgumentException("choice is null or known, got UNKNOWN");
        }
        Objects.requireNonNull(fanout, "fanout");
        if (fanout.isKnown() && fanout.value() < 1) {
            throw new IllegalArgumentException("fanout must be at least 1, got " + fanout.value());
        }
        layers = List.copyOf(layers);
    }

    /** Stable display name, e.g. {@code gateway→orders}. */
    public String location() {
        return from + "→" + to;
    }
}
