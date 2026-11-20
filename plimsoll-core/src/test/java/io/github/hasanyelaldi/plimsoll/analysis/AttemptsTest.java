package io.github.hasanyelaldi.plimsoll.analysis;

import static io.github.hasanyelaldi.plimsoll.model.FailureClass.NOT_SENT;
import static io.github.hasanyelaldi.plimsoll.model.FailureClass.RESET;
import static io.github.hasanyelaldi.plimsoll.model.FailureClass.STATUS_503;
import static io.github.hasanyelaldi.plimsoll.model.FailureClass.STATUS_504;
import static io.github.hasanyelaldi.plimsoll.model.FailureClass.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.FailureClass;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.RetryConditions;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@code E_c(e)} of ADR 0014 through the layers of one edge. */
class AttemptsTest {

    private static final Map<FailureClass, Set<FailureClass>> ISTIO = Map.of(
            NOT_SENT, Set.of(STATUS_503), RESET, Set.of(STATUS_503), TIMEOUT, Set.of(STATUS_504));

    private static Layer layer(int attempts, Val<Set<FailureClass>> retried, Val<Map<FailureClass, Set<FailureClass>>> as) {
        Val<Integer> total = attempts == 0 ? Val.unknown("absent") : Val.explicit(attempts, "spec:1");
        RetryPolicy retry = new RetryPolicy(total, Val.unknown("x"), Val.unknown("x"), Val.unknown("x"),
                Val.unknown("x"), Val.unknown("x"), Val.unknown("x"));
        return new Layer("generic", retry, Val.unknown("x"), new RetryConditions(retried, as));
    }

    private static Layer layer(int attempts, Map<FailureClass, Set<FailureClass>> as, FailureClass... retried) {
        return layer(attempts, Val.explicit(Set.of(retried), "spec:2"), Val.explicit(as, "spec:3"));
    }

    private static Edge edge(Layer... outermostFirst) {
        return new Edge("a", "b", null, Val.explicit(false, "spec:4"), Val.explicit(1, "spec:4"),
                Arrays.asList(outermostFirst));
    }

    @Test
    void aLayerMultipliesOnlyForTheClassesItRetries() {
        Edge e = edge(layer(3, Map.of(), NOT_SENT));
        assertThat(Attempts.tries(e, NOT_SENT)).isEqualTo(3);
        assertThat(Attempts.tries(e, RESET)).isEqualTo(1);
    }

    @Test
    void theOuterLayerSeesWhatTheInnerLayerAnswers() {
        // outer retries 503 only; inner (Istio-like) retries not-sent and answers 503 when it gives up
        Edge e = edge(layer(2, Map.of(), STATUS_503), layer(3, ISTIO, NOT_SENT));
        assertThat(Attempts.tries(e, NOT_SENT)).isEqualTo(6);
        assertThat(Attempts.tries(e, RESET)).isEqualTo(2);
        assertThat(Attempts.tries(e, STATUS_503)).isEqualTo(2);
        assertThat(Attempts.tries(e, TIMEOUT)).isEqualTo(1);
    }

    @Test
    void unknownConditionsRetryAndPassOnEveryClass() {
        Edge e = edge(layer(2, Map.of(), STATUS_504),
                layer(3, Val.unknown("retryOn absent"), Val.unknown("not modelled")));
        for (FailureClass c : FailureClass.values()) {
            assertThat(Attempts.tries(e, c)).as(c.id()).isEqualTo(6);
        }
        assertThat(Attempts.conditionsKnown(e)).isFalse();
    }

    @Test
    void unknownAttemptCountsAreLeftOut() {
        Edge e = edge(layer(0, Map.of(), RESET), layer(3, Map.of(), RESET));
        assertThat(Attempts.tries(e, RESET)).isEqualTo(3);
    }

    @Test
    void conditionValuesShowTheKnownClassesAndTranslations() {
        Edge e = edge(layer(2, Map.of(), STATUS_503), layer(3, ISTIO, NOT_SENT, RESET));
        assertThat(Attempts.conditionsKnown(e)).isTrue();
        assertThat(Attempts.conditionValues("a→b", e)).extracting(UsedValue::name, UsedValue::display).containsExactly(
                tuple("a→b layer 1 (generic) retried", "status-503"),
                tuple("a→b layer 2 (generic) retried", "not-sent, reset"),
                tuple("a→b layer 2 (generic) gives up as",
                        "not-sent as status-503; reset as status-503; timeout as status-504"));
        assertThat(Attempts.classes(Set.of())).isEqualTo("none");
        assertThat(Attempts.givesUpAs(Map.of())).isEqualTo("every class as is");
    }

    @Test
    void aLayerThatTriesOnceNeedsNoRetriedClasses() {
        Val<Set<FailureClass>> unknown = Val.unknown("retry off");
        assertThat(Attempts.conditionsKnown(edge(layer(2, Map.of(), RESET), layer(1, unknown, Val.explicit(Map.of(), "x")))))
                .isTrue();
        assertThat(Attempts.conditionsKnown(edge(layer(2, Map.of(), RESET), layer(1, unknown, Val.unknown("x")))))
                .isFalse();
        assertThat(Attempts.conditionsKnown(edge(layer(2, unknown, Val.explicit(Map.of(), "x"))))).isFalse();
    }
}
