# 0005 Testbeds: spring-petclinic-microservices and TrainTicket

- Status: accepted
- Date: 2026-10-14
- Recorded: 2026-11-08 (#62), from the decision list in CLAUDE.md

## Context and problem statement

Plimsoll's claim is that it works on real configuration, not on models written for it (ADR 0007). Unit fixtures
cannot show that. It needs public microservice systems to run on, with results that others can reproduce.

## Considered options

1. Fixtures only.
2. Public open-source microservice systems, pinned to a commit.
3. Private or employer systems.

## Decision outcome

Option 2, with two testbeds:

- **spring-petclinic-microservices** (primary): a current Spring Boot / Spring Cloud system with a gateway,
  Resilience4j and load-balanced clients. It exercises the collectors on code as it is written today.
- **TrainTicket** (secondary): a larger system with Istio manifests, widely used in microservice research. It
  exercises Istio defaults and scale.

Option 3 is excluded: Plimsoll is developed in the open (ADR 0006), and results on private systems cannot be
published or reproduced.

### Consequences

- Each run is written up in `findings/` with the pinned commit, the command, the generated report, a hand check of
  every finding and the gaps it found (`petclinic.md` for #9, `trainticket.md` for #28). No file of the testbed is
  changed.
- Gaps found by a run become issues; they are re-run when a change affects them.
