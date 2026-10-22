package io.github.hasanyelaldi.plimsoll.collect.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hasanyelaldi.plimsoll.analysis.AnalysisOptions;
import io.github.hasanyelaldi.plimsoll.analysis.AnalysisResult;
import io.github.hasanyelaldi.plimsoll.analysis.Analyzer;
import io.github.hasanyelaldi.plimsoll.analysis.Finding;
import io.github.hasanyelaldi.plimsoll.analysis.Severity;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.EdgesHint;
import io.github.hasanyelaldi.plimsoll.collect.spring.RequestMappingScanner;
import io.github.hasanyelaldi.plimsoll.collect.spring.RequestMappingScanner.Mapping;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.Service;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GatewayCollectorTest {

    private static final Path PETCLINIC = Path.of("src/test/resources/projects/petclinic-gateway");
    private static final String OWNERS =
            "src/main/java/org/springframework/samples/petclinic/customers/web/OwnerResource.java";

    /** The request mappings of the petclinic fixture services, by service name. */
    private static Map<String, List<Mapping>> petclinicServed() {
        Map<String, List<Mapping>> served = new LinkedHashMap<>();
        for (String s : List.of("customers-service", "vets-service", "visits-service")) {
            served.put(s, RequestMappingScanner.scan(Path.of("src/test/resources/projects/petclinic-" + s)).mappings());
        }
        return served;
    }

    private static Edge petclinicEdge(String to) {
        return GatewayCollector.collect(PETCLINIC, petclinicServed(), DefaultsKb.classpath()).edges().stream()
                .filter(e -> e.to().equals(to)).findFirst().orElseThrow();
    }

    private static final String RETRY_GET_POST =
            "- StripPrefix=1\n- { name: Retry, args: { retries: 1, methods: [ GET, POST, PUT ] } }";

    private static GatewayCollector.Result serving(String yaml, String... operations) {
        return servingWith(null, yaml, operations);
    }

    private static GatewayCollector.Result servingWith(EdgesHint hint, String yaml, String... operations) {
        List<Mapping> mappings = new java.util.ArrayList<>();
        for (int i = 0; i < operations.length; i++) {
            String[] op = operations[i].split(" ", 2);
            Val<String> method = op[0].equals("*") ? Val.unknown("any method") : Val.explicit(op[0], "A.java:" + i);
            mappings.add(new Mapping(method, op[1], "A.java:" + i));
        }
        return GatewayCollector.collect(SpringProperties.parse(yaml, "application.yml"), "gw",
                Map.of("a-service", mappings), hint, DefaultsKb.classpath());
    }

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
                        && n.contains("UNKNOWN"));
    }

    // ---- operations and idempotency --------------------------------------

    @Test
    void petclinicCreateOwnerPostIsRetriedThroughTheGateway() {
        Edge e = petclinicEdge("customers-service");
        assertThat(e.operation()).isEqualTo("/api/customer/**");
        assertThat(e.idempotent().value()).isFalse();
        assertThat(e.idempotent().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(e.idempotent().evidence()).startsWith("POST (customers-service /owners, " + OWNERS + ":58); "
                + "http RFC 9110 default (verified by doc)");
        assertThat(e.layers().get(0).retry().totalAttempts().value()).isEqualTo(2);
    }

    @Test
    void petclinicGetOnlyRouteBehindMethodsPostHasNoGatewayRetry() {
        Edge e = petclinicEdge("vets-service");
        RetryPolicy retry = e.layers().get(0).retry();
        assertThat(retry.totalAttempts().value()).isEqualTo(1);
        assertThat(retry.totalAttempts().source()).isEqualTo(Source.EXPLICIT);
        assertThat(retry.totalAttempts().evidence()).contains("Retry (default-filters[1]", "repeats only POST",
                "(methods)", "route vets-service serves vets-service GET /vets at src/main/java/org/springframework/"
                        + "samples/petclinic/vets/web/VetResource.java:45");
        assertThat(e.idempotent().value()).isTrue();
    }

    @Test
    void petclinicCreateVisitPostIsRetriedAndCalleesWithoutCodeStayUnknown() {
        assertThat(petclinicEdge("visits-service").idempotent().value()).isFalse();
        Edge genai = petclinicEdge("genai-service");
        assertThat(genai.idempotent().source()).isEqualTo(Source.UNKNOWN);
        assertThat(genai.idempotent().evidence()).contains("request mappings of genai-service are not known");
        assertThat(genai.layers().get(0).retry().totalAttempts().value()).isEqualTo(2);
    }

    @Test
    void petclinicA3FiresOnTheCreatePostRoutes() {
        GatewayCollector.Result r = GatewayCollector.collect(PETCLINIC, petclinicServed(), DefaultsKb.classpath());
        Map<String, Service> services = new LinkedHashMap<>();
        services.put("api-gateway", new Service("api-gateway", Val.unknown("deadline not collected"), true));
        for (Edge e : r.edges()) {
            services.put(e.to(), new Service(e.to(), Val.unknown("deadline not collected"), false));
        }
        AnalysisResult a = Analyzer.analyze(new ServiceGraph(services, r.edges()), AnalysisOptions.defaults());
        assertThat(a.findings()).filteredOn(f -> f.ruleId().equals("PLM-A3"))
                .extracting(Finding::severity, Finding::location, Finding::detail).containsExactly(
                        org.assertj.core.groups.Tuple.tuple(Severity.ERROR, "api-gateway→customers-service",
                                "non-idempotent call retried, 2 tries"),
                        org.assertj.core.groups.Tuple.tuple(Severity.ERROR, "api-gateway→visits-service",
                                "non-idempotent call retried, 2 tries"));
    }

    @Test
    void petclinicHintOverrideMakesTheCreateOwnerPostIdempotent() {
        EdgesHint hint = EdgesHint.parse("""
                service: api-gateway
                operations:
                  - { to: customers-service, operation: POST /owners, idempotent: true }
                """, "edges-hint.yaml");
        GatewayCollector.Result r = GatewayCollector.collect(PETCLINIC, petclinicServed(), hint,
                DefaultsKb.classpath());
        Edge e = r.edges().stream().filter(x -> x.to().equals("customers-service")).findFirst().orElseThrow();
        // the pets POSTs of customers-service still repeat a non-idempotent operation
        assertThat(e.idempotent().value()).isFalse();
        assertThat(e.idempotent().evidence()).doesNotContain(OWNERS + ":58");
    }

    @Test
    void hintOverrideIsExplicitAndWinsOverTheMethod() {
        EdgesHint hint = EdgesHint.parse("""
                service: gw
                operations:
                  - { to: a-service, operation: POST /a/y, idempotent: true }
                """, "edges-hint.yaml");
        GatewayCollector.Result r = servingWith(hint, route(RETRY_GET_POST), "GET /a/x", "POST /a/y");
        Edge e = r.edges().get(0);
        assertThat(e.idempotent().value()).isTrue();
        assertThat(e.idempotent().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(e.idempotent().evidence())
                .contains("a-service POST /a/y (A.java:1) idempotent: true, edges-hint.yaml:3");
        assertThat(r.notes()).noneMatch(n -> n.contains("not reached"));

        Edge onlyPost = servingWith(hint, route(RETRY_GET_POST), "POST /a/y").edges().get(0);
        assertThat(onlyPost.idempotent().value()).isTrue();
        assertThat(onlyPost.idempotent().source()).isEqualTo(Source.EXPLICIT);
    }

    @Test
    void hintOverrideCanMarkAnOperationNonIdempotent() {
        EdgesHint hint = EdgesHint.parse("""
                service: gw
                operations:
                  - { to: a-service, operation: PUT /a/x, idempotent: false }
                """, "edges-hint.yaml");
        Edge e = servingWith(hint, route(RETRY_GET_POST), "PUT /a/x").edges().get(0);
        assertThat(e.idempotent()).isEqualTo(Val.explicit(false,
                "a-service PUT /a/x (A.java:0) idempotent: false, edges-hint.yaml:3"));
    }

    @Test
    void unreachedHintOperationsAreNotedAndWrongServiceIsRejected() {
        EdgesHint hint = EdgesHint.parse("""
                service: gw
                operations:
                  - { to: a-service, operation: POST /b, idempotent: true }
                """, "edges-hint.yaml");
        assertThat(servingWith(hint, route("- StripPrefix=1"), "POST /a/y").notes())
                .contains("edges-hint.yaml:3: operation a-service POST /b is not reached by any route; not applied");

        EdgesHint other = EdgesHint.parse("service: orders\noperations: []\n", "edges-hint.yaml");
        assertThatThrownBy(() -> servingWith(other, route("- Retry=1"), "GET /a/x"))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessage("edges hint is for service orders, the gateway is gw");
    }

    @Test
    void retryOnGetOnlyJudgesTheRepeatedOperations() {
        GatewayCollector.Result r = serving(route("- StripPrefix=1\n- Retry=1"), "GET /a/x", "POST /a/y");
        Edge e = r.edges().get(0);
        assertThat(e.layers().get(0).retry().totalAttempts().value()).isEqualTo(2);
        assertThat(e.idempotent().value()).isTrue();
        assertThat(e.idempotent().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(e.idempotent().evidence()).startsWith("GET (a-service /a/x, A.java:0)");
    }

    @Test
    void withoutRetryEveryOperationIsJudged() {
        Edge e = serving(route("- StripPrefix=1"), "GET /a/x", "POST /a/y").edges().get(0);
        assertThat(e.idempotent().value()).isFalse();
        assertThat(e.idempotent().evidence()).startsWith("POST (a-service /a/y, A.java:1)");
    }

    @Test
    void stripPrefixAndPathDecideWhatIsReached() {
        // /api/a/** with StripPrefix=1 reaches /a/..., not /b
        Edge e = serving(route("- StripPrefix=1\n- Retry=1,SERVICE_UNAVAILABLE,POST"), "POST /b", "GET /a/{id}")
                .edges().get(0);
        assertThat(e.layers().get(0).retry().totalAttempts().value()).isEqualTo(1);
        assertThat(e.idempotent().value()).isTrue();

        assertThat(GatewayCollector.reaches("/api/a/**", 2, "/anything/{x}")).isTrue();
        assertThat(GatewayCollector.reaches("/api/a/**", 3, "/anything")).isTrue();
        assertThat(GatewayCollector.reaches("/api/a", 1, "/a")).isTrue();
        assertThat(GatewayCollector.reaches("/api/a", 1, "/a/b")).isFalse();
        assertThat(GatewayCollector.reaches("/api/a", 3, "/a")).isFalse();
        assertThat(GatewayCollector.reaches("/api/a/**", 0, "/api/*/pets")).isTrue();
        assertThat(GatewayCollector.reaches("/api/a/**", 0, "/api")).isFalse();
    }

    @Test
    void methodOfAnyMappingIsRepeatedAndUnknown() {
        Edge e = serving(route("- StripPrefix=1\n- Retry=1,SERVICE_UNAVAILABLE,POST"), "* /a/x").edges().get(0);
        assertThat(e.layers().get(0).retry().totalAttempts().value()).isEqualTo(2);
        assertThat(e.idempotent().source()).isEqualTo(Source.UNKNOWN);
        assertThat(e.idempotent().evidence()).contains("any method");
    }

    @Test
    void methodPredicateNarrowsTheOperations() {
        GatewayCollector.Result r = serving("""
                spring.cloud.gateway.server.webflux.routes:
                  - id: a
                    uri: lb://a-service
                    predicates: [ "Path=/a/**", "Method=GET" ]
                    filters: [ { name: Retry, args: { retries: 1, methods: [ GET, POST ] } } ]
                """, "GET /a/x", "POST /a/y");
        assertThat(r.edges().get(0).idempotent().value()).isTrue();
    }

    @Test
    void unmodelledPathsLeaveIdempotencyUnknown() {
        assertThat(serving(route("- RewritePath=/api/(?<p>.*), /${p}"), "GET /a").edges().get(0).idempotent()
                .evidence()).contains("RewritePath", "rewrites the path");
        assertThat(serving(route("- name: StripPrefix"), "GET /a").edges().get(0).idempotent().evidence())
                .contains("without a literal parts count");
        assertThat(serving(route("- StripPrefix=1"), "GET /zzz").edges().get(0).idempotent().evidence())
                .contains("reaches none of the 1 request mappings of a-service");
        assertThat(serving(route("- StripPrefix=1").replace("/api/a/**", "/api/{v}/**"), "GET /a").edges().get(0)
                .idempotent().evidence()).contains("Path /api/{v}/** is not modelled");
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
