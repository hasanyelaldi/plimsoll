package io.github.hasanyelaldi.plimsoll.model;

import java.util.List;
import java.util.Objects;

/**
 * A call from one service to another. {@code layers} are listed outermost first.
 *
 * @param fanout how many times one request to {@code from} calls {@code to} on this operation; at least 1 when known
 */
public record Edge(
        String from, String to, String operation, Val<Boolean> idempotent, Val<Integer> fanout, List<Layer> layers) {

    public Edge {
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
