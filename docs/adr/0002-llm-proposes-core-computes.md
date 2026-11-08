# 0002 The LLM proposes, the deterministic core computes

- Status: accepted
- Date: 2026-10-14
- Recorded: 2026-11-08 (#62), from the decision list in CLAUDE.md

## Context and problem statement

Some facts Plimsoll needs are not written in configuration: where a client method sends its request, whether an
operation is idempotent, how many calls one request makes. A language model could guess them from code. But a
report is attached to an architecture decision as evidence, and a number in it (×27 amplification, "2 of 3 tries
fit") must be reproducible and explainable. Where may an LLM take part?

## Considered options

1. No LLM at all.
2. An LLM computes or fills in values directly.
3. An LLM only proposes values; deterministic code computes every number, and a proposal is visible as such.

## Decision outcome

Option 3, with a delay.

- Every number in a report comes from code in `plimsoll-core`. There are no LLM calls in the core, the analyzers or
  the tests.
- An LLM proposal enters the model only as a value with source `INFERRED` (ADR 0003). It is never used silently:
  the report says which value was inferred.
- No LLM layer before the fourth month of the project, and only together with a measured gold set that shows how
  often its proposals are right.

Option 2 makes reports non-deterministic and unverifiable. Option 1 gives up a useful source of hints for the facts
configuration never states; option 3 keeps that door open without weakening the evidence.

### Consequences

- v0.1 has no LLM layer. Facts the code does not give come from `edges-hint.yaml` (`EXPLICIT`) or stay `UNKNOWN`.
- Same input, byte-identical report. No network in tests.
