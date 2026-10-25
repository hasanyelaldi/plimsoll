package io.github.hasanyelaldi.plimsoll.report;

import io.github.hasanyelaldi.plimsoll.analysis.AnalysisOptions;
import io.github.hasanyelaldi.plimsoll.analysis.AnalysisResult;

/** Output formats. Output is a pure function of the result: no timestamps, no unordered collections, "\n" only. */
public enum ReportFormat {
    TEXT {
        @Override
        public String render(AnalysisResult result, String specName, AnalysisOptions options, Collected collected) {
            return TextReport.render(result, specName, options, collected);
        }
    },
    MARKDOWN {
        @Override
        public String render(AnalysisResult result, String specName, AnalysisOptions options, Collected collected) {
            return MarkdownReport.render(result, specName, options, collected);
        }
    };

    public String render(AnalysisResult result, String specName, AnalysisOptions options) {
        return render(result, specName, options, Collected.SPEC);
    }

    /** @param collected coverage and notes of the graph; {@link Collected#NONE} for a spec */
    public abstract String render(AnalysisResult result, String specName, AnalysisOptions options, Collected collected);
}
