package io.github.hasanyelaldi.plimsoll.collect.springai;

import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringDurations;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties.Property;
import io.github.hasanyelaldi.plimsoll.collect.springai.MavenPom.Dependency;
import io.github.hasanyelaldi.plimsoll.collect.springai.MavenPom.Value;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.EdgesHint;
import io.github.hasanyelaldi.plimsoll.model.Durations;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.FailureClass;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.RetryConditions;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Spring AI retry (#19, ADR 0019): a service with a Spring AI model starter calls a provider outside the system.
 *
 * <p>The OpenAI starter ({@code org.springframework.ai:spring-ai-starter-model-openai} in the module's
 * {@code pom.xml}) at a version whose chat model retries with Spring AI's {@code RetryTemplate}
 * ({@code openai.retry-template-versions} in {@code kb/defaults/spring-ai.yaml}) becomes an edge to the external
 * service {@code openai} with one {@code spring-ai} layer: {@code spring.ai.retry.*} from the service's
 * configuration, else the oracle-verified defaults. {@code max-attempts} counts retries only. The edge's fanout is
 * UNKNOWN (each tool round is another model call); its idempotency comes from an {@code operations} entry of the
 * hint, else UNKNOWN (the provider keeps no state, but the call is a POST and costs money).
 *
 * <p>Other model starters, other versions and versions that cannot be read from the POM are notes; their mechanism
 * stays a PLM-C1 detection. Gradle builds are not read.
 */
public final class SpringAiCollector {

    public static final String KIND = "spring-ai";
    static final String KB = "spring-ai";
    static final String GROUP = "org.springframework.ai";
    static final String STARTER = "spring-ai-starter-model-";
    static final String BOM = "spring-ai-bom";
    private static final List<String> RETRY = List.of("spring", "ai", "retry");

    /**
     * @param edges    at most one per provider modelled
     * @param external the external services the edges go to
     * @param modelled whether a model starter's retry is in the edges, so its PLM-C1 detection can go
     */
    public record Result(List<Edge> edges, Set<String> external, List<String> notes, boolean modelled) {
        public Result {
            edges = List.copyOf(edges);
            external = Set.copyOf(external);
            notes = List.copyOf(notes);
        }

        static final Result NONE = new Result(List.of(), Set.of(), List.of(), false);
    }

    private SpringAiCollector() {}

    public static Result collect(Path root, Path module, String service, SpringProperties props, EdgesHint hint,
            DefaultsKb kb) {
        Optional<MavenPom> pom = MavenPom.read(root, module);
        if (pom.isEmpty()) {
            return Result.NONE;
        }
        List<Edge> edges = new ArrayList<>();
        Set<String> external = new TreeSet<>();
        List<String> notes = new ArrayList<>();
        for (Dependency d : pom.get().dependencies()) {
            if (!GROUP.equals(d.groupId()) || d.artifactId() == null || !d.artifactId().startsWith(STARTER)
                    || "test".equals(d.scope())) {
                continue;
            }
            String provider = d.artifactId().substring(STARTER.length());
            if (!provider.equals("openai")) {
                notes.add(d.evidence() + ": Spring AI model starter " + d.artifactId() + ": its calls to the provider "
                        + "are not modelled (#19 models spring-ai-starter-model-openai)");
                continue;
            }
            Optional<Value> version = version(pom.get(), d);
            if (version.isEmpty()) {
                notes.add(d.evidence() + ": Spring AI version of " + d.artifactId() + " not found in the POM or its "
                        + "parent; its calls to openai are not modelled");
                continue;
            }
            Optional<DefaultsKb.Entry> versions = kb.lookup(KB, "openai.retry-template-versions");
            if (versions.isEmpty() || !List.of(versions.get().value().split(",")).contains(version.get().text())) {
                notes.add(d.evidence() + ": Spring AI " + version.get().text() + " (" + version.get().evidence()
                        + "): how its OpenAI chat model retries is not verified (from 2.0.0-M5 on it uses the "
                        + "OpenAI Java SDK, which retries on its own); its calls to openai are not modelled");
                continue;
            }
            String at = "Spring AI " + version.get().text() + " (" + version.get().evidence() + ")";
            external.add(provider);
            String operation = operation(kb);
            edges.add(new Edge(service, provider, operation, idempotent(hint, provider, operation),
                    Val.unknown("model calls per request: one, plus one per tool round"),
                    List.of(layer(props, kb, at, versions.get()))));
            notes.add(service + "→" + provider + ": " + at + ": OpenAiChatModel.call retries the request with the "
                    + "RetryTemplate; stream() does not retry, and tool calls run after each response, each tool round "
                    + "being another model call (" + versions.get().evidence() + ")");
        }
        return new Result(edges, external, notes, !edges.isEmpty());
    }

    private static Optional<Value> version(MavenPom pom, Dependency d) {
        if (d.version() != null) {
            return pom.resolve(d.version(), d.evidence());
        }
        for (MavenPom p : pom.lineage()) {
            for (Dependency m : p.managed()) {
                if (GROUP.equals(m.groupId()) && (BOM.equals(m.artifactId()) || d.artifactId().equals(m.artifactId()))
                        && m.version() != null) {
                    return pom.resolve(m.version(), m.evidence());
                }
            }
        }
        return Optional.empty();
    }

    private static String operation(DefaultsKb kb) {
        return "POST " + kb.lookup(KB, "openai.completions-path").map(DefaultsKb.Entry::value)
                .orElse("(chat completions)");
    }

    /** An {@code operations} entry of the hint for the provider, else UNKNOWN. */
    private static Val<Boolean> idempotent(EdgesHint hint, String provider, String operation) {
        if (hint != null) {
            for (EdgesHint.OperationHint o : hint.operations()) {
                if (o.to().equals(provider) && o.operation().equals(operation) && o.idempotent().isKnown()) {
                    return o.idempotent();
                }
            }
        }
        return Val.unknown("the provider keeps no state for a chat completion, but the call is a POST and each try "
                + "is billed; say it in an operations entry of the edges-hint.yaml (#19)");
    }

    private static Layer layer(SpringProperties props, DefaultsKb kb, String at, DefaultsKb.Entry wiring) {
        String absent = "not set in spring.ai.retry; " + at;
        Val<Integer> retries = value(props, "max-attempts", absent, kb, "retry.max-attempts", SpringAiCollector::integer);
        Val<Integer> total = retries.isKnown()
                ? new Val<>(retries.value() + 1, retries.source(), retries.evidence() + " (max-attempts "
                        + retries.value() + " + 1: it counts retries only)")
                : retries;
        Val<Duration> initial = value(props, "backoff.initial-interval", absent, kb, "retry.backoff.initial-interval",
                SpringAiCollector::duration);
        Val<Double> multiplier = value(props, "backoff.multiplier", absent, kb, "retry.backoff.multiplier",
                t -> (double) integer(t));
        Val<Duration> max = value(props, "backoff.max-interval", absent, kb, "retry.backoff.max-interval",
                SpringAiCollector::duration);
        Val<Boolean> jitter = kb.defaultOr(KB, "retry.jitter", Boolean::valueOf, "Spring AI sets no jitter");
        Val<Duration> overall = kb.defaultOr(KB, "retry.timeout",
                t -> Durations.parseTimeout(t).orElseThrow(), "Spring AI sets no RetryPolicy timeout");
        Val<Duration> perTry = Val.unknown("the read timeout of the RestClient OpenAiApi uses is not collected ("
                + wiring.evidence() + ")");

        Conditions c = conditions(props, kb);
        RetryPolicy policy = new RetryPolicy(total, perTry, initial, multiplier, max, jitter, c.raw());
        return new Layer(KIND, policy, overall, new RetryConditions(c.classes(), kb.givesUpAs(KB)));
    }

    private record Conditions(Val<Set<String>> raw, Val<Set<FailureClass>> classes) {}

    /**
     * The template retries TransientAiException and ResourceAccessException; the handler decides which statuses
     * become a TransientAiException: {@code on-http-codes}, every 5xx, and the 4xx with {@code on-client-errors},
     * minus {@code exclude-on-http-codes} (an exclusion removes a class only when the class is that one code).
     */
    private static Conditions conditions(SpringProperties props, DefaultsKb kb) {
        Set<String> raw = new TreeSet<>(List.of("ResourceAccessException", "TransientAiException"));
        List<String> evidence = new ArrayList<>();
        Optional<Property> onCodes = props.get("spring", "ai", "retry", "on-http-codes");
        Optional<Property> exclude = props.get("spring", "ai", "retry", "exclude-on-http-codes");
        Optional<Property> clientErrors = props.get("spring", "ai", "retry", "on-client-errors");
        for (Optional<Property> p : List.of(onCodes, exclude, clientErrors)) {
            if (p.isPresent() && p.get().isPlaceholder()) {
                return new Conditions(Val.unknown(p.get().evidence() + ": placeholder"),
                        Val.unknown(p.get().evidence() + ": placeholder; every failure class is retried"));
            }
            p.ifPresent(x -> evidence.add(x.evidence()));
        }
        boolean client = clientErrors.isPresent() && Boolean.parseBoolean(clientErrors.get().text());
        if (client) {
            raw.add("on-client-errors");
        }
        List<Integer> added = onCodes.map(p -> p.values().stream().map(Integer::valueOf).toList()).orElse(List.of());
        List<Integer> excluded = exclude.map(p -> p.values().stream().map(Integer::valueOf).toList()).orElse(List.of());
        added.forEach(code -> raw.add(String.valueOf(code)));
        excluded.forEach(code -> raw.add("not " + code));

        String where = evidence.isEmpty() ? "TransientAiException and ResourceAccessException" : String.join(", ", evidence);
        Val<Set<String>> conditions = evidence.isEmpty()
                ? new Val<>(Collections.unmodifiableSet(raw), Source.LIBRARY_DEFAULT, "Spring AI retries " + where)
                : Val.explicit(Collections.unmodifiableSet(raw), where);
        Val<Set<FailureClass>> classes = kb.retried(KB, conditions, condition -> {
            if (condition.startsWith("not ")) {
                return Set.of();
            }
            return condition.matches("\\d{3}") ? FailureClass.status(Integer.parseInt(condition)).map(Set::of)
                    .orElse(Set.of()) : null;
        });
        if (classes.isKnown() && !excluded.isEmpty()) {
            Set<FailureClass> kept = EnumSet.noneOf(FailureClass.class);
            kept.addAll(classes.value());
            for (int code : excluded) {
                if (!added.contains(code)) {
                    FailureClass.status(code).filter(fc -> isSingleCode(fc, code)).ifPresent(kept::remove);
                }
            }
            classes = new Val<>(Collections.unmodifiableSet(kept), classes.source(), classes.evidence());
        }
        return new Conditions(conditions, classes);
    }

    private static boolean isSingleCode(FailureClass fc, int code) {
        return fc.id().equals("status-" + code);
    }

    private static <T> Val<T> value(SpringProperties props, String key, String absent, DefaultsKb kb,
            String parameter, Function<String, T> parse) {
        List<String> segments = new ArrayList<>(RETRY);
        segments.addAll(List.of(key.split("\\.")));
        String[] path = segments.toArray(String[]::new);
        Optional<Property> p = props.get(path);
        if (p.isEmpty()) {
            return kb.defaultOr(KB, parameter, parse, key + " " + absent);
        }
        if (p.get().isPlaceholder()) {
            return Val.unknown(p.get().evidence() + ": spring.ai.retry." + key + " is a placeholder ("
                    + p.get().text() + ")");
        }
        return Val.explicit(parse.apply(p.get().text()), p.get().evidence());
    }

    private static int integer(String text) {
        return Integer.parseInt(text.strip());
    }

    private static Duration duration(String text) {
        return SpringDurations.parse(text.strip())
                .orElseThrow(() -> new IllegalArgumentException("not a duration: " + text));
    }
}
