package io.github.hasanyelaldi.plimsoll.collect.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hasanyelaldi.plimsoll.collect.spring.SpringDurations;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.convert.DurationStyle;

/** {@link SpringDurations} must accept and reject exactly what Spring Boot's {@code DurationStyle} does. */
class SpringDurationOracleTest {

    @ParameterizedTest
    @ValueSource(strings = {"500ms", "500", "0", "2s", "1m", "1h", "1d", "100us", "10ns", "10MS", "1S", "-5s", "+3s",
        " 7s ", "PT0.5S", "pt1h30m", "P1DT2H", "-PT1S", "1.5s", "5x", "", "ms", "PT", "P", "1sec", "1 s", "1_000ms"})
    void sameAsSpringBoot(String text) {
        assertThat(SpringDurations.parse(text)).isEqualTo(spring(text));
    }

    private static Optional<Duration> spring(String text) {
        try {
            return Optional.of(DurationStyle.detectAndParse(text, ChronoUnit.MILLIS));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
