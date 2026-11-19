package io.github.hasanyelaldi.plimsoll.analysis;

import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.FailureClass;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import java.util.ArrayList;
import java.util.List;

/**
 * PLM-A3 Retry on a non-idempotent edge (ADR 0014). The risk is a retried failure the callee may have processed:
 * the tries are the most {@link Attempts#tries} over every class but {@code not-sent}. Above 1 that is an ERROR,
 * naming a class that causes it when every layer's conditions are known. An edge retried only when the request was
 * not sent is INFO. UNKNOWN conditions retry every class, so they give the ERROR untagged.
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
        for (int i = 0; i < edge.layers().size(); i++) {
            Layer layer = edge.layers().get(i);
            values.add(UsedValue.of(Attempts.layerName(where, edge, i) + " totalAttempts", layer.retry().totalAttempts()));
        }
        values.addAll(Attempts.conditionValues(where, edge));
        FailureClass worst = null;
        long processed = 1;
        for (FailureClass c : FailureClass.values()) {
            long tries = Attempts.tries(edge, c);
            if (c.processed() && tries > processed) {
                worst = c;
                processed = tries;
            }
        }
        long notSent = Attempts.tries(edge, FailureClass.NOT_SENT);
        List<String> unknowns = Attempts.unknownAttempts(where, edge);
        String atLeast = unknowns.isEmpty() ? "" : "at least ";
        if (worst != null) {
            String on = Attempts.conditionsKnown(edge) ? " (on " + worst.id() + ")" : "";
            out.add(new Finding(ID, Severity.ERROR, where,
                    "non-idempotent call retried, " + atLeast + processed + " tries" + on, values, unknowns));
        } else if (notSent > 1) {
            out.add(new Finding(ID, Severity.INFO, where,
                    "retried only when the request was not sent, " + atLeast + notSent + " tries", values, unknowns));
        } else if (!unknowns.isEmpty()) {
            out.add(Attempts.cannotEvaluate(ID, where, "", values, unknowns));
        }
    }
}
