package io.github.hasanyelaldi.plimsoll.collect.effective;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hasanyelaldi.plimsoll.io.SpecLoader;
import io.github.hasanyelaldi.plimsoll.model.ConfigDifference;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import io.github.hasanyelaldi.plimsoll.model.Source;
import java.util.List;
import org.junit.jupiter.api.Test;

class EffectiveDiffTest {

    private static ServiceGraph spec(String edges) {
        return SpecLoader.parse("services: { a: { entry: true }, b: {}, c: {} }\nedges:\n" + edges, "spec.yaml");
    }

    private static final String AB = "  - { from: a, to: b, layers: [ { kind: resilience4j, maxAttempts: 3, "
            + "perTryTimeout: 1s, backoff: 100ms } ] }\n";

    @Test
    void sameValuesGiveNoDifference() {
        assertThat(EffectiveDiff.between(spec(AB), spec(AB), "a")).isEmpty();
    }

    @Test
    void eachChangedValueIsOneDifference() {
        List<ConfigDifference> d = EffectiveDiff.between(spec(AB), spec(AB.replace("maxAttempts: 3", "maxAttempts: 5")
                .replace("1s", "2s")), "a");
        assertThat(d).extracting(ConfigDifference::setting, ConfigDifference::declared, ConfigDifference::effective)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("a→b layer 1 (resilience4j) totalAttempts", "3", "5"),
                        org.assertj.core.groups.Tuple.tuple("a→b layer 1 (resilience4j) perTryTimeout", "1 s", "2 s"));
        assertThat(d.get(0).declaredSource()).isEqualTo(Source.EXPLICIT);
        assertThat(d.get(0).declaredEvidence()).isEqualTo("spec.yaml:3 (maxAttempts)");
    }

    @Test
    void anEdgeOnlyOneSideHasIsADifference() {
        String ac = "  - { from: a, to: c, layers: [ { kind: generic, totalAttempts: 1 } ] }\n";
        assertThat(EffectiveDiff.between(spec(AB), spec(AB + ac), "a")).singleElement().satisfies(x -> {
            assertThat(x.setting()).isEqualTo("a→c");
            assertThat(x.declared()).isEqualTo("no edge");
            assertThat(x.effective()).isEqualTo("an edge with layers generic");
        });
        assertThat(EffectiveDiff.between(spec(AB + ac), spec(AB), "a")).singleElement()
                .satisfies(x -> assertThat(x.effective()).isEqualTo("no edge"));
    }

    @Test
    void onlyTheEdgesOfTheDumpedServiceAreCompared() {
        String bc = "  - { from: b, to: c, layers: [ { kind: generic, totalAttempts: 1 } ] }\n";
        assertThat(EffectiveDiff.between(spec(AB + bc), spec(AB + bc.replace("totalAttempts: 1", "totalAttempts: 2")),
                "a")).isEmpty();
    }

    @Test
    void breakerSettingsAreCompared() {
        String with = AB.replace("backoff: 100ms", "backoff: 100ms, circuitBreaker: { name: cb, slidingWindowSize: 10 }");
        assertThat(EffectiveDiff.between(spec(with), spec(with.replace("slidingWindowSize: 10", "slidingWindowSize: 20")),
                "a")).extracting(ConfigDifference::setting)
                .containsExactly("a→b layer 1 (resilience4j) circuitBreaker slidingWindowSize");
        assertThat(EffectiveDiff.between(spec(AB), spec(with), "a")).extracting(ConfigDifference::setting)
                .containsExactly("a→b layer 1 (resilience4j) circuitBreaker");
    }
}
