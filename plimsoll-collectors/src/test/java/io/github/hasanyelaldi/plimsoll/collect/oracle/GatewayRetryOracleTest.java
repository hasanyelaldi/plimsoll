package io.github.hasanyelaldi.plimsoll.collect.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.reactive.AnnotationConfigReactiveWebApplicationContext;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.AbstractServerHttpRequest;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.SslInfo;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Oracle for the Spring Cloud Gateway (WebFlux) {@code Retry} filter and its order with {@code CircuitBreaker}, as
 * spring-petclinic-microservices configures them. The real gateway handles each request in memory: no server, no
 * socket. A global filter just before the routing filter stands in for the upstream; it counts every try and
 * answers with a status, fails with an IOException, or never answers.
 *
 * <p>No sleeps: backoff is off by default, and the 20 ms TimeLimiter timeouts are the only waiting.
 */
class GatewayRetryOracleTest {

    /**
     * For the other oracles: the gateway and its circuit breaker are on the test classpath for this one, and
     * auto-configure into every {@code @EnableAutoConfiguration} context unless switched off.
     */
    static final String[] GATEWAY_OFF = {"spring.cloud.gateway.server.webflux.enabled=false",
            "spring.cloud.circuitbreaker.resilience4j.enabled=false"};

    enum Answer { STATUS, IO_ERROR, HANG }

    /** The upstream: runs once per try, before {@code NettyRoutingFilter}, and marks the exchange as routed. */
    static final class Upstream implements GlobalFilter, Ordered {
        final AtomicInteger tries = new AtomicInteger();
        Answer answer = Answer.STATUS;
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;

        @Override
        public Mono<Void> filter(org.springframework.web.server.ServerWebExchange exchange,
                org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
            tries.incrementAndGet();
            ServerWebExchangeUtils.setAlreadyRouted(exchange);
            return switch (answer) {
                case STATUS -> {
                    exchange.getResponse().setStatusCode(status);
                    yield chain.filter(exchange);
                }
                case IO_ERROR -> Mono.error(new IOException("connection reset"));
                case HANG -> Mono.never();
            };
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE - 1;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class App {
        @Bean
        Upstream upstream() {
            return new Upstream();
        }

        @Bean
        Customizer<ReactiveResilience4JCircuitBreakerFactory> timeLimiter() {
            return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                    .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
                    .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(20)).build())
                    .build());
        }
    }

    private record Outcome(int tries, HttpStatus status) {}

    /**
     * @param filters the route's filters, outermost first, as {@code filters[i].…=…} property suffixes
     */
    private static Outcome send(HttpMethod method, Answer answer, HttpStatus status, String... filters) {
        List<String> props = new ArrayList<>(List.of(
                "spring.main.banner-mode=off",
                "spring.config.location=optional:classpath:none.yml",
                "spring.cloud.compatibility-verifier.enabled=false",
                "spring.cloud.gateway.server.webflux.routes[0].id=backend",
                "spring.cloud.gateway.server.webflux.routes[0].uri=http://backend.invalid:8080",
                "spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/api/**"));
        for (String f : filters) {
            props.add("spring.cloud.gateway.server.webflux.routes[0]." + f);
        }
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class)
                .web(WebApplicationType.REACTIVE)
                .contextFactory(ApplicationContextFactory.ofContextClass(
                        AnnotationConfigReactiveWebApplicationContext.class))
                .properties(props.toArray(String[]::new))
                .run()) {
            Upstream upstream = context.getBean(Upstream.class);
            upstream.answer = answer;
            upstream.status = status;
            HttpHandler handler = WebHttpHandlerBuilder.applicationContext(context).build();
            Response response = new Response();
            handler.handle(new Request(method, URI.create("http://gateway/api/owners/1")), response)
                    .block(Duration.ofSeconds(10));
            return new Outcome(upstream.tries.get(), HttpStatus.resolve(response.getStatusCode().value()));
        }
    }

    // ---- Retry alone -----------------------------------------------------

    @Test
    void retriesCountsRetriesOnlySoOneRetryIsTwoTries() {
        Outcome o = send(HttpMethod.GET, Answer.STATUS, HttpStatus.SERVICE_UNAVAILABLE,
                "filters[0].name=Retry", "filters[0].args.retries=1");
        assertThat(o.tries()).isEqualTo(2);
        assertThat(o.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void defaultsAreThreeRetriesOnGetFor5xx() {
        assertThat(send(HttpMethod.GET, Answer.STATUS, HttpStatus.SERVICE_UNAVAILABLE, "filters[0].name=Retry")
                .tries()).isEqualTo(4);
        assertThat(send(HttpMethod.GET, Answer.STATUS, HttpStatus.INTERNAL_SERVER_ERROR, "filters[0].name=Retry")
                .tries()).isEqualTo(4);
        assertThat(send(HttpMethod.GET, Answer.STATUS, HttpStatus.NOT_FOUND, "filters[0].name=Retry")
                .tries()).isEqualTo(1);
    }

    @Test
    void defaultMethodsAreGetOnlySoPostIsNotRetried() {
        assertThat(send(HttpMethod.POST, Answer.STATUS, HttpStatus.SERVICE_UNAVAILABLE, "filters[0].name=Retry")
                .tries()).isEqualTo(1);
        assertThat(send(HttpMethod.POST, Answer.IO_ERROR, null, "filters[0].name=Retry").tries()).isEqualTo(1);
    }

    @Test
    void ioExceptionsAreRetriedWithTheSameCount() {
        assertThat(send(HttpMethod.GET, Answer.IO_ERROR, null, "filters[0].name=Retry", "filters[0].args.retries=1")
                .tries()).isEqualTo(2);
        assertThat(send(HttpMethod.GET, Answer.IO_ERROR, null, "filters[0].name=Retry").tries()).isEqualTo(4);
    }

    /** {@code statuses} adds to the default {@code series: SERVER_ERROR}; it does not replace it. */
    @Test
    void petclinicFilterRetriesPostOnceOn5xxAndOnIoExceptions() {
        String[] petclinic = {"filters[0].name=Retry", "filters[0].args.retries=1",
                "filters[0].args.statuses=SERVICE_UNAVAILABLE", "filters[0].args.methods=POST"};
        assertThat(send(HttpMethod.POST, Answer.STATUS, HttpStatus.SERVICE_UNAVAILABLE, petclinic).tries())
                .isEqualTo(2);
        assertThat(send(HttpMethod.POST, Answer.IO_ERROR, null, petclinic).tries()).isEqualTo(2);
        assertThat(send(HttpMethod.POST, Answer.STATUS, HttpStatus.INTERNAL_SERVER_ERROR, petclinic).tries())
                .isEqualTo(2);
        assertThat(send(HttpMethod.POST, Answer.STATUS, HttpStatus.NOT_FOUND, petclinic).tries()).isEqualTo(1);
        assertThat(send(HttpMethod.GET, Answer.STATUS, HttpStatus.SERVICE_UNAVAILABLE, petclinic).tries())
                .isEqualTo(1);
    }

    // ---- CircuitBreaker and Retry ----------------------------------------

    private static final String[] BREAKER_THEN_RETRY = {"filters[0].name=CircuitBreaker", "filters[0].args.name=cb",
            "filters[1].name=Retry", "filters[1].args.retries=2"};
    private static final String[] RETRY_THEN_BREAKER = {"filters[0].name=Retry", "filters[0].args.retries=2",
            "filters[1].name=CircuitBreaker", "filters[1].args.name=cb"};

    @Test
    void breakerListedFirstWrapsAllTriesSoOneTimeoutEndsTheRequest() {
        Outcome o = send(HttpMethod.GET, Answer.HANG, null, BREAKER_THEN_RETRY);
        assertThat(o.tries()).isEqualTo(1);
        assertThat(o.status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void breakerListedFirstStillLetsRetryRepeatFailedStatuses() {
        assertThat(send(HttpMethod.GET, Answer.STATUS, HttpStatus.SERVICE_UNAVAILABLE, BREAKER_THEN_RETRY).tries())
                .isEqualTo(3);
    }

    /**
     * The breaker turns its TimeLimiter timeout into a 504 {@code ResponseStatusException}, which is neither a
     * retried exception nor a response status, so Retry outside the breaker does not repeat a timed-out try either.
     */
    @Test
    void breakerListedAfterRetryAlsoEndsTheRequestAtTheFirstTimeout() {
        Outcome o = send(HttpMethod.GET, Answer.HANG, null, RETRY_THEN_BREAKER);
        assertThat(o.tries()).isEqualTo(1);
        assertThat(o.status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void breakerListedAfterRetryLetsRetryRepeatFailedStatuses() {
        assertThat(send(HttpMethod.GET, Answer.STATUS, HttpStatus.SERVICE_UNAVAILABLE, RETRY_THEN_BREAKER).tries())
                .isEqualTo(3);
    }

    // ---- an in-memory exchange -------------------------------------------

    private static final class Request extends AbstractServerHttpRequest {
        Request(HttpMethod method, URI uri) {
            super(method, uri, null, new HttpHeaders());
        }

        @Override
        protected MultiValueMap<String, HttpCookie> initCookies() {
            return new LinkedMultiValueMap<>();
        }

        @Override
        protected SslInfo initSslInfo() {
            return null;
        }

        @Override
        public Flux<DataBuffer> getBody() {
            return Flux.empty();
        }

        @Override
        public <T> T getNativeRequest() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class Response extends AbstractServerHttpResponse {
        Response() {
            super(DefaultDataBufferFactory.sharedInstance);
        }

        @Override
        public <T> T getNativeResponse() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Mono<Void> writeWithInternal(Publisher<? extends DataBuffer> body) {
            return Flux.from(body).doOnNext(DataBufferUtils::release).then();
        }

        @Override
        protected Mono<Void> writeAndFlushWithInternal(Publisher<? extends Publisher<? extends DataBuffer>> body) {
            return Flux.from(body).concatMap(Flux::from).doOnNext(DataBufferUtils::release).then();
        }

        @Override
        protected void applyStatusCode() {}

        @Override
        protected void applyHeaders() {}

        @Override
        protected void applyCookies() {}
    }
}
