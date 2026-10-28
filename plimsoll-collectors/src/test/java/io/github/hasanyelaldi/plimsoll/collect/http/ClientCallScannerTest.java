package io.github.hasanyelaldi.plimsoll.collect.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hasanyelaldi.plimsoll.collect.http.ClientCallScanner.ClientCall;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.hasanyelaldi.plimsoll.model.Val;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClientCallScannerTest {

    private static final String CONFIG = """
            package p;
            import org.springframework.cloud.client.loadbalancer.LoadBalanced;
            import org.springframework.context.annotation.Bean;
            import org.springframework.web.client.RestTemplate;
            import org.springframework.web.reactive.function.client.WebClient;
            class Config {
                @Bean
                @LoadBalanced
                WebClient.Builder loadBalancedWebClientBuilder() {
                    return WebClient.builder();
                }

                @Bean
                @LoadBalanced
                RestTemplate loadBalancedRestTemplate() {
                    return new RestTemplate();
                }
            }
            """;

    private static List<ClientCall> scan(String... files) {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("Config.java", CONFIG);
        for (int i = 0; i < files.length; i++) {
            sources.put("C" + i + ".java", files[i]);
        }
        return ClientCallScanner.parse(sources);
    }

    private static ClientCall only(String... files) {
        List<ClientCall> calls = scan(files);
        assertThat(calls).hasSize(1);
        return calls.get(0);
    }

    private static final String HEADER = """
            package p;
            import org.springframework.web.reactive.function.client.WebClient;
            import org.springframework.web.client.RestClient;
            import org.springframework.web.client.RestTemplate;
            """;

    @Test
    void literalUriOnAnInjectedLoadBalancedBuilder() {
        ClientCall c = only(HEADER + """
                class CustomersClient {
                    private final WebClient.Builder webClientBuilder;
                    CustomersClient(WebClient.Builder webClientBuilder) {
                        this.webClientBuilder = webClientBuilder;
                    }
                    Object getOwner(int ownerId) {
                        return webClientBuilder.build().get()
                            .uri("http://customers-service/owners/{ownerId}?full=true", ownerId)
                            .retrieve();
                    }
                }
                """);
        assertThat(c.qualifiedMethod()).isEqualTo("p.CustomersClient#getOwner");
        assertThat(c.client()).isEqualTo("WebClient");
        assertThat(c.method()).isEqualTo(Val.explicit("GET", "C0.java:11"));
        assertThat(c.host().value()).isEqualTo("customers-service");
        assertThat(c.operation()).isEqualTo("GET /owners/{ownerId}");
        assertThat(c.evidence()).isEqualTo("C0.java:11");
        assertThat(c.loadBalanced()).isEqualTo(Val.explicit(true,
                "@LoadBalanced WebClient.Builder bean at Config.java:9, injected at parameter webClientBuilder at C0.java:7"));
    }

    @Test
    void fieldInitialisedWithALiteralGivesTheHostAndNamesItsOtherAssignments() {
        ClientCall c = only(HEADER + """
                class VisitsClient {
                    private String hostname = "http://visits-service/";
                    private final WebClient.Builder webClientBuilder;
                    VisitsClient(WebClient.Builder webClientBuilder) {
                        this.webClientBuilder = webClientBuilder;
                    }
                    Object visits(String ids) {
                        return webClientBuilder.build()
                            .get()
                            .uri(hostname + "pets/visits?petId={petId}", ids)
                            .retrieve();
                    }
                    void setHostname(String hostname) {
                        this.hostname = hostname;
                    }
                }
                """);
        assertThat(c.host().value()).isEqualTo("visits-service");
        assertThat(c.host().evidence()).isEqualTo("C0.java:13: hostname + \"pets/visits?petId={petId}\" "
                + "(field hostname is also assigned at C0.java:18)");
        assertThat(c.operation()).isEqualTo("GET /pets/visits");
    }

    @Test
    void localVariableAndAWebClientFieldBuiltInTheConstructor() {
        ClientCall c = only(HEADER + """
                class VetsLoader {
                    private final WebClient webClient;
                    VetsLoader(WebClient.Builder webClientBuilder) {
                        this.webClient = webClientBuilder.build();
                    }
                    void load() {
                        String vetsHostname = "http://vets-service/";
                        webClient.get().uri(vetsHostname + "vets").retrieve();
                    }
                }
                """);
        assertThat(c.host().value()).isEqualTo("vets-service");
        assertThat(c.operation()).isEqualTo("GET /vets");
        assertThat(c.loadBalanced().value()).isTrue();
    }

    @Test
    void hostFromDiscoveryClientIsUnknown() {
        ClientCall c = only(HEADER + """
                class Ai {
                    private final RestClient restClient = RestClient.builder().build();
                    Object addOwner(Object owner) {
                        return restClient.post().uri(customersUri() + "/owners").body(owner).retrieve();
                    }
                    java.net.URI customersUri() {
                        return discoveryClient.getInstances("customers-service").get(0).getUri();
                    }
                }
                """);
        assertThat(c.client()).isEqualTo("RestClient");
        assertThat(c.method().value()).isEqualTo("POST");
        assertThat(c.host()).isEqualTo(Val.unknown("C0.java:8: host not resolved from customersUri() + \"/owners\""));
        assertThat(c.loadBalanced().value()).isFalse();
        assertThat(c.loadBalanced().evidence()).contains("built in place, without a load balancer");
    }

    @Test
    void hostOfAConcatenationThatEndsInsideTheHostIsUnknown() {
        ClientCall c = only(HEADER + """
                class C {
                    void call(WebClient webClient, String name) {
                        webClient.get().uri("http://" + name + "/x").retrieve();
                    }
                }
                """);
        assertThat(c.host().isKnown()).isFalse();
        assertThat(c.loadBalanced().source()).isEqualTo(Source.UNKNOWN);
        assertThat(c.loadBalanced().evidence()).contains("client is the parameter webClient, passed by callers");
    }

    @Test
    void restTemplateCallsGiveTheirMethodAndTheLoadBalancedBean() {
        List<ClientCall> calls = scan(HEADER + """
                import org.springframework.http.HttpMethod;
                class T {
                    private final RestTemplate restTemplate;
                    T(RestTemplate restTemplate) {
                        this.restTemplate = restTemplate;
                    }
                    void calls() {
                        restTemplate.getForObject("http://vets-service/vets", Object.class);
                        restTemplate.exchange("http://visits-service/visits/{id}", HttpMethod.DELETE, null, Object.class, 1);
                        restTemplate.postForEntity(java.net.URI.create("http://customers-service/owners"), null, Object.class);
                    }
                }
                """);
        assertThat(calls).extracting(ClientCall::client).containsOnly("RestTemplate");
        assertThat(calls).extracting(ClientCall::operation)
                .containsExactly("GET /vets", "DELETE /visits/{id}", "POST /owners");
        assertThat(calls).allSatisfy(c -> assertThat(c.loadBalanced().value()).isTrue());
    }

    @Test
    void injectedBuilderWithoutALoadBalancedBeanIsNotLoadBalanced() {
        ClientCall c = only(HEADER + """
                class C {
                    private final RestClient.Builder builder;
                    C(RestClient.Builder builder) {
                        this.builder = builder;
                    }
                    void call() {
                        builder.build().get().uri("http://vets-service/vets").retrieve();
                    }
                }
                """);
        assertThat(c.loadBalanced())
                .isEqualTo(Val.explicit(false, "parameter builder at C0.java:7: no @LoadBalanced RestClient.Builder bean in the module"));
    }

    @Test
    void loadBalancedInjectionPointCounts() {
        ClientCall c = only(HEADER + """
                import org.springframework.cloud.client.loadbalancer.LoadBalanced;
                class C {
                    @LoadBalanced
                    private RestClient.Builder builder;
                    void call() {
                        builder.build().get().uri("http://vets-service/vets").retrieve();
                    }
                }
                """);
        assertThat(c.loadBalanced().value()).isTrue();
        assertThat(c.loadBalanced().evidence()).contains("injection point is @LoadBalanced");
    }

    @Test
    void methodGivenAsAnExpressionIsUnknown() {
        ClientCall c = only(HEADER + """
                class C {
                    private final WebClient webClient = WebClient.create();
                    void call(org.springframework.http.HttpMethod m) {
                        webClient.method(m).uri("http://vets-service/vets").retrieve();
                    }
                }
                """);
        assertThat(c.method().isKnown()).isFalse();
        assertThat(c.operation()).isEqualTo("/vets");
    }

    @Test
    void resilience4jAnnotationsOnTheEnclosingMethodAreListed() {
        ClientCall c = only(HEADER + """
                import io.github.resilience4j.retry.annotation.Retry;
                class C {
                    private final WebClient webClient = WebClient.create();
                    @Retry(name = "vets")
                    void call() {
                        webClient.get().uri("http://vets-service/vets").retrieve();
                    }
                }
                """);
        assertThat(c.resilience4j()).containsExactly("@Retry at C0.java:8");
    }

    @Test
    void otherGetAndUriCallsAreNotRequests() {
        assertThat(scan(HEADER + """
                class C {
                    void call(java.util.Map<String, Object> map, RestTemplate restTemplate) {
                        map.get("x");
                        restTemplate.getForObject("http://vets-service/vets", Object.class);
                        java.util.Optional.of(map).get().toString();
                    }
                }
                """)).extracting(ClientCall::client).containsExactly("RestTemplate");
    }
}
