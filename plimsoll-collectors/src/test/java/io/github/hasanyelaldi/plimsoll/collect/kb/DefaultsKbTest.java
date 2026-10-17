package io.github.hasanyelaldi.plimsoll.collect.kb;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultsKbTest {

    private static final DefaultsKb KB = DefaultsKb.of(Map.of("lib", """
            - library: lib
              versions: "1.0"
              parameter: checked
              value: 3
              unit: attempts-total
              source: https://example.org/docs
              verified_by: oracle
            - library: lib
              versions: unverified
              parameter: seed
              value: 10
              unit: attempts-total
              source: https://example.org/docs
              verified_by: none
            """));

    @Test
    void verifiedEntryBecomesALibraryDefaultWithItsSource() {
        Val<Integer> v = KB.defaultOr("lib", "checked", Integer::valueOf, "maxAttempts not configured");

        assertThat(v.source()).isEqualTo(Source.LIBRARY_DEFAULT);
        assertThat(v.value()).isEqualTo(3);
        assertThat(v.evidence()).isEqualTo(
                "maxAttempts not configured; lib 1.0 default (verified by oracle), https://example.org/docs");
    }

    @Test
    void unverifiedSeedEntryStaysUnknown() {
        Val<Integer> v = KB.defaultOr("lib", "seed", Integer::valueOf, "x not configured");

        assertThat(v.source()).isEqualTo(Source.UNKNOWN);
        assertThat(v.evidence()).isEqualTo("x not configured; no verified lib default for seed");
    }

    @Test
    void missingLibraryOrParameterIsUnknown() {
        assertThat(KB.lookup("lib", "absent")).isEmpty();
        assertThat(KB.lookup("other", "checked")).isEmpty();
    }

    @Test
    void repositoryKnowledgeBaseIsOnTheClasspath() {
        assertThat(DefaultsKb.classpath().lookup("resilience4j-retry", "maxAttempts"))
                .map(DefaultsKb.Entry::value).contains("3");
    }
}
