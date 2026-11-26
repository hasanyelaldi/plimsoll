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
import io.github.hasanyelaldi.plimsoll.model.FailureClass;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.RetryConditions;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Collects Istio traffic policy from Kubernetes manifests: VirtualService HTTP routes and mesh HTTPRoutes (Gateway
 * API) as {@code istio} layers and DestinationRule outlier detection.
 *
 * <p>A VirtualService applies to every caller of its host inside the mesh, so a route is keyed by the callee only;
 * {@link io.github.hasanyelaldi.plimsoll.collect.GraphAssembler} merges it onto edges. {@code attempts} counts
 * retries only and becomes {@code attempts + 1}; a {@code retries} block without {@code attempts} has 0, which turns
 * retries off. Absent fields take Istio's default only through a verified knowledge-base entry, else they are UNKNOWN.
 *
 * <p>ADR 0016: the MeshConfig {@code defaultHttpRetryPolicy} replaces Istio's default policy for a route without
 * {@code retries} and for a host without a VirtualService ({@code route.go}, {@code retry.go} of Istio 1.31). It is
 * read from the {@code istio} ConfigMap ({@code istio-<revision>} too; its {@code data.mesh} text), an
 * {@code IstioOperator}'s {@code spec.meshConfig} and Helm values with a top-level {@code meshConfig}. Several
 * definitions that differ leave the mesh policy UNKNOWN, with a note.
 *
 * <p>ADR 0018: an HTTPRoute whose parent is a Service is read as Istio converts it ({@code conversion.go} of Istio
 * 1.31). Its {@code retry.attempts} counts retries only too; a {@code retry} block without it is UNKNOWN, because the
 * default depends on the installed Gateway API version.
 *
 * <p>Envoy's retry back-off is fully jittered exponential, which no fixed multiplier describes: {@code multiplier}
 * and {@code maxBackoff} stay UNKNOWN. Outlier detection is reported but not modelled by the analysis yet.
 */
public final class IstioCollector {

    private static final String KB = "istio";
    private static final String API_GROUP = "networking.istio.io/";
    private static final String GATEWAY_API_GROUP = "gateway.networking.k8s.io/";
    private static final Pattern GO_DURATION = Pattern.compile("(\\d+(?:\\.\\d+)?)(ns|us|µs|ms|s|m|h)");
    /** {@code name}, {@code name.namespace}, {@code name.namespace.svc}, {@code name.namespace.svc.cluster.local} */
    private static final Pattern CLUSTER_HOST =
            Pattern.compile("([a-z0-9]([-a-z0-9]*[a-z0-9])?)(\\.[a-z0-9-]+(\\.svc(\\.cluster\\.local)?)?)?");

    /**
     * One HTTP route of a VirtualService, or one rule of an HTTPRoute, for one host.
     *
     * @param to       the callee service: the first label of a cluster-local host, else the host as written
     * @param host     the host as resolved by Istio: a short name is qualified with the rule's namespace
     * @param route    the route's {@code name}, else {@code http[i]}
     * @param evidence {@code file:line} of the route
     * @param kind     the resource that declares it
     */
    public record Route(String to, String host, String route, Layer layer, String evidence, Kind kind) {

        public Route(String to, String host, String route, Layer layer, String evidence) {
            this(to, host, route, layer, evidence, Kind.VIRTUAL_SERVICE);
        }
    }

    /** {@code NONE}: no resource; the mesh default as a candidate next to consumer HTTPRoutes (ADR 0018). */
    public enum Kind { VIRTUAL_SERVICE, HTTP_ROUTE, NONE }

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
    /** Hosts with an HTTPRoute, and where the first one is. */
    private final Map<String, String> httpRouteByHost = new TreeMap<>();
    /** Hosts whose HTTPRoutes are all in another namespace than the service. */
    private final Set<String> consumerOnly = new TreeSet<>();
    private IntFunction<String> where;
    /** The MeshConfig {@code defaultHttpRetryPolicy}, or null when none is declared. */
    private MeshPolicy mesh;

    /**
     * A declared {@code defaultHttpRetryPolicy}.
     *
     * @param where    {@code file:line} of a line of the document it was read from
     * @param entry    the {@code defaultHttpRetryPolicy} key
     * @param policy   its value, or null when several definitions disagree
     * @param unknown  why it is UNKNOWN, when several definitions disagree
     */
    private record MeshPolicy(IntFunction<String> where, Entry entry, MapNode policy, String canonical, String unknown) {
        String at() {
            return where.apply(entry.keyLine());
        }
    }

    private record Document(String file, String text, Node node) {
        IntFunction<String> where() {
            return line -> file + ":" + line;
        }
    }

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
        return collect(base, files, Set.of(), kb);
    }

    /** @param lenient those of {@code files} that are noted, not rejected, when they are not valid YAML */
    public static Result collect(Path base, List<Path> files, Set<Path> lenient, DefaultsKb kb) {
        IstioCollector c = new IstioCollector(kb);
        base = base.toAbsolutePath();
        List<Document> docs = new ArrayList<>();
        for (Path p : files) {
            String file = base.relativize(p.toAbsolutePath()).toString().replace('\\', '/');
            String text;
            try {
                text = Files.readString(p, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new InvalidSpecException(file + ": cannot read: " + e.getMessage(), e);
            }
            try {
                for (Node doc : YamlTree.parseAll(text, file)) {
                    docs.add(new Document(file, text, doc));
                }
            } catch (InvalidSpecException e) {
                if (!lenient.contains(p)) {
                    throw e;
                }
                c.notes.add(e.getMessage() + "; its HTTPRoutes are not read");
            }
        }
        // the mesh policy first: it applies to routes of every VirtualService, whichever file it is in
        for (Document d : docs) {
            c.where = d.where();
            c.meshConfig(d);
        }
        for (Document d : docs) {
            c.where = d.where();
            c.document(d.node());
        }
        c.where = line -> "(mesh default)";
        Layer meshDefault = c.meshLayer(c.routeTimeout("no VirtualService for the host"),
                "no VirtualService for the host");
        c.routePrecedence(meshDefault);
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
        if (apiVersion == null || kind == null) {
            return;
        }
        if (apiVersion.startsWith(GATEWAY_API_GROUP)) {
            switch (kind) {
                case "HTTPRoute" -> httpRoute(m);
                case "GRPCRoute" -> grpcRoute(m);
                default -> { }
            }
            return;
        }
        if (!apiVersion.startsWith(API_GROUP)) {
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
                routes.add(new Route(serviceOf(host), host, routeName, layer(route), at(route.line())));
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
            return meshLayer(routeTimeout, "no retries at " + at(route.line()));
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
            totalAttempts = plusOne(kb.defaultOr(KB, "retries.attemptsUnset", Integer::valueOf,
                    "retries at " + at(retriesEntry.keyLine()) + " has no attempts"));
        } else {
            int n = integer(attempts, "attempts");
            totalAttempts = Val.explicit(n + 1, at(attempts.keyLine()) + " (attempts " + n + " + 1)");
        }
        // Istio 1.31 retry.go: attempts 0 removes the retry policy, its per-try timeout with it (#105)
        boolean off = retries != null && Integer.valueOf(1).equals(totalAttempts.value());
        Entry perTryEntry = retries == null ? null : retries.entries().get("perTryTimeout");
        if (off && perTryEntry != null) {
            duration(perTryEntry); // still invalid input when it does not parse
            note(at(perTryEntry.keyLine()) + ": perTryTimeout is not applied; Istio drops the retry policy, and its "
                    + "per-try timeout, when it makes one try (" + totalAttempts.evidence() + ")");
        }

        RetryPolicy retry = new RetryPolicy(
                totalAttempts,
                off ? perTryTimeout(null, "no retry policy with one try (" + totalAttempts.evidence() + ")",
                        routeTimeout) : perTryTimeout(retries, absent, routeTimeout),
                field(retries, "backoff", this::duration,
                        () -> kb.defaultOr(KB, "retries.backoff", t -> parseDuration(t, "kb"), absent)),
                Val.unknown("Envoy back-off is fully jittered exponential; no fixed multiplier"),
                Val.unknown("Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait"),
                kb.defaultOr(KB, "retries.jitter", Boolean::valueOf, "Istio has no jitter setting"),
                field(retries, "retryOn", e -> conditions(text(e.value(), "retryOn")),
                        () -> kb.defaultOr(KB, "retries.retryOn", IstioCollector::conditions, absent)));
        return new Layer("istio", retry, routeTimeout,
                new RetryConditions(retried(retry.retryOn()), kb.givesUpAs(KB)));
    }

    /**
     * ADR 0014: the failure classes the conditions retry. A status code is a condition of its own (Istio adds it to
     * Envoy's retriable status codes).
     */
    private Val<Set<FailureClass>> retried(Val<Set<String>> conditions) {
        return kb.retried(KB, conditions, c -> c.matches("\\d{3}")
                ? FailureClass.status(Integer.parseInt(c)).map(Set::of).orElse(Set.of()) : null);
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

    // ---- HTTPRoute (ADR 0018) --------------------------------------------

    private record ServiceParent(String host, boolean consumer, int line) {}

    private void httpRoute(MapNode r) {
        String name = metadataName(r);
        MapNode spec = map(r, "spec");
        if (spec == null) {
            return;
        }
        List<Node> rules = items(spec, "rules");
        for (ServiceParent parent : serviceParents(spec, name, namespace(r))) {
            List<Route> own = new ArrayList<>();
            for (int i = 0; i < rules.size(); i++) {
                if (!(rules.get(i) instanceof MapNode rule)) {
                    throw new InvalidSpecException(at(rules.get(i).line()) + ": HTTPRoute rule must be a mapping");
                }
                if (!forwards(rule)) {
                    note(at(rule.line()) + ": HTTPRoute " + name + " rules[" + i
                            + "] has no backend with a weight, so it calls no service; not read");
                    continue;
                }
                String ruleName = Optional.ofNullable(optionalText(rule, "name")).orElse("rules[" + i + "]");
                own.add(new Route(serviceOf(parent.host()), parent.host(), name + " " + ruleName,
                        httpRouteLayer(rule), at(rule.line()), Kind.HTTP_ROUTE));
            }
            if (own.isEmpty()) {
                continue;
            }
            if (own.size() > 1) {
                notes.add(at(parent.line()) + ": host " + parent.host() + " has " + own.size()
                        + " rules in HTTPRoute " + name + "; the most specific match applies");
            }
            routes.addAll(own);
            boolean first = httpRouteByHost.putIfAbsent(parent.host(), own.get(0).evidence()) == null;
            if (!parent.consumer()) {
                consumerOnly.remove(parent.host());
            } else {
                notes.add(at(parent.line()) + ": HTTPRoute " + name + " is not in the namespace of " + parent.host()
                        + " (a consumer route); which callers it applies to is not modelled");
                if (first) {
                    consumerOnly.add(parent.host());
                }
            }
        }
    }

    /** The Services an HTTPRoute is attached to, each host once; other parents are noted. */
    private List<ServiceParent> serviceParents(MapNode spec, String name, String namespace) {
        Map<String, ServiceParent> out = new LinkedHashMap<>();
        Integer gatewayLine = null;
        for (Node n : items(spec, "parentRefs")) {
            if (!(n instanceof MapNode ref)) {
                throw new InvalidSpecException(at(n.line()) + ": parentRef must be a mapping");
            }
            String kind = Optional.ofNullable(optionalText(ref, "kind")).orElse("Gateway");
            String group = Optional.ofNullable(optionalText(ref, "group")).orElse("gateway.networking.k8s.io");
            if (!kind.equals("Service") || !(group.isEmpty() || group.equals("core"))) {
                if (kind.equals("Gateway")) {
                    gatewayLine = gatewayLine == null ? n.line() : gatewayLine;
                } else {
                    notes.add(at(n.line()) + ": HTTPRoute " + name + " parent " + kind + " is not read");
                }
                continue;
            }
            String service = optionalText(ref, "name");
            if (service == null) {
                throw new InvalidSpecException(at(n.line()) + ": parentRef has no name");
            }
            String parentNamespace = optionalText(ref, "namespace");
            boolean consumer = parentNamespace != null && !parentNamespace.equals(namespace);
            String host = qualify(service, parentNamespace == null ? namespace : parentNamespace, n.line());
            if (ref.entries().containsKey("port")) {
                notes.add(at(n.line()) + ": HTTPRoute " + name + " is attached to one port of " + host
                        + "; edges have no port, so it is read for every call");
            }
            out.putIfAbsent(host, new ServiceParent(host, consumer, n.line()));
        }
        if (gatewayLine != null && out.isEmpty()) {
            notes.add(at(gatewayLine) + ": HTTPRoute " + name + " binds gateways only, not the mesh; skipped");
        }
        return List.copyOf(out.values());
    }

    private void grpcRoute(MapNode r) {
        MapNode spec = map(r, "spec");
        if (spec != null && items(spec, "parentRefs").stream().anyMatch(n -> n instanceof MapNode ref
                && "Service".equals(optionalText(ref, "kind")))) {
            notes.add(at(r.line()) + ": GRPCRoute " + metadataName(r) + " is not read");
        }
    }

    /** Istio answers 500 (or redirects) without calling a backend when the weights of a rule add up to 0. */
    private boolean forwards(MapNode rule) {
        for (Node n : items(rule, "backendRefs")) {
            Entry weight = n instanceof MapNode ref ? ref.entries().get("weight") : null;
            if (weight == null || integer(weight, "weight") > 0) {
                return true;
            }
        }
        return false;
    }

    /** {@code convertHTTPRoute} of Istio 1.31, then {@code ConvertPolicy} and {@code setTimeout}. */
    private Layer httpRouteLayer(MapNode rule) {
        MapNode timeouts = map(rule, "timeouts");
        Val<Duration> request = timeout(timeouts, "request");
        Val<Duration> backendRequest = timeout(timeouts, "backendRequest");
        Val<Duration> routeTimeout = request != null ? request
                : routeTimeout("no request timeout at " + at(rule.line()));
        Entry retryEntry = rule.entries().get("retry");
        MapNode retry = map(rule, "retry");
        if (retry == null) {
            // without retry, Istio makes backendRequest the route timeout, in place of request
            return meshLayer(isSet(backendRequest) ? backendRequest : routeTimeout,
                    "no retry at " + at(rule.line()));
        }
        String at = at(retryEntry.keyLine());
        String absent = "not set in retry at " + at;
        Entry attempts = retry.entries().get("attempts");
        Val<Integer> totalAttempts;
        if (attempts == null) {
            totalAttempts = Val.unknown("retry at " + at + " has no attempts; its default depends on the installed "
                    + "Gateway API version: " + attemptsDefault("httpRoute.retry.attemptsCrdDefault") + ", "
                    + attemptsDefault("httpRoute.retry.attemptsUnset"));
        } else {
            int n = integer(attempts, "attempts");
            totalAttempts = Val.explicit(n + 1, at(attempts.keyLine()) + " (attempts " + n + " + 1)");
        }
        boolean off = Integer.valueOf(1).equals(totalAttempts.value());
        if (off && isSet(backendRequest)) {
            note(backendRequest.evidence() + ": backendRequest is not applied; Istio makes it the per-try timeout of "
                    + "a retry policy, and retry.attempts 0 removes that policy");
        }
        Val<Duration> perTry = !off && isSet(backendRequest) ? backendRequest
                : perTryTimeout(null, absent, routeTimeout);
        RetryPolicy policy = new RetryPolicy(totalAttempts, perTry,
                field(retry, "backoff", this::duration,
                        () -> kb.defaultOr(KB, "retries.backoff", t -> parseDuration(t, "kb"), absent)),
                Val.unknown("Envoy back-off is fully jittered exponential; no fixed multiplier"),
                Val.unknown("Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait"),
                kb.defaultOr(KB, "retries.jitter", Boolean::valueOf, "Istio has no jitter setting"),
                retryOn(retry, at));
        return new Layer("istio", policy, routeTimeout,
                new RetryConditions(retried(policy.retryOn()), kb.givesUpAs(KB)));
    }

    private String attemptsDefault(String parameter) {
        return kb.lookup(KB, parameter).map(e -> e.value() + " (" + e.evidence() + ")")
                .orElse("UNKNOWN (no verified entry for " + parameter + ")");
    }

    /** The connection conditions Istio always adds, and the listed status codes. */
    private Val<Set<String>> retryOn(MapNode retry, String at) {
        Val<Set<String>> base = kb.defaultOr(KB, "httpRoute.retry.retryOn", IstioCollector::conditions,
                "retry at " + at);
        Entry codes = retry.entries().get("codes");
        if (codes == null || !base.isKnown() || items(retry, "codes").isEmpty()) {
            return base;
        }
        Set<String> all = new TreeSet<>(base.value());
        items(retry, "codes").forEach(n -> all.add(text(n, "codes")));
        return Val.explicit(Set.copyOf(all), at(codes.keyLine()) + "; " + base.evidence());
    }

    /** Null when absent; a zero duration disables the timeout ({@link Durations#UNLIMITED}). */
    private Val<Duration> timeout(MapNode timeouts, String key) {
        Entry e = timeouts == null ? null : timeouts.entries().get(key);
        if (e == null) {
            return null;
        }
        Duration d = duration(e);
        return Val.explicit(d.isZero() ? Durations.UNLIMITED : d, at(e.keyLine()));
    }

    /** Istio ignores a {@code backendRequest} of zero. */
    private static boolean isSet(Val<Duration> timeout) {
        return timeout != null && !timeout.value().equals(Durations.UNLIMITED);
    }

    /** Adds a note once: the rules of an HTTPRoute are read per parent. */
    private void note(String text) {
        if (!notes.contains(text)) {
            notes.add(text);
        }
    }

    /**
     * Which of several resources for one host a proxy uses. A host with consumer routes only gets the mesh default as
     * another candidate: callers the routes do not apply to have it.
     */
    private void routePrecedence(Layer meshDefault) {
        httpRouteByHost.forEach((host, at) -> {
            String vs = virtualServiceByHost.get(host);
            if (vs != null) {
                notes.add(at + ": host " + host + " has this HTTPRoute and a VirtualService at " + vs
                        + "; which one a proxy uses is not modelled");
            } else if (consumerOnly.contains(host)) {
                routes.add(new Route(serviceOf(host), host, "no route", meshDefault, "(mesh default)", Kind.NONE));
            }
        });
    }

    // ---- MeshConfig ------------------------------------------------------

    private static final Pattern MESH_BLOCK = Pattern.compile("\\s*mesh:\\s*\\|[-+]?\\s*(#.*)?");

    /** A {@code defaultHttpRetryPolicy} in an {@code istio} ConfigMap, an IstioOperator or Helm values. */
    private void meshConfig(Document d) {
        if (!(d.node() instanceof MapNode m)) {
            return;
        }
        String apiVersion = optionalScalar(m, "apiVersion");
        String kind = optionalScalar(m, "kind");
        if ("ConfigMap".equals(kind) && "v1".equals(apiVersion)) {
            MapNode metadata = map(m, "metadata");
            String name = metadata == null ? null : optionalScalar(metadata, "name");
            MapNode data = map(m, "data");
            Entry mesh = data == null ? null : data.entries().get("mesh");
            if (name == null || !(name.equals("istio") || name.startsWith("istio-")) || mesh == null
                    || !(mesh.value() instanceof Scalar text)) {
                return;
            }
            // data.mesh is MeshConfig YAML inside a string; with a literal block its line n is the key's line + n
            String[] lines = d.text().split("\\R", -1);
            boolean literal = mesh.keyLine() <= lines.length && MESH_BLOCK.matcher(lines[mesh.keyLine() - 1]).matches();
            String file = d.file();
            int keyLine = mesh.keyLine();
            IntFunction<String> inner = literal ? n -> file + ":" + (keyLine + n)
                    : n -> file + ":" + keyLine + " (data.mesh line " + n + ")";
            Node meshConfig = YamlTree.parse(text.text(), file + " data.mesh");
            if (meshConfig instanceof MapNode mc) {
                meshPolicy(inner, mc, "ConfigMap " + name);
            }
        } else if ("IstioOperator".equals(kind) && apiVersion != null && apiVersion.startsWith("install.istio.io/")) {
            MapNode spec = map(m, "spec");
            MapNode meshConfig = spec == null ? null : map(spec, "meshConfig");
            if (meshConfig != null) {
                meshPolicy(d.where(), meshConfig, "IstioOperator " + metadataName(m));
            }
        } else if (apiVersion == null && kind == null && map(m, "meshConfig") instanceof MapNode meshConfig) {
            meshPolicy(d.where(), meshConfig, "Helm values");
        }
    }

    private void meshPolicy(IntFunction<String> at, MapNode meshConfig, String source) {
        Entry e = meshConfig.entries().get("defaultHttpRetryPolicy");
        if (e == null) {
            return;
        }
        if (!(e.value() instanceof MapNode policy)) {
            throw new InvalidSpecException(at.apply(e.keyLine()) + ": 'defaultHttpRetryPolicy' must be a mapping");
        }
        MeshPolicy found = new MeshPolicy(at, e, policy, canonical(policy), null);
        if (mesh == null) {
            mesh = found;
            return;
        }
        if (mesh.unknown() == null && mesh.canonical().equals(found.canonical())) {
            return;
        }
        String reason = (mesh.unknown() == null ? "defaultHttpRetryPolicy at " + mesh.at() : mesh.unknown())
                + " and " + source + " at " + found.at() + " differ";
        if (mesh.unknown() == null) {
            notes.add(mesh.at() + ": defaultHttpRetryPolicy differs from the one at " + found.at()
                    + "; which one a sidecar gets depends on its revision, so the mesh policy is UNKNOWN");
        } else {
            notes.add(found.at() + ": another defaultHttpRetryPolicy; the mesh policy stays UNKNOWN");
        }
        mesh = new MeshPolicy(mesh.where(), mesh.entry(), null, mesh.canonical(), reason);
    }

    /** Keys sorted, scalars as text: two policies that say the same compare equal. */
    private static String canonical(Node n) {
        return switch (n) {
            case MapNode m -> new TreeMap<>(m.entries()).entrySet().stream()
                    .map(e -> e.getKey() + "=" + canonical(e.getValue().value()))
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
            case ListNode l -> l.items().stream().map(IstioCollector::canonical)
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
            case Scalar sc -> sc.text();
        };
    }

    /**
     * The policy of a route without {@code retries}, or of a host without a VirtualService: the mesh's
     * {@code defaultHttpRetryPolicy} when one is declared, else Istio's default.
     */
    private Layer meshLayer(Val<Duration> routeTimeout, String absent) {
        if (mesh == null) {
            return layer(routeTimeout, null, null, absent);
        }
        if (mesh.policy() == null) {
            String why = absent + "; " + mesh.unknown();
            RetryPolicy retry = new RetryPolicy(Val.unknown(why), Val.unknown(why), Val.unknown(why),
                    Val.unknown("Envoy back-off is fully jittered exponential; no fixed multiplier"),
                    Val.unknown("Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait"),
                    kb.defaultOr(KB, "retries.jitter", Boolean::valueOf, "Istio has no jitter setting"),
                    Val.unknown(why));
            return new Layer("istio", retry, routeTimeout, new RetryConditions(retried(retry.retryOn()),
                    kb.givesUpAs(KB)));
        }
        IntFunction<String> own = where;
        where = mesh.where();
        Layer l;
        try {
            l = layer(routeTimeout, mesh.entry(), mesh.policy(), "not set in defaultHttpRetryPolicy at " + mesh.at());
        } finally {
            where = own;
        }
        Val<Integer> t = l.retry().totalAttempts();
        Val<Integer> tagged = new Val<>(t.value(), t.source(), t.evidence() + "; MeshConfig defaultHttpRetryPolicy, "
                + "applied because " + absent);
        RetryPolicy r = l.retry();
        return new Layer(l.kind(), new RetryPolicy(tagged, r.perTryTimeout(), r.initialBackoff(), r.multiplier(),
                r.maxBackoff(), r.jitter(), r.retryOn()), l.overallTimeout(), l.conditions());
    }

    private String optionalScalar(MapNode m, String key) {
        Entry e = m.entries().get(key);
        return e != null && e.value() instanceof Scalar s && s.token() != JsonToken.VALUE_NULL ? s.text() : null;
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
        outliers.add(new OutlierDetection(serviceOf(host), host, subset,
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

    /** The service a host names: the first label of a cluster-local host, else the host as written. */
    public static String serviceOf(String host) {
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
        return where.apply(line);
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
