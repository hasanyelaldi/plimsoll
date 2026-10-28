package io.github.hasanyelaldi.plimsoll.collect.istio;

import com.fasterxml.jackson.core.JsonToken;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.io.YamlTree;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Entry;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.ListNode;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.MapNode;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Node;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Scalar;
import io.github.hasanyelaldi.plimsoll.model.Durations;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Collects Istio traffic policy from Kubernetes manifests: VirtualService HTTP routes as {@code istio} layers and
 * DestinationRule outlier detection.
 *
 * <p>A VirtualService applies to every caller of its host inside the mesh, so a route is keyed by the callee only;
 * {@link io.github.hasanyelaldi.plimsoll.collect.GraphAssembler} merges it onto edges. {@code attempts} counts
 * retries only and becomes {@code attempts + 1}. Absent fields take Istio's default only through a verified knowledge-base entry, else they are UNKNOWN. The
 * defaults assume no MeshConfig {@code defaultHttpRetryPolicy} override, which manifests do not show.
 *
 * <p>Envoy's retry back-off is fully jittered exponential, which no fixed multiplier describes: {@code multiplier}
 * and {@code maxBackoff} stay UNKNOWN. Outlier detection is reported but not modelled by the analysis yet.
 */
public final class IstioCollector {

    private static final String KB = "istio";
    private static final String API_GROUP = "networking.istio.io/";
    private static final Pattern GO_DURATION = Pattern.compile("(\\d+(?:\\.\\d+)?)(ns|us|µs|ms|s|m|h)");
    /** {@code name}, {@code name.namespace}, {@code name.namespace.svc}, {@code name.namespace.svc.cluster.local} */
    private static final Pattern CLUSTER_HOST =
            Pattern.compile("([a-z0-9]([-a-z0-9]*[a-z0-9])?)(\\.[a-z0-9-]+(\\.svc(\\.cluster\\.local)?)?)?");

    /**
     * One HTTP route of a VirtualService for one host.
     *
     * @param to       the callee service: the first label of a cluster-local host, else the host as written
     * @param host     the host as resolved by Istio: a short name is qualified with the rule's namespace
     * @param route    the route's {@code name}, else {@code http[i]}
     * @param evidence {@code file:line} of the route
     */
    public record Route(String to, String host, String route, Layer layer, String evidence) {}

    /**
     * DestinationRule outlier detection for one host, at the top level ({@code subset} null) or for one subset.
     */
    public record OutlierDetection(String to, String host, String subset, Val<Integer> consecutive5xxErrors,
            Val<Integer> consecutiveGatewayErrors, Val<Duration> interval, Val<Duration> baseEjectionTime,
            Val<Integer> maxEjectionPercent, String evidence) {}

    /**
     * @param routes            in file order, then document order, then host and route order
     * @param outlierDetections in the same order
     * @param meshDefault       the policy sidecars apply to a host that no VirtualService names
     * @param notes             configuration that was found but not applied, in discovery order
     */
    public record Result(List<Route> routes, List<OutlierDetection> outlierDetections, Layer meshDefault,
            List<String> notes) {
        public Result {
            routes = List.copyOf(routes);
            outlierDetections = List.copyOf(outlierDetections);
            notes = List.copyOf(notes);
        }
    }

    private final DefaultsKb kb;
    private final List<Route> routes = new ArrayList<>();
    private final List<OutlierDetection> outliers = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final Map<String, String> virtualServiceByHost = new HashMap<>();
    private String file;

    private IstioCollector(DefaultsKb kb) {
        this.kb = kb;
    }

    /** @param manifests a manifest file, or a directory searched recursively for {@code *.yaml} and {@code *.yml} */
    public static Result collect(Path manifests) {
        return collect(manifests, DefaultsKb.classpath());
    }

    public static Result collect(Path manifests, DefaultsKb kb) {
        Path base = Files.isDirectory(manifests) ? manifests : manifests.toAbsolutePath().getParent();
        return collect(base, files(manifests), kb);
    }

    /**
     * @param base  evidence paths are relative to it
     * @param files manifest files, read in this order
     */
    public static Result collect(Path base, List<Path> files, DefaultsKb kb) {
        IstioCollector c = new IstioCollector(kb);
        base = base.toAbsolutePath();
        for (Path p : files) {
            c.file = base.relativize(p.toAbsolutePath()).toString().replace('\\', '/');
            String text;
            try {
                text = Files.readString(p, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new InvalidSpecException(c.file + ": cannot read: " + e.getMessage(), e);
            }
            for (Node doc : YamlTree.parseAll(text, c.file)) {
                c.document(doc);
            }
        }
        Layer meshDefault = c.layer(c.routeTimeout("no VirtualService for the host"), null, null,
                "no VirtualService for the host");
        return new Result(c.routes, c.outliers, meshDefault, c.notes);
    }

    private static List<Path> files(Path manifests) {
        if (!Files.isDirectory(manifests)) {
            return List.of(manifests.toAbsolutePath());
        }
        try (Stream<Path> walk = Files.walk(manifests)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches(".*\\.ya?ml"))
                    .map(Path::toAbsolutePath)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new InvalidSpecException(manifests + ": cannot list: " + e.getMessage(), e);
        }
    }

    private void document(Node doc) {
        if (!(doc instanceof MapNode m)) {
            return;
        }
        String apiVersion = optionalText(m, "apiVersion");
        String kind = optionalText(m, "kind");
        if (apiVersion == null || !apiVersion.startsWith(API_GROUP) || kind == null) {
            return;
        }
        switch (kind) {
            case "VirtualService" -> virtualService(m);
            case "DestinationRule" -> destinationRule(m);
            default -> { }
        }
    }

    // ---- VirtualService --------------------------------------------------

    private void virtualService(MapNode vs) {
        String name = metadataName(vs);
        String namespace = namespace(vs);
        MapNode spec = map(vs, "spec");
        if (spec == null) {
            return;
        }
        if (!appliesToMesh(spec)) {
            notes.add(at(spec.entries().get("gateways").keyLine()) + ": VirtualService " + name + " binds gateways only, not the mesh; skipped");
            return;
        }
        for (String key : List.of("tcp", "tls")) {
            Entry e = spec.entries().get(key);
            if (e != null) {
                notes.add(at(e.keyLine()) + ": VirtualService " + name + " " + key
                        + " routes have no retries or timeouts; not read");
            }
        }
        List<Node> http = items(spec, "http");
        for (Node hostNode : items(spec, "hosts")) {
            String written = text(hostNode, "hosts");
            if (written.contains("*")) {
                notes.add(at(hostNode.line()) + ": VirtualService " + name + " wildcard host " + written
                        + " matches services that manifests do not name; not read");
                continue;
            }
            String host = qualify(written, namespace, hostNode.line());
            String earlier = virtualServiceByHost.putIfAbsent(host, at(hostNode.line()));
            if (earlier != null) {
                notes.add(at(hostNode.line()) + ": host " + host + " also has a VirtualService at " + earlier
                        + "; Istio does not define which one sidecars use");
            }
            if (http.size() > 1) {
                notes.add(at(hostNode.line()) + ": host " + host + " has " + http.size()
                        + " HTTP routes; the first one whose match fits a request applies");
            }
            for (int i = 0; i < http.size(); i++) {
                if (!(http.get(i) instanceof MapNode route)) {
                    throw new InvalidSpecException(at(http.get(i).line()) + ": http route must be a mapping");
                }
                String routeName = Optional.ofNullable(optionalText(route, "name")).orElse("http[" + i + "]");
                routes.add(new Route(service(host), host, routeName, layer(route), at(route.line())));
            }
        }
    }

    private static boolean appliesToMesh(MapNode spec) {
        Entry gateways = spec.entries().get("gateways");
        if (gateways == null || !(gateways.value() instanceof ListNode list) || list.items().isEmpty()) {
            return true;
        }
        return list.items().stream().anyMatch(n -> n instanceof Scalar s && s.text().equals("mesh"));
    }

    private Layer layer(MapNode route) {
        Entry timeoutEntry = route.entries().get("timeout");
        Val<Duration> routeTimeout = timeoutEntry == null
                ? routeTimeout("no route timeout at " + at(route.line()))
                : Val.explicit(duration(timeoutEntry), at(timeoutEntry.keyLine()));

        Entry retriesEntry = route.entries().get("retries");
        if (retriesEntry == null) {
            return layer(routeTimeout, null, null, "no retries at " + at(route.line()));
        }
        if (!(retriesEntry.value() instanceof MapNode retries)) {
            throw new InvalidSpecException(at(retriesEntry.keyLine()) + ": 'retries' must be a mapping");
        }
        return layer(routeTimeout, retriesEntry, retries, "not set in retries at " + at(retriesEntry.keyLine()));
    }

    /** {@code retries} null: no retry block, every field from the knowledge base; {@code absent} says why. */
    private Layer layer(Val<Duration> routeTimeout, Entry retriesEntry, MapNode retries, String absent) {
        Val<Integer> totalAttempts;
        Entry attempts = retries == null ? null : retries.entries().get("attempts");
        if (retries == null) {
            totalAttempts = plusOne(kb.defaultOr(KB, "retries.attempts", Integer::valueOf, absent));
        } else if (attempts == null) {
            totalAttempts = Val.unknown("retries at " + at(retriesEntry.keyLine())
                    + " has no attempts; the Istio reference does not define this case");
        } else {
            int n = integer(attempts, "attempts");
            totalAttempts = Val.explicit(n + 1, at(attempts.keyLine()) + " (attempts " + n + " + 1)");
        }

        RetryPolicy retry = new RetryPolicy(
                totalAttempts,
                perTryTimeout(retries, absent, routeTimeout),
                field(retries, "backoff", this::duration,
                        () -> kb.defaultOr(KB, "retries.backoff", t -> parseDuration(t, "kb"), absent)),
                Val.unknown("Envoy back-off is fully jittered exponential; no fixed multiplier"),
                Val.unknown("Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait"),
                kb.defaultOr(KB, "retries.jitter", Boolean::valueOf, "Istio has no jitter setting"),
                field(retries, "retryOn", e -> conditions(text(e.value(), "retryOn")),
                        () -> kb.defaultOr(KB, "retries.retryOn", IstioCollector::conditions, absent)));
        return new Layer("istio", retry, routeTimeout);
    }

    /** The route timeout Istio applies when none is set: none (ADR 0011), from the knowledge base. */
    private Val<Duration> routeTimeout(String absent) {
        return kb.defaultOr(KB, "timeout", t -> Durations.parseTimeout(t).orElseGet(() -> parseDuration(t, "kb")),
                absent);
    }

    /** Istio: per-try timeout defaults to the route timeout, which defaults to none. */
    private Val<Duration> perTryTimeout(MapNode retries, String absent, Val<Duration> routeTimeout) {
        Entry e = retries == null ? null : retries.entries().get("perTryTimeout");
        if (e != null) {
            return Val.explicit(duration(e), at(e.keyLine()));
        }
        Optional<DefaultsKb.Entry> rule = kb.lookup(KB, "retries.perTryTimeout")
                .filter(entry -> entry.value().equals("route timeout"));
        if (rule.isEmpty()) {
            return Val.unknown(absent + "; no verified istio default for retries.perTryTimeout");
        }
        if (!routeTimeout.isKnown()) {
            return Val.unknown("no per-try timeout: " + absent + " and " + routeTimeout.evidence() + "; "
                    + rule.get().evidence());
        }
        return new Val<>(routeTimeout.value(), Source.LIBRARY_DEFAULT, "the route timeout (" + routeTimeout.evidence()
                + "); perTryTimeout: " + absent + "; " + rule.get().evidence());
    }

    private <T> Val<T> field(MapNode retries, String key, Function<Entry, T> parse, Supplier<Val<T>> fallback) {
        Entry e = retries == null ? null : retries.entries().get(key);
        return e == null ? fallback.get() : Val.explicit(parse.apply(e), at(e.keyLine()));
    }

    private static Val<Integer> plusOne(Val<Integer> retries) {
        if (!retries.isKnown()) {
            return retries;
        }
        return new Val<>(retries.value() + 1, retries.source(),
                retries.evidence() + " (attempts " + retries.value() + " + 1)");
    }

    private static Set<String> conditions(String text) {
        Set<String> out = new TreeSet<>();
        Arrays.stream(text.split(",")).map(String::strip).filter(s -> !s.isEmpty()).forEach(out::add);
        return Set.copyOf(out);
    }

    // ---- DestinationRule -------------------------------------------------

    private void destinationRule(MapNode dr) {
        String name = metadataName(dr);
        MapNode spec = map(dr, "spec");
        if (spec == null) {
            return;
        }
        Entry hostEntry = spec.entries().get("host");
        if (hostEntry == null) {
            throw new InvalidSpecException(at(spec.line()) + ": DestinationRule " + name + " has no host");
        }
        String written = text(hostEntry.value(), "host");
        if (written.contains("*")) {
            notes.add(at(hostEntry.keyLine()) + ": DestinationRule " + name + " wildcard host " + written
                    + " matches services that manifests do not name; not read");
            return;
        }
        String host = qualify(written, namespace(dr), hostEntry.keyLine());
        trafficPolicy(map(spec, "trafficPolicy"), host, null);
        for (Node n : items(spec, "subsets")) {
            if (n instanceof MapNode subset) {
                trafficPolicy(map(subset, "trafficPolicy"), host, optionalText(subset, "name"));
            }
        }
    }

    private void trafficPolicy(MapNode policy, String host, String subset) {
        if (policy == null) {
            return;
        }
        String where = host + (subset == null ? "" : " subset " + subset);
        Entry portLevel = policy.entries().get("portLevelSettings");
        if (portLevel != null) {
            notes.add(at(portLevel.keyLine()) + ": portLevelSettings for " + where
                    + " override the destination-level policy per port; not read");
        }
        Entry connectionPool = policy.entries().get("connectionPool");
        if (connectionPool != null) {
            notes.add(at(connectionPool.keyLine()) + ": connectionPool for " + where + " not modelled yet");
        }
        Entry od = policy.entries().get("outlierDetection");
        if (od == null) {
            return;
        }
        if (!(od.value() instanceof MapNode m)) {
            throw new InvalidSpecException(at(od.keyLine()) + ": 'outlierDetection' must be a mapping");
        }
        String absent = "not set in outlierDetection at " + at(od.keyLine());
        outliers.add(new OutlierDetection(service(host), host, subset,
                outlierField(m, "consecutive5xxErrors", e -> integer(e, "consecutive5xxErrors"), Integer::valueOf, absent),
                outlierField(m, "consecutiveGatewayErrors", e -> integer(e, "consecutiveGatewayErrors"),
                        Integer::valueOf, absent),
                outlierField(m, "interval", this::duration, t -> parseDuration(t, "kb"), absent),
                outlierField(m, "baseEjectionTime", this::duration, t -> parseDuration(t, "kb"), absent),
                outlierField(m, "maxEjectionPercent", e -> integer(e, "maxEjectionPercent"), Integer::valueOf, absent),
                at(od.keyLine())));
        notes.add(at(od.keyLine()) + ": outlierDetection for " + where + " found; circuit breaking is not analysed yet");
    }

    private <T> Val<T> outlierField(MapNode m, String key, Function<Entry, T> parse, Function<String, T> kbParse,
            String absent) {
        Entry e = m.entries().get(key);
        return e == null
                ? kb.defaultOr(KB, "outlierDetection." + key, kbParse, absent)
                : Val.explicit(parse.apply(e), at(e.keyLine()));
    }

    // ---- hosts -----------------------------------------------------------

    /** Istio reads a short name in the namespace of the rule, not of the service. */
    private String qualify(String written, String namespace, int line) {
        if (written.contains(".")) {
            return written;
        }
        if (namespace == null) {
            notes.add(at(line) + ": short host " + written + " in a rule without metadata.namespace; "
                    + "its namespace is the one it is applied to, not known here");
            return written;
        }
        return written + "." + namespace + ".svc.cluster.local";
    }

    private static String service(String host) {
        Matcher m = CLUSTER_HOST.matcher(host);
        return m.matches() ? m.group(1) : host;
    }

    private String metadataName(MapNode m) {
        MapNode metadata = map(m, "metadata");
        String name = metadata == null ? null : optionalText(metadata, "name");
        return name == null ? "(unnamed)" : name;
    }

    private String namespace(MapNode m) {
        MapNode metadata = map(m, "metadata");
        return metadata == null ? null : optionalText(metadata, "namespace");
    }

    // ---- typed accessors -------------------------------------------------

    private String at(int line) {
        return file + ":" + line;
    }

    private MapNode map(MapNode m, String key) {
        Entry e = m.entries().get(key);
        if (e == null || e.value() instanceof Scalar s && s.token() == JsonToken.VALUE_NULL) {
            return null;
        }
        if (!(e.value() instanceof MapNode child)) {
            throw new InvalidSpecException(at(e.keyLine()) + ": '" + key + "' must be a mapping");
        }
        return child;
    }

    private List<Node> items(MapNode m, String key) {
        Entry e = m.entries().get(key);
        if (e == null || e.value() instanceof Scalar s && s.token() == JsonToken.VALUE_NULL) {
            return List.of();
        }
        if (!(e.value() instanceof ListNode list)) {
            throw new InvalidSpecException(at(e.keyLine()) + ": '" + key + "' must be a list");
        }
        return list.items();
    }

    private String optionalText(MapNode m, String key) {
        Entry e = m.entries().get(key);
        return e == null ? null : text(e.value(), key);
    }

    private String text(Node n, String key) {
        if (!(n instanceof Scalar s) || s.token() == JsonToken.VALUE_NULL) {
            throw new InvalidSpecException(at(n.line()) + ": '" + key + "' must be a scalar");
        }
        return s.text();
    }

    private int integer(Entry e, String key) {
        if (!(e.value() instanceof Scalar s) || s.token() != JsonToken.VALUE_NUMBER_INT) {
            throw new InvalidSpecException(at(e.keyLine()) + ": '" + key + "' must be an integer");
        }
        int n = Integer.parseInt(s.text());
        if (n < 0) {
            throw new InvalidSpecException(at(e.keyLine()) + ": '" + key + "' must be >= 0");
        }
        return n;
    }

    private Duration duration(Entry e) {
        return parseDuration(text(e.value(), "duration"), at(e.keyLine()));
    }

    /** Istio durations are Go durations: {@code 1h}, {@code 1m30s}, {@code 250ms}, {@code 0.5s}. */
    static Duration parseDuration(String text, String where) {
        String s = text.strip();
        Matcher m = GO_DURATION.matcher(s);
        Duration total = Duration.ZERO;
        int end = 0;
        while (m.find() && m.start() == end) {
            BigDecimal nanosPerUnit = BigDecimal.valueOf(switch (m.group(2)) {
                case "ns" -> 1L;
                case "us", "µs" -> 1_000L;
                case "ms" -> 1_000_000L;
                case "s" -> 1_000_000_000L;
                case "m" -> 60_000_000_000L;
                default -> 3_600_000_000_000L;
            });
            total = total.plusNanos(new BigDecimal(m.group(1)).multiply(nanosPerUnit).longValue());
            end = m.end();
        }
        if (end == 0 || end != s.length()) {
            throw new InvalidSpecException(where + ": invalid duration '" + text + "' (expected e.g. 1h, 1m, 1s, 1ms)");
        }
        return total;
    }
}
