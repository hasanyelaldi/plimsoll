package io.github.hasanyelaldi.plimsoll.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code plimsoll analyze <dir>} on the collector fixtures: the petclinic gateway, three services and a mesh. */
class AnalyzeDirectoryTest {

    private static final Path FIXTURES = Path.of("..", "plimsoll-collectors", "src", "test", "resources");

    @TempDir
    Path dir;

    private record Result(int exit, String out, String err) {}

    private Result run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int exit = Main.run(args, new PrintWriter(out), new PrintWriter(err));
        return new Result(exit, out.toString(), err.toString());
    }

    private void copy(String fixture, String target) throws IOException {
        Path from = FIXTURES.resolve(fixture);
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

    private Path petclinic() {
        return dir.resolve("petclinic");
    }

    @BeforeEach
    void petclinicProject() throws IOException {
        copy("projects/petclinic-gateway", "petclinic/api-gateway");
        for (String s : List.of("customers-service", "vets-service", "visits-service")) {
            copy("projects/petclinic-" + s, "petclinic/" + s);
        }
        copy("istio/petclinic", "petclinic/k8s");
        Files.writeString(petclinic().resolve("customers-service/edges-hint.yaml"),
                "service: customers-service\ndeadline: 2s\n");
    }

    @Test
    void analyzesTheCollectedGraphAndPrintsSortedNotes() {
        Result r = run("analyze", petclinic().toString());
        assertThat(r.err()).isEmpty();
        assertThat(r.out()).startsWith("Plimsoll analysis of petclinic\n");
        assertThat(r.out()).contains("\n  customers-service  ×8\n");
        assertThat(r.out()).contains("          api-gateway→customers-service layer 2 (istio) totalAttempts = 4 "
                + "[EXPLICIT: k8s/mesh.yaml:11 (attempts 3 + 1)]");

        String notes = r.out().substring(r.out().indexOf("\nNotes\n"), r.out().indexOf("\nSummary: "));
        List<String> lines = notes.lines().skip(2).toList();
        assertThat(lines).isNotEmpty().isSorted().allMatch(l -> l.startsWith("  "));
        assertThat(lines).contains("  api-gateway: entry, Spring Cloud Gateway routing to customers-service, "
                + "genai-service, vets-service, visits-service (ADR 0009)");
    }

    @Test
    void reportIsByteIdenticalAcrossRuns() {
        for (String format : new String[] {"text", "markdown", "sarif"}) {
            Result first = run("analyze", petclinic().toString(), "--format", format);
            assertThat(first.out()).isEqualTo(run("analyze", petclinic().toString(), "--format", format).out());
        }
        assertThat(run("analyze", petclinic().toString(), "--format", "markdown").out()).contains("\n## Notes\n\n- ");
    }

    /**
     * ADR 0013: every SARIF result points at a line of a file that exists under the analysed directory, and has a
     * fingerprint of its own (gateway findings share lines; A2a reports each layer of an edge).
     */
    @Test
    void sarifLocationsAreFilesOfTheAnalysedDirectory() throws IOException {
        Result r = run("analyze", petclinic().toString(), "--format", "sarif");
        JsonNode results = new ObjectMapper().readTree(r.out()).at("/runs/0/results");
        assertThat(results).isNotEmpty();
        List<String> fingerprints = new ArrayList<>();
        for (JsonNode result : results) {
            fingerprints.add(result.at("/partialFingerprints/primaryLocationLineHash").asText());
            JsonNode location = result.at("/locations/0/physicalLocation");
            String uri = location.at("/artifactLocation/uri").asText();
            assertThat(petclinic().resolve(URI.create(uri).getPath())).as("%s of %s", uri, result.get("ruleId"))
                    .isRegularFile();
            assertThat(location.at("/region/startLine").asInt()).isPositive();
        }
        assertThat(fingerprints).doesNotHaveDuplicates();
    }

    @Test
    void mechanismsNoCollectorModelsAreWarnings() throws IOException {
        Path controller = petclinic().resolve("api-gateway/src/main/java/org/example/OwnerController.java");
        Files.createDirectories(controller.getParent());
        Files.writeString(controller, """
                package org.example;

                import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

                class OwnerController {
                    private final CircuitBreakerFactory cbFactory = null;
                }
                """);
        Result r = run("analyze", petclinic().toString(), "--fail-on", "WARN");
        assertThat(r.exit()).isEqualTo(1);
        assertThat(r.out()).contains("  WARN   PLM-C1   api-gateway  Spring Cloud CircuitBreaker (programmatic) "
                + "detected, not modelled; edges from api-gateway may carry an unseen timeout layer\n"
                + "          api-gateway Spring Cloud CircuitBreaker (programmatic) = type "
                + "org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory "
                + "[EXPLICIT: api-gateway/src/main/java/org/example/OwnerController.java:6; "
                + "https://docs.spring.io/spring-cloud-circuitbreaker/reference/]\n");
    }

    @Test
    void configurationThatWasNotReadIsListedUnderCoverageBeforeTheNotes() {
        Result r = run("analyze", petclinic().toString());
        assertThat(r.out()).contains("""
                  Sources not read
                    api-gateway  config-server     optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888/}  \
                (api-gateway/src/main/resources/application.yml:6)
                    api-gateway  profile-document  spring.config.activate.on-profile = docker  \
                (api-gateway/src/main/resources/application.yml:50)

                Notes
                """);
        assertThat(r.out()).doesNotContain("document skipped");
    }

    @Test
    void coverageSaysWhatWasReadAndSearchedFor() {
        String out = run("analyze", petclinic().toString()).out();
        String coverage = out.substring(out.indexOf("\nCoverage\n"), out.indexOf("\nNotes\n"));
        assertThat(coverage).startsWith("\nCoverage\n  Source: collected from 4 service modules\n  Sources read\n")
                .contains("\n    genai-service      none (no module in the directory)\n")
                .contains("\n    Istio manifests: k8s/mesh.yaml\n  Layer kinds searched\n")
                .contains("searched: spring-cloud-gateway, istio; found: spring-cloud-gateway, istio\n")
                .contains("\n  Sources not read\n");
        assertThat(run("analyze", petclinic().toString(), "--format", "markdown").out())
                .contains("\n## Coverage\n\nSource: collected from 4 service modules.\n\n### Sources read\n");
    }

    @Test
    void invalidHintExitsWithTwo() throws IOException {
        Files.writeString(petclinic().resolve("customers-service/edges-hint.yaml"), "service: customers-service\nbogus: 1\n");
        Result r = run("analyze", petclinic().toString());
        assertThat(r.exit()).isEqualTo(2);
        assertThat(r.out()).isEmpty();
        assertThat(r.err()).startsWith("error: customers-service/edges-hint.yaml:2");
    }

    @Test
    void aDumpOfTheRunningServiceGivesDifferencesAsWarnings() throws IOException {
        copy("projects/effective/orders", "app/orders");
        copy("effective", "app/dumps");
        Path app = dir.resolve("app");
        Result r = run("analyze", app.toString(), "--effective",
                "orders=" + app.resolve("dumps/orders-configprops.json"), "--fail-on", "WARN");
        assertThat(r.exit()).isEqualTo(1);
        assertThat(r.out()).contains("  WARN   PLM-C2   orders  orders→payments-service layer 1 (spring-cloud-gateway) "
                + "totalAttempts: declared 2, runs 3\n");
        assertThat(r.out()).contains("orders            dumps/orders-configprops.json, "
                + "orders/src/main/resources/application.yml");
    }

    @Test
    void aSidecarRouteDumpGivesTheRetriesThatRun() throws IOException {
        copy("projects/effective-mesh", "app");
        copy("effective/envoy", "app/dumps");
        Path app = dir.resolve("app");
        Result r = run("analyze", app.toString(), "--effective", "frontend=" + app.resolve("dumps/http_config.json"),
                "--fail-on", "WARN");
        assertThat(r.exit()).isEqualTo(1);
        assertThat(r.out()).contains("  WARN   PLM-C2   frontend  frontend→productpage layer 2 (istio) "
                + "totalAttempts: declared 2, runs 3\n");
        assertThat(r.out()).contains("    frontend     dumps/http_config.json, frontend/src/main/resources/application.yml\n");
    }

    @Test
    void effectiveNeedsServiceEqualsFileAndADirectory() {
        assertThat(run("analyze", dir.toString(), "--effective", "orders").err())
                .contains("--effective expects <service>=<file>, got orders");
        assertThat(run("analyze", dir.toString(), "--effective", "a=x.json", "--effective", "a=y.json").exit())
                .isEqualTo(2);
        Result spec = run("analyze", Path.of("..", "examples", "checkout.yaml").toString(), "--effective", "a=x.json");
        assertThat(spec.exit()).isEqualTo(2);
        assertThat(spec.err()).contains("--effective needs a directory to collect, not a spec");
    }
}
