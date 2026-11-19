package io.github.hasanyelaldi.plimsoll.analysis;

import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.FailureClass;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.RetryConditions;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Helpers shared by the rules for naming layers and reading attempt counts. */
final class Attempts {

    private Attempts() {}

    /**
     * {@code gateway→orders layer 1 (resilience4j)}; layers are numbered from 1, outermost first.
     *
     * @param where the edge's location in its graph ({@link ServiceGraph#location})
     */
    static String layerName(String where, Edge edge, int index) {
        return where + " layer " + (index + 1) + " (" + edge.layers().get(index).kind() + ")";
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
    static List<String> unknownAttempts(String where, Edge edge) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < edge.layers().size(); i++) {
            if (!known(edge.layers().get(i))) {
                names.add(layerName(where, edge, i) + " totalAttempts");
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

    /**
     * {@code E_c(e)} of ADR 0014: the tries on this edge for a failure of class {@code atCallee} at the callee, as the
     * product of the known totalAttempts of the layers that retry it. The failure starts at the innermost layer; each
     * layer sees the classes the layers inside it may have turned it into. A layer retries when any of them is in its
     * retried classes; UNKNOWN conditions retry, and pass on, every class. UNKNOWN attempt counts are left out, as in
     * {@link #unknownAttempts}.
     */
    static long tries(Edge edge, FailureClass atCallee) {
        Set<FailureClass> seen = EnumSet.of(atCallee);
        long tries = 1;
        for (int i = edge.layers().size() - 1; i >= 0; i--) {
            Layer layer = edge.layers().get(i);
            RetryConditions c = layer.conditions();
            boolean retried = !c.retried().isKnown() || seen.stream().anyMatch(c.retried().value()::contains);
            if (retried && known(layer)) {
                tries = Math.multiplyExact(tries, layer.retry().totalAttempts().value());
            }
            seen = passedOn(c, seen);
        }
        return tries;
    }

    private static Set<FailureClass> passedOn(RetryConditions c, Set<FailureClass> seen) {
        if (!c.givesUpAs().isKnown()) {
            return EnumSet.allOf(FailureClass.class);
        }
        Set<FailureClass> out = EnumSet.noneOf(FailureClass.class);
        for (FailureClass f : seen) {
            out.addAll(c.givesUpAs().value().getOrDefault(f, Set.of(f)));
        }
        return out;
    }

    /** True when every layer of the edge has known retried classes and a known translation (ADR 0014). */
    static boolean conditionsKnown(Edge edge) {
        return edge.layers().stream()
                .allMatch(l -> l.conditions().retried().isKnown() && l.conditions().givesUpAs().isKnown());
    }

    /** The condition values of each layer that are known, for a finding's list of used values. */
    static List<UsedValue> conditionValues(String where, Edge edge) {
        List<UsedValue> values = new ArrayList<>();
        for (int i = 0; i < edge.layers().size(); i++) {
            RetryConditions c = edge.layers().get(i).conditions();
            if (c.retried().isKnown()) {
                values.add(UsedValue.of(layerName(where, edge, i) + " retried", c.retried(), Attempts::classes));
            }
            if (c.givesUpAs().isKnown() && i > 0) {
                values.add(UsedValue.of(layerName(where, edge, i) + " gives up as", c.givesUpAs(), Attempts::givesUpAs));
            }
        }
        return values;
    }

    /** {@code not-sent, status-503}, in the order of {@link FailureClass}; {@code none} for no class. */
    static String classes(Set<FailureClass> classes) {
        return classes.isEmpty() ? "none" : Arrays.stream(FailureClass.values())
                .filter(classes::contains).map(FailureClass::id).collect(Collectors.joining(", "));
    }

    /** {@code not-sent as status-503; timeout as status-504}; {@code every class as is} for an empty map. */
    static String givesUpAs(Map<FailureClass, Set<FailureClass>> map) {
        return map.isEmpty() ? "every class as is" : Arrays.stream(FailureClass.values())
                .filter(map::containsKey).map(f -> f.id() + " as " + classes(map.get(f)))
                .collect(Collectors.joining("; "));
    }
}
