package io.github.hasanyelaldi.plimsoll.report;

import java.util.List;

/**
 * What loading or collecting the graph reported besides the graph. Printed as given; callers sort it. Empty notes
 * print no section, so a hand-written spec gets only its coverage line.
 *
 * @param coverage what was read and searched for
 * @param notes    what the collectors found but did not apply
 */
public record Collected(Coverage coverage, List<String> notes) {

    public static final Collected SPEC = new Collected(Coverage.SPEC, List.of());

    public Collected {
        notes = List.copyOf(notes);
    }
}
