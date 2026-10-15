package io.github.hasanyelaldi.plimsoll.analysis;

import io.github.hasanyelaldi.plimsoll.model.Durations;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.Service;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * PLM-A2a Retry budget exceeds the caller's deadline.
 * {@code W_k = k × perTry + Σ_{j=1..k-1} min(initial × multiplier^(j-1), maxBackoff)}.
 * If {@code W_n > deadline(caller)}: WARN, "k of n tries fit" with k the largest such that {@code W_k ≤ deadline}.
 *
 * <p>Unknown inputs are never guessed. A missing perTryTimeout, totalAttempts or caller deadline means the
 * rule cannot run (INFO). A missing initial backoff is taken as zero, which makes W a lower bound; a missing
 * maxBackoff with a growing backoff is taken as no cap, which makes W an upper bound. The wording says which.
 */
final class RetryBudgetRule {

    static final String ID = "PLM-A2a";

    private RetryBudgetRule() {}

    static List<Finding> evaluate(ServiceGraph graph) {
        List<Finding> findings = new ArrayList<>();
        for (Edge edge : graph.edges()) {
            Service caller = graph.services().get(edge.from());
            for (int i = 0; i < edge.layers().size(); i++) {
                evaluateLayer(edge, i, caller, findings);
            }
        }
        return findings;
    }

    private static void evaluateLayer(Edge edge, int index, Service caller, List<Finding> out) {
        Layer layer = edge.layers().get(index);
        RetryPolicy p = layer.retry();
        String name = Attempts.layerName(edge, index);
        String prefix = Attempts.layerPrefix(edge, index);

        List<UsedValue> values = new ArrayList<>();
        values.add(UsedValue.of(name + " totalAttempts", p.totalAttempts()));
        values.add(UsedValue.of(name + " perTryTimeout", p.perTryTimeout(), Durations::format));
        values.add(UsedValue.of(name + " backoff", p.initialBackoff(), Durations::format));
        values.add(UsedValue.of(name + " multiplier", p.multiplier()));
        values.add(UsedValue.of(name + " maxBackoff", p.maxBackoff(), Durations::format));
        values.add(UsedValue.of(caller.name() + " deadline", caller.deadline(), Durations::format));

        if (p.totalAttempts().isKnown() && p.totalAttempts().value() <= 1) {
            return;
        }
        List<String> missing = new ArrayList<>();
        require(p.totalAttempts(), name + " totalAttempts", missing);
        require(p.perTryTimeout(), name + " perTryTimeout", missing);
        require(caller.deadline(), caller.name() + " deadline", missing);
        if (!missing.isEmpty()) {
            out.add(Attempts.cannotEvaluate(ID, edge.location(), prefix, values, missing));
            return;
        }

        int n = p.totalAttempts().value();
        double perTry = p.perTryTimeout().value().toNanos();
        double deadline = caller.deadline().value().toNanos();
        double multiplier = p.multiplier().value();
        boolean initialUnknown = !p.initialBackoff().isKnown();
        double initial = initialUnknown ? 0 : p.initialBackoff().value().toNanos();
        boolean capUnknown = !initialUnknown && !p.maxBackoff().isKnown() && multiplier > 1 && n > 2;
        Double cap = p.maxBackoff().isKnown() ? (double) p.maxBackoff().value().toNanos() : null;

        // W_n, and the largest k with W_k <= deadline, in one pass.
        double budget = 0;
        int fit = 0;
        boolean exceeded = false;
        for (int t = 1; t <= n; t++) {
            budget += perTry;
            if (t > 1) {
                double backoff = initial * Math.pow(multiplier, t - 2);
                budget += cap == null ? backoff : Math.min(backoff, cap);
            }
            if (!exceeded && budget <= deadline) {
                fit = t;
            } else {
                exceeded = true;
            }
        }

        String budgetText = Durations.format(toDuration(budget));
        String deadlineText = Durations.format(caller.deadline().value());
        List<String> unknowns = new ArrayList<>();
        if (initialUnknown) {
            unknowns.add(name + " backoff");
            if (budget > deadline) {
                out.add(new Finding(ID, Severity.WARN, edge.location(),
                        prefix + "budget at least " + budgetText + " > deadline " + deadlineText
                                + ", at most " + fit + " of " + n + " tries fit",
                        values, unknowns));
            } else {
                out.add(new Finding(ID, Severity.INFO, edge.location(),
                        prefix + "budget at least " + budgetText + " <= deadline " + deadlineText
                                + "; backoff UNKNOWN, cannot confirm that " + n + " tries fit",
                        values, unknowns));
            }
        } else if (capUnknown) {
            if (budget > deadline) {
                unknowns.add(name + " maxBackoff");
                out.add(new Finding(ID, Severity.INFO, edge.location(),
                        prefix + "budget at most " + budgetText + " > deadline " + deadlineText
                                + " unless maxBackoff caps the backoff; maxBackoff UNKNOWN",
                        values, unknowns));
            }
        } else if (budget > deadline) {
            out.add(new Finding(ID, Severity.WARN, edge.location(),
                    prefix + "budget " + budgetText + " > deadline " + deadlineText
                            + ", " + fit + " of " + n + " tries fit",
                    values, unknowns));
        }
    }

    private static void require(Val<?> val, String name, List<String> missing) {
        if (!val.isKnown()) {
            missing.add(name);
        }
    }

    private static Duration toDuration(double nanos) {
        return Duration.ofNanos(Math.round(Math.min(nanos, 9e18)));
    }
}
