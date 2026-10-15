package io.github.hasanyelaldi.plimsoll.report;

import io.github.hasanyelaldi.plimsoll.analysis.AnalysisOptions;
import io.github.hasanyelaldi.plimsoll.analysis.AnalysisResult;

/** Output formats. Output is a pure function of the result: no timestamps, no unordered collections, "\n" only. */
public enum ReportFormat {
    TEXT {
        @Override
        public String render(AnalysisResult result, String specName, AnalysisOptions options) {
            return TextReport.render(result, specName, options);
        }
    },
    MARKDOWN {
        @Override
        public String render(AnalysisResult result, String specName, AnalysisOptions options) {
            return MarkdownReport.render(result, specName, options);
        }
    };

    public abstract String render(AnalysisResult result, String specName, AnalysisOptions options);
}
