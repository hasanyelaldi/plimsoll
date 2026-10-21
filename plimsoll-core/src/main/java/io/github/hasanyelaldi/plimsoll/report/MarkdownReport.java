package io.github.hasanyelaldi.plimsoll.report;

import io.github.hasanyelaldi.plimsoll.analysis.AnalysisOptions;
import io.github.hasanyelaldi.plimsoll.analysis.AnalysisResult;
import io.github.hasanyelaldi.plimsoll.analysis.Finding;
import io.github.hasanyelaldi.plimsoll.analysis.ServiceAmplification;
import io.github.hasanyelaldi.plimsoll.analysis.UsedValue;
import java.util.List;

final class MarkdownReport {

    private MarkdownReport() {}

    static String render(AnalysisResult r, String specName, AnalysisOptions options, List<String> notes) {
        StringBuilder out = new StringBuilder();
        out.append("# Plimsoll analysis of `").append(specName).append("`\n\n");
        out.append("**").append(Reports.summary(r)).append("**\n\n");

        out.append("## Amplification per service\n\n");
        out.append("Limit: ").append(options.maxAmplification()).append("\n\n");
        out.append("| Service | Amplification |\n| --- | --- |\n");
        for (ServiceAmplification a : r.amplification()) {
            out.append("| ").append(cell(a.service())).append(" | ").append(cell(Reports.amplification(a))).append(" |\n");
        }

        out.append("\n## Findings\n\n");
        if (r.findings().isEmpty()) {
            out.append("None.\n");
            return notes(out, notes);
        }
        out.append("| Severity | Rule | Location | Detail |\n| --- | --- | --- | --- |\n");
        for (Finding f : r.findings()) {
            out.append("| ").append(f.severity()).append(" | ").append(f.ruleId()).append(" | ")
                    .append(cell(f.location())).append(" | ").append(cell(f.detail())).append(" |\n");
        }

        out.append("\n## Values used\n");
        for (Finding f : r.findings()) {
            out.append("\n### ").append(f.severity()).append(' ').append(f.ruleId()).append(' ')
                    .append(cell(f.location())).append("\n\n");
            for (UsedValue v : f.values()) {
                out.append("- ").append(cell(v.name())).append(" = ").append(cell(Reports.value(v))).append('\n');
            }
            if (f.dependsOnUnknown()) {
                out.append("- **").append(cell(Reports.unknowns(f))).append("**\n");
            }
        }
        return notes(out, notes);
    }

    private static String notes(StringBuilder out, List<String> notes) {
        if (!notes.isEmpty()) {
            out.append("\n## Notes\n\n");
            for (String n : notes) {
                out.append("- ").append(n).append('\n');
            }
        }
        return out.toString();
    }

    private static String cell(String s) {
        return s.replace("|", "\\|");
    }
}
