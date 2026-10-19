package io.github.hasanyelaldi.plimsoll.collect;

import io.github.hasanyelaldi.plimsoll.collect.http.HttpClientCollector;
import io.github.hasanyelaldi.plimsoll.collect.istio.IstioCollector;
import io.github.hasanyelaldi.plimsoll.collect.istio.IstioCollector.Route;
import io.github.hasanyelaldi.plimsoll.model.Durations;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.Layer;
import io.github.hasanyelaldi.plimsoll.model.RetryPolicy;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Merges collector outputs onto the edges they apply to, so that one edge carries every layer a call passes
 * through, outermost first: the caller's Resilience4j decorators, then its HTTP client, then the Istio sidecar.
 *
 * <p>Edges come from code (the Resilience4j collector and its edges hint). A VirtualService names only the
 * callee, so its routes are matched by {@link Edge#to()} and apply to every caller. When Istio manifests are
 * given, every callee is assumed to be in the mesh: one without a VirtualService gets Istio's default policy,
 * and a note says so.
 *
 * <p>A host with several HTTP routes picks one per request by its match conditions, which an edge does not
 * describe. A field on which the routes agree is kept; a field on which they differ is UNKNOWN and names the
 * candidates. Nothing is picked for the caller.
 */
public final class GraphAssembler {

    private static final String ISTIO = "istio";

    /**
     * @param edges in the order of the input edges
     * @param notes callees assumed to be in the mesh, then routes no edge calls; each group sorted by callee
     */
    public record Result(List<Edge> edges, List<String> notes) {
        public Result {
            edges = List.copyOf(edges);
            notes = List.copyOf(notes);
        }
    }

    private GraphAssembler() {}

    /** @param istio the Istio collector's output, or {@code null} when the graph has no mesh */
    public static Result assemble(List<Edge> edges, IstioCollector.Result istio) {
        return assemble(edges, null, istio);
    }

    /**
     * @param http  the HTTP client collector's output, or {@code null} when no client is known
     * @param istio the Istio collector's output, or {@code null} when the graph has no mesh
     */
    public static Result assemble(List<Edge> edges, HttpClientCollector.Result http, IstioCollector.Result istio) {
        if (http != null) {
            edges = edges.stream().map(e -> http.layerFor(e).map(l -> withLayer(e, l)).orElse(e)).toList();
        }
        if (istio == null) {
            return new Result(edges, List.of());
        }
        Map<String, List<Route>> routesByCallee = new LinkedHashMap<>();
        for (Route r : istio.routes()) {
            routesByCallee.computeIfAbsent(r.to(), k -> new ArrayList<>()).add(r);
        }
        Map<String, Layer> layerByCallee = new TreeMap<>();
        routesByCallee.forEach((to, routes) -> layerByCallee.put(to, merged(to, routes)));

        List<Edge> out = new ArrayList<>();
        Set<String> meshDefault = new TreeSet<>();
        Set<String> called = new TreeSet<>();
        for (Edge e : edges) {
            Layer istioLayer = layerByCallee.get(e.to());
            if (istioLayer == null) {
                istioLayer = istio.meshDefault();
                meshDefault.add(e.to());
            }
            called.add(e.to());
            out.add(withLayer(e, istioLayer));
        }

        List<String> notes = new ArrayList<>();
        for (String to : meshDefault) {
            notes.add(to + ": no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies");
        }
        new TreeMap<>(routesByCallee).forEach((to, routes) -> {
            if (!called.contains(to)) {
                notes.add(to + ": HTTP routes at " + routes.stream().map(Route::evidence).collect(Collectors.joining(", "))
                        + " are not applied; no collected edge calls " + to);
            }
        });
        return new Result(out, notes);
    }

    /** {@code e} with {@code inner} as its new innermost layer. */
    private static Edge withLayer(Edge e, Layer inner) {
        List<Layer> layers = new ArrayList<>(e.layers());
        layers.add(inner);
        return new Edge(e.from(), e.to(), e.operation(), e.idempotent(), e.fanout(), layers);
    }

    private static Layer merged(String to, List<Route> routes) {
        if (routes.size() == 1) {
            return routes.get(0).layer();
        }
        RetryPolicy retry = new RetryPolicy(
                agreed(to, routes, "totalAttempts", p -> p.retry().totalAttempts()),
                agreed(to, routes, "perTryTimeout", p -> p.retry().perTryTimeout()),
                agreed(to, routes, "initialBackoff", p -> p.retry().initialBackoff()),
                agreed(to, routes, "multiplier", p -> p.retry().multiplier()),
                agreed(to, routes, "maxBackoff", p -> p.retry().maxBackoff()),
                agreed(to, routes, "jitter", p -> p.retry().jitter()),
                agreed(to, routes, "retryOn", p -> p.retry().retryOn()));
        return new Layer(ISTIO, retry, agreed(to, routes, "overallTimeout", Layer::overallTimeout));
    }

    private static <T> Val<T> agreed(String to, List<Route> routes, String name, Function<Layer, Val<T>> field) {
        List<Val<T>> values = routes.stream().map(r -> field.apply(r.layer())).toList();
        Val<T> first = values.get(0);
        if (values.stream().allMatch(v -> Objects.equals(v.value(), first.value()))) {
            return new Val<>(first.value(), first.source(),
                    first.evidence() + "; same in all " + routes.size() + " HTTP routes of " + to);
        }
        List<String> candidates = new ArrayList<>();
        for (int i = 0; i < routes.size(); i++) {
            Val<T> v = values.get(i);
            candidates.add(routes.get(i).route() + " at " + routes.get(i).evidence() + ": "
                    + (v.isKnown() ? show(v.value()) : "UNKNOWN"));
        }
        return Val.unknown(to + " has " + routes.size() + " HTTP routes with different " + name + " ("
                + String.join(", ", candidates) + "); which one applies depends on the request match");
    }

    private static String show(Object value) {
        return switch (value) {
            case Duration d -> Durations.format(d);
            case Set<?> set -> set.stream().map(String::valueOf).sorted().collect(Collectors.joining(",", "[", "]"));
            default -> String.valueOf(value);
        };
    }
}
