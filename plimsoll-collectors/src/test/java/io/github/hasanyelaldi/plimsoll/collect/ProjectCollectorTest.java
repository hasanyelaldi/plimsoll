package io.github.hasanyelaldi.plimsoll.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.hasanyelaldi.plimsoll.collect.detect.Detector;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Detection;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.Service;
import io.github.hasanyelaldi.plimsoll.model.UnreadSource;
import io.github.hasanyelaldi.plimsoll.model.Val;
import io.github.hasanyelaldi.plimsoll.report.Coverage;
import io.github.hasanyelaldi.plimsoll.report.Coverage.EdgeSearch;
import io.github.hasanyelaldi.plimsoll.report.Coverage.ServiceSources;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectCollectorTest {

    private static final Path RESOURCES = Path.of("src/test/resources");

    @TempDir
    Path dir;

    /** Copies a fixture directory to {@code dir/target}. */
    private void copy(String fixture, String target) throws IOException {
        Path from = RESOURCES.resolve(fixture);
        try (Stream<Path> walk = Files.walk(from)) {
            walk.forEach(p -> {
                Path to = dir.resolve(target).resolve(from.relativize(p).toString());
                try {
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(to);
                    } else {
                        Files.createDirectories(to.getParent());
                        Files.copy(p, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private void write(String file, String text) throws IOException {
        Files.createDirectories(dir.resolve(file).getParent());
        Files.writeString(dir.resolve(file), text);
    }

    /** The petclinic gateway, three services with their controllers, and the petclinic mesh. */
    private void petclinic() throws IOException {
        copy("projects/petclinic-gateway", "api-gateway");
        for (String s : new String[] {"customers-service", "vets-service", "visits-service"}) {
            copy("projects/petclinic-" + s, s);
        }
        copy("istio/petclinic", "k8s");
        write("customers-service/edges-hint.yaml", "service: customers-service\ndeadline: 2s\n");
    }

    private static Edge edge(ProjectCollector.Result r, String to, String operation) {
        return r.graph().edges().stream().filter(e -> e.to().equals(to) && e.operation().equals(operation))
                .findFirst().orElseThrow();
    }

    private static Layer innermost(Edge e) {
        return e.layers().get(e.layers().size() - 1);
    }

    @Test
    void petclinicEndToEnd() throws IOException {
        petclinic();
        ProjectCollector.Result r = ProjectCollector.collect(dir);

        assertThat(r.graph().services().keySet()).containsExactly(
                "api-gateway", "customers-service", "genai-service", "vets-service", "visits-service");
        assertThat(r.graph().services().values()).filteredOn(Service::entry)
                .extracting(Service::name).containsExactly("api-gateway");
        assertThat(r.graph().services().get("customers-service").deadline())
                .isEqualTo(Val.explicit(Duration.ofSeconds(2), "customers-service/edges-hint.yaml:2"));

        assertThat(r.graph().edges()).extracting(Edge::to)
                .containsExactly("vets-service", "visits-service", "customers-service", "genai-service");
        assertThat(r.graph().edges()).allSatisfy(e -> assertThat(e.layers()).extracting(Layer::kind)
                .containsExactly("spring-cloud-gateway", "istio"));
        Layer customersMesh = innermost(edge(r, "customers-service", "/api/customer/**"));
        assertThat(customersMesh.retry().totalAttempts())
                .isEqualTo(Val.explicit(4, "k8s/mesh.yaml:11 (attempts 3 + 1)"));
        assertThat(edge(r, "customers-service", "/api/customer/**").idempotent().evidence())
                .contains("customers-service/src/main/java/org/springframework/samples/petclinic/customers/web/");

        assertThat(r.notes()).isSorted().doesNotHaveDuplicates().contains(
                "api-gateway: entry, Spring Cloud Gateway routing to customers-service, genai-service, vets-service, "
                        + "visits-service (ADR 0009)",
                "visits-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies");
        assertThat(r.notes()).contains("api-gateway: route vets-service: the TimeLimiter (10 s) of CircuitBreaker "
                + "'defaultCircuitBreaker' (default-filters[0], api-gateway/src/main/resources/application.yml:12) bounds "
                + "all tries of Retry (default-filters[1]), so it caps that layer's overall timeout");
    }

    @Test
    void coverageListsTheFilesReadPerServiceAndTheLayerKindsSearchedPerEdge() throws IOException {
        petclinic();
        Coverage c = ProjectCollector.collect(dir).coverage();

        assertThat(c.source()).isEqualTo("collected from 4 service modules");
        assertThat(c.services()).extracting(ServiceSources::service).containsExactly(
                "api-gateway", "customers-service", "genai-service", "vets-service", "visits-service");
        assertThat(c.services().get(0).read()).contains("api-gateway/src/main/resources/application.yml");
        assertThat(c.services().get(1).read()).contains("customers-service/edges-hint.yaml",
                "customers-service/src/main/java/").isSorted();
        assertThat(c.services().get(2).read()).isEmpty();
        assertThat(c.manifests()).containsExactly("k8s/mesh.yaml");

        assertThat(c.edges()).extracting(EdgeSearch::edge).isSorted().containsExactly(
                "api-gateway→customers-service", "api-gateway→genai-service", "api-gateway→vets-service",
                "api-gateway→visits-service");
        assertThat(c.edges()).allSatisfy(e -> {
            assertThat(e.searched()).containsExactly("spring-cloud-gateway", "istio");
            assertThat(e.found()).containsExactly("spring-cloud-gateway", "istio");
        });
    }

    @Test
    void petclinicClientCallsInCodeBecomeEdgesOrNotes() throws IOException {
        petclinic();
        copy("projects/petclinic-gateway-clients", "api-gateway");
        copy("projects/petclinic-genai-service", "genai-service");
        ProjectCollector.Result r = ProjectCollector.collect(dir);

        String gw = "api-gateway/src/main/java/org/springframework/samples/petclinic/api/";
        String genai = "genai-service/src/main/java/org/springframework/samples/petclinic/genai/";
        Edge owner = edge(r, "customers-service", "GET /owners/{ownerId}");
        assertThat(owner.from()).isEqualTo("api-gateway");
        assertThat(owner.layers()).extracting(Layer::kind).containsExactly("http-client", "istio");
        assertThat(owner.layers().get(0).retry().totalAttempts().evidence()).startsWith("@LoadBalanced WebClient "
                + "(@LoadBalanced WebClient.Builder bean at " + gw + "ApiGatewayApplication.java:61, injected at "
                + "parameter webClientBuilder at " + gw + "application/CustomersServiceClient.java:32)");
        assertThat(owner.fanout().isKnown()).isFalse();

        Edge visits = edge(r, "visits-service", "GET /pets/visits");
        assertThat(visits.from()).isEqualTo("api-gateway");
        assertThat(visits.idempotent().value()).isTrue();
        assertThat(visits.idempotent().evidence()).contains(gw + "application/VisitsServiceClient.java:45",
                "field hostname is also assigned at " + gw + "application/VisitsServiceClient.java:56");
        // #18: ApiGatewayController wraps getVisitsForPets in the 'getOwnerDetails' breaker
        assertThat(visits.layers()).extracting(Layer::kind).containsExactly("resilience4j", "http-client", "istio");
        Layer breaker = visits.layers().get(0);
        assertThat(breaker.breaker().name()).isEqualTo(Val.explicit("getOwnerDetails",
                gw + "boundary/web/ApiGatewayController.java:61"));
        assertThat(breaker.retry().totalAttempts().value()).isEqualTo(1);
        assertThat(breaker.overallTimeout().value()).isEqualTo(Duration.ofSeconds(10));
        assertThat(breaker.retry().perTryTimeout().evidence()).startsWith(gw + "ApiGatewayApplication.java:89: "
                + "timeoutDuration(Duration.ofSeconds(10)) in configureDefault of @Bean defaultCustomizer");
        assertThat(breaker.conditions().givesUpAs().isKnown()).isTrue();
        assertThat(owner.layers()).extracting(Layer::kind).containsExactly("http-client", "istio");
        assertThat(r.notes()).contains("api-gateway: " + gw + "boundary/web/ApiGatewayController.java:61: the run of "
                + "Spring Cloud CircuitBreaker 'getOwnerDetails' (" + gw + "boundary/web/ApiGatewayController.java:61) "
                + "wraps visitsServiceClient.getVisitsForPets(owner.getPetIds()) has a fallback (throwable -> "
                + "emptyVisitsForPets()), so a failure there is answered, not passed on; the analysis still counts "
                + "it as passed on (fail-close)");

        Edge vets = edge(r, "vets-service", "GET /vets");
        assertThat(vets.from()).isEqualTo("genai-service");

        assertThat(r.graph().edges()).filteredOn(e -> e.from().equals("genai-service")).extracting(Edge::to)
                .containsExactly("vets-service");
        assertThat(r.notes()).filteredOn(n -> n.contains("with a host computed at run time")).containsExactly(
                "genai-service: " + genai + "AIDataProvider.java:45: org.springframework.samples.petclinic.genai."
                        + "AIDataProvider#getAllOwners calls GET {?}/owners through RestClient with a host computed "
                        + "at run time (getCustomerServiceUri() + \"/owners\"); not an edge",
                "genai-service: " + genai + "AIDataProvider.java:73: org.springframework.samples.petclinic.genai."
                        + "AIDataProvider#addPetToOwner calls POST {?}/owners/{ownerId}/pets through RestClient with "
                        + "a host computed at run time (getCustomerServiceUri() + \"/owners/\" + ownerId + \"/pets\"); "
                        + "not an edge",
                "genai-service: " + genai + "AIDataProvider.java:82: org.springframework.samples.petclinic.genai."
                        + "AIDataProvider#addOwnerToPetclinic calls POST {?}/owners through RestClient with a host "
                        + "computed at run time (getCustomerServiceUri() + \"/owners\"); not an edge");

        assertThat(r.coverage().edges()).filteredOn(e -> "GET /vets".equals(e.operation())).singleElement()
                .satisfies(e -> {
                    assertThat(e.searched()).containsExactly("resilience4j", "http-client", "istio");
                    assertThat(e.found()).containsExactly("http-client", "istio");
                });
    }

    @Test
    void edgesFromAHintAreSearchedForResilience4jAndTheHttpClientWithoutAMesh() throws IOException {
        copy("projects/gateway", "gw");
        Coverage c = ProjectCollector.collect(dir).coverage();

        assertThat(c.manifests()).isEmpty();
        assertThat(c.edges()).filteredOn(e -> "GET /owners/{id}".equals(e.operation())).singleElement()
                .satisfies(e -> {
                    assertThat(e.searched()).containsExactly("resilience4j", "http-client");
                    assertThat(e.found()).containsExactly("resilience4j", "http-client");
                });
    }

    @Test
    void sameDirectoryGivesTheSameResult() throws IOException {
        petclinic();
        assertThat(ProjectCollector.collect(dir)).isEqualTo(ProjectCollector.collect(dir));
    }

    @Test
    void moduleWithAHintGetsResilience4jAndHttpClientLayersWithEvidenceRelativeToTheDirectory() throws IOException {
        copy("projects/gateway", "gw");
        ProjectCollector.Result r = ProjectCollector.collect(dir);

        Edge getOwner = edge(r, "customers-service", "GET /owners/{id}");
        assertThat(getOwner.from()).isEqualTo("api-gateway");
        assertThat(getOwner.layers()).extracting(Layer::kind).containsExactly("resilience4j", "http-client");
        assertThat(getOwner.layers().get(0).retry().totalAttempts().evidence())
                .startsWith("gw/src/main/resources/application.yml:");
        assertThat(r.graph().services().get("api-gateway").deadline().evidence())
                .startsWith("'deadline' absent (gw/edges-hint.yaml:1)");
        assertThat(r.notes()).anyMatch(n -> n.startsWith("api-gateway: ") && n.contains("notInTheHint")
                && n.endsWith("is annotated but no edges-hint.yaml entry names it; not analysed"));
        assertThat(r.unreadSources()).contains(new UnreadSource("api-gateway", "config-file",
                "application-docker.yml", "gw/src/main/resources/application-docker.yml"));
    }

    @Test
    void yamlThatIsNotAnIstioManifestIsNotParsed() throws IOException {
        petclinic();
        write("chart/templates/deployment.yaml", "metadata: {{ .Values.name }\n  - [\n");
        assertThat(ProjectCollector.collect(dir).graph().edges()).isNotEmpty();
    }

    @Test
    void checkoutsOfSeveralRepositoriesSideBySideGiveOneGraph() throws IOException {
        copy("projects/petclinic-gateway", "gateway-repo/api-gateway");
        for (String s : new String[] {"customers-service", "vets-service", "visits-service"}) {
            copy("projects/petclinic-" + s, s + "-repo/" + s);
        }
        copy("istio/petclinic", "mesh-repo/k8s");
        write("customers-service-repo/customers-service/edges-hint.yaml",
                "service: customers-service\ndeadline: 2s\n");
        for (String repo : new String[] {"gateway-repo", "customers-service-repo", "vets-service-repo",
                "visits-service-repo", "mesh-repo"}) {
            write(repo + "/.git/HEAD", "ref: refs/heads/main\n");
        }
        ProjectCollector.Result r = ProjectCollector.collect(dir);

        assertThat(r.graph().services().keySet()).containsExactly(
                "api-gateway", "customers-service", "genai-service", "vets-service", "visits-service");
        assertThat(r.graph().services().get("customers-service").deadline()).isEqualTo(
                Val.explicit(Duration.ofSeconds(2), "customers-service-repo/customers-service/edges-hint.yaml:2"));
        assertThat(r.graph().edges()).extracting(Edge::to)
                .containsExactly("vets-service", "visits-service", "customers-service", "genai-service");
        assertThat(r.graph().edges()).allSatisfy(e -> assertThat(e.layers()).extracting(Layer::kind)
                .containsExactly("spring-cloud-gateway", "istio"));
        Edge customers = edge(r, "customers-service", "/api/customer/**");
        assertThat(innermost(customers).retry().totalAttempts())
                .isEqualTo(Val.explicit(4, "mesh-repo/k8s/mesh.yaml:11 (attempts 3 + 1)"));
        assertThat(customers.layers().get(0).retry().totalAttempts().evidence())
                .contains("gateway-repo/api-gateway/src/main/resources/application.yml:");
        assertThat(customers.idempotent().evidence()).contains(
                "customers-service-repo/customers-service/src/main/java/org/springframework/samples/petclinic/");
    }

    @Test
    void mechanismsNoCollectorModelsAreDetectedPerServiceInOrder() throws IOException {
        petclinic();
        write("visits-service/pom.xml", """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.retry</groupId>
                      <artifactId>spring-retry</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);
        write("api-gateway/src/main/java/org/example/Client.java", """
                package org.example;
                import reactor.core.publisher.Mono;
                class Client {
                    Mono<String> get(Mono<String> m) { return m.retry(2); }
                }
                """);
        ProjectCollector.Result r = ProjectCollector.collect(dir);
        assertThat(r.detections()).extracting(Detection::service, Detection::mechanism, Detection::evidence)
                .containsExactly(
                        tuple("api-gateway", "Reactor retry", "api-gateway/src/main/java/org/example/Client.java:4"),
                        tuple("visits-service", "Spring Retry", "visits-service/pom.xml:5"));
        assertThat(ProjectCollector.collect(dir)).isEqualTo(r);
    }

    @Test
    void buildOutputAndHiddenDirectoriesAreSkipped() throws IOException {
        copy("projects/petclinic-gateway", "api-gateway");
        copy("istio/petclinic", "target/k8s");
        copy("projects/petclinic-vets-service", ".cache/vets-service");
        ProjectCollector.Result r = ProjectCollector.collect(dir);
        assertThat(r.graph().edges()).allSatisfy(e -> assertThat(e.layers()).extracting(Layer::kind)
                .containsExactly("spring-cloud-gateway"));
        assertThat(edge(r, "vets-service", "/api/vet/**").idempotent().isKnown()).isFalse();
    }

    @Test
    void directoryWithoutAModuleIsInvalid() throws IOException {
        write("README.md", "nothing here\n");
        assertThatThrownBy(() -> ProjectCollector.collect(dir))
                .isInstanceOf(InvalidSpecException.class).hasMessageContaining("no service module found");
        assertThatThrownBy(() -> ProjectCollector.collect(dir.resolve("README.md")))
                .isInstanceOf(InvalidSpecException.class).hasMessageEndingWith("not a directory");
    }

    @Test
    void twoModulesWithOneNameAreInvalid() throws IOException {
        copy("projects/petclinic-vets-service", "a/vets-service");
        copy("projects/petclinic-vets-service", "b/vets-service");
        assertThatThrownBy(() -> ProjectCollector.collect(dir)).isInstanceOf(InvalidSpecException.class)
                .hasMessage("service vets-service is defined by two modules: a/vets-service and b/vets-service");
    }

    @Test
    void hintAndApplicationNameMustAgree() throws IOException {
        write("svc/edges-hint.yaml", "service: orders\n");
        write("svc/src/main/resources/application.yml", "spring.application.name: billing\n");
        assertThatThrownBy(() -> ProjectCollector.collect(dir)).isInstanceOf(InvalidSpecException.class)
                .hasMessage("svc/edges-hint.yaml: service orders but spring.application.name is billing "
                        + "(svc/src/main/resources/application.yml:1)");
    }

    /** ADR 0016, #25 part 4: an istio ConfigMap alone puts the graph in the mesh, with its retry policy. */
    @Test
    void theMeshConfigsDefaultRetryPolicyAppliesToCalleesWithoutAVirtualService() throws IOException {
        copy("projects/effective-mesh/frontend", "frontend");
        write("deploy/istio-configmap.yaml", """
                apiVersion: v1
                kind: ConfigMap
                metadata: { name: istio, namespace: istio-system }
                data:
                  mesh: |
                    defaultHttpRetryPolicy:
                      attempts: 0
                """);
        ProjectCollector.Result r = ProjectCollector.collect(dir);
        Layer istio = innermost(edge(r, "productpage", "/productpage/**"));
        assertThat(istio.kind()).isEqualTo("istio");
        assertThat(istio.retry().totalAttempts().value()).isEqualTo(1);
        assertThat(istio.retry().totalAttempts().evidence()).startsWith("deploy/istio-configmap.yaml:7 (attempts 0 + 1)");
        assertThat(r.coverage().manifests()).containsExactly("deploy/istio-configmap.yaml");
    }

    // ---- data plane mode (ADR 0017, #27) ----------------------------------

    private void frontendIn(String namespaceLabels, String serviceLabels) throws IOException {
        copy("projects/effective-mesh/frontend", "frontend");
        write("k8s/shop.yaml", """
                apiVersion: v1
                kind: Namespace
                metadata:
                  name: shop
                  labels: { %s }
                ---
                apiVersion: apps/v1
                kind: Deployment
                metadata: { name: frontend, namespace: shop }
                spec:
                  template:
                    metadata: { labels: { app: frontend } }
                ---
                apiVersion: v1
                kind: Service
                metadata:
                  name: productpage
                  namespace: shop
                  labels: { %s }
                """.formatted(namespaceLabels, serviceLabels));
    }

    @Test
    void anAmbientCallerToACalleeWithoutAWaypointHasNoIstioLayer() throws IOException {
        frontendIn("istio.io/dataplane-mode: ambient", "app: productpage");
        ProjectCollector.Result r = ProjectCollector.collect(dir);
        Edge e = edge(r, "productpage", "/productpage/**");
        assertThat(e.layers()).extracting(Layer::kind).doesNotContain("istio");
        assertThat(r.notes()).contains("frontend→productpage: no Istio layer; frontend is in ambient mode "
                + "(k8s/shop.yaml:5 istio.io/dataplane-mode (ambient)) and productpage has no waypoint (no "
                + "istio.io/use-waypoint label on Service productpage (k8s/shop.yaml) or namespace shop "
                + "(k8s/shop.yaml); istio 1.32 (docs) default (verified by doc), "
                + "https://istio.io/latest/docs/ambient/usage/waypoint/), so only ztunnel (L4) is on the path");
        assertThat(r.coverage().manifests()).containsExactly("k8s/shop.yaml");
        assertThat(r.coverage().edges()).filteredOn(s -> s.edge().startsWith("frontend→productpage"))
                .singleElement().satisfies(s -> assertThat(s.searched()).contains("istio"));
    }

    @Test
    void anAmbientCallerToACalleeWithAWaypointGetsTheMeshDefault() throws IOException {
        frontendIn("istio.io/dataplane-mode: ambient", "istio.io/use-waypoint: waypoint");
        ProjectCollector.Result r = ProjectCollector.collect(dir);
        Layer istio = innermost(edge(r, "productpage", "/productpage/**"));
        assertThat(istio.kind()).isEqualTo("istio");
        assertThat(istio.retry().totalAttempts().value()).isEqualTo(3);
    }

    @Test
    void aCallerWithoutASidecarHasNoIstioLayerAndOneWithASidecarKeepsIt() throws IOException {
        // the Service's label is the only data plane label: without any, nothing is read (ADR 0017)
        frontendIn("team: shop", "istio.io/use-waypoint: none");
        ProjectCollector.Result none = ProjectCollector.collect(dir);
        assertThat(edge(none, "productpage", "/productpage/**").layers()).extracting(Layer::kind)
                .doesNotContain("istio");
        assertThat(none.notes()).anyMatch(n -> n.startsWith("frontend→productpage: no Istio layer; frontend has no "
                + "sidecar and is not in ambient mode (no injection label on k8s/shop.yaml frontend or namespace "
                + "shop (k8s/shop.yaml); "));

        frontendIn("istio-injection: enabled", "app: productpage");
        ProjectCollector.Result sidecar = ProjectCollector.collect(dir);
        assertThat(innermost(edge(sidecar, "productpage", "/productpage/**")).kind()).isEqualTo("istio");
        assertThat(sidecar.notes()).noneMatch(n -> n.startsWith("frontend→productpage"));
    }

    @Test
    void aCallerOfUnknownModeKeepsTheIstioLayerWithANote() throws IOException {
        copy("projects/effective-mesh/frontend", "frontend");
        write("k8s/namespace.yaml", """
                apiVersion: v1
                kind: Namespace
                metadata: { name: shop, labels: { istio-injection: enabled } }
                """);
        ProjectCollector.Result r = ProjectCollector.collect(dir);
        assertThat(innermost(edge(r, "productpage", "/productpage/**")).kind()).isEqualTo("istio");
        assertThat(r.notes()).contains("frontend: data plane mode not known (no workload manifest for frontend); "
                + "assumed to have a sidecar, so the Istio layer applies to its calls");
    }

    // ---- HTTPRoute (ADR 0018, #27) ----------------------------------------

    private void productpageRoute() throws IOException {
        write("k8s/route.yaml", """
                apiVersion: gateway.networking.k8s.io/v1
                kind: HTTPRoute
                metadata: { name: productpage, namespace: shop }
                spec:
                  parentRefs: [ { group: "", kind: Service, name: productpage } ]
                  rules:
                  - backendRefs: [ { name: productpage, port: 80 } ]
                    retry: { attempts: 4 }
                """);
    }

    @Test
    void anHttpRouteAppliesThroughAWaypointWithoutTheAlphaNote() throws IOException {
        frontendIn("istio.io/dataplane-mode: ambient", "istio.io/use-waypoint: waypoint");
        productpageRoute();
        write("chart/route.yaml", "apiVersion: gateway.networking.k8s.io/v1\nkind: HTTPRoute\nspec: {{ .Values.x }\n  - [\n");
        ProjectCollector.Result r = ProjectCollector.collect(dir);
        assertThat(innermost(edge(r, "productpage", "/productpage/**")).retry().totalAttempts().value()).isEqualTo(5);
        assertThat(r.notes()).noneMatch(n -> n.contains("Alpha"));
        assertThat(r.notes()).anyMatch(n -> n.startsWith("chart/route.yaml")
                && n.endsWith("; its HTTPRoutes are not read"));
        assertThat(r.coverage().manifests()).contains("k8s/route.yaml");
    }

    @Test
    void anHttpRouteAloneDoesNotPutTheGraphInAnIstioMesh() throws IOException {
        copy("projects/effective-mesh/frontend", "frontend");
        productpageRoute();
        ProjectCollector.Result r = ProjectCollector.collect(dir);
        assertThat(edge(r, "productpage", "/productpage/**").layers()).extracting(Layer::kind).doesNotContain("istio");
        assertThat(r.notes()).contains("k8s/route.yaml:7: HTTPRoute for productpage is not applied; no Istio manifest "
                + "or data plane label says that Istio implements it");
        assertThat(r.coverage().manifests()).isEmpty();
    }

    // ---- effective configuration (ADR 0016, #25) --------------------------

    @Test
    void aDumpOfTheRunningServiceReplacesItsDeclaredValuesAndEachDifferenceIsListed() throws IOException {
        copy("projects/effective/orders", "orders");
        copy("effective", "dumps");
        ProjectCollector.Result declared = ProjectCollector.collect(dir);
        assertThat(declared.differences()).isEmpty();
        assertThat(edge(declared, "payments-service", "/api/payments/**").layers().get(0).retry().totalAttempts()
                .value()).isEqualTo(2);

        ProjectCollector.Result r = ProjectCollector.collect(dir, DefaultsKb.classpath(), Detector.classpath(),
                Map.of("orders", java.util.List.of(dir.resolve("dumps/orders-configprops.json"))));
        Edge payments = edge(r, "payments-service", "/api/payments/**");
        assertThat(payments.layers().get(0).retry().totalAttempts().value()).isEqualTo(3);
        assertThat(r.differences()).singleElement().satisfies(d -> {
            assertThat(d.service()).isEqualTo("orders");
            assertThat(d.setting()).isEqualTo("orders→payments-service layer 1 (spring-cloud-gateway) totalAttempts");
            assertThat(d.declared()).isEqualTo("2");
            assertThat(d.declaredEvidence()).endsWith("orders/src/main/resources/application.yml:16");
            assertThat(d.effective()).isEqualTo("3");
            assertThat(d.effectiveEvidence()).startsWith("Retry (route payments filters[0], "
                    + "dumps/orders-configprops.json:352 (/contexts/orders/beans/gatewayProperties/properties/routes/0/"
                    + "filters/0/args/retries; origin: ");
        });
        assertThat(r.coverage().services()).filteredOn(s -> s.service().equals("orders")).singleElement()
                .satisfies(s -> assertThat(s.read()).contains("dumps/orders-configprops.json",
                        "orders/src/main/resources/application.yml"));
    }

    @Test
    void aDumpMustNameAServiceModuleAndBeADumpPlimsollReads() throws IOException {
        copy("projects/effective/orders", "orders");
        copy("effective", "dumps");
        assertThatThrownBy(() -> ProjectCollector.collect(dir, DefaultsKb.classpath(), Detector.classpath(),
                Map.of("billing", java.util.List.of(dir.resolve("dumps/orders-configprops.json")))))
                .isInstanceOf(InvalidSpecException.class).hasMessageContaining("no service module named billing");
        write("dumps/health.json", "{ \"status\": \"UP\" }");
        assertThatThrownBy(() -> ProjectCollector.collect(dir, DefaultsKb.classpath(), Detector.classpath(),
                Map.of("orders", java.util.List.of(dir.resolve("dumps/health.json")))))
                .isInstanceOf(InvalidSpecException.class).hasMessageContaining("not a dump Plimsoll reads");
    }

    @Test
    void aSidecarRouteDumpReplacesTheDeclaredIstioLayerOfTheEdgesFromItsService() throws IOException {
        copy("projects/effective-mesh", "app");
        copy("effective/envoy", "dumps");
        Map<String, java.util.List<Path>> effective =
                Map.of("frontend", java.util.List.of(dir.resolve("dumps/http_config.json")));
        ProjectCollector.Result declared = ProjectCollector.collect(dir);
        Layer before = edge(declared, "productpage", "/productpage/**").layers().getLast();
        assertThat(before.kind()).isEqualTo("istio");
        assertThat(before.retry().totalAttempts().value()).isEqualTo(2);

        ProjectCollector.Result r = ProjectCollector.collect(dir, DefaultsKb.classpath(), Detector.classpath(), effective);
        Layer running = edge(r, "productpage", "/productpage/**").layers().getLast();
        assertThat(running.retry().totalAttempts().value()).isEqualTo(3);
        assertThat(running.retry().totalAttempts().evidence()).startsWith("dumps/http_config.json:28 (/configs/0/");
        // the same conditions, timeouts and back-off, written once as a VirtualService and once as Envoy config
        assertThat(r.differences()).singleElement().satisfies(d -> {
            assertThat(d.setting()).isEqualTo("frontend→productpage layer 2 (istio) totalAttempts");
            assertThat(d.declared()).isEqualTo("2");
            assertThat(d.declaredEvidence()).startsWith("app/istio/productpage.yaml:15 (attempts 1 + 1)");
            assertThat(d.effective()).isEqualTo("3");
        });
        assertThat(r.coverage().services()).filteredOn(s -> s.service().equals("frontend")).singleElement()
                .satisfies(s -> assertThat(s.read()).contains("dumps/http_config.json"));
        assertThat(r.notes()).noneMatch(n -> n.contains("are not applied"));
    }

    @Test
    void aRouteDumpWithoutManifestsPutsTheServiceInTheMesh() throws IOException {
        copy("projects/effective-mesh/frontend", "frontend");
        copy("effective/envoy", "dumps");
        ProjectCollector.Result r = ProjectCollector.collect(dir, DefaultsKb.classpath(), Detector.classpath(),
                Map.of("frontend", java.util.List.of(dir.resolve("dumps/http_config.json"))));
        assertThat(edge(r, "productpage", "/productpage/**").layers().getLast().kind()).isEqualTo("istio");
        assertThat(r.differences()).singleElement().satisfies(d -> {
            assertThat(d.setting()).isEqualTo("frontend→productpage layers");
            assertThat(d.declared()).doesNotContain("istio");
            assertThat(d.effective()).endsWith("istio");
        });
        assertThat(r.coverage().edges()).filteredOn(e -> e.edge().equals("frontend→productpage")).singleElement()
                .satisfies(e -> assertThat(e.searched()).contains("istio"));
    }

    @Test
    void aServiceTakesOneDumpOfEachFormat() throws IOException {
        copy("projects/effective/orders", "orders");
        copy("effective", "dumps");
        ProjectCollector.Result both = ProjectCollector.collect(dir, DefaultsKb.classpath(), Detector.classpath(),
                Map.of("orders", java.util.List.of(dir.resolve("dumps/orders-configprops.json"),
                        dir.resolve("dumps/envoy/http_config.json"))));
        assertThat(both.differences()).extracting(d -> d.setting()).containsExactly(
                "orders→payments-service layer 1 (spring-cloud-gateway) totalAttempts");
        assertThat(both.notes()).contains("orders: the dump of its sidecar has no route to payments-service; "
                + "no Istio layer is added");
        assertThat(both.notes()).anyMatch(n -> n.startsWith("orders: routes to productpage in the dump of its sidecar ("
                + "dumps/envoy/http_config.json:18 "));
        assertThatThrownBy(() -> ProjectCollector.collect(dir, DefaultsKb.classpath(), Detector.classpath(),
                Map.of("orders", java.util.List.of(dir.resolve("dumps/envoy/http_config.json"),
                        dir.resolve("dumps/envoy/http_config.json")))))
                .isInstanceOf(InvalidSpecException.class).hasMessage("--effective orders: two Envoy config_dump dumps, "
                        + "dumps/envoy/http_config.json and dumps/envoy/http_config.json; give one per service");
    }

    /** #19, ADR 0019: the OpenAI starter gives an edge to the external openai, with no Istio layer even in a mesh. */
    @Test
    void aSpringAiStarterGivesAnEdgeToAnExternalProviderAndItsDetectionGoes() throws IOException {
        petclinic();
        copy("projects/petclinic-genai-service", "genai-service");
        write("genai-service/pom.xml", """
                <project>
                  <properties><spring-ai.version>2.0.0-M1</spring-ai.version></properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.ai</groupId>
                      <artifactId>spring-ai-starter-model-openai</artifactId>
                      <version>${spring-ai.version}</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        ProjectCollector.Result r = ProjectCollector.collect(dir);
        Edge e = edge(r, "openai", "POST /v1/chat/completions");
        assertThat(e.from()).isEqualTo("genai-service");
        assertThat(e.layers()).extracting(Layer::kind).containsExactly("spring-ai");
        assertThat(e.layers().get(0).retry().totalAttempts().value()).isEqualTo(11);
        assertThat(r.graph().services().get("openai").external()).isTrue();
        assertThat(r.graph().services().get("openai").entry()).isFalse();
        assertThat(r.detections()).noneSatisfy(d -> assertThat(d.mechanism()).isEqualTo("Spring AI retry"));
        assertThat(r.notes()).contains("edges to external callees (openai) get no Istio layer: the Istio manifests "
                + "describe the services of the mesh (ADR 0019)");
        assertThat(r.coverage().edges()).filteredOn(s -> s.edge().equals("genai-service→openai")).singleElement()
                .satisfies(s -> {
                    assertThat(s.searched()).containsExactly("spring-ai");
                    assertThat(s.found()).containsExactly("spring-ai");
                });
    }
}
