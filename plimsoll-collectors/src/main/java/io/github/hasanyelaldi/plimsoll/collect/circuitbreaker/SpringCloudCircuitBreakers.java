package io.github.hasanyelaldi.plimsoll.collect.circuitbreaker;

import io.github.hasanyelaldi.plimsoll.collect.circuitbreaker.CustomizerScanner.Customizations;
import io.github.hasanyelaldi.plimsoll.collect.circuitbreaker.CustomizerScanner.ProgrammaticConfig;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.Resilience4jConfig;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.Resilience4jConfig.BreakerConfig;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties.Property;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Durations;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The breaker and TimeLimiter a Spring Cloud CircuitBreaker Resilience4j factory gives the id {@code id}
 * ({@code create(id)}, the group being the id), in the order {@code ReactiveResilience4JCircuitBreakerFactory} 5.0.3
 * resolves them (#18):
 *
 * <ol>
 *   <li>a Resilience4j instance of that name in the registry, which Spring Boot creates from
 *       {@code resilience4j.circuitbreaker.instances.<id>} (or {@code timelimiter.instances}) and the factory reuses;
 *   <li>else a shared config of that name, {@code resilience4j.circuitbreaker.configs.<id>};
 *   <li>else the factory's {@code configure(..., id)} from a {@code Customizer} bean;
 *   <li>else its {@code configureDefault}, or without one the registry's default config ({@code configs.default}
 *       or the library defaults).
 * </ol>
 *
 * {@code spring.cloud.circuitbreaker.resilience4j.disable-time-limiter-map.<id>}, else {@code disable-time-limiter},
 * switches the TimeLimiter off: no timeout. Checked by {@code SpringCloudCircuitBreakerOracleTest}.
 */
public final class SpringCloudCircuitBreakers {

    static final String KB = "spring-cloud-circuitbreaker";
    private static final String[] PREFIX = {"spring", "cloud", "circuitbreaker", "resilience4j"};

    /** The settings of one id. */
    public record Resolved(BreakerConfig breaker, Val<Duration> timeLimit) {}

    private final SpringProperties props;
    private final Resilience4jConfig r4j;
    private final Customizations customizations;
    private final DefaultsKb kb;

    private SpringCloudCircuitBreakers(SpringProperties props, Customizations customizations, DefaultsKb kb) {
        this.props = props;
        this.r4j = Resilience4jConfig.of(props, kb);
        this.customizations = customizations;
        this.kb = kb;
    }

    public static SpringCloudCircuitBreakers of(SpringProperties props, Customizations customizations, DefaultsKb kb) {
        return new SpringCloudCircuitBreakers(props, customizations, kb);
    }

    public Resolved resolve(String id) {
        return new Resolved(breaker(id), timeLimit(id));
    }

    private BreakerConfig breaker(String id) {
        String cb = "Spring Cloud CircuitBreaker '" + id + "'";
        if (r4j.hasCircuitBreakerInstance(id)) {
            return via(r4j.circuitBreaker(id), cb + " reuses Resilience4j instance resilience4j.circuitbreaker.instances."
                    + id);
        }
        if (r4j.hasCircuitBreakerConfig(id)) {
            return via(r4j.circuitBreakerConfig(id), cb + " takes the config resilience4j.circuitbreaker.configs." + id);
        }
        Optional<String> unread = unread();
        if (unread.isPresent()) {
            String why = cb + ": " + unread.get();
            return new BreakerConfig(Val.unknown(why), Val.unknown(why), Val.unknown(why), Val.unknown(why),
                    Val.unknown(why));
        }
        ProgrammaticConfig own = customizations.byId().get(id);
        if (own != null) {
            return via(own.breaker(), cb + " is configured by the Customizer's configure");
        }
        if (customizations.defaultConfig() != null) {
            return via(customizations.defaultConfig().breaker(), cb + " takes the Customizer's configureDefault");
        }
        return via(r4j.circuitBreaker(id), cb + ": no Customizer sets it, so the registry's default config applies");
    }

    private Val<Duration> timeLimit(String id) {
        String tl = "TimeLimiter of Spring Cloud CircuitBreaker '" + id + "'";
        Val<Boolean> disabled = disabled(id);
        if (!disabled.isKnown()) {
            return Val.unknown(disabled.evidence());
        }
        if (disabled.value()) {
            return new Val<>(Durations.UNLIMITED, disabled.source(), tl + " is switched off: " + disabled.evidence());
        }
        if (r4j.hasTimeLimiterInstance(id)) {
            return via(r4j.timeLimiterTimeout(id), tl + " reuses Resilience4j instance resilience4j.timelimiter.instances."
                    + id);
        }
        if (r4j.hasTimeLimiterConfig(id)) {
            return via(r4j.timeLimiterConfigTimeout(id), tl + " takes the config resilience4j.timelimiter.configs." + id);
        }
        Optional<String> unread = unread();
        if (unread.isPresent()) {
            return Val.unknown(tl + ": " + unread.get());
        }
        ProgrammaticConfig own = customizations.byId().get(id);
        if (own != null) {
            return via(own.timeLimit(), tl + " is configured by the Customizer's configure");
        }
        if (customizations.defaultConfig() != null) {
            return via(customizations.defaultConfig().timeLimit(), tl + " takes the Customizer's configureDefault");
        }
        return via(r4j.timeLimiterTimeout(id), tl + ": no Customizer sets it, so the registry's default config applies");
    }

    private Optional<String> unread() {
        return customizations.unread().isEmpty() ? Optional.empty()
                : Optional.of("Customizer<" + customizations.factory().type() + "> not fully read: "
                        + String.join("; ", customizations.unread()));
    }

    /** {@code ConfigurationPropertiesUtils.isDisableTimeLimiter}: the per-id entry, else the global switch. */
    private Val<Boolean> disabled(String id) {
        List<String> map = List.of(PREFIX[0], PREFIX[1], PREFIX[2], PREFIX[3], "disableTimeLimiterMap");
        Optional<Property> own = props.under(map.toArray(String[]::new)).stream()
                .filter(p -> p.path().size() == map.size() + 1 && p.path().get(map.size()).equals(id))
                .reduce((a, b) -> b);
        Optional<Property> global = props.get(PREFIX[0], PREFIX[1], PREFIX[2], PREFIX[3], "disableTimeLimiter");
        Optional<Property> set = own.or(() -> global);
        if (set.isEmpty()) {
            return kb.defaultOr(KB, "disableTimeLimiter", Boolean::valueOf,
                    "spring.cloud.circuitbreaker.resilience4j.disable-time-limiter not set");
        }
        Property p = set.get();
        if (p.isPlaceholder()) {
            return Val.unknown(p.evidence() + ": placeholder " + p.text() + " decides whether the TimeLimiter is on");
        }
        String text = p.text() == null ? "" : p.text().strip().toLowerCase(java.util.Locale.ROOT);
        if (!text.equals("true") && !text.equals("false")) {
            throw new InvalidSpecException(p.evidence() + ": " + String.join(".", p.path())
                    + " must be true or false, got " + (p.text() != null ? p.text() : p.items()));
        }
        return Val.explicit(Boolean.valueOf(text), p.evidence() + " (" + String.join(".", p.path()) + ": " + text + ")");
    }

    private static BreakerConfig via(BreakerConfig c, String why) {
        return new BreakerConfig(via(c.failureRateThreshold(), why), via(c.slidingWindowType(), why),
                via(c.slidingWindowSize(), why), via(c.minimumNumberOfCalls(), why), via(c.waitDurationInOpenState(), why));
    }

    /** Puts the value's own evidence, which has its {@code file:line}, first and says how it was picked after it. */
    private static <T> Val<T> via(Val<T> v, String why) {
        return new Val<>(v.value(), v.source(), v.evidence() + " (" + why + ")");
    }
}
