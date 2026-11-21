package io.github.hasanyelaldi.plimsoll.analysis;

import io.github.hasanyelaldi.plimsoll.model.ConfigDifference;
import io.github.hasanyelaldi.plimsoll.model.Source;
import java.util.List;

/**
 * PLM-C2 Declared configuration differs from what runs (ADR 0016): WARN, one finding per service and setting, with
 * the declared and the effective value and where each comes from. The other rules already use the effective value.
 */
final class EffectiveConfigRule {

    static final String ID = "PLM-C2";

    private EffectiveConfigRule() {}

    static List<Finding> evaluate(List<ConfigDifference> differences) {
        return differences.stream().sorted(ConfigDifference.ORDER).distinct()
                .map(d -> new Finding(ID, Severity.WARN, d.service(),
                        d.setting() + ": declared " + d.declared() + ", runs " + d.effective(),
                        List.of(new UsedValue(d.setting() + " declared", d.declared(), d.declaredSource(),
                                        d.declaredEvidence()),
                                new UsedValue(d.setting() + " runs", d.effective(), Source.EXPLICIT,
                                        d.effectiveEvidence())),
                        List.of()))
                .toList();
    }
}
