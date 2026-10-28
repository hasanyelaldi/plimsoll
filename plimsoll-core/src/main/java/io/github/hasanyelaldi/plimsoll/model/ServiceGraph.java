package io.github.hasanyelaldi.plimsoll.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ServiceGraph(Map<String, Service> services, List<Edge> edges) {

    public ServiceGraph {
        services = Collections.unmodifiableMap(new LinkedHashMap<>(services));
        edges = List.copyOf(edges);
    }

    /**
     * Where findings on {@code e} are located: {@code from→to}, plus {@code (operation)} when the graph has another
     * edge with the same {@code from→to}, so that their findings can be told apart (ADR 0012).
     */
    public String location(Edge e) {
        boolean shared = edges.stream().anyMatch(o -> o != e && o.from().equals(e.from()) && o.to().equals(e.to()));
        return shared && e.operation() != null ? e.location() + " (" + e.operation() + ")" : e.location();
    }
}
