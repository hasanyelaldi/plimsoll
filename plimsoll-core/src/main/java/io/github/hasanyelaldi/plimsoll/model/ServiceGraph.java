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
}
