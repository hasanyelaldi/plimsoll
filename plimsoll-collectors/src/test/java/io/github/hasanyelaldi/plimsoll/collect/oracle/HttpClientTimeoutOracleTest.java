package io.github.hasanyelaldi.plimsoll.collect.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Oracle for the response timeout of the HTTP clients spring-petclinic-microservices builds itself
 * ({@code WebClient.builder()}, {@code RestClient.builder()}), without Spring Boot's client configuration. The test
 * classpath has Reactor Netty and neither Apache HttpComponents nor Jetty, so both pick Reactor Netty.
 *
 * <p>Waiting for a timeout would mean sleeping, so the test reads the {@link HttpClient} configuration that the
 * real builders produce. The fields read are private; a rename makes this oracle fail, which is the point.
 */
class HttpClientTimeoutOracleTest {

    @Test
    void webClientBuilderUsesReactorNettyWithoutResponseTimeout() {
        Object exchange = field(WebClient.builder().build(), "exchangeFunction");
        Object connector = field(exchange, "connector");
        assertThat(connector).isInstanceOf(ReactorClientHttpConnector.class);
        assertThat(((HttpClient) field(connector, "httpClient")).configuration().responseTimeout()).isNull();
    }

    @Test
    void restClientBuilderUsesReactorNettyWithA10SecondResponseTimeout() {
        Object factory = field(RestClient.builder().build(), "clientRequestFactory");
        assertThat(factory).isInstanceOf(ReactorClientHttpRequestFactory.class);
        assertThat(((HttpClient) field(factory, "httpClient")).configuration().responseTimeout())
                .isEqualTo(Duration.ofSeconds(10));
    }

    private static Object field(Object target, String name) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                // look in the superclass
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
        throw new AssertionError(target.getClass().getName() + " has no field " + name);
    }
}
