package io.github.hasanyelaldi.plimsoll.collect.resilience4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hasanyelaldi.plimsoll.collect.resilience4j.AnnotationScanner.Kind;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.AnnotationScanner.ScannedClass;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.AnnotationScanner.ScannedMethod;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnnotationScannerTest {

    private static ScannedMethod method(List<ScannedClass> classes, String cls, String name) {
        return classes.stream().filter(c -> c.name().equals(cls)).findFirst().orElseThrow()
                .methods().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void findsTheThreeAnnotationsWithNameAndLine() {
        List<ScannedClass> classes = AnnotationScanner.parse("""
                package p;

                import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
                import io.github.resilience4j.retry.annotation.Retry;
                import io.github.resilience4j.timelimiter.annotation.TimeLimiter;

                class Client {
                    @Retry(name = "a")
                    @CircuitBreaker(name = "b", fallbackMethod = "fallback")
                    @TimeLimiter(name = "c")
                    java.util.concurrent.CompletableFuture<String> call() { return null; }
                }
                """, "Client.java");

        ScannedMethod call = method(classes, "p.Client", "call");
        assertThat(call.annotations().get(Kind.RETRY)).isEqualTo(Val.explicit("a", "Client.java:8"));
        assertThat(call.annotations().get(Kind.CIRCUIT_BREAKER)).isEqualTo(Val.explicit("b", "Client.java:9"));
        assertThat(call.annotations().get(Kind.TIME_LIMITER)).isEqualTo(Val.explicit("c", "Client.java:10"));
        assertThat(call.returnType()).isEqualTo("CompletableFuture");
        assertThat(call.line()).isEqualTo(11);
    }

    @Test
    void wildcardAndQualifiedNamesCountButLookAlikesDoNot() {
        List<ScannedClass> classes = AnnotationScanner.parse("""
                package p;

                import io.github.resilience4j.retry.annotation.*;

                class A {
                    @Retry(name = "wild") void wild() {}
                    @io.github.resilience4j.timelimiter.annotation.TimeLimiter(name = "q") void qualified() {}
                }
                """, "A.java");
        List<ScannedClass> others = AnnotationScanner.parse("""
                package p;

                import com.example.Retry;

                class B {
                    @Retry(name = "x") void custom() {}
                    @CircuitBreaker(name = "y") void notImported() {}
                }
                """, "B.java");

        assertThat(method(classes, "p.A", "wild").annotations()).containsOnlyKeys(Kind.RETRY);
        assertThat(method(classes, "p.A", "qualified").annotations()).containsOnlyKeys(Kind.TIME_LIMITER);
        assertThat(method(others, "p.B", "custom").annotations()).isEmpty();
        assertThat(method(others, "p.B", "notImported").annotations()).isEmpty();
    }

    @Test
    void nonLiteralNameIsUnknown() {
        List<ScannedClass> classes = AnnotationScanner.parse("""
                import io.github.resilience4j.retry.annotation.Retry;

                class C {
                    static final String N = "n";
                    @Retry(name = N) void call() {}
                }
                """, "C.java");

        Val<String> name = method(classes, "C", "call").annotations().get(Kind.RETRY);
        assertThat(name.source()).isEqualTo(Source.UNKNOWN);
        assertThat(name.evidence()).isEqualTo("C.java:5: name is not a string literal: N");
    }

    @Test
    void classLevelAnnotationsAndNestedTypes() {
        List<ScannedClass> classes = AnnotationScanner.parse("""
                package p;

                import io.github.resilience4j.retry.annotation.Retry;

                @Retry(name = "outer")
                class Outer {
                    static class Inner {
                        void call() {}
                    }
                }
                """, "Outer.java");

        assertThat(classes).extracting(ScannedClass::name).containsExactlyInAnyOrder("p.Outer", "p.Outer.Inner");
        ScannedClass outer = classes.stream().filter(c -> c.name().equals("p.Outer")).findFirst().orElseThrow();
        assertThat(outer.annotations().get(Kind.RETRY)).isEqualTo(Val.explicit("outer", "Outer.java:5"));
    }

    @Test
    void unparsableSourceIsInvalidInput() {
        assertThatThrownBy(() -> AnnotationScanner.parse("class {", "Broken.java"))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessageStartingWith("Broken.java: cannot parse Java:");
    }
}
