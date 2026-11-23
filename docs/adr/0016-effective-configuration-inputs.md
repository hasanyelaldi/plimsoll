# 0016 Effective configuration inputs

- Status: accepted
- Date: 2026-11-21
- Issue: #25

## Context and problem statement

Plimsoll reads the configuration declared in the repository. What runs can differ. A config server, environment
variables in a Deployment, Helm values or the Istio MeshConfig `defaultHttpRetryPolicy` can change a retry count or
a timeout without any change in the repository. Today the Coverage section lists the config imports and profiles it
did not read, but it cannot say whether they changed anything.

A running environment can show the effective values:

- Spring Boot's `/actuator/configprops` lists the bound `@ConfigurationProperties` beans with their values, which
  includes Resilience4j, Spring Cloud Gateway, Spring Cloud LoadBalancer and Spring AI settings.
- The Envoy admin `config_dump` of a sidecar lists the route configuration it applies, with the retry policy and
  timeouts of each route, mesh defaults included.

## Decision drivers

- No network calls (ADR 0002 and CLAUDE.md). Plimsoll reads files that someone saved from the environment.
- Every value carries provenance (ADR 0003). A value from a dump says which dump and where in it.
- Same input, same report. A dump is a file, read like any other input.
- The report should say both that the repository is not what runs and what does run.

## Decision outcome

**Input.** `plimsoll analyze <dir> --effective <service>=<file>`, repeatable. The service is named explicitly,
because neither dump says reliably which service it belongs to: configprops names the Spring application context,
and a sidecar's route dump names the routes it calls out to, not its own service. The format is recognised from the
content: a configprops document (`contexts.*.beans`), or an Envoy `config_dump` with route configurations. Anything
else is invalid input (exit code 2), and so is a second dump of the same format for one service. `--effective` needs
a directory; a hand-written spec takes none.

**Values.** A setting read from a dump replaces the declared one in the graph. It is `EXPLICIT`, and its evidence is
`<dump>:<line> (<JSON pointer>)`, so that ADR 0013 places a SARIF result on the dump file. The rules then run on the
effective graph.

**Differences.** Plimsoll collects the directory twice, once as declared and once with the dumps applied, and
compares the values the rules use on the edges from each dumped service: per layer its tries, timeouts, backoff,
retry conditions and breaker, and edges that only one side has. Comparing the collected values rather than the
property keys means that a default needs no knowledge of its own: a setting the repository leaves unset shows its
declared value as `LIBRARY_DEFAULT` or `UNKNOWN`. A setting that no edge uses gives no difference. When values
differ, a new rule reports it:

- **PLM-C2 Declared configuration differs from what runs**: `WARN`, one finding per service and setting, location
  the service, detail `<setting>: declared <value>, runs <value>`. The finding lists both values with their
  evidence. A setting that the repository does not declare, but whose effective value differs from the default the
  analysis would have used, is a difference too. Equal values give no finding.

A dump may also hold settings no collector reads, such as `spring.ai.*` before #19 models Spring AI retry. They are
compared the same way and reported by PLM-C2, but they do not enter the graph.

**Coverage.** The Coverage section lists the dumps read per service.

**configprops in practice** (#25, part 2, `ConfigPropsOracleTest` on Spring Boot 4.1). The dump has
`contexts.<id>.beans.<bean>` with the bean's `prefix`, its bound `properties` (camelCase names, ISO-8601 durations,
defaults included) and `inputs`, which give a value's `origin` when a property source set it. Spring Boot masks the
values (`******`) unless `management.endpoint.configprops.show-values` is `always` (or `when-authorized`); a masked
value is noted and keeps its declared value. A bound `Set` comes out in hash order, so the order of such a list can
change from one dump to the next; the analysis of one dump file stays deterministic. Beans under `resilience4j`,
`spring.cloud.gateway`, `spring.cloud.loadbalancer` and `spring.cloud.circuitbreaker` replace the declared
properties under their prefix; `spring.ai.*` values with an origin are compared key by key. A dump with several
contexts is read in the context named like the service.

**Envoy routes in practice** (#25, part 3). The input is the Envoy admin `config_dump`
(`pilot-agent request GET config_dump` in the `istio-proxy` container, whole or with
`?resource=dynamic_route_configs`), not `istioctl proxy-config routes -o json`: istioctl writes that output with Go's
`encoding/json` from the generated structs, whose shape (oneof wrappers, durations as objects) is not a documented
format, while the admin endpoint writes protobuf JSON. Field names are read in snake_case, as the admin endpoint
writes them, or lowerCamelCase. Each route whose cluster is `outbound|<port>|<subset>|<host>` gives the sidecar's
`istio` layer for that host's service; other clusters (passthrough, inbound) are noted and not read. On an edge from
the dumped service the layer replaces the declared one, or is added when no manifest put the callee in the mesh;
several routes to one callee keep the fields they agree on, as for manifests. Envoy semantics, from the
`RouteAction` and `RetryPolicy` messages of the Envoy that Istio 1.31's proxy pins (`kb/defaults/envoy.yaml`):
`num_retries` counts retries and defaults to 1; a route without a `retry_policy` (its own or its virtual host's)
tries once; `timeout: 0s` disables the route timeout and an absent one is 15 s; an absent `per_try_timeout` is the
route timeout; an absent back-off is 25 ms. Istio writes status codes from `retryOn` to `retriable_status_codes`
(`pilot/pkg/networking/core/route/retry/retry.go`), so those codes are read back as conditions of their own and
`retriable-status-codes` is left out; a route that a VirtualService and a dump describe alike then compares equal.
The test fixture is Istio's own test data, `istioctl/pkg/describe/testdata/describe/http_config.json` at
`56245da4` (Apache-2.0), unchanged: an ingress gateway's routes, whose shape is that of a sidecar's outbound routes.

**MeshConfig, statically.** The Istio MeshConfig `defaultHttpRetryPolicy` is declared configuration, not a dump.
When the `istio` ConfigMap, an `IstioOperator` or Helm `meshConfig` values are among the manifests, the Istio
collector reads it as the mesh default (`attempts: 0` turns retries off) instead of assuming none. That gives no
PLM-C2 finding.

**MeshConfig in practice** (#25, part 4, from `route.go` and `retry.go` of Istio 1.31 at `56245da4`). Istio uses the
policy for an HTTP route without `retries` and for the default route of a host without a VirtualService; a route
with its own `retries` keeps them. The policy goes through the same conversion as a VirtualService's `retries`, so
its absent fields take Istio's defaults (`retryOn`, a per-try timeout equal to the route timeout, 25 ms back-off).
In that conversion a `retries` block without `attempts` has 0, as proto3 reads an absent number, and 0 turns retries
off: such a block is one try (`kb/defaults/istio.yaml` `retries.attemptsUnset`), where the collector used to leave it
`UNKNOWN`. The policy is read from the `istio` ConfigMap or `istio-<revision>` (the MeshConfig text in `data.mesh`;
with a literal block, its lines are mapped to the file's), an `IstioOperator`'s `spec.meshConfig` and Helm values
with a top-level `meshConfig`. A file that mentions `install.istio.io/` or `defaultHttpRetryPolicy` is an Istio
manifest too, so such a file alone puts the graph in the mesh. Several definitions that say the same are one; when
they differ (revisions, a values file next to a ConfigMap), which one a sidecar gets depends on its revision, so the
policy is `UNKNOWN`, with a note. Per-workload proxy configuration is not read.

## Considered alternatives

- **Use the dump only to report differences, keep the declared graph.** The rules would then judge configuration
  that does not run. Rejected: the effective value is the better input, and the finding keeps the declared one.
- **Call the actuator endpoints or istioctl from Plimsoll.** Breaks the no-network principle and needs credentials.
  Rejected.
- **Read `/actuator/env` as well.** It lists property sources with the raw values, before binding. configprops
  shows what was bound, which is what the libraries use. `env` can come later if a case needs it.
- **Infer the service from the dump.** Not reliable for either format. Rejected for now.

## Consequences

- Core: a `ConfigDifference` model and the rule PLM-C2. `Analyzer.analyze` takes the differences next to the
  detections. SARIF lists PLM-C2 among its rules (#25, part 1).
- Collectors: a configprops reader (#25, part 2). Its fixture is produced by real Spring Boot Actuator in a test, not
  written by hand. It overlays the dump on the service's Spring properties.
- Collectors: an Envoy `config_dump` reader for the sidecar's outbound routes (#25, part 3). It maps `retry_policy`
  and timeouts through Envoy's defaults and the failure classes of ADR 0014.
- Collectors: MeshConfig `defaultHttpRetryPolicy` from the manifests (#25, part 4), and a `retries` block without
  `attempts` is one try.
