package io.github.hasanyelaldi.plimsoll.collect.istio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hasanyelaldi.plimsoll.collect.GraphAssembler;
import io.github.hasanyelaldi.plimsoll.collect.istio.IstioCollector.Kind;
import io.github.hasanyelaldi.plimsoll.collect.istio.IstioCollector.Result;
import io.github.hasanyelaldi.plimsoll.collect.istio.IstioCollector.Route;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Durations;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.FailureClass;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ADR 0018, #27 part 2: mesh HTTPRoutes, converted as Istio 1.31 does. */
class HttpRouteTest {

    private static final String BACKEND = "backendRefs: [ { name: orders, port: 80 } ]";

    @TempDir
    Path dir;

    /** An HTTPRoute of Service {@code orders} in {@code shop}; its rules start at line 9. */
    private static String httpRoute(String namespace, String parent, String rules) {
        return """
                apiVersion: gateway.networking.k8s.io/v1
                kind: HTTPRoute
                metadata:
                  name: orders-route
                  namespace: %s
                spec:
                  parentRefs: [ %s ]
                  rules:
                %s---
                """.formatted(namespace, parent, rules.indent(2));
    }

    private static String route(String rules) {
        return httpRoute("shop", "{ group: \"\", kind: Service, name: orders }", rules);
    }

    private Result collect(String... docs) throws IOException {
        Files.writeString(dir.resolve("r.yaml"), String.join("", docs));
        return IstioCollector.collect(dir);
    }

    private Layer layer(String... ruleLines) throws IOException {
        Result r = collect(route("- " + BACKEND + "\n" + String.join("\n", ruleLines).indent(2)));
        assertThat(r.routes()).hasSize(1);
        return r.routes().get(0).layer();
    }

    private static Layer assembled(Result r) {
        Edge e = new Edge("web", "orders", null, Val.unknown("-"), Val.unknown("-"), List.of(), null);
        return GraphAssembler.assemble(List.of(e), r).edges().get(0).layers().get(0);
    }

    @Test
    void attemptsCountRetriesOnlyAndCodesAddToTheConnectionConditions() throws IOException {
        Result r = collect(route("- " + BACKEND + "\n  retry:\n    attempts: 3\n    codes: [503]\n    backoff: 100ms\n"
                + "  timeouts: { request: 2s, backendRequest: 500ms }"));
        Route route = r.routes().get(0);
        assertThat(route.to()).isEqualTo("orders");
        assertThat(route.host()).isEqualTo("orders.shop.svc.cluster.local");
        assertThat(route.route()).isEqualTo("orders-route rules[0]");
        assertThat(route.evidence()).isEqualTo("r.yaml:9");
        Layer l = route.layer();
        assertThat(l.kind()).isEqualTo("istio");
        assertThat(l.retry().totalAttempts()).isEqualTo(Val.explicit(4, "r.yaml:11 (attempts 3 + 1)"));
        assertThat(l.retry().retryOn().value()).isEqualTo(
                Set.of("connect-failure", "refused-stream", "unavailable", "cancelled", "503"));
        assertThat(l.retry().retryOn().evidence()).startsWith("r.yaml:12; ");
        assertThat(l.conditions().retried().value())
                .containsExactlyInAnyOrder(FailureClass.NOT_SENT, FailureClass.STATUS_503);
        assertThat(l.retry().initialBackoff()).isEqualTo(Val.explicit(Duration.ofMillis(100), "r.yaml:13"));
        assertThat(l.retry().perTryTimeout()).isEqualTo(Val.explicit(Duration.ofMillis(500), "r.yaml:14"));
        assertThat(l.overallTimeout()).isEqualTo(Val.explicit(Duration.ofSeconds(2), "r.yaml:14"));
    }

    @Test
    void retryWithoutAttemptsIsUnknownBecauseTheDefaultDependsOnTheCrdVersion() throws IOException {
        Layer l = layer("retry: { codes: [500] }");
        assertThat(l.retry().totalAttempts().isKnown()).isFalse();
        assertThat(l.retry().totalAttempts().evidence())
                .startsWith("retry at r.yaml:10 has no attempts; its default depends on the installed Gateway API "
                        + "version: 1 (istio Gateway API CRDs 1.7.0 default (verified by source), ")
                .contains(", 2 (istio 1.31, Gateway API CRDs up to 1.6 default (verified by source), ");
        assertThat(l.retry().initialBackoff().value()).isEqualTo(Duration.ofMillis(25));
    }

    @Test
    void attemptsZeroIsOneTryAndRemovesThePerTryTimeout() throws IOException {
        Layer l = layer("retry: { attempts: 0 }", "timeouts: { request: 2s, backendRequest: 500ms }");
        assertThat(l.retry().totalAttempts().value()).isEqualTo(1);
        assertThat(l.retry().perTryTimeout().value()).isEqualTo(Duration.ofSeconds(2));
        assertThat(IstioCollector.collect(dir).notes()).anyMatch(n -> n.startsWith("r.yaml:11: backendRequest is not "
                + "applied"));
    }

    @Test
    void withoutRetryTheMeshDefaultAppliesAndBackendRequestIsTheRouteTimeout() throws IOException {
        Layer l = layer("timeouts: { request: 2s, backendRequest: 500ms }");
        assertThat(l.retry().totalAttempts().value()).isEqualTo(3);
        assertThat(l.retry().totalAttempts().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(l.overallTimeout()).isEqualTo(Val.explicit(Duration.ofMillis(500), "r.yaml:10"));
        assertThat(l.retry().perTryTimeout().value()).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void withoutRetryTheMeshConfigPolicyApplies() throws IOException {
        Result r = collect(route("- " + BACKEND), """
                apiVersion: install.istio.io/v1alpha1
                kind: IstioOperator
                metadata: { name: mesh }
                spec: { meshConfig: { defaultHttpRetryPolicy: { attempts: 4 } } }
                """);
        assertThat(r.routes().get(0).layer().retry().totalAttempts().value()).isEqualTo(5);
    }

    @Test
    void zeroTimeoutsAreNoTimeouts() throws IOException {
        Layer l = layer("retry: { attempts: 1 }", "timeouts: { request: 0s, backendRequest: 0s }");
        assertThat(l.overallTimeout()).isEqualTo(Val.explicit(Durations.UNLIMITED, "r.yaml:11"));
        assertThat(l.retry().perTryTimeout().value()).isEqualTo(Durations.UNLIMITED);
        assertThat(layer().overallTimeout().source()).isEqualTo(Source.LIBRARY_DEFAULT);
    }

    @Test
    void aRouteBoundToGatewaysOnlyIsSkippedAndOneBoundToBothIsRead() throws IOException {
        Result skipped = collect(httpRoute("shop", "{ name: gw }", "- " + BACKEND));
        assertThat(skipped.routes()).isEmpty();
        assertThat(skipped.notes()).containsExactly(
                "r.yaml:7: HTTPRoute orders-route binds gateways only, not the mesh; skipped");
        Result both = collect(httpRoute("shop", "{ name: gw }, { group: \"\", kind: Service, name: orders }",
                "- " + BACKEND));
        assertThat(both.routes()).hasSize(1);
        assertThat(both.notes()).isEmpty();
        // without group: "" the kind is in the Gateway API group, where there is no Service
        assertThat(collect(httpRoute("shop", "{ kind: Service, name: orders }", "- " + BACKEND)).routes()).isEmpty();
    }

    @Test
    void severalParentsAreSeveralHostsAndPortsOfOneHostAreReadOnce() throws IOException {
        Result r = collect(httpRoute("shop", "{ group: \"\", kind: Service, name: orders, port: 80 }, "
                + "{ group: \"\", kind: Service, name: orders, port: 81 }, "
                + "{ group: \"\", kind: Service, name: billing }", "- " + BACKEND));
        assertThat(r.routes()).extracting(Route::to).containsExactly("orders", "billing");
        assertThat(r.notes()).filteredOn(n -> n.contains("is attached to one port of orders.shop")).hasSize(2);
    }

    @Test
    void rulesOfOneHostKeepWhatTheyAgreeOnAndOneWithoutABackendIsNotRead() throws IOException {
        Result r = collect(route("- " + BACKEND + "\n  name: first\n  retry: { attempts: 1, backoff: 10ms }\n"
                + "- " + BACKEND + "\n  retry: { attempts: 2, backoff: 10ms }\n"
                + "- backendRefs: [ { name: orders, weight: 0 } ]\n"));
        assertThat(r.routes()).extracting(Route::route).containsExactly("orders-route first", "orders-route rules[1]");
        assertThat(r.notes()).anyMatch(n -> n.contains("rules[2] has no backend with a weight"));
        Layer l = assembled(r);
        assertThat(l.retry().totalAttempts().isKnown()).isFalse();
        assertThat(l.retry().initialBackoff().value()).isEqualTo(Duration.ofMillis(10));
    }

    @Test
    void aVirtualServiceForTheSameHostIsNotedAndMerged() throws IOException {
        Result r = collect(route("- " + BACKEND + "\n  retry: { attempts: 4 }"), """
                apiVersion: networking.istio.io/v1
                kind: VirtualService
                metadata: { name: orders, namespace: shop }
                spec:
                  hosts: [orders]
                  http: [ { retries: { attempts: 1 } } ]
                """);
        assertThat(r.routes()).extracting(Route::kind).containsExactly(Kind.HTTP_ROUTE, Kind.VIRTUAL_SERVICE);
        assertThat(r.notes()).containsExactly("r.yaml:9: host orders.shop.svc.cluster.local has this HTTPRoute and a "
                + "VirtualService at r.yaml:16; which one a proxy uses is not modelled");
        assertThat(assembled(r).retry().totalAttempts().isKnown()).isFalse();
    }

    @Test
    void aConsumerRouteIsACandidateNextToTheMeshDefaultUnlessTheServiceHasItsOwn() throws IOException {
        String consumer = httpRoute("web", "{ group: \"\", kind: Service, name: orders, namespace: shop }",
                "- " + BACKEND + "\n  retry: { attempts: 4 }");
        Result r = collect(consumer);
        assertThat(r.routes()).extracting(Route::kind).containsExactly(Kind.HTTP_ROUTE, Kind.NONE);
        assertThat(r.notes()).anyMatch(n -> n.contains("(a consumer route)"));
        Val<Integer> attempts = assembled(r).retry().totalAttempts();
        assertThat(attempts.isKnown()).isFalse();
        assertThat(attempts.evidence()).endsWith("which one applies depends on the caller");
        for (String[] docs : List.of(new String[] {consumer, route("- " + BACKEND)},
                new String[] {route("- " + BACKEND), consumer})) {
            assertThat(collect(docs).routes()).extracting(Route::kind)
                    .containsExactly(Kind.HTTP_ROUTE, Kind.HTTP_ROUTE);
        }
    }

    @Test
    void grpcRoutesAndOtherParentsAreNoted() throws IOException {
        Result r = collect(route("- " + BACKEND).replace("HTTPRoute", "GRPCRoute"), httpRoute("shop",
                "{ group: networking.istio.io, kind: ServiceEntry, name: ext }", "- " + BACKEND));
        assertThat(r.routes()).isEmpty();
        assertThat(r.notes()).containsExactly("r.yaml:1: GRPCRoute orders-route is not read",
                "r.yaml:17: HTTPRoute orders-route parent ServiceEntry is not read");
    }

    @Test
    void invalidYamlIsRejectedUnlessTheFileIsLenient() throws IOException {
        Path file = dir.resolve("r.yaml");
        Files.writeString(file, "kind: HTTPRoute\nspec: {{ .Values.x }\n  - [\n");
        DefaultsKb kb = DefaultsKb.classpath();
        assertThatThrownBy(() -> IstioCollector.collect(dir, List.of(file), kb))
                .isInstanceOf(InvalidSpecException.class);
        assertThat(IstioCollector.collect(dir, List.of(file), Set.of(file), kb).notes()).singleElement()
                .satisfies(n -> assertThat(n).startsWith("r.yaml").endsWith("; its HTTPRoutes are not read"));
    }
}
