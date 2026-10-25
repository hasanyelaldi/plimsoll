package io.github.hasanyelaldi.plimsoll.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        for (String format : new String[] {"text", "markdown"}) {
            Result first = run("analyze", petclinic().toString(), "--format", format);
            assertThat(first.out()).isEqualTo(run("analyze", petclinic().toString(), "--format", format).out());
        }
        assertThat(run("analyze", petclinic().toString(), "--format", "markdown").out()).contains("\n## Notes\n\n- ");
    }

    @Test
    void mechanismsNoCollectorModelsAreWarnings() throws IOException {
        Path controller = petclinic().resolve("api-gateway/src/main/java/org/example/OwnerController.java");
        Files.createDirectories(controller.getParent());
        Files.writeString(controller, """
                package org.example;

                import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;

                class OwnerController {
                    private final ReactiveCircuitBreakerFactory cbFactory = null;
                }
                """);
        Result r = run("analyze", petclinic().toString(), "--fail-on", "WARN");
        assertThat(r.exit()).isEqualTo(1);
        assertThat(r.out()).contains("  WARN   PLM-C1   api-gateway  Spring Cloud CircuitBreaker (programmatic) "
                + "detected, not modelled; edges from api-gateway may carry an unseen timeout layer\n"
                + "          api-gateway Spring Cloud CircuitBreaker (programmatic) = type "
                + "org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory "
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
}
