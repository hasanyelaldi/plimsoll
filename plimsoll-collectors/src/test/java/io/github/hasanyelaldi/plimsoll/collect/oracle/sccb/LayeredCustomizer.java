package io.github.hasanyelaldi.plimsoll.collect.oracle.sccb;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Oracle fixture, parsed by {@code CustomizerScanner} and run by Spring with {@code oracle/sccb-layered.yml}: a
 * {@code configureDefault} that sets only the TimeLimiter, a {@code configure} for some ids, and a customizer of the
 * blocking factory that the reactive one must not see.
 */
@Configuration(proxyBeanMethods = false)
public class LayeredCustomizer {

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> layered() {
        return factory -> {
            factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                    .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(2000)).build())
                    .build());
            factory.configure(builder -> builder
                    .circuitBreakerConfig(CircuitBreakerConfig.custom()
                            .failureRateThreshold(30)
                            .slidingWindow(7, 5, SlidingWindowType.TIME_BASED)
                            .waitDurationInOpenState(Duration.ofSeconds(20))
                            .permittedNumberOfCallsInHalfOpenState(2)
                            .build())
                    .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(3)).build()),
                    "configured", "yamlConfig", "yamlInstance", "tlInstance");
        };
    }

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> blocking() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(7)).build())
                .build());
    }
}
