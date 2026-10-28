package io.github.hasanyelaldi.plimsoll.collect.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hasanyelaldi.plimsoll.collect.http.HttpClientCollector.EdgeKey;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.EdgesHint;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import io.github.hasanyelaldi.plimsoll.collect.http.ClientCallScanner.ClientCall;
import io.github.hasanyelaldi.plimsoll.model.Durations;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HttpClientCollectorTest {

    private static final String HINT = """
            service: api-gateway
            methods:
              - { method: p.Customers#getOwner, to: customers-service, operation: "GET /owners/{id}", client: webclient-loadbalanced }
              - { method: p.Ai#addOwner, to: customers-service, operation: POST /owners, client: restclient }
              - { method: p.Other#call, to: vets-service }
            """;

    private static HttpClientCollector.Result collect(String applicationYml) {
        return collect(applicationYml, DefaultsKb.classpath());
    }

    private static HttpClientCollector.Result collect(String applicationYml, DefaultsKb kb) {
        return HttpClientCollector.collect(SpringProperties.parse(applicationYml, "application.yml"),
                EdgesHint.parse(HINT, "edges-hint.yaml"), kb);
    }

    private static Layer webClient(HttpClientCollector.Result r) {
        return r.layers().get(new EdgeKey("api-gateway", "customers-service", "GET /owners/{id}"));
    }

    @Test
    void loadBalancerRetryIsOffByDefault() {
        Layer layer = webClient(collect("server.port: 8080"));
        assertThat(layer.kind()).isEqualTo("http-client");
        Val<Integer> attempts = layer.retry().totalAttempts();
        assertThat(attempts.value()).isEqualTo(1);
        assertThat(attempts.source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(attempts.evidence())
                .contains("LoadBalancer retry off", "spring.cloud.loadbalancer.retry.enabled not set",
                        "spring-cloud-loadbalancer 5.0.0, 5.0.3 default (verified by oracle)");
    }

    @Test
    void webClientHasNoResponseTimeout() {
        Layer layer = webClient(collect("server.port: 8080"));
        assertThat(layer.retry().perTryTimeout().value()).isEqualTo(Durations.UNLIMITED);
        assertThat(layer.retry().perTryTimeout().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(layer.retry().perTryTimeout().evidence())
                .contains("response timeout of WebClient.builder()", "spring-web 7.0.2, 7.0.9 default (verified by oracle)");
        assertThat(layer.overallTimeout().value()).isEqualTo(Durations.UNLIMITED);
    }

    @Test
    void enabledRetryUsesTheLibraryDefaultsSameZeroNextOne() {
        RetryPolicy retry = webClient(collect("spring.cloud.loadbalancer.retry.enabled: true")).retry();
        assertThat(retry.totalAttempts().value()).isEqualTo(2);
        assertThat(retry.totalAttempts().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(retry.totalAttempts().evidence()).contains("(same instance 0 + 1) x (next instance 1 + 1)",
                "application.yml:1 (spring.cloud.loadbalancer.retry.enabled)");
        assertThat(retry.initialBackoff().value()).isEqualTo(Duration.ZERO);
        assertThat(retry.maxBackoff().value()).isEqualTo(Duration.ZERO);
        assertThat(retry.multiplier().value()).isEqualTo(1.0);
        assertThat(retry.jitter().value()).isFalse();
        assertThat(retry.retryOn().value()).contains("java.io.IOException", "java.util.concurrent.TimeoutException")
                .noneMatch(c -> c.startsWith("status"));
    }

    @Test
    void explicitRetriesMultiply() {
        Val<Integer> attempts = webClient(collect("""
                spring:
                  cloud:
                    loadbalancer:
                      retry:
                        enabled: true
                        max-retries-on-same-service-instance: 1
                        maxRetriesOnNextServiceInstance: 2
                """)).retry().totalAttempts();
        assertThat(attempts.value()).isEqualTo(6);
        assertThat(attempts.source()).isEqualTo(Source.EXPLICIT);
        assertThat(attempts.evidence()).contains("application.yml:6", "application.yml:7");
    }

    @Test
    void retryableStatusCodesAreForGetOnlyUnlessRetryOnAllOperations() {
        String base = """
                spring.cloud.loadbalancer.retry:
                  enabled: true
                  retryable-status-codes: 503, 502
                """;
        assertThat(webClient(collect(base)).retry().retryOn().value())
                .contains("status 502 (GET only)", "status 503 (GET only)", "java.io.IOException");
        assertThat(webClient(collect(base + "  retry-on-all-operations: true\n")).retry().retryOn().value())
                .contains("status 502", "status 503");
    }

    @Test
    void placeholderSwitchIsUnknown() {
        Val<Integer> attempts = webClient(collect("spring.cloud.loadbalancer.retry.enabled: ${LB_RETRY:false}"))
                .retry().totalAttempts();
        assertThat(attempts.source()).isEqualTo(Source.UNKNOWN);
        assertThat(attempts.evidence()).contains("placeholder ${LB_RETRY:false}");
    }

    @Test
    void perClientPropertiesAreNotModelledAndNoted() {
        HttpClientCollector.Result r = collect("spring.cloud.loadbalancer.clients.customers-service.retry.enabled: true");
        assertThat(webClient(r).retry().totalAttempts().source()).isEqualTo(Source.UNKNOWN);
        assertThat(r.notes()).singleElement().asString()
                .startsWith("customers-service: spring.cloud.loadbalancer.clients.customers-service.* found");
    }

    @Test
    void backoffIsNotModelledAndNoted() {
        HttpClientCollector.Result r = collect("""
                spring.cloud.loadbalancer.retry:
                  enabled: true
                  backoff.enabled: true
                """);
        RetryPolicy retry = webClient(r).retry();
        assertThat(retry.totalAttempts().value()).isEqualTo(2);
        assertThat(retry.initialBackoff().source()).isEqualTo(Source.UNKNOWN);
        assertThat(retry.jitter().source()).isEqualTo(Source.UNKNOWN);
        assertThat(r.notes()).singleElement().asString().contains("LoadBalancer backoff is not modelled yet");
    }

    @Test
    void customRetryableExceptionsAreNotModelledAndNoted() {
        HttpClientCollector.Result r = collect("""
                spring.cloud.loadbalancer.retry:
                  enabled: true
                  retry-on-all-exceptions: true
                """);
        assertThat(webClient(r).retry().retryOn().source()).isEqualTo(Source.UNKNOWN);
        assertThat(r.notes()).singleElement().asString().contains("retry-on-all-exceptions is not modelled yet");
    }

    @Test
    void withoutAVerifiedDefaultTheSwitchIsUnknown() {
        Val<Integer> attempts = webClient(collect("server.port: 8080", DefaultsKb.of(Map.of()))).retry().totalAttempts();
        assertThat(attempts.source()).isEqualTo(Source.UNKNOWN);
        assertThat(attempts.evidence()).contains("no verified spring-cloud-loadbalancer default");
    }

    @Test
    void restClientDoesNotRetryAndItsTimeoutDependsOnTheClasspath() {
        Layer layer = collect("server.port: 8080").layers()
                .get(new EdgeKey("api-gateway", "customers-service", "POST /owners"));
        assertThat(layer.retry().totalAttempts()).isEqualTo(
                Val.explicit(1, "RestClient (edges-hint.yaml:4): RestClient does not retry"));
        assertThat(layer.retry().perTryTimeout().source()).isEqualTo(Source.UNKNOWN);
        assertThat(layer.retry().perTryTimeout().evidence())
                .contains("httpcomponents,jetty,reactor-netty,jdk,simple", "with reactor-netty 10s");
    }

    @Test
    void entriesWithoutAClientGetNoLayer() {
        assertThat(collect("server.port: 8080").layers()).hasSize(2)
                .doesNotContainKey(new EdgeKey("api-gateway", "vets-service", "p.Other#call"));
    }

    @Test
    void invalidRetryCountIsRejected() {
        assertThatThrownBy(() -> collect("""
                spring.cloud.loadbalancer.retry:
                  enabled: true
                  max-retries-on-next-service-instance: -1
                """))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessageContaining("application.yml:3")
                .hasMessageContaining("must be an integer of at least 0, got -1");
    }

    // ---- edges found in code (#37) ---------------------------------------

    private static final Set<String> SERVICES = Set.of("api-gateway", "customers-service", "vets-service");

    private static ClientCall call(String method, String host, String client, Val<Boolean> loadBalanced) {
        return call("p.C#m", method, host, client, loadBalanced, List.of());
    }

    private static ClientCall call(String where, String method, String host, String client,
            Val<Boolean> loadBalanced, List<String> resilience4j) {
        Val<String> h = host == null ? Val.unknown("C.java:9: host not resolved from uri()")
                : Val.explicit(host, "C.java:9: \"http://" + host + "/x\"");
        return new ClientCall(where, client, loadBalanced, Val.explicit(method, "C.java:9"), h, "/x", "uri()",
                resilience4j, "C.java:9");
    }

    private static final Val<Boolean> LB = Val.explicit(true, "@LoadBalanced bean at Config.java:3");

    private static HttpClientCollector.Discovered discover(EdgesHint hint, ClientCall... calls) {
        return HttpClientCollector.discover("api-gateway", List.of(calls), SERVICES, hint,
                SpringProperties.parse("server.port: 8080", "application.yml"), DefaultsKb.classpath());
    }

    @Test
    void loadBalancedWebClientRequestToAServiceIsAnEdgeWithTheWebClientLayer() {
        HttpClientCollector.Discovered d = discover(null, call("POST", "customers-service", "WebClient", LB));

        Edge e = d.edges().get(0);
        assertThat(d.edges()).hasSize(1);
        assertThat(e.from()).isEqualTo("api-gateway");
        assertThat(e.to()).isEqualTo("customers-service");
        assertThat(e.operation()).isEqualTo("POST /x");
        assertThat(e.layers()).isEmpty();
        assertThat(e.idempotent().value()).isFalse();
        assertThat(e.idempotent().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(e.idempotent().evidence()).startsWith("POST (C.java:9: \"http://customers-service/x\")");
        assertThat(e.fanout()).isEqualTo(Val.unknown(
                "found in code at C.java:9; no edges-hint.yaml entry gives its fanout (ADR 0009)"));

        Layer layer = d.http().layerFor(e).orElseThrow();
        assertThat(layer.kind()).isEqualTo("http-client");
        assertThat(layer.retry().totalAttempts().value()).isEqualTo(1);
        assertThat(layer.retry().totalAttempts().evidence())
                .startsWith("@LoadBalanced WebClient (@LoadBalanced bean at Config.java:3): LoadBalancer retry off");
        assertThat(d.http().notes()).isEmpty();
    }

    @Test
    void loadBalancedRestTemplateIsAnEdgeWithUnknownAttempts() {
        HttpClientCollector.Discovered d = discover(null, call("GET", "vets-service", "RestTemplate", LB));

        Layer layer = d.http().layerFor(d.edges().get(0)).orElseThrow();
        assertThat(layer.retry().totalAttempts().source()).isEqualTo(Source.UNKNOWN);
        assertThat(layer.retry().totalAttempts().evidence())
                .contains("@LoadBalanced RestTemplate", "not verified yet");
        assertThat(d.http().notes()).singleElement().asString()
                .startsWith("C.java:9: p.C#m calls vets-service through a @LoadBalanced RestTemplate");
    }

    @Test
    void requestsThatAreNotLoadBalancedServiceCallsAreNotes() {
        HttpClientCollector.Discovered d = discover(null,
                call("GET", null, "RestClient", Val.explicit(false, "C.java:2: built in place")),
                call("GET", "customers-service", "RestClient", Val.explicit(false, "C.java:2: built in place")),
                call("GET", "billing", "WebClient", LB),
                call("GET", "api.example.com", "RestClient", Val.explicit(false, "C.java:2: built in place")),
                call("GET", "vets-service", "WebClient", Val.unknown("C.java:4: client not traced")));

        assertThat(d.edges()).isEmpty();
        assertThat(d.http().layers()).isEmpty();
        assertThat(d.http().notes()).containsExactly(
                "C.java:9: p.C#m calls GET /x through RestClient with a host computed at run time (uri()); not an edge",
                "C.java:9: p.C#m calls GET /x through RestClient on customers-service without a load balancer "
                        + "(C.java:2: built in place), so the host is resolved by DNS; not an edge",
                "C.java:9: p.C#m calls GET /x through WebClient on billing, which is not a service of this "
                        + "directory; not an edge",
                "C.java:9: p.C#m calls GET /x through WebClient on vets-service; whether it is load-balanced is "
                        + "not known (C.java:4: client not traced); not an edge");
    }

    @Test
    void requestUnderResilience4jAnnotationsNeedsAHintEntry() {
        HttpClientCollector.Discovered d = discover(null,
                call("p.C#m", "GET", "vets-service", "WebClient", LB, List.of("@Retry at C.java:7")));

        assertThat(d.edges()).isEmpty();
        assertThat(d.http().notes()).containsExactly("C.java:9: p.C#m calls GET /x through WebClient on vets-service "
                + "under @Retry at C.java:7; add an edges-hint.yaml entry for p.C#m so that its Resilience4j layer "
                + "is read; not an edge");
    }

    @Test
    void theHintWinsForTheMethodsItNamesAndDuplicatesCollapse() {
        EdgesHint hint = EdgesHint.parse("""
                service: api-gateway
                methods:
                  - { method: p.Customers#getOwner, to: customers-service }
                """, "edges-hint.yaml");
        HttpClientCollector.Discovered d = discover(hint,
                call("p.Customers#getOwner", "GET", "customers-service", "WebClient", LB, List.of()),
                call("p.Other#a", "GET", "vets-service", "WebClient", LB, List.of()),
                call("p.Other#b", "GET", "vets-service", "WebClient", LB, List.of()));

        assertThat(d.edges()).extracting(Edge::to, Edge::operation)
                .containsExactly(org.assertj.core.api.Assertions.tuple("vets-service", "GET /x"));
        assertThat(d.http().notes()).isEmpty();
    }
}
