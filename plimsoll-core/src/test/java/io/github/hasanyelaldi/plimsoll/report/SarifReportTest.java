package io.github.hasanyelaldi.plimsoll.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.github.hasanyelaldi.plimsoll.analysis.AnalysisOptions;
import io.github.hasanyelaldi.plimsoll.analysis.AnalysisResult;
import io.github.hasanyelaldi.plimsoll.analysis.Analyzer;
import io.github.hasanyelaldi.plimsoll.analysis.Finding;
import io.github.hasanyelaldi.plimsoll.analysis.Severity;
import io.github.hasanyelaldi.plimsoll.analysis.UsedValue;
import io.github.hasanyelaldi.plimsoll.io.SpecLoader;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.report.Coverage.ServiceSources;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** SARIF output (#75): valid against the OASIS 2.1.0 schema, deterministic, located per ADR 0013. */
class SarifReportTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static String checkout() throws IOException {
        String yaml = Files.readString(Path.of("..", "examples", "checkout.yaml"));
        AnalysisResult result = Analyzer.analyze(SpecLoader.parse(yaml, "checkout.yaml"), AnalysisOptions.defaults());
        return ReportFormat.SARIF.render(result, "checkout.yaml", AnalysisOptions.defaults());
    }

    private static List<JsonNode> results(String sarif) throws IOException {
        List<JsonNode> results = new ArrayList<>();
        JSON.readTree(sarif).at("/runs/0/results").forEach(results::add);
        return results;
    }

    private static String primary(JsonNode result) {
        JsonNode location = result.at("/locations/0/physicalLocation");
        return location.at("/artifactLocation/uri").asText() + ":" + location.at("/region/startLine").asInt();
    }

    private static JsonNode only(String sarif) throws IOException {
        List<JsonNode> results = results(sarif);
        assertThat(results).hasSize(1);
        return results.get(0);
    }

    private static String render(Finding finding, Collected collected) {
        return ReportFormat.SARIF.render(new AnalysisResult(List.of(), List.of(finding)), "input",
                AnalysisOptions.defaults(), collected);
    }

    private static Finding finding(String location, UsedValue... values) {
        return new Finding("PLM-A2b", Severity.INFO, location, "cannot evaluate", List.of(values), List.of());
    }

    @Test
    void checkoutReportIsValidSarif() throws IOException {
        Schema schema;
        try (InputStream in = getClass().getResourceAsStream("/sarif/sarif-schema-2.1.0.json")) {
            schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_4).getSchema(in);
        }
        List<Error> errors = schema.validate(checkout(), InputFormat.JSON);
        assertThat(errors).isEmpty();
        // The schema does reject: a wrong version and a result level SARIF does not have.
        assertThat(schema.validate(checkout().replace("\"version\": \"2.1.0\"", "\"version\": \"9\""), InputFormat.JSON))
                .isNotEmpty();
        assertThat(schema.validate(checkout().replace("\"level\": \"note\"", "\"level\": \"info\""), InputFormat.JSON))
                .isNotEmpty();
    }

    @Test
    void oneResultPerFindingWithItsLevelAndRule() throws IOException {
        List<JsonNode> results = results(checkout());
        assertThat(results).extracting(r -> r.get("level").asText())
                .containsExactly("error", "error", "warning", "warning", "warning", "warning", "warning", "warning",
                        "warning", "note", "note");
        JsonNode rules = JSON.readTree(checkout()).at("/runs/0/tool/driver/rules");
        for (JsonNode r : results) {
            assertThat(rules.get(r.get("ruleIndex").asInt()).get("id").asText()).isEqualTo(r.get("ruleId").asText());
        }
    }

    @Test
    void findingsAreLocatedAtTheirFirstExplicitFileLine() throws IOException {
        List<JsonNode> results = results(checkout());
        JsonNode a3 = results.get(1);
        assertThat(a3.get("ruleId").asText()).isEqualTo("PLM-A3");
        assertThat(primary(a3)).isEqualTo("checkout.yaml:10");
        assertThat(a3.at("/locations/0/physicalLocation/artifactLocation/uriBaseId").asText()).isEqualTo("SRCROOT");
        assertThat(a3.at("/relatedLocations/0/physicalLocation/region/startLine").asInt()).isEqualTo(11);
        assertThat(a3.at("/message/text").asText())
                .startsWith("orders→payments: non-idempotent call retried, 3 tries\nValues used:\n- ")
                .contains("[EXPLICIT: checkout.yaml:10]");
        assertThat(a3.at("/properties/values/0/source").asText()).isEqualTo("EXPLICIT");
    }

    @Test
    void sameInputGivesByteIdenticalOutput() throws IOException {
        String sarif = checkout();
        assertThat(sarif).isEqualTo(checkout()).endsWith("}\n").doesNotContain("\r");
    }

    @Test
    void explicitValuesComeFirstAndUrlsAreNotLocations() throws IOException {
        JsonNode r = only(render(finding("a→b",
                new UsedValue("a→b fanout", "2", Source.LIBRARY_DEFAULT,
                        "found at svc/src/Client.java:36; https://example.org:8080/docs/x.yaml:3"),
                new UsedValue("a deadline", "1 s", Source.EXPLICIT, "svc/edges-hint.yaml:4")), Collected.SPEC));
        assertThat(primary(r)).isEqualTo("svc/edges-hint.yaml:4");
        assertThat(r.get("relatedLocations")).hasSize(1);
        assertThat(r.at("/relatedLocations/0/physicalLocation/artifactLocation/uri").asText())
                .isEqualTo("svc/src/Client.java");
    }

    @Test
    void withoutFileLineASpecFindingIsAtLineOneOfTheSpec() throws IOException {
        JsonNode r = only(render(finding("a→b",
                new UsedValue("b deadline", "UNKNOWN", Source.UNKNOWN, "'deadline' absent")), Collected.SPEC));
        assertThat(primary(r)).isEqualTo("input:1");
        assertThat(r.has("relatedLocations")).isFalse();
    }

    @Test
    void withoutFileLineACollectedFindingIsAtTheFirstFileReadForItsService() throws IOException {
        Coverage coverage = new Coverage("collected", List.of(
                new ServiceSources("api-gateway", List.of("gateway/src/main/java/", "gateway/pom.xml"))),
                List.of(), List.of(), List.of());
        Collected collected = new Collected(coverage, List.of());

        assertThat(primary(only(render(finding("api-gateway→vets (GET /vets)"), collected))))
                .isEqualTo("gateway/pom.xml:1");
        assertThat(only(render(finding("vets→api-gateway"), collected)).has("locations")).isFalse();
    }

    @Test
    void pathsAreUriEncoded() throws IOException {
        JsonNode r = only(render(finding("a→b",
                new UsedValue("a deadline", "1 s", Source.EXPLICIT, "my svc/edges-hint.yaml:4")), Collected.SPEC));
        // "my svc" is not one path token; the match starts after the space.
        assertThat(primary(r)).isEqualTo("svc/edges-hint.yaml:4");
        r = only(render(finding("a→b",
                new UsedValue("a deadline", "1 s", Source.EXPLICIT, "svc/{id}/edges-hint.yaml:4")), Collected.SPEC));
        assertThat(primary(r)).isEqualTo("svc/%7Bid%7D/edges-hint.yaml:4");
    }

    @Test
    void findingsSharingALineHaveDistinctFingerprints() throws IOException {
        UsedValue retry = new UsedValue("retries", "2", Source.EXPLICIT, "gateway/application.yml:15");
        String sarif = ReportFormat.SARIF.render(new AnalysisResult(List.of(), List.of(
                        new Finding("PLM-A3", Severity.ERROR, "gw→a", "retried", List.of(retry), List.of()),
                        new Finding("PLM-A3", Severity.ERROR, "gw→b", "retried", List.of(retry), List.of()),
                        new Finding("PLM-A3", Severity.ERROR, "gw→b", "retried again", List.of(retry), List.of()))),
                "input", AnalysisOptions.defaults(), Collected.SPEC);
        List<JsonNode> results = results(sarif);
        assertThat(primary(results.get(0))).isEqualTo(primary(results.get(1)));
        List<String> fingerprints = results.stream()
                .map(r -> r.at("/partialFingerprints/primaryLocationLineHash").asText()).toList();
        assertThat(fingerprints.get(0)).matches("[0-9a-f]{64}:1");
        assertThat(fingerprints).doesNotHaveDuplicates();
        // Same rule, severity and location: the same hash, counted.
        assertThat(fingerprints.get(2)).isEqualTo(fingerprints.get(1).replace(":1", ":2"));
    }

    @Test
    void everyRuleTheAnalyzerHasIsDescribed() throws Exception {
        JsonNode rules = JSON.readTree(checkout()).at("/runs/0/tool/driver/rules");
        List<String> described = new ArrayList<>();
        rules.forEach(r -> described.add(r.get("id").asText()));

        Path analysis = Path.of("src", "main", "java", "io", "github", "hasanyelaldi", "plimsoll", "analysis");
        List<String> ids = new ArrayList<>();
        try (Stream<Path> files = Files.list(analysis)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith("Rule.java")).sorted().toList()) {
                String name = file.getFileName().toString().replace(".java", "");
                Field id = Class.forName("io.github.hasanyelaldi.plimsoll.analysis." + name).getDeclaredField("ID");
                id.setAccessible(true);
                ids.add((String) id.get(null));
            }
        }
        assertThat(ids).hasSize(7);
        assertThat(described).containsExactlyInAnyOrderElementsOf(ids);
    }
}
