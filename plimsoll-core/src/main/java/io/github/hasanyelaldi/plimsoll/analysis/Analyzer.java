package io.github.hasanyelaldi.plimsoll.analysis;

import io.github.hasanyelaldi.plimsoll.model.Detection;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import java.util.ArrayList;
import java.util.List;

/** Runs all rules. Pure and deterministic: the same graph and options give the same result. */
public final class Analyzer {

    private Analyzer() {}

    public static AnalysisResult analyze(ServiceGraph graph, AnalysisOptions options) {
        return analyze(graph, options, List.of());
    }

    /** @param detections mechanisms found by the collectors but not modelled (PLM-C1) */
    public static AnalysisResult analyze(ServiceGraph graph, AnalysisOptions options, List<Detection> detections) {
        List<Finding> findings = new ArrayList<>();
        AmplificationRule.Outcome amplification = AmplificationRule.evaluate(graph, options);
        findings.addAll(amplification.findings());
        findings.addAll(RetryBudgetRule.evaluate(graph));
        findings.addAll(AbandonedWorkRule.evaluate(graph));
        findings.addAll(NonIdempotentRetryRule.evaluate(graph));
        findings.addAll(BackoffHygieneRule.evaluate(graph));
        findings.addAll(LayeredRetryRule.evaluate(graph));
        findings.addAll(UncollectedMechanismRule.evaluate(detections));
        findings.sort(Finding.ORDER);
        return new AnalysisResult(amplification.services(), findings);
    }
}
