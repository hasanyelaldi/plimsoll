package io.github.hasanyelaldi.plimsoll.collect.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.hasanyelaldi.plimsoll.collect.circuitbreaker.CustomizerScanner;
import io.github.hasanyelaldi.plimsoll.collect.circuitbreaker.CustomizerScanner.Customizations;
import io.github.hasanyelaldi.plimsoll.collect.circuitbreaker.CustomizerScanner.Factory;
import io.github.hasanyelaldi.plimsoll.collect.circuitbreaker.SpringCloudCircuitBreakers;
import io.github.hasanyelaldi.plimsoll.collect.circuitbreaker.SpringCloudCircuitBreakers.Resolved;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.collect.oracle.sccb.LayeredCustomizer;
import io.github.hasanyelaldi.plimsoll.collect.oracle.sccb.PetclinicCustomizer;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties;
import io.github.hasanyelaldi.plimsoll.model.CircuitBreaker.WindowType;
import io.github.hasanyelaldi.plimsoll.model.Durations;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.PrematureCloseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

/**
 * Differential oracle (#18): Spring Cloud CircuitBreaker 5.0.3 with resilience4j-spring-boot4 runs a fixture
 * {@code Customizer} and {@code application.yml}; {@link CustomizerScanner} parses the same Customizer source and
 * {@link SpringCloudCircuitBreakers} must give every id the breaker settings and TimeLimiter timeout that
 * {@code ReactiveResilience4JCircuitBreakerFactory.create(id)} uses. A TimeLimiter that is switched off is a timeout
 * of {@code unlimited}. No waiting: each breaker runs a {@code Mono} that is already complete.
 */
class SpringCloudCircuitBreakerOracleTest {

    private static final Path FIXTURES = Path.of("src/test/java/io/github/hasanyelaldi/plimsoll/collect/oracle/sccb");

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class App {}

    abstract static class Fixture {
        private final String yml;
        private final Class<?> customizer;
        ConfigurableApplicationContext context;
        private SpringCloudCircuitBreakers ours;
        Customizations customizations;

        /** @param yml classpath resource or null; @param customizer fixture class or null */
        Fixture(String yml, Class<?> customizer) {
            this.yml = yml;
            this.customizer = customizer;
        }

        @BeforeAll
        void start() throws IOException {
            List<Class<?>> sources = new ArrayList<>(List.of(App.class));
            if (customizer != null) {
                sources.add(customizer);
            }
            context = new SpringApplicationBuilder(sources.toArray(Class<?>[]::new))
                    .web(WebApplicationType.NONE)
                    .properties("spring.cloud.gateway.server.webflux.enabled=false", "spring.main.banner-mode=off",
                            "spring.config.location=" + (yml == null ? "optional:classpath:none.yml" : "classpath:" + yml))
                    .run();
            String yaml = "";
            if (yml != null) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream(yml)) {
                    yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            Map<String, String> java = Map.of();
            if (customizer != null) {
                Path file = FIXTURES.resolve(customizer.getSimpleName() + ".java");
                java = Map.of(file.getFileName().toString(), Files.readString(file));
            }
            DefaultsKb kb = DefaultsKb.classpath();
            customizations = CustomizerScanner.parse(java, kb).get(Factory.REACTIVE);
            ours = SpringCloudCircuitBreakers.of(SpringProperties.parse(yaml, yml == null ? "none.yml" : yml),
                    customizations, kb);
        }

        @AfterAll
        void stop() {
            context.close();
        }

        Resolved ours(String id) {
            return ours.resolve(id);
        }

        /** Runs the id's breaker once, then reads what the factory built for it. */
        void assertMatches(String id) {
            ReactiveResilience4JCircuitBreakerFactory factory = context.getBean(ReactiveResilience4JCircuitBreakerFactory.class);
            assertThat(factory.create(id).run(Mono.just(1), null).block()).isEqualTo(1);
            CircuitBreakerConfig real = factory.getCircuitBreakerRegistry().circuitBreaker(id).getCircuitBreakerConfig();
            Optional<TimeLimiter> limiter = factory.getTimeLimiterRegistry().find(id);
            Resolved ours = ours(id);
            assertThat(ours.breaker().failureRateThreshold().value()).as("failureRateThreshold")
                    .isEqualTo((double) real.getFailureRateThreshold());
            assertThat(ours.breaker().slidingWindowType().value()).as("slidingWindowType")
                    .isEqualTo(WindowType.valueOf(real.getSlidingWindowType().name()));
            assertThat(ours.breaker().slidingWindowSize().value()).as("slidingWindowSize")
                    .isEqualTo(real.getSlidingWindowSize());
            assertThat(ours.breaker().minimumNumberOfCalls().value()).as("minimumNumberOfCalls")
                    .isEqualTo(real.getMinimumNumberOfCalls());
            assertThat(ours.breaker().waitDurationInOpenState().value().toMillis()).as("waitDurationInOpenState")
                    .isEqualTo(real.getWaitIntervalFunctionInOpenState().apply(1))
                    .isEqualTo(real.getWaitIntervalFunctionInOpenState().apply(5));
            Duration timeout = limiter.map(t -> t.getTimeLimiterConfig().getTimeoutDuration()).orElse(Durations.UNLIMITED);
            assertThat(ours.timeLimit().value()).as("TimeLimiter timeout").isEqualTo(timeout);
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Petclinic extends Fixture {
        Petclinic() {
            super(null, PetclinicCustomizer.class);
        }

        /**
         * A breaker around a call ({@code cb.run(mono, null)}) subscribes to it once and passes its error on as it is:
         * a request that was not sent, a reset and a timeout stay what they are (KB {@code givesUpAs}, #18).
         */
        @ParameterizedTest
        @ValueSource(strings = {"not-sent", "reset", "timeout", "status-503"})
        void aBreakerTriesOnceAndPassesTheErrorOn(String failure) {
            Throwable error = switch (failure) {
                case "not-sent" -> new WebClientRequestException(new ConnectException("Connection refused"),
                        HttpMethod.GET, URI.create("http://visits-service/pets/visits"), new HttpHeaders());
                case "reset" -> new WebClientRequestException(PrematureCloseException.TEST_EXCEPTION, HttpMethod.GET,
                        URI.create("http://visits-service/pets/visits"), new HttpHeaders());
                case "timeout" -> new TimeoutException("Did not observe any item");
                default -> WebClientResponseException.create(503, "Service Unavailable", new HttpHeaders(), new byte[0],
                        null);
            };
            AtomicInteger tries = new AtomicInteger();
            Mono<Integer> call = Mono.defer(() -> {
                tries.incrementAndGet();
                return Mono.error(error);
            });
            ReactiveResilience4JCircuitBreakerFactory factory =
                    context.getBean(ReactiveResilience4JCircuitBreakerFactory.class);
            // block() wraps a checked exception; unwrap gives back what the breaker emitted
            Throwable thrown = catchThrowable(() -> factory.create("passOn-" + failure).run(call, null).block());
            assertThat(Exceptions.unwrap(thrown)).isSameAs(error);
            assertThat(tries).hasValue(1);
        }

        @Test
        void everyIdGetsTheLibraryBreakerAndATenSecondTimeLimiter() {
            assertMatches("defaultCircuitBreaker");
            Resolved r = ours("defaultCircuitBreaker");
            assertThat(r.timeLimit().value()).isEqualTo(Duration.ofSeconds(10));
            assertThat(r.timeLimit().source()).isEqualTo(Source.EXPLICIT);
            assertThat(r.timeLimit().evidence()).startsWith("PetclinicCustomizer.java:23: timeoutDuration(Duration.ofSeconds(10))");
            assertThat(r.breaker().slidingWindowSize().source()).isEqualTo(Source.LIBRARY_DEFAULT);
            assertThat(r.breaker().slidingWindowSize().evidence()).contains("CircuitBreakerConfig.ofDefaults()");
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Layered extends Fixture {
        Layered() {
            super("oracle/sccb-layered.yml", LayeredCustomizer.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"configured", "other", "yamlConfig", "yamlInstance", "tlInstance", "tlConfig",
            "noLimiter"})
        void breaker(String id) {
            assertMatches(id);
        }

        @Test
        void theCustomizerIsReadCompletely() {
            assertThat(customizations.unread()).isEmpty();
            assertThat(customizations.byId()).containsOnlyKeys("configured", "yamlConfig", "yamlInstance", "tlInstance");
        }

        @Test
        void aResilience4jInstanceOrConfigOfTheIdWinsOverTheCustomizer() {
            assertThat(ours("yamlInstance").breaker().minimumNumberOfCalls().value()).isEqualTo(8);
            assertThat(ours("yamlConfig").breaker().slidingWindowSize().value()).isEqualTo(12);
            assertThat(ours("tlInstance").timeLimit().value()).isEqualTo(Duration.ofSeconds(6));
            assertThat(ours("tlConfig").timeLimit().value()).isEqualTo(Duration.ofSeconds(5));
            assertThat(ours("yamlConfig").timeLimit().value()).isEqualTo(Duration.ofSeconds(3));
        }

        @Test
        void configureDefaultWithoutABreakerStartsFromTheLibraryNotFromConfigsDefault() {
            assertThat(ours("other").breaker().failureRateThreshold().value()).isEqualTo(50.0);
            assertThat(ours("other").timeLimit().value()).isEqualTo(Duration.ofMillis(2000));
        }

        @Test
        void theBlockingFactorysCustomizerDoesNotReachTheReactiveOne() {
            assertThat(ours("other").timeLimit().value()).isNotEqualTo(Duration.ofSeconds(7));
        }

        @Test
        void aSwitchedOffTimeLimiterHasNoTimeout() {
            assertThat(Durations.isUnlimited(ours("noLimiter").timeLimit().value())).isTrue();
            assertThat(ours("noLimiter").timeLimit().evidence()).contains("disable-time-limiter-map");
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class RegistryDefaults extends Fixture {
        RegistryDefaults() {
            super("oracle/sccb-registry-defaults.yml", null);
        }

        @ParameterizedTest
        @ValueSource(strings = {"plain", "withLimiter"})
        void breaker(String id) {
            assertMatches(id);
        }

        @Test
        void withoutACustomizerTheRegistryDefaultsApply() {
            assertThat(ours("plain").breaker().failureRateThreshold().value()).isEqualTo(70.0);
            assertThat(Durations.isUnlimited(ours("plain").timeLimit().value())).isTrue();
            assertThat(ours("withLimiter").timeLimit().value()).isEqualTo(Duration.ofMillis(2500));
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class NothingConfigured extends Fixture {
        NothingConfigured() {
            super(null, null);
        }

        @Test
        void theLibraryDefaultsApplyWithAOneSecondTimeLimiter() {
            assertMatches("plain");
            Resolved r = ours("plain");
            assertThat(r.timeLimit().value()).isEqualTo(Duration.ofSeconds(1));
            assertThat(r.timeLimit().source()).isEqualTo(Source.LIBRARY_DEFAULT);
            assertThat(r.breaker().failureRateThreshold().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        }
    }
}
