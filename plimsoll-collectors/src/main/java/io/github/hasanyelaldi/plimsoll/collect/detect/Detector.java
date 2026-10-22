package io.github.hasanyelaldi.plimsoll.collect.detect;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.io.YamlTree;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.ListNode;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.MapNode;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Node;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Scalar;
import io.github.hasanyelaldi.plimsoll.model.Detection;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Finds retry, timeout and breaker mechanisms that no collector models, from the patterns in
 * {@code kb/detectors/mechanisms.yaml}: direct non-test dependencies in {@code pom.xml} / {@code build.gradle(.kts)},
 * annotations, types and calls in {@code src/main/java}, and Spring property prefixes.
 *
 * <p>Every match is a {@link Detection} with {@code file:line}; a Java file gives at most one match per pattern
 * (its first occurrence). Types and annotations must resolve through the file's imports, a fully qualified name or
 * a wildcard import of their package. Nothing is resolved across files, so a mechanism behind a wrapper class in
 * another module is missed.
 */
public final class Detector {

    /** One row of the patterns file. */
    public record Mechanism(String mechanism, String effect, Kind kind, String pattern, String source) {

        String describe() {
            return kind.label + " " + pattern;
        }
    }

    public enum Kind {
        DEPENDENCY("dependency"), ANNOTATION("annotation"), TYPE("type"), CALL("call"), CONFIG_KEY("config-key");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        static Kind of(String label, String where) {
            for (Kind k : values()) {
                if (k.label.equals(label)) {
                    return k;
                }
            }
            throw new InvalidSpecException(where + ": unknown kind '" + label + "'");
        }
    }

    static final String RESOURCE = "kb/detectors/mechanisms.yaml";
    private static final List<String> DEPENDENCY_PATH = List.of("project", "dependencies", "dependency");
    private static final Set<String> EFFECTS = Set.of("retry", "timeout", "breaker");
    private static final JavaParser PARSER =
            new JavaParser(new ParserConfiguration().setLanguageLevel(LanguageLevel.JAVA_21));
    /** {@code implementation 'g:a:v'}, {@code implementation("g:a")}, {@code api "g:a"}. */
    private static final Pattern GRADLE_DEPENDENCY =
            Pattern.compile("^\\s*(\\w+)\\s*\\(?\\s*['\"]([^:'\"\\s]+):([^:'\"\\s]+)[:'\"]");

    private final List<Mechanism> mechanisms;

    private Detector(List<Mechanism> mechanisms) {
        this.mechanisms = List.copyOf(mechanisms);
    }

    /** The patterns shipped on the classpath. */
    public static Detector classpath() {
        try (InputStream in = Detector.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " not on the classpath");
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), RESOURCE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Detector parse(String yaml, String fileName) {
        if (!(YamlTree.parse(yaml, fileName) instanceof ListNode list)) {
            throw new InvalidSpecException(fileName + ": must be a list of mechanisms");
        }
        List<Mechanism> mechanisms = new ArrayList<>();
        for (Node n : list.items()) {
            if (!(n instanceof MapNode m)) {
                throw new InvalidSpecException(fileName + ":" + n.line() + ": entry must be a mapping");
            }
            String where = fileName + ":" + m.line();
            String effect = text(m, "effect", where);
            if (!EFFECTS.contains(effect)) {
                throw new InvalidSpecException(where + ": unknown effect '" + effect + "'");
            }
            Kind kind = Kind.of(text(m, "kind", where), where);
            String pattern = text(m, "pattern", where);
            if (kind == Kind.CALL && !pattern.contains("#") || kind == Kind.DEPENDENCY && !pattern.contains(":")) {
                throw new InvalidSpecException(where + ": malformed " + kind.label + " pattern '" + pattern + "'");
            }
            String source = text(m, "source", where);
            if (!source.startsWith("https://")) {
                throw new InvalidSpecException(where + ": source must be an https link");
            }
            mechanisms.add(new Mechanism(text(m, "mechanism", where), effect, kind, pattern, source));
        }
        return new Detector(mechanisms);
    }

    public List<Mechanism> mechanisms() {
        return mechanisms;
    }

    /**
     * Every match in one service module, sorted. File names in evidence are relative to {@code root}.
     *
     * @param props the module's Spring properties, for {@code config-key} patterns
     */
    public List<Detection> detect(Path root, Path module, String service, SpringProperties props) {
        List<Detection> out = new ArrayList<>();
        dependencies(root, module).forEach(dep -> {
            for (Mechanism m : of(Kind.DEPENDENCY)) {
                if (dependencyMatches(m.pattern(), dep.coordinates())) {
                    out.add(detection(service, m, dep.evidence()));
                }
            }
        });
        for (Path file : javaFiles(module.resolve("src/main/java"))) {
            String name = relative(root, file);
            CompilationUnit cu = parseJava(file, name);
            for (Mechanism m : mechanisms) {
                javaMatch(cu, m).ifPresent(line -> out.add(detection(service, m, name + ":" + line)));
            }
        }
        for (Mechanism m : of(Kind.CONFIG_KEY)) {
            props.under(m.pattern().split("\\.")).stream().findFirst()
                    .ifPresent(p -> out.add(detection(service, m, p.evidence())));
        }
        return out.stream().sorted(Detection.ORDER).distinct().toList();
    }

    private List<Mechanism> of(Kind kind) {
        return mechanisms.stream().filter(m -> m.kind() == kind).toList();
    }

    private static Detection detection(String service, Mechanism m, String evidence) {
        return new Detection(service, m.mechanism(), m.effect(), m.describe(), evidence, m.source());
    }

    // --- build files

    record Dependency(String coordinates, String evidence) {}

    static boolean dependencyMatches(String pattern, String coordinates) {
        return pattern.endsWith("*")
                ? coordinates.startsWith(pattern.substring(0, pattern.length() - 1))
                : coordinates.equals(pattern);
    }

    private static List<Dependency> dependencies(Path root, Path module) {
        List<Dependency> deps = new ArrayList<>();
        Path pom = module.resolve("pom.xml");
        if (Files.isRegularFile(pom)) {
            deps.addAll(mavenDependencies(read(pom, relative(root, pom)), relative(root, pom)));
        }
        for (String gradle : List.of("build.gradle", "build.gradle.kts")) {
            Path file = module.resolve(gradle);
            if (Files.isRegularFile(file)) {
                deps.addAll(gradleDependencies(read(file, relative(root, file)), relative(root, file)));
            }
        }
        return deps;
    }

    /** Direct dependencies ({@code project/dependencies/dependency}) without {@code <scope>test</scope>}. */
    static List<Dependency> mavenDependencies(String xml, String fileName) {
        List<Dependency> deps = new ArrayList<>();
        List<String> path = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        String group = null;
        String artifact = null;
        String scope = null;
        int line = 0;
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            XMLStreamReader r = factory.createXMLStreamReader(new StringReader(xml));
            while (r.hasNext()) {
                int event = r.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    path.add(r.getLocalName());
                    text.setLength(0);
                    if (path.equals(DEPENDENCY_PATH)) {
                        group = artifact = scope = null;
                    }
                } else if (event == XMLStreamConstants.CHARACTERS) {
                    text.append(r.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (path.equals(DEPENDENCY_PATH)) {
                        if (group != null && artifact != null && !"test".equals(scope)) {
                            deps.add(new Dependency(group + ":" + artifact, fileName + ":" + line));
                        }
                    } else if (path.size() == DEPENDENCY_PATH.size() + 1
                            && path.subList(0, DEPENDENCY_PATH.size()).equals(DEPENDENCY_PATH)) {
                        switch (r.getLocalName()) {
                            case "groupId" -> group = text.toString().trim();
                            case "artifactId" -> {
                                artifact = text.toString().trim();
                                line = r.getLocation().getLineNumber();
                            }
                            case "scope" -> scope = text.toString().trim();
                            default -> { }
                        }
                    }
                    path.remove(path.size() - 1);
                }
            }
        } catch (XMLStreamException e) {
            throw new InvalidSpecException(fileName + ": cannot parse: " + e.getMessage(), e);
        }
        return deps;
    }

    /** {@code configuration 'group:artifact...'} lines whose configuration does not start with {@code test}. */
    static List<Dependency> gradleDependencies(String text, String fileName) {
        List<Dependency> deps = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = GRADLE_DEPENDENCY.matcher(lines[i]);
            if (m.find() && !m.group(1).startsWith("test")) {
                deps.add(new Dependency(m.group(2) + ":" + m.group(3), fileName + ":" + (i + 1)));
            }
        }
        return deps;
    }

    // --- Java sources

    private static List<Path> javaFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new InvalidSpecException(dir + ": cannot list sources: " + e.getMessage(), e);
        }
    }

    private static CompilationUnit parseJava(Path file, String name) {
        ParseResult<CompilationUnit> result = PARSER.parse(read(file, name));
        return result.getResult().filter(r -> result.isSuccessful()).orElseThrow(() ->
                new InvalidSpecException(name + ": cannot parse Java: " + result.getProblems().get(0).getMessage()));
    }

    /** The line of the first occurrence of {@code m} in {@code cu}, if any. */
    static Optional<Integer> javaMatch(CompilationUnit cu, Mechanism m) {
        return switch (m.kind()) {
            case ANNOTATION -> first(cu.findAll(AnnotationExpr.class).stream()
                    .filter(a -> refersTo(cu, a.getNameAsString(), m.pattern())));
            case TYPE -> first(cu.findAll(ClassOrInterfaceType.class).stream()
                    .filter(t -> refersTo(cu, t.getNameWithScope(), m.pattern())));
            case CALL -> {
                String prefix = m.pattern().substring(0, m.pattern().indexOf('#'));
                String method = m.pattern().substring(m.pattern().indexOf('#') + 1);
                boolean imported = cu.getImports().stream().map(ImportDeclaration::getNameAsString)
                        .anyMatch(i -> i.equals(prefix) || i.startsWith(prefix + "."));
                if (!imported) {
                    yield Optional.empty();
                }
                yield first(cu.findAll(MethodCallExpr.class).stream().filter(c -> c.getNameAsString().equals(method)));
            }
            default -> Optional.empty();
        };
    }

    /** Whether {@code name} as written in {@code cu} is the type {@code fqn}. */
    private static boolean refersTo(CompilationUnit cu, String name, String fqn) {
        if (name.equals(fqn)) {
            return true;
        }
        String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
        String pkg = fqn.substring(0, fqn.lastIndexOf('.'));
        if (!name.equals(simple)) {
            return false;
        }
        return cu.getImports().stream().filter(i -> !i.isStatic()).anyMatch(i -> i.isAsterisk()
                ? i.getNameAsString().equals(pkg)
                : i.getNameAsString().equals(fqn));
    }

    private static Optional<Integer> first(Stream<? extends com.github.javaparser.ast.Node> nodes) {
        return nodes.map(n -> n.getBegin().map(p -> p.line).orElse(0)).min(Integer::compare);
    }

    // --- helpers

    private static String read(Path file, String name) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new InvalidSpecException(name + ": cannot read: " + e.getMessage(), e);
        }
    }

    private static String relative(Path root, Path p) {
        return root.relativize(p).toString().replace('\\', '/');
    }

    private static String text(MapNode m, String key, String where) {
        YamlTree.Entry e = m.entries().get(key);
        if (e == null || !(e.value() instanceof Scalar s) || s.text() == null || s.text().isBlank()) {
            throw new InvalidSpecException(where + ": entry needs a scalar '" + key + "'");
        }
        return s.text();
    }
}
