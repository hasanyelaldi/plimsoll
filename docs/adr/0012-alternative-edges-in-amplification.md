# 0012 Alternative edges in amplification

- Status: accepted
- Date: 2026-10-28
- Issue: #48
- Amends: the PLM-A1 formula in CLAUDE.md

## Context and problem statement

PLM-A1 computes `L(v) = Σ over edges u→v of L(u) × E(u→v) × fanout(u→v)`. The sum assumes that one request to
`u` makes every call on `u`'s outgoing edges. That holds for a hand-written spec, where a service's edges are the
calls of one request. It does not hold for a Spring Cloud Gateway: a request is routed by the first route whose
predicates match, so the routes of a gateway are alternatives. On TrainTicket two routes go to `ts-auth-service`,
each with Istio ×3, and A1 reported ×6 where one gateway request can cause at most ×3 (#28).

## Decision drivers

- A1 stays an upper bound: never below the real worst case.
- Same input, same result; no change for graphs without alternatives (the acceptance test).
- Only mark alternatives that a library guarantees, with evidence; everything else keeps the sum.

## Decision outcome

`Edge` gets an optional `choice`, a `Val<String>` naming the routing decision the edge is one alternative of,
e.g. `routes of api-gateway`, with its evidence. Edges from one service with the same choice are mutually
exclusive; edges without a choice are made together, as before. In a hand-written spec an edge may say
`choice: <name>`.

Per target `v`, the calls that one request to `u` causes on `v`:

```
R_v(v) = 1
R_v(u) = Σ over edges e of u without a choice   E(e) × fanout(e) × R_v(e.to)
       + Σ over choices c of u   max over edges e of c   E(e) × fanout(e) × R_v(e.to)
L(v)   = max over entries s of R_v(s)
```

Without choices this is the sum over every path, the formula before (checked by a property test). With choices
every service gets the alternative that is worst for it. The alternatives worst for two services may differ, so
the loads are per-service upper bounds and not one consistent scenario, as the per-entry maximum already is.

- The finding lists the loads along the chosen alternatives and says `max over N alternatives of <choice>`.
- An `UNKNOWN` attempt count or fanout on any alternative that reaches `v` makes the result a lower bound
  (`depends-on-unknown`), whether that alternative was chosen or not, since it might be the larger one.
- On a tie the first edge in input order is listed; the value is the same.
- Cycles (`UNBOUNDED`) and the other rules do not change.

Sources of choices in v0.1: the `lb://` routes of one Spring Cloud Gateway form one choice, `routes of <gateway>`,
with the routes' `file:line`. That a request takes one route is checked against the real gateway by an oracle.
Edges from an edges hint or found in code (#37) have no choice: which inbound request triggers them is not known.

Findings on an edge are located at `from→to`. When the graph has several edges with the same `from→to`, their
location adds the operation, `from→to (operation)`, so that the findings can be told apart.

### Considered alternatives

- **An operation-level graph** (a node per service and inbound operation, edges triggered by an operation). The
  most precise model, and the one the performance what-if will need, but much larger than v0.1, and the trigger of
  a hint or code edge is not known anyway.
- **Merge the routes to one callee into one edge.** Rejected: layers and idempotency differ per route, so A3 and
  A4 would lose precision.
- **A choice as a set of edges** (an alternative made of several edges, such as a gateway controller endpoint that
  calls two services). Not needed by any v0.1 source; a later extension of the same field.

### Consequences

- TrainTicket: `ts-auth-service` goes from ×6 to ×3, and its two A3 findings get distinct locations.
- petclinic: the gateway's own code edges (`CustomersServiceClient`, `VisitsServiceClient`, called from
  `ApiGatewayController`) are added to the max over its routes, so customers-service and visits-service stay at
  "at least ×3". Telling that a controller request is an alternative to the routes needs call-graph analysis.
