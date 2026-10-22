package io.github.hasanyelaldi.plimsoll.analysis;

import io.github.hasanyelaldi.plimsoll.model.Detection;
import io.github.hasanyelaldi.plimsoll.model.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * PLM-C1 Mechanism detected but not modelled: WARN. One finding per service and mechanism, listing every match.
 * A1 is an upper bound over the layers Plimsoll sees; a layer it misses makes the real amplification higher.
 */
final class UncollectedMechanismRule {

    static final String ID = "PLM-C1";

    private UncollectedMechanismRule() {}

    static List<Finding> evaluate(List<Detection> detections) {
        Map<String, Map<String, List<Detection>>> byService = new TreeMap<>();
        detections.stream().sorted(Detection.ORDER).distinct().forEach(d -> byService
                .computeIfAbsent(d.service(), s -> new TreeMap<>())
                .computeIfAbsent(d.mechanism(), m -> new ArrayList<>())
                .add(d));
        List<Finding> findings = new ArrayList<>();
        byService.forEach((service, mechanisms) -> mechanisms.forEach((mechanism, matches) -> {
            String effects = String.join(" or ", matches.stream().map(Detection::effect).distinct().sorted().toList());
            List<UsedValue> values = matches.stream()
                    .map(d -> new UsedValue(service + " " + mechanism, d.pattern(), Source.EXPLICIT,
                            d.evidence() + "; " + d.source()))
                    .toList();
            findings.add(new Finding(ID, Severity.WARN, service, mechanism + " detected, not modelled; edges from "
                    + service + " may carry an unseen " + effects + " layer", values, List.of()));
        }));
        return findings;
    }
}
