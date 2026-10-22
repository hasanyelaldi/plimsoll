# 0010 Service graphs spread across several repositories

- Status: accepted
- Date: 2026-10-22
- Issue: #13

## Context and problem statement

Plimsoll's findings only appear when the whole graph is analysed at once: a retry policy that looks harmless on one
service multiplies along the call chain. spring-petclinic-microservices is a monorepo, but many systems keep each
service, or a few, in its own repository, sometimes with the Istio manifests in yet another one. There was no defined
way to analyse such a system.

## Decision drivers

- One graph, one report: A1 and A5 need every edge of a path.
- Callees must be matched to services the same way whatever the repository layout.
- Evidence must stay unambiguous when two repositories have files at the same relative path.
- Same input, byte-identical report (principle 5), independent of the order in which repositories are found.
- No new input format before a user needs one.

## Considered options

1. **Single checkout directory.** Check out all repositories side by side and run `plimsoll analyze` on the parent.
2. **Partial specs plus merge.** Each repository emits a fragment of the graph in CI; a merge step builds one graph.
3. **Edges from OpenTelemetry traces.** Who calls whom from traces of the running system, layers from config.

## Decision outcome

Option 1. It needs no new feature: `ProjectCollector` already walks the whole directory, so every repository under
the parent is read like a module directory of a monorepo.

- **Callee matching** is unchanged: the service name is `spring.application.name` (or the hint's `service:`), a
  gateway route matches `lb://<service>`, an HTTP client hint names its callee, and an Istio host matches on its
  first label. A repository boundary plays no part.
- **Evidence** is relative to the analysed directory, so it starts with the repository's directory, e.g.
  `customers-service-repo/customers-service/edges-hint.yaml:2`. No separate repository field is needed.
- **Order**: modules and manifests are sorted by path, so the report does not depend on file-system order.
- **Conflicts**: a service defined by modules in two repositories is invalid input (exit 2), as within one repository.
- `.git` and other hidden directories are skipped like any hidden directory.

`ProjectCollectorTest.checkoutsOfSeveralRepositoriesSideBySideGiveOneGraph` checks this on the petclinic fixture split
into one repository per service plus a mesh repository. The same split of the real petclinic checkout (#9) gives the
monorepo report with only the evidence paths prefixed.

### Rejected for now

- **Option 2.** Needs a fragment format, conflict rules and order-independent merging, for a need nobody has stated
  yet. Parked as #40.
- **Option 3.** OpenTelemetry ingestion is v0.2 scope; it would add edges that configuration does not show (see #37),
  not replace collection.

### Consequences

- The user needs read access to every repository on one machine. A CI job that analyses the system clones them first.
- Repository directory names show up in evidence, so the report reads best when checkouts are named after their
  repositories.
- Versions of the repositories are whatever is checked out; the report does not record commits. Pin them in the
  checkout script when a report is attached to an ADR.
