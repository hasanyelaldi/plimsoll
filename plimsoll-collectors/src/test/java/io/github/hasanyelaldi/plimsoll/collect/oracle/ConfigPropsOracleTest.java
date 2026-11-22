package io.github.hasanyelaldi.plimsoll.collect.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hasanyelaldi.plimsoll.collect.effective.ConfigPropsDump;
import io.github.hasanyelaldi.plimsoll.collect.effective.ConfigPropsDump.Dump;
import io.github.hasanyelaldi.plimsoll.collect.gateway.GatewayCollector;
import io.github.hasanyelaldi.plimsoll.collect.kb.DefaultsKb;
import io.github.hasanyelaldi.plimsoll.collect.resilience4j.Resilience4jConfig;
import io.github.hasanyelaldi.plimsoll.collect.spring.SpringProperties;
import io.github.hasanyelaldi.plimsoll.model.Source;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.reactive.AnnotationConfigReactiveWebApplicationContext;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClientsProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * Differential oracle (ADR 0016, #25): a reactive Spring Boot 4.1 service with Actuator binds
 * {@code oracle/configprops-app.yml}, {@code /actuator/configprops} is fetched in memory, and {@link ConfigPropsDump}
 * must turn it into properties from which the collectors read the values the running libraries use. It also checks
 * that {@code effective/orders-configprops.json}, the fixture the other tests read, is this dump for the beans
 * Plimsoll reads, and that a value is masked unless {@code show-values} is {@code always}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigPropsOracleTest {

    private static final String FIXTURE = "effective/orders-configprops.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class App {}

    private ConfigurableApplicationContext context;
    private String dump;
    private SpringProperties effective;

    /** @param args command-line arguments, which override the YAML (builder properties would not) */
    private static ConfigurableApplicationContext service(String... args) {
        return new SpringApplicationBuilder(App.class)
                .web(WebApplicationType.REACTIVE)
                .contextFactory(ApplicationContextFactory.ofContextClass(
                        AnnotationConfigReactiveWebApplicationContext.class))
                .properties("spring.main.banner-mode=off", "spring.config.location=classpath:oracle/configprops-app.yml",
                        "spring.cloud.compatibility-verifier.enabled=false")
                .run(args);
    }

    @BeforeAll
    void start() {
        context = service();
        InMemoryHttp.Answer a = InMemoryHttp.get(context, "/actuator/configprops");
        assertThat(a.status()).isEqualTo(200);
        dump = a.body();
        Dump d = ConfigPropsDump.parse(dump, "orders-configprops.json", "orders");
        assertThat(d.context()).isEqualTo("orders");
        effective = d.applyTo(SpringProperties.parse("spring.application.name: orders\n", "application.yml"));
    }

    @AfterAll
    void stop() {
        context.close();
    }

    @Test
    void resilience4jValuesAreTheOnesTheRegistriesUse() {
        Resilience4jConfig r4j = Resilience4jConfig.of(effective, DefaultsKb.classpath());
        var real = context.getBean(RetryRegistry.class).retry("backend").getRetryConfig();
        assertThat(r4j.retry("backend").totalAttempts().value()).isEqualTo(real.getMaxAttempts()).isEqualTo(5);
        assertThat(r4j.retry("backend").totalAttempts().source()).isEqualTo(Source.EXPLICIT);
        assertThat(r4j.retry("backend").totalAttempts().evidence()).startsWith("orders-configprops.json:1 (/contexts/"
                + "orders/beans/resilience4j.retry-io.github.resilience4j.springboot.retry.autoconfigure.RetryProperties"
                + "/properties/instances/backend/maxAttempts; origin: class path resource "
                + "[oracle/configprops-app.yml] - ");
        assertThat(r4j.retry("backend").initialBackoff().value().toMillis())
                .isEqualTo(real.getIntervalBiFunction().apply(1, null)).isEqualTo(250L);
        assertThat(r4j.timeLimiterTimeout("backend").value()).isEqualTo(context.getBean(TimeLimiterRegistry.class)
                .timeLimiter("backend").getTimeLimiterConfig().getTimeoutDuration()).isEqualTo(Duration.ofSeconds(2));
        assertThat(r4j.circuitBreaker("backend").slidingWindowSize().value()).isEqualTo(context
                .getBean(CircuitBreakerRegistry.class).circuitBreaker("backend").getCircuitBreakerConfig()
                .getSlidingWindowSize()).isEqualTo(20);
    }

    @Test
    void gatewayRoutesAndLoadBalancerRetryAreTheBoundOnes() {
        GatewayCollector.Result gw = GatewayCollector.collect(effective, "orders", DefaultsKb.classpath());
        assertThat(gw.edges()).singleElement().satisfies(e -> {
            assertThat(e.to()).isEqualTo("payments-service");
            assertThat(e.layers().get(0).retry().totalAttempts().value()).isEqualTo(3);
        });
        LoadBalancerClientsProperties lb = context.getBean(LoadBalancerClientsProperties.class);
        assertThat(effective.get("spring", "cloud", "loadbalancer", "retry", "enabled").orElseThrow().text())
                .isEqualTo(String.valueOf(lb.getRetry().isEnabled())).isEqualTo("true");
        assertThat(effective.get("spring", "cloud", "loadbalancer", "retry", "max-retries-on-next-service-instance")
                .orElseThrow().text()).isEqualTo(String.valueOf(lb.getRetry().getMaxRetriesOnNextServiceInstance()));
    }

    /** The fixture is this dump, trimmed to the beans under the prefixes Plimsoll reads. */
    @Test
    void theFixtureIsTheRealDumpForTheBeansPlimsollReads() throws IOException {
        JsonNode live = JSON.readTree(dump).at("/contexts/orders/beans");
        JsonNode fixture;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(FIXTURE)) {
            fixture = JSON.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8)).at("/contexts/orders/beans");
        }
        int read = 0;
        for (Iterator<Map.Entry<String, JsonNode>> it = live.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> bean = it.next();
            String prefix = bean.getValue().get("prefix").asText();
            if (prefix.startsWith("resilience4j") || prefix.startsWith("spring.cloud.gateway")
                    || prefix.startsWith("spring.cloud.loadbalancer") || prefix.startsWith("spring.cloud.circuitbreaker")
                    || prefix.startsWith("spring.ai")) {
                assertThat(sorted(fixture.get(bean.getKey()))).as("fixture bean %s; regenerate %s from this test's "
                        + "dump", bean.getKey(), FIXTURE).isEqualTo(sorted(bean.getValue()));
                read++;
            }
        }
        assertThat(fixture.size()).isEqualTo(read);
    }

    /**
     * Bound {@code Set}s, such as the LoadBalancer's {@code retryableExceptions}, come out in hash order, which changes
     * from run to run; lists of scalars are compared as sorted.
     */
    private static JsonNode sorted(JsonNode n) {
        if (n == null) {
            return null;
        }
        if (n.isObject()) {
            var o = JSON.createObjectNode();
            n.fields().forEachRemaining(e -> o.set(e.getKey(), sorted(e.getValue())));
            return o;
        }
        if (n.isArray()) {
            var a = JSON.createArrayNode();
            java.util.List<JsonNode> items = new java.util.ArrayList<>();
            n.forEach(i -> items.add(sorted(i)));
            if (items.stream().allMatch(JsonNode::isValueNode)) {
                items.sort(java.util.Comparator.comparing(JsonNode::asText));
            }
            items.forEach(a::add);
            return a;
        }
        return n;
    }

    /** Spring Boot masks configprops values by default; the reader then keeps the declared value and notes it. */
    @Test
    void valuesAreMaskedUnlessShowValuesIsAlways() {
        try (ConfigurableApplicationContext masked = service("--management.endpoint.configprops.show-values=never")) {
            String body = InMemoryHttp.get(masked, "/actuator/configprops").body();
            Dump d = ConfigPropsDump.parse(body, "masked.json", "orders");
            assertThat(d.overlaid()).noneMatch(p -> String.join(".", p.path()).endsWith("instances.backend.maxAttempts"));
            assertThat(d.notes()).anyMatch(n -> n.contains("instances.backend.maxAttempts is masked; set "
                    + "management.endpoint.configprops.show-values=always"));
        }
    }
}
