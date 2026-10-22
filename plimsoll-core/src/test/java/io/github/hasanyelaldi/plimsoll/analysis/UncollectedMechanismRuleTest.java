package io.github.hasanyelaldi.plimsoll.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.hasanyelaldi.plimsoll.io.SpecLoader;
import io.github.hasanyelaldi.plimsoll.model.Detection;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import io.github.hasanyelaldi.plimsoll.model.Source;
import java.util.List;
import org.junit.jupiter.api.Test;

class UncollectedMechanismRuleTest {

    private static final String RETRY_DOC = "https://github.com/spring-projects/spring-retry";
    private static final ServiceGraph GRAPH = SpecLoader.parse("services: { a: { entry: true } }\n", "spec.yaml");

    private static Detection retryable(String service, String evidence) {
        return new Detection(service, "Spring Retry", "retry",
                "annotation org.springframework.retry.annotation.Retryable", evidence, RETRY_DOC);
    }

    private static List<Finding> c1(List<Detection> detections) {
        return Analyzer.analyze(GRAPH, AnalysisOptions.defaults(), detections).findings().stream()
                .filter(f -> f.ruleId().equals("PLM-C1")).toList();
    }

    @Test
    void detectedMechanismIsWarnWithItsEvidence() {
        List<Finding> f = c1(List.of(retryable("orders", "orders/src/main/java/Orders.java:12")));
        assertThat(f).hasSize(1);
        assertThat(f.get(0).severity()).isEqualTo(Severity.WARN);
        assertThat(f.get(0).location()).isEqualTo("orders");
        assertThat(f.get(0).detail()).isEqualTo(
                "Spring Retry detected, not modelled; edges from orders may carry an unseen retry layer");
        assertThat(f.get(0).dependsOnUnknown()).isFalse();
        assertThat(f.get(0).values()).containsExactly(new UsedValue("orders Spring Retry",
                "annotation org.springframework.retry.annotation.Retryable", Source.EXPLICIT,
                "orders/src/main/java/Orders.java:12; " + RETRY_DOC));
    }

    @Test
    void matchesOfOneMechanismInOneServiceAreOneFindingInEvidenceOrder() {
        List<Finding> f = c1(List.of(
                retryable("orders", "orders/src/main/java/B.java:3"),
                new Detection("orders", "Spring Retry", "retry", "dependency org.springframework.retry:spring-retry",
                        "orders/pom.xml:20", RETRY_DOC),
                retryable("orders", "orders/src/main/java/A.java:7"),
                retryable("orders", "orders/src/main/java/A.java:7")));
        assertThat(f).hasSize(1);
        assertThat(f.get(0).values()).extracting(UsedValue::evidence).containsExactly(
                "orders/pom.xml:20; " + RETRY_DOC,
                "orders/src/main/java/A.java:7; " + RETRY_DOC,
                "orders/src/main/java/B.java:3; " + RETRY_DOC);
    }

    @Test
    void oneFindingPerServiceAndMechanismSortedByService() {
        List<Finding> f = c1(List.of(
                retryable("payments", "payments/P.java:1"),
                new Detection("orders", "Spring Cloud CircuitBreaker", "timeout",
                        "type org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory", "orders/O.java:2",
                        "https://docs.spring.io/spring-cloud-circuitbreaker/reference/"),
                retryable("orders", "orders/O.java:1")));
        assertThat(f).extracting(Finding::location, Finding::detail).containsExactly(
                tuple("orders",
                        "Spring Cloud CircuitBreaker detected, not modelled; edges from orders may carry an unseen "
                                + "timeout layer"),
                tuple("orders",
                        "Spring Retry detected, not modelled; edges from orders may carry an unseen retry layer"),
                tuple("payments",
                        "Spring Retry detected, not modelled; edges from payments may carry an unseen retry layer"));
    }

    @Test
    void noDetectionNoFinding() {
        assertThat(c1(List.of())).isEmpty();
        assertThat(Analyzer.analyze(GRAPH, AnalysisOptions.defaults()))
                .isEqualTo(Analyzer.analyze(GRAPH, AnalysisOptions.defaults(), List.of()));
    }
}
