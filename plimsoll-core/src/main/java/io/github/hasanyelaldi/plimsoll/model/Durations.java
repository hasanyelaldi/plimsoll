package io.github.hasanyelaldi.plimsoll.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsing and formatting of spec durations such as {@code 300ms}, {@code 1s}, {@code 2m}. */
public final class Durations {

    private static final Pattern PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)(ms|s|m)");
    private static final BigDecimal NANOS_PER_MS = BigDecimal.valueOf(1_000_000L);

    private Durations() {}

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

    /** {@code 800 ms}, {@code 3 s}, {@code 2.45 s}. Locale independent. */
    public static String format(Duration d) {
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
