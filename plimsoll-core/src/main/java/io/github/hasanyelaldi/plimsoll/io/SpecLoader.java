package io.github.hasanyelaldi.plimsoll.io;

import com.fasterxml.jackson.core.JsonToken;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Entry;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.ListNode;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.MapNode;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Node;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Scalar;
import io.github.hasanyelaldi.plimsoll.model.Durations;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.Service;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Loads the hand-written YAML spec. Attempt counts are normalized here, at the boundary:
 * Resilience4j {@code maxAttempts} and generic {@code totalAttempts} already include the first call,
 * Istio {@code attempts} counts retries only and becomes {@code attempts + 1}.
 *
 * <p>An absent {@code multiplier} means fixed backoff (1.0) and an absent {@code fanout} means one call;
 * any other absent field is UNKNOWN.
 */
public final class SpecLoader {

    private static final Set<String> TOP_KEYS = Set.of("services", "edges");
    private static final Set<String> SERVICE_KEYS = Set.of("entry", "deadline");
    private static final Set<String> EDGE_KEYS = Set.of("from", "to", "operation", "idempotent", "fanout", "layers",
            "choice");
    private static final Set<String> LAYER_KEYS = Set.of(
            "kind", "maxAttempts", "attempts", "totalAttempts", "perTryTimeout", "backoff", "multiplier",
            "maxBackoff", "jitter", "retryOn", "overallTimeout");
    private static final Map<String, String> ATTEMPTS_KEY = Map.of(
            "resilience4j", "maxAttempts",
            "istio", "attempts",
            "http-client", "totalAttempts",
            "generic", "totalAttempts");

    private final String file;

    private SpecLoader(String file) {
        this.file = file;
    }

    public static ServiceGraph load(Path path) {
        String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new InvalidSpecException(path + ": cannot read spec: " + e.getMessage(), e);
        }
        return parse(text, path.getFileName().toString());
    }

    /** @param fileName used in {@code file:line} evidence */
    public static ServiceGraph parse(String yaml, String fileName) {
        return new SpecLoader(fileName).graph(YamlTree.parse(yaml, fileName));
    }

    private ServiceGraph graph(Node root) {
        MapNode top = map(root, "spec");
        checkKeys(top, TOP_KEYS, "spec");

        Map<String, Service> services = new LinkedHashMap<>();
        MapNode serviceMap = map(required(top, "services").value(), "services");
        for (var e : serviceMap.entries().entrySet()) {
            services.put(e.getKey(), service(e.getKey(), map(e.getValue().value(), "service " + e.getKey())));
        }
        if (services.values().stream().noneMatch(Service::entry)) {
            throw error(serviceMap.line(), "no entry service: mark at least one service with 'entry: true'");
        }

        List<Edge> edges = new ArrayList<>();
        Entry edgesEntry = top.entries().get("edges");
        if (edgesEntry != null) {
            for (Node n : list(edgesEntry.value(), "edges").items()) {
                MapNode m = map(n, "edge");
                Edge edge = edge(m);
                for (String end : List.of(edge.from(), edge.to())) {
                    if (!services.containsKey(end)) {
                        throw error(m.line(), "edge " + edge.location() + " refers to unknown service '" + end + "'");
                    }
                }
                edges.add(edge);
            }
        }
        return new ServiceGraph(services, edges);
    }

    private Service service(String name, MapNode m) {
        checkKeys(m, SERVICE_KEYS, "service " + name);
        boolean entry = m.entries().containsKey("entry") && bool(m.entries().get("entry"), "entry");
        return new Service(name, duration(m, "deadline"), entry);
    }

    private Edge edge(MapNode m) {
        checkKeys(m, EDGE_KEYS, "edge");
        String from = string(required(m, "from"), "from");
        String to = string(required(m, "to"), "to");
        Entry op = m.entries().get("operation");
        Entry fan = m.entries().get("fanout");
        Val<Integer> fanout = fan == null
                ? Val.explicit(1, absent("fanout", m) + ", spec default 1")
                : Val.explicit(integer(fan, "fanout", 1), at(fan.keyLine()));
        List<Layer> layers = new ArrayList<>();
        Entry layersEntry = m.entries().get("layers");
        if (layersEntry != null) {
            for (Node n : list(layersEntry.value(), "layers").items()) {
                layers.add(layer(map(n, "layer")));
            }
        }
        Entry choice = m.entries().get("choice");
        return new Edge(from, to, op == null ? null : string(op, "operation"), booleanVal(m, "idempotent"), fanout,
                layers, choice == null ? null : Val.explicit(string(choice, "choice"), at(choice.keyLine())));
    }

    private Layer layer(MapNode m) {
        checkKeys(m, LAYER_KEYS, "layer");
        String kind = string(required(m, "kind"), "kind");
        String attemptsKey = ATTEMPTS_KEY.get(kind);
        if (attemptsKey == null) {
            throw error(m.entries().get("kind").keyLine(), "unknown layer kind '" + kind
                    + "' (expected resilience4j, istio, http-client or generic)");
        }
        for (String other : Set.of("maxAttempts", "attempts", "totalAttempts")) {
            if (!other.equals(attemptsKey) && m.entries().containsKey(other)) {
                throw error(m.entries().get(other).keyLine(),
                        "'" + other + "' does not apply to kind '" + kind + "' (use '" + attemptsKey + "')");
            }
        }

        Val<Integer> total;
        Entry attempts = m.entries().get(attemptsKey);
        if (attempts == null) {
            total = Val.unknown(absent(attemptsKey, m));
        } else if (kind.equals("istio")) {
            int retries = integer(attempts, attemptsKey, 0);
            total = Val.explicit(retries + 1, at(attempts.keyLine()) + " (attempts " + retries + " + 1)");
        } else {
            total = Val.explicit(integer(attempts, attemptsKey, 1), at(attempts.keyLine()) + " (" + attemptsKey + ")");
        }

        Entry mult = m.entries().get("multiplier");
        Val<Double> multiplier = mult == null
                ? Val.explicit(1.0, absent("multiplier", m) + ", spec default 1.0 (fixed backoff)")
                : Val.explicit(number(mult, "multiplier"), at(mult.keyLine()));

        RetryPolicy retry = new RetryPolicy(
                total,
                timeout(m, "perTryTimeout"),
                duration(m, "backoff"),
                multiplier,
                duration(m, "maxBackoff"),
                booleanVal(m, "jitter"),
                retryOn(m));
        return new Layer(kind, retry, timeout(m, "overallTimeout"));
    }

    private Val<Set<String>> retryOn(MapNode m) {
        Entry e = m.entries().get("retryOn");
        if (e == null) {
            return Val.unknown(absent("retryOn", m));
        }
        Set<String> conditions = new TreeSet<>();
        for (Node n : list(e.value(), "retryOn").items()) {
            conditions.add(scalar(new Entry(e.keyLine(), n), "retryOn", null, "a list of strings or numbers").text());
        }
        return Val.explicit(Set.copyOf(conditions), at(e.keyLine()));
    }

    // ---- typed accessors -------------------------------------------------

    private Val<Duration> duration(MapNode m, String key) {
        Entry e = m.entries().get(key);
        if (e == null) {
            return Val.unknown(absent(key, m));
        }
        String text = scalar(e, key, JsonToken.VALUE_STRING, "a duration string such as 300ms or 1s").text();
        Duration d = Durations.parse(text).orElseThrow(() ->
                error(e.keyLine(), "'" + key + "': invalid duration '" + text + "' (use e.g. 300ms, 1s, 2m)"));
        return Val.explicit(d, at(e.keyLine()));
    }

    /** A duration, or {@code unlimited} for no timeout (ADR 0011). */
    private Val<Duration> timeout(MapNode m, String key) {
        Entry e = m.entries().get(key);
        if (e == null) {
            return Val.unknown(absent(key, m));
        }
        String text = scalar(e, key, JsonToken.VALUE_STRING, "a duration string such as 300ms or 1s, or unlimited").text();
        Duration d = Durations.parseTimeout(text).orElseThrow(() ->
                error(e.keyLine(), "'" + key + "': invalid timeout '" + text + "' (use e.g. 300ms, 1s, 2m or unlimited)"));
        return Val.explicit(d, at(e.keyLine()));
    }

    private Val<Boolean> booleanVal(MapNode m, String key) {
        Entry e = m.entries().get(key);
        return e == null ? Val.unknown(absent(key, m)) : Val.explicit(bool(e, key), at(e.keyLine()));
    }

    private boolean bool(Entry e, String key) {
        Scalar s = scalar(e, key, null, "true or false");
        if (s.token() != JsonToken.VALUE_TRUE && s.token() != JsonToken.VALUE_FALSE) {
            throw error(e.keyLine(), "'" + key + "' must be true or false");
        }
        return s.token() == JsonToken.VALUE_TRUE;
    }

    private int integer(Entry e, String key, int min) {
        Scalar s = scalar(e, key, JsonToken.VALUE_NUMBER_INT, "an integer");
        try {
            int v = Integer.parseInt(s.text());
            if (v < min) {
                throw error(e.keyLine(), "'" + key + "' must be at least " + min + ", got " + v);
            }
            return v;
        } catch (NumberFormatException ex) {
            throw error(e.keyLine(), "'" + key + "' is not a valid integer: " + s.text());
        }
    }

    private double number(Entry e, String key) {
        Scalar s = scalar(e, key, null, "a number");
        if (s.token() != JsonToken.VALUE_NUMBER_INT && s.token() != JsonToken.VALUE_NUMBER_FLOAT) {
            throw error(e.keyLine(), "'" + key + "' must be a number");
        }
        double v = Double.parseDouble(s.text());
        if (!(v >= 1.0) || Double.isInfinite(v)) {
            throw error(e.keyLine(), "'" + key + "' must be a finite number >= 1, got " + s.text());
        }
        return v;
    }

    private String string(Entry e, String key) {
        return scalar(e, key, JsonToken.VALUE_STRING, "a string").text();
    }

    private Scalar scalar(Entry e, String key, JsonToken expected, String description) {
        if (!(e.value() instanceof Scalar s) || s.token() == JsonToken.VALUE_NULL
                || (expected != null && s.token() != expected)) {
            throw error(e.keyLine(), "'" + key + "' must be " + description);
        }
        return s;
    }

    // ---- structure helpers -----------------------------------------------

    private MapNode map(Node n, String what) {
        if (n instanceof MapNode m) {
            return m;
        }
        throw error(n.line(), what + " must be a mapping");
    }

    private ListNode list(Node n, String what) {
        if (n instanceof ListNode l) {
            return l;
        }
        throw error(n.line(), what + " must be a list");
    }

    private Entry required(MapNode m, String key) {
        Entry e = m.entries().get(key);
        if (e == null) {
            throw error(m.line(), "missing required key '" + key + "'");
        }
        return e;
    }

    private void checkKeys(MapNode m, Set<String> allowed, String what) {
        for (var e : m.entries().entrySet()) {
            if (!allowed.contains(e.getKey())) {
                throw error(e.getValue().keyLine(), "unknown key '" + e.getKey() + "' in " + what);
            }
        }
    }

    private String at(int line) {
        return file + ":" + line;
    }

    private String absent(String key, MapNode m) {
        return "'" + key + "' absent (" + at(m.line()) + ")";
    }

    private InvalidSpecException error(int line, String message) {
        return new InvalidSpecException(at(line) + ": " + message);
    }
}
