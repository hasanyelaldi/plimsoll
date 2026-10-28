# 0011 Unlimited timeouts

- Status: accepted
- Date: 2026-10-28
- Issue: #38
- Amends: 0003 (provenance-carrying values)

## Context and problem statement

Many clients have no timeout unless one is configured. The Spring Cloud Gateway HTTP client has no response timeout
by default, a `WebClient.builder()` with Reactor Netty has none, an Istio route has none, and the gateway `Retry`
filter puts no limit on the whole retry sequence. Each of these facts is sourced, and the WebClient and gateway ones
are checked by oracles. Until now the collectors recorded them as `UNKNOWN`, with the fact in the evidence. That
breaks the meaning of `UNKNOWN` ("not stated anywhere"). It also makes PLM-A2a and PLM-A2b answer "cannot evaluate"
when the answer is known: without a per-try timeout, one try that hangs can use the caller's whole deadline.

## Decision drivers

- `UNKNOWN` means the value is not known (ADR 0003). A value that is known not to exist is known.
- The model stays `Val<Duration>`: no new type through the model, rules and report.
- Same input, same output: the representation must compare and print deterministically.

## Decision outcome

An absent timeout is the known value `Durations.UNLIMITED`, the largest `java.time.Duration`, with the source
of the fact that it is absent:

- `LIBRARY_DEFAULT` when a sourced knowledge-base entry says the default is none, e.g. the gateway's
  `httpclient.response-timeout`.
- `EXPLICIT` when configuration switches the timeout off, e.g. a negative per-route `response-timeout` on a
  gateway route.
- `UNKNOWN` as before when the knowledge base has no sourced entry.

It prints as `unlimited`. In a hand-written spec, `perTryTimeout` and `overallTimeout` accept `unlimited`. A
`deadline`, `backoff` or `maxBackoff` does not, because none of the collected sources can make them unlimited.

Rules:

- **PLM-A2a.** An unlimited per-try timeout makes every `W_k` unlimited, so `W_n > deadline(caller)` for every
  known deadline and 0 of n tries fit: `WARN`, "budget unlimited > deadline 3 s, 0 of 3 tries fit (no per-try
  timeout)". Without retries there is no finding, as before. With an `UNKNOWN` caller deadline the rule still
  cannot evaluate.
- **PLM-A2b.** `deadline(callee) > unlimited` never holds, so an unlimited per-try timeout gives no finding. With
  several layers the shortest known per-try timeout still decides, and `UNLIMITED` compares above every finite one.
- **PLM-A1, A3, A4, A5.** Do not read timeouts and are unchanged.

### Considered alternatives

- **Keep `UNKNOWN` with the fact in the evidence.** Rejected: A2a/A2b then hide a known risk behind "cannot
  evaluate", and `UNKNOWN` stops meaning one thing.
- **`Val<Optional<Duration>>` or a dedicated `Timeout` type.** Rejected for v0.1: it changes every layer field and
  every rule for one special value that a sentinel expresses exactly.
- **A2a INFO instead of WARN.** Rejected: a retrying edge without a per-try timeout is the case where retries do
  least against slow failures, and the rule's severity for "not all tries fit" is `WARN`.

### Consequences

- The gateway response timeout (global and per route), the WebClient response timeout, the gateway `Retry`
  `timeout` and Istio's route timeout and its derived per-try timeout become `unlimited` with their sources.
- On a collected graph whose callers have a `deadline:` hint, A2a warns on every retrying edge without a per-try
  timeout.
- A connect timeout only bounds connection setup, not a try, so it is not a per-try timeout and is not modelled.
