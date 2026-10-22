package io.github.hasanyelaldi.plimsoll.model;

import java.util.Comparator;

/** Helpers for {@code file:line} evidence strings. */
public final class Evidence {

    /** By file, then by line number as a number ({@code a.yml:5} before {@code a.yml:49}); a bare file comes first. */
    public static final Comparator<String> ORDER =
            Comparator.comparing(Evidence::file).thenComparingInt(Evidence::line);

    private Evidence() {}

    static String file(String evidence) {
        int colon = evidence.lastIndexOf(':');
        return colon >= 0 && isLine(evidence.substring(colon + 1)) ? evidence.substring(0, colon) : evidence;
    }

    static int line(String evidence) {
        int colon = evidence.lastIndexOf(':');
        String tail = evidence.substring(colon + 1);
        return colon >= 0 && isLine(tail) ? Integer.parseInt(tail) : 0;
    }

    private static boolean isLine(String s) {
        return !s.isEmpty() && s.length() < 10 && s.chars().allMatch(Character::isDigit);
    }
}
