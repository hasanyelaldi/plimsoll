package io.github.hasanyelaldi.plimsoll.collect.effective;

import io.github.hasanyelaldi.plimsoll.collect.effective.ConfigPropsDump.Dump;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties.Property;
import io.github.hasanyelaldi.plimsoll.model.CircuitBreaker;
import io.github.hasanyelaldi.plimsoll.model.ConfigDifference;
import io.github.hasanyelaldi.plimsoll.model.Durations;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * ADR 0016: where the graph that runs departs from the declared one. Both graphs are collected from the same
 * directory, the running one with the service's dump applied, so the comparison is on the values the rules use:
 * for each edge from the service, layer by layer, its tries, timeouts, backoff, retry conditions and breaker. An edge
 * only one of the graphs has is a difference too. Values that print the same are equal, whatever their evidence.
 */
public final class EffectiveDiff {

    private EffectiveDiff() {}

    /** The differences on the edges from {@code service}. */
    public static List<ConfigDifference> between(ServiceGraph declared, ServiceGraph running, String service) {
        Map<String, Edge> before = edges(declared, service);
        Map<String, Edge> after = edges(running, service);
        List<ConfigDifference> out = new ArrayList<>();
        for (Map.Entry<String, Edge> e : after.entrySet()) {
            Edge d = before.get(e.getKey());
            String where = running.location(e.getValue());
            if (d == null) {
                out.add(new ConfigDifference(service, where, "no edge", Source.UNKNOWN,
                        "the declared configuration has no " + where, "an edge with layers " + kinds(e.getValue()),
                        firstEvidence(e.getValue())));
                continue;
            }
            List<Layer> dl = d.layers();
            List<Layer> rl = e.getValue().layers();
            if (!kinds(d).equals(kinds(e.getValue()))) {
                out.add(new ConfigDifference(service, where + " layers", kinds(d), Source.EXPLICIT,
                        firstEvidence(d), kinds(e.getValue()), firstEvidence(e.getValue())));
                continue;
            }
            for (int i = 0; i < rl.size(); i++) {
                layer(out, service, where + " layer " + (i + 1) + " (" + rl.get(i).kind() + ")", dl.get(i), rl.get(i));
            }
        }
        for (Map.Entry<String, Edge> e : before.entrySet()) {
            if (!after.containsKey(e.getKey())) {
                String where = declared.location(e.getValue());
                out.add(new ConfigDifference(service, where, "an edge with layers " + kinds(e.getValue()),
                        Source.EXPLICIT, firstEvidence(e.getValue()), "no edge",
                        "the running configuration of " + service + " has no " + where));
            }
        }
        return out;
    }

    /**
     * Settings under {@link ConfigPropsDump#COMPARED} that a property source set at run time, against the declared
     * ones; no collector reads them.
     */
    public static List<ConfigDifference> compared(String service, Dump dump, SpringProperties declared) {
        List<ConfigDifference> out = new ArrayList<>();
        for (Property p : dump.compared()) {
            String setting = String.join(".", p.path());
            String runs = text(p);
            Optional<Property> d = declared.get(p.path().toArray(String[]::new));
            if (d.isPresent() && text(d.get()).equals(runs)) {
                continue;
            }
            out.add(d.map(x -> new ConfigDifference(service, setting, text(x), Source.EXPLICIT, x.evidence(), runs,
                            p.evidence()))
                    .orElseGet(() -> new ConfigDifference(service, setting, "not set", Source.UNKNOWN,
                            "not in " + String.join(", ", declared.files()), runs, p.evidence())));
        }
        return out;
    }

    private static void layer(List<ConfigDifference> out, String service, String where, Layer d, Layer r) {
        RetryPolicy dr = d.retry();
        RetryPolicy rr = r.retry();
        field(out, service, where, "totalAttempts", dr.totalAttempts(), rr.totalAttempts(), String::valueOf);
        field(out, service, where, "perTryTimeout", dr.perTryTimeout(), rr.perTryTimeout(), Durations::format);
        field(out, service, where, "backoff", dr.initialBackoff(), rr.initialBackoff(), Durations::format);
        field(out, service, where, "multiplier", dr.multiplier(), rr.multiplier(), String::valueOf);
        field(out, service, where, "maxBackoff", dr.maxBackoff(), rr.maxBackoff(), Durations::format);
        field(out, service, where, "jitter", dr.jitter(), rr.jitter(), String::valueOf);
        field(out, service, where, "retryOn", dr.retryOn(), rr.retryOn(), EffectiveDiff::sorted);
        field(out, service, where, "overallTimeout", d.overallTimeout(), r.overallTimeout(), Durations::format);
        field(out, service, where, "retried", d.conditions().retried(), r.conditions().retried(), EffectiveDiff::sorted);
        CircuitBreaker db = d.breaker();
        CircuitBreaker rb = r.breaker();
        if (db == null && rb == null) {
            return;
        }
        if (db == null || rb == null) {
            out.add(new ConfigDifference(service, where + " circuitBreaker", db == null ? "none" : name(db),
                    Source.EXPLICIT, db == null ? "no breaker declared" : db.name().evidence(),
                    rb == null ? "none" : name(rb), rb == null ? "no breaker runs" : rb.name().evidence()));
            return;
        }
        String b = where + " circuitBreaker";
        field(out, service, b, "name", db.name(), rb.name(), String::valueOf);
        field(out, service, b, "position", db.position(), rb.position(), String::valueOf);
        field(out, service, b, "failureRateThreshold", db.failureRateThreshold(), rb.failureRateThreshold(),
                String::valueOf);
        field(out, service, b, "slidingWindowType", db.slidingWindowType(), rb.slidingWindowType(), String::valueOf);
        field(out, service, b, "slidingWindowSize", db.slidingWindowSize(), rb.slidingWindowSize(), String::valueOf);
        field(out, service, b, "minimumNumberOfCalls", db.minimumNumberOfCalls(), rb.minimumNumberOfCalls(),
                String::valueOf);
        field(out, service, b, "waitDurationInOpenState", db.waitDurationInOpenState(), rb.waitDurationInOpenState(),
                Durations::format);
    }

    private static <T> void field(List<ConfigDifference> out, String service, String where, String name, Val<T> d,
            Val<T> r, Function<T, String> format) {
        String declared = d.isKnown() ? format.apply(d.value()) : "UNKNOWN";
        String runs = r.isKnown() ? format.apply(r.value()) : "UNKNOWN";
        if (!declared.equals(runs)) {
            out.add(new ConfigDifference(service, where + " " + name, declared, d.source(), d.evidence(), runs,
                    r.evidence()));
        }
    }

    private static Map<String, Edge> edges(ServiceGraph g, String service) {
        Map<String, Edge> out = new LinkedHashMap<>();
        for (Edge e : g.edges()) {
            if (e.from().equals(service)) {
                out.put(e.to() + " " + e.operation(), e);
            }
        }
        return out;
    }

    private static String kinds(Edge e) {
        return String.join(", ", e.layers().stream().map(Layer::kind).toList());
    }

    private static String firstEvidence(Edge e) {
        return e.layers().isEmpty() ? "an edge without layers" : e.layers().get(0).retry().totalAttempts().evidence();
    }

    private static String name(CircuitBreaker b) {
        return b.name().isKnown() ? b.name().value() : "UNKNOWN";
    }

    private static String sorted(Collection<?> values) {
        return String.join(", ", new TreeSet<>(values.stream().map(String::valueOf).toList()));
    }

    private static String text(Property p) {
        return p.text() != null ? p.text() : String.join(",", p.items());
    }

}
