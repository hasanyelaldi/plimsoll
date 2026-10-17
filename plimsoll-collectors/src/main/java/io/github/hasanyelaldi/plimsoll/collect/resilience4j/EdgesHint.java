package io.github.hasanyelaldi.plimsoll.collect.resilience4j;

import com.fasterxml.jackson.core.JsonToken;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.io.YamlTree;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Entry;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.ListNode;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.MapNode;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Node;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Scalar;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@code edges-hint.yaml}: which service the code belongs to and which annotated method calls which service.
 * Code alone does not say where a client method sends its request.
 *
 * <pre>
 * service: api-gateway
 * methods:
 *   - method: com.example.CustomersClient#getOwner   # fully qualified class # method name
 *     to: customers-service
 *     operation: GET /owners/{id}                     # optional, defaults to Class#method
 *     idempotent: true                                # optional, UNKNOWN when absent
 *     fanout: 1                                       # optional, default 1
 * </pre>
 */
public record EdgesHint(String service, List<Hint> methods) {

    public EdgesHint {
        methods = List.copyOf(methods);
    }

    /** @param evidence {@code file:line} of the entry */
    public record Hint(String className, String method, String to, String operation, Val<Boolean> idempotent,
            int fanout, String evidence) {

        public String qualifiedMethod() {
            return className + "#" + method;
        }
    }

    private static final Set<String> TOP_KEYS = Set.of("service", "methods");
    private static final Set<String> METHOD_KEYS = Set.of("method", "to", "operation", "idempotent", "fanout");

    /** @param displayName used in {@code file:line} evidence */
    public static EdgesHint load(Path file, String displayName) {
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8), displayName);
        } catch (IOException e) {
            throw new InvalidSpecException(displayName + ": cannot read: " + e.getMessage(), e);
        }
    }

    public static EdgesHint parse(String yaml, String fileName) {
        MapNode top = map(YamlTree.parse(yaml, fileName), "edges hint", fileName);
        checkKeys(top, TOP_KEYS, fileName);
        String service = string(required(top, "service", fileName), "service", fileName);
        List<Hint> hints = new ArrayList<>();
        Entry methods = top.entries().get("methods");
        if (methods != null) {
            if (!(methods.value() instanceof ListNode list)) {
                throw new InvalidSpecException(fileName + ":" + methods.keyLine() + ": methods must be a list");
            }
            for (Node n : list.items()) {
                hints.add(hint(map(n, "method entry", fileName), fileName));
            }
        }
        return new EdgesHint(service, hints);
    }

    private static Hint hint(MapNode m, String file) {
        checkKeys(m, METHOD_KEYS, file);
        Entry methodEntry = required(m, "method", file);
        String method = string(methodEntry, "method", file);
        int hash = method.indexOf('#');
        if (hash <= 0 || hash == method.length() - 1 || method.indexOf('#', hash + 1) >= 0) {
            throw new InvalidSpecException(file + ":" + methodEntry.keyLine()
                    + ": method must be <fully.qualified.Class>#<method>, got '" + method + "'");
        }
        String to = string(required(m, "to", file), "to", file);
        Entry op = m.entries().get("operation");
        Entry idem = m.entries().get("idempotent");
        Val<Boolean> idempotent = idem == null
                ? Val.unknown("'idempotent' absent (" + file + ":" + m.line() + ")")
                : Val.explicit(bool(idem, file), file + ":" + idem.keyLine());
        Entry fan = m.entries().get("fanout");
        int fanout = fan == null ? 1 : fanout(fan, file);
        return new Hint(method.substring(0, hash), method.substring(hash + 1), to,
                op == null ? null : string(op, "operation", file), idempotent, fanout, file + ":" + m.line());
    }

    private static MapNode map(Node n, String what, String file) {
        if (n instanceof MapNode m) {
            return m;
        }
        throw new InvalidSpecException(file + ":" + n.line() + ": " + what + " must be a mapping");
    }

    private static Entry required(MapNode m, String key, String file) {
        Entry e = m.entries().get(key);
        if (e == null) {
            throw new InvalidSpecException(file + ":" + m.line() + ": missing required key '" + key + "'");
        }
        return e;
    }

    private static void checkKeys(MapNode m, Set<String> allowed, String file) {
        for (var e : m.entries().entrySet()) {
            if (!allowed.contains(e.getKey())) {
                throw new InvalidSpecException(file + ":" + e.getValue().keyLine() + ": unknown key '" + e.getKey() + "'");
            }
        }
    }

    private static String string(Entry e, String key, String file) {
        if (e.value() instanceof Scalar s && s.token() == JsonToken.VALUE_STRING) {
            return s.text();
        }
        throw new InvalidSpecException(file + ":" + e.keyLine() + ": '" + key + "' must be a string");
    }

    private static boolean bool(Entry e, String file) {
        if (e.value() instanceof Scalar s
                && (s.token() == JsonToken.VALUE_TRUE || s.token() == JsonToken.VALUE_FALSE)) {
            return s.token() == JsonToken.VALUE_TRUE;
        }
        throw new InvalidSpecException(file + ":" + e.keyLine() + ": 'idempotent' must be true or false");
    }

    private static int fanout(Entry e, String file) {
        if (e.value() instanceof Scalar s && s.token() == JsonToken.VALUE_NUMBER_INT) {
            try {
                int v = Integer.parseInt(s.text());
                if (v >= 1) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
                // falls through to the error below
            }
        }
        throw new InvalidSpecException(file + ":" + e.keyLine() + ": 'fanout' must be an integer of at least 1");
    }
}
