package io.github.hasanyelaldi.plimsoll.collect.resilience4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.Source;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Provenance and error handling. Effective values are checked against real Spring Boot by the oracle. */
class Resilience4jConfigTest {

    private static Resilience4jConfig config(String yaml) {
        return Resilience4jConfig.of(SpringProperties.parse(yaml, "application.yml"), DefaultsKb.classpath());
    }

    @Test
    void configuredValuesAreExplicitWithFileAndLine() {
        RetryPolicy p = config("""
                resilience4j.retry.instances.orders:
                  max-attempts: 3
                  wait-duration: 100ms
                  enable-exponential-backoff: true
                  exponential-backoff-multiplier: 2
                  exponential-max-wait-duration: 1s
                  enable-randomized-wait: false
                  retry-exceptions: [java.io.IOException]
                """).retry("orders");

        assertThat(p.totalAttempts().value()).isEqualTo(3);
        assertThat(p.totalAttempts().source()).isEqualTo(Source.EXPLICIT);
        assertThat(p.totalAttempts().evidence()).isEqualTo("application.yml:2 (maxAttempts)");
        assertThat(p.initialBackoff().value()).isEqualTo(Duration.ofMillis(100));
        assertThat(p.initialBackoff().evidence()).isEqualTo("application.yml:3 (waitDuration)");
        assertThat(p.multiplier().value()).isEqualTo(2.0);
        assertThat(p.multiplier().evidence()).isEqualTo("application.yml:5");
        assertThat(p.maxBackoff().value()).isEqualTo(Duration.ofSeconds(1));
        assertThat(p.jitter().value()).isFalse();
        assertThat(p.jitter().source()).isEqualTo(Source.EXPLICIT);
        assertThat(p.retryOn().value()).containsExactly("java.io.IOException");
        assertThat(p.perTryTimeout().source()).isEqualTo(Source.UNKNOWN);
    }

    @Test
    void inheritedValuesCiteTheBaseConfigLine() {
        RetryPolicy p = config("""
                resilience4j.retry:
                  configs:
                    shared:
                      max-attempts: 5
                  instances:
                    orders:
                      base-config: shared
                """).retry("orders");

        assertThat(p.totalAttempts().value()).isEqualTo(5);
        assertThat(p.totalAttempts().evidence()).isEqualTo("application.yml:4 (maxAttempts)");
    }

    @Test
    void unconfiguredValuesAreVerifiedLibraryDefaults() {
        RetryPolicy p = config("resilience4j.retry.instances.orders.max-attempts: 2\n").retry("unconfigured");

        assertThat(p.totalAttempts().value()).isEqualTo(3);
        assertThat(p.totalAttempts().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(p.totalAttempts().evidence()).startsWith(
                "not configured; resilience4j-retry 2.4.0 default (verified by oracle), https://");
        assertThat(p.initialBackoff().value()).isEqualTo(Duration.ofMillis(500));
        assertThat(p.multiplier().value()).isEqualTo(1.0);
        assertThat(p.multiplier().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(p.jitter().value()).isFalse();
        assertThat(p.jitter().source()).isEqualTo(Source.LIBRARY_DEFAULT);
    }

    @Test
    void withoutAVerifiedKnowledgeBaseEntryTheValueIsUnknown() {
        Resilience4jConfig c = Resilience4jConfig.of(
                SpringProperties.parse("resilience4j.retry.instances.orders.wait-duration: 100ms\n", "application.yml"),
                DefaultsKb.of(Map.of()));
        RetryPolicy p = c.retry("orders");

        assertThat(p.totalAttempts().source()).isEqualTo(Source.UNKNOWN);
        assertThat(p.totalAttempts().evidence()).isEqualTo(
                "not configured; no verified resilience4j-retry default for maxAttempts");
        assertThat(p.initialBackoff().source()).isEqualTo(Source.EXPLICIT);
        assertThat(p.multiplier().source()).isEqualTo(Source.UNKNOWN);
        assertThat(p.jitter().source()).isEqualTo(Source.UNKNOWN);
    }

    @Test
    void placeholdersAreUnknownAndNamed() {
        RetryPolicy p = config("resilience4j.retry.instances.orders.max-attempts: ${RETRIES:3}\n").retry("orders");

        assertThat(p.totalAttempts().source()).isEqualTo(Source.UNKNOWN);
        assertThat(p.totalAttempts().evidence())
                .isEqualTo("application.yml:1: placeholder ${RETRIES:3} depends on the environment");
    }

    @Test
    void exponentialWithoutMultiplierUsesTheVerifiedDefault() {
        RetryPolicy p = config("""
                resilience4j.retry.instances.orders:
                  wait-duration: 100ms
                  enable-exponential-backoff: true
                """).retry("orders");

        assertThat(p.multiplier().value()).isEqualTo(1.5);
        assertThat(p.multiplier().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(p.maxBackoff().source()).isEqualTo(Source.UNKNOWN);
    }

    @Test
    void missingBaseConfigIsInvalid() {
        assertThatThrownBy(() -> config("resilience4j.retry.instances.orders.base-config: nope\n").retry("orders"))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessage("application.yml:1: baseConfig 'nope' not found under resilience4j.retry.configs");
    }

    @Test
    void baseConfigCycleIsInvalidAndTerminates() {
        assertThatThrownBy(() -> config("""
                resilience4j.retry.configs:
                  a: { base-config: b }
                  b: { base-config: a }
                resilience4j.retry.instances.orders.base-config: a
                """).retry("orders"))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessageContaining("baseConfig cycle");
    }

    @Test
    void maxAttemptsBelowOneIsInvalid() {
        assertThatThrownBy(() -> config("resilience4j.retry.instances.orders.max-attempts: 0\n").retry("orders"))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessage("application.yml:1: maxAttempts must be at least 1, got 0 (Resilience4j refuses to start)");
    }

    @Test
    void timeLimiterTimeoutAndAspectOrders() {
        Resilience4jConfig c = config("""
                resilience4j.timelimiter.instances.orders.timeout-duration: 800ms
                resilience4j.retry.retry-aspect-order: 10
                """);

        assertThat(c.timeLimiterTimeout("orders").value()).isEqualTo(Duration.ofMillis(800));
        assertThat(c.timeLimiterTimeout("orders").evidence()).isEqualTo("application.yml:1");
        assertThat(c.timeLimiterTimeout("other").value()).isEqualTo(Duration.ofSeconds(1));
        assertThat(c.timeLimiterTimeout("other").source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(c.retryAspectOrder().value()).isEqualTo(10);
        assertThat(c.retryAspectOrder().source()).isEqualTo(Source.EXPLICIT);
        assertThat(c.timeLimiterAspectOrder().value()).isEqualTo(Integer.MAX_VALUE - 2);
        assertThat(c.timeLimiterAspectOrder().source()).isEqualTo(Source.LIBRARY_DEFAULT);
    }
}
