package io.github.hasanyelaldi.plimsoll.model;

import java.util.List;

/** A call from one service to another. {@code layers} are listed outermost first. */
public record Edge(
        String from, String to, String operation, Val<Boolean> idempotent, int fanout, List<Layer> layers) {

    public Edge {
        layers = List.copyOf(layers);
    }

    /** Stable display name, e.g. {@code gateway→orders}. */
    public String location() {
        return from + "→" + to;
    }
}
