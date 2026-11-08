# 0007 Positioning: extraction, verified semantics and measurement, not the graph math

- Status: accepted
- Date: 2026-10-14
- Recorded: 2026-11-08 (#62), from the decision list in CLAUDE.md

## Context and problem statement

Retry amplification along a call graph is known and its math is simple: attempts multiply along a path and add up
across paths. Other work already computes it. What is Plimsoll's contribution, and so where should effort go?

Related work:

- [retryamp](https://github.com/rashedhasan090/retryamp): the same graph math over a hand-written spec.
- Mehan and Saluja, [arXiv 2608.25403](https://arxiv.org/html/2608.25403): a retry amplification factor, from a
  regex-based study of Python code.
- [RPCShield](https://github.com/uber-research/rpcshield) (Uber, SOSP '26): fail-open / fail-close static analysis
  for Go.
- [WASABI](https://www.microsoft.com/en-us/research/?p=1068147) (SOSP '24): LLM-assisted detection of retry bugs
  inside code.
- Mendonça, Aderaldo, Cámara and Garlan (ICSA 2020): a PRISM model of Retry and Circuit Breaker, followed by
  ResilienceBench.

## Decision outcome

The novelty is not the graph math. It is:

1. **Extraction from real configuration**: building the graph and its policies from code, Spring YAML and mesh
   manifests as they are, instead of from a model someone writes by hand.
2. **Verified semantics**: each library's behaviour (attempt counting, defaults, filter order, timeouts) checked by
   oracle tests against the real library (ADR 0003).
3. **Measurement**: runs on public testbeds (ADR 0005), and later measured amplification under fault injection.

### Consequences

- Effort goes into collectors, the knowledge base, oracles and testbed runs. The rules stay simple and are
  documented as formulas.
- Related work is cited, not duplicated. retryamp is a possible export target.
