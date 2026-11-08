# 0003 Provenance-carrying values and a semantic oracle per library version

- Status: accepted
- Date: 2026-10-14
- Issue: #2, #4
- Recorded: 2026-11-08 (#62), from the decision list in CLAUDE.md
- Amended by: 0008, 0009, 0011

## Context and problem statement

A finding is only useful as evidence if a reader can check it. Two things make that hard. First, most values that
drive a finding are not written anywhere: an absent `maxAttempts` means the library's default. Second, libraries
count differently: Resilience4j's `maxAttempts` includes the first call, Istio's `retries.attempts` does not. A
silently wrong default or a misread count gives a wrong number that looks right.

## Considered options

1. Plain values; defaults and semantics taken from documentation as the code is written.
2. Every value carries its source and evidence; library defaults come from a sourced knowledge base; library
   semantics are checked by tests that run the real library.

## Decision outcome

Option 2.

- **Provenance.** Every value is a `Val<T>(value, source, evidence)` with source:
  - `EXPLICIT`: written in configuration or code, evidence `file:line`;
  - `LIBRARY_DEFAULT`: the library's default, evidence library, version and documentation URL;
  - `INFERRED`: a proposal (ADR 0002), never used silently;
  - `UNKNOWN`: not known.
- **No invented defaults.** Defaults live in `kb/defaults/<library>.yaml`, one entry per parameter with a source
  URL and `verified_by` (`doc`, `source`, `oracle` or `none`). Without a sourced entry the value is `UNKNOWN`.
- **Unknowns stay visible.** A rule computes with what is known, tags the finding `depends-on-unknown` and names the
  unknown value. It never substitutes a guess.
- **Semantic oracles.** Before encoding how a library behaves (attempt counting, decorator order, timeouts), an
  oracle test runs the real library in test scope and asserts that behaviour. Attempt counts are normalized to
  `totalAttempts` (always including the first call) at the boundary. The library version is a property
  (`resilience4j.version`) so the oracles can run against other versions.

Option 1 is how the mismatch between attempt counts goes unnoticed, and it gives a reader no way to check a number.

### Consequences

- Each finding lists the values it used and where each came from.
- Supporting a library means a collector, KB entries and an oracle test, not only a parser.
- The four sources cover values that are not from a library: an RFC value (0008), a hint file (0009) and an absent
  timeout (0011) each have an ADR that places them.
