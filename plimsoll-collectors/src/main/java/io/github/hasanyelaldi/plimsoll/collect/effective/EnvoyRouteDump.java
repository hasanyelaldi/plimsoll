package io.github.hasanyelaldi.plimsoll.collect.effective;

import com.fasterxml.jackson.core.JsonToken;
import io.github.hasanyelaldi.plimsoll.collect.istio.IstioCollector;
import io.github.hasanyelaldi.plimsoll.collect.istio.IstioCollector.Route;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.io.YamlTree;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Entry;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.ListNode;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.MapNode;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Node;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Scalar;
import io.github.hasanyelaldi.plimsoll.model.Durations;
import io.github.hasanyelaldi.plimsoll.model.FailureClass;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.RetryConditions;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The HTTP routes an Istio sidecar applies, read from its Envoy {@code config_dump} (ADR 0016, #25 part 3): the
 * admin endpoint's JSON, whole or with {@code ?resource=dynamic_route_configs}, for example from
 * {@code kubectl exec <pod> -c istio-proxy -- pilot-agent request GET config_dump}.
 *
 * <p>Every route whose cluster is an outbound service ({@code outbound|<port>|<subset>|<host>}) becomes an
 * {@code istio} layer for that callee, with Envoy's semantics: {@code num_retries} counts retries only, and defaults
 * to 1; no {@code retry_policy} (on the route or its virtual host) means one try; a {@code timeout} of 0 means none,
 * and an absent one is Envoy's 15 s; an absent {@code per_try_timeout} is the route timeout; an absent back-off is
 * 25 ms (all in {@code kb/defaults/envoy.yaml}). The retry conditions map to failure classes through the istio
 * entries (ADR 0014): {@code retriable_status_codes} are conditions of their own, as in a VirtualService's
 * {@code retryOn}, so {@code retriable-status-codes} itself is left out. Field names are read in snake_case, as the
 * admin endpoint writes them, or in lowerCamelCase. Each value's evidence is {@code <dump>:<line> (<JSON pointer>)}.
 */
public final class EnvoyRouteDump {

    private static final String KB = "envoy";
    private static final String ISTIO_KB = "istio";
    private static final String ROUTES_CONFIG_DUMP = "envoy.admin.v3.RoutesConfigDump";
    private static final Pattern PROTO_DURATION = Pattern.compile("-?\\d+(\\.\\d+)?s");
    private static final String RETRIABLE_STATUS_CODES = "retriable-status-codes";

    /**
     * @param routes in dump order
     * @param notes  routes that were not read
     */
    public record Dump(String file, List<Route> routes, List<String> notes) {
        public Dump {
            routes = List.copyOf(routes);
            notes = List.copyOf(notes);
        }
    }

    private final String file;
    private final DefaultsKb kb;
    private final List<Route> routes = new ArrayList<>();
    private final Set<String> otherClusters = new TreeSet<>();

    private EnvoyRouteDump(String file, DefaultsKb kb) {
        this.file = file;
        this.kb = kb;
    }

    /** Whether {@code root} looks like an Envoy config dump with route configurations. */
    public static boolean recognises(Node root) {
        return root instanceof MapNode m && m.entries().get("configs") != null
                && m.entries().get("configs").value() instanceof ListNode configs
                && configs.items().stream().anyMatch(c -> c instanceof MapNode cm && type(cm).contains(ROUTES_CONFIG_DUMP));
    }

    /** @param file the dump as named in evidence */
    public static Dump parse(String json, String file, DefaultsKb kb) {
        Node root = YamlTree.parse(json, file);
        if (!recognises(root)) {
            throw new InvalidSpecException(file + ": not an Envoy config_dump with route configurations");
        }
        EnvoyRouteDump d = new EnvoyRouteDump(file, kb);
        List<Node> configs = ((ListNode) ((MapNode) root).entries().get("configs").value()).items();
        for (int i = 0; i < configs.size(); i++) {
            if (!(configs.get(i) instanceof MapNode c) || !type(c).contains(ROUTES_CONFIG_DUMP)) {
                continue;
            }
            String pointer = "/configs/" + i;
            if (field(c, "route_config") instanceof MapNode rc) {
                // ?resource=dynamic_route_configs: one item per route configuration
                d.routeConfig(rc, pointer + "/" + key(c, "route_config"));
                continue;
            }
            for (String list : List.of("static_route_configs", "dynamic_route_configs")) {
                List<Node> items = items(c, list);
                for (int j = 0; j < items.size(); j++) {
                    if (items.get(j) instanceof MapNode item && field(item, "route_config") instanceof MapNode rc) {
                        d.routeConfig(rc, pointer + "/" + key(c, list) + "/" + j + "/" + key(item, "route_config"));
                    }
                }
            }
        }
        List<String> notes = new ArrayList<>();
        if (!d.otherClusters.isEmpty()) {
            notes.add(file + ": routes to " + String.join(", ", d.otherClusters)
                    + " are not outbound services; not read");
        }
        if (d.routes.isEmpty()) {
            notes.add(file + ": no route to an outbound service");
        }
        return new Dump(file, d.routes, notes);
    }

    private void routeConfig(MapNode rc, String pointer) {
        List<Node> hosts = items(rc, "virtual_hosts");
        for (int v = 0; v < hosts.size(); v++) {
            if (!(hosts.get(v) instanceof MapNode vh)) {
                continue;
            }
            String vhPointer = pointer + "/" + key(rc, "virtual_hosts") + "/" + v;
            String vhName = field(vh, "name") instanceof Scalar s ? s.text() : "virtual_hosts[" + v + "]";
            MapNode vhRetry = field(vh, "retry_policy") instanceof MapNode m ? m : null;
            String vhRetryPointer = vhPointer + "/" + key(vh, "retry_policy");
            List<Node> rs = items(vh, "routes");
            for (int r = 0; r < rs.size(); r++) {
                if (!(rs.get(r) instanceof MapNode route) || !(field(route, "route") instanceof MapNode action)) {
                    continue; // a redirect or a direct response calls no service
                }
                String routePointer = vhPointer + "/" + key(vh, "routes") + "/" + r;
                String name = field(route, "name") instanceof Scalar s ? s.text() : vhName + " routes[" + r + "]";
                Set<String> clusters = clusters(action);
                Layer layer = null;
                for (String cluster : clusters) {
                    String[] parts = cluster.split("\\|", -1);
                    if (parts.length != 4 || !parts[0].equals("outbound")) {
                        otherClusters.add(cluster);
                        continue;
                    }
                    if (layer == null) {
                        layer = layer(action, routePointer + "/" + key(route, "route"), route.line(), vhRetry,
                                vhRetryPointer);
                    }
                    routes.add(new Route(IstioCollector.serviceOf(parts[3]), parts[3], name, layer,
                            at(route.line(), routePointer)));
                }
            }
        }
    }

    /** The cluster, or the distinct clusters of weighted clusters, in order. */
    private static Set<String> clusters(MapNode action) {
        Set<String> out = new LinkedHashSet<>();
        if (field(action, "cluster") instanceof Scalar s) {
            out.add(s.text());
        } else if (field(action, "weighted_clusters") instanceof MapNode w) {
            for (Node n : items(w, "clusters")) {
                if (n instanceof MapNode c && field(c, "name") instanceof Scalar s) {
                    out.add(s.text());
                }
            }
        }
        return out;
    }

    private Layer layer(MapNode action, String pointer, int routeLine, MapNode vhRetry, String vhRetryPointer) {
        Val<Duration> routeTimeout = timeout(action, pointer, routeLine);
        MapNode policy = field(action, "retry_policy") instanceof MapNode m ? m : vhRetry;
        String policyPointer = policy == vhRetry ? vhRetryPointer : pointer + "/" + key(action, "retry_policy");
        if (policy == null) {
            String none = at(routeLine, pointer) + ": no retry_policy on the route or its virtual host";
            Val<Set<String>> retryOn = Val.explicit(Set.of(), none);
            RetryPolicy retry = new RetryPolicy(Val.explicit(1, none + " (one try)"),
                    perTryTimeout(null, policyPointer, routeTimeout, none), backoff(null, policyPointer, none),
                    multiplier(), maxBackoff(), jitter(), retryOn);
            return new Layer(ISTIO_KB, retry, routeTimeout, new RetryConditions(retried(retryOn), kb.givesUpAs(ISTIO_KB)));
        }
        String absent = "not set in retry_policy at " + at(policy.line(), policyPointer);
        Val<Integer> totalAttempts;
        Entry n = entry(policy, "num_retries");
        if (n == null) {
            Val<Integer> retries = kb.defaultOr(KB, "retryPolicy.numRetries", Integer::valueOf, absent);
            totalAttempts = retries.isKnown() ? new Val<>(retries.value() + 1, retries.source(),
                    retries.evidence() + " (num_retries " + retries.value() + " + 1)") : retries;
        } else {
            int retries = integer(n, policyPointer + "/" + key(policy, "num_retries"));
            totalAttempts = Val.explicit(retries + 1, at(n.keyLine(), policyPointer + "/" + key(policy, "num_retries"))
                    + " (num_retries " + retries + " + 1)");
        }
        Val<Set<String>> retryOn = retryOn(policy, policyPointer);
        RetryPolicy retry = new RetryPolicy(totalAttempts, perTryTimeout(policy, policyPointer, routeTimeout, absent),
                backoff(policy, policyPointer, absent), multiplier(), maxBackoff(), jitter(), retryOn);
        return new Layer(ISTIO_KB, retry, routeTimeout, new RetryConditions(retried(retryOn), kb.givesUpAs(ISTIO_KB)));
    }

    /** A {@code timeout} of 0 disables the route timeout; an absent one is Envoy's default. */
    private Val<Duration> timeout(MapNode action, String pointer, int routeLine) {
        Entry e = entry(action, "timeout");
        if (e == null) {
            return kb.defaultOr(KB, "route.timeout", EnvoyRouteDump::kbDuration,
                    "no timeout at " + at(routeLine, pointer));
        }
        String where = at(e.keyLine(), pointer + "/" + key(action, "timeout"));
        Duration d = duration(e, where);
        return d.isZero() ? Val.explicit(Durations.UNLIMITED, where + " (0: no route timeout)") : Val.explicit(d, where);
    }

    private Val<Duration> perTryTimeout(MapNode policy, String pointer, Val<Duration> routeTimeout, String absent) {
        Entry e = policy == null ? null : entry(policy, "per_try_timeout");
        if (e != null) {
            String where = at(e.keyLine(), pointer + "/" + key(policy, "per_try_timeout"));
            return Val.explicit(duration(e, where), where);
        }
        var rule = kb.lookup(KB, "retryPolicy.perTryTimeout").filter(r -> r.value().equals("route timeout"));
        if (rule.isEmpty()) {
            return Val.unknown(absent + "; no verified envoy default for retryPolicy.perTryTimeout");
        }
        if (!routeTimeout.isKnown()) {
            return Val.unknown("no per-try timeout: " + absent + " and " + routeTimeout.evidence() + "; "
                    + rule.get().evidence());
        }
        return new Val<>(routeTimeout.value(), Source.LIBRARY_DEFAULT, "the route timeout (" + routeTimeout.evidence()
                + "); per_try_timeout: " + absent + "; " + rule.get().evidence());
    }

    private Val<Duration> backoff(MapNode policy, String pointer, String absent) {
        if (policy != null && field(policy, "retry_back_off") instanceof MapNode b) {
            Entry base = entry(b, "base_interval");
            if (base != null) {
                String where = at(base.keyLine(), pointer + "/" + key(policy, "retry_back_off") + "/"
                        + key(b, "base_interval"));
                return Val.explicit(duration(base, where), where);
            }
        }
        return kb.defaultOr(KB, "retryPolicy.retryBackOff.baseInterval", EnvoyRouteDump::kbDuration, absent);
    }

    /** The same values the Istio collector gives, so that a route read from both compares equal. */
    private static Val<Double> multiplier() {
        return Val.unknown("Envoy back-off is fully jittered exponential; no fixed multiplier");
    }

    private static Val<Duration> maxBackoff() {
        return Val.unknown("Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait");
    }

    private Val<Boolean> jitter() {
        return kb.defaultOr(ISTIO_KB, "retries.jitter", Boolean::valueOf, "Envoy has no jitter setting");
    }

    /** {@code retry_on} without {@code retriable-status-codes}, plus each of {@code retriable_status_codes}. */
    private Val<Set<String>> retryOn(MapNode policy, String pointer) {
        Entry on = entry(policy, "retry_on");
        Set<String> out = new TreeSet<>();
        List<String> where = new ArrayList<>();
        if (on != null) {
            String text = on.value() instanceof Scalar s ? s.text() : "";
            Arrays.stream(text.split(",")).map(String::strip).filter(c -> !c.isEmpty())
                    .filter(c -> !c.equals(RETRIABLE_STATUS_CODES)).forEach(out::add);
            where.add(at(on.keyLine(), pointer + "/" + key(policy, "retry_on")));
        }
        Entry codes = entry(policy, "retriable_status_codes");
        if (codes != null && codes.value() instanceof ListNode list && !list.items().isEmpty()) {
            list.items().forEach(c -> {
                if (c instanceof Scalar s) {
                    out.add(s.text());
                }
            });
            where.add(at(codes.keyLine(), pointer + "/" + key(policy, "retriable_status_codes")));
        }
        if (where.isEmpty()) {
            where.add(at(policy.line(), pointer) + ": no retry_on, so no condition is retried");
        }
        return Val.explicit(Set.copyOf(out), String.join(", ", where));
    }

    /** ADR 0014, as for a VirtualService: a status code is a condition of its own. */
    private Val<Set<FailureClass>> retried(Val<Set<String>> conditions) {
        return kb.retried(ISTIO_KB, conditions, c -> c.matches("\\d{3}")
                ? FailureClass.status(Integer.parseInt(c)).map(Set::of).orElse(Set.of()) : null);
    }

    // ---- JSON access -----------------------------------------------------

    private static String type(MapNode m) {
        return field(m, "@type") instanceof Scalar s ? s.text() : "";
    }

    /** The key as the dump spells it: {@code snake} or its lowerCamelCase form. */
    private static String key(MapNode m, String snake) {
        if (m.entries().containsKey(snake)) {
            return snake;
        }
        String camel = camel(snake);
        return m.entries().containsKey(camel) ? camel : snake;
    }

    private static String camel(String snake) {
        StringBuilder b = new StringBuilder();
        boolean up = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                up = true;
            } else {
                b.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return b.toString();
    }

    private static Entry entry(MapNode m, String snake) {
        return m.entries().get(key(m, snake));
    }

    private static Node field(MapNode m, String snake) {
        Entry e = entry(m, snake);
        return e == null || e.value() instanceof Scalar s && s.token() == JsonToken.VALUE_NULL ? null : e.value();
    }

    private static List<Node> items(MapNode m, String snake) {
        return field(m, snake) instanceof ListNode l ? l.items() : List.of();
    }

    private String at(int line, String pointer) {
        return file + ":" + line + " (" + pointer + ")";
    }

    private int integer(Entry e, String pointer) {
        if (e.value() instanceof Scalar s && s.text().matches("\\d+")) {
            return Integer.parseInt(s.text());
        }
        throw new InvalidSpecException(at(e.keyLine(), pointer) + ": expected a non-negative integer");
    }

    /** A protobuf JSON duration: seconds with an optional fraction and the suffix {@code s}, such as {@code 0.250s}. */
    private static Duration duration(Entry e, String where) {
        String text = e.value() instanceof Scalar s ? s.text().strip() : "";
        if (!PROTO_DURATION.matcher(text).matches() || text.startsWith("-")) {
            throw new InvalidSpecException(where + ": invalid duration '" + text + "' (expected e.g. 0.250s)");
        }
        BigDecimal seconds = new BigDecimal(text.substring(0, text.length() - 1));
        return Duration.ofNanos(seconds.movePointRight(9).longValueExact());
    }

    private static Duration kbDuration(String text) {
        return Durations.parse(text).orElseThrow(() -> new InvalidSpecException("kb/defaults/envoy.yaml: invalid "
                + "duration '" + text + "'"));
    }
}
