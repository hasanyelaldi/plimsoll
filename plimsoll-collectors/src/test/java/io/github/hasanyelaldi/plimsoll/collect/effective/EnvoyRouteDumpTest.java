package io.github.hasanyelaldi.plimsoll.collect.effective;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hasanyelaldi.plimsoll.collect.effective.EnvoyRouteDump.Dump;
import io.github.hasanyelaldi.plimsoll.collect.istio.IstioCollector.Route;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Durations;
import io.github.hasanyelaldi.plimsoll.model.FailureClass;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.Source;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** ADR 0016, #25 part 3: Envoy route configurations as istio layers, with Envoy's defaults. */
class EnvoyRouteDumpTest {

    /** Istio's own test data, unchanged; see its README. */
    private static final Path FIXTURE = Path.of("src/test/resources/effective/envoy/http_config.json");
    private static final DefaultsKb KB = DefaultsKb.classpath();

    private static Dump parse(String json) {
        return EnvoyRouteDump.parse(json, "dump.json", KB);
    }

    private static String routes(String routeConfigs) {
        return """
                { "configs": [ { "@type": "type.googleapis.com/envoy.admin.v3.RoutesConfigDump",
                  "dynamic_route_configs": [ %s ] } ] }
                """.formatted(routeConfigs);
    }

    private static Layer layerTo(Dump d, String to) {
        return d.routes().stream().filter(r -> r.to().equals(to)).findFirst().orElseThrow().layer();
    }

    @Test
    void istiosTestDataGivesTheRoutesToProductpageWithTheirRetryPolicy() throws IOException {
        Dump d = EnvoyRouteDump.parse(Files.readString(FIXTURE), "dumps/envoy/http_config.json", KB);
        assertThat(d.routes()).hasSize(5).allSatisfy(r -> {
            assertThat(r.to()).isEqualTo("productpage");
            assertThat(r.host()).isEqualTo("productpage.default.svc.cluster.local");
        });
        Route first = d.routes().get(0);
        assertThat(first.route()).isEqualTo("*:80 routes[0]");
        assertThat(first.evidence()).isEqualTo("dumps/envoy/http_config.json:18 (/configs/0/dynamic_route_configs/0/"
                + "route_config/virtual_hosts/0/routes/0)");
        Layer l = first.layer();
        assertThat(l.kind()).isEqualTo("istio");
        assertThat(l.retry().totalAttempts().value()).isEqualTo(3);
        assertThat(l.retry().totalAttempts().source()).isEqualTo(Source.EXPLICIT);
        assertThat(l.retry().totalAttempts().evidence()).isEqualTo("dumps/envoy/http_config.json:28 (/configs/0/"
                + "dynamic_route_configs/0/route_config/virtual_hosts/0/routes/0/route/retry_policy/num_retries)"
                + " (num_retries 2 + 1)");
        // "0s": no route timeout, so no per-try timeout either
        assertThat(l.overallTimeout().value()).isEqualTo(Durations.UNLIMITED);
        assertThat(l.overallTimeout().source()).isEqualTo(Source.EXPLICIT);
        assertThat(l.retry().perTryTimeout().value()).isEqualTo(Durations.UNLIMITED);
        assertThat(l.retry().perTryTimeout().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(l.retry().initialBackoff().value()).isEqualTo(Duration.ofMillis(25));
        assertThat(l.retry().initialBackoff().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(l.retry().jitter().value()).isTrue();
        // retriable-status-codes is replaced by the codes themselves, as in a VirtualService's retryOn
        assertThat(l.retry().retryOn().value())
                .containsExactlyInAnyOrder("connect-failure", "refused-stream", "unavailable", "cancelled", "503");
        assertThat(l.conditions().retried().value())
                .containsExactlyInAnyOrder(FailureClass.NOT_SENT, FailureClass.STATUS_503);
        assertThat(l.conditions().givesUpAs().isKnown()).isTrue();
        assertThat(d.notes()).isEmpty();
    }

    @Test
    void absentFieldsTakeEnvoysDefaultsAndCamelCaseIsRead() {
        Dump d = parse(routes("""
                { "routeConfig": { "name": "9080", "virtualHosts": [ { "name": "reviews:9080", "routes": [
                  { "route": { "cluster": "outbound|9080||reviews.default.svc.cluster.local",
                    "retryPolicy": { "retryOn": "5xx", "perTryTimeout": "0.250s",
                                     "retryBackOff": { "baseInterval": "0.100s" } } } } ] } ] } }
                """));
        Layer l = layerTo(d, "reviews");
        assertThat(l.retry().totalAttempts().value()).isEqualTo(2);
        assertThat(l.retry().totalAttempts().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(l.retry().totalAttempts().evidence()).contains("(num_retries 1 + 1)");
        assertThat(l.overallTimeout().value()).isEqualTo(Duration.ofSeconds(15));
        assertThat(l.overallTimeout().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(l.retry().perTryTimeout().value()).isEqualTo(Duration.ofMillis(250));
        assertThat(l.retry().perTryTimeout().evidence()).endsWith("/routeConfig/virtualHosts/0/routes/0/route/"
                + "retryPolicy/perTryTimeout)");
        assertThat(l.retry().initialBackoff().value()).isEqualTo(Duration.ofMillis(100));
        assertThat(l.conditions().retried().value()).contains(FailureClass.STATUS_500, FailureClass.RESET);
    }

    @Test
    void noRetryPolicyIsOneTryAndTheVirtualHostsPolicyAppliesToRoutesWithoutOne() {
        Dump d = parse(routes("""
                { "route_config": { "name": "80", "virtual_hosts": [
                  { "name": "a", "routes": [ { "route": { "cluster": "outbound|80||a.shop.svc.cluster.local",
                                                          "timeout": "2s" } } ] },
                  { "name": "b", "retry_policy": { "num_retries": 4, "retry_on": "reset" },
                    "routes": [ { "route": { "cluster": "outbound|80||b.shop.svc.cluster.local" } } ] } ] } }
                """));
        Layer a = layerTo(d, "a");
        assertThat(a.retry().totalAttempts().value()).isEqualTo(1);
        assertThat(a.retry().totalAttempts().evidence()).contains("no retry_policy on the route or its virtual host");
        assertThat(a.retry().perTryTimeout().value()).isEqualTo(Duration.ofSeconds(2));
        assertThat(a.conditions().retried().value()).isEmpty();
        Layer b = layerTo(d, "b");
        assertThat(b.retry().totalAttempts().value()).isEqualTo(5);
        assertThat(b.retry().totalAttempts().evidence()).contains("/virtual_hosts/1/retry_policy/num_retries");
    }

    @Test
    void weightedClustersPassthroughAndTheResourceFormAreHandled() {
        Dump d = parse("""
                { "configs": [ { "@type": "type.googleapis.com/envoy.admin.v3.RoutesConfigDump.DynamicRouteConfig",
                  "route_config": { "name": "80", "virtual_hosts": [ { "name": "x", "routes": [
                    { "name": "split", "route": { "weighted_clusters": { "clusters": [
                        { "name": "outbound|80|v1|ratings.shop.svc.cluster.local", "weight": 90 },
                        { "name": "outbound|80|v2|ratings.shop.svc.cluster.local", "weight": 10 } ] } } },
                    { "route": { "cluster": "PassthroughCluster" } },
                    { "redirect": { "path_redirect": "/" } } ] } ] } } ] }
                """);
        assertThat(d.routes()).extracting(Route::to, Route::route)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("ratings", "split"),
                        org.assertj.core.groups.Tuple.tuple("ratings", "split"));
        assertThat(d.notes()).containsExactly("dump.json: routes to PassthroughCluster are not outbound services; "
                + "not read");
    }

    @Test
    void otherDocumentsAndInvalidValuesAreRejected() {
        assertThatThrownBy(() -> parse("{ \"configs\": [ { \"@type\": \"x.ClustersConfigDump\" } ] }"))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessage("dump.json: not an Envoy config_dump with route configurations");
        assertThatThrownBy(() -> parse(routes("""
                { "route_config": { "virtual_hosts": [ { "routes": [
                  { "route": { "cluster": "outbound|80||a.b.svc.cluster.local", "timeout": "2m" } } ] } ] } }
                """))).isInstanceOf(InvalidSpecException.class).hasMessageContaining("invalid duration '2m'");
        assertThat(parse(routes("")).notes()).containsExactly("dump.json: no route to an outbound service");
    }
}
