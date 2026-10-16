package io.github.hasanyelaldi.plimsoll.analysis;

import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.Service;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * PLM-A5 Layered retries, two checks, both WARN:
 *
 * <ul>
 *   <li>one edge with two or more layers that retry ({@code totalAttempts > 1}): the layers multiply;
 *   <li>one path, starting at an entry service, with retries on two or more edges: the hops multiply.
 * </ul>
 *
 * <p>A path is reported from its first to its last retrying edge, and a stretch that lies inside a longer
 * reported one is dropped, so each chain of retries shows up once. Paths are simple: a cycle is followed
 * until it would revisit a service (A1 already reports it as unbounded).
 *
 * <p>Only known retries are counted. UNKNOWN attempt counts on the reported edge or path are named, since
 * they could add retries.
 */
final class LayeredRetryRule {

    static final String ID = "PLM-A5";

    private LayeredRetryRule() {}

    static List<Finding> evaluate(ServiceGraph graph) {
        List<Finding> findings = new ArrayList<>();
        for (Edge edge : graph.edges()) {
            evaluateEdge(edge, findings);
        }
        evaluatePaths(graph, findings);
        return findings;
    }

    // ---- one edge, several layers -------------------------------------------------

    private static void evaluateEdge(Edge edge, List<Finding> out) {
        List<String> retrying = new ArrayList<>();
        long product = 1;
        for (Layer layer : edge.layers()) {
            if (Attempts.retries(layer)) {
                int n = layer.retry().totalAttempts().value();
                retrying.add(layer.kind() + " ×" + n);
                product = Math.multiplyExact(product, n);
            }
        }
        if (retrying.size() < 2) {
            return;
        }
        out.add(new Finding(ID, Severity.WARN, edge.location(),
                "retries in " + retrying.size() + " layers of one edge (" + String.join(", ", retrying)
                        + "), ×" + product + " per call",
                attemptValues(edge), Attempts.unknownAttempts(edge)));
    }

    // ---- one path, several edges ----------------------------------------------------

    private static void evaluatePaths(ServiceGraph graph, List<Finding> out) {
        Map<String, List<Integer>> next = new TreeMap<>();
        for (int i = 0; i < graph.edges().size(); i++) {
            next.computeIfAbsent(graph.edges().get(i).from(), k -> new ArrayList<>()).add(i);
        }
        List<List<Integer>> stretches = new ArrayList<>();
        graph.services().values().stream()
                .filter(Service::entry)
                .map(Service::name)
                .sorted()
                .forEach(entry -> walk(entry, graph.edges(), next, new ArrayList<>(), new TreeSet<>(Set.of(entry)), stretches));

        Map<String, Finding> byLocation = new TreeMap<>();
        for (List<Integer> stretch : stretches) {
            boolean inside = stretches.stream()
                    .anyMatch(other -> other.size() > stretch.size() && Collections.indexOfSubList(other, stretch) >= 0);
            if (!inside) {
                Finding f = pathFinding(stretch, graph.edges());
                byLocation.putIfAbsent(f.location(), f);
            }
        }
        out.addAll(byLocation.values());
    }

    /** Depth-first over simple paths; at the end of each path, records its stretch of retries if it has two. */
    private static void walk(
            String at, List<Edge> edges, Map<String, List<Integer>> next,
            List<Integer> path, Set<String> visited, List<List<Integer>> stretches) {
        boolean extended = false;
        for (int i : next.getOrDefault(at, List.of())) {
            String to = edges.get(i).to();
            if (visited.add(to)) {
                extended = true;
                path.add(i);
                walk(to, edges, next, path, visited, stretches);
                path.remove(path.size() - 1);
                visited.remove(to);
            }
        }
        if (!extended) {
            List<Integer> retrying = path.stream().filter(i -> Attempts.edgeRetries(edges.get(i))).toList();
            if (retrying.size() >= 2) {
                List<Integer> stretch = List.copyOf(path.subList(path.indexOf(retrying.get(0)), path.indexOf(retrying.get(retrying.size() - 1)) + 1));
                if (!stretches.contains(stretch)) {
                    stretches.add(stretch);
                }
            }
        }
    }

    private static Finding pathFinding(List<Integer> stretch, List<Edge> edges) {
        StringBuilder location = new StringBuilder(edges.get(stretch.get(0)).from());
        List<UsedValue> values = new ArrayList<>();
        List<String> unknowns = new ArrayList<>();
        int retrying = 0;
        for (int i : stretch) {
            Edge e = edges.get(i);
            location.append('→').append(e.to());
            values.addAll(attemptValues(e));
            unknowns.addAll(Attempts.unknownAttempts(e));
            if (Attempts.edgeRetries(e)) {
                retrying++;
            }
        }
        return new Finding(ID, Severity.WARN, location.toString(),
                "retries on " + retrying + " edges of one path", values, unknowns);
    }

    private static List<UsedValue> attemptValues(Edge edge) {
        List<UsedValue> values = new ArrayList<>();
        for (int i = 0; i < edge.layers().size(); i++) {
            values.add(UsedValue.of(Attempts.layerName(edge, i) + " totalAttempts", edge.layers().get(i).retry().totalAttempts()));
        }
        return values;
    }
}
