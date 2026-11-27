package io.github.hasanyelaldi.plimsoll.model;

import java.time.Duration;

/** @param external outside the analysed system (ADR 0019): never an entry, no outgoing edges */
public record Service(String name, Val<Duration> deadline, boolean entry, boolean external) {

    public Service(String name, Val<Duration> deadline, boolean entry) {
        this(name, deadline, entry, false);
    }
}
