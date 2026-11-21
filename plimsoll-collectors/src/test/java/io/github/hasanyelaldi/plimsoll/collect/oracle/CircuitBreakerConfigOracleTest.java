package io.github.hasanyelaldi.plimsoll.collect.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.Resilience4jConfig;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.Resilience4jConfig.BreakerConfig;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.CircuitBreaker.WindowType;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
 * Differential oracle (ADR 0015): real Spring Boot with resilience4j-spring-boot4 binds a fixture
 * {@code application.yml}, and {@link Resilience4jConfig#circuitBreaker} must resolve every instance to the same
 * failure rate threshold, sliding window, minimum number of calls and wait in the open state. Values it cannot
 * state as one duration (an exponential or randomized wait) must be UNKNOWN. This also checks the knowledge base
 * entries in {@code resilience4j-circuitbreaker.yaml}.
 */
class CircuitBreakerConfigOracleTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class App {}

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
                    .properties(GatewayRetryOracleTest.GATEWAY_OFF)
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

        CircuitBreakerConfig real(String instance) {
            return context.getBean(CircuitBreakerRegistry.class).circuitBreaker(instance).getCircuitBreakerConfig();
        }

        BreakerConfig ours(String instance) {
            return collected.circuitBreaker(instance);
        }

        void assertMatches(String instance) {
            CircuitBreakerConfig real = real(instance);
            BreakerConfig ours = ours(instance);
            assertThat(ours.failureRateThreshold().value()).as("failureRateThreshold")
                    .isEqualTo((double) real.getFailureRateThreshold());
            assertThat(ours.slidingWindowType().value()).as("slidingWindowType")
                    .isEqualTo(WindowType.valueOf(real.getSlidingWindowType().name()));
            assertThat(ours.slidingWindowSize().value()).as("slidingWindowSize").isEqualTo(real.getSlidingWindowSize());
            assertThat(ours.minimumNumberOfCalls().value()).as("minimumNumberOfCalls")
                    .isEqualTo(real.getMinimumNumberOfCalls());
            long first = real.getWaitIntervalFunctionInOpenState().apply(1);
            long later = real.getWaitIntervalFunctionInOpenState().apply(5);
            if (ours.waitDurationInOpenState().isKnown()) {
                assertThat(ours.waitDurationInOpenState().value().toMillis()).as("waitDurationInOpenState")
                        .isEqualTo(first).isEqualTo(later);
            } else {
                assertThat(later).as("an UNKNOWN wait is one that changes from opening to opening").isNotEqualTo(first);
            }
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WithDefaultConfig extends Fixture {
        WithDefaultConfig() {
            super("oracle/breakers-with-default.yml");
        }

        @ParameterizedTest
        @ValueSource(strings = {"plain", "fromBase", "fromLayered", "direct", "relaxed", "minAboveWindow",
            "notConfigured"})
        void breaker(String instance) {
            assertMatches(instance);
        }

        @ParameterizedTest
        @ValueSource(strings = {"fromExponential", "ownExponential", "ownRandomized"})
        void aWaitThatChangesIsUnknown(String instance) {
            assertThat(ours(instance).waitDurationInOpenState().source()).isEqualTo(Source.UNKNOWN);
            assertMatches(instance);
        }

        @Test
        void aConfigWithoutBaseConfigDoesNotStartFromTheDefaultConfig() {
            assertThat(real("fromBase").getSlidingWindowSize()).isEqualTo(30);
            assertThat(real("fromBase").getMinimumNumberOfCalls()).isEqualTo(CircuitBreakerConfig.DEFAULT_MINIMUM_NUMBER_OF_CALLS);
            assertThat(real("direct").getFailureRateThreshold()).isEqualTo(80f);
        }

        @Test
        void minimumNumberOfCallsIsNotCappedAtTheWindowSize() {
            assertThat(real("minAboveWindow").getMinimumNumberOfCalls()).isEqualTo(10);
        }
    }

    @Test
    void aWaitInTheOpenStateBelowOneMillisecondStopsTheApplication() {
        SpringApplicationBuilder app = new SpringApplicationBuilder(App.class)
                .web(WebApplicationType.NONE)
                .properties(GatewayRetryOracleTest.GATEWAY_OFF)
                .properties("spring.main.banner-mode=off", "spring.config.location=optional:classpath:none.yml",
                        "resilience4j.circuitbreaker.instances.zero.wait-duration-in-open-state=0s");
        assertThatThrownBy(app::run)
                .hasRootCauseMessage("waitDurationInOpenStateMillis must be greater than or equal to 1 millis.");
        Resilience4jConfig ours = Resilience4jConfig.of(SpringProperties.parse(
                "resilience4j.circuitbreaker.instances.zero.wait-duration-in-open-state: 0s\n", "a.yml"),
                DefaultsKb.classpath());
        assertThatThrownBy(() -> ours.circuitBreaker("zero")).isInstanceOf(InvalidSpecException.class)
                .hasMessageContaining("at least 1 ms");
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WithoutDefaultConfig extends Fixture {
        WithoutDefaultConfig() {
            super("oracle/breakers-without-default.yml");
        }

        @ParameterizedTest
        @ValueSource(strings = {"rateOnly", "millis", "solo", "notConfigured"})
        void breaker(String instance) {
            assertMatches(instance);
        }

        @Test
        void anUnconfiguredInstanceDoesNotUseTheConfigOfItsName() {
            assertThat(real("solo").getSlidingWindowSize()).isEqualTo(CircuitBreakerConfig.DEFAULT_SLIDING_WINDOW_SIZE);
        }

        @Test
        void unconfiguredValuesAreLibraryDefaults() {
            BreakerConfig ours = ours("notConfigured");
            assertThat(ours.failureRateThreshold().source()).isEqualTo(Source.LIBRARY_DEFAULT);
            assertThat(ours.failureRateThreshold().evidence()).contains("https://resilience4j.readme.io/docs/circuitbreaker");
            assertThat(ours.waitDurationInOpenState().source()).isEqualTo(Source.LIBRARY_DEFAULT);
            assertThat(ours("solo").slidingWindowSize().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        }
    }
}
