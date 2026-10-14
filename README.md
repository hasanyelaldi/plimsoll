# Plimsoll

> Every ship has a Plimsoll line. Your service graph should too.

Plimsoll finds where retry, timeout and circuit-breaker settings across a service graph go past safe limits. It computes this from real configuration (code, Spring YAML, Istio manifests), not from hand-written models.

**Status:** pre-v0.1, under active development. Nothing is usable yet.

## Build

Requires Java 21.

```bash
./mvnw verify
java -jar plimsoll-cli/target/plimsoll.jar
```

## License

Apache-2.0, see [LICENSE](LICENSE).
