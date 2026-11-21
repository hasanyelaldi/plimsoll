package io.github.hasanyelaldi.plimsoll.model;

import java.util.Comparator;
import java.util.Objects;

/**
 * A setting whose value in a running environment differs from the one the repository declares (ADR 0016).
 *
 * @param service           the service the dump belongs to
 * @param setting           the setting, e.g. {@code resilience4j.retry.instances.backend.max-attempts}
 * @param declared          the declared value as the report shows it, e.g. {@code 3}
 * @param declaredSource    {@code EXPLICIT} from the repository, {@code LIBRARY_DEFAULT} or {@code UNKNOWN} when not set
 * @param declaredEvidence  where the declared value comes from
 * @param effective         the value that runs, as the report shows it
 * @param effectiveEvidence {@code <dump>:<line> (<JSON pointer>)}
 */
public record ConfigDifference(String service, String setting, String declared, Source declaredSource,
        String declaredEvidence, String effective, String effectiveEvidence) {

    /** Service, then setting, then effective evidence. */
    public static final Comparator<ConfigDifference> ORDER = Comparator.comparing(ConfigDifference::service)
            .thenComparing(ConfigDifference::setting)
            .thenComparing(ConfigDifference::effectiveEvidence, Evidence.ORDER);

    public ConfigDifference {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(setting, "setting");
        Objects.requireNonNull(declared, "declared");
        Objects.requireNonNull(declaredSource, "declaredSource");
        Objects.requireNonNull(effective, "effective");
        Objects.requireNonNull(effectiveEvidence, "effectiveEvidence");
        if (declared.equals(effective)) {
            throw new IllegalArgumentException(setting + ": declared and effective are both " + declared);
        }
    }
}
