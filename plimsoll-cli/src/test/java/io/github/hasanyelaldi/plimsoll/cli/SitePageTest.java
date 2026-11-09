package io.github.hasanyelaldi.plimsoll.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The project page ({@code site/index.html}, #68) shows the checkout report and a chart of its amplification. Neither
 * is written by hand: both must be what {@code plimsoll analyze examples/checkout.yaml} prints today.
 */
class SitePageTest {

    private static final Path PAGE = Path.of("..", "site", "index.html");
    private static final Path CHECKOUT = Path.of("..", "examples", "checkout.yaml");

    private static final Pattern REPORT = Pattern.compile(
            "<pre class=\"report\" id=\"checkout-report\"[^>]*>(.*?)</pre>", Pattern.DOTALL);
    private static final Pattern BAR = Pattern.compile(
            "<g class=\"bar[^\"]*\" data-service=\"([a-z-]+)\" data-amp=\"(\\d+)\">(.*?)</g>", Pattern.DOTALL);
    private static final Pattern AMPLIFICATION = Pattern.compile("^  (\\S+)\\s+×(\\d+)$");
    private static final Pattern LIMIT = Pattern.compile(
            "<g class=\"limit\" data-limit=\"(\\d+)\">(.*?)</g>", Pattern.DOTALL);
    private static final Pattern REPORTED_LIMIT = Pattern.compile("Amplification per service \\(limit (\\d+)\\)");

    private static String page;
    private static String report;

    @BeforeAll
    static void load() throws IOException {
        page = Files.readString(PAGE, StandardCharsets.UTF_8);
        StringWriter out = new StringWriter();
        Main.run(new String[] {"analyze", CHECKOUT.toString()}, new PrintWriter(out), new PrintWriter(new StringWriter()));
        report = out.toString();
    }

    @Test
    void embeddedReportIsTheCliOutput() {
        Matcher m = REPORT.matcher(page);
        assertThat(m.find()).as("<pre id=\"checkout-report\"> in site/index.html").isTrue();
        assertThat(unescape(m.group(1))).isEqualTo(report);
    }

    @Test
    void chartBarsAreTheReportedAmplification() {
        Map<String, String> reported = new LinkedHashMap<>();
        boolean inSection = false;
        for (String line : report.lines().toList()) {
            if (line.startsWith("Amplification per service")) {
                inSection = true;
            } else if (inSection) {
                Matcher a = AMPLIFICATION.matcher(line);
                if (!a.matches()) {
                    break;
                }
                reported.put(a.group(1), a.group(2));
            }
        }

        Map<String, String> charted = new LinkedHashMap<>();
        Matcher bar = BAR.matcher(page);
        while (bar.find()) {
            String service = bar.group(1);
            String amp = bar.group(2);
            assertThat(bar.group(3)).as("labels of the %s bar", service)
                    .contains(">×" + amp + "<")
                    .contains(">" + service + "<");
            charted.put(service, amp);
        }

        assertThat(reported).isNotEmpty();
        assertThat(charted).containsExactlyInAnyOrderEntriesOf(reported);
    }

    @Test
    void chartLimitLineIsTheReportedLimit() {
        Matcher reported = REPORTED_LIMIT.matcher(report);
        assertThat(reported.find()).as("limit in the report header").isTrue();

        Matcher limit = LIMIT.matcher(page);
        assertThat(limit.find()).as("<g class=\"limit\"> in the chart").isTrue();
        assertThat(limit.group(1)).isEqualTo(reported.group(1));
        assertThat(limit.group(2)).contains(">limit ×" + reported.group(1) + "<");
    }

    private static String unescape(String html) {
        return html.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
    }
}
