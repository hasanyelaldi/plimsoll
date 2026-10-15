package io.github.hasanyelaldi.plimsoll.analysis;

import java.util.Comparator;
import java.util.List;

/**
 * One result of a rule.
 *
 * @param ruleId e.g. {@code PLM-A1}
 * @param location service name, edge ({@code a→b}) or path
 * @param values every value the rule used, with provenance
 * @param unknowns names of UNKNOWN values the result depends on; non-empty means tag {@code depends-on-unknown}
 */
public record Finding(
        String ruleId,
        Severity severity,
        String location,
        String detail,
        List<UsedValue> values,
        List<String> unknowns) {

    public static final String DEPENDS_ON_UNKNOWN = "depends-on-unknown";

    /** Severity, then rule id, then location, then detail. */
    public static final Comparator<Finding> ORDER = Comparator.comparing(Finding::severity)
            .thenComparing(Finding::ruleId)
            .thenComparing(Finding::location)
            .thenComparing(Finding::detail);

    public Finding {
        values = List.copyOf(values);
        unknowns = unknowns.stream().distinct().sorted().toList();
    }

    public boolean dependsOnUnknown() {
        return !unknowns.isEmpty();
    }
}
