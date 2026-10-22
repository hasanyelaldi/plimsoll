package io.github.hasanyelaldi.plimsoll.report;

import io.github.hasanyelaldi.plimsoll.model.UnreadSource;
import java.util.List;

/**
 * What collecting the graph from a directory reported besides the graph. Printed as given; callers sort it. Empty
 * lists print no section, so a hand-written spec gets neither.
 *
 * @param notes what the collectors found but did not apply
 * @param unreadSources configuration referred to or shipped but not read
 */
public record Collected(List<String> notes, List<UnreadSource> unreadSources) {

    public static final Collected NONE = new Collected(List.of(), List.of());

    public Collected {
        notes = List.copyOf(notes);
        unreadSources = List.copyOf(unreadSources);
    }
}
