package io.github.hasanyelaldi.plimsoll.collect.resilience4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hasanyelaldi.plimsoll.collect.resilience4j.EdgesHint.Hint;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import org.junit.jupiter.api.Test;

class EdgesHintTest {

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
        assertThat(get.fanout()).isEqualTo(2);
        assertThat(get.client()).isEqualTo(Val.explicit("webclient-loadbalanced", "edges-hint.yaml:8"));
        Hint post = hint.methods().get(1);
        assertThat(post.idempotent().source()).isEqualTo(Source.UNKNOWN);
        assertThat(post.fanout()).isEqualTo(1);
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
}
