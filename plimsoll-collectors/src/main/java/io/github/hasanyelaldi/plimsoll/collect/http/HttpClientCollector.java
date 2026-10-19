package io.github.hasanyelaldi.plimsoll.collect.http;

import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.EdgesHint;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.EdgesHint.Hint;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Collects the HTTP client layer of each edge whose {@code edges-hint.yaml} entry names a {@code client}: one
 * {@code http-client} layer, which {@link io.github.hasanyelaldi.plimsoll.collect.GraphAssembler} puts between the
 * caller's Resilience4j decorators and the Istio sidecar.
 *
 * <ul>
 *   <li>{@code webclient-loadbalanced}: Spring Cloud LoadBalancer retries, read from the service's
 *       {@code spring.cloud.loadbalancer.retry.*} properties. They are off unless {@code retry.enabled} is set to
 *       {@code true}; then a failed try is repeated {@code max-retries-on-same-service-instance} times on the same
 *       instance, and that is repeated on {@code max-retries-on-next-service-instance} further instances. The builder
 *       is assumed to be a plain {@code WebClient.builder()}, whose Reactor Netty connector has no response timeout.
 *   <li>{@code restclient}: no retries. Its request factory, and so its timeout, depends on which HTTP library is on
 *       the classpath, which the configuration does not show: the timeout is UNKNOWN.
 * </ul>
 *
 * Each behaviour is checked against the real libraries by {@code LoadBalancerRetryOracleTest} and
 * {@code HttpClientTimeoutOracleTest}. Per-client LoadBalancer properties
 * ({@code spring.cloud.loadbalancer.clients.<service>.*}), backoff and custom retryable exceptions are not modelled
 * yet: they give UNKNOWN and a note.
 */
public final class HttpClientCollector {

    public static final String KIND = "http-client";
    static final String LB_KB = "spring-cloud-loadbalancer";
    static final String WEB_KB = "spring-web";
    private static final String RETRY = "spring.cloud.loadbalancer.retry.";

    /** Where an edge is: the caller, the callee and the operation, as the collected {@link Edge} names them. */
    public record EdgeKey(String from, String to, String operation) {
        static EdgeKey of(Edge e) {
            return new EdgeKey(e.from(), e.to(), e.operation());
        }
    }

    /**
     * @param layers one per hint entry that names a client, in hint order
     * @param notes  configuration that was found but not modelled, in discovery order
     */
    public record Result(Map<EdgeKey, Layer> layers, List<String> notes) {
        public Result {
            layers = Collections.unmodifiableMap(new LinkedHashMap<>(layers));
            notes = List.copyOf(notes);
        }

        public Optional<Layer> layerFor(Edge e) {
            return Optional.ofNullable(layers.get(EdgeKey.of(e)));
        }
    }

    private final SpringProperties props;
    private final DefaultsKb kb;
    private final List<String> notes = new ArrayList<>();

    private HttpClientCollector(SpringProperties props, DefaultsKb kb) {
        this.props = props;
        this.kb = kb;
    }

    /** @param serviceRoot the service's module directory, with {@code src/main/resources} */
    public static Result collect(Path serviceRoot, Path edgesHint) {
        return collect(serviceRoot, edgesHint, DefaultsKb.classpath());
    }

    public static Result collect(Path serviceRoot, Path edgesHint, DefaultsKb kb) {
        return collect(SpringProperties.forService(serviceRoot),
                EdgesHint.load(edgesHint, edgesHint.getFileName().toString()), kb);
    }

    public static Result collect(SpringProperties props, EdgesHint hint, DefaultsKb kb) {
        HttpClientCollector c = new HttpClientCollector(props, kb);
        Map<EdgeKey, Layer> layers = new LinkedHashMap<>();
        for (Hint h : hint.methods()) {
            if (h.client() == null) {
                continue;
            }
            EdgeKey key = new EdgeKey(hint.service(), h.to(), h.operation() != null ? h.operation() : h.qualifiedMethod());
            Layer layer = switch (h.client().value()) {
                case "webclient-loadbalanced" -> c.loadBalancedWebClient(h);
                case "restclient" -> c.restClient(h);
                default -> throw new IllegalStateException("client " + h.client().value());
            };
            Layer previous = layers.putIfAbsent(key, layer);
            if (previous != null && !previous.equals(layer)) {
                throw new InvalidSpecException(h.evidence() + ": " + key.from() + " -> " + key.to() + " '"
                        + key.operation() + "' is already listed with another client; give each entry its own operation");
            }
        }
        return new Result(layers, c.notes);
    }

    // ---- WebClient + Spring Cloud LoadBalancer ---------------------------

    private Layer loadBalancedWebClient(Hint h) {
        String client = "@LoadBalanced WebClient (" + h.client().evidence() + ")";
        Val<Duration> timeout = webClientResponseTimeout(client);
        Set<String> perClient = props.names("spring", "cloud", "loadbalancer", "clients");
        if (perClient.contains(h.to())) {
            notes.add(h.to() + ": spring.cloud.loadbalancer.clients." + h.to()
                    + ".* found; per-client LoadBalancer properties are not modelled yet, retries of "
                    + h.qualifiedMethod() + " are UNKNOWN");
            return new Layer(KIND, unknownRetry("per-client LoadBalancer properties for " + h.to()
                    + " are not modelled", timeout), timeout);
        }

        Val<Boolean> enabled = setting("enabled", HttpClientCollector::bool, Boolean::valueOf);
        if (!enabled.isKnown()) {
            return new Layer(KIND, unknownRetry("LoadBalancer retry switch not known: " + enabled.evidence(), timeout),
                    timeout);
        }
        if (!enabled.value()) {
            String why = client + ": LoadBalancer retry off; " + enabled.evidence();
            return new Layer(KIND, noRetry(new Val<>(1, enabled.source(), why), timeout), timeout);
        }

        Val<Integer> same = setting("max-retries-on-same-service-instance", HttpClientCollector::retries,
                Integer::valueOf);
        Val<Integer> next = setting("max-retries-on-next-service-instance", HttpClientCollector::retries,
                Integer::valueOf);
        Val<Integer> attempts;
        if (!same.isKnown() || !next.isKnown()) {
            attempts = Val.unknown("LoadBalancer retries not known: " + same.evidence() + "; " + next.evidence());
        } else {
            Source source = same.source() == Source.EXPLICIT && next.source() == Source.EXPLICIT
                    ? Source.EXPLICIT : Source.LIBRARY_DEFAULT;
            attempts = new Val<>((same.value() + 1) * (next.value() + 1), source, client + ": (same instance "
                    + same.value() + " + 1) x (next instance " + next.value() + " + 1); " + same.evidence() + "; "
                    + next.evidence() + "; " + enabled.evidence());
        }

        Val<Boolean> backoff = setting("backoff.enabled", HttpClientCollector::bool, Boolean::valueOf);
        Val<Duration> initial;
        Val<Double> multiplier;
        Val<Duration> maxBackoff;
        Val<Boolean> jitter;
        if (!backoff.isKnown()) {
            String why = "LoadBalancer backoff switch not known: " + backoff.evidence();
            initial = Val.unknown(why);
            multiplier = Val.unknown(why);
            maxBackoff = Val.unknown(why);
            jitter = Val.unknown(why);
        } else if (backoff.value()) {
            notes.add(backoff.evidence() + ": LoadBalancer backoff is not modelled yet, backoff of "
                    + h.qualifiedMethod() + " is UNKNOWN");
            String why = "LoadBalancer backoff is not modelled yet (" + backoff.evidence() + ")";
            initial = Val.unknown(why);
            multiplier = Val.unknown(why);
            maxBackoff = Val.unknown(why);
            jitter = Val.unknown(why);
        } else {
            String why = "no wait between LoadBalancer retries; " + backoff.evidence();
            initial = new Val<>(Duration.ZERO, backoff.source(), why);
            multiplier = new Val<>(1.0, backoff.source(), why);
            maxBackoff = new Val<>(Duration.ZERO, backoff.source(), why);
            jitter = new Val<>(false, backoff.source(), why);
        }
        RetryPolicy retry = new RetryPolicy(attempts, timeout, initial, multiplier, maxBackoff, jitter, retryOn(h));
        return new Layer(KIND, retry, timeout);
    }

    /** Exceptions are retried for every method; status codes for GET only, unless retry-on-all-operations. */
    private Val<Set<String>> retryOn(Hint h) {
        for (String custom : List.of("retryable-exceptions", "retry-on-all-exceptions")) {
            Optional<Property> p = property(custom);
            if (p.isPresent()) {
                notes.add(p.get().evidence() + ": " + RETRY + custom + " is not modelled yet, retry conditions of "
                        + h.qualifiedMethod() + " are UNKNOWN");
                return Val.unknown(RETRY + custom + " is not modelled yet (" + p.get().evidence() + ")");
            }
        }
        Optional<DefaultsKb.Entry> exceptions = kb.lookup(LB_KB, RETRY + "retryable-exceptions");
        if (exceptions.isEmpty()) {
            return Val.unknown("retryable-exceptions not set; no verified " + LB_KB + " default");
        }
        Set<String> on = new TreeSet<>();
        for (String e : exceptions.get().value().split(",")) {
            on.add(e.strip());
        }
        List<String> evidence = new ArrayList<>(List.of("retryable-exceptions not set; " + exceptions.get().evidence()));
        Source source = Source.LIBRARY_DEFAULT;

        Optional<Property> codes = property("retryable-status-codes");
        if (codes.isPresent()) {
            if (codes.get().isPlaceholder()) {
                return placeholder(codes.get());
            }
            Val<Boolean> all = setting("retry-on-all-operations", HttpClientCollector::bool, Boolean::valueOf);
            if (!all.isKnown()) {
                return Val.unknown("retry-on-all-operations not known: " + all.evidence());
            }
            for (String code : codes.get().values()) {
                on.add("status " + code.strip() + (all.value() ? "" : " (GET only)"));
            }
            evidence.add(codes.get().evidence());
            evidence.add(all.evidence());
            source = Source.EXPLICIT;
        } else {
            Optional<DefaultsKb.Entry> none = kb.lookup(LB_KB, RETRY + "retryable-status-codes")
                    .filter(e -> e.value().equals("none"));
            if (none.isEmpty()) {
                return Val.unknown("retryable-status-codes not set; no verified " + LB_KB + " default");
            }
            evidence.add("no retryable status codes; " + none.get().evidence());
        }
        return new Val<>(Set.copyOf(on), source, String.join("; ", evidence));
    }

    private Val<Duration> webClientResponseTimeout(String client) {
        return kb.lookup(WEB_KB, "webclient-builder.reactor-netty.response-timeout")
                .filter(e -> e.value().equals("none"))
                .<Val<Duration>>map(e -> Val.unknown(client + ": no response timeout (WebClient.builder() with "
                        + "Reactor Netty); " + e.evidence()))
                .orElseGet(() -> Val.unknown(client + ": no verified " + WEB_KB
                        + " default for webclient-builder.reactor-netty.response-timeout"));
    }

    // ---- RestClient ------------------------------------------------------

    private Layer restClient(Hint h) {
        String client = "RestClient (" + h.client().evidence() + ")";
        String factories = kb.lookup(WEB_KB, "restclient-builder.request-factory")
                .map(e -> "the first of " + e.value() + " on the classpath (" + e.evidence() + ")")
                .orElse("the HTTP library on the classpath");
        String reactor = kb.lookup(WEB_KB, "restclient-builder.reactor-netty.response-timeout")
                .map(e -> "; with reactor-netty " + e.value() + " (" + e.evidence() + ")")
                .orElse("");
        Val<Duration> timeout = Val.unknown(client + ": response timeout depends on the request factory, "
                + factories + reactor + "; classpath not read");
        return new Layer(KIND, noRetry(Val.explicit(1, client + ": RestClient does not retry"), timeout), timeout);
    }

    // ---- values ----------------------------------------------------------

    /** {@code spring.cloud.loadbalancer.retry.<name>}, or its default from the knowledge base. */
    private <T> Val<T> setting(String name, Function<Property, T> parse, Function<String, T> parseKb) {
        Optional<Property> p = property(name);
        if (p.isPresent()) {
            return p.get().isPlaceholder()
                    ? placeholder(p.get())
                    : Val.explicit(parse.apply(p.get()), p.get().evidence() + " (" + RETRY + name + ")");
        }
        return kb.defaultOr(LB_KB, RETRY + name, parseKb, RETRY + name + " not set");
    }

    private Optional<Property> property(String name) {
        List<String> path = new ArrayList<>(List.of("spring", "cloud", "loadbalancer", "retry"));
        path.addAll(List.of(name.split("\\.")));
        return props.get(path.toArray(String[]::new));
    }

    private static RetryPolicy noRetry(Val<Integer> attempts, Val<Duration> perTry) {
        return new RetryPolicy(attempts, perTry, Val.unknown("no retry"), Val.unknown("no retry"),
                Val.unknown("no retry"), Val.unknown("no retry"), Val.unknown("no retry"));
    }

    private static RetryPolicy unknownRetry(String evidence, Val<Duration> perTry) {
        return new RetryPolicy(Val.unknown(evidence), perTry, Val.unknown(evidence), Val.unknown(evidence),
                Val.unknown(evidence), Val.unknown(evidence), Val.unknown(evidence));
    }

    private static <T> Val<T> placeholder(Property p) {
        return Val.unknown(p.evidence() + ": placeholder " + p.text() + " depends on the environment");
    }

    private static boolean bool(Property p) {
        String t = scalar(p).strip();
        if (t.equalsIgnoreCase("true") || t.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(t);
        }
        throw invalid(p, "true or false");
    }

    private static int retries(Property p) {
        try {
            int n = Integer.parseInt(scalar(p).strip());
            if (n >= 0) {
                return n;
            }
        } catch (NumberFormatException e) {
            // falls through to the error below
        }
        throw invalid(p, "an integer of at least 0");
    }

    private static String scalar(Property p) {
        if (p.text() == null) {
            throw invalid(p, "a single value");
        }
        return p.text();
    }

    private static InvalidSpecException invalid(Property p, String expected) {
        return new InvalidSpecException(p.evidence() + ": " + String.join(".", p.path()) + " must be " + expected
                + ", got " + (p.text() != null ? p.text() : p.items()));
    }
}
