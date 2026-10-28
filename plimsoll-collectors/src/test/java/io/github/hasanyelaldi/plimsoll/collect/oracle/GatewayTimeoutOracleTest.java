package io.github.hasanyelaldi.plimsoll.collect.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.reactive.AnnotationConfigReactiveWebApplicationContext;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Oracle for the Spring Cloud Gateway (WebFlux) HTTP client timeouts: which response timeout the real
 * {@link NettyRoutingFilter} applies to a route, from the global {@code httpclient.response-timeout} and the
 * route's {@code metadata.response-timeout}, and the connect timeout default.
 *
 * <p>Waiting for a timeout would mean sleeping, so the test asks the routing filter for the timeout it would apply
 * (its private {@code getResponseTimeout(Route)}). That a timed-out try is retried is shown by
 * {@code GatewayRetryOracleTest#responseTimeoutOfATryIsRetried}.
 */
class GatewayTimeoutOracleTest {

    private static final String ROUTES = "spring.cloud.gateway.server.webflux.routes";

    /** The response timeout per route id; {@code null} for none. */
    private static Map<String, Duration> responseTimeouts(String... props) throws Exception {
        List<String> all = new ArrayList<>(List.of(
                "spring.main.banner-mode=off",
                "spring.config.location=optional:classpath:none.yml",
                "spring.cloud.compatibility-verifier.enabled=false"));
        String[][] routes = {
                {"plain"},
                {"millis", "metadata.response-timeout=500"},
                {"negative", "metadata.response-timeout=-1"},
                {"garbage", "metadata.response-timeout=soon"}};
        for (int i = 0; i < routes.length; i++) {
            all.add(ROUTES + "[" + i + "].id=" + routes[i][0]);
            all.add(ROUTES + "[" + i + "].uri=http://backend.invalid:8080");
            all.add(ROUTES + "[" + i + "].predicates[0]=Path=/" + routes[i][0] + "/**");
            for (int j = 1; j < routes[i].length; j++) {
                all.add(ROUTES + "[" + i + "]." + routes[i][j]);
            }
        }
        all.addAll(List.of(props));
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(GatewayRetryOracleTest.App.class)
                .web(WebApplicationType.REACTIVE)
                .contextFactory(ApplicationContextFactory.ofContextClass(
                        AnnotationConfigReactiveWebApplicationContext.class))
                .properties(all.toArray(String[]::new))
                .run()) {
            NettyRoutingFilter filter = context.getBean(NettyRoutingFilter.class);
            Method get = NettyRoutingFilter.class.getDeclaredMethod("getResponseTimeout", Route.class);
            get.setAccessible(true);
            Map<String, Duration> out = new TreeMap<>();
            for (Route r : context.getBean(RouteLocator.class).getRoutes().collectList().block()) {
                out.put(r.getId(), (Duration) get.invoke(filter, r));
            }
            return out;
        }
    }

    @Test
    void noResponseTimeoutByDefault() throws Exception {
        Map<String, Duration> t = responseTimeouts();
        assertThat(t.get("plain")).isNull();
        assertThat(t.get("garbage")).isNull();
    }

    @Test
    void routeMetadataIsInMillisAndANegativeValueSwitchesTheGlobalTimeoutOff() throws Exception {
        Map<String, Duration> t = responseTimeouts(
                "spring.cloud.gateway.server.webflux.httpclient.response-timeout=2s");
        assertThat(t.get("plain")).isEqualTo(Duration.ofSeconds(2));
        assertThat(t.get("millis")).isEqualTo(Duration.ofMillis(500));
        assertThat(t.get("negative")).isNull();
        assertThat(t.get("garbage")).isEqualTo(Duration.ofSeconds(2));   // not a number: the global value
    }

    @Test
    void globalResponseTimeoutTakesSpringDurations() throws Exception {
        assertThat(responseTimeouts("spring.cloud.gateway.server.webflux.httpclient.response-timeout=1500")
                .get("plain")).isEqualTo(Duration.ofMillis(1500));
    }

    @Test
    void connectTimeoutIsNotSetSoReactorNettyKeepsItsOwn() throws Exception {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(GatewayRetryOracleTest.App.class)
                .web(WebApplicationType.REACTIVE)
                .contextFactory(ApplicationContextFactory.ofContextClass(
                        AnnotationConfigReactiveWebApplicationContext.class))
                .properties("spring.main.banner-mode=off", "spring.config.location=optional:classpath:none.yml",
                        "spring.cloud.compatibility-verifier.enabled=false")
                .run()) {
            HttpClientProperties props = context.getBean(HttpClientProperties.class);
            assertThat(props.getConnectTimeout()).isNull();
            assertThat(props.getResponseTimeout()).isNull();
        }
    }
}
