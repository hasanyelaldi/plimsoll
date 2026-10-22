package io.github.hasanyelaldi.plimsoll.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceTest {

    @Test
    void linesCompareAsNumbersWithinAFile() {
        assertThat(List.of("b.yml:1", "a.yml:49", "a.yml", "a.yml:5", "a.yml:10").stream().sorted(Evidence.ORDER))
                .containsExactly("a.yml", "a.yml:5", "a.yml:10", "a.yml:49", "b.yml:1");
    }

    @Test
    void aColonThatIsNotALineNumberIsPartOfTheFile() {
        assertThat(Evidence.file("c:/x/a.yml")).isEqualTo("c:/x/a.yml");
        assertThat(Evidence.line("c:/x/a.yml")).isZero();
        assertThat(Evidence.file("x/a.yml:7")).isEqualTo("x/a.yml");
        assertThat(Evidence.line("x/a.yml:7")).isEqualTo(7);
    }
}
