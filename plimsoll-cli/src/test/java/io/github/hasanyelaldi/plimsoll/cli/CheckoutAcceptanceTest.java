package io.github.hasanyelaldi.plimsoll.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The canonical demo: {@code plimsoll analyze examples/checkout.yaml} must report exactly the table in CLAUDE.md. */
class CheckoutAcceptanceTest {

    private static final Path CHECKOUT = Path.of("..", "examples", "checkout.yaml");

    @Test
    void checkoutReportsExactlyTheElevenExpectedFindings() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int exit = Main.run(new String[] {"analyze", CHECKOUT.toString()}, new PrintWriter(out), new PrintWriter(err));

        assertThat(exit).isEqualTo(1);
        assertThat(err.toString()).isEmpty();
        assertThat(out.toString().lines().filter(l -> l.matches("  (ERROR|WARN|INFO) .*")))
                .containsExactly(
                        "  ERROR  PLM-A1   ledger  ×27 (orders ×3, payments ×9), limit 10",
                        "  ERROR  PLM-A3   orders→payments  non-idempotent call retried, 3 tries",
                        "  WARN   PLM-A2a  gateway→orders  budget 3.2 s > deadline 3 s, 2 of 3 tries fit",
                        "  WARN   PLM-A2a  orders→payments  budget 2.45 s > deadline 2 s, 2 of 3 tries fit",
                        "  WARN   PLM-A2b  gateway→orders  callee deadline 2 s > per-try 1 s",
                        "  WARN   PLM-A2b  orders→payments  callee deadline 2 s > per-try 800 ms",
                        "  WARN   PLM-A2b  payments→ledger  callee deadline 500 ms > per-try 300 ms",
                        "  WARN   PLM-A4   payments→ledger  zero backoff",
                        "  WARN   PLM-A5   gateway→orders→payments→ledger  retries on 3 edges of one path",
                        "  INFO   PLM-A4   gateway→orders  no jitter",
                        "  INFO   PLM-A4   payments→ledger  no jitter");
        assertThat(out.toString()).endsWith("""

                Coverage
                  Source: hand-written spec; every layer is taken as written, nothing was collected

                Summary: 2 error, 7 warn, 2 info
                """);
    }
}
