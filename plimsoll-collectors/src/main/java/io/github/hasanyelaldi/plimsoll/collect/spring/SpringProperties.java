package io.github.hasanyelaldi.plimsoll.collect.spring;

import com.fasterxml.jackson.core.JsonToken;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.io.YamlTree;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.ListNode;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.MapNode;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Node;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Scalar;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Spring Boot properties read from {@code application.yml} files, flattened to property paths with the line each
 * value is on. Nested keys and dotted keys ({@code a.b.c: 1}) give the same path.
 *
 * <p>Relaxed binding: property names match regardless of case, {@code -} and {@code _} ({@code max-attempts},
 * {@code maxAttempts}, {@code max_attempts}). Map keys such as instance names are compared exactly.
 *
 * <p>A list is available both as one property ({@link Property#items()}, when every item is a scalar) and item by
 * item under its index, {@code routes[0].id}, as Spring Boot binds it; see {@link #indices(String...)}.
 *
 * <p>Only the default profile is read. Documents activated by {@code spring.config.activate.*} are skipped, never
 * merged silently. They are listed in {@link #unread()}, together with profile activations, config imports, other
 * config files of the module, and placeholders in the properties the collectors read.
 */
public final class SpringProperties {

    /**
     * One property. {@code items} is non-null for a YAML list, {@code text} otherwise.
     *
     * @param evidence {@code file:line} of the key
     */
    public record Property(List<String> path, String text, List<String> items, String evidence) {

        public Property {
            path = List.copyOf(path);
            items = items == null ? null : List.copyOf(items);
        }

        /** The value depends on the environment ({@code ${...}}), so it is not known from the file alone. */
        public boolean isPlaceholder() {
            return text != null ? text.contains("${") : items.stream().anyMatch(i -> i.contains("${"));
        }

        /** A list, or a comma-separated scalar, as Spring binds it to an array. */
        public List<String> values() {
            if (items != null) {
                return items;
            }
            return Arrays.stream(text.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        }
    }

    /**
     * Configuration that was not read.
     *
     * @param kind {@code config-server}, {@code config-import}, {@code config-file}, {@code profile-document},
     *             {@code profile} or {@code placeholder}
     * @param evidence {@code file:line}, or the file for a whole file
     */
    public record Unread(String kind, String reference, String evidence) {}

    /** Property prefixes whose {@code ${...}} values are reported: the ones the collectors read. */
    static final List<List<String>> COLLECTED_PREFIXES = List.of(
            List.of("resilience4j"),
            List.of("spring", "cloud", "gateway"),
            List.of("spring", "cloud", "loadbalancer"));

    /** Under {@code src/main/resources}. Spring Boot reads {@code config/} after the root, so it overrides it. */
    public static final List<String> SERVICE_CONFIG_FILES = List.of(
            "application.yml", "application.yaml", "config/application.yml", "config/application.yaml");

    private final List<String> files;
    private final List<Property> properties;
    private final List<Unread> unread;

    private SpringProperties(List<String> files, List<Property> properties, List<Unread> unread) {
        this.files = List.copyOf(files);
        this.properties = List.copyOf(properties);
        this.unread = List.copyOf(unread);
    }

    /** The {@link #SERVICE_CONFIG_FILES} of a service module that exist; evidence paths relative to the module. */
    public static SpringProperties forService(Path serviceRoot) {
        return forService(serviceRoot, serviceRoot);
    }

    /** As {@link #forService(Path)}, with evidence paths relative to {@code base}. */
    public static SpringProperties forService(Path base, Path serviceRoot) {
        Path resources = serviceRoot.resolve("src/main/resources");
        SpringProperties props = load(base, SERVICE_CONFIG_FILES.stream().map(resources::resolve)
                .filter(Files::isRegularFile).toList());
        List<Unread> unread = new ArrayList<>(props.unread);
        for (Path dir : List.of(resources, resources.resolve("config"))) {
            otherConfigFiles(base, resources, dir).forEach(f -> unread.add(new Unread("config-file",
                    f.substring(f.lastIndexOf('/') + 1), f)));
        }
        return new SpringProperties(props.files, props.properties, unread);
    }

    /** {@code application*} and {@code bootstrap*} files in {@code dir} other than {@link #SERVICE_CONFIG_FILES}. */
    private static List<String> otherConfigFiles(Path base, Path resources, Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> list = Files.list(dir)) {
            return list.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("(application|bootstrap).*\\.(properties|ya?ml)"))
                    .filter(p -> !SERVICE_CONFIG_FILES.contains(resources.relativize(p).toString().replace('\\', '/')))
                    .map(p -> base.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new InvalidSpecException(dir + ": cannot list: " + e.getMessage(), e);
        }
    }

    /**
     * Reads {@code files} in order; a later file overrides an earlier one. Evidence paths are relative to
     * {@code root}.
     */
    public static SpringProperties load(Path root, List<Path> files) {
        List<String> names = new ArrayList<>();
        List<Property> props = new ArrayList<>();
        List<Unread> unread = new ArrayList<>();
        for (Path file : files) {
            String name = root.relativize(file).toString().replace('\\', '/');
            names.add(name);
            String text;
            try {
                text = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new InvalidSpecException(name + ": cannot read: " + e.getMessage(), e);
            }
            read(text, name, props, unread);
        }
        return new SpringProperties(names, props, unread);
    }

    /** @param fileName used in {@code file:line} evidence */
    public static SpringProperties parse(String yaml, String fileName) {
        List<Property> props = new ArrayList<>();
        List<Unread> unread = new ArrayList<>();
        read(yaml, fileName, props, unread);
        return new SpringProperties(List.of(fileName), props, unread);
    }

    private static void read(String yaml, String fileName, List<Property> out, List<Unread> unread) {
        for (Node doc : YamlTree.parseAll(yaml, fileName)) {
            List<Property> docProps = new ArrayList<>();
            if (!(doc instanceof MapNode m)) {
                throw new InvalidSpecException(fileName + ":" + doc.line() + ": document must be a mapping");
            }
            flatten(m, new ArrayList<>(), fileName, docProps);
            Optional<Property> activation = docProps.stream()
                    .filter(p -> startsWith(p.path(), "spring", "config", "activate"))
                    .findFirst();
            if (activation.isPresent()) {
                unread.add(new Unread("profile-document", assignment(activation.get()), fileName + ":" + doc.line()));
                continue;
            }
            for (Property p : docProps) {
                if (p.path().getLast().startsWith("[")) {
                    continue;  // a scalar list item; the whole list is a property of its own
                }
                if (startsWith(p.path(), "spring", "profiles", "active")
                        || startsWith(p.path(), "spring", "profiles", "include")) {
                    unread.add(new Unread("profile", assignment(p), p.evidence()));
                } else if (startsWith(p.path(), "spring", "config", "import")) {
                    for (String location : p.values()) {
                        boolean server = location.replaceFirst("^optional:", "").startsWith("configserver:");
                        unread.add(new Unread(server ? "config-server" : "config-import", location, p.evidence()));
                    }
                } else if (p.isPlaceholder() && COLLECTED_PREFIXES.stream()
                        .anyMatch(prefix -> startsWith(p.path(), prefix.toArray(String[]::new)))) {
                    unread.add(new Unread("placeholder", assignment(p), p.evidence()));
                }
            }
            out.addAll(docProps);
        }
    }

    private static void flatten(MapNode m, List<String> prefix, String file, List<Property> out) {
        for (var e : m.entries().entrySet()) {
            List<String> path = new ArrayList<>(prefix);
            path.addAll(segments(e.getKey()));
            String evidence = file + ":" + e.getValue().keyLine();
            switch (e.getValue().value()) {
                case MapNode child -> flatten(child, path, file, out);
                case ListNode list -> {
                    indexed(list, path, file, out);
                    List<String> items = new ArrayList<>();
                    for (Node item : list.items()) {
                        if (!(item instanceof Scalar s) || s.token() == JsonToken.VALUE_NULL) {
                            items = null;
                            break;
                        }
                        items.add(s.text());
                    }
                    if (items != null) {
                        out.add(new Property(path, null, items, evidence));
                    }
                }
                case Scalar s -> {
                    if (s.token() != JsonToken.VALUE_NULL) {
                        out.add(new Property(path, s.text(), null, evidence));
                    }
                }
            }
        }
    }

    /** Each list item under its index, as Spring Boot binds {@code routes[0].id}: {@code path + "[0]" + ...}. */
    private static void indexed(ListNode list, List<String> path, String file, List<Property> out) {
        for (int i = 0; i < list.items().size(); i++) {
            Node item = list.items().get(i);
            List<String> itemPath = new ArrayList<>(path);
            itemPath.add("[" + i + "]");
            switch (item) {
                case MapNode child -> flatten(child, itemPath, file, out);
                case ListNode nested -> indexed(nested, itemPath, file, out);
                case Scalar s -> {
                    if (s.token() != JsonToken.VALUE_NULL) {
                        out.add(new Property(itemPath, s.text(), null, file + ":" + s.line()));
                    }
                }
            }
        }
    }

    private static List<String> segments(String key) {
        if (key.startsWith("[") && key.endsWith("]")) {
            return List.of(key.substring(1, key.length() - 1));
        }
        return Arrays.asList(key.split("\\."));
    }

    /** Relaxed-binding form of a property name segment. */
    static String canonical(String segment) {
        return segment.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(List<String> path, String... prefix) {
        if (path.size() < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (!canonical(path.get(i)).equals(canonical(prefix[i]))) {
                return false;
            }
        }
        return true;
    }

    /** The files read, in reading order, as named in evidence. */
    public List<String> files() {
        return files;
    }

    /** The effective property at {@code path}, every segment matched with relaxed binding. */
    public Optional<Property> get(String... path) {
        return last(p -> p.size() == path.length && startsWith(p, path));
    }

    /**
     * The effective property {@code prefix.<name>.field}: {@code prefix} and {@code field} with relaxed binding,
     * {@code name} (a map key such as an instance name) exactly.
     */
    public Optional<Property> get(List<String> prefix, String name, String field) {
        int n = prefix.size();
        return last(p -> p.size() == n + 2 && startsWith(p, prefix.toArray(String[]::new))
                && p.get(n).equals(name) && canonical(p.get(n + 1)).equals(canonical(field)));
    }

    /** Map keys directly under {@code prefix}, sorted. */
    public SortedSet<String> names(String... prefix) {
        SortedSet<String> names = new TreeSet<>();
        for (Property p : properties) {
            if (p.path().size() > prefix.length && startsWith(p.path(), prefix)) {
                names.add(p.path().get(prefix.length));
            }
        }
        return names;
    }

    /** The effective properties below {@code prefix}, matched with relaxed binding, in the order last set. */
    public List<Property> under(String... prefix) {
        Map<List<String>, Property> effective = new LinkedHashMap<>();
        for (Property p : properties) {
            if (p.path().size() > prefix.length && startsWith(p.path(), prefix)) {
                List<String> key = p.path().stream().map(SpringProperties::canonical).toList();
                effective.remove(key);
                effective.put(key, p);
            }
        }
        return List.copyOf(effective.values());
    }

    /** The list indices directly under {@code prefix}, in ascending order. */
    public List<Integer> indices(String... prefix) {
        return names(prefix).stream()
                .filter(n -> n.matches("\\[\\d+]"))
                .map(n -> Integer.valueOf(n.substring(1, n.length() - 1)))
                .sorted()
                .toList();
    }

    /** Configuration that was not read, in file order. */
    public List<Unread> unread() {
        return unread;
    }

    /** {@code a.b.c = value} as written. */
    private static String assignment(Property p) {
        return String.join(".", p.path()) + " = " + (p.text() != null ? p.text() : String.join(",", p.items()));
    }

    private Optional<Property> last(Predicate<List<String>> match) {
        Property found = null;
        for (Property p : properties) {
            if (match.test(p.path())) {
                found = p;
            }
        }
        return Optional.ofNullable(found);
    }
}
