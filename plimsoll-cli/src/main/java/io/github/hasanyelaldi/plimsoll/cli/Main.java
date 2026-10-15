package io.github.hasanyelaldi.plimsoll.cli;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "plimsoll",
        mixinStandardHelpOptions = true,
        version = "plimsoll 0.1.0-SNAPSHOT",
        description = "Finds where retry, timeout and circuit-breaker settings across a service graph go past safe limits.",
        subcommands = AnalyzeCommand.class)
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        // Fixed UTF-8 so reports are byte-identical whatever the platform locale is.
        PrintWriter out = new PrintWriter(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
        PrintWriter err = new PrintWriter(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        int exit = run(args, out, err);
        out.flush();
        System.exit(exit);
    }

    /** Runs the CLI against the given streams; returns the exit code. Picocli usage errors give 2. */
    public static int run(String[] args, PrintWriter out, PrintWriter err) {
        CommandLine cmd = new CommandLine(new Main());
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        cmd.setOut(out);
        cmd.setErr(err);
        return cmd.execute(args);
    }
}
