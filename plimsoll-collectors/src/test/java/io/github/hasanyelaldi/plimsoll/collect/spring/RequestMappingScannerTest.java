package io.github.hasanyelaldi.plimsoll.collect.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hasanyelaldi.plimsoll.collect.spring.RequestMappingScanner.Mapping;
import io.github.hasanyelaldi.plimsoll.collect.spring.RequestMappingScanner.Result;
import io.github.hasanyelaldi.plimsoll.model.Source;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RequestMappingScannerTest {

    private static final Path CUSTOMERS = Path.of("src/test/resources/projects/petclinic-customers-service");

    private static Result parse(String body) {
        return RequestMappingScanner.parse("""
                package x;
                import org.springframework.web.bind.annotation.*;
                """ + body, "X.java");
    }

    @Test
    void petclinicCustomersServiceMappings() {
        Result r = RequestMappingScanner.scan(CUSTOMERS);
        assertThat(r.mappings()).extracting(Mapping::operation).containsExactly(
                "POST /owners", "GET /owners/{ownerId}", "GET /owners", "PUT /owners/{ownerId}",
                "GET /petTypes", "POST /owners/{ownerId}/pets", "PUT /owners/*/pets/{petId}",
                "GET /owners/*/pets/{petId}");
        Mapping create = r.mappings().get(0);
        String file = "src/main/java/org/springframework/samples/petclinic/customers/web/OwnerResource.java";
        assertThat(create.evidence()).isEqualTo(file + ":58");
        assertThat(create.method().source()).isEqualTo(Source.EXPLICIT);
        assertThat(create.method().evidence()).isEqualTo(file + ":58");
        assertThat(r.notes()).isEmpty();
    }

    @Test
    void requestMappingMethodsAndArrays() {
        Result r = parse("""
                @RestController
                @RequestMapping({"/a", "/b/"})
                class C {
                    @RequestMapping(path = "x", method = {RequestMethod.GET, RequestMethod.PATCH})
                    void m() {}
                    @RequestMapping("/any")
                    void any() {}
                }
                """);
        assertThat(r.mappings()).extracting(Mapping::operation).containsExactly(
                "GET /a/x", "PATCH /a/x", "GET /b/x", "PATCH /b/x", "* /a/any", "* /b/any");
        assertThat(r.mappings().get(4).method().source()).isEqualTo(Source.UNKNOWN);
        assertThat(r.mappings().get(4).method().evidence()).contains("serves every HTTP method");
    }

    @Test
    void onlyControllersCount() {
        Result r = RequestMappingScanner.parse("""
                package x;
                import org.springframework.web.bind.annotation.GetMapping;
                interface Feign {
                    @GetMapping("/owners") String owners();
                }
                @org.springframework.stereotype.Controller
                class Page {
                    @GetMapping("/") String home() { return null; }
                }
                @RestController
                class NotImported {
                    @GetMapping("/no") String no() { return null; }
                }
                """, "X.java");
        assertThat(r.mappings()).extracting(Mapping::operation).containsExactly("GET /");
    }

    @Test
    void lookAlikeAnnotationsAreIgnored() {
        Result r = RequestMappingScanner.parse("""
                package x;
                import org.springframework.web.bind.annotation.RestController;
                import com.example.GetMapping;
                @RestController
                class C {
                    @GetMapping("/x") void x() {}
                }
                """, "X.java");
        assertThat(r.mappings()).isEmpty();
    }

    @Test
    void nonLiteralPathsAreSkippedAndNoted() {
        Result r = parse("""
                @RestController
                class C {
                    static final String P = "/p";
                    @PostMapping(P) void p() {}
                    @DeleteMapping(path = "/d") void d() {}
                }
                """);
        assertThat(r.mappings()).extracting(Mapping::operation).containsExactly("DELETE /d");
        assertThat(r.notes()).containsExactly("X.java:6: mapping path of p is not a string literal; skipped");
    }

    @Test
    void pathsJoinAsSpringDoes() {
        assertThat(RequestMappingScanner.join("", "")).isEqualTo("/");
        assertThat(RequestMappingScanner.join("/owners", "")).isEqualTo("/owners");
        assertThat(RequestMappingScanner.join("", "owners/*/pets")).isEqualTo("/owners/*/pets");
        assertThat(RequestMappingScanner.join("/a/", "/b/")).isEqualTo("/a/b");
    }
}
