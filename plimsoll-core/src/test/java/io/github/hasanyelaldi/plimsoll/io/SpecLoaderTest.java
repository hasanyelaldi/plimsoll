package io.github.hasanyelaldi.plimsoll.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hasanyelaldi.plimsoll.model.CircuitBreaker;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.FailureClass;
import io.github.hasanyelaldi.plimsoll.model.RetryConditions;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
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
                        retryOn: [503, reset]
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
        assertThat(p.retryOn().value()).containsExactlyInAnyOrder("503", "reset");
        RetryConditions c = e.layers().get(0).conditions();
        assertThat(c.retried()).isEqualTo(Val.explicit(Set.of(FailureClass.STATUS_503, FailureClass.RESET), "spec.yaml:16"));
        assertThat(c.givesUpAs().value()).isEmpty();
        assertThat(c.givesUpAs().source()).isEqualTo(Source.LIBRARY_DEFAULT);
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

    /** ADR 0014: retryOn names failure classes; how a failure passes on depends on the kind. */
    @Test
    void retryOnListsFailureClasses() {
        String head = HEAD + "edges:\n  - { from: a, to: b, layers: [ ";
        RetryConditions generic = SpecLoader.parse(head + "{ kind: generic, retryOn: [not-sent, status-5xx-other, 599] } ] }\n", "t")
                .edges().get(0).layers().get(0).conditions();
        assertThat(generic.retried().value()).containsExactly(FailureClass.NOT_SENT, FailureClass.STATUS_5XX_OTHER);
        assertThat(generic.givesUpAs()).isEqualTo(Val.explicit(Map.of(), "t:5 (a generic layer passes a failure on as it is)"));

        RetryConditions istio = SpecLoader.parse(head + "{ kind: istio, attempts: 2 } ] }\n", "t")
                .edges().get(0).layers().get(0).conditions();
        assertThat(istio.retried().source()).isEqualTo(Source.UNKNOWN);
        assertThat(istio.retried().evidence()).endsWith("every failure class is retried");
        assertThat(istio.givesUpAs().source()).isEqualTo(Source.UNKNOWN);

        assertInvalid(head + "{ kind: generic, retryOn: [connect-failure] } ] }\n", "unknown failure class 'connect-failure'");
        assertInvalid(head + "{ kind: generic, retryOn: [302] } ] }\n", "unknown failure class '302'");
    }

    /** ADR 0015: a layer may have a circuit breaker; an absent field is UNKNOWN. */
    @Test
    void aLayerMayHaveACircuitBreaker() {
        String yaml = HEAD + """
                edges:
                  - from: a
                    to: b
                    layers:
                      - kind: resilience4j
                        maxAttempts: 3
                        circuitBreaker:
                          name: orders
                          position: each-try
                          failureRateThreshold: 50
                          slidingWindowType: count-based
                          slidingWindowSize: 10
                          waitDurationInOpenState: 30s
                      - { kind: istio, attempts: 1 }
                """;
        Edge edge = SpecLoader.parse(yaml, "t.yaml").edges().get(0);
        CircuitBreaker b = edge.layers().get(0).breaker();
        assertThat(b.name()).isEqualTo(Val.explicit("orders", "t.yaml:11"));
        assertThat(b.position()).isEqualTo(Val.explicit(CircuitBreaker.Position.EACH_TRY, "t.yaml:12"));
        assertThat(b.failureRateThreshold()).isEqualTo(Val.explicit(50.0, "t.yaml:13"));
        assertThat(b.slidingWindowType()).isEqualTo(Val.explicit(CircuitBreaker.WindowType.COUNT_BASED, "t.yaml:14"));
        assertThat(b.slidingWindowSize()).isEqualTo(Val.explicit(10, "t.yaml:15"));
        assertThat(b.minimumNumberOfCalls()).isEqualTo(Val.unknown("'minimumNumberOfCalls' absent (t.yaml:11)"));
        assertThat(b.waitDurationInOpenState()).isEqualTo(Val.explicit(Duration.ofSeconds(30), "t.yaml:16"));
        assertThat(edge.layers().get(1).breaker()).isNull();
    }

    @Test
    void invalidCircuitBreakersAreRejected() {
        String head = HEAD + "edges:\n  - { from: a, to: b, layers: [ { kind: generic, circuitBreaker: ";
        assertInvalid(head + "{ position: outside } } ] }\n", "'position': unknown value 'outside' (expected wraps-all-tries or each-try)");
        assertInvalid(head + "{ slidingWindowType: sliding } } ] }\n", "expected count-based or time-based");
        assertInvalid(head + "{ failureRateThreshold: 0 } } ] }\n", "above 0 and at most 100, got 0");
        assertInvalid(head + "{ failureRateThreshold: 101 } } ] }\n", "above 0 and at most 100, got 101");
        assertInvalid(head + "{ slidingWindowSize: 0 } } ] }\n", "'slidingWindowSize' must be at least 1");
        assertInvalid(head + "{ halfOpenCalls: 3 } } ] }\n", "unknown key 'halfOpenCalls' in circuitBreaker");
        assertInvalid(head + "true } ] }\n", "circuitBreaker must be a mapping");
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

    /** ADR 0019. */
    @Test
    void aServiceMayBeExternalButNeitherAnEntryNorACaller() {
        var g = SpecLoader.parse("""
                services: { a: { entry: true }, llm: { external: true } }
                edges: [ { from: a, to: llm } ]
                """, "t.yaml");
        assertThat(g.services().get("llm").external()).isTrue();
        assertThat(g.services().get("llm").deadline().isKnown()).isFalse();
        assertThat(g.services().get("a").external()).isFalse();
        assertInvalid("services: { a: { entry: true, external: true } }\n", "t.yaml:1: service a is external and "
                + "cannot be an entry (ADR 0019)");
        assertInvalid("""
                services: { a: { entry: true }, llm: { external: true } }
                edges: [ { from: llm, to: a } ]
                """, "edge llm→a starts at external service 'llm', which has no outgoing edges (ADR 0019)");
    }

    /** #19: Spring AI passes max-attempts to maxRetries, so it counts retries only. */
    @Test
    void springAiMaxAttemptsCountRetriesOnly() {
        var g = SpecLoader.parse("""
                services: { a: { entry: true }, llm: { external: true } }
                edges: [ { from: a, to: llm, layers: [ { kind: spring-ai, maxAttempts: 10 } ] } ]
                """, "t.yaml");
        assertThat(g.edges().get(0).layers().get(0).retry().totalAttempts())
                .isEqualTo(io.github.hasanyelaldi.plimsoll.model.Val.explicit(11, "t.yaml:2 (maxAttempts 10 + 1)"));
    }
}
