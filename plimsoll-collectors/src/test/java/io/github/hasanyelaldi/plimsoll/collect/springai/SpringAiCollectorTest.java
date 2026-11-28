package io.github.hasanyelaldi.plimsoll.collect.springai;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.EdgesHint;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties;
import io.github.hasanyelaldi.plimsoll.model.Durations;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.FailureClass;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** #19: a Spring AI OpenAI starter becomes an edge to the external service openai with a spring-ai layer. */
class SpringAiCollectorTest {

    @TempDir
    Path root;

    private static final String PARENT = """
            <project>
              <properties>
                <spring-ai.version>2.0.0-M1</spring-ai.version>
              </properties>
            </project>
            """;

    private static String pom(String starter, String management) {
        return """
                <project>
                  <parent><artifactId>shop</artifactId></parent>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.ai</groupId>
                      <artifactId>%s</artifactId>
                    </dependency>
                  </dependencies>
                  <dependencyManagement>
                    <dependencies>
                      %s
                    </dependencies>
                  </dependencyManagement>
                </project>
                """.formatted(starter, management);
    }

    private static final String BOM = """
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
              </dependency>""";

    private SpringAiCollector.Result collect(String pom, String yaml) throws IOException {
        return collect(pom, yaml, null);
    }

    private SpringAiCollector.Result collect(String pom, String yaml, EdgesHint hint) throws IOException {
        Files.writeString(root.resolve("pom.xml"), PARENT);
        Path module = root.resolve("genai");
        Files.createDirectories(module.resolve("src/main/resources"));
        Files.writeString(module.resolve("pom.xml"), pom);
        if (yaml != null) {
            Files.writeString(module.resolve("src/main/resources/application.yml"), yaml);
        }
        return SpringAiCollector.collect(root, module, "genai", SpringProperties.forService(root, module), hint,
                DefaultsKb.classpath());
    }

    @Test
    void theDefaultsGiveElevenTriesWithExponentialBackoffToTheExternalProvider() throws IOException {
        SpringAiCollector.Result r = collect(pom("spring-ai-starter-model-openai", BOM), null);
        assertThat(r.modelled()).isTrue();
        assertThat(r.external()).containsExactly("openai");
        Edge e = r.edges().get(0);
        assertThat(e.location()).isEqualTo("genai→openai");
        assertThat(e.operation()).isEqualTo("POST /v1/chat/completions");
        assertThat(e.idempotent().isKnown()).isFalse();
        assertThat(e.fanout().isKnown()).isFalse();

        Layer layer = e.layers().get(0);
        RetryPolicy p = layer.retry();
        assertThat(layer.kind()).isEqualTo("spring-ai");
        assertThat(p.totalAttempts().value()).isEqualTo(11);
        assertThat(p.totalAttempts().source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(p.totalAttempts().evidence())
                .startsWith("max-attempts not set in spring.ai.retry; Spring AI 2.0.0-M1 (genai/pom.xml:11, "
                        + "pom.xml:3 (spring-ai.version)); spring-ai 2.0.0-M1, 2.0.3 default (verified by oracle)")
                .endsWith("(max-attempts 10 + 1: it counts retries only)");
        assertThat(p.initialBackoff().value()).isEqualTo(Duration.ofSeconds(2));
        assertThat(p.multiplier().value()).isEqualTo(5.0);
        assertThat(p.maxBackoff().value()).isEqualTo(Duration.ofMinutes(3));
        assertThat(p.jitter().value()).isFalse();
        assertThat(p.perTryTimeout().isKnown()).isFalse();
        assertThat(layer.overallTimeout().value()).isEqualTo(Durations.UNLIMITED);

        assertThat(layer.conditions().retried().value()).containsExactlyInAnyOrder(FailureClass.NOT_SENT,
                FailureClass.RESET, FailureClass.TIMEOUT, FailureClass.STATUS_500, FailureClass.STATUS_502,
                FailureClass.STATUS_503, FailureClass.STATUS_504, FailureClass.STATUS_5XX_OTHER);
        assertThat(layer.conditions().givesUpAs().value()).containsEntry(FailureClass.TIMEOUT,
                java.util.Set.of(FailureClass.TIMEOUT));
        assertThat(r.notes()).singleElement().satisfies(n -> assertThat(n)
                .startsWith("genai→openai: Spring AI 2.0.0-M1 (").contains("stream() does not retry"));
    }

    @Test
    void retryPropertiesAreReadWithFileAndLine() throws IOException {
        SpringAiCollector.Result r = collect(pom("spring-ai-starter-model-openai", BOM), """
                spring:
                  ai:
                    retry:
                      max-attempts: 3
                      on-client-errors: true
                      on-http-codes: 429
                      exclude-on-http-codes: [ 503, 400 ]
                      backoff:
                        initial-interval: 500ms
                        multiplier: 2
                        max-interval: 4s
                """);
        RetryPolicy p = r.edges().get(0).layers().get(0).retry();
        String yml = "genai/src/main/resources/application.yml";
        assertThat(p.totalAttempts()).isEqualTo(Val.explicit(4, yml + ":4 (max-attempts 3 + 1: it counts retries "
                + "only)"));
        assertThat(p.initialBackoff()).isEqualTo(Val.explicit(Duration.ofMillis(500), yml + ":9"));
        assertThat(p.multiplier()).isEqualTo(Val.explicit(2.0, yml + ":10"));
        assertThat(p.maxBackoff()).isEqualTo(Val.explicit(Duration.ofSeconds(4), yml + ":11"));
        assertThat(p.retryOn().value()).containsExactlyInAnyOrder("ResourceAccessException", "TransientAiException",
                "on-client-errors", "429", "not 503", "not 400");
        // 503 is excluded; 400 is only part of status-4xx-other, so that class stays
        assertThat(r.edges().get(0).layers().get(0).conditions().retried().value()).containsExactlyInAnyOrder(
                FailureClass.NOT_SENT, FailureClass.RESET, FailureClass.TIMEOUT, FailureClass.STATUS_500,
                FailureClass.STATUS_502, FailureClass.STATUS_504, FailureClass.STATUS_5XX_OTHER,
                FailureClass.STATUS_409, FailureClass.STATUS_429, FailureClass.STATUS_4XX_OTHER);
    }

    @Test
    void aPlaceholderIsUnknownNotGuessed() throws IOException {
        SpringAiCollector.Result r = collect(pom("spring-ai-starter-model-openai", BOM), """
                spring.ai.retry.max-attempts: ${AI_RETRIES:2}
                """);
        Val<Integer> total = r.edges().get(0).layers().get(0).retry().totalAttempts();
        assertThat(total.isKnown()).isFalse();
        assertThat(total.evidence()).contains("is a placeholder (${AI_RETRIES:2})");
    }

    @Test
    void anOperationsHintGivesTheIdempotency() throws IOException {
        EdgesHint hint = EdgesHint.parse("""
                service: genai
                operations:
                  - { to: openai, operation: POST /v1/chat/completions, idempotent: true }
                """, "edges-hint.yaml");
        Edge e = collect(pom("spring-ai-starter-model-openai", BOM), null, hint).edges().get(0);
        assertThat(e.idempotent().value()).isTrue();
        assertThat(e.idempotent().source()).isEqualTo(Source.EXPLICIT);
    }

    @Test
    void otherStartersAndUnverifiedVersionsAreNotesOnly() throws IOException {
        SpringAiCollector.Result other = collect(pom("spring-ai-starter-model-anthropic", BOM), null);
        assertThat(other.modelled()).isFalse();
        assertThat(other.edges()).isEmpty();
        assertThat(other.notes()).singleElement().satisfies(n -> assertThat(n).isEqualTo("genai/pom.xml:4: Spring AI "
                + "model starter spring-ai-starter-model-anthropic: its calls to the provider are not modelled (#19 "
                + "models spring-ai-starter-model-openai)"));

        SpringAiCollector.Result sdk = collect(pom("spring-ai-starter-model-openai",
                BOM.replace("${spring-ai.version}", "2.0.0-M5")), null);
        assertThat(sdk.edges()).isEmpty();
        assertThat(sdk.notes()).singleElement().satisfies(n -> assertThat(n)
                .startsWith("genai/pom.xml:4: Spring AI 2.0.0-M5 (genai/pom.xml:11)")
                .contains("OpenAI Java SDK"));

        SpringAiCollector.Result noVersion = collect(pom("spring-ai-starter-model-openai", ""), null);
        assertThat(noVersion.edges()).isEmpty();
        assertThat(noVersion.notes()).singleElement().satisfies(n -> assertThat(n).contains("version of "
                + "spring-ai-starter-model-openai not found"));
    }

    @Test
    void aModuleWithoutAPomOrStarterGivesNothing() throws IOException {
        Path module = Files.createDirectories(root.resolve("plain"));
        assertThat(SpringAiCollector.collect(root, module, "plain", SpringProperties.forService(root, module), null,
                DefaultsKb.classpath()).edges()).isEmpty();
        SpringAiCollector.Result r = collect("<project><dependencies/></project>", null);
        assertThat(r.edges()).isEmpty();
        assertThat(r.notes()).isEmpty();
    }
}
