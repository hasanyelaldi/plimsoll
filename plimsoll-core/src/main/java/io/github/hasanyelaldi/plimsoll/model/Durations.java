package io.github.hasanyelaldi.plimsoll.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsing and formatting of spec durations such as {@code 300ms}, {@code 1s}, {@code 2m}.
 *
 * <p>{@link #UNLIMITED} is a timeout that is known not to exist, such as an HTTP client without a response timeout
 * (ADR 0011). It is a known value, not UNKNOWN, and is written {@code unlimited}.
 */
public final class Durations {

    /** No timeout: the largest {@link Duration}, so that it compares above every finite one. */
    public static final Duration UNLIMITED = Duration.ofSeconds(Long.MAX_VALUE, 999_999_999);
    public static final String UNLIMITED_TEXT = "unlimited";

    private static final Pattern PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)(ms|s|m)");
    private static final BigDecimal NANOS_PER_MS = BigDecimal.valueOf(1_000_000L);

    private Durations() {}

    public static boolean isUnlimited(Duration d) {
        return UNLIMITED.equals(d);
    }

    /** Like {@link #parse}, and {@code unlimited} for a timeout that does not exist. */
    public static Optional<Duration> parseTimeout(String text) {
        return text.strip().equals(UNLIMITED_TEXT) ? Optional.of(UNLIMITED) : parse(text);
    }

    public static Optional<Duration> parse(String text) {
        Matcher m = PATTERN.matcher(text.strip());
        if (!m.matches()) {
            return Optional.empty();
        }
        BigDecimal amount = new BigDecimal(m.group(1));
        BigDecimal nanosPerUnit =
                switch (m.group(2)) {
                    case "ms" -> NANOS_PER_MS;
                    case "s" -> NANOS_PER_MS.multiply(BigDecimal.valueOf(1000));
                    default -> NANOS_PER_MS.multiply(BigDecimal.valueOf(60_000));
                };
        return Optional.of(Duration.ofNanos(amount.multiply(nanosPerUnit).longValueExact()));
    }

    /** {@code 800 ms}, {@code 3 s}, {@code 2.45 s}, {@code unlimited}. Locale independent. */
    public static String format(Duration d) {
        if (isUnlimited(d)) {
            return UNLIMITED_TEXT;
        }
        long nanos = d.toNanos();
        if (nanos < 1_000_000_000L) {
            return plain(BigDecimal.valueOf(nanos).movePointLeft(6)) + " ms";
        }
        return plain(BigDecimal.valueOf(nanos).movePointLeft(9)) + " s";
    }

    private static String plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return (stripped.signum() == 0 ? BigDecimal.ZERO : stripped).toPlainString();
    }
}
