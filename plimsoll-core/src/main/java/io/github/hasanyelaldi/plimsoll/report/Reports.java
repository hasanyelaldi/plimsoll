package io.github.hasanyelaldi.plimsoll.report;

import io.github.hasanyelaldi.plimsoll.analysis.AnalysisResult;
import io.github.hasanyelaldi.plimsoll.analysis.Finding;
import io.github.hasanyelaldi.plimsoll.analysis.ServiceAmplification;
import io.github.hasanyelaldi.plimsoll.analysis.Severity;
import io.github.hasanyelaldi.plimsoll.analysis.UsedValue;
import java.util.List;

/** Wording shared by the text and Markdown reports. */
final class Reports {

    private Reports() {}

    static String summary(AnalysisResult r) {
        return r.count(Severity.ERROR) + " error, " + r.count(Severity.WARN) + " warn, " + r.count(Severity.INFO) + " info";
    }

    static String amplification(ServiceAmplification a) {
        if (a.unbounded()) {
            return "UNBOUNDED";
        }
        return (a.unknowns().isEmpty() ? "×" : "at least ×") + a.value();
    }

    static String value(UsedValue v) {
        return v.display() + " [" + v.source() + ": " + v.evidence() + "]";
    }

    static String read(Coverage.ServiceSources s) {
        return s.read().isEmpty() ? "none (no module in the directory)" : String.join(", ", s.read());
    }

    static String manifests(Coverage c) {
        return c.manifests().isEmpty() ? "none" : String.join(", ", c.manifests());
    }

    static String operation(Coverage.EdgeSearch e) {
        return e.operation() == null ? "-" : e.operation();
    }

    static String kinds(List<String> kinds) {
        return kinds.isEmpty() ? "none" : String.join(", ", kinds);
    }

    static String unknowns(Finding f) {
        return Finding.DEPENDS_ON_UNKNOWN + ": " + String.join(", ", f.unknowns());
    }
}
