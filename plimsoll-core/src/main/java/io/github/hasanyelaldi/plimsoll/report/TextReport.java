package io.github.hasanyelaldi.plimsoll.report;

import io.github.hasanyelaldi.plimsoll.analysis.AnalysisOptions;
import io.github.hasanyelaldi.plimsoll.analysis.AnalysisResult;
import io.github.hasanyelaldi.plimsoll.analysis.Finding;
import io.github.hasanyelaldi.plimsoll.analysis.ServiceAmplification;
import io.github.hasanyelaldi.plimsoll.analysis.UsedValue;
import io.github.hasanyelaldi.plimsoll.model.UnreadSource;
import java.util.List;

final class TextReport {

    private TextReport() {}

    static String render(AnalysisResult r, String specName, AnalysisOptions options, Collected collected) {
        StringBuilder out = new StringBuilder();
        out.append("Plimsoll analysis of ").append(specName).append('\n');

        out.append("\nAmplification per service (limit ").append(options.maxAmplification()).append(")\n");
        int width = r.amplification().stream().mapToInt(a -> a.service().length()).max().orElse(0);
        for (ServiceAmplification a : r.amplification()) {
            out.append("  ").append(pad(a.service(), width)).append("  ").append(Reports.amplification(a)).append('\n');
        }

        out.append("\nFindings\n");
        if (r.findings().isEmpty()) {
            out.append("  none\n");
        }
        for (Finding f : r.findings()) {
            out.append("  ").append(pad(f.severity().name(), 5)).append("  ").append(pad(f.ruleId(), 7)).append("  ")
                    .append(f.location()).append("  ").append(f.detail()).append('\n');
            for (UsedValue v : f.values()) {
                out.append("          ").append(v.name()).append(" = ").append(Reports.value(v)).append('\n');
            }
            if (f.dependsOnUnknown()) {
                out.append("          ").append(Reports.unknowns(f)).append('\n');
            }
        }

        if (!collected.unreadSources().isEmpty()) {
            out.append("\nSources not read\n");
            List<UnreadSource> unread = collected.unreadSources();
            int service = unread.stream().mapToInt(u -> u.service().length()).max().orElse(0);
            int kind = unread.stream().mapToInt(u -> u.kind().length()).max().orElse(0);
            for (UnreadSource u : unread) {
                out.append("  ").append(pad(u.service(), service)).append("  ").append(pad(u.kind(), kind))
                        .append("  ").append(u.reference()).append("  (").append(u.evidence()).append(")\n");
            }
        }

        if (!collected.notes().isEmpty()) {
            out.append("\nNotes\n");
            for (String n : collected.notes()) {
                out.append("  ").append(n).append('\n');
            }
        }

        out.append("\nSummary: ").append(Reports.summary(r)).append('\n');
        return out.toString();
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s : s + " ".repeat(width - s.length());
    }
}
