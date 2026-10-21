package io.github.hasanyelaldi.plimsoll.collect;

import io.github.hasanyelaldi.plimsoll.collect.gateway.GatewayCollector;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.EdgesHint;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Edge;
import io.github.hasanyelaldi.plimsoll.model.Service;
import io.github.hasanyelaldi.plimsoll.model.ServiceGraph;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Turns assembled edges into a {@link ServiceGraph}: one {@link Service} per service named by an edge, a hint or a
 * gateway, with its deadline and entry flag decided by the rules of ADR 0009.
 *
 * <ul>
 *   <li><b>deadline</b>: {@code deadline:} in the service's own {@code edges-hint.yaml}, otherwise UNKNOWN. Never
 *       taken from the timeouts its callers impose or from its own outbound timeouts, so that PLM-A2a and PLM-A2b
 *       never compare a value with itself.
 *   <li><b>entry</b>: {@code entry:} in the hint wins; otherwise a Spring Cloud Gateway with at least one collected
 *       route is an entry; every other service is not.
 * </ul>
 *
 * <p>{@code Service.entry} is a plain boolean, so the rule that made each entry, and every hint that overrode it,
 * is written to the notes with its evidence.
 */
public final class ServiceAssembler {

    /**
     * @param notes one per entry decision, sorted by service, then a note if no service is an entry
     */
    public record Result(ServiceGraph graph, List<String> notes) {
        public Result {
            notes = List.copyOf(notes);
        }
    }

    private ServiceAssembler() {}

    /**
     * @param edges    the assembled edges ({@link GraphAssembler}), gateway edges included
     * @param hints    the edges hints of the services whose code is known
     * @param gateways the Spring Cloud Gateway collector's outputs
     */
    public static Result assemble(List<Edge> edges, List<EdgesHint> hints, List<GatewayCollector.Result> gateways) {
        Map<String, EdgesHint> hintByService = new TreeMap<>();
        for (EdgesHint h : hints) {
            if (hintByService.putIfAbsent(h.service(), h) != null) {
                throw new InvalidSpecException("service " + h.service() + " has more than one edges hint");
            }
        }
        Map<String, GatewayCollector.Result> gatewayByService = new TreeMap<>();
        for (GatewayCollector.Result g : gateways) {
            gatewayByService.put(g.service(), g);
        }

        TreeSet<String> names = new TreeSet<>(hintByService.keySet());
        names.addAll(gatewayByService.keySet());
        for (Edge e : edges) {
            names.add(e.from());
            names.add(e.to());
        }

        Map<String, Service> services = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        for (String name : names) {
            EdgesHint hint = hintByService.get(name);
            GatewayCollector.Result gateway = gatewayByService.get(name);
            boolean entry = entry(name, hint, gateway, notes);
            services.put(name, new Service(name, deadline(name, hint), entry));
        }
        if (services.values().stream().noneMatch(Service::entry)) {
            notes.add("no entry service: PLM-A1 has nothing to start from; set 'entry: true' in the edges-hint.yaml "
                    + "of the service that takes outside requests");
        }
        return new Result(new ServiceGraph(services, edges), notes);
    }

    private static Val<Duration> deadline(String name, EdgesHint hint) {
        String how = "set 'deadline:' in the edges-hint.yaml of " + name + " (ADR 0009)";
        if (hint == null) {
            return Val.unknown(name + " has no edges hint; " + how);
        }
        Val<Duration> d = hint.deadline();
        return d.isKnown() ? d : Val.unknown(d.evidence() + "; " + how);
    }

    private static boolean entry(String name, EdgesHint hint, GatewayCollector.Result gateway, List<String> notes) {
        boolean routes = gateway != null && !gateway.edges().isEmpty();
        String gatewayRule = routes
                ? "Spring Cloud Gateway routing to " + String.join(", ",
                        new TreeSet<>(gateway.edges().stream().map(Edge::to).toList()))
                : null;
        if (hint != null && hint.entry().isKnown()) {
            boolean entry = hint.entry().value();
            if (entry || routes) {
                notes.add(name + ": " + (entry ? "entry" : "not an entry") + " (" + hint.entry().evidence() + ")"
                        + (routes && !entry ? "; overrides the gateway rule (" + gatewayRule + ")" : ""));
            }
            return entry;
        }
        if (routes) {
            notes.add(name + ": entry, " + gatewayRule + " (ADR 0009)");
            return true;
        }
        return false;
    }
}
