package io.github.hasanyelaldi.plimsoll.collect.gateway;

import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringDurations;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties.Property;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Collects the routes of a Spring Cloud Gateway (WebFlux) service: each route to {@code lb://<service>} becomes an
 * edge gateway → service, its {@code Path} predicate the operation, and its Retry filters (the gateway's
 * {@code default-filters} first, then the route's own) {@code spring-cloud-gateway} layers.
 *
 * <p>Filters are read from {@code spring.cloud.gateway.server.webflux.*}, or from the legacy
 * {@code spring.cloud.gateway.*} prefix when the new one is absent, in the expanded ({@code name} / {@code args})
 * and the shortcut ({@code Retry=3,INTERNAL_SERVER_ERROR,GET}) forms.
 *
 * <p>Retry counts retries only: {@code totalAttempts = retries + 1}. Its {@code methods}, {@code statuses},
 * {@code series} and {@code exceptions} go into {@code retryOn} as {@code method POST}, {@code status ...},
 * {@code series ...} and exception class names; the layer is not yet restricted to the methods an operation uses.
 * The per-try timeout is the gateway HTTP client's {@code response-timeout}; the Retry {@code timeout} bounds the
 * whole retry sequence, so it is the layer's overall timeout.
 *
 * <p>CircuitBreaker filters are noted, not modelled. Whether a CircuitBreaker listed before Retry wraps all tries
 * or each try is not checked against the real gateway yet, so it is stated as UNKNOWN in the notes.
 */
public final class GatewayCollector {

    public static final String KIND = "spring-cloud-gateway";
    static final String KB = "spring-cloud-gateway";
    private static final String[] PREFIX = {"spring", "cloud", "gateway", "server", "webflux"};
    private static final String[] LEGACY_PREFIX = {"spring", "cloud", "gateway"};
    private static final String LB = "lb://";

    /** Positional arguments of the Retry shortcut form (RetryGatewayFilterFactory#shortcutFieldOrder). */
    private static final List<String> RETRY_SHORTCUT = List.of("retries", "statuses", "methods",
            "backoff.firstBackoff", "backoff.maxBackoff", "backoff.factor", "backoff.basedOnPreviousValue",
            "jitter.randomFactor", "timeout");
    private static final Set<String> RETRY_ARGS = Set.of("retries", "statuses", "methods", "series", "exceptions",
            "backoff.firstbackoff", "backoff.maxbackoff", "backoff.factor", "backoff.basedonpreviousvalue",
            "jitter.randomfactor", "timeout");

    /**
     * @param service the gateway's name, the {@code from} of every edge
     * @param edges   one per {@code lb://} route, in route order
     * @param notes   configuration that was found but not modelled, in discovery order
     */
    public record Result(String service, List<Edge> edges, List<String> notes) {
        public Result {
            edges = List.copyOf(edges);
            notes = List.copyOf(notes);
        }
    }

    /**
     * One filter as listed.
     *
     * @param args     argument name (relaxed-binding form, dotted for nested ones) to its value
     * @param where    e.g. {@code default-filters[1]} or {@code route vets-service filters[0]}
     * @param evidence {@code file:line} of the filter
     */
    private record Filter(String name, Map<String, Property> args, String where, String evidence) {}

    private final SpringProperties props;
    private final DefaultsKb kb;
    private final String[] prefix;
    private final List<String> notes = new ArrayList<>();

    private GatewayCollector(SpringProperties props, DefaultsKb kb, String[] prefix) {
        this.props = props;
        this.kb = kb;
        this.prefix = prefix;
    }

    /** @param serviceRoot the gateway's module directory, with {@code src/main/resources} */
    public static Result collect(Path serviceRoot) {
        return collect(serviceRoot, DefaultsKb.classpath());
    }

    public static Result collect(Path serviceRoot, DefaultsKb kb) {
        return collect(SpringProperties.forService(serviceRoot), serviceRoot.getFileName().toString(), kb);
    }

    /** @param fallbackName the gateway's name when {@code spring.application.name} is not set */
    public static Result collect(SpringProperties props, String fallbackName, DefaultsKb kb) {
        String[] prefix = PREFIX;
        if (!configures(props, PREFIX) && configures(props, LEGACY_PREFIX)) {
            prefix = LEGACY_PREFIX;
        }
        GatewayCollector c = new GatewayCollector(props, kb, prefix);
        if (prefix == LEGACY_PREFIX) {
            c.notes.add("routes read from the legacy prefix spring.cloud.gateway.*; Spring Cloud Gateway 5 reads "
                    + "spring.cloud.gateway.server.webflux.*");
        } else if (configures(props, LEGACY_PREFIX)) {
            c.notes.add("spring.cloud.gateway.routes / default-filters ignored; "
                    + "spring.cloud.gateway.server.webflux.* is set and is the one read");
        }

        String service;
        Optional<Property> name = props.get("spring", "application", "name");
        if (name.isPresent() && !name.get().isPlaceholder() && name.get().text() != null) {
            service = name.get().text();
        } else {
            service = fallbackName;
            c.notes.add("spring.application.name not set; the gateway is named after its directory, " + fallbackName);
        }
        c.notes.addAll(props.notes());
        return new Result(service, c.edges(service), c.notes);
    }

    private static boolean configures(SpringProperties props, String[] prefix) {
        return !props.indices(path(prefix, "routes")).isEmpty()
                || !props.indices(path(prefix, "default-filters")).isEmpty();
    }

    // ---- routes ----------------------------------------------------------

    private List<Edge> edges(String service) {
        List<Filter> defaults = filters(path(prefix, "default-filters"), "default-filters");
        List<Edge> edges = new ArrayList<>();
        for (int i : props.indices(path(prefix, "routes"))) {
            String[] route = path(prefix, "routes", "[" + i + "]");
            String routeEvidence = evidenceOf(route);
            String id = text(path(route, "id")).orElse("routes[" + i + "]");
            Optional<String> uri = text(path(route, "uri"));
            if (uri.isEmpty() || !uri.get().startsWith(LB)) {
                notes.add(routeEvidence + ": route " + id + " goes to " + uri.orElse("no uri")
                        + ", not to an lb:// service; no edge");
                continue;
            }
            String to = uri.get().substring(LB.length());
            List<Filter> filters = new ArrayList<>(defaults);
            filters.addAll(filters(path(route, "filters"), "route " + id + " filters"));
            edges.add(edge(service, to, id, route, operation(route, id), filters));
        }
        return edges;
    }

    private Edge edge(String service, String to, String id, String[] route, String operation, List<Filter> filters) {
        Val<Duration> perTry = responseTimeout(id, route);
        List<Filter> retries = filters.stream().filter(f -> f.name().equals("Retry")).toList();
        List<Filter> breakers = filters.stream().filter(f -> f.name().equals("CircuitBreaker")).toList();
        for (Filter b : breakers) {
            String breaker = b.args().containsKey("name") ? " '" + b.args().get("name").text() + "'" : "";
            notes.add(b.evidence() + ": CircuitBreaker" + breaker + " (" + b.where() + ") on route " + id
                    + " is not modelled; its TimeLimiter is UNKNOWN");
        }
        if (!breakers.isEmpty() && !retries.isEmpty()) {
            notes.add("route " + id + ": CircuitBreaker and Retry both apply; whether the breaker wraps all tries "
                    + "or each try is UNKNOWN (not checked against the real gateway yet)");
        }
        if (retries.size() > 1) {
            notes.add("route " + id + ": " + retries.size() + " Retry filters; listed default-filters first, "
                    + "their order in the gateway's filter chain is not checked");
        }

        List<Layer> layers = new ArrayList<>();
        for (Filter r : retries) {
            layers.add(retryLayer(r, perTry));
        }
        if (layers.isEmpty()) {
            String why = "no Retry filter on route " + id + " (" + evidenceOf(route) + ") or in default-filters";
            layers.add(new Layer(KIND, noRetry(Val.explicit(1, why), perTry), Val.unknown("no Retry filter")));
        }
        Val<Boolean> idempotent = Val.unknown("route " + id + " (" + evidenceOf(route)
                + ") does not say which HTTP methods it serves");
        return new Edge(service, to, operation, idempotent, 1, layers);
    }

    /** The {@code Path} patterns of the route, or {@code route <id>} when it has none. */
    private String operation(String[] route, String id) {
        List<String> patterns = new ArrayList<>();
        for (int i : props.indices(path(route, "predicates"))) {
            String[] p = path(route, "predicates", "[" + i + "]");
            Optional<Property> shortcut = props.get(p);
            if (shortcut.isPresent() && shortcut.get().text() != null) {
                String text = shortcut.get().text();
                int eq = text.indexOf('=');
                if (eq > 0 && text.substring(0, eq).strip().equals("Path")) {
                    split(text.substring(eq + 1)).forEach(patterns::add);
                }
            } else if (text(path(p, "name")).filter("Path"::equals).isPresent()) {
                String[] args = path(p, "args");
                for (Property a : props.under(args)) {
                    if (a.path().subList(args.length, a.path().size()).stream().noneMatch(s -> s.startsWith("["))) {
                        patterns.addAll(a.values());
                    }
                }
            }
        }
        return patterns.isEmpty() ? "route " + id : String.join(",", patterns);
    }

    // ---- filters ---------------------------------------------------------

    private List<Filter> filters(String[] list, String where) {
        List<Filter> out = new ArrayList<>();
        for (int i : props.indices(list)) {
            String[] f = path(list, "[" + i + "]");
            String here = where + "[" + i + "]";
            Optional<Property> shortcut = props.get(f);
            if (shortcut.isPresent() && shortcut.get().text() != null) {
                out.add(shortcut(shortcut.get(), here));
                continue;
            }
            Optional<String> name = text(path(f, "name"));
            if (name.isEmpty()) {
                throw new InvalidSpecException(evidenceOf(f) + ": filter " + here + " has no name");
            }
            Map<String, Property> args = new LinkedHashMap<>();
            String[] argsPath = path(f, "args");
            for (Property a : props.under(argsPath)) {
                List<String> rest = a.path().subList(argsPath.length, a.path().size());
                if (rest.stream().noneMatch(s -> s.startsWith("["))) {
                    args.put(key(rest), a);
                }
            }
            out.add(new Filter(name.get(), args, here, evidenceOf(f)));
        }
        return out;
    }

    /** {@code Name=a,b,c} (positional) or {@code Name=key=value,key=value}. */
    private Filter shortcut(Property p, String where) {
        String text = p.text().strip();
        int eq = text.indexOf('=');
        String name = eq < 0 ? text : text.substring(0, eq).strip();
        List<String> values = eq < 0 ? List.of() : split(text.substring(eq + 1));
        Map<String, Property> args = new LinkedHashMap<>();
        boolean named = !values.isEmpty() && values.stream().allMatch(v -> v.contains("="));
        for (int i = 0; i < values.size(); i++) {
            String v = values.get(i);
            String argName;
            String argValue;
            if (named) {
                argName = v.substring(0, v.indexOf('=')).strip();
                argValue = v.substring(v.indexOf('=') + 1).strip();
            } else if (name.equals("Retry")) {
                if (i >= RETRY_SHORTCUT.size()) {
                    throw new InvalidSpecException(p.evidence() + ": Retry shortcut takes at most "
                            + RETRY_SHORTCUT.size() + " arguments, got " + values.size());
                }
                argName = RETRY_SHORTCUT.get(i);
                argValue = v;
            } else {
                argName = "_genkey_" + i;
                argValue = v;
            }
            args.put(key(List.of(argName.split("\\."))),
                    new Property(List.of(argName), argValue, null, p.evidence() + " (" + argName + ")"));
        }
        return new Filter(name, args, where, p.evidence());
    }

    // ---- Retry -----------------------------------------------------------

    private Layer retryLayer(Filter f, Val<Duration> perTry) {
        String filter = "Retry (" + f.where() + ", " + f.evidence() + ")";
        for (String arg : f.args().keySet()) {
            if (!RETRY_ARGS.contains(arg)) {
                notes.add(f.evidence() + ": Retry argument '" + arg + "' is not modelled");
            }
        }
        Val<Integer> retries = arg(f, "retries", GatewayCollector::retries, Integer::valueOf);
        Val<Integer> attempts = retries.isKnown()
                ? new Val<>(retries.value() + 1, retries.source(),
                        filter + ": retries " + retries.value() + " + 1; " + retries.evidence())
                : Val.unknown(filter + ": retries not known: " + retries.evidence());

        Val<Duration> initial;
        Val<Double> multiplier;
        Val<Duration> maxBackoff;
        boolean backoff = f.args().keySet().stream().anyMatch(k -> k.startsWith("backoff."));
        if (backoff) {
            initial = arg(f, "backoff.firstbackoff", p -> duration(p), GatewayCollector::kbDuration);
            multiplier = arg(f, "backoff.factor", GatewayCollector::factor, Double::valueOf);
            maxBackoff = Optional.ofNullable(f.args().get("backoff.maxbackoff"))
                    .map(p -> p.isPlaceholder() ? GatewayCollector.<Duration>placeholder(p)
                            : Val.explicit(duration(p), p.evidence()))
                    .orElseGet(() -> Val.unknown(filter + ": backoff without maxBackoff has no cap"));
        } else {
            Optional<DefaultsKb.Entry> off = kb.lookup(KB, "retry.backoff").filter(e -> e.value().equals("disabled"));
            if (off.isPresent()) {
                String why = filter + ": no backoff, retries follow each other without waiting; "
                        + off.get().evidence();
                initial = new Val<>(Duration.ZERO, Source.LIBRARY_DEFAULT, why);
                multiplier = new Val<>(1.0, Source.LIBRARY_DEFAULT, why);
                maxBackoff = new Val<>(Duration.ZERO, Source.LIBRARY_DEFAULT, why);
            } else {
                String why = filter + ": backoff not set; no verified " + KB + " default for retry.backoff";
                initial = Val.unknown(why);
                multiplier = Val.unknown(why);
                maxBackoff = Val.unknown(why);
            }
        }

        Val<Boolean> jitter;
        Property randomFactor = f.args().get("jitter.randomfactor");
        if (randomFactor != null) {
            jitter = randomFactor.isPlaceholder() ? placeholder(randomFactor)
                    : Val.explicit(randomFactor(randomFactor) > 0, randomFactor.evidence());
        } else {
            jitter = kb.lookup(KB, "retry.jitter").filter(e -> e.value().equals("disabled"))
                    .map(e -> new Val<>(false, Source.LIBRARY_DEFAULT, filter + ": jitter not set; " + e.evidence()))
                    .orElseGet(() -> Val.unknown(filter + ": jitter not set; no verified " + KB
                            + " default for retry.jitter"));
        }

        Val<Duration> overall;
        Property timeout = f.args().get("timeout");
        if (timeout != null) {
            overall = timeout.isPlaceholder() ? placeholder(timeout) : Val.explicit(duration(timeout), timeout.evidence());
        } else {
            overall = Val.unknown(filter + ": timeout not set, so the retry sequence is unlimited"
                    + kb.lookup(KB, "retry.timeout").map(e -> "; " + e.evidence()).orElse(""));
        }

        RetryPolicy retry = new RetryPolicy(attempts, perTry, initial, multiplier, maxBackoff, jitter,
                retryOn(f, filter));
        return new Layer(KIND, retry, overall);
    }

    /** Status codes and series (combined with OR), exceptions, and the methods all of them apply to. */
    private Val<Set<String>> retryOn(Filter f, String filter) {
        Set<String> on = new TreeSet<>();
        List<String> evidence = new ArrayList<>();
        Source source = Source.EXPLICIT;
        for (String[] part : List.of(new String[] {"statuses", "status "}, new String[] {"series", "series "},
                new String[] {"exceptions", ""}, new String[] {"methods", "method "})) {
            String arg = part[0];
            Property p = f.args().get(arg);
            if (p != null) {
                if (p.isPlaceholder()) {
                    return placeholder(p);
                }
                p.values().forEach(v -> on.add(part[1] + (arg.equals("exceptions") ? v : v.toUpperCase(Locale.ROOT))));
                evidence.add(p.evidence() + " (" + arg + ")");
                continue;
            }
            Optional<DefaultsKb.Entry> e = kb.lookup(KB, "retry." + arg);
            if (e.isEmpty()) {
                return Val.unknown(filter + ": " + arg + " not set; no verified " + KB + " default for retry." + arg);
            }
            if (!e.get().value().equals("none")) {
                split(e.get().value()).forEach(v -> on.add(part[1] + v));
            }
            evidence.add(arg + " not set; " + e.get().evidence());
            source = Source.LIBRARY_DEFAULT;
        }
        return new Val<>(Set.copyOf(on), source, filter + ": " + String.join("; ", evidence));
    }

    /** The gateway HTTP client's response timeout, which bounds each try. */
    private Val<Duration> responseTimeout(String id, String[] route) {
        Optional<Property> perRoute = props.get(path(route, "metadata", "response-timeout"));
        if (perRoute.isPresent()) {
            notes.add(perRoute.get().evidence() + ": per-route response-timeout of " + id + " is not modelled");
            return Val.unknown("per-route response-timeout of " + id + " is not modelled ("
                    + perRoute.get().evidence() + ")");
        }
        String name = String.join(".", path(prefix, "httpclient", "response-timeout"));
        Optional<Property> global = props.get(path(prefix, "httpclient", "response-timeout"));
        if (global.isEmpty()) {
            return Val.unknown(name + " not set; no verified " + KB + " default for it");
        }
        return global.get().isPlaceholder() ? placeholder(global.get())
                : Val.explicit(duration(global.get()), global.get().evidence() + " (" + name + ")");
    }

    // ---- values ----------------------------------------------------------

    /** A Retry argument, or its default from the knowledge base. */
    private <T> Val<T> arg(Filter f, String name, Function<Property, T> parse, Function<String, T> parseKb) {
        Property p = f.args().get(name);
        if (p != null) {
            return p.isPlaceholder() ? placeholder(p) : Val.explicit(parse.apply(p), p.evidence());
        }
        String kbName = switch (name) {
            case "backoff.firstbackoff" -> "retry.backoff.firstBackoff";
            default -> "retry." + name;
        };
        return kb.defaultOr(KB, kbName, parseKb, name + " not set");
    }

    private Optional<String> text(String[] path) {
        return props.get(path).map(Property::text);
    }

    private String evidenceOf(String[] path) {
        return props.under(path).stream().map(Property::evidence).findFirst()
                .or(() -> props.get(path).map(Property::evidence))
                .orElse(String.join(".", path));
    }

    private static String key(List<String> segments) {
        return String.join(".", segments.stream().map(s -> s.replace("-", "").replace("_", "")
                .toLowerCase(Locale.ROOT)).toList());
    }

    private static String[] path(String[] prefix, String... more) {
        String[] out = new String[prefix.length + more.length];
        System.arraycopy(prefix, 0, out, 0, prefix.length);
        System.arraycopy(more, 0, out, prefix.length, more.length);
        return out;
    }

    private static List<String> split(String text) {
        return List.of(text.split(",")).stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static RetryPolicy noRetry(Val<Integer> attempts, Val<Duration> perTry) {
        return new RetryPolicy(attempts, perTry, Val.unknown("no retry"), Val.unknown("no retry"),
                Val.unknown("no retry"), Val.unknown("no retry"), Val.unknown("no retry"));
    }

    private static <T> Val<T> placeholder(Property p) {
        return Val.unknown(p.evidence() + ": placeholder " + p.text() + " depends on the environment");
    }

    private static int retries(Property p) {
        try {
            int n = Integer.parseInt(scalar(p));
            if (n > 0) {
                return n;
            }
        } catch (NumberFormatException e) {
            // falls through to the error below
        }
        throw invalid(p, "an integer greater than 0");
    }

    private static double factor(Property p) {
        try {
            double v = Double.parseDouble(scalar(p));
            if (v >= 1) {
                return v;
            }
        } catch (NumberFormatException e) {
            // falls through to the error below
        }
        throw invalid(p, "a number of at least 1");
    }

    private static double randomFactor(Property p) {
        try {
            double v = Double.parseDouble(scalar(p));
            if (v >= 0 && v <= 1) {
                return v;
            }
        } catch (NumberFormatException e) {
            // falls through to the error below
        }
        throw invalid(p, "a number between 0 and 1");
    }

    private static Duration duration(Property p) {
        return SpringDurations.parse(scalar(p)).filter(d -> !d.isNegative())
                .orElseThrow(() -> invalid(p, "a duration such as 100ms"));
    }

    private static Duration kbDuration(String text) {
        return SpringDurations.parse(text).orElseThrow(() -> new IllegalStateException("kb duration " + text));
    }

    private static String scalar(Property p) {
        if (p.text() == null) {
            throw invalid(p, "a single value");
        }
        return p.text().strip();
    }

    private static InvalidSpecException invalid(Property p, String expected) {
        return new InvalidSpecException(p.evidence() + ": " + String.join(".", p.path()) + " must be " + expected
                + ", got " + (p.text() != null ? p.text() : p.items()));
    }
}
