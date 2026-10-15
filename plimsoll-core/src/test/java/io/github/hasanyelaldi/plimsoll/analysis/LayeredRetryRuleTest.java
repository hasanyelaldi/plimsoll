package io.github.hasanyelaldi.plimsoll.analysis;

import static io.github.hasanyelaldi.plimsoll.analysis.Specs.analyze;
import static io.github.hasanyelaldi.plimsoll.analysis.Specs.chain;
import static io.github.hasanyelaldi.plimsoll.analysis.Specs.findings;
import static io.github.hasanyelaldi.plimsoll.analysis.Specs.load;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class LayeredRetryRuleTest {

    private static final String R = "layers: [ { kind: resilience4j, maxAttempts: 3 } ]";
    private static final String ONCE = "layers: [ { kind: resilience4j, maxAttempts: 1 } ]";

    // ---- one edge, several layers -------------------------------------------------

    @Test
    void resilience4jAndIstioRetriesOnOneEdgeMultiplyToTwelve() {
        AnalysisResult r = analyze("""
                services: { a: { entry: true }, b: {} }
                edges:
                  - { from: a, to: b, layers: [ { kind: resilience4j, maxAttempts: 3 }, { kind: istio, attempts: 3 } ] }
                """);
        assertThat(load(r, "b")).isEqualTo(BigInteger.valueOf(12));
        List<Finding> f = findings(r, "PLM-A5");
        assertThat(f).hasSize(1);
        assertThat(f.get(0).severity()).isEqualTo(Severity.WARN);
        assertThat(f.get(0).location()).isEqualTo("a→b");
        assertThat(f.get(0).detail()).isEqualTo("retries in 2 layers of one edge (resilience4j ×3, istio ×4), ×12 per call");
        assertThat(f.get(0).values()).extracting(UsedValue::name).containsExactly(
                "a→b layer 1 (resilience4j) totalAttempts", "a→b layer 2 (istio) totalAttempts");
    }

    @Test
    void severalLayersWithOnlyOneRetryingAreFine() {
        assertThat(findings(analyze("""
                services: { a: { entry: true }, b: {} }
                edges:
                  - { from: a, to: b, layers: [ { kind: resilience4j, maxAttempts: 3 }, { kind: istio, attempts: 0 } ] }
                """), "PLM-A5")).isEmpty();
    }

    @Test
    void unknownAttemptsOnTheEdgeAreNamed() {
        List<Finding> f = findings(analyze("""
                services: { a: { entry: true }, b: {} }
                edges:
                  - { from: a, to: b, layers: [ { kind: resilience4j, maxAttempts: 2 }, { kind: generic }, { kind: istio, attempts: 1 } ] }
                """), "PLM-A5");
        assertThat(f).hasSize(1);
        assertThat(f.get(0).unknowns()).containsExactly("a→b layer 2 (generic) totalAttempts");
    }

    // ---- one path, several edges ----------------------------------------------------

    @Test
    void retriesOnEveryHopOfAChainAreOneFinding() {
        List<Finding> f = findings(analyze(chain(3, 3, 3)), "PLM-A5");
        assertThat(f).hasSize(1);
        assertThat(f.get(0).location()).isEqualTo("s0→s1→s2→s3");
        assertThat(f.get(0).detail()).isEqualTo("retries on 3 edges of one path");
        assertThat(f.get(0).values()).hasSize(3);
    }

    @Test
    void retriesOnASingleEdgeOfThePathAreFine() {
        assertThat(findings(analyze(chain(1, 3, 1)), "PLM-A5")).isEmpty();
        assertThat(findings(analyze(chain(3)), "PLM-A5")).isEmpty();
    }

    @Test
    void pathIsTrimmedToItsRetryingStretch() {
        List<Finding> f = findings(analyze(chain(1, 3, 1, 2, 1)), "PLM-A5");
        assertThat(f).extracting(Finding::location, Finding::detail)
                .containsExactly(tuple("s1→s2→s3→s4", "retries on 2 edges of one path"));
    }

    @Test
    void eachBranchOfADiamondIsItsOwnPath() {
        List<Finding> f = findings(analyze("services: { a: { entry: true }, b: {}, c: {}, d: {} }\nedges:\n"
                + "  - { from: a, to: b, " + R + " }\n  - { from: a, to: c, " + R + " }\n"
                + "  - { from: b, to: d, " + R + " }\n  - { from: c, to: d, " + R + " }\n"), "PLM-A5");
        assertThat(f).extracting(Finding::location).containsExactly("a→b→d", "a→c→d");
    }

    @Test
    void stretchInsideALongerOneIsReportedOnce() {
        // x (second entry) reaches the same b→c→d stretch that a→b→c→d already contains.
        List<Finding> f = findings(analyze("services: { a: { entry: true }, x: { entry: true }, b: {}, c: {}, d: {} }\nedges:\n"
                + "  - { from: a, to: b, " + R + " }\n  - { from: x, to: b, " + ONCE + " }\n"
                + "  - { from: b, to: c, " + R + " }\n  - { from: c, to: d, " + R + " }\n"), "PLM-A5");
        assertThat(f).extracting(Finding::location).containsExactly("a→b→c→d");
    }

    @Test
    void cycleTerminates() {
        List<Finding> f = findings(analyze("services: { a: { entry: true }, b: {}, c: {} }\nedges:\n"
                + "  - { from: a, to: b, " + R + " }\n  - { from: b, to: c, " + R + " }\n"
                + "  - { from: c, to: b, " + R + " }\n"), "PLM-A5");
        assertThat(f).extracting(Finding::location).containsExactly("a→b→c");
    }

    @Test
    void unknownAttemptsOnThePathAreNamed() {
        List<Finding> f = findings(analyze("services: { a: { entry: true }, b: {}, c: {}, d: {} }\nedges:\n"
                + "  - { from: a, to: b, " + R + " }\n  - { from: b, to: c, layers: [ { kind: generic } ] }\n"
                + "  - { from: c, to: d, " + R + " }\n"), "PLM-A5");
        assertThat(f).hasSize(1);
        assertThat(f.get(0).detail()).isEqualTo("retries on 2 edges of one path");
        assertThat(f.get(0).unknowns()).containsExactly("b→c layer 1 (generic) totalAttempts");
    }

    @Test
    void serviceNotReachableFromAnEntryIsNotAPathStart() {
        assertThat(findings(analyze("services: { a: { entry: true }, b: {}, c: {} }\nedges:\n"
                + "  - { from: b, to: c, " + R + " }\n  - { from: c, to: a, " + R + " }\n"), "PLM-A5")).isEmpty();
    }
}
