package io.github.hasanyelaldi.plimsoll.collect.circuitbreaker;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hasanyelaldi.plimsoll.collect.circuitbreaker.CustomizerScanner.Customizations;
import io.github.hasanyelaldi.plimsoll.collect.circuitbreaker.CustomizerScanner.Factory;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.model.CircuitBreaker.WindowType;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CustomizerScannerTest {

    private static Map<Factory, Customizations> parse(String... sources) {
        Map<String, String> files = new LinkedHashMap<>();
        for (int i = 0; i < sources.length; i++) {
            files.put("C" + i + ".java", sources[i]);
        }
        return CustomizerScanner.parse(files, DefaultsKb.classpath());
    }

    private static Customizations reactive(String body) {
        return parse("""
                class C {
                    @Bean
                    Customizer<ReactiveResilience4JCircuitBreakerFactory> c() {
                %s
                    }
                }
                """.formatted(body.indent(8))).get(Factory.REACTIVE);
    }

    @Test
    void petclinicsCustomizerIsReadFromTheFixture() {
        Path gateway = Path.of("src/test/resources/projects/petclinic-gateway");
        Customizations c = CustomizerScanner.scan(gateway, gateway.resolve("src/main/java"), DefaultsKb.classpath())
                .get(Factory.REACTIVE);
        assertThat(c.unread()).isEmpty();
        assertThat(c.byId()).isEmpty();
        assertThat(c.defaultConfig().timeLimit().value()).isEqualTo(Duration.ofSeconds(10));
        assertThat(c.defaultConfig().breaker().failureRateThreshold().source()).isEqualTo(Source.LIBRARY_DEFAULT);
    }

    @Test
    void customChainsSetTheFieldsTheyNameAndLeaveTheRestAtTheDefaults() {
        Customizations c = reactive("""
                return f -> f.configure(b -> b.circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .failureRateThreshold(25.5f)
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                        .slidingWindowSize(30)
                        .waitDurationInOpenState(Duration.parse("PT45S"))
                        .build()), "a", "b");
                """);
        assertThat(c.byId()).containsOnlyKeys("a", "b");
        var b = c.byId().get("a").breaker();
        assertThat(b.failureRateThreshold().value()).isEqualTo(25.5);
        assertThat(b.slidingWindowType().value()).isEqualTo(WindowType.TIME_BASED);
        assertThat(b.slidingWindowSize()).isEqualTo(Val.explicit(30, "C0.java:7: slidingWindowSize(30) in "
                + "configure(..., \"a\", \"b\") of @Bean c"));
        assertThat(b.waitDurationInOpenState().value()).isEqualTo(Duration.ofSeconds(45));
        assertThat(b.minimumNumberOfCalls().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(b.minimumNumberOfCalls().evidence()).startsWith("C0.java:4: not set in the "
                + "CircuitBreakerConfig.custom() of configure(..., \"a\", \"b\") of @Bean c");
        assertThat(c.byId().get("a").timeLimit().value()).isEqualTo(Duration.ofSeconds(1));
        assertThat(c.byId().get("a").timeLimit().evidence()).contains("no TimeLimiterConfig in configure");
    }

    @Test
    void aBlockLambdaAppliesItsStatementsInOrder() {
        Customizations c = reactive("""
                return factory -> {
                    factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                            .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(300)).build())
                            .build());
                    factory.addCircuitBreakerCustomizer(cb -> cb.getEventPublisher(), "a");
                    factory.configureDefault(id -> {
                        return new Resilience4JConfigBuilder(id)
                                .timeLimiterConfig(TimeLimiterConfig.ofDefaults())
                                .build();
                    });
                };
                """);
        assertThat(c.unread()).isEmpty();
        assertThat(c.defaultConfig().timeLimit().value()).isEqualTo(Duration.ofSeconds(1));
        assertThat(c.defaultConfig().timeLimit().evidence()).startsWith("C0.java:11: TimeLimiterConfig.ofDefaults()");
    }

    @Test
    void valuesThatAreNotLiteralsAreUnknownAndNamed() {
        Customizations c = reactive("""
                return f -> f.configureDefault(id -> new Resilience4JConfigBuilder(id)
                        .circuitBreakerConfig(CircuitBreakerConfig.custom().slidingWindowSize(size).build())
                        .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(props.getTimeout()).build())
                        .build());
                """);
        assertThat(c.unread()).isEmpty();
        assertThat(c.defaultConfig().breaker().slidingWindowSize()).isEqualTo(Val.unknown(
                "C0.java:5: slidingWindowSize(size) is not a literal in configureDefault of @Bean c"));
        assertThat(c.defaultConfig().timeLimit()).isEqualTo(Val.unknown("C0.java:6: timeoutDuration(props.getTimeout()) "
                + "is not a literal duration in configureDefault of @Bean c"));
        assertThat(c.defaultConfig().breaker().failureRateThreshold().source()).isEqualTo(Source.LIBRARY_DEFAULT);
    }

    @Test
    void aConfigFromElsewhereIsUnknown() {
        Customizations c = reactive("""
                return f -> f.configureDefault(id -> new Resilience4JConfigBuilder(id)
                        .circuitBreakerConfig(sharedConfig)
                        .build());
                """);
        assertThat(c.defaultConfig().breaker().slidingWindowSize().source()).isEqualTo(Source.UNKNOWN);
        assertThat(c.defaultConfig().breaker().slidingWindowSize().evidence()).isEqualTo("C0.java:5: sharedConfig in "
                + "configureDefault of @Bean c is not CircuitBreakerConfig.ofDefaults() or a custom()...build() chain");
        assertThat(c.defaultConfig().timeLimit().source()).isEqualTo(Source.LIBRARY_DEFAULT);
    }

    @Test
    void whatCannotBeFollowedIsUnread() {
        assertThat(reactive("return f -> f.configure(b -> b, ids);").unread())
                .containsExactly("C0.java:4: configure in C0.java:2 (@Bean c, "
                        + "Customizer<ReactiveResilience4JCircuitBreakerFactory>) names its ids with something other "
                        + "than string literals; not read");
        assertThat(reactive("return f -> f.configureCircuitBreakerRegistry(registry);").unread())
                .singleElement().asString().contains("configureCircuitBreakerRegistry", "is not read");
        assertThat(reactive("Customizer<ReactiveResilience4JCircuitBreakerFactory> x = f -> {};\nreturn x;").unread())
                .anyMatch(u -> u.contains("not a single 'return factory -> ...' statement"));
        Customizations implemented = parse("""
                class Mine implements Customizer<ReactiveResilience4JCircuitBreakerFactory> {
                    public void customize(ReactiveResilience4JCircuitBreakerFactory f) { f.configureDefault(null); }
                }
                """).get(Factory.REACTIVE);
        assertThat(implemented.unread()).containsExactly("C0.java:1: Customizer<ReactiveResilience4JCircuitBreakerFactory> "
                + "outside a @Bean method returning a lambda is not read");
    }

    @Test
    void twoBeansSettingTheSameThingConflict() {
        String bean = """
                class C%d {
                    @Bean
                    Customizer<ReactiveResilience4JCircuitBreakerFactory> c%d() {
                        return f -> f.configureDefault(id -> new Resilience4JConfigBuilder(id).build());
                    }
                }
                """;
        Customizations c = parse(bean.formatted(1, 1), bean.formatted(2, 2)).get(Factory.REACTIVE);
        assertThat(c.unread()).singleElement().asString().contains("both call configureDefault",
                "the order of Customizer beans is not read");
    }

    @Test
    void eachFactoryHasItsOwnCustomizers() {
        Map<Factory, Customizations> all = parse("""
                class C {
                    @Bean
                    Customizer<Resilience4JCircuitBreakerFactory> blocking() {
                        return f -> f.configureDefault(id -> new Resilience4JConfigBuilder(id).build());
                    }
                }
                """);
        assertThat(all.get(Factory.BLOCKING).defaultConfig()).isNotNull();
        assertThat(all.get(Factory.REACTIVE).defaultConfig()).isNull();
        assertThat(all.get(Factory.REACTIVE).unread()).isEmpty();
    }
}
