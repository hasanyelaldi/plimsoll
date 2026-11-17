package io.github.hasanyelaldi.plimsoll.collect.kb;

import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.io.YamlTree;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.ListNode;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.MapNode;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Node;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Scalar;
import io.github.hasanyelaldi.plimsoll.model.FailureClass;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * The library defaults knowledge base, {@code kb/defaults/<library>.yaml}.
 *
 * <p>Only entries that name a {@code source} and were checked ({@code verified_by} doc, source or oracle) are
 * returned. A seed entry with {@code verified_by: none} is treated as absent, so the value stays UNKNOWN.
 */
public final class DefaultsKb {

    private static final Set<String> VERIFIED = Set.of("doc", "source", "oracle");

    /** One knowledge base row. {@code value} is the raw scalar text. */
    public record Entry(String library, String versions, String parameter, String value, String unit,
            String source, String verifiedBy) {

        /** e.g. {@code resilience4j-retry 2.4.0 default (verified by oracle), https://...} */
        public String evidence() {
            return library + " " + versions + " default (verified by " + verifiedBy + "), " + source;
        }
    }

    private final Function<String, String> reader;
    private final Map<String, List<Entry>> cache = new HashMap<>();

    private DefaultsKb(Function<String, String> reader) {
        this.reader = reader;
    }

    /** Reads {@code kb/defaults/<library>.yaml} from the classpath. */
    public static DefaultsKb classpath() {
        return new DefaultsKb(library -> {
            String resource = "kb/defaults/" + library + ".yaml";
            try (InputStream in = DefaultsKb.class.getClassLoader().getResourceAsStream(resource)) {
                return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** For tests: library name to YAML text. */
    public static DefaultsKb of(Map<String, String> yamlByLibrary) {
        return new DefaultsKb(yamlByLibrary::get);
    }

    public Optional<Entry> lookup(String library, String parameter) {
        return entries(library).stream()
                .filter(e -> e.parameter().equals(parameter))
                .filter(e -> e.source() != null && !e.source().isBlank() && VERIFIED.contains(e.verifiedBy()))
                .findFirst();
    }

    /**
     * The default as a {@link Source#LIBRARY_DEFAULT} value, or UNKNOWN when the knowledge base has no
     * verified entry. {@code absent} describes what is missing in the configuration.
     */
    public <T> Val<T> defaultOr(String library, String parameter, Function<String, T> parse, String absent) {
        return lookup(library, parameter)
                .map(e -> new Val<>(parse.apply(e.value()), Source.LIBRARY_DEFAULT, absent + "; " + e.evidence()))
                .orElseGet(() -> Val.unknown(absent + "; no verified " + library + " default for " + parameter));
    }

    /**
     * ADR 0014: the failure classes a layer's retry conditions retry, from the {@code retryOn.<condition>} entries.
     * {@code direct} maps a condition without an entry (a status code), or returns null to look it up. A condition
     * with neither leaves the classes UNKNOWN, so every class counts as retried.
     */
    public Val<Set<FailureClass>> retried(String library, Val<Set<String>> conditions,
            Function<String, Set<FailureClass>> direct) {
        if (!conditions.isKnown()) {
            return Val.unknown(conditions.evidence() + "; every failure class is retried");
        }
        Set<FailureClass> classes = EnumSet.noneOf(FailureClass.class);
        Set<String> sources = new TreeSet<>();
        for (String condition : new TreeSet<>(conditions.value())) {
            Set<FailureClass> mapped = direct.apply(condition);
            if (mapped != null) {
                classes.addAll(mapped);
                continue;
            }
            Optional<Entry> e = lookup(library, "retryOn." + condition);
            if (e.isEmpty()) {
                return Val.unknown(conditions.evidence() + "; no verified " + library + " mapping for retry condition '"
                        + condition + "', so every failure class is retried");
            }
            classes.addAll(failureClasses(e.get()));
            sources.add(e.get().evidence());
        }
        String evidence = conditions.evidence() + (sources.isEmpty() ? "" : "; classes: " + String.join("; ", sources));
        return new Val<>(Collections.unmodifiableSet(classes), conditions.source(), evidence);
    }

    /** ADR 0014: what a layer answers the layer outside once it gives up, from the {@code givesUpAs.<class>} entries. */
    public Val<Map<FailureClass, Set<FailureClass>>> givesUpAs(String library) {
        Map<FailureClass, Set<FailureClass>> map = new EnumMap<>(FailureClass.class);
        Set<String> sources = new TreeSet<>();
        for (FailureClass from : List.of(FailureClass.NOT_SENT, FailureClass.RESET, FailureClass.TIMEOUT)) {
            Optional<Entry> e = lookup(library, "givesUpAs." + from.id());
            if (e.isEmpty()) {
                return Val.unknown("no verified " + library + " entry for givesUpAs." + from.id());
            }
            map.put(from, failureClasses(e.get()));
            sources.add(e.get().evidence());
        }
        return new Val<>(Collections.unmodifiableMap(map), Source.LIBRARY_DEFAULT, String.join("; ", sources));
    }

    /** {@code status-503,status-502} or {@code none}. */
    private static Set<FailureClass> failureClasses(Entry e) {
        Set<FailureClass> out = EnumSet.noneOf(FailureClass.class);
        if (!e.value().equals("none")) {
            for (String id : e.value().split(",")) {
                out.add(FailureClass.parse(id.strip()).orElseThrow(() -> new InvalidSpecException("kb/defaults/"
                        + e.library() + ".yaml: " + e.parameter() + ": unknown failure class '" + id.strip() + "'")));
            }
        }
        return Collections.unmodifiableSet(out);
    }

    private List<Entry> entries(String library) {
        return cache.computeIfAbsent(library, this::load);
    }

    private List<Entry> load(String library) {
        String yaml = reader.apply(library);
        if (yaml == null) {
            return List.of();
        }
        String file = "kb/defaults/" + library + ".yaml";
        Node root = YamlTree.parse(yaml, file);
        if (!(root instanceof ListNode list)) {
            throw new InvalidSpecException(file + ": must be a list of entries");
        }
        return list.items().stream().map(n -> entry(n, file)).toList();
    }

    private static Entry entry(Node n, String file) {
        if (!(n instanceof MapNode m)) {
            throw new InvalidSpecException(file + ":" + n.line() + ": entry must be a mapping");
        }
        return new Entry(text(m, "library", file), text(m, "versions", file), text(m, "parameter", file),
                text(m, "value", file), text(m, "unit", file), text(m, "source", file), text(m, "verified_by", file));
    }

    private static String text(MapNode m, String key, String file) {
        YamlTree.Entry e = m.entries().get(key);
        if (e == null) {
            throw new InvalidSpecException(file + ":" + m.line() + ": entry has no '" + key + "'");
        }
        if (!(e.value() instanceof Scalar s)) {
            throw new InvalidSpecException(file + ":" + e.keyLine() + ": '" + key + "' must be a scalar");
        }
        return s.text();
    }
}
