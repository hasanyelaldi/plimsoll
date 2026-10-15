package io.github.hasanyelaldi.plimsoll.analysis;

import io.github.hasanyelaldi.plimsoll.model.Durations;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import java.util.ArrayList;
import java.util.List;

/**
 * PLM-A4 Backoff hygiene, per layer that retries: zero backoff is WARN (retries hit the callee back to
 * back), no jitter is INFO (callers retry in lockstep).
 *
 * <p>Only explicitly known values are judged: an UNKNOWN backoff or jitter, or an UNKNOWN attempt count,
 * gives no finding rather than a guessed one.
 */
final class BackoffHygieneRule {

    static final String ID = "PLM-A4";

    private BackoffHygieneRule() {}

    static List<Finding> evaluate(ServiceGraph graph) {
        List<Finding> findings = new ArrayList<>();
        for (Edge edge : graph.edges()) {
            for (int i = 0; i < edge.layers().size(); i++) {
                if (Attempts.retries(edge.layers().get(i))) {
                    evaluateLayer(edge, i, findings);
                }
            }
        }
        return findings;
    }

    private static void evaluateLayer(Edge edge, int index, List<Finding> out) {
        Layer layer = edge.layers().get(index);
        String name = Attempts.layerName(edge, index);
        String prefix = Attempts.layerPrefix(edge, index);
        UsedValue attempts = UsedValue.of(name + " totalAttempts", layer.retry().totalAttempts());

        var backoff = layer.retry().initialBackoff();
        if (backoff.isKnown() && backoff.value().isZero()) {
            out.add(new Finding(ID, Severity.WARN, edge.location(), prefix + "zero backoff",
                    List.of(attempts, UsedValue.of(name + " backoff", backoff, Durations::format)), List.of()));
        }
        var jitter = layer.retry().jitter();
        if (jitter.isKnown() && !jitter.value()) {
            out.add(new Finding(ID, Severity.INFO, edge.location(), prefix + "no jitter",
                    List.of(attempts, UsedValue.of(name + " jitter", jitter)), List.of()));
        }
    }
}
