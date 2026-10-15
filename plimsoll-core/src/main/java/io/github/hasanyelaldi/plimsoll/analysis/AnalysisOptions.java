package io.github.hasanyelaldi.plimsoll.analysis;

/** @param maxAmplification A1 limit: a service above it is an ERROR */
public record AnalysisOptions(int maxAmplification) {

    public static final int DEFAULT_MAX_AMPLIFICATION = 10;

    public static AnalysisOptions defaults() {
        return new AnalysisOptions(DEFAULT_MAX_AMPLIFICATION);
    }
}
