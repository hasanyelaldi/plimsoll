package io.github.hasanyelaldi.plimsoll.collect.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpMethodsTest {

    private static final DefaultsKb KB = DefaultsKb.classpath();

    @Test
    void rfc9110MethodsAreIdempotentExceptPostAndPatch() {
        for (String m : List.of("GET", "HEAD", "OPTIONS", "TRACE", "PUT", "DELETE")) {
            Val<Boolean> v = HttpMethods.idempotent(m, "Foo.java:3", KB);
            assertThat(v.value()).as(m).isTrue();
            assertThat(v.source()).isEqualTo(Source.LIBRARY_DEFAULT);
            assertThat(v.evidence()).startsWith(m + " (Foo.java:3); http RFC 9110 default (verified by doc)")
                    .contains("rfc9110#section-9.2.2");
        }
        assertThat(HttpMethods.idempotent("POST", "x", KB).value()).isFalse();
        Val<Boolean> patch = HttpMethods.idempotent("PATCH", "x", KB);
        assertThat(patch.value()).isFalse();
        assertThat(patch.evidence()).contains("rfc5789#section-2");
    }

    @Test
    void methodWithoutVerifiedEntryIsUnknown() {
        Val<Boolean> v = HttpMethods.idempotent("POST", "x", DefaultsKb.of(Map.of()));
        assertThat(v.source()).isEqualTo(Source.UNKNOWN);
        assertThat(v.evidence()).contains("no verified http default for idempotent.POST");
    }

    @Test
    void operationStartsWithAMethodOrNot() {
        assertThat(HttpMethods.ofOperation("GET /owners/{id}")).contains("GET");
        assertThat(HttpMethods.ofOperation("CustomersClient#getOwner")).isEmpty();
        assertThat(HttpMethods.ofOperation("get /owners")).isEmpty();
        assertThat(HttpMethods.ofOperation(null)).isEmpty();
        assertThat(HttpMethods.normalize("RequestMethod.PATCH")).contains("PATCH");
        assertThat(HttpMethods.normalize("post")).contains("POST");
        assertThat(HttpMethods.normalize("retrieve")).isEmpty();
    }

    @Test
    void allIsTheWorstOfItsParts() {
        Val<Boolean> get = HttpMethods.idempotent("GET", "a", KB);
        Val<Boolean> post = HttpMethods.idempotent("POST", "b", KB);
        Val<Boolean> unknown = Val.unknown("method of c not known");

        assertThat(HttpMethods.all(List.of(get, unknown, post), "none")).isEqualTo(post);
        assertThat(HttpMethods.all(List.of(get, unknown), "none")).isEqualTo(unknown);
        Val<Boolean> both = HttpMethods.all(List.of(get, Val.explicit(true, "hint:4")), "none");
        assertThat(both.value()).isTrue();
        assertThat(both.source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(both.evidence()).contains("GET (a)", "hint:4");
        assertThat(HttpMethods.all(List.of(), "no operations").evidence()).isEqualTo("no operations");
    }
}
