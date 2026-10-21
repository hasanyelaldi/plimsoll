package io.github.hasanyelaldi.plimsoll.collect.resilience4j;

import io.github.hasanyelaldi.plimsoll.collect.http.HttpMethods;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.AnnotationScanner.HttpCall;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.AnnotationScanner.Kind;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.AnnotationScanner.ScannedClass;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.AnnotationScanner.ScannedMethod;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.EdgesHint.Hint;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Collects the Resilience4j layers of one Spring Boot service: {@code application.yml} for the instances,
 * annotations for where they apply, {@code edges-hint.yaml} for which service each annotated method calls.
 *
 * <p>Per edge, one {@code resilience4j} layer. {@code @Retry} gives the retry policy. {@code @TimeLimiter} gives
 * the per-try timeout when its aspect runs inside Retry (the default order), else the overall timeout; it applies
 * only to methods returning {@code CompletionStage}. {@code @CircuitBreaker} is detected and reported in the notes
 * but not modelled yet.
 *
 * <p>The edge is idempotent as the hint says; without {@code idempotent} in the hint, as its HTTP method is per
 * RFC 9110 ({@link HttpMethods}). The method is the one the hint's {@code operation} starts with
 * ({@code GET /owners/{id}}) or the one of the {@code WebClient} / {@code RestClient} calls in the method body.
 * When the two disagree, or a call's method is not a constant, idempotency is UNKNOWN.
 */
public final class Resilience4jCollector {

    private static final Set<String> COMPLETION_STAGES = Set.of("CompletableFuture", "CompletionStage");
    private static final Set<String> REACTIVE = Set.of("Mono", "Flux", "Single", "Maybe", "Completable",
            "Observable", "Flowable");

    /**
     * @param edges   one per hint entry, in hint order
     * @param unmapped annotated classes and methods that no hint entry names, sorted
     * @param notes   configuration that was found but not applied, in discovery order
     */
    public record Result(List<Edge> edges, List<String> unmapped, List<String> notes) {
        public Result {
            edges = List.copyOf(edges);
            unmapped = List.copyOf(unmapped);
            notes = List.copyOf(notes);
        }
    }

    private final Resilience4jConfig config;
    private final DefaultsKb kb;
    private final List<String> notes = new ArrayList<>();

    private Resilience4jCollector(Resilience4jConfig config, DefaultsKb kb) {
        this.config = config;
        this.kb = kb;
    }

    /** @param serviceRoot the service's module directory, with {@code src/main/java} and {@code src/main/resources} */
    public static Result collect(Path serviceRoot, Path edgesHint) {
        return collect(serviceRoot, edgesHint, DefaultsKb.classpath());
    }

    public static Result collect(Path serviceRoot, Path edgesHint, DefaultsKb kb) {
        return collect(serviceRoot, serviceRoot, EdgesHint.load(edgesHint, edgesHint.getFileName().toString()), kb);
    }

    /** @param base file names in evidence and notes are relative to it */
    public static Result collect(Path base, Path serviceRoot, EdgesHint hint, DefaultsKb kb) {
        Path resources = serviceRoot.resolve("src/main/resources");
        SpringProperties props = SpringProperties.forService(base, serviceRoot);

        Resilience4jCollector c = new Resilience4jCollector(Resilience4jConfig.of(props, kb), kb);
        c.noteUnreadConfig(base, resources);
        c.notes.addAll(props.notes());

        List<ScannedClass> classes = AnnotationScanner.scan(base, serviceRoot.resolve("src/main/java"));
        List<Edge> edges = new ArrayList<>();
        Set<String> mapped = new HashSet<>();
        for (Hint h : hint.methods()) {
            edges.add(c.edge(hint.service(), h, classes));
            mapped.add(h.qualifiedMethod());
            mapped.add(h.className());
        }
        return new Result(edges, unmapped(classes, mapped), c.notes);
    }

    private void noteUnreadConfig(Path root, Path resources) {
        if (!Files.isDirectory(resources)) {
            return;
        }
        try (Stream<Path> list = Files.list(resources)) {
            list.map(p -> p.getFileName().toString())
                    .filter(n -> n.matches("application.*\\.(properties|ya?ml)") && !SpringProperties.SERVICE_CONFIG_FILES.contains(n))
                    .sorted()
                    .forEach(n -> notes.add(root.relativize(resources.resolve(n)).toString().replace('\\', '/')
                            + ": not read (only application.yml / application.yaml of the default profile)"));
        } catch (IOException e) {
            throw new InvalidSpecException(resources + ": cannot list: " + e.getMessage(), e);
        }
    }

    private Edge edge(String from, Hint h, List<ScannedClass> classes) {
        ScannedClass cls = classes.stream().filter(c -> c.name().equals(h.className())).findFirst().orElseThrow(() ->
                new InvalidSpecException(h.evidence() + ": class " + h.className() + " not found under src/main/java"));
        List<ScannedMethod> overloads = cls.methods().stream().filter(m -> m.name().equals(h.method())).toList();
        if (overloads.isEmpty()) {
            throw new InvalidSpecException(h.evidence() + ": method " + h.qualifiedMethod() + " not found");
        }
        ScannedMethod method = overloads.get(0);
        Map<Kind, Val<String>> effective = effective(cls, method);
        for (ScannedMethod other : overloads) {
            if (!effective(cls, other).equals(effective) || !other.returnType().equals(method.returnType())) {
                throw new InvalidSpecException(h.evidence() + ": overloads of " + h.qualifiedMethod()
                        + " differ in annotations or return type; cannot tell which one is called");
            }
        }
        String site = cls.file() + ":" + method.line();
        String operation = h.operation() != null ? h.operation() : h.qualifiedMethod();
        List<Layer> layers = effective.isEmpty() ? List.of() : List.of(layer(h, method, effective, site));
        Val<Boolean> idempotent = h.idempotent().isKnown() ? h.idempotent() : idempotency(h, overloads);
        return new Edge(from, h.to(), operation, idempotent, h.fanout(), layers);
    }

    /** From the HTTP method of the hint's operation or of the calls in the method body. */
    private Val<Boolean> idempotency(Hint h, List<ScannedMethod> overloads) {
        List<HttpCall> calls = overloads.stream().flatMap(m -> m.httpCalls().stream()).toList();
        for (HttpCall c : calls) {
            if (!c.method().isKnown()) {
                return Val.unknown(h.idempotent().evidence() + "; " + c.method().evidence());
            }
        }
        Optional<String> declared = HttpMethods.ofOperation(h.operation());
        if (declared.isPresent()) {
            String where = h.evidence() + " operation";
            for (HttpCall c : calls) {
                if (!c.method().value().equals(declared.get())) {
                    notes.add(c.evidence() + ": " + h.qualifiedMethod() + " calls " + c.method().value()
                            + " but its hint operation (" + h.evidence() + ") says " + declared.get());
                    return Val.unknown(h.idempotent().evidence() + "; HTTP method differs: " + declared.get()
                            + " at " + where + ", " + c.method().value() + " at " + c.evidence());
                }
            }
            return HttpMethods.idempotent(declared.get(), where, kb);
        }
        return HttpMethods.all(calls.stream().map(c -> HttpMethods.idempotent(c.method().value(), c.evidence(), kb))
                .toList(), h.idempotent().evidence() + "; no HTTP method: the operation does not start with one and "
                        + h.qualifiedMethod() + " makes no WebClient / RestClient call");
    }

    /** Method annotations win over class annotations, per kind, as in the Resilience4j aspects. */
    private static Map<Kind, Val<String>> effective(ScannedClass cls, ScannedMethod m) {
        Map<Kind, Val<String>> out = new EnumMap<>(Kind.class);
        out.putAll(cls.annotations());
        out.putAll(m.annotations());
        return out;
    }

    private Layer layer(Hint h, ScannedMethod method, Map<Kind, Val<String>> annotations, String site) {
        String where = h.qualifiedMethod() + " (" + site + ")";
        Val<String> retryName = annotations.get(Kind.RETRY);
        Val<String> breakerName = annotations.get(Kind.CIRCUIT_BREAKER);
        Val<String> limiterName = annotations.get(Kind.TIME_LIMITER);

        if (breakerName != null) {
            notes.add(breakerName.evidence() + ": @CircuitBreaker" + named(breakerName) + " on " + h.qualifiedMethod()
                    + " found; circuit breakers are not modelled yet");
        }

        RetryPolicy retry;
        if (retryName == null) {
            retry = noRetry(site + " (no @Retry on " + h.qualifiedMethod() + ")");
        } else if (!retryName.isKnown()) {
            retry = unknownRetry("@Retry instance not known: " + retryName.evidence());
        } else {
            retry = config.retry(retryName.value());
        }

        Val<Duration> perTry = Val.unknown("no @TimeLimiter on " + where);
        Val<Duration> overall = Val.unknown("no @TimeLimiter on " + where);
        if (limiterName != null) {
            Val<Duration> timeout = limiterName.isKnown()
                    ? config.timeLimiterTimeout(limiterName.value())
                    : Val.unknown("@TimeLimiter instance not known: " + limiterName.evidence());
            if (REACTIVE.contains(method.returnType())) {
                notes.add(limiterName.evidence() + ": @TimeLimiter on " + h.qualifiedMethod() + " returning "
                        + method.returnType() + ": reactive types are not verified yet, timeout not used");
                perTry = Val.unknown("@TimeLimiter on a reactive method, not verified: " + limiterName.evidence());
                overall = perTry;
            } else if (!COMPLETION_STAGES.contains(method.returnType())) {
                notes.add(limiterName.evidence() + ": @TimeLimiter on " + h.qualifiedMethod() + " returning "
                        + method.returnType() + ": Resilience4j fails every call (CompletionStage expected)");
            } else if (retryName == null) {
                perTry = timeout;
                overall = timeout;
            } else {
                Val<Integer> retryOrder = config.retryAspectOrder();
                Val<Integer> limiterOrder = config.timeLimiterAspectOrder();
                if (!retryOrder.isKnown() || !limiterOrder.isKnown()) {
                    perTry = Val.unknown("aspect order not known: " + retryOrder.evidence() + "; "
                            + limiterOrder.evidence());
                    overall = perTry;
                } else if (retryOrder.value() < limiterOrder.value()) {
                    perTry = timeout;     // lower order = outer aspect: Retry wraps TimeLimiter
                } else if (retryOrder.value() > limiterOrder.value()) {
                    overall = timeout;
                } else {
                    perTry = Val.unknown("Retry and TimeLimiter have the same aspect order; Spring does not define "
                            + "which wraps the other (" + retryOrder.evidence() + "; " + limiterOrder.evidence() + ")");
                    overall = perTry;
                }
            }
        }
        RetryPolicy withTimeout = new RetryPolicy(retry.totalAttempts(), perTry, retry.initialBackoff(),
                retry.multiplier(), retry.maxBackoff(), retry.jitter(), retry.retryOn());
        return new Layer("resilience4j", withTimeout, overall);
    }

    private static String named(Val<String> name) {
        return name.isKnown() ? "(name = \"" + name.value() + "\")" : "";
    }

    private static RetryPolicy noRetry(String evidence) {
        Val<Duration> none = Val.unknown("no retry");
        return new RetryPolicy(Val.explicit(1, evidence), none, none, Val.unknown("no retry"), none,
                Val.unknown("no retry"), Val.unknown("no retry"));
    }

    private static RetryPolicy unknownRetry(String evidence) {
        return new RetryPolicy(Val.unknown(evidence), Val.unknown(evidence), Val.unknown(evidence),
                Val.unknown(evidence), Val.unknown(evidence), Val.unknown(evidence), Val.unknown(evidence));
    }

    private static List<String> unmapped(List<ScannedClass> classes, Set<String> mapped) {
        List<String> out = new ArrayList<>();
        for (ScannedClass c : classes) {
            if (!c.annotations().isEmpty() && !mapped.contains(c.name())) {
                out.add(c.name() + " (" + c.file() + ":" + c.line() + ", class-level " + kinds(c.annotations()) + ")");
            }
            for (ScannedMethod m : c.methods()) {
                String qualified = c.name() + "#" + m.name();
                if (!m.annotations().isEmpty() && !mapped.contains(qualified)) {
                    out.add(qualified + " (" + c.file() + ":" + m.line() + ", " + kinds(m.annotations()) + ")");
                }
            }
        }
        return out.stream().distinct().sorted().toList();
    }

    private static String kinds(Map<Kind, Val<String>> annotations) {
        return String.join(" ", annotations.keySet().stream().map(k -> "@" + k.simpleName).toList());
    }
}
