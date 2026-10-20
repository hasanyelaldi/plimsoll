package io.github.hasanyelaldi.plimsoll.collect.resilience4j;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hasanyelaldi.plimsoll.collect.resilience4j.Resilience4jCollector.Result;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Edge idempotency from the HTTP method, on the fixture project {@code projects/http-methods}. */
class HintIdempotencyTest {

    private static final String CLIENT = "src/main/java/com/example/owners/OwnersClient.java";
    private static Result result;

    @BeforeAll
    static void collect() throws URISyntaxException {
        Path root = Path.of(HintIdempotencyTest.class.getResource("/projects/http-methods").toURI());
        result = Resilience4jCollector.collect(root, root.resolve("edges-hint.yaml"));
    }

    private static Val<Boolean> idempotent(int hint) {
        Edge e = result.edges().get(hint);
        return e.idempotent();
    }

    @Test
    void webClientPostIsNotIdempotent() {
        Val<Boolean> v = idempotent(0);
        assertThat(v.value()).isFalse();
        assertThat(v.source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(v.evidence()).startsWith("POST (" + CLIENT + ":21); http RFC 9110 default (verified by doc)");
    }

    @Test
    void restClientMethodConstantIsRead() {
        assertThat(idempotent(1).value()).isTrue();
        assertThat(idempotent(1).evidence()).startsWith("GET (" + CLIENT + ":28)");
    }

    @Test
    void methodFromAnExpressionIsUnknown() {
        Val<Boolean> v = idempotent(2);
        assertThat(v.source()).isEqualTo(Source.UNKNOWN);
        assertThat(v.evidence()).contains("'idempotent' absent", CLIENT + ":32: HTTP method is not a constant: method");
    }

    @Test
    void operationAndCallThatDisagreeAreUnknownAndNoted() {
        Val<Boolean> v = idempotent(3);
        assertThat(v.source()).isEqualTo(Source.UNKNOWN);
        assertThat(v.evidence()).contains("HTTP method differs: POST at edges-hint.yaml:6 operation, GET at "
                + CLIENT + ":36");
        assertThat(result.notes()).anyMatch(n -> n.startsWith(CLIENT + ":36: com.example.owners.OwnersClient#mismatch "
                + "calls GET but its hint operation (edges-hint.yaml:6) says POST"));
    }

    @Test
    void explicitHintOverridesTheMethod() {
        assertThat(idempotent(4)).isEqualTo(Val.explicit(true, "edges-hint.yaml:8"));
    }

    @Test
    void operationPrefixGivesTheMethodWithoutACall() {
        assertThat(idempotent(5).value()).isTrue();
        assertThat(idempotent(5).evidence()).startsWith("DELETE (edges-hint.yaml:9 operation)");
    }

    @Test
    void noMethodAnywhereIsUnknown() {
        Val<Boolean> v = idempotent(6);
        assertThat(v.source()).isEqualTo(Source.UNKNOWN);
        assertThat(v.evidence()).contains("no HTTP method", "makes no WebClient / RestClient call");
    }

    @Test
    void severalCallsAreIdempotentOnlyIfAllAre() {
        Val<Boolean> v = idempotent(7);
        assertThat(v.value()).isTrue();
        assertThat(v.evidence()).contains("GET (" + CLIENT + ":42)", "PUT (" + CLIENT + ":43)");
    }
}
