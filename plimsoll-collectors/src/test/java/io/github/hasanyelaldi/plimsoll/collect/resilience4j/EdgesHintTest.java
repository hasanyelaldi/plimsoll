package io.github.hasanyelaldi.plimsoll.collect.resilience4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hasanyelaldi.plimsoll.collect.resilience4j.EdgesHint.Hint;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.EdgesHint.OperationHint;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class EdgesHintTest {

    @Test
    void readsDeadlineAndEntryOfItsOwnService() {
        EdgesHint hint = EdgesHint.parse("""
                service: orders
                deadline: 2s
                entry: false
                """, "edges-hint.yaml");
        assertThat(hint.deadline()).isEqualTo(Val.explicit(Duration.ofSeconds(2), "edges-hint.yaml:2"));
        assertThat(hint.entry()).isEqualTo(Val.explicit(false, "edges-hint.yaml:3"));
    }

    @Test
    void rejectsAnInvalidDeadlineOrEntry() {
        assertThatThrownBy(() -> EdgesHint.parse("service: a\ndeadline: soon\n", "h.yaml"))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessage("h.yaml:2: 'deadline' must be a positive duration such as 2s or 500ms, got 'soon'");
        assertThatThrownBy(() -> EdgesHint.parse("service: a\ndeadline: 0ms\n", "h.yaml"))
                .isInstanceOf(InvalidSpecException.class);
        assertThatThrownBy(() -> EdgesHint.parse("service: a\nentry: yes please\n", "h.yaml"))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessage("h.yaml:2: 'entry' must be true or false");
    }

    @Test
    void readsServiceAndMethodsWithProvenance() {
        EdgesHint hint = EdgesHint.parse("""
                service: gateway
                methods:
                  - method: p.Client#get
                    to: orders
                    operation: GET /orders
                    idempotent: true
                    fanout: 2
                    client: webclient-loadbalanced
                  - { method: p.Client#post, to: payments }
                """, "edges-hint.yaml");

        assertThat(hint.service()).isEqualTo("gateway");
        Hint get = hint.methods().get(0);
        assertThat(get.className()).isEqualTo("p.Client");
        assertThat(get.method()).isEqualTo("get");
        assertThat(get.to()).isEqualTo("orders");
        assertThat(get.operation()).isEqualTo("GET /orders");
        assertThat(get.idempotent()).isEqualTo(Val.explicit(true, "edges-hint.yaml:6"));
        assertThat(get.fanout()).isEqualTo(Val.explicit(2, "edges-hint.yaml:7"));
        assertThat(get.client()).isEqualTo(Val.explicit("webclient-loadbalanced", "edges-hint.yaml:8"));
        Hint post = hint.methods().get(1);
        assertThat(post.idempotent().source()).isEqualTo(Source.UNKNOWN);
        assertThat(post.fanout()).isEqualTo(Val.unknown("'fanout' absent (edges-hint.yaml:9)"));
        assertThat(hint.deadline()).isEqualTo(Val.unknown("'deadline' absent (edges-hint.yaml:1)"));
        assertThat(hint.entry()).isEqualTo(Val.unknown("'entry' absent (edges-hint.yaml:1)"));
        assertThat(post.operation()).isNull();
        assertThat(post.client()).isNull();
    }

    @Test
    void clientMustBeAKnownOne() {
        assertThatThrownBy(() -> EdgesHint.parse("""
                service: gateway
                methods:
                  - { method: p.C#m, to: orders, client: feign }
                """, "edges-hint.yaml"))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessage("edges-hint.yaml:3: 'client' must be one of [restclient, webclient-loadbalanced], got 'feign'");
    }

    @Test
    void methodMustBeClassHashMethod() {
        assertThatThrownBy(() -> EdgesHint.parse("""
                service: gateway
                methods:
                  - { method: p.Client.get, to: orders }
                """, "edges-hint.yaml"))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessage("edges-hint.yaml:3: method must be <fully.qualified.Class>#<method>, got 'p.Client.get'");
    }

    @Test
    void unknownKeysAreRejected() {
        assertThatThrownBy(() -> EdgesHint.parse("""
                service: gateway
                methods:
                  - { method: p.C#m, to: orders, retries: 3 }
                """, "edges-hint.yaml"))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessage("edges-hint.yaml:3: unknown key 'retries'");
    }

    @Test
    void readsOperationsWithExplicitIdempotency() {
        EdgesHint hint = EdgesHint.parse("""
                service: gateway
                operations:
                  - to: customers-service
                    operation: POST /owners
                    idempotent: true
                """, "edges-hint.yaml");

        assertThat(hint.methods()).isEmpty();
        assertThat(hint.operations()).containsExactly(new OperationHint("customers-service", "POST /owners",
                Val.explicit(true, "edges-hint.yaml:5"), "edges-hint.yaml:3"));
    }

    @Test
    void operationNeedsIdempotentAndKnownKeys() {
        assertThatThrownBy(() -> EdgesHint.parse("""
                service: gateway
                operations:
                  - { to: orders, operation: POST /orders }
                """, "edges-hint.yaml"))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessageContaining("idempotent");
        assertThatThrownBy(() -> EdgesHint.parse("""
                service: gateway
                operations:
                  - { to: orders, operation: POST /orders, idempotent: true, fanout: 2 }
                """, "edges-hint.yaml"))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessage("edges-hint.yaml:3: unknown key 'fanout'");
        assertThatThrownBy(() -> EdgesHint.parse("""
                service: gateway
                operations: { to: orders }
                """, "edges-hint.yaml"))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessage("edges-hint.yaml:2: operations must be a list");
    }
}
