package io.github.hasanyelaldi.plimsoll.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hasanyelaldi.plimsoll.analysis.AnalysisOptions;
import io.github.hasanyelaldi.plimsoll.analysis.AnalysisResult;
import io.github.hasanyelaldi.plimsoll.analysis.Analyzer;
import io.github.hasanyelaldi.plimsoll.analysis.Finding;
import io.github.hasanyelaldi.plimsoll.io.SpecLoader;
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
        assertThat(TextReport.render(result(), "demo.yaml", AnalysisOptions.defaults())).startsWith(expected.stripTrailing());
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
}
