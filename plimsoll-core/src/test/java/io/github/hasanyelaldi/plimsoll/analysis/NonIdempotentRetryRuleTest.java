package io.github.hasanyelaldi.plimsoll.analysis;

import static io.github.hasanyelaldi.plimsoll.analysis.Specs.analyze;
import static io.github.hasanyelaldi.plimsoll.analysis.Specs.findings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;

class NonIdempotentRetryRuleTest {

    private static List<Finding> a3(String edgeAttributes, String layers) {
        return findings(analyze("services: { a: { entry: true }, b: {} }\n"
                + "edges:\n  - { from: a, to: b, " + edgeAttributes + "layers: [ " + layers + " ] }\n"), "PLM-A3");
    }

    @Test
    void retriedNonIdempotentEdgeIsError() {
        List<Finding> f = a3("idempotent: false, ", "{ kind: istio, attempts: 2 }");
        assertThat(f).hasSize(1);
        assertThat(f.get(0).severity()).isEqualTo(Severity.ERROR);
        assertThat(f.get(0).location()).isEqualTo("a→b");
        assertThat(f.get(0).detail()).isEqualTo("non-idempotent call retried, 3 tries");
        assertThat(f.get(0).dependsOnUnknown()).isFalse();
        assertThat(f.get(0).values()).extracting(UsedValue::name)
                .containsExactly("a→b idempotent", "a→b layer 1 (istio) totalAttempts");
        assertThat(f.get(0).values().get(0).evidence()).isEqualTo("spec.yaml:3");
    }

    @Test
    void triesMultiplyAcrossLayers() {
        List<Finding> f = a3("idempotent: false, ",
                "{ kind: resilience4j, maxAttempts: 3 }, { kind: istio, attempts: 3 }");
        assertThat(f.get(0).detail()).isEqualTo("non-idempotent call retried, 12 tries");
    }

    @Test
    void idempotentEdgeMayRetry() {
        assertThat(a3("idempotent: true, ", "{ kind: resilience4j, maxAttempts: 3 }")).isEmpty();
    }

    @Test
    void nonIdempotentEdgeWithoutRetriesIsSilent() {
        assertThat(a3("idempotent: false, ", "{ kind: resilience4j, maxAttempts: 1 }")).isEmpty();
        assertThat(a3("idempotent: false, ", "{ kind: istio, attempts: 0 }")).isEmpty();
        assertThat(findings(analyze("services: { a: { entry: true }, b: {} }\n"
                + "edges:\n  - { from: a, to: b, idempotent: false }\n"), "PLM-A3")).isEmpty();
    }

    @Test
    void unknownIdempotencyIsNotAssumed() {
        assertThat(a3("", "{ kind: resilience4j, maxAttempts: 3 }")).isEmpty();
    }

    @Test
    void unknownAttemptsWithoutAKnownRetryCannotBeEvaluated() {
        List<Finding> f = a3("idempotent: false, ", "{ kind: generic }");
        assertThat(f.get(0).severity()).isEqualTo(Severity.INFO);
        assertThat(f.get(0).detail()).isEqualTo("cannot evaluate: a→b layer 1 (generic) totalAttempts UNKNOWN");
        assertThat(f.get(0).unknowns()).containsExactly("a→b layer 1 (generic) totalAttempts");
    }

    @Test
    void knownRetryNextToUnknownAttemptsIsAtLeast() {
        List<Finding> f = a3("idempotent: false, ", "{ kind: generic }, { kind: istio, attempts: 1 }");
        assertThat(f.get(0).severity()).isEqualTo(Severity.ERROR);
        assertThat(f.get(0).detail()).isEqualTo("non-idempotent call retried, at least 2 tries");
        assertThat(f.get(0).unknowns()).containsExactly("a→b layer 1 (generic) totalAttempts");
    }

    @Test
    void edgesThatShareCallerAndCalleeAreLocatedWithTheirOperation() {
        List<Finding> f = findings(analyze("""
                services: { gw: { entry: true }, auth: {}, other: {} }
                edges:
                  - { from: gw, to: auth, operation: POST /login, idempotent: false, layers: [ { kind: istio, attempts: 2 } ] }
                  - { from: gw, to: auth, operation: POST /users, idempotent: false, layers: [ { kind: istio, attempts: 2 } ] }
                  - { from: gw, to: other, operation: POST /x, idempotent: false, layers: [ { kind: istio, attempts: 2 } ] }
                """), "PLM-A3");
        assertThat(f).extracting(Finding::location)
                .containsExactly("gw→auth (POST /login)", "gw→auth (POST /users)", "gw→other");
        assertThat(f.get(0).values()).extracting(UsedValue::name)
                .containsExactly("gw→auth (POST /login) idempotent", "gw→auth (POST /login) layer 1 (istio) totalAttempts");
    }

    @Test
    void retriedOnlyWhenNotSentIsInfo() {
        List<Finding> f = a3("idempotent: false, ", "{ kind: generic, totalAttempts: 3, retryOn: [not-sent] }");
        assertThat(f).hasSize(1);
        assertThat(f.get(0).severity()).isEqualTo(Severity.INFO);
        assertThat(f.get(0).detail()).isEqualTo("retried only when the request was not sent, 3 tries");
        assertThat(f.get(0).dependsOnUnknown()).isFalse();
        assertThat(f.get(0).values()).extracting(UsedValue::name, UsedValue::display).contains(
                tuple("a→b layer 1 (generic) retried", "not-sent"));
    }

    @Test
    void aRetriedProcessedClassIsErrorAndNamed() {
        List<Finding> f = a3("idempotent: false, ", "{ kind: generic, totalAttempts: 3, retryOn: [not-sent, 503] }");
        assertThat(f.get(0).severity()).isEqualTo(Severity.ERROR);
        assertThat(f.get(0).detail()).isEqualTo("non-idempotent call retried, 3 tries (on status-503)");
    }

    @Test
    void triesOfTheWorstProcessedClassNotTheProductOfAllLayers() {
        // outer retries 503 twice, inner retries not-sent three times: a processed failure is tried twice
        List<Finding> f = a3("idempotent: false, ", "{ kind: generic, totalAttempts: 2, retryOn: [503] }, "
                + "{ kind: generic, totalAttempts: 3, retryOn: [not-sent] }");
        assertThat(f.get(0).severity()).isEqualTo(Severity.ERROR);
        assertThat(f.get(0).detail()).isEqualTo("non-idempotent call retried, 2 tries (on status-503)");
    }

    @Test
    void anUnknownTranslationNamesNoClass() {
        // a spec's istio layer may pass on any class, so the outer layer may retry a processed failure
        List<Finding> f = a3("idempotent: false, ", "{ kind: resilience4j, maxAttempts: 2, retryOn: [503] }, "
                + "{ kind: istio, attempts: 2, retryOn: [not-sent] }");
        assertThat(f.get(0).severity()).isEqualTo(Severity.ERROR);
        assertThat(f.get(0).detail()).isEqualTo("non-idempotent call retried, 2 tries");
        assertThat(f.get(0).dependsOnUnknown()).isFalse();
    }

    @Test
    void aRetryOnNoFailureIsSilent() {
        assertThat(a3("idempotent: false, ", "{ kind: generic, totalAttempts: 3, retryOn: [] }")).isEmpty();
    }

    @Test
    void unknownConditionsKeepTodaysFinding() {
        List<Finding> f = a3("idempotent: false, ", "{ kind: istio, attempts: 2 }");
        assertThat(f.get(0).detail()).isEqualTo("non-idempotent call retried, 3 tries");
        assertThat(f.get(0).values()).extracting(UsedValue::name).noneMatch(n -> n.endsWith("retried"));
    }
}
