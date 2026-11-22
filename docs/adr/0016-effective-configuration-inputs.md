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
- `istioctl proxy-config routes <pod> -o json` lists the Envoy route configuration a sidecar applies, with the retry
  policy and timeouts of each route, mesh defaults included.

## Decision drivers

- No network calls (ADR 0002 and CLAUDE.md). Plimsoll reads files that someone saved from the environment.
- Every value carries provenance (ADR 0003). A value from a dump says which dump and where in it.
- Same input, same report. A dump is a file, read like any other input.
- The report should say both that the repository is not what runs and what does run.

## Decision outcome

**Input.** `plimsoll analyze <dir> --effective <service>=<file>`, repeatable. The service is named explicitly,
because neither dump says reliably which service it belongs to: configprops names the Spring application context,
and a sidecar's route dump names the routes it calls out to, not its own service. The format is recognised from the
content: a configprops document (`contexts.*.beans`), or an Envoy route configuration dump. Anything else is invalid
input (exit code 2). `--effective` needs a directory; a hand-written spec takes none.

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

**MeshConfig, statically.** The Istio MeshConfig `defaultHttpRetryPolicy` is declared configuration, not a dump.
When the `istio` ConfigMap, an `IstioOperator` or Helm `meshConfig` values are among the manifests, the Istio
collector reads it as the mesh default (`attempts: 0` turns retries off) instead of assuming none. That gives no
PLM-C2 finding.

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
- Collectors: an Envoy route-dump reader for the sidecar's outbound routes (#25, part 3). It maps `retry_policy` and
  timeouts through the Envoy semantics of ADR 0014.
- Collectors: MeshConfig `defaultHttpRetryPolicy` from the manifests (#25, part 4).
