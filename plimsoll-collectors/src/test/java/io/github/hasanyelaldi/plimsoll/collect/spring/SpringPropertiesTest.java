package io.github.hasanyelaldi.plimsoll.collect.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties.Property;
import java.util.List;
import org.junit.jupiter.api.Test;

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
    void profileDocumentsAreSkippedWithANote() {
        SpringProperties p = SpringProperties.parse("""
                a.b: 1
                ---
                spring.config.activate.on-profile: prod
                a.b: 9
                """, "application.yml");

        assertThat(p.get("a", "b")).map(Property::text).contains("1");
        assertThat(p.notes()).containsExactly("application.yml:3: document skipped, it is activated by "
                + "spring.config.activate.on-profile (only the default profile is read)");
    }

    @Test
    void activatingProfilesIsNoted() {
        SpringProperties p = SpringProperties.parse("spring.profiles.active: prod\n", "application.yml");

        assertThat(p.notes()).containsExactly(
                "application.yml:1: spring.profiles.active activates profiles; their documents and files are not applied");
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
}
