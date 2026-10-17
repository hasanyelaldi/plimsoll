package io.github.hasanyelaldi.plimsoll.collect.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.Resilience4jConfig;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * Differential oracle: real Spring Boot with resilience4j-spring-boot4 binds a fixture {@code application.yml},
 * and {@link Resilience4jConfig} must resolve every instance to the same attempts, wait intervals and timeouts.
 * This also checks the knowledge base entries the collector falls back to.
 */
class Resilience4jConfigOracleTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class App {}

    /** One Spring context and one collector view of the same fixture. */
    abstract static class Fixture {
        private final String resource;
        private ConfigurableApplicationContext context;
        private Resilience4jConfig collected;

        Fixture(String resource) {
            this.resource = resource;
        }

        @BeforeAll
        void start() throws IOException {
            context = new SpringApplicationBuilder(App.class)
                    .web(WebApplicationType.NONE)
                    .properties("spring.main.banner-mode=off", "spring.config.location=classpath:" + resource)
                    .run();
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
                String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                collected = Resilience4jConfig.of(SpringProperties.parse(yaml, resource), DefaultsKb.classpath());
            }
        }

        @AfterAll
        void stop() {
            context.close();
        }

        void assertRetryMatches(String instance) {
            RetryConfig real = context.getBean(RetryRegistry.class).retry(instance).getRetryConfig();
            RetryPolicy ours = collected.retry(instance);

            assertThat(ours.totalAttempts().value()).as("totalAttempts").isEqualTo(real.getMaxAttempts());
            int retries = real.getMaxAttempts() - 1;
            IntervalBiFunction<Object> interval = real.getIntervalBiFunction();
            if (ours.jitter().value()) {
                // Randomized: the library's waits vary; ours says jitter and keeps the base wait.
                Set<Long> seen = new HashSet<>();
                for (int i = 0; i < 50; i++) {
                    seen.add(interval.apply(1, Either.left(new RuntimeException())));
                }
                assertThat(seen).as("randomized waits").hasSizeGreaterThan(1);
            } else {
                assertThat(ourWaits(ours, retries)).as("waits before each retry").isEqualTo(realWaits(interval, retries));
            }
        }

        void assertTimeoutMatches(String instance) {
            Duration real = context.getBean(TimeLimiterRegistry.class).timeLimiter(instance)
                    .getTimeLimiterConfig().getTimeoutDuration();
            assertThat(collected.timeLimiterTimeout(instance).value()).isEqualTo(real);
        }
    }

    private static List<Long> realWaits(IntervalBiFunction<Object> interval, int retries) {
        List<Long> waits = new ArrayList<>();
        for (int k = 1; k <= retries; k++) {
            waits.add(interval.apply(k, Either.left(new RuntimeException())));
        }
        return waits;
    }

    /**
     * {@code min(initial × multiplier^(k-1), maxBackoff)}, the formula rule A2a uses, truncated to whole
     * milliseconds as {@code IntervalFunction} does.
     */
    private static List<Long> ourWaits(RetryPolicy p, int retries) {
        List<Long> waits = new ArrayList<>();
        for (int k = 1; k <= retries; k++) {
            double wait = p.initialBackoff().value().toMillis() * Math.pow(p.multiplier().value(), k - 1);
            if (p.maxBackoff().isKnown()) {
                wait = Math.min(wait, p.maxBackoff().value().toMillis());
            }
            waits.add((long) wait);
        }
        return waits;
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WithDefaultConfig extends Fixture {
        WithDefaultConfig() {
            super("oracle/with-default.yml");
        }

        @ParameterizedTest
        @ValueSource(strings = {"plain", "fromBase", "overrideWait", "flagWithoutWait", "flagWithWait",
            "defaultMultiplier", "jittered", "underscoreStyle", "backendA", "dotted", "notConfigured"})
        void retry(String instance) {
            assertRetryMatches(instance);
        }

        @ParameterizedTest
        @ValueSource(strings = {"own", "fromBase", "fromDefault", "notConfigured"})
        void timeLimiter(String instance) {
            assertTimeoutMatches(instance);
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WithoutDefaultConfig extends Fixture {
        WithoutDefaultConfig() {
            super("oracle/without-default.yml");
        }

        @ParameterizedTest
        @ValueSource(strings = {"waitOnly", "attemptsOnly", "notConfigured"})
        void retry(String instance) {
            assertRetryMatches(instance);
        }

        @ParameterizedTest
        @ValueSource(strings = {"cancelOnly", "notConfigured"})
        void timeLimiter(String instance) {
            assertTimeoutMatches(instance);
        }
    }

    @Test
    void maxAttemptsBelowOneStopsTheApplication() {
        SpringApplicationBuilder app = new SpringApplicationBuilder(App.class)
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off", "spring.config.location=optional:classpath:none.yml",
                        "resilience4j.retry.instances.zero.max-attempts=0");
        assertThatThrownBy(app::run).hasRootCauseMessage("maxAttempts must be greater than or equal to 1.");
    }
}
