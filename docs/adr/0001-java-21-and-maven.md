# 0001 Java 21 and Maven

- Status: accepted
- Date: 2026-10-14
- Issue: #1
- Recorded: 2026-11-08 (#62), from the decision list in CLAUDE.md

## Context and problem statement

Plimsoll reads the configuration of JVM services: Spring Boot properties, Resilience4j annotations, Spring Cloud
Gateway routes, HTTP client calls in Java code. Its oracle tests (ADR 0003) run those libraries for real. It is meant
to be published and its adoption measured. Which language and build tool?

## Considered options

1. Java 21 with Maven.
2. Java 21 with Gradle.
3. Python, or another non-JVM language.

## Decision outcome

Option 1.

- **Java:** the libraries whose semantics Plimsoll encodes (Resilience4j, Spring Cloud Gateway, Spring Cloud
  LoadBalancer) are Java libraries. Oracle tests can only run them in-process on the JVM, and JavaParser reads the
  code they configure. Java 21 is the current LTS that these libraries support.
- **Maven over Gradle:** releases go to Maven Central through Sonatype's Central Portal. Sonatype ships an official
  Maven plugin for it and no official Gradle plugin. Maven Central download numbers are also adoption evidence for
  the project.
- **Python** is used only for analysis notebooks in empirical studies, never in the tool.

### Consequences

- A multi-module build (`plimsoll-core`, `plimsoll-collectors`, `plimsoll-cli`) with the Maven Wrapper. Every
  version is a `<property>` in the parent POM and managed there; child POMs never repeat a version.
- The CLI ships as one shaded executable jar (`plimsoll.jar`).
- CI runs `./mvnw -B verify` on Java 21.
