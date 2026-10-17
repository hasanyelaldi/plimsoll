package io.github.hasanyelaldi.plimsoll.analysis;

import static io.github.hasanyelaldi.plimsoll.analysis.Specs.analyze;
import static io.github.hasanyelaldi.plimsoll.analysis.Specs.findings;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.Service;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetryBudgetRuleTest {

    private static List<Finding> a2a(String yaml) {
        return findings(analyze(yaml), "PLM-A2a");
    }

    private static String spec(String callerDeadline, String layer) {
        return "services: { a: { entry: true, deadline: " + callerDeadline + " }, b: {} }\n"
                + "edges:\n  - { from: a, to: b, layers: [ " + layer + " ] }\n";
    }

    @Test
    void budgetOverflowReportsHowManyTriesFit() {
        // W_3 = 3 x 1s + 2 x 100ms = 3.2s > 3s; W_2 = 2.1s fits
        List<Finding> f = a2a(spec("3s",
                "{ kind: resilience4j, maxAttempts: 3, perTryTimeout: 1s, backoff: 100ms }"));
        assertThat(f).hasSize(1);
        assertThat(f.get(0).severity()).isEqualTo(Severity.WARN);
        assertThat(f.get(0).location()).isEqualTo("a→b");
        assertThat(f.get(0).detail()).isEqualTo("budget 3.2 s > deadline 3 s, 2 of 3 tries fit");
        assertThat(f.get(0).dependsOnUnknown()).isFalse();
        assertThat(f.get(0).values()).extracting(UsedValue::name).contains(
                "a→b layer 1 (resilience4j) totalAttempts", "a→b layer 1 (resilience4j) perTryTimeout",
                "a→b layer 1 (resilience4j) backoff", "a deadline");
    }

    @Test
    void istioRetriesAreNormalizedBeforeTheBudgetIsComputed() {
        // attempts: 2 -> 3 tries; W_3 = 3 x 800ms + 2 x 25ms = 2.45s > 2s; W_2 = 1.625s
        List<Finding> f = a2a(spec("2s", "{ kind: istio, attempts: 2, perTryTimeout: 800ms, backoff: 25ms }"));
        assertThat(f.get(0).detail()).isEqualTo("budget 2.45 s > deadline 2 s, 2 of 3 tries fit");
    }

    @Test
    void budgetThatFitsIsSilent() {
        assertThat(a2a(spec("4s", "{ kind: generic, totalAttempts: 3, perTryTimeout: 1s, backoff: 100ms }"))).isEmpty();
    }

    @Test
    void budgetExactlyEqualToTheDeadlineFits() {
        assertThat(a2a(spec("3s", "{ kind: generic, totalAttempts: 3, perTryTimeout: 1s, backoff: 0ms }"))).isEmpty();
    }

    @Test
    void noRetriesNoBudgetFinding() {
        assertThat(a2a(spec("1s", "{ kind: generic, totalAttempts: 1, perTryTimeout: 5s }"))).isEmpty();
    }

    @Test
    void evenTheFirstTryMayNotFit() {
        List<Finding> f = a2a(spec("500ms", "{ kind: generic, totalAttempts: 2, perTryTimeout: 1s, backoff: 0ms }"));
        assertThat(f.get(0).detail()).isEqualTo("budget 2 s > deadline 500 ms, 0 of 2 tries fit");
    }

    @Test
    void exponentialBackoffIsCappedByMaxBackoff() {
        // backoffs: 100, 200, min(400, 250) = 250 -> W_4 = 4 x 1s + 550ms = 4.55s
        List<Finding> f = a2a(spec("4s",
                "{ kind: generic, totalAttempts: 4, perTryTimeout: 1s, backoff: 100ms, multiplier: 2, maxBackoff: 250ms }"));
        assertThat(f.get(0).detail()).isEqualTo("budget 4.55 s > deadline 4 s, 3 of 4 tries fit");
    }

    @Test
    void unknownPerTryTimeoutCannotBeEvaluatedAndIsNamed() {
        List<Finding> f = a2a(spec("3s", "{ kind: generic, totalAttempts: 3, backoff: 100ms }"));
        assertThat(f).hasSize(1);
        assertThat(f.get(0).severity()).isEqualTo(Severity.INFO);
        assertThat(f.get(0).detail()).isEqualTo("cannot evaluate: a→b layer 1 (generic) perTryTimeout UNKNOWN");
        assertThat(f.get(0).unknowns()).containsExactly("a→b layer 1 (generic) perTryTimeout");
        assertThat(f.get(0).values()).anySatisfy(v -> {
            assertThat(v.name()).endsWith("perTryTimeout");
            assertThat(v.display()).isEqualTo("UNKNOWN");
        });
    }

    @Test
    void unknownCallerDeadlineCannotBeEvaluated() {
        List<Finding> f = findings(analyze("""
                services: { a: { entry: true }, b: {} }
                edges:
                  - { from: a, to: b, layers: [ { kind: generic, totalAttempts: 3, perTryTimeout: 1s } ] }
                """), "PLM-A2a");
        assertThat(f.get(0).unknowns()).containsExactly("a deadline");
    }

    @Test
    void unknownAttemptsCannotBeEvaluated() {
        List<Finding> f = a2a(spec("3s", "{ kind: generic, perTryTimeout: 1s }"));
        assertThat(f.get(0).unknowns()).containsExactly("a→b layer 1 (generic) totalAttempts");
    }

    @Test
    void unknownBackoffGivesALowerBoundOnTheBudget() {
        List<Finding> f = a2a(spec("2s", "{ kind: generic, totalAttempts: 3, perTryTimeout: 1s }"));
        assertThat(f.get(0).severity()).isEqualTo(Severity.WARN);
        assertThat(f.get(0).detail()).isEqualTo("budget at least 3 s > deadline 2 s, at most 2 of 3 tries fit");
        assertThat(f.get(0).unknowns()).containsExactly("a→b layer 1 (generic) backoff");

        List<Finding> fits = a2a(spec("5s", "{ kind: generic, totalAttempts: 3, perTryTimeout: 1s }"));
        assertThat(fits.get(0).severity()).isEqualTo(Severity.INFO);
        assertThat(fits.get(0).detail()).isEqualTo(
                "budget at least 3 s <= deadline 5 s; backoff UNKNOWN, cannot confirm that 3 tries fit");
    }

    @Test
    void unknownMaxBackoffWithGrowingBackoffIsOnlyFlaggedWhenItCouldMatter() {
        String growing = "{ kind: generic, totalAttempts: 4, perTryTimeout: 1s, backoff: 1s, multiplier: 2 }";
        List<Finding> f = a2a(spec("5s", growing)); // uncapped W_4 = 4 + 1 + 2 + 4 = 11s
        assertThat(f.get(0).severity()).isEqualTo(Severity.INFO);
        assertThat(f.get(0).unknowns()).containsExactly("a→b layer 1 (generic) maxBackoff");
        assertThat(a2a(spec("20s", growing))).isEmpty(); // fits even without a cap
    }

    @Test
    void severalLayersAreEvaluatedSeparately() {
        List<Finding> f = a2a(spec("2s",
                "{ kind: resilience4j, maxAttempts: 2, perTryTimeout: 500ms, backoff: 0ms }, "
                        + "{ kind: istio, attempts: 2, perTryTimeout: 900ms, backoff: 0ms }"));
        assertThat(f).hasSize(1);
        assertThat(f.get(0).detail()).isEqualTo("layer 2 (istio): budget 2.7 s > deadline 2 s, 2 of 3 tries fit");
    }

    /** The spec loader always sets a multiplier; collectors may not. */
    private static List<Finding> a2aUnknownMultiplier(String callerDeadline, int attempts) {
        RetryPolicy retry = new RetryPolicy(
                Val.explicit(attempts, "app.yml:1"),
                Val.explicit(Duration.ofSeconds(1), "app.yml:2"),
                Val.explicit(Duration.ofMillis(100), "app.yml:3"),
                Val.unknown("exponentialBackoffMultiplier absent"),
                Val.unknown("exponentialMaxWaitDuration absent"),
                Val.explicit(false, "app.yml:4"),
                Val.unknown("retryExceptions absent"));
        Service a = new Service("a", Val.explicit(Duration.parse("PT" + callerDeadline), "spec:1"), true);
        Service b = new Service("b", Val.unknown("deadline absent"), false);
        Edge edge = new Edge("a", "b", null, Val.unknown("idempotent absent"), 1,
                List.of(new Layer("resilience4j", retry, Val.unknown("overallTimeout absent"))));
        ServiceGraph graph = new ServiceGraph(Map.of("a", a, "b", b), List.of(edge));
        return findings(Analyzer.analyze(graph, AnalysisOptions.defaults()), "PLM-A2a");
    }

    @Test
    void unknownMultiplierGivesALowerBound() {
        // multiplier taken as 1: W_4 >= 4 x 1s + 3 x 100ms = 4.3s; W_3 >= 3.2s
        List<Finding> f = a2aUnknownMultiplier("3.2S", 4);
        assertThat(f).hasSize(1);
        assertThat(f.get(0).severity()).isEqualTo(Severity.WARN);
        assertThat(f.get(0).detail()).isEqualTo("budget at least 4.3 s > deadline 3.2 s, at most 3 of 4 tries fit");
        assertThat(f.get(0).unknowns()).containsExactly("a→b layer 1 (resilience4j) multiplier");
    }

    @Test
    void unknownMultiplierCannotConfirmThatTheBudgetFits() {
        List<Finding> f = a2aUnknownMultiplier("5S", 4);
        assertThat(f).hasSize(1);
        assertThat(f.get(0).severity()).isEqualTo(Severity.INFO);
        assertThat(f.get(0).detail())
                .isEqualTo("budget at least 4.3 s <= deadline 5 s; multiplier UNKNOWN, cannot confirm that 4 tries fit");
    }

    @Test
    void unknownMultiplierDoesNotMatterWithASingleBackoff() {
        // two tries: one backoff, the initial one; W_2 = 2.1s exactly
        assertThat(a2aUnknownMultiplier("3S", 2)).isEmpty();
    }
}
