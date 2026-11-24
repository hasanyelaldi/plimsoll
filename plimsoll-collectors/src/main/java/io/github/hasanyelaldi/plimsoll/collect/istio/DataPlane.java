package io.github.hasanyelaldi.plimsoll.collect.istio;

import com.fasterxml.jackson.core.JsonToken;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.io.YamlTree;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Entry;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.MapNode;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Node;
import io.github.hasanyelaldi.plimsoll.io.YamlTree.Scalar;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * ADR 0017: the Istio data plane mode of each service, from the labels of its namespace and workload manifests, and
 * whether its Service is reached through a waypoint. A caller's mode decides whether a proxy that applies HTTP policy
 * sits on its calls.
 */
public final class DataPlane {

    private static final String KB = "istio";
    /** Text that marks a manifest with data plane labels. */
    public static final List<String> MARKERS = List.of("istio-injection", "istio.io/rev", "sidecar.istio.io/",
            "istio.io/dataplane-mode", "istio.io/use-waypoint");
    private static final Set<String> WORKLOADS = Set.of("Deployment", "StatefulSet", "DaemonSet");
    private static final String INJECT = "sidecar.istio.io/inject";
    private static final String STATUS = "sidecar.istio.io/status";
    private static final String REV = "istio.io/rev";
    private static final String INJECTION = "istio-injection";
    private static final String MODE = "istio.io/dataplane-mode";
    private static final String WAYPOINT = "istio.io/use-waypoint";

    public enum Mode {
        SIDECAR, AMBIENT, NONE;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /**
     * @param modes     per service, its data plane mode
     * @param waypoints per service, whether calls to it pass a waypoint
     * @param files     the manifests that gave a namespace, workload or Service of the graph, sorted
     * @param notes     manifests that could not be read, in file order
     */
    public record Result(Map<String, Val<Mode>> modes, Map<String, Val<Boolean>> waypoints, List<String> files,
            List<String> notes) {
        public Result {
            modes = Map.copyOf(modes);
            waypoints = Map.copyOf(waypoints);
            files = List.copyOf(files);
            notes = List.copyOf(notes);
        }

        public Val<Mode> mode(String service) {
            return modes.getOrDefault(service, Val.unknown("no workload manifest for " + service));
        }

        public Val<Boolean> waypoint(String service) {
            return waypoints.getOrDefault(service, Val.unknown("no Service or namespace manifest for " + service));
        }
    }

    private record Label(String value, String at) {
        String evidence() {
            return at + " (" + value + ")";
        }
    }

    private record Resource(String name, String namespace, Map<String, Label> labels, Map<String, Label> pod,
            String file) {
        boolean names(String service) {
            return name.equals(service) || matches(labels, service) || matches(pod, service);
        }

        private static boolean matches(Map<String, Label> labels, String service) {
            return List.of("app", "app.kubernetes.io/name").stream()
                    .anyMatch(k -> labels.containsKey(k) && labels.get(k).value().equals(service));
        }
    }

    private final DefaultsKb kb;
    private final Map<String, Resource> namespaces = new TreeMap<>();
    private final List<Resource> workloads = new ArrayList<>();
    private final List<Resource> services = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final Set<String> used = new TreeSet<>();

    private DataPlane(DefaultsKb kb) {
        this.kb = kb;
    }

    /**
     * @param base     evidence paths are relative to it
     * @param files    Kubernetes manifests; one that cannot be parsed is noted when it has data plane labels, else
     *                 skipped
     * @param graph    the services to resolve
     */
    public static Result collect(Path base, List<Path> files, Collection<String> graph, DefaultsKb kb) {
        DataPlane d = new DataPlane(kb);
        base = base.toAbsolutePath();
        for (Path p : files) {
            String file = base.relativize(p.toAbsolutePath()).toString().replace('\\', '/');
            String text;
            try {
                text = Files.readString(p, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new InvalidSpecException(file + ": cannot read: " + e.getMessage(), e);
            }
            List<Node> docs;
            try {
                docs = YamlTree.parseAll(text, file);
            } catch (InvalidSpecException e) {
                if (MARKERS.stream().anyMatch(text::contains)) {
                    d.notes.add(e.getMessage() + "; its data plane labels are not read");
                }
                continue;
            }
            for (Node doc : docs) {
                if (doc instanceof MapNode m) {
                    d.document(m, file);
                }
            }
        }
        Map<String, Val<Mode>> modes = new TreeMap<>();
        Map<String, Val<Boolean>> waypoints = new TreeMap<>();
        for (String s : new TreeSet<>(graph)) {
            modes.put(s, d.mode(s));
            waypoints.put(s, d.waypoint(s));
        }
        return new Result(modes, waypoints, List.copyOf(d.used), d.notes);
    }

    private void document(MapNode m, String file) {
        String kind = text(m, "kind");
        if (kind == null || text(m, "apiVersion") == null) {
            return;
        }
        MapNode metadata = map(m, "metadata");
        String name = metadata == null ? null : text(metadata, "name");
        if (name == null) {
            return;
        }
        String namespace = text(metadata, "namespace");
        Map<String, Label> labels = labels(metadata, file);
        if (kind.equals("Namespace")) {
            namespaces.putIfAbsent(name, new Resource(name, name, labels, Map.of(), file));
        } else if (kind.equals("Service")) {
            services.add(new Resource(name, namespace, labels, Map.of(), file));
        } else if (kind.equals("Pod")) {
            workloads.add(new Resource(name, namespace, labels, labels, file));
        } else if (WORKLOADS.contains(kind)) {
            MapNode spec = map(m, "spec");
            MapNode template = spec == null ? null : map(spec, "template");
            MapNode podMetadata = template == null ? null : map(template, "metadata");
            workloads.add(new Resource(name, namespace, labels,
                    podMetadata == null ? Map.of() : labels(podMetadata, file), file));
        }
    }

    /** Labels and annotations together; a label wins over an annotation of the same key. */
    private static Map<String, Label> labels(MapNode metadata, String file) {
        Map<String, Label> out = new LinkedHashMap<>();
        for (String key : List.of("labels", "annotations")) {
            MapNode m = map(metadata, key);
            if (m == null) {
                continue;
            }
            m.entries().forEach((k, e) -> {
                if (e.value() instanceof Scalar s && s.token() != JsonToken.VALUE_NULL) {
                    out.putIfAbsent(k, new Label(s.text(), file + ":" + e.keyLine() + " " + k));
                }
            });
        }
        return out;
    }

    // ---- mode ------------------------------------------------------------

    private Val<Mode> mode(String service) {
        List<Resource> found = workloads.stream().filter(w -> w.names(service)).toList();
        if (found.isEmpty()) {
            return Val.unknown("no workload manifest for " + service);
        }
        List<Val<Mode>> modes = found.stream().map(this::mode).toList();
        found.forEach(w -> used.add(w.file()));
        if (modes.stream().map(Val::value).distinct().count() > 1) {
            return Val.unknown("its workloads differ: " + modes.stream()
                    .map(v -> (v.isKnown() ? v.value() : "unknown") + " (" + v.evidence() + ")")
                    .collect(Collectors.joining(", ")));
        }
        return modes.get(0);
    }

    private Val<Mode> mode(Resource w) {
        Map<String, Label> pod = w.pod();
        Resource ns = w.namespace() == null ? null : namespaces.get(w.namespace());
        Map<String, Label> nsLabels = ns == null ? Map.of() : ns.labels();
        if (ns != null) {
            used.add(ns.file());
        }
        String where = w.file() + " " + w.name();

        // sidecar injection: disabled anywhere wins, then enabled anywhere (sidecar-injection docs)
        Val<Boolean> sidecar;
        if (is(pod, INJECT, "false")) {
            sidecar = Val.explicit(false, pod.get(INJECT).evidence());
        } else if (is(nsLabels, INJECTION, "disabled")) {
            sidecar = Val.explicit(false, nsLabels.get(INJECTION).evidence());
        } else if (pod.containsKey(STATUS)) {
            sidecar = Val.explicit(true, pod.get(STATUS).at() + " (injected into the manifest)");
        } else if (is(pod, INJECT, "true")) {
            sidecar = Val.explicit(true, pod.get(INJECT).evidence());
        } else if (pod.containsKey(REV)) {
            sidecar = Val.explicit(true, pod.get(REV).evidence());
        } else if (is(nsLabels, INJECTION, "enabled")) {
            sidecar = Val.explicit(true, nsLabels.get(INJECTION).evidence());
        } else if (nsLabels.containsKey(REV)) {
            sidecar = Val.explicit(true, nsLabels.get(REV).evidence());
        } else if (ns != null) {
            sidecar = kb.defaultOr(KB, "injection.default", Boolean::valueOf, "no injection label on " + where
                    + " or namespace " + ns.name() + " (" + ns.file() + ")");
        } else {
            sidecar = Val.unknown(w.namespace() == null ? where + " has no metadata.namespace"
                    : "no manifest for namespace " + w.namespace() + " of " + where);
        }
        if (Boolean.TRUE.equals(sidecar.value())) {
            return Val.explicit(Mode.SIDECAR, sidecar.evidence());
        }

        Val<Boolean> ambient;
        Label mode = pod.containsKey(MODE) ? pod.get(MODE) : nsLabels.get(MODE);
        if (mode != null) {
            ambient = Val.explicit(mode.value().equals("ambient"), mode.evidence());
        } else if (ns != null) {
            ambient = kb.defaultOr(KB, "ambient.default", Boolean::valueOf, "no " + MODE + " label on " + where
                    + " or namespace " + ns.name() + " (" + ns.file() + ")");
        } else {
            ambient = Val.unknown(sidecar.evidence());
        }
        if (Boolean.TRUE.equals(ambient.value())) {
            return Val.explicit(Mode.AMBIENT, ambient.evidence());
        }
        if (sidecar.isKnown() && ambient.isKnown()) {
            Source source = sidecar.source() == Source.EXPLICIT && ambient.source() == Source.EXPLICIT
                    ? Source.EXPLICIT : Source.LIBRARY_DEFAULT;
            String evidence = sidecar.evidence().equals(ambient.evidence()) ? sidecar.evidence()
                    : sidecar.evidence() + "; " + ambient.evidence();
            return new Val<>(Mode.NONE, source, evidence);
        }
        return Val.unknown(sidecar.isKnown() ? ambient.evidence() : sidecar.evidence());
    }

    // ---- waypoint --------------------------------------------------------

    private Val<Boolean> waypoint(String service) {
        Resource svc = services.stream().filter(s -> s.name().equals(service)).findFirst().orElse(null);
        if (svc != null && svc.labels().containsKey(WAYPOINT)) {
            used.add(svc.file());
            return waypoint(svc.labels().get(WAYPOINT));
        }
        String namespace = svc != null && svc.namespace() != null ? svc.namespace()
                : workloads.stream().filter(w -> w.names(service) && w.namespace() != null)
                        .map(Resource::namespace).findFirst().orElse(null);
        Resource ns = namespace == null ? null : namespaces.get(namespace);
        if (ns == null) {
            return Val.unknown(namespace == null ? "the namespace of " + service + " is not known"
                    : "no manifest for namespace " + namespace + " of " + service);
        }
        used.add(ns.file());
        if (ns.labels().containsKey(WAYPOINT)) {
            return waypoint(ns.labels().get(WAYPOINT));
        }
        return kb.defaultOr(KB, "waypoint.default", Boolean::valueOf, "no " + WAYPOINT + " label on "
                + (svc == null ? "" : "Service " + service + " (" + svc.file() + ") or ") + "namespace " + namespace
                + " (" + ns.file() + ")");
    }

    private static Val<Boolean> waypoint(Label label) {
        return Val.explicit(!label.value().equals("none"), label.evidence());
    }

    // ---- accessors -------------------------------------------------------

    private static boolean is(Map<String, Label> labels, String key, String value) {
        return labels.containsKey(key) && labels.get(key).value().equalsIgnoreCase(value);
    }

    private static MapNode map(MapNode m, String key) {
        Entry e = m.entries().get(key);
        return e != null && e.value() instanceof MapNode child ? child : null;
    }

    private static String text(MapNode m, String key) {
        Entry e = m.entries().get(key);
        return e != null && e.value() instanceof Scalar s && s.token() != JsonToken.VALUE_NULL ? s.text() : null;
    }
}
