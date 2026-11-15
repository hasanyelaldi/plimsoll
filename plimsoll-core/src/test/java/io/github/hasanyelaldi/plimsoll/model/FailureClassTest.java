package io.github.hasanyelaldi.plimsoll.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR 0014 failure classes. */
class FailureClassTest {

    @Test
    void statusCodesMapToTheirClass() {
        assertThat(FailureClass.status(503)).contains(FailureClass.STATUS_503);
        assertThat(FailureClass.status(501)).contains(FailureClass.STATUS_5XX_OTHER);
        assertThat(FailureClass.status(599)).contains(FailureClass.STATUS_5XX_OTHER);
        assertThat(FailureClass.status(409)).contains(FailureClass.STATUS_409);
        assertThat(FailureClass.status(429)).contains(FailureClass.STATUS_429);
        assertThat(FailureClass.status(404)).contains(FailureClass.STATUS_4XX_OTHER);
        assertThat(FailureClass.status(302)).isEmpty();
        assertThat(FailureClass.status(600)).isEmpty();
    }

    @Test
    void parsesIdsAndBareStatusCodes() {
        assertThat(FailureClass.parse("not-sent")).contains(FailureClass.NOT_SENT);
        assertThat(FailureClass.parse("status-504")).contains(FailureClass.STATUS_504);
        assertThat(FailureClass.parse("502")).contains(FailureClass.STATUS_502);
        assertThat(FailureClass.parse("NOT_SENT")).isEmpty();
        assertThat(FailureClass.parse("connect-failure")).isEmpty();
    }

    @Test
    void onlyNotSentIsUnprocessed() {
        for (FailureClass c : FailureClass.values()) {
            assertThat(c.processed()).isEqualTo(c != FailureClass.NOT_SENT);
        }
    }
}
