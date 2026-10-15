package io.github.hasanyelaldi.plimsoll.analysis;

import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.Service;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import io.github.hasanyelaldi.plimsoll.model.Source;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector;
import org.jgrapht.graph.DirectedPseudograph;
import org.jgrapht.traverse.TopologicalOrderIterator;

/**
 * PLM-A1 Amplification. Upper bound assuming every try fails and failure propagates upward.
 * For an entry {@code s}: {@code L(s) = 1}, {@code L(v) = Σ L(u) × E(u→v) × fanout(u→v)} over edges into
 * {@code v}, where {@code E} is the product of {@code totalAttempts} over the edge's layers. In topological
 * order this sums every path, so diamonds are counted once per path.
 *
 * <p>A cycle reachable from an entry makes the bound infinite: {@code ERROR UNBOUNDED}. Every service
 * reachable from such a cycle is unbounded too; the rest is still computed exactly.
 *
 * <p>An UNKNOWN attempt count is counted as 1, so the result is a lower bound and is tagged.
 */
final class AmplificationRule {

    static final String ID = "PLM-A1";

    record Outcome(List<ServiceAmplification> services, List<Finding> findings) {}

    /** One topological pass from one entry. Edge objects in the graphs are indices into the edge list. */
    private record Run(
            String entry,
            Graph<String, Integer> dag,
            List<String> topo,
            Map<String, BigInteger> load,
            Map<String, Set<String>> unknowns) {}

    private AmplificationRule() {}

    static Outcome evaluate(ServiceGraph graph, AnalysisOptions options) {
        List<Edge> edges = graph.edges();
        List<String> entries = graph.services().values().stream()
                .filter(Service::entry)
                .map(Service::name)
                .sorted()
                .toList();

        Graph<String, Integer> all = reachableGraph(graph, entries);
        List<Finding> findings = new ArrayList<>();

        // Cycles and everything downstream of them.
        List<Set<String>> cycles = cyclicComponents(all);
        Set<String> cyclic = new TreeSet<>();
        cycles.forEach(cyclic::addAll);
        Set<String> unbounded = walk(cyclic, all, false, v -> true);
        for (Set<String> cycle : cycles) {
            findings.add(unboundedFinding(cycle, all, edges, entries, unbounded));
        }

        // Exact loads for everything else, one pass per entry.
        Map<String, Run> best = new TreeMap<>();
        Map<String, String> unboundedVia = new TreeMap<>();
        for (String entry : entries) {
            for (String v : walk(Set.of(entry), all, false, v -> true)) {
                if (unbounded.contains(v)) {
                    unboundedVia.putIfAbsent(v, entry);
                }
            }
            if (unbounded.contains(entry)) {
                continue;
            }
            Run run = run(entry, all, edges, unbounded);
            for (var e : run.load().entrySet()) {
                Run current = best.get(e.getKey());
                if (current == null || e.getValue().compareTo(current.load().get(e.getKey())) > 0) {
                    best.put(e.getKey(), run);
                }
            }
        }

        List<ServiceAmplification> services = new ArrayList<>();
        for (String v : all.vertexSet()) { // vertex set is sorted by name
            if (unbounded.contains(v)) {
                services.add(new ServiceAmplification(v, unboundedVia.get(v), null, true, List.of()));
                continue;
            }
            Run run = best.get(v);
            List<String> unknown = List.copyOf(run.unknowns().get(v));
            BigInteger value = run.load().get(v);
            services.add(new ServiceAmplification(v, run.entry(), value, false, unknown));
            if (value.compareTo(BigInteger.valueOf(options.maxAmplification())) > 0) {
                findings.add(loadFinding(v, run, edges, options, Severity.ERROR));
            } else if (!unknown.isEmpty()) {
                findings.add(loadFinding(v, run, edges, options, Severity.INFO));
            }
        }
        return new Outcome(services, findings);
    }

    // ---- graph construction ------------------------------------------------

    private static Graph<String, Integer> reachableGraph(ServiceGraph graph, List<String> entries) {
        Map<String, List<String>> next = new TreeMap<>();
        for (Edge e : graph.edges()) {
            next.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
        }
        Set<String> reachable = new TreeSet<>(entries);
        Deque<String> todo = new ArrayDeque<>(entries);
        while (!todo.isEmpty()) {
            for (String v : next.getOrDefault(todo.pop(), List.of())) {
                if (reachable.add(v)) {
                    todo.push(v);
                }
            }
        }
        Graph<String, Integer> g = new DirectedPseudograph<>(Integer.class);
        reachable.forEach(g::addVertex);
        for (int i = 0; i < graph.edges().size(); i++) {
            Edge e = graph.edges().get(i);
            if (reachable.contains(e.from())) {
                g.addEdge(e.from(), e.to(), i);
            }
        }
        return g;
    }

    private static List<Set<String>> cyclicComponents(Graph<String, Integer> g) {
        List<Set<String>> cycles = new ArrayList<>();
        for (Set<String> component : new KosarajuStrongConnectivityInspector<>(g).stronglyConnectedSets()) {
            String any = component.iterator().next();
            if (component.size() > 1 || g.containsEdge(any, any)) {
                cycles.add(new TreeSet<>(component));
            }
        }
        cycles.sort((a, b) -> a.iterator().next().compareTo(b.iterator().next()));
        return cycles;
    }

    /** Vertices reachable from {@code starts} (inclusive), following edges forward or backward. */
    private static Set<String> walk(
            Collection<String> starts, Graph<String, Integer> g, boolean backward,
            Predicate<String> allowed) {
        Set<String> seen = new TreeSet<>();
        Deque<String> todo = new ArrayDeque<>();
        for (String s : starts) {
            if (allowed.test(s) && seen.add(s)) {
                todo.push(s);
            }
        }
        while (!todo.isEmpty()) {
            String u = todo.pop();
            Set<Integer> step = backward ? g.incomingEdgesOf(u) : g.outgoingEdgesOf(u);
            for (int i : step) {
                String v = backward ? g.getEdgeSource(i) : g.getEdgeTarget(i);
                if (allowed.test(v) && seen.add(v)) {
                    todo.push(v);
                }
            }
        }
        return seen;
    }

    // ---- computation --------------------------------------------------------

    private static Run run(String entry, Graph<String, Integer> all, List<Edge> edges, Set<String> unbounded) {
        Set<String> sub = walk(Set.of(entry), all, false, v -> !unbounded.contains(v));
        Graph<String, Integer> dag = new DirectedPseudograph<>(Integer.class);
        sub.forEach(dag::addVertex);
        for (int i : new TreeSet<>(all.edgeSet())) {
            if (sub.contains(all.getEdgeSource(i)) && sub.contains(all.getEdgeTarget(i))) {
                dag.addEdge(all.getEdgeSource(i), all.getEdgeTarget(i), i);
            }
        }
        List<String> topo = new ArrayList<>();
        new TopologicalOrderIterator<>(dag).forEachRemaining(topo::add);

        Map<String, BigInteger> load = new TreeMap<>();
        Map<String, Set<String>> unknowns = new TreeMap<>();
        for (String v : sub) {
            load.put(v, v.equals(entry) ? BigInteger.ONE : BigInteger.ZERO);
            unknowns.put(v, new TreeSet<>());
        }
        for (String u : topo) {
            for (int i : new TreeSet<>(dag.outgoingEdgesOf(u))) {
                Edge e = edges.get(i);
                String v = dag.getEdgeTarget(i);
                load.merge(v, load.get(u).multiply(attempts(e)).multiply(BigInteger.valueOf(e.fanout())), BigInteger::add);
                unknowns.get(v).addAll(unknowns.get(u));
                unknowns.get(v).addAll(Attempts.unknownAttempts(e));
            }
        }
        return new Run(entry, dag, topo, load, unknowns);
    }

    /** Product of the known totalAttempts of the edge's layers; UNKNOWN counts as 1. */
    private static BigInteger attempts(Edge e) {
        BigInteger product = BigInteger.ONE;
        for (Layer layer : e.layers()) {
            if (Attempts.known(layer)) {
                product = product.multiply(BigInteger.valueOf(layer.retry().totalAttempts().value()));
            }
        }
        return product;
    }

    // ---- findings -------------------------------------------------------------

    private static Finding loadFinding(String v, Run run, List<Edge> edges, AnalysisOptions options, Severity severity) {
        Set<String> ancestors = walk(Set.of(v), run.dag(), true, x -> true);
        List<String> contributors = run.topo().stream()
                .filter(s -> ancestors.contains(s) && !s.equals(v) && !s.equals(run.entry()))
                .toList();
        boolean lowerBound = !run.unknowns().get(v).isEmpty();

        StringBuilder detail = new StringBuilder(lowerBound ? "at least ×" : "×").append(run.load().get(v));
        if (!contributors.isEmpty()) {
            detail.append(" (");
            for (int i = 0; i < contributors.size(); i++) {
                detail.append(i == 0 ? "" : ", ").append(contributors.get(i)).append(" ×").append(run.load().get(contributors.get(i)));
            }
            detail.append(')');
        }
        detail.append(", limit ").append(options.maxAmplification());
        if (severity == Severity.INFO) {
            detail.append("; the real value may be higher");
        }

        List<UsedValue> values = new ArrayList<>();
        for (int i : new TreeSet<>(run.dag().edgeSet())) {
            if (ancestors.contains(run.dag().getEdgeSource(i)) && ancestors.contains(run.dag().getEdgeTarget(i))) {
                values.addAll(edgeValues(edges.get(i)));
            }
        }
        return new Finding(ID, severity, v, detail.toString(), values, List.copyOf(run.unknowns().get(v)));
    }

    private static Finding unboundedFinding(
            Set<String> cycle, Graph<String, Integer> all, List<Edge> edges, List<String> entries, Set<String> unbounded) {
        List<String> reachedFrom = entries.stream()
                .filter(en -> walk(Set.of(en), all, false, v -> true).containsAll(cycle))
                .toList();
        List<String> downstream = unbounded.stream().filter(v -> !cycle.contains(v)).toList();
        String detail = "UNBOUNDED: " + String.join(", ", cycle) + " call"
                + (cycle.size() == 1 ? "s itself" : " each other in a cycle")
                + ", reachable from " + String.join(", ", reachedFrom)
                + (downstream.isEmpty() ? "" : "; also unbounded: " + String.join(", ", downstream));
        List<UsedValue> values = new ArrayList<>();
        for (int i : new TreeSet<>(all.edgeSet())) {
            if (cycle.contains(all.getEdgeSource(i)) && cycle.contains(all.getEdgeTarget(i))) {
                values.addAll(edgeValues(edges.get(i)));
            }
        }
        List<String> unknowns = new ArrayList<>();
        for (UsedValue u : values) {
            if (u.display().equals("UNKNOWN")) {
                unknowns.add(u.name());
            }
        }
        return new Finding(ID, Severity.ERROR, String.join(", ", cycle), detail, values, unknowns);
    }

    private static List<UsedValue> edgeValues(Edge e) {
        List<UsedValue> values = new ArrayList<>();
        for (int i = 0; i < e.layers().size(); i++) {
            values.add(UsedValue.of(Attempts.layerName(e, i) + " totalAttempts", e.layers().get(i).retry().totalAttempts()));
        }
        if (e.fanout() > 1) {
            values.add(new UsedValue(e.location() + " fanout", String.valueOf(e.fanout()),
                    Source.EXPLICIT, "edge " + e.location()));
        }
        return values;
    }
}
