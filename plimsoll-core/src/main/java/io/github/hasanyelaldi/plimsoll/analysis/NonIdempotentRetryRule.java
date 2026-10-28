package io.github.hasanyelaldi.plimsoll.analysis;

import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import java.util.ArrayList;
import java.util.List;

/**
 * PLM-A3 Retry on a non-idempotent edge: ERROR. The tries per call are the product of the known
 * totalAttempts of the edge's layers.
 *
 * <p>Only an edge explicitly marked {@code idempotent: false} is checked; an UNKNOWN idempotency is not
 * assumed either way. If such an edge has no known retry but an UNKNOWN attempt count, the rule cannot
 * decide (INFO). Known retries next to UNKNOWN attempt counts give "at least" n tries, tagged.
 */
final class NonIdempotentRetryRule {

    static final String ID = "PLM-A3";

    private NonIdempotentRetryRule() {}

    static List<Finding> evaluate(ServiceGraph graph) {
        List<Finding> findings = new ArrayList<>();
        for (Edge edge : graph.edges()) {
            if (edge.idempotent().isKnown() && !edge.idempotent().value()) {
                evaluateEdge(edge, graph.location(edge), findings);
            }
        }
        return findings;
    }

    private static void evaluateEdge(Edge edge, String where, List<Finding> out) {
        List<UsedValue> values = new ArrayList<>();
        values.add(UsedValue.of(where + " idempotent", edge.idempotent()));
        long tries = 1;
        for (int i = 0; i < edge.layers().size(); i++) {
            Layer layer = edge.layers().get(i);
            values.add(UsedValue.of(Attempts.layerName(where, edge, i) + " totalAttempts", layer.retry().totalAttempts()));
            if (Attempts.known(layer)) {
                tries = Math.multiplyExact(tries, layer.retry().totalAttempts().value());
            }
        }
        List<String> unknowns = Attempts.unknownAttempts(where, edge);
        if (Attempts.edgeRetries(edge)) {
            out.add(new Finding(ID, Severity.ERROR, where,
                    "non-idempotent call retried, " + (unknowns.isEmpty() ? "" : "at least ") + tries + " tries",
                    values, unknowns));
        } else if (!unknowns.isEmpty()) {
            out.add(Attempts.cannotEvaluate(ID, where, "", values, unknowns));
        }
    }
}
