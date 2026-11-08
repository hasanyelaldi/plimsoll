# 0006 Apache-2.0, developed in the open, named Plimsoll

- Status: accepted
- Date: 2026-10-14
- Recorded: 2026-11-08 (#62), from the decision list in CLAUDE.md

## Context and problem statement

The tool is meant to be used in other organisations' builds and its reports attached to their decisions. That
needs a license companies accept and a process others can follow. It also needs a name.

## Considered options

1. Apache-2.0, developed in the open.
2. MIT.
3. A copyleft license (GPL, AGPL).

## Decision outcome

Option 1. Apache-2.0 is permissive like MIT and adds an explicit patent grant, which matters to companies adopting
a tool into their build. It is also the license of most of the libraries Plimsoll models. A copyleft license would
keep the tool out of many builds. Development is in the open: issues, milestones, ADRs and testbed runs are public.

The name is **Plimsoll**, after the Plimsoll line: the load line on a ship's hull that marks how deep it may safely
be loaded. The tool marks the same thing for a service graph's retry and timeout settings.

### Consequences

- Only code the author has the right to release under Apache-2.0 is contributed.
- Maven coordinates are `io.github.hasanyelaldi:plimsoll-*`, base package `io.github.hasanyelaldi.plimsoll`.
