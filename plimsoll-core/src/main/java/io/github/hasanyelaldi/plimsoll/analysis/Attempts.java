package io.github.hasanyelaldi.plimsoll.analysis;

import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import java.util.ArrayList;
import java.util.List;

/** Helpers shared by the rules for naming layers and reading attempt counts. */
final class Attempts {

    private Attempts() {}

    /** {@code gateway→orders layer 1 (resilience4j)}; layers are numbered from 1, outermost first. */
    static String layerName(Edge edge, int index) {
        return edge.location() + " layer " + (index + 1) + " (" + edge.layers().get(index).kind() + ")";
    }

    static boolean known(Layer layer) {
        return layer.retry().totalAttempts().isKnown();
    }

    static boolean retries(Layer layer) {
        return known(layer) && layer.retry().totalAttempts().value() > 1;
    }

    static boolean edgeRetries(Edge edge) {
        return edge.layers().stream().anyMatch(Attempts::retries);
    }

    /** Names of totalAttempts values on this edge that are UNKNOWN. */
    static List<String> unknownAttempts(Edge edge) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < edge.layers().size(); i++) {
            if (!known(edge.layers().get(i))) {
                names.add(layerName(edge, i) + " totalAttempts");
            }
        }
        return names;
    }

    /** INFO finding for a rule that cannot run because inputs are UNKNOWN; nothing is guessed. */
    static Finding cannotEvaluate(
            String ruleId, String location, String prefix, List<UsedValue> values, List<String> unknowns) {
        return new Finding(ruleId, Severity.INFO, location,
                prefix + "cannot evaluate: " + String.join(", ", unknowns) + " UNKNOWN", values, unknowns);
    }

    /** "layer 2 (istio): " on edges with several layers, so each finding says which layer it is about. */
    static String layerPrefix(Edge edge, int index) {
        return edge.layers().size() > 1
                ? "layer " + (index + 1) + " (" + edge.layers().get(index).kind() + "): "
                : "";
    }
}
