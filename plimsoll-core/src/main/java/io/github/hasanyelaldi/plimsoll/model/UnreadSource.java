package io.github.hasanyelaldi.plimsoll.model;

import java.util.Comparator;

/**
 * Configuration a service refers to or ships that Plimsoll did not read, so values set there are missing from the
 * graph.
 *
 * @param kind {@code config-server}, {@code config-import}, {@code config-file}, {@code profile-document},
 *             {@code profile} or {@code placeholder}
 * @param reference what was not read, as written (an import location, a file name, a property)
 * @param evidence {@code file:line}, or the file for a whole file
 */
public record UnreadSource(String service, String kind, String reference, String evidence) {

    /** Service, then evidence, then kind, then reference. */
    public static final Comparator<UnreadSource> ORDER = Comparator.comparing(UnreadSource::service)
            .thenComparing(UnreadSource::evidence, Evidence.ORDER)
            .thenComparing(UnreadSource::kind)
            .thenComparing(UnreadSource::reference);
}
