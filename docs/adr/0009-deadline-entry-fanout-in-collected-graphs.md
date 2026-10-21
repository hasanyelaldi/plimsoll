# 0009 Deadline, entry and fanout in collected graphs

- Status: accepted
- Date: 2026-10-21
- Issue: #21
- Amends: 0003 (provenance-carrying values)

## Context and problem statement

PLM-A2a compares an edge's retry budget with `deadline(caller)`, PLM-A2b compares `deadline(callee)` with the
edge's per-try timeout, and PLM-A1 starts at the `entry` services and multiplies by `fanout`. A hand-written spec
states all three. Real configuration states none of them: Spring, Resilience4j and Istio have no "deadline" field,
nothing marks a service as an entry, and how many times one request calls a callee is a property of the code path,
not of configuration. The collectors need a rule for each value that keeps the rules meaningful and never guesses.

## Decision drivers

- A value that is not stated stays `UNKNOWN` (ADR 0003); a rule then names it instead of computing with a guess.
- A rule must not compare a value with itself. If `deadline(callee)` were derived from the timeouts its callers
  impose, A2b would always hold; if `deadline(caller)` were derived from the caller's own outbound timeouts or
  retry budget, A2a would.
- The user can always state what the configuration does not.

## Decision outcome

### deadline

`deadline(s)` is how long `s` keeps working on one inbound request. It comes only from `s` itself or from the user:

1. `deadline:` at the top of `s`'s `edges-hint.yaml`, `EXPLICIT` with `file:line`.
2. A server-side inbound request timeout of `s`, from a sourced knowledge-base entry. No collector reads one in
   v0.1: a Spring MVC (Tomcat) or WebFlux (Netty) server has no processing deadline by default, and a connection
   or idle timeout is not one.
3. Otherwise `UNKNOWN`, and the evidence says where to state it.

Never from the timeouts of `s`'s callers (A2b would compare a value with itself), and never from `s`'s own outbound
timeouts or retry budget, such as a TimeLimiter (A2a would). The Spring Cloud Gateway TimeLimiter in
spring-petclinic-microservices (10 s) is therefore not the gateway's deadline: it bounds the gateway's calls on a
route, so it belongs on those edges as an overall timeout.

### entry

A service is an entry if:

1. its `edges-hint.yaml` says `entry: true` (`entry: false` overrides every rule below), or
2. it is a Spring Cloud Gateway with at least one route to a collected service: a gateway exists to take outside
   requests.

Every other collected service is not an entry. `Service.entry` stays a `boolean`; the rule that made a service an
entry is written in the assembler's notes with its evidence. If no service is an entry, a note says that PLM-A1 has
nothing to start from. Istio ingress gateways (`Gateway` resources) are not read in v0.1.

### fanout

`Edge.fanout` becomes `Val<Integer>`:

- `fanout:` on an `edges-hint.yaml` method entry is `EXPLICIT`. Absent, it is `UNKNOWN`: a client method may be
  called in a loop, and the configuration does not say.
- A Spring Cloud Gateway route forwards each request it matches once: `EXPLICIT` 1, with the route's `file:line`.
- In a hand-written spec, an absent `fanout` means 1, like an absent `multiplier` means fixed backoff. The spec is
  the author's whole model of the graph, so an edge without a fanout is one call.

PLM-A1 counts an `UNKNOWN` fanout as 1 and tags the result `depends-on-unknown`, exactly as it does for an `UNKNOWN`
attempt count: the amplification it reports is then a lower bound.

### Considered alternatives

- **Fanout 1 by default in collected graphs.** Rejected: it is a guess, and an unseen loop is exactly what A1 is
  meant to find.
- **Fanout required in the hint.** Rejected: an unknown value already has a defined treatment in A1, and the report
  then shows which edge needs it.
- **`Val<Boolean>` for `entry`.** Rejected for now: A1 needs a yes or no, and an `UNKNOWN` entry would need its own
  semantics. The notes carry the rule and its evidence.
- **Deadline from the gateway TimeLimiter or from an outbound budget.** Rejected, see above.

### Consequences

- On spring-petclinic-microservices without a hint, A2a and A2b name the `UNKNOWN` deadline of each service; with
  `deadline:` in the hints they produce findings.
- Every collected edge from a hint method without `fanout:` tags A1 `depends-on-unknown` downstream of it.
- The overall timeout of the gateway TimeLimiter is not modelled yet (a `later` issue).
