package io.github.hasanyelaldi.plimsoll.analysis;

import java.util.List;

/**
 * @param amplification one entry per service reachable from an entry service, sorted by service name
 * @param findings sorted by severity, rule id, location
 */
public record AnalysisResult(List<ServiceAmplification> amplification, List<Finding> findings) {

    public AnalysisResult {
        amplification = List.copyOf(amplification);
        findings = List.copyOf(findings);
    }

    public long count(Severity severity) {
        return findings.stream().filter(f -> f.severity() == severity).count();
    }

    public boolean hasFindingAtLeast(Severity threshold) {
        return findings.stream().anyMatch(f -> f.severity().atLeast(threshold));
    }
}
