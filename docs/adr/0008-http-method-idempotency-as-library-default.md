# 0008 HTTP method idempotency is a LIBRARY_DEFAULT

- Status: accepted
- Date: 2026-10-20
- Issue: #20
- Amends: 0003 (provenance-carrying values)

## Context and problem statement

PLM-A3 needs `Edge.idempotent`. Real configuration never states it, but the HTTP method of an operation is visible
in code (`WebClient.post()`, `@PostMapping`) and the standard says which methods are idempotent: RFC 9110 §9.2.2
(GET, HEAD, OPTIONS, TRACE, PUT, DELETE are; POST is not) and RFC 5789 §2 (PATCH is not).

ADR 0003 defines four sources: `EXPLICIT`, `LIBRARY_DEFAULT`, `INFERRED`, `UNKNOWN`. A value taken from an RFC is
none of them by name. Which source does it carry?

## Considered options

1. `LIBRARY_DEFAULT`, with the RFC section as evidence, stored in `kb/defaults/http.yaml`.
2. A new source, `STANDARD`.
3. `INFERRED`.

## Decision outcome

Option 1. `LIBRARY_DEFAULT` already means "not written in this project's configuration, taken from a documented
reference, overridden by explicit configuration", which is how the RFC value behaves: a POST protected by an
idempotency key is marked `idempotent: true` in `edges-hint.yaml` and that `EXPLICIT` value wins. The knowledge
base already carries the source URL and `verified_by`, so the report still shows that the value comes from an RFC
and not from a library.

Option 2 would change the core model, every report and the spec loader for a distinction no rule uses. Option 3 is
wrong: `INFERRED` is reserved for LLM proposals, which are never used silently, while the RFC value is used.

### Consequences

- A server may violate the standard (a GET with side effects). The value is the standard's, not the server's; the
  evidence says so, and an explicit override is the way to correct it.
- The method itself is `EXPLICIT` when read from code or configuration, with `file:line`. Only the step from method
  to idempotency is `LIBRARY_DEFAULT`.
- An operation whose method is not visible stays `UNKNOWN`; no method is assumed.
