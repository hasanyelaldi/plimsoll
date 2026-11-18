package io.github.hasanyelaldi.plimsoll.collect.resilience4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hasanyelaldi.plimsoll.analysis.AnalysisOptions;
import io.github.hasanyelaldi.plimsoll.analysis.AnalysisResult;
import io.github.hasanyelaldi.plimsoll.analysis.Analyzer;
import io.github.hasanyelaldi.plimsoll.analysis.ServiceAmplification;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.Resilience4jCollector.Result;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.FailureClass;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.RetryConditions;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.Service;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End to end on the fixture project {@code src/test/resources/projects/gateway}. */
class Resilience4jCollectorTest {

    private static Path root;
    private static Result result;

    @BeforeAll
    static void collect() throws URISyntaxException {
        root = Path.of(Resilience4jCollectorTest.class.getResource("/projects/gateway").toURI());
        result = Resilience4jCollector.collect(root, root.resolve("edges-hint.yaml"));
    }

    private static Edge edge(String operation) {
        return result.edges().stream().filter(e -> e.operation().equals(operation)).findFirst().orElseThrow();
    }

    @Test
    void retryTimeLimiterAndHintMakeOneLayerWithAPerTryTimeout() {
        Edge e = edge("GET /owners/{id}");
        assertThat(e.from()).isEqualTo("api-gateway");
        assertThat(e.to()).isEqualTo("customers-service");
        assertThat(e.idempotent()).isEqualTo(Val.explicit(true, "edges-hint.yaml:6"));
        assertThat(e.layers()).hasSize(1);

        Layer layer = e.layers().get(0);
        RetryPolicy p = layer.retry();
        assertThat(layer.kind()).isEqualTo("resilience4j");
        assertThat(p.totalAttempts()).isEqualTo(Val.explicit(3, "src/main/resources/application.yml:5 (maxAttempts)"));
        assertThat(p.initialBackoff().value()).isEqualTo(Duration.ofMillis(100));
        assertThat(p.perTryTimeout()).isEqualTo(Val.explicit(Duration.ofMillis(800), "src/main/resources/application.yml:15"));
        assertThat(layer.overallTimeout().source()).isEqualTo(Source.UNKNOWN);
    }

    /** ADR 0014: without retryExceptions every exception is retried, so every failure class; failures pass on as is. */
    @Test
    void defaultRetryConditionsCoverEveryFailureClass() {
        RetryConditions c = edge("GET /owners/{id}").layers().get(0).conditions();
        assertThat(c.retried().value()).containsExactlyInAnyOrder(FailureClass.values());
        assertThat(c.retried().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(c.retried().evidence()).startsWith("retryExceptions not configured; resilience4j-retry 2.4.0 default "
                + "(verified by oracle)");
        assertThat(c.givesUpAs().value()).isEqualTo(Map.of(FailureClass.NOT_SENT, Set.of(FailureClass.NOT_SENT),
                FailureClass.RESET, Set.of(FailureClass.RESET), FailureClass.TIMEOUT, Set.of(FailureClass.TIMEOUT)));
    }

    /** Which exception an HTTP client throws for a failure is client-specific, so listed exceptions are not mapped. */
    @Test
    void listedRetryExceptionsLeaveTheClassesUnknown() {
        Val<Set<FailureClass>> retried = DefaultsKb.classpath().retried(Resilience4jConfig.RETRY_KB,
                Val.explicit(Set.of("java.io.IOException"), "application.yml:9 (retryExceptions)"), c -> null);
        assertThat(retried.source()).isEqualTo(Source.UNKNOWN);
        assertThat(retried.evidence()).isEqualTo("application.yml:9 (retryExceptions); no verified resilience4j-retry "
                + "mapping for retry condition 'java.io.IOException', so every failure class is retried");
    }

    @Test
    void classLevelRetryAppliesToTheMethodAndKeepsFanout() {
        Edge e = edge("com.example.gateway.VisitsClient#list");
        RetryPolicy p = e.layers().get(0).retry();
        assertThat(e.fanout()).isEqualTo(Val.explicit(2, "edges-hint.yaml:9"));
        assertThat(p.totalAttempts().value()).isEqualTo(2);
        assertThat(p.multiplier()).isEqualTo(Val.explicit(2.0, "src/main/resources/application.yml:11"));
        assertThat(p.perTryTimeout().evidence()).startsWith("no @TimeLimiter on com.example.gateway.VisitsClient#list");
    }

    @Test
    void retryNameFromAConstantIsUnknown() {
        RetryPolicy p = edge("com.example.gateway.CustomersClient#getVisits").layers().get(0).retry();
        assertThat(p.totalAttempts().source()).isEqualTo(Source.UNKNOWN);
        assertThat(p.totalAttempts().evidence()).isEqualTo(
                "@Retry instance not known: src/main/java/com/example/gateway/CustomersClient.java:19: "
                        + "name is not a string literal: VISITS");
    }

    @Test
    void timeLimiterOnASynchronousMethodIsNotUsedAndNoted() {
        Layer layer = edge("com.example.gateway.CustomersClient#syncLookup").layers().get(0);
        assertThat(layer.retry().totalAttempts().value()).isEqualTo(1);
        assertThat(layer.retry().perTryTimeout().source()).isEqualTo(Source.UNKNOWN);
        assertThat(result.notes()).contains("src/main/java/com/example/gateway/CustomersClient.java:24: @TimeLimiter on "
                + "com.example.gateway.CustomersClient#syncLookup returning String: Resilience4j fails every call "
                + "(CompletionStage expected)");
    }

    @Test
    void methodWithoutAnnotationsHasNoLayers() {
        assertThat(edge("com.example.gateway.CustomersClient#plain").layers()).isEmpty();
    }

    @Test
    void circuitBreakersAreDetectedButNotModelled() {
        assertThat(result.notes()).contains("src/main/java/com/example/gateway/CustomersClient.java:13: "
                + "@CircuitBreaker(name = \"customers\") on com.example.gateway.CustomersClient#getOwner found; "
                + "circuit breakers are not modelled yet");
    }

    @Test
    void profilesAndOtherConfigFilesAreLeftToTheUnreadSourcesNotNoted() {
        assertThat(result.notes()).noneMatch(n -> n.contains("application-docker.yml") || n.contains("on-profile"));
    }

    @Test
    void annotatedMethodsWithoutAHintAreListed() {
        assertThat(result.unmapped()).containsExactly(
                "com.example.gateway.CustomersClient#notInTheHint "
                        + "(src/main/java/com/example/gateway/CustomersClient.java:30, @Retry)",
                "com.example.gateway.LookAlikes#qualified "
                        + "(src/main/java/com/example/gateway/LookAlikes.java:16, @CircuitBreaker)");
    }

    @Test
    void collectedEdgesFeedTheAnalyzer() {
        Map<String, Service> services = new LinkedHashMap<>();
        services.put("api-gateway", new Service("api-gateway", Val.unknown("deadline not collected"), true));
        services.put("customers-service", new Service("customers-service", Val.unknown("deadline not collected"), false));
        services.put("visits-service", new Service("visits-service", Val.unknown("deadline not collected"), false));
        AnalysisResult r = Analyzer.analyze(new ServiceGraph(services, result.edges()), AnalysisOptions.defaults());

        // getOwner x3 + syncLookup x1 + plain x1
        assertThat(r.amplification()).filteredOn(a -> a.service().equals("customers-service"))
                .extracting(ServiceAmplification::value).containsExactly(BigInteger.valueOf(5));
    }

    @Test
    void hintNamingAMissingMethodIsInvalid(@TempDir Path tmp) throws IOException {
        Path hint = tmp.resolve("edges-hint.yaml");
        Files.writeString(hint, "service: api-gateway\nmethods:\n"
                + "  - { method: com.example.gateway.CustomersClient#nope, to: x }\n");

        assertThatThrownBy(() -> Resilience4jCollector.collect(root, hint))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessage("edges-hint.yaml:3: method com.example.gateway.CustomersClient#nope not found");
    }
}
