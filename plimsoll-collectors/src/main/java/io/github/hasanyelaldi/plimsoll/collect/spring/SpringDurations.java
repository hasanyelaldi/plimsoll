package io.github.hasanyelaldi.plimsoll.collect.spring;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring Boot's {@code java.time.Duration} property syntax: {@code 500ms}, {@code 2s}, {@code 1m}, a bare number
 * (milliseconds, the default unit of an unannotated {@code Duration} property) or ISO-8601 such as {@code PT0.5S}.
 * Checked against Spring's own {@code DurationStyle} by {@code SpringDurationOracleTest}.
 */
public final class SpringDurations {

    private static final Pattern SIMPLE = Pattern.compile("([+-]?\\d+)([a-zA-Z]{0,2})");
    private static final Pattern ISO = Pattern.compile("[+-]?[pP].*");

    private SpringDurations() {}

    public static Optional<Duration> parse(String text) {
        Matcher m = SIMPLE.matcher(text);
        if (m.matches()) {
            long amount = Long.parseLong(m.group(1));
            ChronoUnit unit = switch (m.group(2).toLowerCase(Locale.ROOT)) {
                case "", "ms" -> ChronoUnit.MILLIS;
                case "ns" -> ChronoUnit.NANOS;
                case "us" -> ChronoUnit.MICROS;
                case "s" -> ChronoUnit.SECONDS;
                case "m" -> ChronoUnit.MINUTES;
                case "h" -> ChronoUnit.HOURS;
                case "d" -> ChronoUnit.DAYS;
                default -> null;
            };
            return unit == null ? Optional.empty() : Optional.of(Duration.of(amount, unit));
        }
        if (ISO.matcher(text).matches()) {
            try {
                return Optional.of(Duration.parse(text));
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
