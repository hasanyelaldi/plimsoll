package io.github.hasanyelaldi.plimsoll.analysis;

import java.math.BigInteger;
import java.util.List;

/**
 * Worst-case number of calls one request to {@code entry} causes at {@code service}.
 *
 * @param value {@code null} when {@code unbounded}
 * @param unknowns attempt counts that were UNKNOWN; when non-empty {@code value} is a lower bound
 */
public record ServiceAmplification(
        String service, String entry, BigInteger value, boolean unbounded, List<String> unknowns) {

    public ServiceAmplification {
        unknowns = List.copyOf(unknowns);
    }
}
