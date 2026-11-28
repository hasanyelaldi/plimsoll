package io.github.hasanyelaldi.plimsoll.collect.springai;

import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * The parts of a {@code pom.xml} the Spring AI collector needs, with line numbers: properties, direct dependencies
 * and dependency-management entries. Properties resolve through this POM and its parent at the default relative
 * path ({@code ../pom.xml}); nothing is downloaded.
 */
record MavenPom(String file, Map<String, Value> properties, List<Dependency> dependencies,
        List<Dependency> managed, MavenPom parent) {

    record Value(String text, String evidence) {}

    /** @param version null when the POM gives none */
    record Dependency(String groupId, String artifactId, String version, String scope, String evidence) {}

    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    /** @return empty when the module has no {@code pom.xml} */
    static Optional<MavenPom> read(Path root, Path module) {
        Path pom = module.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            return Optional.empty();
        }
        Path parentPom = module.getParent() == null ? null : module.getParent().resolve("pom.xml");
        MavenPom parent = parentPom != null && parentPom.startsWith(root) && Files.isRegularFile(parentPom)
                ? parse(root, parentPom, null) : null;
        return Optional.of(parse(root, pom, parent));
    }

    /** The value of {@code text} with {@code ${...}} replaced, and the evidence of each property used. */
    Optional<Value> resolve(String text, String evidence) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = PROPERTY.matcher(text);
        StringBuilder out = new StringBuilder();
        List<String> used = new ArrayList<>(List.of(evidence));
        while (m.find()) {
            Optional<Value> v = property(m.group(1));
            if (v.isEmpty()) {
                return Optional.empty();
            }
            m.appendReplacement(out, Matcher.quoteReplacement(v.get().text()));
            used.add(v.get().evidence() + " (" + m.group(1) + ")");
        }
        m.appendTail(out);
        return Optional.of(new Value(out.toString(), String.join(", ", used)));
    }

    private Optional<Value> property(String name) {
        Value v = properties.get(name);
        if (v != null) {
            return Optional.of(v);
        }
        return parent == null ? Optional.empty() : parent.property(name);
    }

    /** The dependency-management entries of this POM, then of its parent. */
    List<MavenPom> lineage() {
        List<MavenPom> out = new ArrayList<>();
        for (MavenPom p = this; p != null; p = p.parent()) {
            out.add(p);
        }
        return out;
    }

    private static MavenPom parse(Path root, Path pom, MavenPom parent) {
        String file = root.relativize(pom).toString().replace('\\', '/');
        Map<String, Value> properties = new LinkedHashMap<>();
        List<Dependency> dependencies = new ArrayList<>();
        List<Dependency> managed = new ArrayList<>();
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try (Reader in = Files.newBufferedReader(pom, StandardCharsets.UTF_8)) {
            XMLStreamReader x = factory.createXMLStreamReader(in);
            Deque<String> path = new ArrayDeque<>();
            Map<String, String> fields = null;
            int dependencyLine = 0;
            while (x.hasNext()) {
                int event = x.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    path.addLast(x.getLocalName());
                    String where = String.join("/", path);
                    if (where.equals("project/dependencies/dependency")
                            || where.equals("project/dependencyManagement/dependencies/dependency")) {
                        fields = new LinkedHashMap<>();
                        dependencyLine = x.getLocation().getLineNumber();
                    } else if (path.size() == 3 && where.startsWith("project/properties/")) {
                        int line = x.getLocation().getLineNumber();
                        properties.put(x.getLocalName(), new Value(x.getElementText().strip(), file + ":" + line));
                        path.removeLast();
                    } else if (fields != null && (where.endsWith("dependency/groupId")
                            || where.endsWith("dependency/artifactId") || where.endsWith("dependency/version")
                            || where.endsWith("dependency/scope"))) {
                        fields.put(x.getLocalName(), x.getElementText().strip());
                        path.removeLast();
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String where = String.join("/", path);
                    if (fields != null && where.endsWith("dependencies/dependency")) {
                        Dependency d = new Dependency(fields.get("groupId"), fields.get("artifactId"),
                                fields.get("version"), fields.get("scope"), file + ":" + dependencyLine);
                        (where.startsWith("project/dependencyManagement") ? managed : dependencies).add(d);
                        fields = null;
                    }
                    path.removeLast();
                }
            }
        } catch (IOException | XMLStreamException e) {
            throw new InvalidSpecException(file + ": cannot read pom.xml: " + e.getMessage(), e);
        }
        return new MavenPom(file, properties, dependencies, managed, parent);
    }
}
