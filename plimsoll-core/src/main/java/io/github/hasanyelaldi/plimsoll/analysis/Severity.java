package io.github.hasanyelaldi.plimsoll.analysis;

/** Declared from most to least severe; the order is the sort order of reports. */
public enum Severity {
    ERROR,
    WARN,
    INFO;

    public boolean atLeast(Severity threshold) {
        return ordinal() <= threshold.ordinal();
    }
}
