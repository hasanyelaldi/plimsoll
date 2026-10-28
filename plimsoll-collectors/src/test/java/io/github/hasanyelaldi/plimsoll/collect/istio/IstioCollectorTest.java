package io.github.hasanyelaldi.plimsoll.collect.istio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hasanyelaldi.plimsoll.collect.istio.IstioCollector.OutlierDetection;
import io.github.hasanyelaldi.plimsoll.collect.istio.IstioCollector.Result;
import io.github.hasanyelaldi.plimsoll.collect.istio.IstioCollector.Route;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Durations;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** On the fixture manifests in {@code src/test/resources/istio/mesh}. */
class IstioCollectorTest {

    private static final String VS = "virtual-services.yaml";
    private static final String MORE = "nested/more.yml";

    private static Path root;
    private static Result result;

    @BeforeAll
    static void collect() throws URISyntaxException {
        root = Path.of(IstioCollectorTest.class.getResource("/istio/mesh").toURI());
        result = IstioCollector.collect(root);
    }

    private static Route route(String to, String name) {
        return result.routes().stream().filter(r -> r.to().equals(to) && r.route().equals(name))
                .findFirst().orElseThrow();
    }

    @Test
    void explicitRetriesAreNormalizedToTotalAttemptsWithFileLine() {
        Route r = route("orders", "default");
        assertThat(r.host()).isEqualTo("orders.shop.svc.cluster.local");
        assertThat(r.evidence()).isEqualTo(VS + ":10");

        Layer layer = r.layer();
        RetryPolicy p = layer.retry();
        assertThat(layer.kind()).isEqualTo("istio");
        assertThat(p.totalAttempts()).isEqualTo(Val.explicit(3, VS + ":13 (attempts 2 + 1)"));
        assertThat(p.perTryTimeout()).isEqualTo(Val.explicit(Duration.ofMillis(800), VS + ":14"));
        assertThat(p.initialBackoff()).isEqualTo(Val.explicit(Duration.ofMillis(50), VS + ":16"));
        assertThat(p.retryOn()).isEqualTo(Val.explicit(Set.of("5xx", "reset", "503"), VS + ":15"));
        assertThat(layer.overallTimeout()).isEqualTo(Val.explicit(Duration.ofSeconds(2), VS + ":11"));
    }

    @Test
    void attemptsZeroDisablesRetries() {
        RetryPolicy p = route("payments", "http[0]").layer().retry();
        assertThat(p.totalAttempts()).isEqualTo(Val.explicit(1, VS + ":42 (attempts 0 + 1)"));
    }

    @Test
    void absentRetriesUseTheVerifiedIstioDefault() {
        Layer layer = route("payments", "http[1]").layer();
        RetryPolicy p = layer.retry();
        assertThat(p.totalAttempts().value()).isEqualTo(3);
        assertThat(p.totalAttempts().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(p.totalAttempts().evidence())
                .startsWith("no retries at " + VS + ":43; istio 1.31 default (verified by doc), https://istio.io/")
                .endsWith("(attempts 2 + 1)");
        assertThat(p.retryOn().value()).containsExactlyInAnyOrder(
                "connect-failure", "refused-stream", "unavailable", "cancelled");
        assertThat(p.initialBackoff().value()).isEqualTo(Duration.ofMillis(25));
        assertThat(p.jitter().value()).isTrue();
        assertThat(p.jitter().evidence()).contains("envoyproxy.io");
    }

    @Test
    void perTryTimeoutFallsBackToTheRouteTimeout() {
        Val<Duration> perTry = route("payments", "http[1]").layer().retry().perTryTimeout();
        assertThat(perTry.value()).isEqualTo(Duration.ofMillis(1500));
        assertThat(perTry.source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(perTry.evidence()).startsWith("the route timeout (" + VS + ":43); perTryTimeout: no retries at " + VS + ":43");
    }

    @Test
    void noRouteTimeoutMeansNoPerTryTimeout() {
        Layer layer = route("payments", "http[0]").layer();
        assertThat(layer.overallTimeout().value()).isEqualTo(Durations.UNLIMITED);
        assertThat(layer.overallTimeout().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(layer.overallTimeout().evidence()).startsWith("no route timeout at ")
                .contains("istio 1.31 default (verified by doc)");
        assertThat(layer.retry().perTryTimeout().value()).isEqualTo(Durations.UNLIMITED);
        assertThat(layer.retry().perTryTimeout().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(layer.retry().perTryTimeout().evidence()).startsWith("the route timeout (no route timeout at ");
    }

    @Test
    void envoyBackoffHasNoFixedMultiplierOrCap() {
        RetryPolicy p = route("orders", "default").layer().retry();
        assertThat(p.multiplier().source()).isEqualTo(Source.UNKNOWN);
        assertThat(p.maxBackoff().source()).isEqualTo(Source.UNKNOWN);
    }

    @Test
    void retriesWithoutAttemptsAreUnknownAndGoDurationsParse() {
        Route r = route("ledger", "http[0]");
        RetryPolicy p = r.layer().retry();
        assertThat(p.totalAttempts().source()).isEqualTo(Source.UNKNOWN);
        assertThat(p.totalAttempts().evidence()).contains("has no attempts");
        assertThat(p.perTryTimeout()).isEqualTo(Val.explicit(Duration.ofSeconds(90), MORE + ":11"));
    }

    @Test
    void shortHostWithoutNamespaceStaysShortWithANote() {
        assertThat(route("ledger", "http[0]").host()).isEqualTo("ledger");
        assertThat(result.notes()).anySatisfy(n -> assertThat(n).startsWith(MORE + ":7: short host ledger"));
    }

    @Test
    void routesFollowFileThenDocumentOrderAndSkipGatewayOnlyRules() {
        assertThat(result.routes()).extracting(r -> r.to() + "/" + r.route()).containsExactly(
                "ledger/http[0]", "orders/default", "payments/http[0]", "payments/http[1]");
        assertThat(result.notes()).anySatisfy(n -> assertThat(n).contains("wildcard host *.example.com"));
        assertThat(result.notes()).anySatisfy(n -> assertThat(n)
                .isEqualTo(MORE + ":24: VirtualService edge binds gateways only, not the mesh; skipped"));
        assertThat(result.notes()).anySatisfy(n -> assertThat(n)
                .isEqualTo(VS + ":36: host payments.shop.svc.cluster.local has 2 HTTP routes; "
                        + "the first one whose match fits a request applies"));
    }

    @Test
    void outlierDetectionIsReadPerSubsetWithDefaults() {
        assertThat(result.outlierDetections()).hasSize(2);
        OutlierDetection top = result.outlierDetections().get(0);
        assertThat(top.to()).isEqualTo("payments");
        assertThat(top.host()).isEqualTo("payments.shop.svc.cluster.local");
        assertThat(top.subset()).isNull();
        assertThat(top.consecutive5xxErrors()).isEqualTo(Val.explicit(3, MORE + ":42"));
        assertThat(top.interval()).isEqualTo(Val.explicit(Duration.ofSeconds(5), MORE + ":43"));
        assertThat(top.baseEjectionTime().value()).isEqualTo(Duration.ofSeconds(30));
        assertThat(top.baseEjectionTime().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(top.maxEjectionPercent().value()).isEqualTo(10);
        assertThat(top.consecutiveGatewayErrors().value()).isZero();

        OutlierDetection v2 = result.outlierDetections().get(1);
        assertThat(v2.subset()).isEqualTo("v2");
        assertThat(v2.baseEjectionTime()).isEqualTo(Val.explicit(Duration.ofMinutes(1), MORE + ":48"));
        assertThat(v2.consecutive5xxErrors().value()).isEqualTo(5);

        assertThat(result.notes()).anySatisfy(n -> assertThat(n).startsWith(MORE + ":38: connectionPool"));
        assertThat(result.notes()).anySatisfy(n -> assertThat(n).isEqualTo(MORE
                + ":41: outlierDetection for payments.shop.svc.cluster.local found; circuit breaking is not analysed yet"));
    }

    @Test
    void withoutAVerifiedKbEntryAbsentValuesAreUnknown() {
        Result r = IstioCollector.collect(root, DefaultsKb.of(Map.of()));
        Route payments = r.routes().stream().filter(x -> x.route().equals("http[1]")).findFirst().orElseThrow();
        RetryPolicy p = payments.layer().retry();
        assertThat(p.totalAttempts().source()).isEqualTo(Source.UNKNOWN);
        assertThat(p.totalAttempts().evidence()).endsWith("no verified istio default for retries.attempts");
        assertThat(p.perTryTimeout().source()).isEqualTo(Source.UNKNOWN);
        assertThat(p.jitter().source()).isEqualTo(Source.UNKNOWN);
        assertThat(r.outlierDetections().get(0).baseEjectionTime().source()).isEqualTo(Source.UNKNOWN);
    }

    @Test
    void unverifiedKbEntryIsIgnored() {
        String seed = """
                - library: istio
                  versions: unverified
                  parameter: retries.attempts
                  value: 2
                  unit: attempts-retries-only
                  source: https://istio.io/
                  verified_by: none
                """;
        Result r = IstioCollector.collect(root, DefaultsKb.of(Map.of("istio", seed)));
        assertThat(r.routes().get(3).layer().retry().totalAttempts().source()).isEqualTo(Source.UNKNOWN);
    }

    @Test
    void singleFileIsAccepted() {
        Result r = IstioCollector.collect(root.resolve(VS));
        assertThat(r.routes()).hasSize(3);
        assertThat(r.routes().get(0).evidence()).isEqualTo(VS + ":10");
    }

    @Test
    void sameOutputOnEveryRun() {
        assertThat(IstioCollector.collect(root)).isEqualTo(result);
    }

    @Test
    void invalidValuesCiteFileAndLine(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("bad.yaml"), """
                apiVersion: networking.istio.io/v1
                kind: VirtualService
                metadata: { name: x, namespace: n }
                spec:
                  hosts: [ x ]
                  http:
                    - retries:
                        attempts: -1
                """);
        assertThatThrownBy(() -> IstioCollector.collect(dir))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessage("bad.yaml:8: 'attempts' must be >= 0");

        Files.writeString(dir.resolve("bad.yaml"), """
                apiVersion: networking.istio.io/v1
                kind: VirtualService
                metadata: { name: x, namespace: n }
                spec:
                  hosts: [ x ]
                  http:
                    - timeout: 3 seconds
                """);
        assertThatThrownBy(() -> IstioCollector.collect(dir))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessageStartingWith("bad.yaml:7: invalid duration '3 seconds'");
    }

    @Test
    void goDurations() {
        assertThat(IstioCollector.parseDuration("1h", "t")).isEqualTo(Duration.ofHours(1));
        assertThat(IstioCollector.parseDuration("1m30s", "t")).isEqualTo(Duration.ofSeconds(90));
        assertThat(IstioCollector.parseDuration("0.5s", "t")).isEqualTo(Duration.ofMillis(500));
        assertThat(IstioCollector.parseDuration("250ms", "t")).isEqualTo(Duration.ofMillis(250));
        assertThatThrownBy(() -> IstioCollector.parseDuration("10", "t")).isInstanceOf(InvalidSpecException.class);
        assertThatThrownBy(() -> IstioCollector.parseDuration("s", "t")).isInstanceOf(InvalidSpecException.class);
    }
}
