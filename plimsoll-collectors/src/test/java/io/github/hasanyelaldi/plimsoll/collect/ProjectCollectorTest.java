package io.github.hasanyelaldi.plimsoll.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
                        Files.copy(p, to);
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
        assertThat(r.notes()).anyMatch(n -> n.startsWith(
                "api-gateway: api-gateway/src/main/resources/application.yml:12: CircuitBreaker 'defaultCircuitBreaker'"));
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
                    assertThat(e.searched()).containsExactly("http-client", "istio");
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
}
