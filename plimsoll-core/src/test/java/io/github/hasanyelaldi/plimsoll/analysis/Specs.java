package io.github.hasanyelaldi.plimsoll.analysis;

import io.github.hasanyelaldi.plimsoll.io.SpecLoader;
import java.math.BigInteger;
import java.util.List;

/** Test helpers: build a graph from inline YAML and look at the result. */
final class Specs {

    private Specs() {}

    static AnalysisResult analyze(String yaml) {
        return analyze(yaml, AnalysisOptions.defaults());
    }

    static AnalysisResult analyze(String yaml, AnalysisOptions options) {
        return Analyzer.analyze(SpecLoader.parse(yaml, "spec.yaml"), options);
    }

    static BigInteger load(AnalysisResult r, String service) {
        return r.amplification().stream()
                .filter(a -> a.service().equals(service))
                .findFirst()
                .orElseThrow()
                .value();
    }

    static List<Finding> findings(AnalysisResult r, String ruleId) {
        return r.findings().stream().filter(f -> f.ruleId().equals(ruleId)).toList();
    }

    /** gateway -> orders -> payments -> ledger, each hop resilience4j with the given maxAttempts. */
    static String chain(int... attempts) {
        StringBuilder y = new StringBuilder("services:\n  s0: { entry: true }\n");
        for (int i = 1; i <= attempts.length; i++) {
            y.append("  s").append(i).append(": {}\n");
        }
        y.append("edges:\n");
        for (int i = 0; i < attempts.length; i++) {
            y.append("  - { from: s").append(i).append(", to: s").append(i + 1)
                    .append(", layers: [ { kind: resilience4j, maxAttempts: ").append(attempts[i]).append(" } ] }\n");
        }
        return y.toString();
    }
}
