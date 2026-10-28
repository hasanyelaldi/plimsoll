package io.github.hasanyelaldi.plimsoll.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import io.github.hasanyelaldi.plimsoll.model.Source;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SpecLoaderTest {

    private static final String HEAD = """
            services:
              a: { entry: true, deadline: 3s }
              b: { deadline: 500ms }
            """;

    private static RetryPolicy onlyPolicy(String layer) {
        ServiceGraph g = SpecLoader.parse(HEAD + "edges:\n  - { from: a, to: b, layers: [ " + layer + " ] }\n", "t.yaml");
        return g.edges().get(0).layers().get(0).retry();
    }

    @Test
    void resilience4jMaxAttemptsIsAlreadyTotal() {
        RetryPolicy p = onlyPolicy("{ kind: resilience4j, maxAttempts: 3 }");
        assertThat(p.totalAttempts().value()).isEqualTo(3);
        assertThat(p.totalAttempts().source()).isEqualTo(Source.EXPLICIT);
    }

    @Test
    void istioAttemptsCountRetriesOnly() {
        RetryPolicy p = onlyPolicy("{ kind: istio, attempts: 2 }");
        assertThat(p.totalAttempts().value()).isEqualTo(3);
        assertThat(p.totalAttempts().evidence()).isEqualTo("t.yaml:5 (attempts 2 + 1)");
    }

    @Test
    void genericTotalAttemptsIsTakenAsNamed() {
        assertThat(onlyPolicy("{ kind: generic, totalAttempts: 4 }").totalAttempts().value()).isEqualTo(4);
        assertThat(onlyPolicy("{ kind: http-client, totalAttempts: 2 }").totalAttempts().value()).isEqualTo(2);
    }

    @Test
    void absentMultiplierMeansFixedBackoffAndEverythingElseIsUnknown() {
        RetryPolicy p = onlyPolicy("{ kind: generic }");
        assertThat(p.multiplier().value()).isEqualTo(1.0);
        assertThat(p.totalAttempts().source()).isEqualTo(Source.UNKNOWN);
        assertThat(p.totalAttempts().value()).isNull();
        assertThat(p.totalAttempts().evidence()).contains("'totalAttempts' absent");
        assertThat(p.perTryTimeout().source()).isEqualTo(Source.UNKNOWN);
        assertThat(p.initialBackoff().source()).isEqualTo(Source.UNKNOWN);
        assertThat(p.maxBackoff().source()).isEqualTo(Source.UNKNOWN);
        assertThat(p.jitter().source()).isEqualTo(Source.UNKNOWN);
        assertThat(p.retryOn().source()).isEqualTo(Source.UNKNOWN);
    }

    @Test
    void valuesCarryFileAndLine() {
        ServiceGraph g = SpecLoader.parse("""
                services:
                  a: { entry: true, deadline: 3s }
                  b: {}
                edges:
                  - from: a
                    to: b
                    idempotent: false
                    layers:
                      - kind: resilience4j
                        maxAttempts: 3
                        perTryTimeout: 1s
                        backoff: 100ms
                        multiplier: 2
                        maxBackoff: 1s
                        jitter: true
                        retryOn: [503, ioerror]
                """, "spec.yaml");
        Edge e = g.edges().get(0);
        assertThat(e.idempotent().value()).isFalse();
        assertThat(e.idempotent().evidence()).isEqualTo("spec.yaml:7");
        RetryPolicy p = e.layers().get(0).retry();
        assertThat(p.perTryTimeout().value()).isEqualTo(Duration.ofSeconds(1));
        assertThat(p.perTryTimeout().evidence()).isEqualTo("spec.yaml:11");
        assertThat(p.initialBackoff().value()).isEqualTo(Duration.ofMillis(100));
        assertThat(p.multiplier().value()).isEqualTo(2.0);
        assertThat(p.jitter().value()).isTrue();
        assertThat(p.retryOn().value()).containsExactlyInAnyOrder("503", "ioerror");
        assertThat(g.services().get("a").deadline().evidence()).isEqualTo("spec.yaml:2");
        assertThat(g.services().get("b").deadline().source()).isEqualTo(Source.UNKNOWN);
        assertThat(g.services().get("a").entry()).isTrue();
        assertThat(g.services().get("b").entry()).isFalse();
    }

    @Test
    void fanoutDefaultsToOne() {
        ServiceGraph g = SpecLoader.parse(HEAD + "edges:\n  - { from: a, to: b, fanout: 4 }\n  - { from: a, to: b }\n", "t");
        assertThat(g.edges()).extracting(e -> e.fanout().value()).containsExactly(4, 1);
        assertThat(g.edges()).extracting(e -> e.fanout().source()).containsOnly(Source.EXPLICIT);
        assertThat(g.edges().get(0).fanout().evidence()).isEqualTo("t:5");
        assertThat(g.edges().get(1).fanout().evidence()).isEqualTo("'fanout' absent (t:6), spec default 1");
    }

    @Test
    void invalidInputIsRejectedWithFileAndLine() {
        assertInvalid("", "empty document");
        assertInvalid("services: [", "invalid YAML");
        assertInvalid("- a\n- b\n", "spec must be a mapping");
        assertInvalid("services: { a: {} }\n", "no entry service");
        assertInvalid(HEAD + "typo: 1\n", "t.yaml:4: unknown key 'typo'");
        assertInvalid(HEAD + "edges:\n  - { from: a, to: nope }\n", "unknown service 'nope'");
        assertInvalid(HEAD + "edges:\n  - { from: a, to: b, layers: [ { kind: carrier-pigeon } ] }\n", "unknown layer kind");
        assertInvalid(HEAD + "edges:\n  - { from: a, to: b, layers: [ { kind: istio, maxAttempts: 3 } ] }\n", "does not apply to kind 'istio'");
        assertInvalid(HEAD + "edges:\n  - { from: a, to: b, layers: [ { kind: generic, totalAttempts: 0 } ] }\n", "at least 1");
        assertInvalid(HEAD + "edges:\n  - { from: a, to: b, layers: [ { kind: generic, perTryTimeout: 300 } ] }\n", "must be a duration string");
        assertInvalid(HEAD + "edges:\n  - { from: a, to: b, layers: [ { kind: generic, perTryTimeout: 3h } ] }\n", "invalid timeout '3h'");
        assertInvalid("services:\n  a: { entry: true, deadline: unlimited }\n", "invalid duration 'unlimited'");
        assertInvalid(HEAD + "edges:\n  - { from: a, to: b, layers: [ { kind: generic, backoff: unlimited } ] }\n", "invalid duration 'unlimited'");
        assertInvalid(HEAD + "edges:\n  - { from: a, to: b, layers: [ { kind: generic, multiplier: 0.5 } ] }\n", "finite number >= 1");
        assertInvalid(HEAD + "edges:\n  - { from: a, to: b, fanout: 0 }\n", "at least 1");
        assertInvalid("services:\n  a: { entry: true }\n  a: { entry: true }\n", "duplicate key 'a'");
    }

    @Test
    void missingFileIsInvalidInput() {
        assertThatThrownBy(() -> SpecLoader.load(java.nio.file.Path.of("does-not-exist.yaml")))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessageContaining("cannot read spec");
    }

    private static void assertInvalid(String yaml, String messagePart) {
        assertThatThrownBy(() -> SpecLoader.parse(yaml, "t.yaml"))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessageContaining(messagePart);
    }
}
