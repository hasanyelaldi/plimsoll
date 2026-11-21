package io.github.hasanyelaldi.plimsoll.collect.circuitbreaker;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hasanyelaldi.plimsoll.collect.circuitbreaker.BreakerCallScanner.Result;
import io.github.hasanyelaldi.plimsoll.collect.circuitbreaker.BreakerCallScanner.Wrap;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BreakerCallScannerTest {

    private static Result parse(String source) {
        return BreakerCallScanner.parse(Map.of("C.java", source));
    }

    @Test
    void petclinicWrapsTheVisitsClientThroughTransform() {
        Path gw = Path.of("src/test/resources/projects/petclinic-gateway-clients");
        Result r = BreakerCallScanner.scan(gw, gw.resolve("src/main/java"));
        assertThat(r.notes()).isEmpty();
        assertThat(r.wraps()).singleElement().satisfies(w -> {
            assertThat(w.id()).isEqualTo("getOwnerDetails");
            assertThat(w.create()).isEqualTo("src/main/java/org/springframework/samples/petclinic/api/boundary/web/"
                    + "ApiGatewayController.java:61");
            assertThat(w.wraps()).isEqualTo("visitsServiceClient.getVisitsForPets(owner.getPetIds())");
            assertThat(w.target()).isEqualTo(
                    "org.springframework.samples.petclinic.api.application.VisitsServiceClient#getVisitsForPets");
            assertThat(w.fallback()).isEqualTo("throwable -> emptyVisitsForPets()");
            assertThat(w.covers("org.springframework.samples.petclinic.api.application.VisitsServiceClient"
                    + "#getVisitsForPets", "src/main/java/org/springframework/samples/petclinic/api/application/"
                    + "VisitsServiceClient.java:44")).isTrue();
            assertThat(w.covers("org.springframework.samples.petclinic.api.application.CustomersServiceClient"
                    + "#getOwner", "x.java:1")).isFalse();
        });
    }

    @Test
    void anInlineRunWrapsTheRequestWrittenInsideIt() {
        Result r = parse("""
                class C {
                    ReactiveCircuitBreakerFactory f;
                    WebClient web;
                    Mono<String> get() {
                        return f.create("inline").run(
                                web.get().uri("http://a-service/x").retrieve().bodyToMono(String.class),
                                null);
                    }
                }
                """);
        Wrap w = r.wraps().get(0);
        assertThat(w.id()).isEqualTo("inline");
        assertThat(w.fallback()).isNull();
        assertThat(w.from()).isEqualTo(6);
        assertThat(w.covers("C#get", "C.java:6")).isTrue();
        assertThat(w.covers("C#get", "C.java:9")).isFalse();
    }

    @Test
    void aLocalVariableAndAMethodOfTheSameClassAreFollowed() {
        Result r = parse("""
                package p;
                class C {
                    private final ReactiveResilience4JCircuitBreakerFactory factory;
                    Mono<String> get() {
                        ReactiveCircuitBreaker cb = this.factory.create("local");
                        Mono<String> m = fetch();
                        return cb.run(m, t -> Mono.empty());
                    }
                    Mono<String> fetch() { return null; }
                }
                """);
        assertThat(r.wraps()).singleElement().satisfies(w -> {
            assertThat(w.wraps()).isEqualTo("fetch()");
            assertThat(w.target()).isEqualTo("p.C#fetch");
        });
    }

    @Test
    void whatIsNotModelledIsNoted() {
        Result r = parse("""
                class C {
                    CircuitBreakerFactory blocking;
                    ReactiveCircuitBreakerFactory reactive;
                    void a() { blocking.create("b").run(() -> 1); }
                    void b() { reactive.create("g", "group").run(Mono.just(1)); }
                    ReactiveCircuitBreaker c() { return reactive.create("kept"); }
                    void d(String id) { reactive.create(id).run(Mono.just(1)); }
                }
                """);
        assertThat(r.notes()).containsExactly(
                "C.java:4: blocking.create(\"b\") on a blocking CircuitBreakerFactory is not modelled; only the "
                        + "reactive factories are (#18)",
                "C.java:5: reactive.create(\"g\", \"group\") names a group; create(id, group) is not modelled",
                "C.java:6: reactive.create(\"kept\") is not run where it is created; the calls it wraps are not known");
        assertThat(r.wraps()).singleElement().satisfies(w -> assertThat(w.id()).isNull());
    }

    @Test
    void otherCreateCallsAreIgnored() {
        Result r = parse("""
                class C {
                    OrderFactory orders;
                    void a() { orders.create("x"); URI.create("http://a"); }
                }
                """);
        assertThat(r.wraps()).isEmpty();
        assertThat(r.notes()).isEmpty();
    }
}
