# 0018 Mesh HTTPRoutes are read as Istio converts them

- Status: accepted
- Date: 2026-11-25
- Issue: #27

## Context and problem statement

HTTPRoute (Gateway API) is the supported route resource for waypoints, and Istio sidecars apply it too. A route whose
`parentRefs` name a Service configures calls inside the mesh (GAMMA); one whose parents are Gateways does not. Its
`retry` and `timeouts` need their own normalization, and other meshes (Linkerd, Cilium, Kuma) implement the same
resource with other semantics.

Istio 1.31 source (56245da4) converts a rule to a VirtualService HTTP route (`pilot/pkg/config/kube/gateway/conversion.go`):

| HTTPRoute rule | Istio |
| --- | --- |
| `retry.attempts: n` | n retries, so `n + 1` tries; 0 removes the retry policy, its per-try timeout included (`retry.go`) |
| `retry` without `attempts` | 2 retries, if the field reaches Istio absent |
| `retry.codes` | added to `connect-failure,refused-stream,unavailable,cancelled` |
| `retry.backoff` | base interval; absent is Envoy's 25 ms |
| no `retry` | no policy, so the mesh `defaultHttpRetryPolicy` or Istio's default applies (`route.go`) |
| `timeouts.request` | route timeout; `0s` or absent is none |
| `timeouts.backendRequest` | per-try timeout with `retry`; without it, the route timeout in place of `request`; `0s` is ignored |

The default of `attempts` depends on the installed CRDs: up to Gateway API 1.6 the field has no default, so Istio's 2
applies; the 1.7.0 CRD has `default: 1`, which the API server fills in before Istio reads the route. GEP-1731's
"default 1" is therefore not what every cluster does, and a repository rarely says which CRDs are installed.

## Decision outcome

- An HTTPRoute with a core Service parent (`group: ""`, `kind: Service`) becomes one `istio` route per rule, with the
  table above. The host is the Service in the parent's namespace, else the route's.
- `retry` without `attempts` is `UNKNOWN`; the evidence names both defaults. The KB has both, `verified_by: source`.
- Several rules, or several HTTPRoutes of one host, follow the several-routes rule: fields they agree on are kept, the
  rest is `UNKNOWN`. A rule without a weighted backend calls nothing and is not read (note).
- A VirtualService and an HTTPRoute for one host: both are candidates under the same rule, with a note. Which one a
  proxy uses depends on its configuration order.
- A route in another namespace than its Service (a consumer route) is a candidate next to the mesh default, with a
  note. A waypoint ignores it unless it is in the waypoint's namespace (`getVirtualServiceForWaypoint`); for sidecars
  it depends on the caller's namespace and routes.
- HTTPRoute files are read only when an Istio manifest or a data plane label shows that Istio is the mesh. Otherwise
  each mesh HTTPRoute is noted and no layer is added. An HTTPRoute file that is not valid YAML (a Helm template) is
  noted.
- Gateway-only parents, ServiceEntry parents, GRPCRoute and a parent's `port` are noted, not modelled.
- ADR 0017's Alpha note for a route through a waypoint is about VirtualService only.

### Consequences

- HTTPRoutes used for north-south traffic (Gateway parents) add no layer; an ingress gateway in front of an entry
  service is not an edge of the graph.
- The version of the CRDs could become an input later; until then `attempts` has to be written to be known.
