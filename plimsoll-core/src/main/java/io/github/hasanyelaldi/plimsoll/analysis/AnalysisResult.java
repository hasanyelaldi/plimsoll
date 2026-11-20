package io.github.hasanyelaldi.plimsoll.analysis;

import java.util.List;

/**
 * @param amplification one entry per service reachable from an entry service, sorted by service name
 * @param findings sorted by severity, rule id, location
 * @param breakers the circuit breakers of the graph, in edge order and outermost layer first (ADR 0015)
 */
public record AnalysisResult(List<ServiceAmplification> amplification, List<Finding> findings,
        List<BreakerListing> breakers) {

    public AnalysisResult {
        amplification = List.copyOf(amplification);
        findings = List.copyOf(findings);
        breakers = List.copyOf(breakers);
    }

    /** A result for a graph without circuit breakers. */
    public AnalysisResult(List<ServiceAmplification> amplification, List<Finding> findings) {
        this(amplification, findings, List.of());
    }

    public long count(Severity severity) {
        return findings.stream().filter(f -> f.severity() == severity).count();
    }

    public boolean hasFindingAtLeast(Severity threshold) {
        return findings.stream().anyMatch(f -> f.severity().atLeast(threshold));
    }
}
