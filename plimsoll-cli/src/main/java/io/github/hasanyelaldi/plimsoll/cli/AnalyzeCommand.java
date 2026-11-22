package io.github.hasanyelaldi.plimsoll.cli;

import io.github.hasanyelaldi.plimsoll.analysis.AnalysisOptions;
import io.github.hasanyelaldi.plimsoll.analysis.AnalysisResult;
import io.github.hasanyelaldi.plimsoll.analysis.Analyzer;
import io.github.hasanyelaldi.plimsoll.analysis.Severity;
import io.github.hasanyelaldi.plimsoll.collect.ProjectCollector;
import io.github.hasanyelaldi.plimsoll.collect.detect.Detector;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.io.SpecLoader;
import io.github.hasanyelaldi.plimsoll.model.ConfigDifference;
import io.github.hasanyelaldi.plimsoll.model.Detection;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import io.github.hasanyelaldi.plimsoll.report.Collected;
import io.github.hasanyelaldi.plimsoll.report.ReportFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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

    @Option(names = "--effective", paramLabel = "<service>=<file>",
            description = "A dump of what a running service uses: the JSON of its /actuator/configprops, or its "
                    + "sidecar's Envoy config_dump; repeatable, at most one of each per service, directories only. "
                    + "Its values replace the declared ones; each difference is a PLM-C2 finding (ADR 0016).")
    List<String> effective = new ArrayList<>();

    @Option(names = "--format", paramLabel = "<format>", defaultValue = "TEXT",
            description = "Report format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    ReportFormat format;

    @Override
    public Integer call() {
        if (maxAmplification < 1) {
            spec.commandLine().getErr().println("error: --max-amp must be at least 1");
            return INVALID_INPUT;
        }
        Map<String, List<Path>> dumps = new TreeMap<>();
        for (String e : effective) {
            int eq = e.indexOf('=');
            if (eq <= 0 || eq == e.length() - 1) {
                spec.commandLine().getErr().println("error: --effective expects <service>=<file>, got " + e);
                return INVALID_INPUT;
            }
            dumps.computeIfAbsent(e.substring(0, eq), k -> new ArrayList<>()).add(Path.of(e.substring(eq + 1)));
        }
        if (!dumps.isEmpty() && !Files.isDirectory(input)) {
            spec.commandLine().getErr().println("error: --effective needs a directory to collect, not a spec");
            return INVALID_INPUT;
        }
        ServiceGraph graph;
        Collected collected = Collected.SPEC;
        List<Detection> detections = List.of();
        List<ConfigDifference> differences = List.of();
        try {
            if (Files.isDirectory(input)) {
                ProjectCollector.Result project = ProjectCollector.collect(input, DefaultsKb.classpath(),
                        Detector.classpath(), dumps);
                graph = project.graph();
                collected = new Collected(project.coverage(), project.notes());
                detections = project.detections();
                differences = project.differences();
            } else {
                graph = SpecLoader.load(input);
            }
        } catch (InvalidSpecException e) {
            spec.commandLine().getErr().println("error: " + e.getMessage());
            return INVALID_INPUT;
        }
        AnalysisOptions options = new AnalysisOptions(maxAmplification);
        AnalysisResult result = Analyzer.analyze(graph, options, detections, differences);
        spec.commandLine().getOut().print(format.render(result, name(input), options, collected));
        spec.commandLine().getOut().flush();
        return result.hasFindingAtLeast(failOn) ? FINDINGS : OK;
    }

    private static String name(Path input) {
        Path name = input.toAbsolutePath().normalize().getFileName();
        return name == null ? input.toString() : name.toString();
    }
}
