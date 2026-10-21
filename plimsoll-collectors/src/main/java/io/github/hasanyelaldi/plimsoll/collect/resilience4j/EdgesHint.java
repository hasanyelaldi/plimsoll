package io.github.hasanyelaldi.plimsoll.collect.resilience4j;

import com.fasterxml.jackson.core.JsonToken;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.io.YamlTree;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Entry;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.ListNode;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.MapNode;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Node;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Scalar;
import io.github.hasanyelaldi.plimsoll.model.Durations;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * {@code edges-hint.yaml}: which service the code belongs to and which annotated method calls which service.
 * Code alone does not say where a client method sends its request.
 *
 * <pre>
 * service: api-gateway
 * deadline: 10s                                       # optional: how long the service works on one request
 * entry: true                                         # optional: overrides the entry rule (ADR 0009)
 * methods:
 *   - method: com.example.CustomersClient#getOwner   # fully qualified class # method name
 *     to: customers-service
 *     operation: GET /owners/{id}                     # optional, defaults to Class#method
 *     idempotent: true                                # optional, UNKNOWN when absent
 *     fanout: 1                                       # optional: calls per request, UNKNOWN when absent
 *     client: webclient-loadbalanced                  # optional: the HTTP client the method calls through
 * operations:                                         # optional: facts about operations the service calls
 *   - to: customers-service
 *     operation: POST /owners                         # method and path as the callee maps it
 *     idempotent: true                                # e.g. a POST protected by an idempotency key
 * </pre>
 *
 * <p>{@code client} is {@code webclient-loadbalanced} (a {@code @LoadBalanced WebClient.Builder} made with
 * {@code WebClient.builder()}) or {@code restclient} (made with {@code RestClient.builder()}, no load balancer).
 * Without it the edge has no HTTP client layer.
 *
 * <p>{@code operations} overrides what the code gives for an operation reached without an annotated client method,
 * such as a gateway route: its {@code idempotent} is EXPLICIT and wins over the HTTP method's.
 */
public record EdgesHint(String service, Val<Duration> deadline, Val<Boolean> entry, List<Hint> methods,
        List<OperationHint> operations) {

    public EdgesHint {
        methods = List.copyOf(methods);
        operations = List.copyOf(operations);
    }

    /**
     * @param operation e.g. {@code POST /owners}
     * @param evidence  {@code file:line} of the entry
     */
    public record OperationHint(String to, String operation, Val<Boolean> idempotent, String evidence) {}

    /**
     * @param client   one of {@link #CLIENTS} with the line it is on, or null when absent
     * @param evidence {@code file:line} of the entry
     */
    public record Hint(String className, String method, String to, String operation, Val<Boolean> idempotent,
            Val<Integer> fanout, Val<String> client, String evidence) {

        public String qualifiedMethod() {
            return className + "#" + method;
        }
    }

    private static final Set<String> TOP_KEYS = Set.of("service", "deadline", "entry", "methods", "operations");
    private static final Set<String> OPERATION_KEYS = Set.of("to", "operation", "idempotent");
    public static final Set<String> CLIENTS = Set.of("webclient-loadbalanced", "restclient");

    private static final Set<String> METHOD_KEYS =
            Set.of("method", "to", "operation", "idempotent", "fanout", "client");

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
        List<OperationHint> operations = new ArrayList<>();
        Entry ops = top.entries().get("operations");
        if (ops != null) {
            if (!(ops.value() instanceof ListNode list)) {
                throw new InvalidSpecException(fileName + ":" + ops.keyLine() + ": operations must be a list");
            }
            for (Node n : list.items()) {
                operations.add(operationHint(map(n, "operation entry", fileName), fileName));
            }
        }
        return new EdgesHint(service, deadline(top, fileName), entry(top, fileName), hints, operations);
    }

    private static OperationHint operationHint(MapNode m, String file) {
        checkKeys(m, OPERATION_KEYS, file);
        String to = string(required(m, "to", file), "to", file);
        String operation = string(required(m, "operation", file), "operation", file);
        Entry idem = required(m, "idempotent", file);
        return new OperationHint(to, operation, Val.explicit(bool(idem, file), file + ":" + idem.keyLine()),
                file + ":" + m.line());
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
        Val<Integer> fanout = fan == null
                ? Val.unknown("'fanout' absent (" + file + ":" + m.line() + ")")
                : Val.explicit(fanout(fan, file), file + ":" + fan.keyLine());
        Entry cl = m.entries().get("client");
        Val<String> client = null;
        if (cl != null) {
            String name = string(cl, "client", file);
            if (!CLIENTS.contains(name)) {
                throw new InvalidSpecException(file + ":" + cl.keyLine() + ": 'client' must be one of "
                        + new TreeSet<>(CLIENTS) + ", got '" + name + "'");
            }
            client = Val.explicit(name, file + ":" + cl.keyLine());
        }
        return new Hint(method.substring(0, hash), method.substring(hash + 1), to,
                op == null ? null : string(op, "operation", file), idempotent, fanout, client, file + ":" + m.line());
    }

    private static Val<Duration> deadline(MapNode top, String file) {
        Entry e = top.entries().get("deadline");
        if (e == null) {
            return Val.unknown("'deadline' absent (" + file + ":" + top.line() + ")");
        }
        String text = string(e, "deadline", file);
        Duration d = Durations.parse(text).filter(Duration::isPositive).orElseThrow(() -> new InvalidSpecException(
                file + ":" + e.keyLine() + ": 'deadline' must be a positive duration such as 2s or 500ms, got '" + text + "'"));
        return Val.explicit(d, file + ":" + e.keyLine());
    }

    private static Val<Boolean> entry(MapNode top, String file) {
        Entry e = top.entries().get("entry");
        return e == null
                ? Val.unknown("'entry' absent (" + file + ":" + top.line() + ")")
                : Val.explicit(bool(e, "entry", file), file + ":" + e.keyLine());
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
        return bool(e, "idempotent", file);
    }

    private static boolean bool(Entry e, String key, String file) {
        if (e.value() instanceof Scalar s
                && (s.token() == JsonToken.VALUE_TRUE || s.token() == JsonToken.VALUE_FALSE)) {
            return s.token() == JsonToken.VALUE_TRUE;
        }
        throw new InvalidSpecException(file + ":" + e.keyLine() + ": '" + key + "' must be true or false");
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
