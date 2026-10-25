package io.github.hasanyelaldi.plimsoll.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hasanyelaldi.plimsoll.analysis.AnalysisOptions;
import io.github.hasanyelaldi.plimsoll.analysis.AnalysisResult;
import io.github.hasanyelaldi.plimsoll.analysis.Analyzer;
import io.github.hasanyelaldi.plimsoll.analysis.Finding;
import io.github.hasanyelaldi.plimsoll.io.SpecLoader;
import io.github.hasanyelaldi.plimsoll.model.UnreadSource;
import io.github.hasanyelaldi.plimsoll.report.Coverage.EdgeSearch;
import io.github.hasanyelaldi.plimsoll.report.Coverage.ServiceSources;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportTest {

    private static final String SPEC = """
            services:
              a: { entry: true, deadline: 1s }
              b: { deadline: 2s }
              c: {}
            edges:
              - { from: a, to: b, layers: [ { kind: resilience4j, maxAttempts: 3, perTryTimeout: 600ms, backoff: 100ms } ] }
              - { from: b, to: c, layers: [ { kind: generic, perTryTimeout: 1s } ] }
            """;

    private static AnalysisResult result() {
        return Analyzer.analyze(SpecLoader.parse(SPEC, "demo.yaml"), AnalysisOptions.defaults());
    }

    @Test
    void textReportMatchesTheGoldenOutput() {
        String expected = """
                Plimsoll analysis of demo.yaml

                Amplification per service (limit 10)
                  a  ×1
                  b  ×3
                  c  at least ×3

                Findings
                  WARN   PLM-A2a  a→b  budget 2 s > deadline 1 s, 1 of 3 tries fit
                          a→b layer 1 (resilience4j) totalAttempts = 3 [EXPLICIT: demo.yaml:6 (maxAttempts)]
                          a→b layer 1 (resilience4j) perTryTimeout = 600 ms [EXPLICIT: demo.yaml:6]
                          a→b layer 1 (resilience4j) backoff = 100 ms [EXPLICIT: demo.yaml:6]
                          a→b layer 1 (resilience4j) multiplier = 1.0 [EXPLICIT: 'multiplier' absent (demo.yaml:6), spec default 1.0 (fixed backoff)]
                          a→b layer 1 (resilience4j) maxBackoff = UNKNOWN [UNKNOWN: 'maxBackoff' absent (demo.yaml:6)]
                          a deadline = 1 s [EXPLICIT: demo.yaml:2]
                """;
        assertThat(TextReport.render(result(), "demo.yaml", AnalysisOptions.defaults(), Collected.SPEC)).startsWith(expected.stripTrailing());
    }

    @Test
    void unknownValuesAreShownAsUnknownWithTheirTag() {
        String text = ReportFormat.TEXT.render(result(), "demo.yaml", AnalysisOptions.defaults());
        assertThat(text).contains("b→c layer 1 (generic) totalAttempts = UNKNOWN [UNKNOWN: 'totalAttempts' absent");
        assertThat(text).contains("depends-on-unknown: b→c layer 1 (generic) totalAttempts");
        String md = ReportFormat.MARKDOWN.render(result(), "demo.yaml", AnalysisOptions.defaults());
        assertThat(md).contains("= UNKNOWN [UNKNOWN:");
        assertThat(md).contains("**depends-on-unknown: ");
    }

    @Test
    void findingsAreSortedBySeverityThenRuleThenLocation() {
        List<Finding> findings = result().findings();
        assertThat(findings).isSortedAccordingTo(Finding.ORDER);
        AnalysisResult shuffled = new AnalysisResult(result().amplification(), findings.reversed());
        assertThat(shuffled.findings()).isNotEqualTo(findings);
    }

    @Test
    void outputIsByteIdenticalAcrossRuns() {
        for (ReportFormat format : ReportFormat.values()) {
            String first = format.render(result(), "demo.yaml", AnalysisOptions.defaults());
            String second = format.render(result(), "demo.yaml", AnalysisOptions.defaults());
            assertThat(second).isEqualTo(first);
            assertThat(first).doesNotContain("\r").endsWith("\n");
        }
    }

    @Test
    void markdownHasSummaryAndTables() {
        String md = ReportFormat.MARKDOWN.render(result(), "demo.yaml", AnalysisOptions.defaults());
        assertThat(md).startsWith("# Plimsoll analysis of `demo.yaml`\n\n**0 error, 2 warn, 3 info**\n");
        assertThat(md).contains("| Severity | Rule | Location | Detail |");
        assertThat(md).contains("| WARN | PLM-A2a | a→b | budget 2 s > deadline 1 s, 1 of 3 tries fit |");
    }

    @Test
    void noFindingsIsStatedExplicitly() {
        AnalysisResult r = Analyzer.analyze(SpecLoader.parse("services: { a: { entry: true } }\n", "x.yaml"), AnalysisOptions.defaults());
        assertThat(ReportFormat.TEXT.render(r, "x.yaml", AnalysisOptions.defaults()))
                .contains("Findings\n  none\n").endsWith("Summary: 0 error, 0 warn, 0 info\n");
    }

    @Test
    void notesArePrintedAsGivenAndOnlyWhenThereAreAny() {
        Collected notes = new Collected(Coverage.SPEC, List.of("a: first | note", "b: second note"));
        String text = ReportFormat.TEXT.render(result(), "demo", AnalysisOptions.defaults(), notes);
        assertThat(text).contains("\nNotes\n  a: first | note\n  b: second note\n\nSummary: ");
        String md = ReportFormat.MARKDOWN.render(result(), "demo", AnalysisOptions.defaults(), notes);
        assertThat(md).endsWith("\n## Notes\n\n- a: first | note\n- b: second note\n");

        AnalysisResult none = Analyzer.analyze(SpecLoader.parse("services: { a: { entry: true } }\n", "x.yaml"),
                AnalysisOptions.defaults());
        assertThat(ReportFormat.MARKDOWN.render(none, "x", AnalysisOptions.defaults(), notes))
                .endsWith("None.\n\n## Coverage\n\nSource: hand-written spec; every layer is taken as written, nothing was collected.\n"
                        + "\n## Notes\n\n- a: first | note\n- b: second note\n");

        for (ReportFormat format : ReportFormat.values()) {
            assertThat(format.render(result(), "demo", AnalysisOptions.defaults(), Collected.SPEC))
                    .isEqualTo(format.render(result(), "demo", AnalysisOptions.defaults()))
                    .doesNotContain("Notes")
                    .doesNotContain("Sources not read");
        }
    }

    @Test
    void aSpecGetsOneCoverageLine() {
        assertThat(ReportFormat.TEXT.render(result(), "demo", AnalysisOptions.defaults())).contains("""

                Coverage
                  Source: hand-written spec; every layer is taken as written, nothing was collected

                Summary: """);
        assertThat(ReportFormat.MARKDOWN.render(result(), "demo", AnalysisOptions.defaults())).endsWith("""

                ## Coverage

                Source: hand-written spec; every layer is taken as written, nothing was collected.
                """);
    }

    @Test
    void coverageListsSourcesReadLayerKindsSearchedAndSourcesNotReadBeforeNotes() {
        Coverage coverage = new Coverage("collected from 2 service modules",
                List.of(new ServiceSources("gw", List.of("gw/pom.xml", "gw/src/main/resources/application.yml")),
                        new ServiceSources("orders", List.of())),
                List.of(),
                List.of(new EdgeSearch("gw→orders", "GET /orders", List.of("spring-cloud-gateway", "istio"),
                                List.of("spring-cloud-gateway")),
                        new EdgeSearch("orders→payments", null, List.of("resilience4j"), List.of())),
                List.of(new UnreadSource("gw", "config-server", "optional:configserver:${URL:http://x|y}",
                                "gw/src/main/resources/application.yml:5"),
                        new UnreadSource("orders", "profile", "spring.profiles.active = docker",
                                "orders/src/main/resources/application.yml:2")));
        Collected collected = new Collected(coverage, List.of("gw: a note"));
        String text = ReportFormat.TEXT.render(result(), "demo", AnalysisOptions.defaults(), collected);
        assertThat(text).contains("""

                Coverage
                  Source: collected from 2 service modules
                  Sources read
                    gw      gw/pom.xml, gw/src/main/resources/application.yml
                    orders  none (no module in the directory)
                    Istio manifests: none
                  Layer kinds searched
                    gw→orders        GET /orders  searched: spring-cloud-gateway, istio; found: spring-cloud-gateway
                    orders→payments  -            searched: resilience4j; found: none
                  Sources not read
                    gw      config-server  optional:configserver:${URL:http://x|y}  (gw/src/main/resources/application.yml:5)
                    orders  profile        spring.profiles.active = docker  (orders/src/main/resources/application.yml:2)

                Notes
                  gw: a note

                Summary: """);
        String md = ReportFormat.MARKDOWN.render(result(), "demo", AnalysisOptions.defaults(), collected);
        assertThat(md).endsWith("""

                ## Coverage

                Source: collected from 2 service modules.

                ### Sources read

                | Service | Sources |
                | --- | --- |
                | gw | gw/pom.xml, gw/src/main/resources/application.yml |
                | orders | none (no module in the directory) |

                Istio manifests: none.

                ### Layer kinds searched

                | Edge | Operation | Searched | Found |
                | --- | --- | --- | --- |
                | gw→orders | GET /orders | spring-cloud-gateway, istio | spring-cloud-gateway |
                | orders→payments | - | resilience4j | none |

                ### Sources not read

                | Service | Kind | Reference | Evidence |
                | --- | --- | --- | --- |
                | gw | config-server | `optional:configserver:${URL:http://x\\|y}` | gw/src/main/resources/application.yml:5 |
                | orders | profile | `spring.profiles.active = docker` | orders/src/main/resources/application.yml:2 |

                ## Notes

                - gw: a note
                """);
        for (ReportFormat format : ReportFormat.values()) {
            assertThat(format.render(result(), "demo", AnalysisOptions.defaults(), collected))
                    .isEqualTo(format.render(result(), "demo", AnalysisOptions.defaults(), collected));
        }
    }
}
