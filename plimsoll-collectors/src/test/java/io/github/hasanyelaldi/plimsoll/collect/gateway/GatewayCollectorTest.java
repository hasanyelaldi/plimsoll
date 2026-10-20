package io.github.hasanyelaldi.plimsoll.collect.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.Source;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GatewayCollectorTest {

    private static final Path PETCLINIC = Path.of("src/test/resources/projects/petclinic-gateway");

    private static GatewayCollector.Result collect(String applicationYml) {
        return collect(applicationYml, DefaultsKb.classpath());
    }

    private static GatewayCollector.Result collect(String applicationYml, DefaultsKb kb) {
        return GatewayCollector.collect(SpringProperties.parse(applicationYml, "application.yml"), "gw", kb);
    }

    private static String route(String filters) {
        return """
                spring.cloud.gateway.server.webflux.routes:
                  - id: a
                    uri: lb://a-service
                    predicates: [ "Path=/api/a/**" ]
                    filters:
                """ + filters.indent(6);
    }

    private static Layer only(GatewayCollector.Result r) {
        assertThat(r.edges()).hasSize(1);
        assertThat(r.edges().get(0).layers()).hasSize(1);
        return r.edges().get(0).layers().get(0);
    }

    // ---- petclinic -------------------------------------------------------

    @Test
    void petclinicGatewayRoutesBecomeEdges() {
        GatewayCollector.Result r = GatewayCollector.collect(PETCLINIC);
        assertThat(r.service()).isEqualTo("api-gateway");
        assertThat(r.edges()).extracting(Edge::from).containsOnly("api-gateway");
        assertThat(r.edges()).extracting(Edge::to)
                .containsExactly("vets-service", "visits-service", "customers-service", "genai-service");
        assertThat(r.edges()).extracting(Edge::operation)
                .containsExactly("/api/vet/**", "/api/visit/**", "/api/customer/**", "/api/genai/**");
        assertThat(r.edges()).allSatisfy(e -> assertThat(e.idempotent().source()).isEqualTo(Source.UNKNOWN));
    }

    @Test
    void petclinicDefaultRetryIsOneRetryOnPostAnd503() {
        GatewayCollector.Result r = GatewayCollector.collect(PETCLINIC);
        for (Edge e : r.edges()) {
            assertThat(e.layers()).hasSize(1);
            Layer layer = e.layers().get(0);
            assertThat(layer.kind()).isEqualTo("spring-cloud-gateway");
            RetryPolicy retry = layer.retry();
            assertThat(retry.totalAttempts().value()).isEqualTo(2);
            assertThat(retry.totalAttempts().source()).isEqualTo(Source.EXPLICIT);
            assertThat(retry.totalAttempts().evidence())
                    .contains("retries 1 + 1", "src/main/resources/application.yml:18", "default-filters[1]");
            assertThat(retry.retryOn().value()).containsExactlyInAnyOrder("status SERVICE_UNAVAILABLE",
                    "series SERVER_ERROR", "java.io.IOException", "java.util.concurrent.TimeoutException",
                    "method POST");
            assertThat(retry.retryOn().source()).isEqualTo(Source.LIBRARY_DEFAULT);
            assertThat(retry.initialBackoff().value()).isEqualTo(Duration.ZERO);
            assertThat(retry.initialBackoff().source()).isEqualTo(Source.LIBRARY_DEFAULT);
            assertThat(retry.jitter().value()).isFalse();
            assertThat(retry.perTryTimeout().source()).isEqualTo(Source.UNKNOWN);
            assertThat(layer.overallTimeout().source()).isEqualTo(Source.UNKNOWN);
            assertThat(layer.overallTimeout().evidence()).contains("unlimited");
        }
    }

    @Test
    void petclinicBreakersAreNotedWithTheirOrderUnknown() {
        GatewayCollector.Result r = GatewayCollector.collect(PETCLINIC);
        assertThat(r.notes())
                .anyMatch(n -> n.contains("CircuitBreaker 'defaultCircuitBreaker' (default-filters[0])")
                        && n.contains("route vets-service"))
                .anyMatch(n -> n.contains("CircuitBreaker 'genaiCircuitBreaker' (route genai-service filters[1])")
                        && n.contains("application.yml:46"))
                .anyMatch(n -> n.contains("route genai-service: CircuitBreaker and Retry both apply")
                        && n.contains("UNKNOWN"))
                .anyMatch(n -> n.contains("document skipped"));
    }

    // ---- normalization and arguments -------------------------------------

    @Test
    void retriesCountRetriesOnly() {
        Layer layer = only(collect(route("- { name: Retry, args: { retries: 1 } }")));
        assertThat(layer.retry().totalAttempts().value()).isEqualTo(2);
    }

    @Test
    void absentRetriesUseTheVerifiedDefaultOfThree() {
        RetryPolicy retry = only(collect(route("- name: Retry"))).retry();
        assertThat(retry.totalAttempts().value()).isEqualTo(4);
        assertThat(retry.totalAttempts().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(retry.totalAttempts().evidence())
                .contains("retries not set", "spring-cloud-gateway 5.0.0, 5.0.3 default (verified by doc)");
        assertThat(retry.retryOn().value()).containsExactlyInAnyOrder("series SERVER_ERROR", "java.io.IOException",
                "java.util.concurrent.TimeoutException", "method GET");
    }

    @Test
    void shortcutFormIsPositional() {
        Layer layer = only(collect(route("- Retry=3,INTERNAL_SERVER_ERROR,GET,10ms,50ms,2,false,0.5,100ms")));
        RetryPolicy retry = layer.retry();
        assertThat(retry.totalAttempts().value()).isEqualTo(4);
        assertThat(retry.totalAttempts().evidence()).contains("application.yml:6 (retries)");
        assertThat(retry.initialBackoff().value()).isEqualTo(Duration.ofMillis(10));
        assertThat(retry.maxBackoff().value()).isEqualTo(Duration.ofMillis(50));
        assertThat(retry.multiplier().value()).isEqualTo(2.0);
        assertThat(retry.jitter().value()).isTrue();
        assertThat(retry.retryOn().value()).contains("status INTERNAL_SERVER_ERROR", "method GET");
        assertThat(layer.overallTimeout().value()).isEqualTo(Duration.ofMillis(100));
        assertThat(layer.overallTimeout().source()).isEqualTo(Source.EXPLICIT);
    }

    @Test
    void expandedAndShortcutFormsAgree() {
        Layer expanded = only(collect(route("""
                - name: Retry
                  args:
                    retries: 3
                    statuses: INTERNAL_SERVER_ERROR
                    methods: GET
                    backoff: { firstBackoff: 10ms, maxBackoff: 50ms, factor: 2, basedOnPreviousValue: false }
                    jitter: { randomFactor: 0.5 }
                    timeout: 100ms
                """)));
        Layer shortcut = only(collect(route("- Retry=3,INTERNAL_SERVER_ERROR,GET,10ms,50ms,2,false,0.5,100ms")));
        RetryPolicy a = expanded.retry();
        RetryPolicy b = shortcut.retry();
        assertThat(a.totalAttempts().value()).isEqualTo(b.totalAttempts().value());
        assertThat(a.initialBackoff().value()).isEqualTo(b.initialBackoff().value());
        assertThat(a.maxBackoff().value()).isEqualTo(b.maxBackoff().value());
        assertThat(a.multiplier().value()).isEqualTo(b.multiplier().value());
        assertThat(a.jitter().value()).isEqualTo(b.jitter().value());
        assertThat(a.retryOn().value()).isEqualTo(b.retryOn().value());
        assertThat(expanded.overallTimeout().value()).isEqualTo(shortcut.overallTimeout().value());
    }

    @Test
    void backoffWithoutValuesUsesSourceDefaultsAndHasNoCap() {
        RetryPolicy retry = only(collect(route("- { name: Retry, args: { backoff: { maxBackoff: 1s } } }"))).retry();
        assertThat(retry.initialBackoff().value()).isEqualTo(Duration.ofMillis(5));
        assertThat(retry.initialBackoff().evidence()).contains("verified by source");
        assertThat(retry.multiplier().value()).isEqualTo(2.0);

        RetryPolicy uncapped = only(collect(route("- { name: Retry, args: { backoff: { firstBackoff: 1s } } }")))
                .retry();
        assertThat(uncapped.maxBackoff().source()).isEqualTo(Source.UNKNOWN);
        assertThat(uncapped.maxBackoff().evidence()).contains("no cap");
    }

    @Test
    void listsAndSeriesAreRead() {
        RetryPolicy retry = only(collect(route("""
                - name: Retry
                  args: { series: [], statuses: [ BAD_GATEWAY, GATEWAY_TIMEOUT ], methods: [ GET, POST ],
                          exceptions: java.io.IOException }
                """))).retry();
        assertThat(retry.retryOn().value()).containsExactlyInAnyOrder("status BAD_GATEWAY", "status GATEWAY_TIMEOUT",
                "method GET", "method POST", "java.io.IOException");
        assertThat(retry.retryOn().source()).isEqualTo(Source.EXPLICIT);
    }

    @Test
    void responseTimeoutBoundsEachTry() {
        GatewayCollector.Result r = collect("spring.cloud.gateway.server.webflux.httpclient.response-timeout: 2s\n"
                + route("- name: Retry"));
        Layer layer = only(r);
        assertThat(layer.retry().perTryTimeout().value()).isEqualTo(Duration.ofSeconds(2));
        assertThat(layer.retry().perTryTimeout().evidence()).contains("application.yml:1");
    }

    @Test
    void placeholdersAreUnknown() {
        RetryPolicy retry = only(collect(route("- { name: Retry, args: { retries: \"${RETRIES:2}\" } }"))).retry();
        assertThat(retry.totalAttempts().source()).isEqualTo(Source.UNKNOWN);
        assertThat(retry.totalAttempts().evidence()).contains("depends on the environment");
    }

    // ---- routes ----------------------------------------------------------

    @Test
    void routeWithoutRetryHasOneTry() {
        Layer layer = only(collect(route("- StripPrefix=2")));
        assertThat(layer.retry().totalAttempts().value()).isEqualTo(1);
        assertThat(layer.retry().totalAttempts().evidence()).contains("no Retry filter on route a");
    }

    @Test
    void defaultAndRouteRetriesAreTwoLayersDefaultFirst() {
        GatewayCollector.Result r = collect("""
                spring.cloud.gateway.server.webflux:
                  default-filters: [ "Retry=1" ]
                  routes:
                    - { id: a, uri: "lb://a-service", filters: [ "Retry=2" ] }
                """);
        assertThat(r.edges().get(0).layers()).extracting(l -> l.retry().totalAttempts().value())
                .containsExactly(2, 3);
        assertThat(r.edges().get(0).operation()).isEqualTo("route a");
        assertThat(r.notes()).anyMatch(n -> n.contains("2 Retry filters"));
    }

    @Test
    void nonLoadBalancedRoutesAreNotEdges() {
        GatewayCollector.Result r = collect("""
                spring.cloud.gateway.server.webflux.routes:
                  - { id: ext, uri: "https://example.org" }
                  - { id: b, uri: "lb://b-service", predicates: [ { name: Path, args: { patterns: [ /b/**, /c/** ] } } ] }
                """);
        assertThat(r.edges()).extracting(Edge::to).containsExactly("b-service");
        assertThat(r.edges().get(0).operation()).isEqualTo("/b/**,/c/**");
        assertThat(r.notes()).anyMatch(n -> n.contains("route ext goes to https://example.org"));
    }

    @Test
    void legacyPrefixIsReadWithANote() {
        GatewayCollector.Result r = collect("""
                spring.application.name: edge
                spring.cloud.gateway.routes:
                  - { id: a, uri: "lb://a-service", filters: [ "Retry=1" ] }
                """);
        assertThat(r.service()).isEqualTo("edge");
        assertThat(only(r).retry().totalAttempts().value()).isEqualTo(2);
        assertThat(r.notes()).anyMatch(n -> n.contains("legacy prefix"));
    }

    @Test
    void missingApplicationNameFallsBackWithANote() {
        GatewayCollector.Result r = collect(route("- Retry=1"));
        assertThat(r.service()).isEqualTo("gw");
        assertThat(r.notes()).anyMatch(n -> n.contains("spring.application.name not set"));
    }

    @Test
    void withoutAVerifiedKbEntryDefaultsAreUnknown() {
        RetryPolicy retry = only(collect(route("- name: Retry"), DefaultsKb.of(Map.of()))).retry();
        assertThat(retry.totalAttempts().source()).isEqualTo(Source.UNKNOWN);
        assertThat(retry.initialBackoff().source()).isEqualTo(Source.UNKNOWN);
        assertThat(retry.jitter().source()).isEqualTo(Source.UNKNOWN);
        assertThat(retry.retryOn().source()).isEqualTo(Source.UNKNOWN);
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThatThrownBy(() -> collect(route("- Retry=0")))
                .isInstanceOf(InvalidSpecException.class).hasMessageContaining("greater than 0");
        assertThatThrownBy(() -> collect(route("- { name: Retry, args: { timeout: soon } }")))
                .isInstanceOf(InvalidSpecException.class).hasMessageContaining("a duration");
        assertThatThrownBy(() -> collect(route("- Retry=1,A,GET,1ms,2ms,2,false,0.5,1s,extra")))
                .isInstanceOf(InvalidSpecException.class).hasMessageContaining("at most 9");
        assertThatThrownBy(() -> collect(route("- { args: { retries: 1 } }")))
                .isInstanceOf(InvalidSpecException.class).hasMessageContaining("has no name");
    }

    @Test
    void sameInputGivesTheSameResult() {
        assertThat(GatewayCollector.collect(PETCLINIC)).isEqualTo(GatewayCollector.collect(PETCLINIC));
    }
}
