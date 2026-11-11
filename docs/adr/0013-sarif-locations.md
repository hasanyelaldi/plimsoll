# 0013 Locations of findings in SARIF

- Status: accepted
- Date: 2026-11-11
- Issue: #75

## Context and problem statement

`--format=SARIF` (#75) writes a SARIF 2.1.0 report so that GitHub code scanning and other viewers show findings on
the configuration they came from. A SARIF result points at a file and a line. A Plimsoll finding does not: its
location is a service (`ledger`), an edge (`orders→payments`) or a path. The files and lines are in the evidence of
the values the finding used, as free text (`EXPLICIT: checkout.yaml:10`, or `Retry (default-filters[1],
application.yml:15): retries 1 + 1; application.yml:17`). Some findings use no value with a file and line, e.g.
`cannot evaluate: api-gateway deadline UNKNOWN`. Code scanning drops a result that has no location.

## Decision drivers

- Every location in the report comes from the input; nothing is guessed (principle 2).
- Same input, byte-identical report (principle 5).
- A finding is shown where a reader would change the configuration.

## Decision outcome

- **Primary location:** the first `file:line` in the evidence of the finding's values, taking the `EXPLICIT`
  values first and the others after them, each in the order the finding lists them.
- **Related locations:** every other distinct `file:line` in that evidence, in the same order. The values used,
  with their provenance, are also in the message and in the result's `properties`, as in the other formats.
- **No `file:line` in any value:**
  - a hand-written spec gives line 1 of the spec file;
  - a collected directory gives the first file read for the first service named in the finding's location (its
    `pom.xml` or build file, from the Coverage section);
  - if neither exists the result has no location. That is valid SARIF; code scanning then does not show it.
- **Paths:** relative to the base `SRCROOT`, which is the analysed directory or the directory of the spec. The report
  holds no absolute path, so it is the same on every machine; the uploader maps `SRCROOT` to the repository.
- **`file:line`** is a relative path ending in a file extension, then `:` and a line number. A URL never matches,
  because the path may not follow `/` or `:`.

### Consequences

- An A1 finding on a service is shown at the first retry on a path to it, not at the service. The message names the
  service.
- A finding located by the fallback points at the start of a file and says nothing about that line; its message
  names the unknown value it depends on.
- Collectors write evidence as `file:line` today. A collector that writes it differently loses its SARIF locations,
  and the test on a collected directory catches that.
