package io.github.hasanyelaldi.plimsoll.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hasanyelaldi.plimsoll.analysis.AnalysisOptions;
import io.github.hasanyelaldi.plimsoll.analysis.AnalysisResult;
import io.github.hasanyelaldi.plimsoll.analysis.Analyzer;
import io.github.hasanyelaldi.plimsoll.analysis.Finding;
import io.github.hasanyelaldi.plimsoll.analysis.Severity;
import io.github.hasanyelaldi.plimsoll.collect.gateway.GatewayCollector;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.EdgesHint;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.Resilience4jCollector;
import io.github.hasanyelaldi.plimsoll.collect.spring.RequestMappingScanner;
import io.github.hasanyelaldi.plimsoll.collect.spring.RequestMappingScanner.Mapping;
import io.github.hasanyelaldi.plimsoll.io.InvalidSpecException;
import io.github.hasanyelaldi.plimsoll.model.Service;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServiceAssemblerTest {

    private static final Path PROJECTS = Path.of("src/test/resources/projects");

    /** The petclinic gateway fixture with the request mappings of the fixture services. */
    private static GatewayCollector.Result petclinicGateway() {
        Map<String, List<Mapping>> served = new LinkedHashMap<>();
        for (String s : List.of("customers-service", "vets-service", "visits-service")) {
            served.put(s, RequestMappingScanner.scan(PROJECTS.resolve("petclinic-" + s)).mappings());
        }
        return GatewayCollector.collect(PROJECTS.resolve("petclinic-gateway"), served, DefaultsKb.classpath());
    }

    private static ServiceAssembler.Result petclinic(List<EdgesHint> hints) {
        GatewayCollector.Result gateway = petclinicGateway();
        return ServiceAssembler.assemble(gateway.edges(), hints, List.of(gateway));
    }

    private static List<Finding> findings(AnalysisResult r, String ruleId) {
        return r.findings().stream().filter(f -> f.ruleId().equals(ruleId)).toList();
    }

    @Test
    void gatewayWithRoutesIsTheEntryAndTheNoteSaysWhy() {
        ServiceAssembler.Result r = petclinic(List.of());
        assertThat(r.graph().services().keySet()).containsExactly(
                "api-gateway", "customers-service", "genai-service", "vets-service", "visits-service");
        assertThat(r.graph().services().values()).filteredOn(Service::entry)
                .extracting(Service::name).containsExactly("api-gateway");
        assertThat(r.notes()).containsExactly("api-gateway: entry, Spring Cloud Gateway routing to customers-service, "
                + "genai-service, vets-service, visits-service (ADR 0009)");
    }

    @Test
    void deadlinesWithoutAHintStayUnknownAndSayWhereToStateThem() {
        Service vets = petclinic(List.of()).graph().services().get("vets-service");
        assertThat(vets.deadline()).isEqualTo(Val.unknown(
                "vets-service has no edges hint; set 'deadline:' in the edges-hint.yaml of vets-service (ADR 0009)"));
    }

    @Test
    void petclinicA2aAndA2bNameTheUnknownDeadlines() {
        AnalysisResult r = Analyzer.analyze(petclinic(List.of()).graph(), AnalysisOptions.defaults());
        List<Finding> timing = r.findings().stream()
                .filter(f -> f.ruleId().equals("PLM-A2a") || f.ruleId().equals("PLM-A2b")).toList();
        assertThat(timing).isNotEmpty().allSatisfy(f -> {
            assertThat(f.severity()).isEqualTo(Severity.INFO);
            assertThat(f.detail()).contains("cannot evaluate");
            assertThat(f.unknowns()).anyMatch(u -> u.endsWith(" deadline"));
        });
        assertThat(findings(r, "PLM-A2b")).extracting(Finding::location).containsExactly(
                "api-gateway→customers-service", "api-gateway→genai-service", "api-gateway→vets-service",
                "api-gateway→visits-service");
    }

    @Test
    void hintStatesDeadlineAndOverridesTheEntryRule() {
        EdgesHint gateway = EdgesHint.parse("service: api-gateway\nentry: false\n", "gw-hint.yaml");
        EdgesHint customers = EdgesHint.parse("service: customers-service\ndeadline: 2s\nentry: true\n", "c-hint.yaml");
        ServiceAssembler.Result r = petclinic(List.of(gateway, customers));
        Map<String, Service> services = r.graph().services();
        assertThat(services.get("customers-service").deadline())
                .isEqualTo(Val.explicit(Duration.ofSeconds(2), "c-hint.yaml:2"));
        assertThat(services.get("api-gateway").deadline()).isEqualTo(Val.unknown(
                "'deadline' absent (gw-hint.yaml:1); set 'deadline:' in the edges-hint.yaml of api-gateway (ADR 0009)"));
        assertThat(services.values()).filteredOn(Service::entry)
                .extracting(Service::name).containsExactly("customers-service");
        assertThat(r.notes()).containsExactly(
                "api-gateway: not an entry (gw-hint.yaml:2); overrides the gateway rule (Spring Cloud Gateway "
                        + "routing to customers-service, genai-service, vets-service, visits-service)",
                "customers-service: entry (c-hint.yaml:3)");
    }

    @Test
    void hintDeadlineTurnsA2bIntoAFinding() throws Exception {
        Path project = PROJECTS.resolve("gateway");
        var edges = Resilience4jCollector.collect(project, project.resolve("edges-hint.yaml")).edges();
        EdgesHint gateway = EdgesHint.parse("service: api-gateway\nentry: true\n", "gw-hint.yaml");
        EdgesHint customers = EdgesHint.parse("service: customers-service\ndeadline: 2s\n", "c-hint.yaml");
        ServiceAssembler.Result r = ServiceAssembler.assemble(edges, List.of(gateway, customers), List.of());
        AnalysisResult a = Analyzer.analyze(r.graph(), AnalysisOptions.defaults());
        Finding f = findings(a, "PLM-A2b").stream()
                .filter(x -> x.location().equals("api-gateway→customers-service (GET /owners/{id})")
                        && x.severity() == Severity.WARN)
                .findFirst().orElseThrow();
        assertThat(f.detail()).isEqualTo("callee deadline 2 s > per-try 800 ms");
        assertThat(f.values()).anySatisfy(v -> {
            assertThat(v.name()).isEqualTo("customers-service deadline");
            assertThat(v.source()).isEqualTo(Source.EXPLICIT);
            assertThat(v.evidence()).isEqualTo("c-hint.yaml:2");
        });
    }

    @Test
    void noEntryLeavesANote() {
        Path project = PROJECTS.resolve("gateway");
        var edges = Resilience4jCollector.collect(project, project.resolve("edges-hint.yaml")).edges();
        ServiceAssembler.Result r = ServiceAssembler.assemble(edges, List.of(), List.of());
        assertThat(r.graph().services().values()).noneMatch(Service::entry);
        assertThat(r.notes()).containsExactly("no entry service: PLM-A1 has nothing to start from; set 'entry: true' "
                + "in the edges-hint.yaml of the service that takes outside requests");
        assertThat(Analyzer.analyze(r.graph(), AnalysisOptions.defaults()).amplification()).isEmpty();
    }

    @Test
    void rejectsTwoHintsForOneService() {
        EdgesHint a = EdgesHint.parse("service: orders\n", "a.yaml");
        EdgesHint b = EdgesHint.parse("service: orders\ndeadline: 1s\n", "b.yaml");
        assertThatThrownBy(() -> ServiceAssembler.assemble(List.of(), List.of(a, b), List.of()))
                .isInstanceOf(InvalidSpecException.class)
                .hasMessage("service orders has more than one edges hint");
    }
}
