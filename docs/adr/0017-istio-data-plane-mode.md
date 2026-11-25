# 0017 Istio data plane mode: which edges an Istio layer applies to

- Status: accepted
- Date: 2026-11-24
- Issue: #27

## Context and problem statement

Since #7, a directory with Istio manifests puts every edge in the mesh: each edge gets an `istio` layer, from its
callee's VirtualService or from the mesh default policy. That holds only when the proxy that applies HTTP policy sits
on the path. Istio has two data plane modes:

- **Sidecar.** The caller's sidecar applies the route of the callee's host, retries and timeouts included. A pod gets
  a sidecar only when it is injected: the injector reads `istio-injection` and `istio.io/rev` on the namespace and
  `sidecar.istio.io/inject` and `istio.io/rev` on the pod. With no label, the pod is not injected (unless the
  injector is installed with `enableNamespacesByDefault`). `istioctl kube-inject` injects a sidecar into the
  manifest itself, which then carries the `sidecar.istio.io/status` annotation.
- **Ambient.** `istio.io/dataplane-mode=ambient` on the namespace or the pod (`none` on the pod opts out). ztunnel
  handles L4 only and applies no HTTP retry or timeout. HTTP policy needs a waypoint, attached to the callee with
  `istio.io/use-waypoint` on its Service or namespace. A VirtualService through a waypoint is Alpha; HTTPRoute is the
  supported route resource. If a pod is set up for both modes, the sidecar wins.

Istio 1.31 source (`pilot/pkg/networking/core`, 56245da4): a waypoint translates a VirtualService route with the same
`TranslateRoute` as a sidecar (`listener_waypoint.go`), so a route without `retries` gets the MeshConfig
`defaultHttpRetryPolicy` there too (`route.go`). A waypoint host without a route gets
`BuildDefaultHTTPOutboundRoute`, the same default route a sidecar uses (`httproute.go`). So where a waypoint applies,
its layer is the one a sidecar would apply.

## Decision drivers

- Never invent a value (principle 2). An edge without a proxy has no Istio retries; an edge where the mode is not
  known keeps today's assumption, said in a note.
- Testbeds without any data plane labels (TrainTicket injects with `istioctl kube-inject`) must not change.
- Labels are read from the repository; the cluster can have labels the repository does not show, as for ADR 0016.

## Decision outcome

**Reading.** Data plane labels are read from `Namespace` manifests, from workloads (`Deployment`, `StatefulSet`,
`DaemonSet`: pod template labels and annotations; `Pod`) and from `Service` manifests (`istio.io/use-waypoint`).
A workload belongs to the service whose name is its `metadata.name` or its `app` / `app.kubernetes.io/name` label;
a Service to the service of its name. A workload without `metadata.namespace` has an unknown namespace. These are read only when some Kubernetes manifest
of the directory has a data plane label; a manifest that cannot be parsed (a Helm template) is noted when it has
one, else skipped.

**Mode of a caller**, with `file:line` evidence:

| Signal | Mode |
| --- | --- |
| `sidecar.istio.io/inject: "false"` on the pod or `istio-injection: disabled` on the namespace | no sidecar |
| `sidecar.istio.io/status` on the pod (kube-inject output), `sidecar.istio.io/inject: "true"`, `istio.io/rev` on the pod or the namespace, `istio-injection: enabled` | `sidecar` |
| else `istio.io/dataplane-mode` on the pod (`ambient` / `none`), else on the namespace | `ambient` / no ambient |
| no label, with the workload and its namespace manifest read | `none` (injector default, `LIBRARY_DEFAULT`) |
| anything else | `UNKNOWN` |

**Waypoint of a callee:** `istio.io/use-waypoint` on its Service, else on its namespace (`none` = no waypoint);
no label with the namespace manifest read is no waypoint (`LIBRARY_DEFAULT`); else `UNKNOWN`. Pod-level
`use-waypoint` concerns workload-addressed traffic, which Plimsoll does not model.

**Edges.** A running sidecar dump (ADR 0016) still decides an edge's Istio layer. Otherwise:

| Caller | Callee waypoint | Istio layer |
| --- | --- | --- |
| `sidecar` | any | yes |
| `ambient` | yes | yes; a VirtualService route there is noted as Alpha |
| `ambient` | no | none: ztunnel applies no HTTP policy |
| `ambient` | `UNKNOWN` | yes, with a note |
| `none` | any | none |
| `UNKNOWN` | any | yes (as before); a note only when the directory has data plane labels at all |

A sidecar caller towards a callee with a waypoint keeps one layer; whether both proxies retry is not modelled, and
a note says so.

### Consequences

- An Istio layer that is not on the path no longer counts towards A1, A3 and A5.
- The new KB entries are the documented defaults: no injection without a label, no waypoint without a label.
- Not read: the injector's `enableNamespacesByDefault` and custom `namespaceSelector`s, `--set` values, labels set
  in the cluster only. A note names the case where the namespace manifest gives no label, since a cluster label
  or manual injection would change the answer.
- HTTPRoute (the supported route resource for waypoints) is ADR 0018.
