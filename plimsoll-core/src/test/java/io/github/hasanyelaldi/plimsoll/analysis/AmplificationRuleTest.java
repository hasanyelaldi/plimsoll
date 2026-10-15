package io.github.hasanyelaldi.plimsoll.analysis;

import static io.github.hasanyelaldi.plimsoll.analysis.Specs.analyze;
import static io.github.hasanyelaldi.plimsoll.analysis.Specs.findings;
import static io.github.hasanyelaldi.plimsoll.analysis.Specs.load;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class AmplificationRuleTest {

    private static BigInteger n(long v) {
        return BigInteger.valueOf(v);
    }

    @Test
    void singleEdgeAmplifiesByItsAttempts() {
        AnalysisResult r = analyze(Specs.chain(3));
        assertThat(load(r, "s0")).isEqualTo(n(1));
        assertThat(load(r, "s1")).isEqualTo(n(3));
        assertThat(findings(r, "PLM-A1")).isEmpty(); // 3 <= default limit 10
    }

    @Test
    void threeHopChainIs27AndExplainsItself() {
        AnalysisResult r = analyze("""
                services:
                  gateway: { entry: true }
                  orders: {}
                  payments: {}
                  ledger: {}
                edges:
                  - { from: gateway, to: orders, layers: [ { kind: resilience4j, maxAttempts: 3 } ] }
                  - { from: orders, to: payments, layers: [ { kind: istio, attempts: 2 } ] }
                  - { from: payments, to: ledger, layers: [ { kind: resilience4j, maxAttempts: 3 } ] }
                """);
        assertThat(load(r, "ledger")).isEqualTo(n(27));
        Finding f = findings(r, "PLM-A1").get(0);
        assertThat(f.severity()).isEqualTo(Severity.ERROR);
        assertThat(f.location()).isEqualTo("ledger");
        assertThat(f.detail()).isEqualTo("×27 (orders ×3, payments ×9), limit 10");
        assertThat(f.values()).extracting(UsedValue::name).containsExactly(
                "gateway→orders layer 1 (resilience4j) totalAttempts",
                "orders→payments layer 1 (istio) totalAttempts",
                "payments→ledger layer 1 (resilience4j) totalAttempts");
        assertThat(f.values()).extracting(UsedValue::evidence).contains("spec.yaml:8 (attempts 2 + 1)");
        assertThat(f.unknowns()).isEmpty();
        assertThat(findings(r, "PLM-A1")).hasSize(1); // payments is x9 <= 10
    }

    @Test
    void istioAttemptsTwoMeansThreeTotal() {
        AnalysisResult r = analyze("""
                services: { a: { entry: true }, b: {} }
                edges:
                  - { from: a, to: b, layers: [ { kind: istio, attempts: 2 } ] }
                """);
        assertThat(load(r, "b")).isEqualTo(n(3));
    }

    @Test
    void resilience4jThreeTimesIstioThreeOnOneEdgeIsTwelve() {
        // Istio attempts: 3 means 3 retries = 4 total; 3 x 4 = 12.
        AnalysisResult r = analyze("""
                services: { a: { entry: true }, b: {} }
                edges:
                  - from: a
                    to: b
                    layers:
                      - { kind: resilience4j, maxAttempts: 3 }
                      - { kind: istio, attempts: 3 }
                """);
        assertThat(load(r, "b")).isEqualTo(n(12));
        Finding f = findings(r, "PLM-A1").get(0);
        assertThat(f.detail()).isEqualTo("×12, limit 10");
        assertThat(f.values()).hasSize(2);
    }

    @Test
    void diamondSumsBothPaths() {
        AnalysisResult r = analyze("""
                services: { a: { entry: true }, b: {}, c: {}, d: {} }
                edges:
                  - { from: a, to: b, layers: [ { kind: generic, totalAttempts: 2 } ] }
                  - { from: a, to: c, layers: [ { kind: generic, totalAttempts: 3 } ] }
                  - { from: b, to: d, layers: [ { kind: generic, totalAttempts: 2 } ] }
                  - { from: c, to: d, layers: [ { kind: generic, totalAttempts: 4 } ] }
                """, new AnalysisOptions(5));
        assertThat(load(r, "d")).isEqualTo(n(2 * 2 + 3 * 4));
        assertThat(findings(r, "PLM-A1")).extracting(Finding::location).containsExactly("d");
        assertThat(findings(r, "PLM-A1").get(0).detail()).isEqualTo("×16 (b ×2, c ×3), limit 5");
        assertThat(findings(r, "PLM-A1").get(0).values()).hasSize(4);
    }

    @Test
    void fanoutAndParallelEdgesMultiplyAndSum() {
        AnalysisResult r = analyze("""
                services: { a: { entry: true }, b: {}, c: {} }
                edges:
                  - { from: a, to: b, fanout: 4, layers: [ { kind: generic, totalAttempts: 2 } ] }
                  - { from: a, to: b, operation: other, layers: [ { kind: generic, totalAttempts: 3 } ] }
                  - { from: b, to: c, fanout: 2, layers: [] }
                """);
        assertThat(load(r, "b")).isEqualTo(n(8 + 3));
        assertThat(load(r, "c")).isEqualTo(n(22));
    }

    @Test
    void cycleIsUnboundedAndTerminates() {
        AnalysisResult r = analyze("""
                services: { a: { entry: true }, b: {}, c: {}, d: {}, side: {} }
                edges:
                  - { from: a, to: b, layers: [ { kind: generic, totalAttempts: 2 } ] }
                  - { from: b, to: c, layers: [ { kind: generic, totalAttempts: 2 } ] }
                  - { from: c, to: b, layers: [ { kind: generic, totalAttempts: 2 } ] }
                  - { from: c, to: d, layers: [ { kind: generic, totalAttempts: 2 } ] }
                  - { from: a, to: side, layers: [ { kind: generic, totalAttempts: 2 } ] }
                """);
        assertThat(r.amplification()).filteredOn(ServiceAmplification::unbounded)
                .extracting(ServiceAmplification::service).containsExactly("b", "c", "d");
        assertThat(load(r, "a")).isEqualTo(n(1));
        assertThat(load(r, "side")).isEqualTo(n(2));
        Finding f = findings(r, "PLM-A1").get(0);
        assertThat(f.severity()).isEqualTo(Severity.ERROR);
        assertThat(f.location()).isEqualTo("b, c");
        assertThat(f.detail()).startsWith("UNBOUNDED: b, c call each other in a cycle, reachable from a");
        assertThat(f.detail()).endsWith("also unbounded: d");
        assertThat(findings(r, "PLM-A1")).hasSize(1);
    }

    @Test
    void selfLoopIsUnbounded() {
        AnalysisResult r = analyze("""
                services: { a: { entry: true }, b: {} }
                edges:
                  - { from: a, to: b, layers: [] }
                  - { from: b, to: b, layers: [] }
                """);
        assertThat(findings(r, "PLM-A1").get(0).detail()).startsWith("UNBOUNDED: b calls itself");
    }

    @Test
    void cycleNotReachableFromAnEntryIsIgnored() {
        AnalysisResult r = analyze("""
                services: { a: { entry: true }, b: {}, x: {}, y: {} }
                edges:
                  - { from: a, to: b, layers: [] }
                  - { from: x, to: y, layers: [] }
                  - { from: y, to: x, layers: [] }
                """);
        assertThat(findings(r, "PLM-A1")).isEmpty();
        assertThat(r.amplification()).extracting(ServiceAmplification::service).containsExactly("a", "b");
    }

    @Test
    void limitIsConfigurableAndStrict() {
        String spec = Specs.chain(3); // s1 = x3
        assertThat(findings(analyze(spec, new AnalysisOptions(3)), "PLM-A1")).isEmpty();
        assertThat(findings(analyze(spec, new AnalysisOptions(2)), "PLM-A1")).hasSize(1);
    }

    @Test
    void severalEntriesReportTheWorstOne() {
        AnalysisResult r = analyze("""
                services: { a: { entry: true }, z: { entry: true }, b: {} }
                edges:
                  - { from: a, to: b, layers: [ { kind: generic, totalAttempts: 2 } ] }
                  - { from: z, to: b, layers: [ { kind: generic, totalAttempts: 5 } ] }
                """);
        assertThat(load(r, "b")).isEqualTo(n(5));
        assertThat(r.amplification().get(1).entry()).isEqualTo("z");
    }

    @Test
    void unknownAttemptsGiveALowerBoundAndAreNamed() {
        AnalysisResult r = analyze("""
                services: { a: { entry: true }, b: {}, c: {} }
                edges:
                  - { from: a, to: b, layers: [ { kind: generic, totalAttempts: 3 } ] }
                  - { from: b, to: c, layers: [ { kind: generic } ] }
                """);
        assertThat(load(r, "c")).isEqualTo(n(3));
        assertThat(r.amplification().get(2).unknowns()).containsExactly("b→c layer 1 (generic) totalAttempts");
        Finding f = findings(r, "PLM-A1").stream().filter(x -> x.location().equals("c")).findFirst().orElseThrow();
        assertThat(f.severity()).isEqualTo(Severity.INFO);
        assertThat(f.detail()).startsWith("at least ×3");
        assertThat(f.dependsOnUnknown()).isTrue();
        assertThat(f.values()).anySatisfy(v -> assertThat(v.display()).isEqualTo("UNKNOWN"));
    }

    @Test
    void lowerBoundAboveTheLimitIsStillAnError() {
        AnalysisResult r = analyze("""
                services: { a: { entry: true }, b: {}, c: {} }
                edges:
                  - { from: a, to: b, layers: [ { kind: generic, totalAttempts: 20 } ] }
                  - { from: b, to: c, layers: [ { kind: generic } ] }
                """);
        Finding f = findings(r, "PLM-A1").stream().filter(x -> x.location().equals("c")).findFirst().orElseThrow();
        assertThat(f.severity()).isEqualTo(Severity.ERROR);
        assertThat(f.detail()).startsWith("at least ×20");
        assertThat(f.unknowns()).containsExactly("b→c layer 1 (generic) totalAttempts");
    }
}
