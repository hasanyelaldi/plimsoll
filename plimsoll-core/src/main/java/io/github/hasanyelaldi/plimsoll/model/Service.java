package io.github.hasanyelaldi.plimsoll.model;

import java.time.Duration;

public record Service(String name, Val<Duration> deadline, boolean entry) {}
