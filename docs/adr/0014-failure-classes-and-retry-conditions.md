# 0014 Failure classes and retry conditions

- Status: accepted
- Date: 2026-11-15
- Issue: #24
- Amends: PLM-A1 and PLM-A3 in CLAUDE.md

## Context and problem statement

A1 and A3 treat every layer as retrying every failure. Real layers have different triggers, and the collectors
already record them in `RetryPolicy.retryOn`, in each library's own words:

- Istio: Envoy policies, e.g. the default `connect-failure,refused-stream,unavailable,cancelled`.
- Spring Cloud Gateway `Retry`: `series SERVER_ERROR`, `status ...`, exception classes, `method GET`.
- Spring Cloud LoadBalancer: exception classes; status codes for GET only.
- Resilience4j: `retryExceptions`, or every exception when none is listed.

No rule reads them. Two layers multiply only for a failure that triggers both. A non-idempotent call that is
retried only when the request never reached the callee causes no duplicate side effect. On TrainTicket, A3 reports
34 `ERROR`s for gateway POST routes that only the implicit Istio default retries (#28). Of its four policies, two
fire before the request is sent, and two read a gRPC status that a REST call never returns.

## Decision drivers

- A1 stays an upper bound, never below the real worst case.
- Same input, same report. No change for graphs without known retry conditions; `checkout.yaml` has none.
- Every mapping from a library condition to a failure class is sourced and, where the library can run in a test,
  checked by an oracle (ADR 0003). An unmapped condition is never guessed.

## Decision outcome

### Failure classes

A failure, as one layer sees it, is in exactly one class:

| Class | Meaning | Could the callee have processed the request? |
| --- | --- | --- |
| `not-sent` | connection failure, connect timeout, stream refused by the callee | no |
| `reset` | connection closed or reset after the request was sent, no response | yes |
| `timeout` | no response within the layer's per-try timeout | yes |
| `status-500`, `status-502`, `status-503`, `status-504`, `status-5xx-other` | a response with that status | yes |
| `status-409`, `status-429`, `status-4xx-other` | a response with that status | yes |

A 503 does not prove that the callee did nothing, so only `not-sent` counts as unprocessed. Plimsoll's edges are
HTTP calls. Conditions on a gRPC status (Envoy's `cancelled`, `unavailable`, `deadline-exceeded`,
`resource-exhausted`, `internal`) read the `grpc-status` header, so they match no class of an HTTP edge.

### Retry conditions per library

A knowledge-base file maps each library condition to the classes it retries, with a source per entry:

- **Istio / Envoy** (`router_filter` docs, `retry_state_impl.cc` at Envoy v1.40.2):
  - `connect-failure`: `not-sent`. `refused-stream`: `not-sent`.
  - `reset`: `reset`, `timeout`. `reset-before-request`: `not-sent`.
  - `5xx`: every 5xx status, `not-sent`, `reset`, `timeout`.
  - `gateway-error`: `status-502`, `status-503`, `status-504`, `not-sent`, `reset`, `timeout`.
  - `retriable-4xx`: `status-409`. A status code listed in `retryOn`: that status.
  - gRPC policies: no class.

  A per-try timeout resets the stream locally and is retried under `5xx`, `gateway-error` or `reset`, not under
  `connect-failure` or `refused-stream`. The Envoy version Istio 1.31 ships is pinned in the KB entries.
- **Spring Cloud Gateway** `Retry`: `series SERVER_ERROR` covers every 5xx status; `status X` covers that status;
  exception classes map to classes. The KB says which: `IOException` covers `not-sent` and `reset`, and the
  gateway's `TimeoutException` covers `timeout`. Each class an oracle shows the real gateway retrying. `method`
  entries are already applied by the collector, which gives a layer one try when the edge's method is not listed.
- **Spring Cloud LoadBalancer**: its default exceptions mapped the same way, oracle-checked. Status codes for GET.
- **Resilience4j**: no `retryExceptions` means every exception, so every class. Listed exception classes map
  through the same KB entries. A class the KB does not map leaves the layer's conditions `UNKNOWN`. Which
  exception an HTTP client throws for a status is client-specific.
- **`generic`** (hand-written): `retryOn` lists Plimsoll's class names directly.

A layer whose `retryOn` is `UNKNOWN`, or contains a condition the KB does not map, retries every class.

### Through the layers of an edge

Layers are outermost first. A failure starts at the innermost layer. When a layer gives up, the layer outside it
may see a different class:

- **Istio** answers its caller `503` after a connection failure or reset, and `504` after a per-try timeout
  (`router.cc`). A status passes through.
- Layers in the caller's process (`resilience4j`, `http-client`) pass the failure on as it is.
- **Spring Cloud Gateway**: what it answers after giving up is checked by an oracle before it is used. Until then,
  a layer outside a gateway layer retries every class.

For a failure of class `c` at the callee, layer `i` sees `t_i(c)`, the class after the translations of the layers
inside it. The tries on edge `e` are:

```
E_c(e) = Π over layers i of (totalAttempts_i if layer i retries t_i(c), else 1)
```

`E(e)`, the product of every layer's `totalAttempts`, is the largest `E_c(e)`.

### Across services

What a service answers its caller when its own callee failed is application code. Plimsoll does not know its class,
so above the failing edge every class is possible.

### A1

The bound `L(v)` and its severity do not change. Per class, the load on `v` when `v` fails with class `c`:

```
L_c(v) = Σ over edges e = u→v of L(u) × E_c(e) × fanout(e)
```

This uses the alternatives of ADR 0012 the same way. `L(u)` is the unchanged bound, because the class above the
failing edge is unknown. The finding gives the per-class figures that are below `L(v)`, but only when every layer
on the edges into `v` has known conditions. Otherwise there are none. The bound is never tagged for an unknown
`retryOn`, since it does not depend on one.

### A3

A non-idempotent edge is a risk when a failure the callee may have processed is retried. Its tries are then
`max over c ≠ not-sent of E_c(e)`:

- If that is above 1: `ERROR`, as today, naming a class that causes it.
- If only `not-sent` is retried: `INFO`, "retried only when the request was not sent, n tries".
- An `UNKNOWN` condition counts as retrying every class, so the result is `ERROR` and is not tagged. That is
  today's finding.

## Considered alternatives

- **Tighten A1's severity with `max over c of L_c(v)`.** Sound, because the class above the failing edge is
  treated as unknown. It would still change many findings at once. First show the figures, then decide on
  severity with evidence from the testbeds (later issue).
- **Count 503 as unprocessed.** Envoy and Spring both answer 503 for local reasons (no healthy host, overload).
  An application may also answer 503 after doing part of the work. Rejected; a later rule could report it as INFO.
- **Assume Spring Boot's 500 for an unhandled exception across services.** That is a library default and could be
  checked by an oracle. But a service often catches the exception or maps it to another status, so the class
  would be guessed. Rejected for now.
- **One flag per layer, "retries before send only".** Too coarse for gateway and Resilience4j conditions, and it
  cannot express the translations through Istio.

## Consequences

- KB: a failure-class file and per-library condition entries, with sources and `verified_by`. The gateway and
  LoadBalancer mappings, and the gateway's translation, get oracle tests.
- A3 on TrainTicket: the 34 Istio-default POST retries become `INFO`, and `findings/trainticket.md` is updated.
  Petclinic's gateway POST routes stay `ERROR`, because the default exceptions include the gateway's
  `TimeoutException`.
- A1 details get per-class lines for collected graphs where conditions are known. Hand-written specs without
  `retryOn` report exactly as before.
- gRPC edges are out of scope. A gRPC callee would need its own classes.
