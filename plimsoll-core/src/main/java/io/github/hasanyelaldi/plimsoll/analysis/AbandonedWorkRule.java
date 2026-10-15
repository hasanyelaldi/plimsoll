package io.github.hasanyelaldi.plimsoll.analysis;

import io.github.hasanyelaldi.plimsoll.model.Durations;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.Service;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * PLM-A2b Abandoned work. If {@code deadline(callee) > perTry(edge)} the caller gives up while the callee
 * keeps working. WARN if the edge retries (the abandoned work is repeated), INFO otherwise.
 *
 * <p>With several layers the per-try timeout is the smallest known one: that layer gives up first.
 * UNKNOWN per-try timeouts on other layers, or unknown attempt counts that could change the severity, are
 * named in the finding.
 */
final class AbandonedWorkRule {

    static final String ID = "PLM-A2b";

    private AbandonedWorkRule() {}

    static List<Finding> evaluate(ServiceGraph graph) {
        List<Finding> findings = new ArrayList<>();
        for (Edge edge : graph.edges()) {
            if (!edge.layers().isEmpty()) {
                evaluateEdge(edge, graph.services().get(edge.to()), findings);
            }
        }
        return findings;
    }

    private static void evaluateEdge(Edge edge, Service callee, List<Finding> out) {
        List<UsedValue> values = new ArrayList<>();
        values.add(UsedValue.of(callee.name() + " deadline", callee.deadline(), Durations::format));

        int shortest = -1;
        List<String> unknownPerTry = new ArrayList<>();
        for (int i = 0; i < edge.layers().size(); i++) {
            var perTry = edge.layers().get(i).retry().perTryTimeout();
            values.add(UsedValue.of(Attempts.layerName(edge, i) + " perTryTimeout", perTry, Durations::format));
            if (!perTry.isKnown()) {
                unknownPerTry.add(Attempts.layerName(edge, i) + " perTryTimeout");
            } else if (shortest < 0 || perTry.value().compareTo(edge.layers().get(shortest).retry().perTryTimeout().value()) < 0) {
                shortest = i;
            }
        }
        for (int i = 0; i < edge.layers().size(); i++) {
            values.add(UsedValue.of(Attempts.layerName(edge, i) + " totalAttempts", edge.layers().get(i).retry().totalAttempts()));
        }

        List<String> missing = new ArrayList<>();
        if (!callee.deadline().isKnown()) {
            missing.add(callee.name() + " deadline");
        }
        if (shortest < 0) {
            missing.addAll(unknownPerTry);
        }
        if (!missing.isEmpty()) {
            out.add(Attempts.cannotEvaluate(ID, edge.location(), "", values, missing));
            return;
        }

        Duration perTry = edge.layers().get(shortest).retry().perTryTimeout().value();
        if (callee.deadline().value().compareTo(perTry) <= 0) {
            return;
        }
        boolean retries = Attempts.edgeRetries(edge);
        List<String> unknowns = new ArrayList<>(unknownPerTry);
        if (!retries) {
            unknowns.addAll(Attempts.unknownAttempts(edge));
        }
        out.add(new Finding(ID, retries ? Severity.WARN : Severity.INFO, edge.location(),
                Attempts.layerPrefix(edge, shortest) + "callee deadline " + Durations.format(callee.deadline().value())
                        + " > per-try " + Durations.format(perTry),
                values, unknowns));
    }
}
