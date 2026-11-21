package io.github.hasanyelaldi.plimsoll.collect.circuitbreaker;

import io.github.hasanyelaldi.plimsoll.collect.circuitbreaker.BreakerCallScanner.Wrap;
import io.github.hasanyelaldi.plimsoll.collect.circuitbreaker.SpringCloudCircuitBreakers.Resolved;
import io.github.hasanyelaldi.plimsoll.collect.http.ClientCallScanner.ClientCall;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.model.CircuitBreaker;
import io.github.hasanyelaldi.plimsoll.model.Durations;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.RetryConditions;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The layer a Spring Cloud CircuitBreaker adds to the requests its {@code run} wraps (#18): kind
 * {@code resilience4j}, outside the HTTP client. It runs the call once ({@code SpringCloudCircuitBreakerOracleTest});
 * its TimeLimiter bounds that one try, so it is both the per-try and the overall timeout; it passes the call's error
 * on as it is (KB {@code givesUpAs}); its breaker has the settings {@link SpringCloudCircuitBreakers} resolves for the
 * id. Runs that wrap no request that became an edge are noted.
 */
public final class WrappedCalls {

    public static final String KIND = "resilience4j";
    private static final String KB = SpringCloudCircuitBreakers.KB;

    private final BreakerCallScanner.Result scanned;
    private final SpringCloudCircuitBreakers breakers;
    private final DefaultsKb kb;
    private final Set<Wrap> used = new LinkedHashSet<>();
    private final List<String> notes = new ArrayList<>();

    public WrappedCalls(BreakerCallScanner.Result scanned, SpringCloudCircuitBreakers breakers, DefaultsKb kb) {
        this.scanned = scanned;
        this.breakers = breakers;
        this.kb = kb;
    }

    /** No breaker wraps any call. */
    public static WrappedCalls none(DefaultsKb kb) {
        return new WrappedCalls(new BreakerCallScanner.Result(List.of(), List.of()), null, kb);
    }

    /** The breaker layers around a request that becomes an edge, outermost first; call once per edge made. */
    public List<Layer> layersFor(ClientCall call) {
        List<Wrap> around = scanned.wraps().stream()
                .filter(w -> w.covers(call.qualifiedMethod(), call.evidence())).toList();
        if (around.size() > 1) {
            notes.add(call.evidence() + ": " + around.size() + " Spring Cloud CircuitBreaker runs wrap this request ("
                    + String.join(", ", around.stream().map(Wrap::create).toList())
                    + "); their nesting is not read, so they are listed in the order found");
        }
        used.addAll(around);
        return around.stream().map(w -> layer(w, call)).toList();
    }

    /** The scanner's notes, then runs that wrap no request that became an edge. */
    public List<String> notes() {
        List<String> out = new ArrayList<>(scanned.notes());
        out.addAll(notes);
        for (Wrap w : scanned.wraps()) {
            if (!used.contains(w)) {
                out.add(w.create() + ": CircuitBreaker" + (w.id() == null ? "" : " '" + w.id() + "'") + " wraps "
                        + w.wraps() + ", which makes no request that became an edge; not applied");
            }
        }
        return out;
    }

    private Layer layer(Wrap w, ClientCall call) {
        String where = "Spring Cloud CircuitBreaker" + (w.id() == null ? "" : " '" + w.id() + "'") + " (" + w.create()
                + ") wraps " + w.wraps();
        Val<Integer> once = Val.explicit(1, where + ", which runs it once (SpringCloudCircuitBreakerOracleTest)");
        Val<String> name;
        CircuitBreaker breaker;
        Val<Duration> limit;
        Val<CircuitBreaker.Position> position = Val.unknown(where + ": the layer tries once, so both positions count "
                + "the same calls");
        if (w.id() == null) {
            String why = where + ": its id is not a string literal, so its settings are not known";
            name = Val.unknown(why);
            breaker = new CircuitBreaker(name, position, Val.unknown(why), Val.unknown(why), Val.unknown(why),
                    Val.unknown(why), Val.unknown(why));
            limit = Val.unknown(why);
        } else {
            name = Val.explicit(w.id(), w.create());
            Resolved r = breakers.resolve(w.id());
            breaker = new CircuitBreaker(name, position, r.breaker().failureRateThreshold(),
                    r.breaker().slidingWindowType(), r.breaker().slidingWindowSize(),
                    r.breaker().minimumNumberOfCalls(), r.breaker().waitDurationInOpenState());
            limit = r.timeLimit();
            if (limit.isKnown() && !Durations.isUnlimited(limit.value())) {
                notes.add(call.evidence() + ": the TimeLimiter (" + Durations.format(limit.value()) + ") of " + where
                        + " bounds the request to " + call.host().value());
            }
        }
        if (w.fallback() != null) {
            notes.add(w.create() + ": the run of " + where + " has a fallback (" + w.fallback() + "), so a failure "
                    + "there is answered, not passed on; the analysis still counts it as passed on (fail-close)");
        }
        String noRetry = "no retry: " + where;
        RetryPolicy retry = new RetryPolicy(once, limit, Val.unknown(noRetry), Val.unknown(noRetry),
                Val.unknown(noRetry), Val.unknown(noRetry), Val.unknown(noRetry));
        RetryConditions conditions = new RetryConditions(Val.unknown(noRetry), kb.givesUpAs(KB));
        return new Layer(KIND, retry, limit, conditions, breaker);
    }

    /** For tests and notes: the runs found. */
    public List<Wrap> wraps() {
        return scanned.wraps();
    }
}
