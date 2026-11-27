# 0019 External callees: services outside the analysed system

- Status: accepted
- Date: 2026-11-27
- Issue: #19

## Context and problem statement

Every node of a `ServiceGraph` so far is a service of the analysed system: a module of the directory or a service a
hand-written spec defines. Some calls leave the system: petclinic's genai-service calls an LLM provider through
Spring AI, which retries the call itself (`spring.ai.retry.*`). That call is where amplification costs most (each
try is billed, and the provider may be rate-limiting), yet it has no node to land on, so A1 cannot report it and
the Spring AI retry stays a PLM-C1 warning.

An external callee differs from a system service in what Plimsoll can know about it: it has no configuration in
the directory, so it has no deadline of its own, no outgoing edges and no entry role.

## Decision drivers

- A1 must reach the provider: amplification there is the reason #19 exists.
- Principle 2: no invented values. A provider's deadline (how long it keeps working after the caller gave up) is
  not known; it must not be guessed.
- Specs and collected graphs without external callees must give byte-identical reports.

## Considered options

1. A `Service` flag `external`.
2. A separate node type next to `Service`.
3. Leave external calls out and report the Spring AI retry as a note.

Option 3 hides the costliest amplification. Option 2 would make every rule and report handle two node types for
what is, to the graph math, one more node. Option 1 is chosen.

## Decision outcome

**Model.** `Service` gains `boolean external` (`Service(name, deadline, entry, external)`; the three-argument
constructor keeps `external = false`). An external service is never an entry and has no outgoing edges; a spec
that says otherwise is invalid input (exit 2). Its deadline is `UNKNOWN` unless a spec gives one.

**Spec.** A service may say `external: true`.

**Rules.**

| Rule | External callee |
| --- | --- |
| A1 | Counted like any service, against the same limit; the detail says `external callee`. |
| A2a | Unchanged: it reads the caller's deadline. |
| A2b | With its deadline `UNKNOWN`, no finding (the provider's own timeout is not knowable from the system's configuration); with a deadline in the spec, as for any callee. |
| A3, A4, A5 | Unchanged. |

**Collected graphs.** A collector that models a client library calling outside the system (Spring AI, #19) adds
the external service, named after the provider (`openai`), and the edge to it with that library's layer. That is
the collector's change, not this ADR's.

### Consequences

- The A1 limit applies at the provider. A system that retries its LLM calls ten times exceeds the default limit of
  10 at the provider, which is the point.
- A2b says nothing about external callees by default. If a spec author knows the provider's behaviour, a deadline
  in the spec brings the finding back.
- Not modelled: calls back from an external service (webhooks), and an external callee's own retries.
