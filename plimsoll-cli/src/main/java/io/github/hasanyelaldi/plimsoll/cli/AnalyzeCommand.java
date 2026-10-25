package io.github.hasanyelaldi.plimsoll.cli;

import io.github.hasanyelaldi.plimsoll.analysis.AnalysisOptions;
import io.github.hasanyelaldi.plimsoll.analysis.AnalysisResult;
import io.github.hasanyelaldi.plimsoll.analysis.Analyzer;
import io.github.hasanyelaldi.plimsoll.analysis.Severity;
import io.github.hasanyelaldi.plimsoll.collect.ProjectCollector;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.io.SpecLoader;
import io.github.hasanyelaldi.plimsoll.model.Detection;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import io.github.hasanyelaldi.plimsoll.report.Collected;
import io.github.hasanyelaldi.plimsoll.report.Coverage;
import io.github.hasanyelaldi.plimsoll.report.ReportFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * {@code plimsoll analyze <spec-or-dir>}: a YAML spec file, or a directory whose configuration is collected
 * ({@link ProjectCollector}). Exit codes: 0 nothing at or above --fail-on, 1 otherwise, 2 invalid input.
 */
@Command(
        name = "analyze",
        mixinStandardHelpOptions = true,
        description = "Find retry, timeout and amplification problems in a service graph spec, "
                + "or in the configuration collected from a directory of services.")
final class AnalyzeCommand implements Callable<Integer> {

    static final int OK = 0;
    static final int FINDINGS = 1;
    static final int INVALID_INPUT = 2;

    @Spec
    CommandSpec spec;

    @Parameters(index = "0", paramLabel = "<spec-or-dir>",
            description = "YAML spec file, or a directory of service modules (edges-hint.yaml, Spring config, "
                    + "Istio manifests) to collect the graph from.")
    Path input;

    @Option(names = "--max-amp", paramLabel = "<n>", defaultValue = "10",
            description = "A1 amplification limit; a service above it is an ERROR (default: ${DEFAULT-VALUE}).")
    int maxAmplification;

    @Option(names = "--fail-on", paramLabel = "<severity>", defaultValue = "ERROR",
            description = "Exit with 1 if a finding is at least this severe: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    Severity failOn;

    @Option(names = "--format", paramLabel = "<format>", defaultValue = "TEXT",
            description = "Report format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    ReportFormat format;

    @Override
    public Integer call() {
        if (maxAmplification < 1) {
            spec.commandLine().getErr().println("error: --max-amp must be at least 1");
            return INVALID_INPUT;
        }
        ServiceGraph graph;
        Collected collected = Collected.SPEC;
        List<Detection> detections = List.of();
        try {
            if (Files.isDirectory(input)) {
                ProjectCollector.Result project = ProjectCollector.collect(input);
                graph = project.graph();
                collected = new Collected(new Coverage("collected directory", List.of(), List.of(), List.of(),
                        project.unreadSources()), project.notes());
                detections = project.detections();
            } else {
                graph = SpecLoader.load(input);
            }
        } catch (InvalidSpecException e) {
            spec.commandLine().getErr().println("error: " + e.getMessage());
            return INVALID_INPUT;
        }
        AnalysisOptions options = new AnalysisOptions(maxAmplification);
        AnalysisResult result = Analyzer.analyze(graph, options, detections);
        spec.commandLine().getOut().print(format.render(result, name(input), options, collected));
        spec.commandLine().getOut().flush();
        return result.hasFindingAtLeast(failOn) ? FINDINGS : OK;
    }

    private static String name(Path input) {
        Path name = input.toAbsolutePath().normalize().getFileName();
        return name == null ? input.toString() : name.toString();
    }
}
