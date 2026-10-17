package io.github.hasanyelaldi.plimsoll.collect.resilience4j;

import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringDurations;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties.Property;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Effective Resilience4j Retry and TimeLimiter settings of named instances, resolved from Spring Boot properties
 * the way {@code resilience4j-spring-boot4} resolves them:
 *
 * <ul>
 *   <li>An instance with {@code baseConfig} starts from that config; otherwise from {@code configs.default} when
 *       it exists; otherwise from the library defaults. An instance that is not configured at all gets
 *       {@code configs.default} or the library defaults.
 *   <li>The base only fills the instance's {@code enableExponentialBackoff}, {@code enableRandomizedWait},
 *       {@code exponentialBackoffMultiplier} and {@code exponentialMaxWaitDuration} when the instance leaves them
 *       unset. {@code waitDuration} and {@code maxAttempts} come from the built base config.
 *   <li>The backoff is rebuilt only when the instance itself sets {@code waitDuration}: the exponential and
 *       randomized flags have no effect without it. {@code maxAttempts} below 1 stops the application at
 *       startup, so it is invalid input here.
 * </ul>
 *
 * Each rule is checked against real Spring Boot by {@code Resilience4jConfigOracleTest}. Placeholders
 * ({@code ${...}}) give UNKNOWN; a missing value falls back to the knowledge base, else UNKNOWN.
 */
public final class Resilience4jConfig {

    static final String RETRY_KB = "resilience4j-retry";
    static final String SPRING_KB = "resilience4j-spring-boot4";
    static final String TIME_LIMITER_KB = "resilience4j-timelimiter";
    private static final String DEFAULT = "default";

    private static final List<String> RETRY_CONFIGS = List.of("resilience4j", "retry", "configs");
    private static final List<String> RETRY_INSTANCES = List.of("resilience4j", "retry", "instances");
    private static final List<String> TL_CONFIGS = List.of("resilience4j", "timelimiter", "configs");
    private static final List<String> TL_INSTANCES = List.of("resilience4j", "timelimiter", "instances");

    private final SpringProperties props;
    private final DefaultsKb kb;

    private Resilience4jConfig(SpringProperties props, DefaultsKb kb) {
        this.props = props;
        this.kb = kb;
    }

    public static Resilience4jConfig of(SpringProperties props, DefaultsKb kb) {
        return new Resilience4jConfig(props, kb);
    }

    /** Names under {@code resilience4j.retry.instances}, sorted. */
    public Set<String> retryInstances() {
        return props.names(RETRY_INSTANCES.toArray(String[]::new));
    }

    // ---- Retry -----------------------------------------------------------

    /** Unset fields of one {@code configs.<name>} or {@code instances.<name>} block are null. */
    private record RetryProps(Property baseConfig, Property waitDuration, Property maxAttempts,
            Property enableExponentialBackoff, Property exponentialBackoffMultiplier,
            Property exponentialMaxWaitDuration, Property enableRandomizedWait, Property retryExceptions) {

        /** {@code ConfigUtils.mergePropertiesIfAny}: the base fills only these four fields. */
        RetryProps mergedWith(RetryProps base) {
            return new RetryProps(baseConfig, waitDuration, maxAttempts,
                    or(enableExponentialBackoff, base.enableExponentialBackoff),
                    or(exponentialBackoffMultiplier, base.exponentialBackoffMultiplier),
                    or(exponentialMaxWaitDuration, base.exponentialMaxWaitDuration),
                    or(enableRandomizedWait, base.enableRandomizedWait),
                    retryExceptions);
        }

        private static Property or(Property own, Property base) {
            return own != null ? own : base;
        }
    }

    /**
     * The effective retry policy of an instance. {@code perTryTimeout} is UNKNOWN: Retry has none of its own;
     * the caller adds a TimeLimiter or HTTP client timeout.
     */
    public RetryPolicy retry(String instance) {
        RetryProps own = retryProps(RETRY_INSTANCES, instance);
        RetryPolicy policy = retryConfig(own, instance, new LinkedHashSet<>());
        return new RetryPolicy(policy.totalAttempts(), Val.unknown("Retry has no per-try timeout"),
                policy.initialBackoff(), policy.multiplier(), policy.maxBackoff(), policy.jitter(), policy.retryOn());
    }

    /** {@code CommonRetryConfigurationProperties.createRetryConfig}. */
    private RetryPolicy retryConfig(RetryProps p, String name, Set<String> visiting) {
        RetryPolicy base = null;
        if (p != null && p.baseConfig() != null) {
            String baseName = known(p.baseConfig()).text();
            RetryProps baseProps = retryProps(RETRY_CONFIGS, baseName);
            if (baseProps == null) {
                throw new InvalidSpecException(p.baseConfig().evidence() + ": baseConfig '" + baseName
                        + "' not found under resilience4j.retry.configs");
            }
            if (!visiting.add(baseName)) {
                throw new InvalidSpecException(p.baseConfig().evidence() + ": baseConfig cycle " + visiting);
            }
            p = p.mergedWith(baseProps);
            base = retryConfig(baseProps, baseName, visiting);
        } else if (!name.equals(DEFAULT) && retryProps(RETRY_CONFIGS, DEFAULT) != null) {
            RetryProps defaults = retryProps(RETRY_CONFIGS, DEFAULT);
            if (p != null) {
                p = p.mergedWith(defaults);
            }
            base = retryConfig(defaults, DEFAULT, visiting);
        }
        return build(base != null ? base : retryDefaults(), p);
    }

    private RetryPolicy retryDefaults() {
        String absent = "not configured";
        Val<Duration> wait = kb.defaultOr(RETRY_KB, "waitDuration", Resilience4jConfig::kbDuration, absent);
        return new RetryPolicy(
                kb.defaultOr(RETRY_KB, "maxAttempts", Integer::valueOf, absent),
                Val.unknown("Retry has no per-try timeout"),
                wait,
                fixedBackoffFrom(wait),
                Val.unknown("fixed backoff, no cap"),
                kb.defaultOr(SPRING_KB, "resilience4j.retry.enable-randomized-wait", Boolean::valueOf, absent),
                Val.unknown("retryExceptions not configured; no verified default"));
    }

    /** The default interval function waits the same {@code waitDuration} before every retry. */
    private static Val<Double> fixedBackoffFrom(Val<Duration> wait) {
        return wait.isKnown()
                ? new Val<>(1.0, wait.source(), "fixed backoff; " + wait.evidence())
                : Val.unknown("backoff not known: " + wait.evidence());
    }

    /** {@code buildConfig} and {@code configureRetryIntervalFunction} applied to a base policy. */
    private RetryPolicy build(RetryPolicy base, RetryProps p) {
        if (p == null) {
            return base;
        }
        Val<Integer> attempts = base.totalAttempts();
        if (p.maxAttempts() != null) {
            if (p.maxAttempts().isPlaceholder()) {
                attempts = placeholder(p.maxAttempts());
            } else {
                int n = integer(p.maxAttempts());
                if (n < 1) {
                    throw new InvalidSpecException(p.maxAttempts().evidence() + ": maxAttempts must be at least 1, got "
                            + n + " (Resilience4j refuses to start)");
                }
                attempts = Val.explicit(n, p.maxAttempts().evidence() + " (maxAttempts)");
            }
        }

        Val<Duration> initial = base.initialBackoff();
        Val<Double> multiplier = base.multiplier();
        Val<Duration> maxBackoff = base.maxBackoff();
        Val<Boolean> jitter = base.jitter();
        if (p.waitDuration() != null) {
            initial = p.waitDuration().isPlaceholder()
                    ? placeholder(p.waitDuration())
                    : Val.explicit(duration(p.waitDuration()), p.waitDuration().evidence() + " (waitDuration)");
            Val<Boolean> exponential = flag(p.enableExponentialBackoff(), "enable-exponential-backoff");
            jitter = flag(p.enableRandomizedWait(), "enable-randomized-wait");
            if (!exponential.isKnown()) {
                multiplier = Val.unknown("enableExponentialBackoff not known: " + exponential.evidence());
                maxBackoff = Val.unknown("enableExponentialBackoff not known: " + exponential.evidence());
            } else if (!exponential.value()) {
                multiplier = new Val<>(1.0, exponential.source(), "fixed backoff; " + exponential.evidence());
                maxBackoff = Val.unknown("fixed backoff, no cap");
            } else {
                multiplier = p.exponentialBackoffMultiplier() == null
                        ? kb.defaultOr(SPRING_KB, "resilience4j.retry.exponential-backoff-multiplier",
                                Double::valueOf, "exponentialBackoffMultiplier not configured")
                        : value(p.exponentialBackoffMultiplier(), Resilience4jConfig::number);
                maxBackoff = p.exponentialMaxWaitDuration() == null
                        ? Val.unknown("exponentialMaxWaitDuration not configured; no verified default cap")
                        : value(p.exponentialMaxWaitDuration(), Resilience4jConfig::duration);
            }
        }

        Val<Set<String>> retryOn = base.retryOn();
        if (p.retryExceptions() != null) {
            retryOn = p.retryExceptions().isPlaceholder()
                    ? placeholder(p.retryExceptions())
                    : Val.explicit(Set.copyOf(new TreeSet<>(p.retryExceptions().values())),
                            p.retryExceptions().evidence() + " (retryExceptions)");
        }
        return new RetryPolicy(attempts, base.perTryTimeout(), initial, multiplier, maxBackoff, jitter, retryOn);
    }

    /** A boolean switch of this block, or its Spring default from the knowledge base. */
    private Val<Boolean> flag(Property p, String kbName) {
        if (p == null) {
            return kb.defaultOr(SPRING_KB, "resilience4j.retry." + kbName, Boolean::valueOf, kbName + " not set");
        }
        return value(p, Resilience4jConfig::bool);
    }

    /** The block {@code <section>.<name>}, or null when it does not exist. */
    private RetryProps retryProps(List<String> section, String name) {
        if (!configured(section, name)) {
            return null;
        }
        return new RetryProps(field(section, name, "baseConfig"), field(section, name, "waitDuration"),
                field(section, name, "maxAttempts"), field(section, name, "enableExponentialBackoff"),
                field(section, name, "exponentialBackoffMultiplier"),
                field(section, name, "exponentialMaxWaitDuration"), field(section, name, "enableRandomizedWait"),
                field(section, name, "retryExceptions"));
    }

    private boolean configured(List<String> section, String name) {
        return props.names(section.toArray(String[]::new)).contains(name);
    }

    private Property field(List<String> section, String name, String field) {
        return props.get(section, name, field).orElse(null);
    }

    // ---- TimeLimiter -----------------------------------------------------

    /**
     * The effective {@code timeoutDuration} of a TimeLimiter instance, with the same base resolution as Retry
     * ({@code CommonTimeLimiterConfigurationProperties.createTimeLimiterConfig}).
     */
    public Val<Duration> timeLimiterTimeout(String instance) {
        return timeLimiterTimeout(TL_INSTANCES, instance, new LinkedHashSet<>());
    }

    private Val<Duration> timeLimiterTimeout(List<String> section, String name, Set<String> visiting) {
        boolean configured = configured(section, name);
        Property baseConfig = configured ? field(section, name, "baseConfig") : null;
        Val<Duration> base = null;
        if (baseConfig != null) {
            String baseName = known(baseConfig).text();
            if (!configured(TL_CONFIGS, baseName)) {
                throw new InvalidSpecException(baseConfig.evidence() + ": baseConfig '" + baseName
                        + "' not found under resilience4j.timelimiter.configs");
            }
            if (!visiting.add(baseName)) {
                throw new InvalidSpecException(baseConfig.evidence() + ": baseConfig cycle " + visiting);
            }
            base = timeLimiterTimeout(TL_CONFIGS, baseName, visiting);
        } else if (!name.equals(DEFAULT) && configured(TL_CONFIGS, DEFAULT)) {
            base = timeLimiterTimeout(TL_CONFIGS, DEFAULT, visiting);
        }
        Property own = configured ? field(section, name, "timeoutDuration") : null;
        if (own != null) {
            return value(own, Resilience4jConfig::duration);
        }
        return base != null ? base : kb.defaultOr(TIME_LIMITER_KB, "timeoutDuration", Resilience4jConfig::kbDuration,
                "timeoutDuration not configured");
    }

    // ---- aspect order ----------------------------------------------------

    /** {@code resilience4j.retry.retryAspectOrder}; lower order = outer aspect. */
    public Val<Integer> retryAspectOrder() {
        return aspectOrder("retry", "retryAspectOrder", "resilience4j.retry.retry-aspect-order");
    }

    /** {@code resilience4j.timelimiter.timeLimiterAspectOrder}; lower order = outer aspect. */
    public Val<Integer> timeLimiterAspectOrder() {
        return aspectOrder("timelimiter", "timeLimiterAspectOrder", "resilience4j.timelimiter.time-limiter-aspect-order");
    }

    private Val<Integer> aspectOrder(String module, String field, String kbName) {
        return props.get("resilience4j", module, field)
                .map(p -> value(p, Resilience4jConfig::integer))
                .orElseGet(() -> kb.defaultOr(SPRING_KB, kbName, Integer::valueOf, field + " not set"));
    }

    // ---- values ----------------------------------------------------------

    private static <T> Val<T> value(Property p, Function<Property, T> parse) {
        return p.isPlaceholder() ? placeholder(p) : Val.explicit(parse.apply(p), p.evidence());
    }

    private static <T> Val<T> placeholder(Property p) {
        return Val.unknown(p.evidence() + ": placeholder " + p.text() + " depends on the environment");
    }

    private static Property known(Property p) {
        if (p.isPlaceholder() || p.text() == null) {
            throw new InvalidSpecException(p.evidence() + ": " + String.join(".", p.path())
                    + " must be a plain name, got " + (p.text() != null ? p.text() : p.items()));
        }
        return p;
    }

    private static int integer(Property p) {
        try {
            return Integer.parseInt(scalar(p).strip());
        } catch (NumberFormatException e) {
            throw invalid(p, "an integer");
        }
    }

    private static double number(Property p) {
        try {
            return Double.parseDouble(scalar(p).strip());
        } catch (NumberFormatException e) {
            throw invalid(p, "a number");
        }
    }

    private static boolean bool(Property p) {
        String t = scalar(p).strip();
        if (t.equalsIgnoreCase("true") || t.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(t);
        }
        throw invalid(p, "true or false");
    }

    private static Duration duration(Property p) {
        return SpringDurations.parse(scalar(p)).orElseThrow(() -> invalid(p, "a duration such as 500ms or PT0.5S"));
    }

    private static Duration kbDuration(String text) {
        return SpringDurations.parse(text).orElseThrow(() ->
                new IllegalStateException("knowledge base duration '" + text + "' is not valid"));
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
