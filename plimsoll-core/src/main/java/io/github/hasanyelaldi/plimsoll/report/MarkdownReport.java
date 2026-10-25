package io.github.hasanyelaldi.plimsoll.report;

import io.github.hasanyelaldi.plimsoll.analysis.AnalysisOptions;
import io.github.hasanyelaldi.plimsoll.analysis.AnalysisResult;
import io.github.hasanyelaldi.plimsoll.analysis.Finding;
import io.github.hasanyelaldi.plimsoll.analysis.ServiceAmplification;
import io.github.hasanyelaldi.plimsoll.analysis.UsedValue;
import io.github.hasanyelaldi.plimsoll.model.UnreadSource;
import io.github.hasanyelaldi.plimsoll.report.Coverage.EdgeSearch;
import io.github.hasanyelaldi.plimsoll.report.Coverage.ServiceSources;

final class MarkdownReport {

    private MarkdownReport() {}

    static String render(AnalysisResult r, String specName, AnalysisOptions options, Collected collected) {
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
            return collected(out, collected);
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
        return collected(out, collected);
    }

    private static String collected(StringBuilder out, Collected collected) {
        Coverage c = collected.coverage();
        out.append("\n## Coverage\n\n").append("Source: ").append(cell(c.source())).append(".\n");

        if (!c.services().isEmpty() || !c.manifests().isEmpty()) {
            out.append("\n### Sources read\n\n");
            out.append("| Service | Sources |\n| --- | --- |\n");
            for (ServiceSources s : c.services()) {
                out.append("| ").append(cell(s.service())).append(" | ").append(cell(Reports.read(s))).append(" |\n");
            }
            out.append("\nIstio manifests: ").append(cell(Reports.manifests(c))).append(".\n");
        }

        if (!c.edges().isEmpty()) {
            out.append("\n### Layer kinds searched\n\n");
            out.append("| Edge | Operation | Searched | Found |\n| --- | --- | --- | --- |\n");
            for (EdgeSearch e : c.edges()) {
                out.append("| ").append(cell(e.edge())).append(" | ").append(cell(Reports.operation(e))).append(" | ")
                        .append(Reports.kinds(e.searched())).append(" | ").append(Reports.kinds(e.found()))
                        .append(" |\n");
            }
        }

        if (!c.unreadSources().isEmpty()) {
            out.append("\n### Sources not read\n\n");
            out.append("| Service | Kind | Reference | Evidence |\n| --- | --- | --- | --- |\n");
            for (UnreadSource u : c.unreadSources()) {
                out.append("| ").append(cell(u.service())).append(" | ").append(u.kind()).append(" | `")
                        .append(cell(u.reference())).append("` | ").append(cell(u.evidence())).append(" |\n");
            }
        }
        if (!collected.notes().isEmpty()) {
            out.append("\n## Notes\n\n");
            for (String n : collected.notes()) {
                out.append("- ").append(n).append('\n');
            }
        }
        return out.toString();
    }

    private static String cell(String s) {
        return s.replace("|", "\\|");
    }
}
