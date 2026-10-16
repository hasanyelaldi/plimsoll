package io.github.hasanyelaldi.plimsoll.analysis;

import static io.github.hasanyelaldi.plimsoll.analysis.Specs.analyze;
import static io.github.hasanyelaldi.plimsoll.analysis.Specs.findings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;

class BackoffHygieneRuleTest {

    private static List<Finding> a4(String layers) {
        return findings(analyze("services: { a: { entry: true }, b: {} }\n"
                + "edges:\n  - { from: a, to: b, layers: [ " + layers + " ] }\n"), "PLM-A4");
    }

    @Test
    void zeroBackoffOnARetryIsWarn() {
        List<Finding> f = a4("{ kind: resilience4j, maxAttempts: 3, backoff: 0ms, jitter: true }");
        assertThat(f).hasSize(1);
        assertThat(f.get(0).severity()).isEqualTo(Severity.WARN);
        assertThat(f.get(0).location()).isEqualTo("a→b");
        assertThat(f.get(0).detail()).isEqualTo("zero backoff");
        assertThat(f.get(0).values()).extracting(UsedValue::name, UsedValue::display).containsExactly(
                tuple("a→b layer 1 (resilience4j) totalAttempts", "3"),
                tuple("a→b layer 1 (resilience4j) backoff", "0 ms"));
    }

    @Test
    void retryWithoutJitterIsInfo() {
        List<Finding> f = a4("{ kind: resilience4j, maxAttempts: 3, backoff: 100ms, jitter: false }");
        assertThat(f).hasSize(1);
        assertThat(f.get(0).severity()).isEqualTo(Severity.INFO);
        assertThat(f.get(0).detail()).isEqualTo("no jitter");
    }

    @Test
    void zeroBackoffWithoutJitterGivesBoth() {
        assertThat(a4("{ kind: resilience4j, maxAttempts: 3, backoff: 0ms, jitter: false }"))
                .extracting(Finding::severity, Finding::detail)
                .containsExactly(
                        tuple(Severity.WARN, "zero backoff"),
                        tuple(Severity.INFO, "no jitter"));
    }

    @Test
    void healthyBackoffIsSilent() {
        assertThat(a4("{ kind: istio, attempts: 2, backoff: 25ms, jitter: true }")).isEmpty();
    }

    @Test
    void layersThatDoNotRetryAreNotJudged() {
        assertThat(a4("{ kind: resilience4j, maxAttempts: 1, backoff: 0ms, jitter: false }")).isEmpty();
        assertThat(a4("{ kind: istio, attempts: 0, backoff: 0ms, jitter: false }")).isEmpty();
    }

    @Test
    void unknownValuesAreNotGuessed() {
        assertThat(a4("{ kind: resilience4j, maxAttempts: 3 }")).isEmpty();
        assertThat(a4("{ kind: generic, backoff: 0ms, jitter: false }")).isEmpty(); // totalAttempts UNKNOWN
    }

    @Test
    void eachLayerIsJudgedOnItsOwn() {
        List<Finding> f = a4("{ kind: resilience4j, maxAttempts: 3, backoff: 100ms, jitter: true }, "
                + "{ kind: istio, attempts: 2, backoff: 0ms, jitter: true }");
        assertThat(f).extracting(Finding::detail).containsExactly("layer 2 (istio): zero backoff");
    }
}
