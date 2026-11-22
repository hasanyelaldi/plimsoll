package io.github.hasanyelaldi.plimsoll.collect.effective;

import com.fasterxml.jackson.core.JsonToken;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties.Property;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.io.YamlTree;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Entry;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.ListNode;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.MapNode;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Node;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Scalar;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A Spring Boot {@code /actuator/configprops} dump of a running service (ADR 0016, #25): the
 * {@code @ConfigurationProperties} beans as they were bound, each under its {@code prefix}, with the {@code inputs}
 * that say where a value came from.
 *
 * <p>The beans under the prefixes the collectors read ({@link #OVERLAID}) become properties that replace the declared
 * ones under the same prefixes. Their keys are the bean's property names (camelCase, which relaxed binding matches)
 * and durations are ISO-8601, which the collectors read. Each value's evidence is
 * {@code <dump>:<line> (<JSON pointer>)}, with the {@code origin} of its input when the dump has one. Empty lists and
 * maps carry no value and are left out. A masked value ({@code ******}, the default unless
 * {@code management.endpoint.configprops.show-values} is {@code always}) is left out with a note, so the declared
 * value stays for it.
 *
 * <p>Beans under {@link #COMPARED} are not read by any collector; they are only compared with the declared values.
 * The structure is the one Spring Boot 4.1 writes, checked by {@code ConfigPropsOracleTest}.
 */
public final class ConfigPropsDump {

    /** Prefixes whose dumped beans replace the declared properties. */
    public static final List<List<String>> OVERLAID = List.of(
            List.of("resilience4j"),
            List.of("spring", "cloud", "gateway"),
            List.of("spring", "cloud", "loadbalancer"),
            List.of("spring", "cloud", "circuitbreaker"));

    /** Prefixes compared key by key but not modelled (Spring AI retry, #19). */
    public static final List<List<String>> COMPARED = List.of(List.of("spring", "ai"));

    static final String MASKED = "******";

    /**
     * @param overlaid   the properties under {@link #OVERLAID}
     * @param compared   the properties under {@link #COMPARED}, with an origin, i.e. set by some property source
     * @param prefixes   the bean prefixes under {@link #OVERLAID} the dump has, which replace the declared ones
     * @param notes      masked values, several contexts
     */
    public record Dump(String file, String context, List<Property> overlaid, List<Property> compared,
            List<List<String>> prefixes, List<String> notes) {
        public Dump {
            overlaid = List.copyOf(overlaid);
            compared = List.copyOf(compared);
            prefixes = List.copyOf(prefixes);
            notes = List.copyOf(notes);
        }

        /** The service's declared properties with this dump's beans in place of the declared ones. */
        public SpringProperties applyTo(SpringProperties declared) {
            return declared.overlay(file, prefixes, overlaid);
        }
    }

    private ConfigPropsDump() {}

    /** Whether {@code root} looks like a configprops dump: a {@code contexts} mapping whose entries have {@code beans}. */
    public static boolean recognises(Node root) {
        return root instanceof MapNode m && m.entries().get("contexts") != null
                && m.entries().get("contexts").value() instanceof MapNode contexts
                && !contexts.entries().isEmpty()
                && contexts.entries().values().stream().allMatch(e -> e.value() instanceof MapNode c
                        && c.entries().get("beans") != null);
    }

    /**
     * @param file    the dump as named in evidence
     * @param service the service it belongs to; picks the context when the dump has several
     */
    public static Dump parse(String json, String file, String service) {
        Node root = YamlTree.parse(json, file);
        if (!recognises(root)) {
            throw new InvalidSpecException(file + ": not a /actuator/configprops dump (no contexts.<id>.beans)");
        }
        MapNode contexts = (MapNode) ((MapNode) root).entries().get("contexts").value();
        List<String> notes = new ArrayList<>();
        String id;
        if (contexts.entries().size() == 1) {
            id = contexts.entries().keySet().iterator().next();
        } else if (contexts.entries().containsKey(service)) {
            id = service;
            notes.add(file + ": contexts " + String.join(", ", new TreeMap<>(contexts.entries()).keySet())
                    + "; read context " + id + ", the one named like the service");
        } else {
            throw new InvalidSpecException(file + ": contexts " + String.join(", ",
                    new TreeMap<>(contexts.entries()).keySet()) + " and none is named " + service
                    + "; keep the application's context only");
        }
        MapNode beans = (MapNode) ((MapNode) contexts.entries().get(id).value()).entries().get("beans").value();
        List<Property> overlaid = new ArrayList<>();
        List<Property> compared = new ArrayList<>();
        List<List<String>> prefixes = new ArrayList<>();
        // sorted by bean name, so that the output does not depend on the dump's order
        for (Map.Entry<String, Entry> bean : new TreeMap<>(beans.entries()).entrySet()) {
            if (!(bean.getValue().value() instanceof MapNode b) || !(field(b, "prefix") instanceof Scalar p)) {
                continue;
            }
            List<String> prefix = Arrays.asList(p.text().split("\\."));
            boolean overlay = OVERLAID.stream().anyMatch(o -> SpringProperties.isUnder(prefix, o));
            boolean compare = COMPARED.stream().anyMatch(o -> SpringProperties.isUnder(prefix, o));
            if (!overlay && !compare) {
                continue;
            }
            String pointer = "/contexts/" + escape(id) + "/beans/" + escape(bean.getKey()) + "/properties";
            Node properties = field(b, "properties");
            Node inputs = field(b, "inputs");
            if (!(properties instanceof MapNode props)) {
                continue;
            }
            List<Property> out = new ArrayList<>();
            Walk w = new Walk(file, notes, out, compare);
            w.map(props, inputs, new ArrayList<>(prefix), pointer);
            if (overlay) {
                prefixes.add(prefix);
                overlaid.addAll(out);
            } else {
                compared.addAll(out);
            }
        }
        return new Dump(file, id, overlaid, compared, prefixes, notes);
    }

    private static Node field(MapNode m, String name) {
        Entry e = m.entries().get(name);
        return e == null ? null : e.value();
    }

    /** RFC 6901. */
    private static String escape(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }

    /** Flattens a bean's {@code properties} as {@code SpringProperties} flattens YAML, list items as {@code [i]}. */
    private record Walk(String file, List<String> notes, List<Property> out, boolean onlyWithOrigin) {

        void map(MapNode m, Node inputs, List<String> path, String pointer) {
            for (Map.Entry<String, Entry> e : m.entries().entrySet()) {
                List<String> p = new ArrayList<>(path);
                p.add(e.getKey());
                Node input = inputs instanceof MapNode im ? field(im, e.getKey()) : null;
                node(e.getValue().value(), input, p, pointer + "/" + escape(e.getKey()), e.getValue().keyLine());
            }
        }

        void node(Node n, Node input, List<String> path, String pointer, int line) {
            switch (n) {
                case MapNode child -> map(child, input, path, pointer);
                case ListNode list -> {
                    List<String> items = new ArrayList<>();
                    for (int i = 0; i < list.items().size(); i++) {
                        Node item = list.items().get(i);
                        Node itemInput = input instanceof ListNode il && i < il.items().size() ? il.items().get(i) : null;
                        List<String> itemPath = new ArrayList<>(path);
                        itemPath.add("[" + i + "]");
                        node(item, itemInput, itemPath, pointer + "/" + i, item.line());
                        if (items != null && item instanceof Scalar sc && sc.token() != JsonToken.VALUE_NULL) {
                            items.add(sc.text());
                        } else {
                            items = null;
                        }
                    }
                    // a list of scalars is also one property, as SpringProperties reads YAML lists
                    if (items != null && !items.isEmpty() && !masked(items, path, pointer, line)) {
                        add(new Property(path, null, items, evidence(line, pointer, origin(input))), input);
                    }
                }
                case Scalar s -> {
                    if (s.token() == JsonToken.VALUE_NULL) {
                        return;
                    }
                    if (masked(List.of(s.text()), path, pointer, line)) {
                        return;
                    }
                    add(new Property(path, s.text(), null, evidence(line, pointer, origin(input))), input);
                }
            }
        }

        private void add(Property p, Node input) {
            if (!onlyWithOrigin || origin(input) != null) {
                out.add(p);
            }
        }

        private boolean masked(List<String> values, List<String> path, String pointer, int line) {
            if (values.stream().noneMatch(MASKED::equals)) {
                return false;
            }
            notes.add(file + ":" + line + " (" + pointer + "): " + String.join(".", path) + " is masked; set "
                    + "management.endpoint.configprops.show-values=always to compare it; the declared value is kept");
            return true;
        }

        private static String origin(Node input) {
            return input instanceof MapNode m && field(m, "origin") instanceof Scalar o ? o.text() : null;
        }

        private String evidence(int line, String pointer, String origin) {
            return file + ":" + line + " (" + pointer + (origin == null ? "" : "; origin: " + origin) + ")";
        }
    }
}
