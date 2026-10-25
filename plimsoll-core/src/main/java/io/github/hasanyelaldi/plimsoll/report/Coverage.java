package io.github.hasanyelaldi.plimsoll.report;

import io.github.hasanyelaldi.plimsoll.model.UnreadSource;
import java.util.List;

/**
 * Where the graph came from and how far that looked, so that a reader can tell a layer that is absent from one
 * that was never searched for. Printed as given; callers sort it. Empty lists print no subsection.
 *
 * @param source        where the graph came from, e.g. {@code hand-written spec}
 * @param services      per service, the files that were read
 * @param manifests     Istio manifests read for the whole graph
 * @param edges         per edge, the layer kinds searched for and the ones found
 * @param unreadSources configuration referred to or shipped but not read
 */
public record Coverage(String source, List<ServiceSources> services, List<String> manifests, List<EdgeSearch> edges,
        List<UnreadSource> unreadSources) {

    public static final Coverage SPEC = new Coverage(
            "hand-written spec; every layer is taken as written, nothing was collected",
            List.of(), List.of(), List.of(), List.of());

    public Coverage {
        services = List.copyOf(services);
        manifests = List.copyOf(manifests);
        edges = List.copyOf(edges);
        unreadSources = List.copyOf(unreadSources);
    }

    /** @param read files and directories read for {@code service}; empty when the directory has no module for it */
    public record ServiceSources(String service, List<String> read) {
        public ServiceSources {
            read = List.copyOf(read);
        }
    }

    /**
     * @param operation the edge's operation, or {@code null}
     * @param searched  layer kinds the collectors looked for on this edge
     * @param found     layer kinds on the edge, outermost first
     */
    public record EdgeSearch(String edge, String operation, List<String> searched, List<String> found) {
        public EdgeSearch {
            searched = List.copyOf(searched);
            found = List.copyOf(found);
        }
    }
}
