package io.github.hasanyelaldi.plimsoll.cli;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "plimsoll",
        mixinStandardHelpOptions = true,
        versionProvider = Main.Version.class,
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

    /** The project version, filled into {@code version.properties} at build time so it never drifts from the POM. */
    static final class Version implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() throws IOException {
            Properties p = new Properties();
            try (InputStream in = Main.class.getResourceAsStream("version.properties")) {
                if (in == null) {
                    throw new IOException("version.properties is missing from the jar");
                }
                p.load(in);
            }
            return new String[] {"plimsoll " + p.getProperty("version")};
        }
    }
}
