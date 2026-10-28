package io.github.hasanyelaldi.plimsoll.analysis;

import static io.github.hasanyelaldi.plimsoll.analysis.Specs.analyze;
import static io.github.hasanyelaldi.plimsoll.analysis.Specs.findings;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AbandonedWorkRuleTest {

    private static List<Finding> a2b(String calleeDeadline, String layers) {
        return findings(analyze("services: { a: { entry: true }, b: { deadline: " + calleeDeadline + " } }\n"
                + "edges:\n  - { from: a, to: b, layers: [ " + layers + " ] }\n"), "PLM-A2b");
    }

    @Test
    void calleeKeepsWorkingAfterTheCallerGaveUpOnARetriedEdgeIsWarn() {
        List<Finding> f = a2b("2s", "{ kind: resilience4j, maxAttempts: 3, perTryTimeout: 1s }");
        assertThat(f).hasSize(1);
        assertThat(f.get(0).severity()).isEqualTo(Severity.WARN);
        assertThat(f.get(0).location()).isEqualTo("a→b");
        assertThat(f.get(0).detail()).isEqualTo("callee deadline 2 s > per-try 1 s");
        assertThat(f.get(0).dependsOnUnknown()).isFalse();
    }

    @Test
    void sameSituationWithoutRetriesIsInfo() {
        List<Finding> f = a2b("2s", "{ kind: generic, totalAttempts: 1, perTryTimeout: 800ms }");
        assertThat(f.get(0).severity()).isEqualTo(Severity.INFO);
        assertThat(f.get(0).detail()).isEqualTo("callee deadline 2 s > per-try 800 ms");
    }

    @Test
    void calleeDeadlineWithinPerTryIsSilent() {
        assertThat(a2b("500ms", "{ kind: generic, totalAttempts: 3, perTryTimeout: 1s }")).isEmpty();
        assertThat(a2b("1s", "{ kind: generic, totalAttempts: 3, perTryTimeout: 1s }")).isEmpty(); // equal: not abandoned
    }

    @Test
    void edgeWithoutLayersHasNothingToCompare() {
        assertThat(findings(analyze("services: { a: { entry: true }, b: { deadline: 2s } }\n"
                + "edges:\n  - { from: a, to: b }\n"), "PLM-A2b")).isEmpty();
    }

    @Test
    void shortestKnownPerTryAmongLayersIsUsed() {
        List<Finding> f = a2b("2s", "{ kind: resilience4j, maxAttempts: 2, perTryTimeout: 1s }, "
                + "{ kind: istio, attempts: 1, perTryTimeout: 300ms }");
        assertThat(f.get(0).severity()).isEqualTo(Severity.WARN);
        assertThat(f.get(0).detail()).isEqualTo("layer 2 (istio): callee deadline 2 s > per-try 300 ms");
    }

    @Test
    void unknownPerTryTimeoutIsNamedNotGuessed() {
        List<Finding> f = a2b("2s", "{ kind: generic, totalAttempts: 3 }");
        assertThat(f.get(0).severity()).isEqualTo(Severity.INFO);
        assertThat(f.get(0).detail()).isEqualTo("cannot evaluate: a→b layer 1 (generic) perTryTimeout UNKNOWN");
        assertThat(f.get(0).unknowns()).containsExactly("a→b layer 1 (generic) perTryTimeout");
    }

    @Test
    void unknownCalleeDeadlineIsNamed() {
        List<Finding> f = findings(analyze("""
                services: { a: { entry: true }, b: {} }
                edges:
                  - { from: a, to: b, layers: [ { kind: generic, totalAttempts: 3, perTryTimeout: 1s } ] }
                """), "PLM-A2b");
        assertThat(f.get(0).unknowns()).containsExactly("b deadline");
    }

    @Test
    void unknownAttemptsLeaveTheSeverityAtInfoAndAreNamed() {
        List<Finding> f = a2b("2s", "{ kind: generic, perTryTimeout: 1s }");
        assertThat(f.get(0).severity()).isEqualTo(Severity.INFO);
        assertThat(f.get(0).detail()).isEqualTo("callee deadline 2 s > per-try 1 s");
        assertThat(f.get(0).unknowns()).containsExactly("a→b layer 1 (generic) totalAttempts");
    }

    @Test
    void unlimitedPerTryTimeoutNeverAbandonsTheCallee() {
        assertThat(a2b("2s", "{ kind: generic, totalAttempts: 3, perTryTimeout: unlimited }")).isEmpty();
    }

    @Test
    void finitePerTryTimeoutOnAnotherLayerStillCounts() {
        List<Finding> f = a2b("2s", "{ kind: resilience4j, maxAttempts: 2, perTryTimeout: 1s }, "
                + "{ kind: http-client, totalAttempts: 1, perTryTimeout: unlimited }");
        assertThat(f).singleElement().satisfies(x -> {
            assertThat(x.severity()).isEqualTo(Severity.WARN);
            assertThat(x.detail()).isEqualTo("layer 1 (resilience4j): callee deadline 2 s > per-try 1 s");
        });
    }
}
