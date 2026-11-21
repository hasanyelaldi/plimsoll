# 0015 Circuit breakers in the model

- Status: accepted
- Date: 2026-11-20
- Issue: #22

## Context and problem statement

Plimsoll finds where retry, timeout and circuit-breaker settings go past safe limits, but its model has no breaker.
The collectors find breakers and only note them: a Resilience4j `@CircuitBreaker`, a Spring Cloud Gateway
`CircuitBreaker` filter. #18 will find the programmatic Spring Cloud CircuitBreaker configuration as well.

A breaker changes how many calls reach the callee, and its position relative to the retry decides by how much. The
oracles show it (`DecoratorOrderOracleTest`, `SpringAnnotationOrderOracleTest`). With the retry outside the breaker,
every try passes through the breaker, and an open breaker rejects the remaining tries: a 5-try retry makes 2 real
calls once a breaker with a window of 2 opens. With the breaker outside the retry, one breaker call wraps all 5
tries. `GatewayRetryOracleTest` shows the same for gateway filters, which run in the order they are listed.

## Decision drivers

- Every value carries provenance (ADR 0003). A breaker found without its configuration is still in the model, with
  `UNKNOWN` values that name what is missing.
- A1 stays a fail-close upper bound. Using a breaker to lower it needs a model of time (calls per window, how long
  the breaker stays open), which a static graph does not have.
- Same input, same report. Graphs without breakers report exactly as before; `checkout.yaml` has none.

## Decision outcome

A `Layer` may carry one `CircuitBreaker`, or none:

| Field | Meaning |
| --- | --- |
| `name` | the instance name, e.g. a Resilience4j instance |
| `position` | `wraps-all-tries`: the breaker is outside the layer's retry and counts one call for all its tries; `each-try`: the retry is outside the breaker, which counts and can reject each try |
| `failureRateThreshold` | percentage of failed calls in the window that opens the breaker |
| `slidingWindowType` | `count-based` (the last n calls) or `time-based` (the calls of the last n seconds) |
| `slidingWindowSize` | n, in calls or seconds as the type says |
| `minimumNumberOfCalls` | calls in the window before the failure rate is computed |
| `waitDurationInOpenState` | how long the breaker stays open before it lets trial calls through |

The fields are those of Resilience4j, which is also what the Spring Cloud Gateway and Spring Cloud CircuitBreaker
breakers use. Each is a `Val`: `EXPLICIT` from configuration, `LIBRARY_DEFAULT` from a sourced knowledge-base entry
checked by an oracle, else `UNKNOWN`.

The position is relative to the retry of the breaker's own layer. Across layers, the layer order already says it:
an outer layer wraps every try of the layers inside it. On a layer that tries once, both positions count the same
calls, and the position is recorded as found.

The report lists every breaker after the findings, one line per breaker with its edge, layer, name, position and
values with their sources. No rule reads breakers yet. A hand-written spec may give a layer a `circuitBreaker`;
an absent field is `UNKNOWN`.

Istio's `outlierDetection` is not a breaker in this sense. It ejects a host from the load-balancing pool after
consecutive errors, and calls go on to the other hosts. It stays a collector note.

## Considered alternatives

- **A breaker as its own `Layer` kind.** Layers are ordered, so the position would follow from the list. But a
  layer's kind names the library it comes from, and the rules treat each layer as a source of tries. A breaker
  layer would try once and need special cases in A1, A2 and A5. Rejected.
- **Lower A1 with the breaker now.** How many calls pass before the breaker opens depends on the request rate and
  the window, which the graph does not hold. Rejected for now; a later issue may add a breaker-limited figure next
  to the bound, as #24 did for failure classes.
- **Only a flag "has a breaker".** Too little to explain a later figure, and it drops values the collectors can
  read with provenance. Rejected.

## Consequences

- `Layer` gets a `breaker` component (null when there is none); the existing constructors stay.
- `AnalysisResult` carries the breaker list, so that the text and Markdown reports can print it. SARIF output does
  not change, because a breaker is not a finding.
- The Resilience4j collector (#22, next) turns `@CircuitBreaker` and its instance configuration into a breaker, with
  defaults in `kb/defaults/resilience4j-circuitbreaker.yaml` checked by an oracle and the position from the aspect
  orders. The gateway collector turns `CircuitBreaker` filters into breakers, the position from the filter order;
  their values come from Spring Cloud CircuitBreaker configuration, which stays `UNKNOWN` until #18.
- The Resilience4j oracle (#22, `CircuitBreakerConfigOracleTest`) shows how Spring Boot resolves a configured
  instance: its `baseConfig`, else a config of its own name, else `configs.default`, else the library defaults.
  An instance that is not configured gets `configs.default` or the library defaults, even when a config has its
  name, and a config without `baseConfig` does not start from `configs.default`. A `waitDurationInOpenState`
  below 1 ms stops the application. With exponential backoff or a randomized wait the wait in the open state
  changes between openings, so the model records it as `UNKNOWN`.
- The gateway collector (#22) puts a route's first `CircuitBreaker` filter on its outermost gateway layer; the
  position comes from the filter order (no `Retry` filter: `UNKNOWN`). A further breaker on the same route, such as
  petclinic's `genaiCircuitBreaker` inside `defaultCircuitBreaker`, is a note, because a layer carries one breaker.
  The gateway oracle shows that the shortcut form `CircuitBreaker=cb` names the breaker `cb`.
