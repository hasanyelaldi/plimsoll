package io.github.hasanyelaldi.plimsoll.collect;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hasanyelaldi.plimsoll.analysis.AnalysisOptions;
import io.github.hasanyelaldi.plimsoll.analysis.AnalysisResult;
import io.github.hasanyelaldi.plimsoll.analysis.Analyzer;
import io.github.hasanyelaldi.plimsoll.analysis.Finding;
import io.github.hasanyelaldi.plimsoll.analysis.ServiceAmplification;
import io.github.hasanyelaldi.plimsoll.analysis.Severity;
import io.github.hasanyelaldi.plimsoll.collect.http.HttpClientCollector;
import io.github.hasanyelaldi.plimsoll.collect.istio.IstioCollector;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.Resilience4jCollector;
import io.github.hasanyelaldi.plimsoll.model.Durations;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.Layer;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The fixture project {@code projects/gateway} in the mesh {@code istio/petclinic}. */
class GraphAssemblerTest {

    private static final String MESH = "mesh.yaml";

    private static List<Edge> codeEdges;
    private static GraphAssembler.Result result;

    @BeforeAll
    static void assemble() throws URISyntaxException {
        Path project = Path.of(GraphAssemblerTest.class.getResource("/projects/gateway").toURI());
        Path mesh = Path.of(GraphAssemblerTest.class.getResource("/istio/petclinic").toURI());
        codeEdges = Resilience4jCollector.collect(project, project.resolve("edges-hint.yaml")).edges();
        result = GraphAssembler.assemble(codeEdges, IstioCollector.collect(mesh));
    }

    private static Edge edge(String operationSuffix) {
        return result.edges().stream().filter(e -> e.operation().endsWith(operationSuffix)).findFirst().orElseThrow();
    }

    private static AnalysisResult analyze(List<Edge> edges) {
        Map<String, Service> services = new LinkedHashMap<>();
        for (String name : List.of("api-gateway", "customers-service", "visits-service")) {
            services.put(name, new Service(name, Val.unknown("deadline not collected"), name.equals("api-gateway")));
        }
        return Analyzer.analyze(new ServiceGraph(services, edges), AnalysisOptions.defaults());
    }

    private static BigInteger load(AnalysisResult r, String service) {
        return r.amplification().stream().filter(a -> a.service().equals(service))
                .map(ServiceAmplification::value).findFirst().orElseThrow();
    }

    @Test
    void istioLayerIsAddedInnermostAndKeepsItsProvenance() {
        Edge e = edge("GET /owners/{id}");
        assertThat(e.layers()).extracting(Layer::kind).containsExactly("resilience4j", "istio");
        RetryPolicy istio = e.layers().get(1).retry();
        assertThat(istio.totalAttempts()).isEqualTo(Val.explicit(4, MESH + ":11 (attempts 3 + 1)"));
        assertThat(istio.perTryTimeout()).isEqualTo(Val.explicit(Duration.ofMillis(500), MESH + ":12"));
        assertThat(e.layers().get(0)).isEqualTo(codeEdges.get(0).layers().get(0));
    }

    @Test
    void edgeWithoutCodeLayersGetsOnlyTheIstioLayer() {
        assertThat(edge("CustomersClient#plain").layers()).extracting(Layer::kind).containsExactly("istio");
    }

    @Test
    void resilience4jThreeTimesIstioThreeRetriesIsTwelveAndA5() {
        AnalysisResult r = analyze(result.edges());

        // getOwner 3 x 4 + syncLookup 1 x 4 + plain 4
        assertThat(load(r, "customers-service")).isEqualTo(BigInteger.valueOf(20));
        assertThat(r.findings()).filteredOn(f -> f.ruleId().equals("PLM-A5")
                        && f.location().equals("api-gateway→customers-service"))
                .extracting(Finding::severity, Finding::detail)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(Severity.WARN,
                        "retries in 2 layers of one edge (resilience4j ×3, istio ×4), ×12 per call"));
    }

    @Test
    void httpClientLayerGoesBetweenResilience4jAndIstio() throws URISyntaxException {
        Path project = Path.of(GraphAssemblerTest.class.getResource("/projects/gateway").toURI());
        Path mesh = Path.of(GraphAssemblerTest.class.getResource("/istio/petclinic").toURI());
        GraphAssembler.Result r = GraphAssembler.assemble(codeEdges,
                HttpClientCollector.collect(project, project.resolve("edges-hint.yaml")), IstioCollector.collect(mesh));
        Edge getOwner = r.edges().get(0);
        assertThat(getOwner.layers()).extracting(Layer::kind).containsExactly("resilience4j", "http-client", "istio");
        assertThat(getOwner.layers().get(1).retry().totalAttempts().value()).isEqualTo(2);
        assertThat(r.edges().get(4).layers()).extracting(Layer::kind).containsExactly("http-client", "istio");
        assertThat(r.edges().get(1).layers()).extracting(Layer::kind).containsExactly("resilience4j", "istio");

        AnalysisResult a = analyze(r.edges());
        // getOwner 3 x 2 x 4 + syncLookup 1 x 4 + plain 1 x 4
        assertThat(load(a, "customers-service")).isEqualTo(BigInteger.valueOf(32));
        assertThat(a.findings()).filteredOn(f -> f.ruleId().equals("PLM-A5")
                        && f.location().equals("api-gateway→customers-service"))
                .extracting(Finding::detail)
                .containsExactly("retries in 3 layers of one edge (resilience4j ×3, http-client ×2, istio ×4), ×24 per call");
    }

    @Test
    void calleeWithoutVirtualServiceGetsTheIstioDefaultAndANote() {
        RetryPolicy p = edge("CustomersClient#getVisits").layers().get(1).retry();
        assertThat(p.totalAttempts().value()).isEqualTo(3);
        assertThat(p.totalAttempts().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(p.totalAttempts().evidence()).startsWith("no VirtualService for the host; istio 1.31 default");
        assertThat(edge("CustomersClient#getVisits").layers().get(1).overallTimeout().value())
                .isEqualTo(Durations.UNLIMITED);

        // getVisits UNKNOWN (counted as 1, a lower bound) x 3 + list 2 x 3 x fanout 2
        assertThat(load(analyze(result.edges()), "visits-service")).isEqualTo(BigInteger.valueOf(15));
        assertThat(result.notes()).contains(
                "visits-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies");
    }

    @Test
    void routesThatNoEdgeCallsAreNoted() {
        assertThat(result.notes()).containsExactly(
                "visits-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies",
                "vets-service: HTTP routes at " + MESH + ":26 are not applied; no collected edge calls vets-service");
    }

    @Test
    void withoutAMeshEdgesAreUnchanged() {
        GraphAssembler.Result r = GraphAssembler.assemble(codeEdges, null);
        assertThat(r.edges()).isEqualTo(codeEdges);
        assertThat(r.notes()).isEmpty();
    }

    @Test
    void severalRoutesKeepWhatTheyAgreeOnAndLeaveTheRestUnknown(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("vs.yaml"), """
                apiVersion: networking.istio.io/v1
                kind: VirtualService
                metadata: { name: b, namespace: n }
                spec:
                  hosts: [ b ]
                  http:
                    - name: refunds
                      match: [ { uri: { prefix: /refunds } } ]
                      retries: { attempts: 0, perTryTimeout: 1s, retryOn: "5xx,reset" }
                    - name: rest
                      retries: { attempts: 2, perTryTimeout: 1s, retryOn: "reset,5xx" }
                """);
        Edge ab = new Edge("a", "b", "op", Val.explicit(true, "hint"), Val.explicit(1, "hint"), List.of());
        Layer layer = GraphAssembler.assemble(List.of(ab), IstioCollector.collect(dir)).edges().get(0).layers().get(0);
        RetryPolicy p = layer.retry();

        assertThat(p.totalAttempts()).isEqualTo(Val.unknown("b has 2 HTTP routes with different totalAttempts "
                + "(refunds at vs.yaml:7: 1, rest at vs.yaml:10: 3); which one applies depends on the request match"));
        assertThat(p.perTryTimeout()).isEqualTo(Val.explicit(Duration.ofSeconds(1),
                "vs.yaml:9; same in all 2 HTTP routes of b"));
        assertThat(p.retryOn().value()).containsExactlyInAnyOrder("5xx", "reset");
        assertThat(p.multiplier().isKnown()).isFalse();
    }

    @Test
    void differingSetsAreListedInSortedOrder(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("vs.yaml"), """
                apiVersion: networking.istio.io/v1
                kind: VirtualService
                metadata: { name: b, namespace: n }
                spec:
                  hosts: [ b ]
                  http:
                    - retries: { attempts: 1, retryOn: "reset,5xx,gateway-error" }
                    - retries: { attempts: 1, perTryTimeout: 250ms }
                """);
        Edge ab = new Edge("a", "b", "op", Val.explicit(true, "hint"), Val.explicit(1, "hint"), List.of());
        RetryPolicy p = GraphAssembler.assemble(List.of(ab), IstioCollector.collect(dir)).edges().get(0)
                .layers().get(0).retry();

        assertThat(p.retryOn().evidence()).startsWith(
                "b has 2 HTTP routes with different retryOn (http[0] at vs.yaml:7: [5xx,gateway-error,reset], http[1]");
        assertThat(p.perTryTimeout().evidence()).startsWith(
                "b has 2 HTTP routes with different perTryTimeout (http[0] at vs.yaml:7: unlimited, "
                        + "http[1] at vs.yaml:8: 250 ms)");
    }
}
