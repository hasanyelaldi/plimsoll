package io.github.hasanyelaldi.plimsoll.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hasanyelaldi.plimsoll.io.SpecLoader;
import io.github.hasanyelaldi.plimsoll.model.ConfigDifference;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import io.github.hasanyelaldi.plimsoll.model.Source;
import java.util.List;
import org.junit.jupiter.api.Test;

class EffectiveConfigRuleTest {

    private static final ServiceGraph GRAPH = SpecLoader.parse("services: { a: { entry: true } }\n", "spec.yaml");
    private static final String ATTEMPTS = "resilience4j.retry.instances.backend.max-attempts";

    private static ConfigDifference attempts(String service, String declared, Source source, String effective) {
        return new ConfigDifference(service, ATTEMPTS, declared, source, service + "/src/main/resources/application.yml:7",
                effective, "dumps/" + service + "-configprops.json:42 (/contexts/" + service + "/beans/x/properties)");
    }

    private static List<Finding> c2(List<ConfigDifference> differences) {
        return Analyzer.analyze(GRAPH, AnalysisOptions.defaults(), List.of(), differences).findings().stream()
                .filter(f -> f.ruleId().equals("PLM-C2")).toList();
    }

    @Test
    void aDifferenceIsWarnWithBothValuesAndTheirEvidence() {
        List<Finding> f = c2(List.of(attempts("orders", "3", Source.EXPLICIT, "5")));
        assertThat(f).singleElement().satisfies(x -> {
            assertThat(x.severity()).isEqualTo(Severity.WARN);
            assertThat(x.location()).isEqualTo("orders");
            assertThat(x.detail()).isEqualTo(ATTEMPTS + ": declared 3, runs 5");
            assertThat(x.dependsOnUnknown()).isFalse();
            assertThat(x.values()).containsExactly(
                    new UsedValue(ATTEMPTS + " declared", "3", Source.EXPLICIT,
                            "orders/src/main/resources/application.yml:7"),
                    new UsedValue(ATTEMPTS + " runs", "5", Source.EXPLICIT,
                            "dumps/orders-configprops.json:42 (/contexts/orders/beans/x/properties)"));
        });
    }

    @Test
    void aSettingTheRepositoryLeavesAtItsDefaultCanDifferToo() {
        List<Finding> f = c2(List.of(attempts("orders", "3", Source.LIBRARY_DEFAULT, "1")));
        assertThat(f.get(0).values().get(0).source()).isEqualTo(Source.LIBRARY_DEFAULT);
    }

    @Test
    void findingsAreSortedByServiceThenSettingWithoutRepeats() {
        ConfigDifference b = attempts("payments", "3", Source.EXPLICIT, "4");
        ConfigDifference a = attempts("orders", "3", Source.EXPLICIT, "4");
        assertThat(c2(List.of(b, a, b))).extracting(Finding::location).containsExactly("orders", "payments");
    }

    @Test
    void noDifferenceNoFinding() {
        assertThat(c2(List.of())).isEmpty();
        assertThat(Analyzer.analyze(GRAPH, AnalysisOptions.defaults()).findings()).isEmpty();
    }

    @Test
    void equalValuesAreNotADifference() {
        assertThatThrownBy(() -> attempts("orders", "3", Source.EXPLICIT, "3"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("both 3");
    }
}
