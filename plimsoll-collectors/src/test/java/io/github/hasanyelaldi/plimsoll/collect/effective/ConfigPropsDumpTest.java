package io.github.hasanyelaldi.plimsoll.collect.effective;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hasanyelaldi.plimsoll.collect.effective.ConfigPropsDump.Dump;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties.Property;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigPropsDumpTest {

    private static final Path FIXTURE = Path.of("src/test/resources/effective/orders-configprops.json");

    private static String bean(String prefix, String properties, String inputs) {
        return """
                { "contexts": { "orders": { "beans": { "b": {
                  "prefix": "%s", "properties": %s, "inputs": %s } } } } }
                """.formatted(prefix, properties, inputs);
    }

    private static Property at(Dump d, String path) {
        return d.overlaid().stream().filter(p -> String.join(".", p.path()).equals(path)).findFirst().orElseThrow();
    }

    @Test
    void theRealDumpGivesTheBoundValuesWithLineAndPointer() throws IOException {
        Dump d = ConfigPropsDump.parse(Files.readString(FIXTURE), "dumps/orders.json", "orders");
        assertThat(d.context()).isEqualTo("orders");
        Property retries = at(d, "spring.cloud.gateway.server.webflux.routes.[0].filters.[0].args.retries");
        assertThat(retries.text()).isEqualTo("2");
        assertThat(retries.evidence()).isEqualTo("dumps/orders.json:352 (/contexts/orders/beans/gatewayProperties/"
                + "properties/routes/0/filters/0/args/retries; origin: class path resource [oracle/configprops-app.yml]"
                + " - 16:30)");
        assertThat(at(d, "resilience4j.retry.instances.backend.waitDuration").text()).isEqualTo("PT0.25S");
        // a default has no origin
        assertThat(at(d, "resilience4j.retry.retryAspectOrder").evidence()).doesNotContain("origin");
        assertThat(d.prefixes()).contains(List.of("resilience4j", "retry"),
                List.of("spring", "cloud", "gateway", "server", "webflux"), List.of("spring", "cloud", "loadbalancer"));
        assertThat(d.prefixes()).noneMatch(p -> p.get(0).equals("server") || p.get(0).equals("management"));
        assertThat(d.notes()).isEmpty();
    }

    @Test
    void theDumpReplacesTheDeclaredPropertiesUnderItsPrefixesOnly() throws IOException {
        Dump d = ConfigPropsDump.parse(Files.readString(FIXTURE), "dumps/orders.json", "orders");
        SpringProperties declared = SpringProperties.parse("""
                spring.application.name: orders
                resilience4j.retry.instances.backend.max-attempts: 3
                resilience4j.retry.instances.other.max-attempts: 4
                """, "application.yml");
        SpringProperties running = d.applyTo(declared);
        assertThat(running.get("resilience4j", "retry", "instances", "backend", "max-attempts").orElseThrow().text())
                .isEqualTo("5");
        assertThat(running.get("resilience4j", "retry", "instances", "other", "max-attempts")).isEmpty();
        assertThat(running.get("spring", "application", "name").orElseThrow().evidence()).isEqualTo("application.yml:1");
        assertThat(running.files()).containsExactly("application.yml", "dumps/orders.json");
    }

    @Test
    void emptyListsAndMapsCarryNoValue() {
        Dump d = ConfigPropsDump.parse(bean("spring.cloud.gateway.server.webflux",
                "{ \"routes\": [], \"defaultFilters\": [], \"metadata\": {}, \"x\": null }", "{}"), "d.json", "orders");
        assertThat(d.overlaid()).isEmpty();
        assertThat(d.prefixes()).containsExactly(List.of("spring", "cloud", "gateway", "server", "webflux"));
    }

    @Test
    void aMaskedValueIsLeftOutWithANote() {
        Dump d = ConfigPropsDump.parse(bean("resilience4j.retry",
                "{ \"instances\": { \"a\": { \"maxAttempts\": \"******\" } } }", "{}"), "d.json", "orders");
        assertThat(d.overlaid()).isEmpty();
        assertThat(d.notes()).containsExactly("d.json:2 (/contexts/orders/beans/b/properties/instances/a/maxAttempts): "
                + "resilience4j.retry.instances.a.maxAttempts is masked; set "
                + "management.endpoint.configprops.show-values=always to compare it; the declared value is kept");
    }

    @Test
    void springAiIsComparedOnlyWhereAPropertySourceSetIt() {
        Dump d = ConfigPropsDump.parse(bean("spring.ai.retry",
                "{ \"maxAttempts\": 4, \"onClientErrors\": false }",
                "{ \"maxAttempts\": { \"value\": \"4\", \"origin\": \"System Environment Property \\\"SPRING_AI_RETRY_MAX_ATTEMPTS\\\"\" }, \"onClientErrors\": {} }"),
                "d.json", "orders");
        assertThat(d.overlaid()).isEmpty();
        assertThat(d.prefixes()).isEmpty();
        assertThat(d.compared()).singleElement().satisfies(p -> {
            assertThat(String.join(".", p.path())).isEqualTo("spring.ai.retry.maxAttempts");
            assertThat(p.evidence()).contains("origin: System Environment Property");
        });
    }

    @Test
    void severalContextsNeedOneNamedLikeTheService() {
        String two = "{ \"contexts\": { \"orders\": { \"beans\": {} }, \"bootstrap\": { \"beans\": {} } } }";
        assertThat(ConfigPropsDump.parse(two, "d.json", "orders").notes()).containsExactly(
                "d.json: contexts bootstrap, orders; read context orders, the one named like the service");
        assertThatThrownBy(() -> ConfigPropsDump.parse(two, "d.json", "payments"))
                .isInstanceOf(InvalidSpecException.class).hasMessageContaining("none is named payments");
    }

    @Test
    void anythingElseIsNotADump() {
        assertThatThrownBy(() -> ConfigPropsDump.parse("{ \"status\": \"UP\" }", "health.json", "orders"))
                .isInstanceOf(InvalidSpecException.class).hasMessage("health.json: not a /actuator/configprops dump "
                        + "(no contexts.<id>.beans)");
    }
}
