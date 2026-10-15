package io.github.hasanyelaldi.plimsoll.model;

/** Where a configuration value came from. */
public enum Source {
    /** Written in a config file; evidence is {@code file:line}. */
    EXPLICIT,
    /** Taken from a library's documented default; evidence is the doc URL. */
    LIBRARY_DEFAULT,
    /** Proposed by an LLM; never used silently. */
    INFERRED,
    /** Not known. The value is {@code null}; analyses must not guess it. */
    UNKNOWN
}
