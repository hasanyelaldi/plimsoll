package io.github.hasanyelaldi.plimsoll.collect.detect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Detection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DetectorTest {

    private static final Detector DETECTOR = Detector.parse("""
            - { mechanism: Spring Retry, effect: retry, kind: dependency,
                pattern: "org.springframework.retry:spring-retry", source: "https://example.org/retry" }
            - { mechanism: Spring AI retry, effect: retry, kind: dependency,
                pattern: "org.springframework.ai:spring-ai-starter-model-*", source: "https://example.org/ai" }
            - { mechanism: Spring AI retry, effect: retry, kind: config-key,
                pattern: spring.ai.retry, source: "https://example.org/ai" }
            - { mechanism: MicroProfile FT, effect: retry, kind: annotation,
                pattern: org.eclipse.microprofile.faulttolerance.Retry, source: "https://example.org/mp" }
            - { mechanism: Feign Retryer, effect: retry, kind: type,
                pattern: feign.Retryer, source: "https://example.org/feign" }
            - { mechanism: Reactor retry, effect: retry, kind: call,
                pattern: "reactor.core.publisher#retryWhen", source: "https://example.org/reactor" }
            """, "mechanisms.yaml");

    @TempDir
    Path dir;

    private void write(String file, String text) throws IOException {
        Files.createDirectories(dir.resolve(file).getParent());
        Files.writeString(dir.resolve(file), text);
    }

    private List<Detection> detect() {
        Path module = dir.resolve("svc");
        return DETECTOR.detect(dir, module, "svc", SpringProperties.forService(dir, module));
    }

    @Test
    void mavenDirectDependenciesMatchButNotTestScopeManagementOrComments() throws IOException {
        write("svc/pom.xml", """
                <project>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.springframework.retry</groupId>
                        <artifactId>spring-retry</artifactId>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.ai</groupId>
                      <artifactId>spring-ai-starter-model-openai</artifactId>
                      <!-- artifactId>spring-retry</artifactId -->
                    </dependency>
                    <dependency>
                      <groupId>org.springframework.retry</groupId>
                      <artifactId>spring-retry</artifactId>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);
        assertThat(detect()).extracting(Detection::mechanism, Detection::pattern, Detection::evidence)
                .containsExactly(tuple("Spring AI retry", "dependency org.springframework.ai:spring-ai-starter-model-*",
                        "svc/pom.xml:13"));
    }

    @Test
    void gradleDependenciesMatchExceptTestConfigurations() throws IOException {
        write("svc/build.gradle.kts", """
                dependencies {
                    implementation("org.springframework.retry:spring-retry:2.0.12")
                    testImplementation("org.springframework.ai:spring-ai-starter-model-openai")
                }
                """);
        assertThat(detect()).extracting(Detection::mechanism, Detection::evidence)
                .containsExactly(tuple("Spring Retry", "svc/build.gradle.kts:2"));
    }

    @Test
    void annotationsAndTypesMustResolveThroughImports() throws IOException {
        write("svc/src/main/java/a/Imported.java", """
                package a;
                import org.eclipse.microprofile.faulttolerance.Retry;
                class Imported {
                    @Retry
                    void call() {}
                }
                """);
        write("svc/src/main/java/a/Wildcard.java", """
                package a;
                import feign.*;
                class Wildcard {

                    Retryer retryer;
                }
                """);
        write("svc/src/main/java/a/Qualified.java", """
                package a;
                class Qualified {
                    @org.eclipse.microprofile.faulttolerance.Retry void call() {}
                }
                """);
        write("svc/src/main/java/a/LookAlike.java", """
                package a;
                import io.github.resilience4j.retry.annotation.Retry;
                class LookAlike {
                    @Retry(name = "x") void call() {}
                    Retryer notFeign;
                }
                """);
        assertThat(detect()).extracting(Detection::mechanism, Detection::evidence).containsExactly(
                tuple("Feign Retryer", "svc/src/main/java/a/Wildcard.java:5"),
                tuple("MicroProfile FT", "svc/src/main/java/a/Imported.java:4"),
                tuple("MicroProfile FT", "svc/src/main/java/a/Qualified.java:3"));
    }

    @Test
    void callNeedsAnImportUnderItsPrefixAndGivesTheFirstLine() throws IOException {
        write("svc/src/main/java/a/Client.java", """
                package a;
                import reactor.core.publisher.Mono;
                class Client {
                    Mono<String> get(Mono<String> m) {
                        return m.retryWhen(null)
                                .retryWhen(null);
                    }
                }
                """);
        write("svc/src/main/java/a/Other.java", """
                package a;
                class Other {
                    void run(Other o) { o.retryWhen(null); }
                    void retryWhen(Object o) {}
                }
                """);
        assertThat(detect()).extracting(Detection::pattern, Detection::evidence)
                .containsExactly(tuple("call reactor.core.publisher#retryWhen", "svc/src/main/java/a/Client.java:5"));
    }

    @Test
    void configKeyMatchesWithRelaxedBinding() throws IOException {
        write("svc/src/main/resources/application.yml", """
                spring:
                  application.name: svc
                  ai:
                    retry:
                      maxAttempts: 2
                """);
        assertThat(detect()).extracting(Detection::pattern, Detection::evidence)
                .containsExactly(tuple("config-key spring.ai.retry", "svc/src/main/resources/application.yml:5"));
    }

    @Test
    void nothingDetectedInAPlainModule() throws IOException {
        write("svc/src/main/java/a/Plain.java", "package a;\nclass Plain {}\n");
        assertThat(detect()).isEmpty();
    }

    @Test
    void shippedPatternsAreWellFormed() {
        List<Detector.Mechanism> mechanisms = Detector.classpath().mechanisms();
        assertThat(mechanisms).isNotEmpty().allSatisfy(m -> assertThat(m.source()).startsWith("https://"));
        assertThat(mechanisms).extracting(Detector.Mechanism::mechanism)
                .doesNotContain("Resilience4j", "Istio", "Spring Cloud Gateway Retry");
    }

    @Test
    void malformedPatternsAreInvalid() {
        assertThatThrownBy(() -> Detector.parse(
                "- { mechanism: X, effect: retry, kind: call, pattern: noHash, source: \"https://x\" }\n", "m.yaml"))
                .isInstanceOf(InvalidSpecException.class).hasMessage("m.yaml:1: malformed call pattern 'noHash'");
        assertThatThrownBy(() -> Detector.parse(
                "- { mechanism: X, effect: slow, kind: type, pattern: a.B, source: \"https://x\" }\n", "m.yaml"))
                .isInstanceOf(InvalidSpecException.class).hasMessage("m.yaml:1: unknown effect 'slow'");
        assertThatThrownBy(() -> Detector.parse(
                "- { mechanism: X, effect: retry, kind: type, pattern: a.B, source: \"http://x\" }\n", "m.yaml"))
                .isInstanceOf(InvalidSpecException.class).hasMessage("m.yaml:1: source must be an https link");
        assertThatThrownBy(() -> Detector.parse(
                "- { mechanism: X, effect: retry, kind: guess, pattern: a.B, source: \"https://x\" }\n", "m.yaml"))
                .isInstanceOf(InvalidSpecException.class).hasMessage("m.yaml:1: unknown kind 'guess'");
    }
}
