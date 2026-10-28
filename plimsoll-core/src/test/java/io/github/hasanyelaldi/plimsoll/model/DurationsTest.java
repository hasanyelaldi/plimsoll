package io.github.hasanyelaldi.plimsoll.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DurationsTest {

    @Test
    void parsesUnits() {
        assertThat(Durations.parse("300ms")).contains(Duration.ofMillis(300));
        assertThat(Durations.parse("1s")).contains(Duration.ofSeconds(1));
        assertThat(Durations.parse("1.5s")).contains(Duration.ofMillis(1500));
        assertThat(Durations.parse("2m")).contains(Duration.ofMinutes(2));
        assertThat(Durations.parse("0ms")).contains(Duration.ZERO);
    }

    @Test
    void rejectsGarbage() {
        assertThat(Durations.parse("300")).isEmpty();
        assertThat(Durations.parse("-1s")).isEmpty();
        assertThat(Durations.parse("1 h")).isEmpty();
    }

    @Test
    void unlimitedIsATimeoutOnly() {
        assertThat(Durations.parseTimeout("unlimited")).contains(Durations.UNLIMITED);
        assertThat(Durations.parseTimeout("300ms")).contains(Duration.ofMillis(300));
        assertThat(Durations.parse("unlimited")).isEmpty();
        assertThat(Durations.format(Durations.UNLIMITED)).isEqualTo("unlimited");
        assertThat(Durations.UNLIMITED).isGreaterThan(Duration.ofDays(365_000));
        assertThat(Durations.isUnlimited(Duration.ofDays(1))).isFalse();
    }

    @Test
    void formatsLikeTheReports() {
        assertThat(Durations.format(Duration.ofMillis(800))).isEqualTo("800 ms");
        assertThat(Durations.format(Duration.ofSeconds(3))).isEqualTo("3 s");
        assertThat(Durations.format(Duration.ofMillis(3200))).isEqualTo("3.2 s");
        assertThat(Durations.format(Duration.ofMillis(2450))).isEqualTo("2.45 s");
        assertThat(Durations.format(Duration.ZERO)).isEqualTo("0 ms");
    }
}
