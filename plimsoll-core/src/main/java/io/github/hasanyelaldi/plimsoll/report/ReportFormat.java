package io.github.hasanyelaldi.plimsoll.report;

import io.github.hasanyelaldi.plimsoll.analysis.AnalysisOptions;
import io.github.hasanyelaldi.plimsoll.analysis.AnalysisResult;
import java.util.List;

/** Output formats. Output is a pure function of the result: no timestamps, no unordered collections, "\n" only. */
public enum ReportFormat {
    TEXT {
        @Override
        public String render(AnalysisResult result, String specName, AnalysisOptions options, List<String> notes) {
            return TextReport.render(result, specName, options, notes);
        }
    },
    MARKDOWN {
        @Override
        public String render(AnalysisResult result, String specName, AnalysisOptions options, List<String> notes) {
            return MarkdownReport.render(result, specName, options, notes);
        }
    };

    public String render(AnalysisResult result, String specName, AnalysisOptions options) {
        return render(result, specName, options, List.of());
    }

    /**
     * @param notes what collecting the graph found but did not apply, printed as given (callers sort them); an empty
     *              list prints no Notes section
     */
    public abstract String render(AnalysisResult result, String specName, AnalysisOptions options, List<String> notes);
}
