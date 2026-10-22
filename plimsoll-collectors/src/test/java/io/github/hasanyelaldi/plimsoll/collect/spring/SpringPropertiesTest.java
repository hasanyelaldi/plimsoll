package io.github.hasanyelaldi.plimsoll.collect.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties.Property;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties.Unread;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringPropertiesTest {

    private static final List<String> INSTANCES = List.of("resilience4j", "retry", "instances");

    @Test
    void nestedAndDottedKeysGiveTheSamePathWithTheirLine() {
        SpringProperties p = SpringProperties.parse("""
                resilience4j:
                  retry:
                    instances:
                      a:
                        max-attempts: 3
                resilience4j.retry.instances.b.maxAttempts: 4
                """, "application.yml");

        assertThat(p.get(INSTANCES, "a", "maxAttempts")).map(Property::evidence).contains("application.yml:5");
        assertThat(p.get(INSTANCES, "b", "max-attempts")).map(Property::text).contains("4");
        assertThat(p.names("resilience4j", "retry", "instances")).containsExactly("a", "b");
    }

    @Test
    void propertyNamesUseRelaxedBindingButMapKeysMatchExactly() {
        SpringProperties p = SpringProperties.parse("""
                Resilience4j.RETRY.instances.backendA.max_attempts: 2
                """, "application.yml");

        assertThat(p.get(INSTANCES, "backendA", "maxAttempts")).isPresent();
        assertThat(p.get(INSTANCES, "backenda", "maxAttempts")).isEmpty();
    }

    @Test
    void laterValuesOverrideEarlierOnes() {
        SpringProperties p = SpringProperties.parse("""
                a.b: 1
                ---
                a:
                  b: 2
                """, "application.yml");

        assertThat(p.get("a", "b")).map(Property::text).contains("2");
        assertThat(p.get("a", "b")).map(Property::evidence).contains("application.yml:4");
    }

    @Test
    void profileDocumentsAreSkippedAndListedAsUnread() {
        SpringProperties p = SpringProperties.parse("""
                a.b: 1
                ---
                spring.config.activate.on-profile: prod
                a.b: 9
                """, "application.yml");

        assertThat(p.get("a", "b")).map(Property::text).contains("1");
        assertThat(p.unread()).containsExactly(
                new Unread("profile-document", "spring.config.activate.on-profile = prod", "application.yml:3"));
    }

    @Test
    void activatingProfilesIsListedAsUnread() {
        SpringProperties p = SpringProperties.parse("spring.profiles.active: prod\nspring.profiles.include: [a, b]\n",
                "application.yml");

        assertThat(p.unread()).containsExactly(
                new Unread("profile", "spring.profiles.active = prod", "application.yml:1"),
                new Unread("profile", "spring.profiles.include = a,b", "application.yml:2"));
    }

    @Test
    void configImportsAreListedAsUnreadOneByOne() {
        SpringProperties p = SpringProperties.parse("""
                spring:
                  config:
                    import: optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888/},optional:classpath:/creds.yaml
                ---
                spring.config.import:
                  - configserver:http://config:8888
                  - file:./extra.yml
                """, "application.yml");

        assertThat(p.unread()).containsExactly(
                new Unread("config-server", "optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888/}",
                        "application.yml:3"),
                new Unread("config-import", "optional:classpath:/creds.yaml", "application.yml:3"),
                new Unread("config-server", "configserver:http://config:8888", "application.yml:5"),
                new Unread("config-import", "file:./extra.yml", "application.yml:5"));
    }

    @Test
    void placeholdersAreListedOnlyUnderCollectedPrefixes() {
        SpringProperties p = SpringProperties.parse("""
                resilience4j.retry.instances.orders.max-attempts: ${ORDERS_RETRIES:3}
                resilience4j.retry.instances.orders.retry-exceptions: [ "${EX}", java.io.IOException ]
                spring.cloud.gateway.server.webflux.routes:
                  - id: orders
                    uri: ${ORDERS_URI}
                spring.ai.openai.api-key: ${OPENAI_API_KEY:demo}
                server.port: ${PORT:8080}
                """, "application.yml");

        assertThat(p.unread()).containsExactly(
                new Unread("placeholder", "resilience4j.retry.instances.orders.max-attempts = ${ORDERS_RETRIES:3}",
                        "application.yml:1"),
                new Unread("placeholder", "resilience4j.retry.instances.orders.retry-exceptions = ${EX},java.io.IOException",
                        "application.yml:2"),
                new Unread("placeholder", "spring.cloud.gateway.server.webflux.routes.[0].uri = ${ORDERS_URI}",
                        "application.yml:5"));
    }

    @Test
    void otherConfigFilesOfAServiceAreListedAsUnread(@TempDir Path dir) throws IOException {
        Path resources = dir.resolve("svc/src/main/resources");
        Files.createDirectories(resources.resolve("config"));
        for (String f : List.of("application.yml", "application-docker.yml", "application.properties",
                "bootstrap.yml", "config/application.yml", "config/application-prod.yaml", "logback-spring.xml")) {
            Files.writeString(resources.resolve(f), "a: 1\n");
        }

        assertThat(SpringProperties.forService(dir, dir.resolve("svc")).unread()).extracting(Unread::reference,
                Unread::evidence).containsExactly(
                tuple("application-docker.yml", "svc/src/main/resources/application-docker.yml"),
                tuple("application.properties", "svc/src/main/resources/application.properties"),
                tuple("bootstrap.yml", "svc/src/main/resources/bootstrap.yml"),
                tuple("application-prod.yaml", "svc/src/main/resources/config/application-prod.yaml"));
        assertThat(SpringProperties.forService(dir, dir.resolve("svc")).unread()).extracting(Unread::kind)
                .containsOnly("config-file");
    }

    @Test
    void listsAndCommaSeparatedValuesBothGiveValues() {
        SpringProperties p = SpringProperties.parse("""
                x:
                  list: [java.io.IOException, java.util.concurrent.TimeoutException]
                  csv: java.io.IOException, java.util.concurrent.TimeoutException
                """, "application.yml");

        assertThat(p.get("x", "list").orElseThrow().values()).containsExactly(
                "java.io.IOException", "java.util.concurrent.TimeoutException");
        assertThat(p.get("x", "csv").orElseThrow().values()).containsExactly(
                "java.io.IOException", "java.util.concurrent.TimeoutException");
    }

    @Test
    void placeholdersAreRecognised() {
        SpringProperties p = SpringProperties.parse("a: ${RETRIES:3}\nb: 3\n", "application.yml");

        assertThat(p.get("a").orElseThrow().isPlaceholder()).isTrue();
        assertThat(p.get("b").orElseThrow().isPlaceholder()).isFalse();
    }

    @Test
    void listItemsAreAlsoBoundUnderTheirIndex() {
        SpringProperties p = SpringProperties.parse("""
                routes:
                  - id: a
                    filters: [ StripPrefix=2, { name: Retry, args: { retries: 1 } } ]
                  - id: b
                """, "application.yml");

        assertThat(p.indices("routes")).containsExactly(0, 1);
        assertThat(p.get("routes", "[1]", "id")).map(Property::evidence).contains("application.yml:4");
        assertThat(p.get("routes", "[0]", "filters", "[0]")).map(Property::text).contains("StripPrefix=2");
        assertThat(p.under("routes", "[0]", "filters", "[1]", "args")).extracting(Property::text).containsExactly("1");
        assertThat(p.get("routes", "[0]", "filters")).isEmpty();
    }
}
