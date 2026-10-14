package io.github.hasanyelaldi.plimsoll;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlimsollTest {

    @Test
    void nameIsStable() {
        assertThat(Plimsoll.NAME).isEqualTo("plimsoll");
    }
}
