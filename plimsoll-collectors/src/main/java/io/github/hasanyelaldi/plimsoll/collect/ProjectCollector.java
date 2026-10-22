package io.github.hasanyelaldi.plimsoll.collect;

import io.github.hasanyelaldi.plimsoll.collect.detect.Detector;
import io.github.hasanyelaldi.plimsoll.collect.gateway.GatewayCollector;
import io.github.hasanyelaldi.plimsoll.collect.http.HttpClientCollector;
import io.github.hasanyelaldi.plimsoll.collect.http.HttpClientCollector.EdgeKey;
import io.github.hasanyelaldi.plimsoll.collect.istio.IstioCollector;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.EdgesHint;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.Resilience4jCollector;
import io.github.hasanyelaldi.plimsoll.collect.spring.RequestMappingScanner;
import io.github.hasanyelaldi.plimsoll.collect.spring.RequestMappingScanner.Mapping;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties.Property;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Detection;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Collects the service graph of a directory: finds its service modules and Istio manifests, runs every collector
 * on them and assembles edges ({@link GraphAssembler}) and services ({@link ServiceAssembler}).
 *
 * <ul>
 *   <li>A <b>service module</b> is a directory with an {@code edges-hint.yaml}, a {@code src/main/java} directory or
 *       one of the {@link SpringProperties#SERVICE_CONFIG_FILES}. Its name is the hint's {@code service}, else
 *       {@code spring.application.name}, else the directory name. A hint and an application name that disagree, or
 *       two modules with one name, are invalid input.
 *   <li>Every module's request mappings are scanned, so that gateway routes reach the operations of their callees.
 *       A module with a hint gets the Resilience4j and HTTP client collectors; a module that configures gateway
 *       routes or default filters gets the Spring Cloud Gateway collector.
 *   <li>An <b>Istio manifest</b> is a {@code .yaml} / {@code .yml} file outside the modules' {@code src} directories
 *       that mentions {@code networking.istio.io/}. Other YAML (Helm templates, Compose files) is not parsed. Without
 *       such a file the graph has no mesh.
 * </ul>
 *
 * <p>Every module is also searched for mechanisms no collector models ({@link Detector}); they become PLM-C1.
 *
 * <p>File names in evidence and notes are relative to the directory; a module's notes start with its service name.
 * Hidden directories, {@code target}, {@code build} and {@code node_modules} are skipped. Modules and manifests
 * are visited in path order and the notes are sorted, so the same directory always gives the same result.
 */
public final class ProjectCollector {

    public static final String EDGES_HINT = "edges-hint.yaml";
    private static final Set<String> SKIPPED = Set.of("target", "build", "node_modules");
    private static final String ISTIO_API = "networking.istio.io/";

    /**
     * @param notes every collector's and assembler's notes; sorted, without repeats
     * @param detections mechanisms found in the modules that no collector models; sorted, without repeats
     */
    public record Result(ServiceGraph graph, List<String> notes, List<Detection> detections) {
        public Result {
            notes = List.copyOf(notes);
            detections = List.copyOf(detections);
        }
    }

    private record Module(Path root, String path, String name, SpringProperties props, EdgesHint hint) {}

    private ProjectCollector() {}

    public static Result collect(Path dir) {
        return collect(dir, DefaultsKb.classpath(), Detector.classpath());
    }

    public static Result collect(Path dir, DefaultsKb kb, Detector detector) {
        Path root = dir.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new InvalidSpecException(dir + ": not a directory");
        }
        List<Path> moduleRoots = new ArrayList<>();
        List<Path> manifests = new ArrayList<>();
        walk(root, moduleRoots, manifests);
        if (moduleRoots.isEmpty()) {
            throw new InvalidSpecException(dir + ": no service module found (a directory with " + EDGES_HINT
                    + ", src/main/java or src/main/resources/application.yml)");
        }

        List<String> notes = new ArrayList<>();
        Map<String, Module> modules = new TreeMap<>();
        for (Path p : moduleRoots) {
            Module m = module(root, p);
            Module other = modules.putIfAbsent(m.name(), m);
            if (other != null) {
                throw new InvalidSpecException("service " + m.name() + " is defined by two modules: " + other.path()
                        + " and " + m.path());
            }
        }

        Map<String, List<Mapping>> served = new TreeMap<>();
        for (Module m : modules.values()) {
            RequestMappingScanner.Result scanned = RequestMappingScanner.scan(root, m.root());
            served.put(m.name(), scanned.mappings());
            scanned.notes().forEach(n -> notes.add(m.name() + ": " + n));
        }

        List<Detection> detections = new ArrayList<>();
        for (Module m : modules.values()) {
            detections.addAll(detector.detect(root, m.root(), m.name(), m.props()));
        }

        List<Edge> edges = new ArrayList<>();
        Map<EdgeKey, Layer> httpLayers = new LinkedHashMap<>();
        List<EdgesHint> hints = new ArrayList<>();
        List<GatewayCollector.Result> gateways = new ArrayList<>();
        for (Module m : sortedByPath(modules)) {
            if (m.hint() != null) {
                hints.add(m.hint());
                Resilience4jCollector.Result r4j = Resilience4jCollector.collect(root, m.root(), m.hint(), kb);
                edges.addAll(r4j.edges());
                r4j.notes().forEach(n -> notes.add(m.name() + ": " + n));
                r4j.unmapped().forEach(u -> notes.add(m.name() + ": " + u + " is annotated but no "
                        + EDGES_HINT + " entry names it; not analysed"));
                HttpClientCollector.Result http = HttpClientCollector.collect(m.props(), m.hint(), kb);
                httpLayers.putAll(http.layers());
                http.notes().forEach(n -> notes.add(m.name() + ": " + n));
            } else {
                m.props().notes().forEach(n -> notes.add(m.name() + ": " + n));
            }
            if (GatewayCollector.configuresGateway(m.props())) {
                GatewayCollector.Result gateway = GatewayCollector.collect(m.props(), m.name(), served, m.hint(), kb);
                gateways.add(gateway);
                edges.addAll(gateway.edges());
                gateway.notes().forEach(n -> notes.add(m.name() + ": " + n));
            }
        }

        IstioCollector.Result istio = null;
        if (!manifests.isEmpty()) {
            istio = IstioCollector.collect(root, manifests, kb);
            notes.addAll(istio.notes());
        }
        GraphAssembler.Result assembled =
                GraphAssembler.assemble(edges, new HttpClientCollector.Result(httpLayers, List.of()), istio);
        notes.addAll(assembled.notes());
        ServiceAssembler.Result services = ServiceAssembler.assemble(assembled.edges(), hints, gateways);
        notes.addAll(services.notes());

        return new Result(services.graph(), notes.stream().sorted().distinct().toList(),
                detections.stream().sorted(Detection.ORDER).distinct().toList());
    }

    private static List<Module> sortedByPath(Map<String, Module> modules) {
        return modules.values().stream().sorted((a, b) -> a.path().compareTo(b.path())).toList();
    }

    private static Module module(Path root, Path dir) {
        String path = root.equals(dir) ? "." : relative(root, dir);
        SpringProperties props = SpringProperties.forService(root, dir);
        Path hintFile = dir.resolve(EDGES_HINT);
        String hintName = relative(root, hintFile);
        EdgesHint hint = Files.isRegularFile(hintFile) ? EdgesHint.load(hintFile, hintName) : null;

        Optional<Property> appName = props.get("spring", "application", "name")
                .filter(p -> !p.isPlaceholder() && p.text() != null);
        String name;
        if (hint != null) {
            name = hint.service();
            if (appName.isPresent() && !appName.get().text().equals(name)) {
                throw new InvalidSpecException(hintName + ": service " + name + " but spring.application.name is "
                        + appName.get().text() + " (" + appName.get().evidence() + ")");
            }
        } else {
            name = appName.map(Property::text).orElse(dir.getFileName().toString());
        }
        return new Module(dir, path, name, props, hint);
    }

    private static String relative(Path root, Path p) {
        return root.relativize(p).toString().replace('\\', '/');
    }

    private static boolean isModule(Path dir) {
        Path resources = dir.resolve("src/main/resources");
        return Files.isRegularFile(dir.resolve(EDGES_HINT))
                || Files.isDirectory(dir.resolve("src/main/java"))
                || SpringProperties.SERVICE_CONFIG_FILES.stream()
                        .anyMatch(f -> Files.isRegularFile(resources.resolve(f)));
    }

    private static void walk(Path root, List<Path> modules, List<Path> manifests) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (!dir.equals(root) && (name.startsWith(".") || SKIPPED.contains(name))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (name.equals("src") && modules.contains(dir.getParent())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (isModule(dir)) {
                        modules.add(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String name = file.getFileName().toString();
                    if (attrs.isRegularFile() && name.matches(".*\\.ya?ml") && !name.equals(EDGES_HINT)
                            && Files.readString(file, StandardCharsets.UTF_8).contains(ISTIO_API)) {
                        manifests.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new InvalidSpecException(root + ": cannot list: " + e.getMessage(), e);
        }
        modules.sort(null);
        manifests.sort(null);
    }
}
