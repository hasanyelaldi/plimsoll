package io.github.hasanyelaldi.plimsoll.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

    /** Three hops of x3: ledger is x27 (ERROR) and the budgets overrun (WARN). */
    private static final String RISKY = """
            services:
              gateway:  { entry: true, deadline: 3s }
              orders:   { deadline: 2s }
              payments: { deadline: 2s }
              ledger:   { deadline: 500ms }
            edges:
              - { from: gateway,  to: orders,   layers: [ { kind: resilience4j, maxAttempts: 3, perTryTimeout: 1s, backoff: 100ms } ] }
              - { from: orders,   to: payments, layers: [ { kind: istio, attempts: 2, perTryTimeout: 800ms, backoff: 25ms } ] }
              - { from: payments, to: ledger,   layers: [ { kind: resilience4j, maxAttempts: 3, perTryTimeout: 300ms, backoff: 0ms } ] }
            """;

    /** The budget fits the caller's deadline, but the callee keeps working after a retried try: one WARN. */
    private static final String WARN_ONLY = """
            services:
              a: { entry: true, deadline: 1s }
              b: { deadline: 2s }
            edges:
              - { from: a, to: b, layers: [ { kind: generic, totalAttempts: 2, perTryTimeout: 500ms, backoff: 0ms } ] }
            """;

    /** Same gap between callee deadline and per-try, but nothing is retried: one INFO. */
    private static final String INFO_ONLY = """
            services:
              a: { entry: true, deadline: 1s }
              b: { deadline: 2s }
            edges:
              - { from: a, to: b, layers: [ { kind: generic, totalAttempts: 1, perTryTimeout: 500ms } ] }
            """;

    /** Nothing to report. */
    private static final String CLEAN = """
            services:
              a: { entry: true, deadline: 5s }
              b: { deadline: 500ms }
            edges:
              - { from: a, to: b, layers: [ { kind: generic, totalAttempts: 2, perTryTimeout: 1s, backoff: 10ms } ] }
            """;

    @TempDir
    Path dir;

    private record Result(int exit, String out, String err) {}

    private Result run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int exit = Main.run(args, new PrintWriter(out), new PrintWriter(err));
        return new Result(exit, out.toString(), err.toString());
    }

    private String spec(String name, String yaml) throws IOException {
        return Files.writeString(dir.resolve(name), yaml).toString();
    }

    @Test
    void errorFindingExitsWithOneAndPrintsTheReport() throws IOException {
        Result r = run("analyze", spec("risky.yaml", RISKY));
        assertThat(r.exit()).isEqualTo(1);
        assertThat(r.out()).startsWith("Plimsoll analysis of risky.yaml\n");
        assertThat(r.out()).contains("ERROR  PLM-A1   ledger  ×27 (orders ×3, payments ×9), limit 10");
        assertThat(r.err()).isEmpty();
    }

    @Test
    void cleanSpecExitsWithZero() throws IOException {
        Result r = run("analyze", spec("clean.yaml", CLEAN));
        assertThat(r.exit()).isZero();
        assertThat(r.out()).contains("Findings\n  none\n").endsWith("Summary: 0 error, 0 warn, 0 info\n");
    }

    @Test
    void failOnControlsTheExitCode() throws IOException {
        String warnOnly = spec("warn.yaml", WARN_ONLY);
        Result byDefault = run("analyze", warnOnly);
        assertThat(byDefault.out()).contains("WARN");
        assertThat(byDefault.exit()).isZero();
        assertThat(run("analyze", warnOnly, "--fail-on", "warn").exit()).isEqualTo(1);
        assertThat(run("analyze", warnOnly, "--fail-on", "WARN").exit()).isEqualTo(1);
        String infoOnly = spec("info.yaml", INFO_ONLY);
        assertThat(run("analyze", infoOnly, "--fail-on", "warn").exit()).isZero();
        assertThat(run("analyze", infoOnly, "--fail-on", "info").exit()).isEqualTo(1);
        assertThat(run("analyze", spec("clean.yaml", CLEAN), "--fail-on", "info").exit()).isZero();
    }

    @Test
    void maxAmpMovesTheLimit() throws IOException {
        String risky = spec("risky.yaml", RISKY);
        assertThat(run("analyze", risky, "--max-amp", "27").out()).doesNotContain("PLM-A1   ledger");
        assertThat(run("analyze", risky, "--max-amp", "27").exit()).isZero();
        assertThat(run("analyze", risky, "--max-amp", "26").exit()).isEqualTo(1);
    }

    @Test
    void markdownFormat() throws IOException {
        Result r = run("analyze", spec("risky.yaml", RISKY), "--format", "markdown");
        assertThat(r.out()).startsWith("# Plimsoll analysis of `risky.yaml`");
        assertThat(r.out()).contains("| ERROR | PLM-A1 | ledger |");
    }

    @Test
    void reportIsByteIdenticalAcrossRuns() throws IOException {
        String risky = spec("risky.yaml", RISKY);
        for (String format : new String[] {"text", "markdown"}) {
            assertThat(run("analyze", risky, "--format", format).out())
                    .isEqualTo(run("analyze", risky, "--format", format).out());
        }
    }

    @Test
    void invalidInputExitsWithTwo() throws IOException {
        Result bad = run("analyze", spec("bad.yaml", "services: [\n"));
        assertThat(bad.exit()).isEqualTo(2);
        assertThat(bad.out()).isEmpty();
        assertThat(bad.err()).startsWith("error: bad.yaml: invalid YAML");

        Result typo = run("analyze", spec("typo.yaml", "services: { a: { entry: true, deadlin: 1s } }\n"));
        assertThat(typo.exit()).isEqualTo(2);
        assertThat(typo.err()).contains("typo.yaml:1: unknown key 'deadlin'");
    }

    @Test
    void missingFileDirectoryAndUsageErrorsExitWithTwo() {
        assertThat(run("analyze", dir.resolve("nope.yaml").toString()).exit()).isEqualTo(2);
        assertThat(run("analyze", dir.toString()).err()).contains("is a directory");
        assertThat(run("analyze", dir.toString()).exit()).isEqualTo(2);
        assertThat(run("analyze").exit()).isEqualTo(2);
        assertThat(run("analyze", "x.yaml", "--bogus").exit()).isEqualTo(2);
        assertThat(run("analyze", "x.yaml", "--fail-on", "fatal").exit()).isEqualTo(2);
    }

    @Test
    void maxAmpBelowOneIsInvalid() throws IOException {
        Result r = run("analyze", spec("clean.yaml", CLEAN), "--max-amp", "0");
        assertThat(r.exit()).isEqualTo(2);
        assertThat(r.err()).contains("--max-amp must be at least 1");
    }
}
