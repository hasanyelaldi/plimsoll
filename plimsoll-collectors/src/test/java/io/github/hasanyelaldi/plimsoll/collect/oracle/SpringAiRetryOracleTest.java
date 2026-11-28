package io.github.hasanyelaldi.plimsoll.collect.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseErrorHandler;

/**
 * Oracle for Spring AI retry (#19): the {@code RetryTemplate} and {@code ResponseErrorHandler} beans of
 * {@code SpringAiRetryAutoConfiguration}, bound from {@code spring.ai.retry.*} by real Spring Boot. A chat model such
 * as {@code OpenAiChatModel} runs each HTTP call to the provider through that template ({@link RetryUtils#execute}),
 * and its HTTP client classifies error responses with that handler. A failing {@code Retryable} counts the tries;
 * backoff is read from the policy's {@code BackOff} or set to zero, so nothing sleeps.
 */
class SpringAiRetryOracleTest {

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(SpringAiRetryAutoConfiguration.class)
    static class App {}

    private static void with(Consumer<ConfigurableApplicationContext> check, String... properties) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class)
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off", "spring.config.location=optional:classpath:none.yml")
                .run(properties)) {
            check.accept(context);
        }
    }

    private static final String NO_WAIT = "--spring.ai.retry.backoff.initial-interval=0ms";

    /** The tries of one call that fails every time with {@code error}, and what the caller gets. */
    private record Outcome(int tries, Throwable thrown) {}

    private static Outcome failing(RetryTemplate template, RuntimeException error) {
        AtomicInteger tries = new AtomicInteger();
        try {
            RetryUtils.execute(template, () -> {
                tries.incrementAndGet();
                throw error;
            });
        } catch (RuntimeException e) {
            return new Outcome(tries.get(), e);
        }
        throw new AssertionError("the call did not fail");
    }

    private static Outcome failing(String error, String... properties) {
        List<Outcome> out = new ArrayList<>();
        String[] args = new String[properties.length + 1];
        args[0] = NO_WAIT;
        System.arraycopy(properties, 0, args, 1, properties.length);
        with(c -> out.add(failing(c.getBean(RetryTemplate.class), switch (error) {
            case "transient" -> new TransientAiException("503");
            case "io" -> new ResourceAccessException("I/O error on POST request");
            case "non-transient" -> new NonTransientAiException("401");
            default -> new IllegalStateException(error);
        })), args);
        return out.get(0);
    }

    @Test
    void maxAttemptsCountsRetriesOnlySoTheDefaultIsElevenTries() {
        Outcome o = failing("transient");
        assertThat(o.tries()).isEqualTo(11);
        assertThat(failing("transient", "--spring.ai.retry.max-attempts=3").tries()).isEqualTo(4);
        assertThat(failing("transient", "--spring.ai.retry.max-attempts=0").tries()).isEqualTo(1);
    }

    @Test
    void afterTheLastTryTheCallerGetsTheLastErrorAsItIs() {
        TransientAiException error = new TransientAiException("503 - overloaded");
        List<Outcome> out = new ArrayList<>();
        with(c -> out.add(failing(c.getBean(RetryTemplate.class), error)), NO_WAIT);
        assertThat(out.get(0).thrown()).isSameAs(error);
    }

    @Test
    void transientAndResourceAccessErrorsAreRetriedOthersAreNot() {
        assertThat(failing("io").tries()).isEqualTo(11);
        assertThat(failing("non-transient").tries()).isEqualTo(1);
        assertThat(failing("other").tries()).isEqualTo(1);
    }

    @Test
    void theDefaultBackoffIsExponentialFromTwoSecondsTimesFiveCappedAtThreeMinutesWithoutJitter() {
        with(c -> {
            RetryPolicy policy = c.getBean(RetryTemplate.class).getRetryPolicy();
            for (int run = 0; run < 2; run++) { // the same waits every time: no jitter
                BackOffExecution waits = policy.getBackOff().start();
                List<Long> first = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    first.add(waits.nextBackOff());
                }
                assertThat(first).containsExactly(2_000L, 10_000L, 50_000L, 180_000L, 180_000L);
            }
            assertThat(policy.getTimeout()).isEqualTo(Duration.ZERO);
        });
    }

    @Test
    void backoffPropertiesBind() {
        with(c -> {
            BackOffExecution waits = c.getBean(RetryTemplate.class).getRetryPolicy().getBackOff().start();
            assertThat(List.of(waits.nextBackOff(), waits.nextBackOff(), waits.nextBackOff()))
                    .containsExactly(100L, 300L, 500L);
        }, "--spring.ai.retry.backoff.initial-interval=100ms", "--spring.ai.retry.backoff.multiplier=3",
                "--spring.ai.retry.backoff.max-interval=500ms");
    }

    // ---- error responses -------------------------------------------------------

    private static ClientHttpResponse response(int status) {
        return new ClientHttpResponse() {
            @Override
            public HttpStatusCode getStatusCode() {
                return HttpStatusCode.valueOf(status);
            }

            @Override
            public String getStatusText() {
                return "";
            }

            @Override
            public void close() {}

            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream("{\"error\":\"x\"}".getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }
        };
    }

    /** Whether the handler's exception for {@code status} is retried by the template. */
    private static List<Boolean> retried(List<Integer> statuses, String... properties) {
        List<Boolean> out = new ArrayList<>();
        with(c -> {
            ResponseErrorHandler handler = c.getBean(ResponseErrorHandler.class);
            RetryPolicy policy = c.getBean(RetryTemplate.class).getRetryPolicy();
            for (int status : statuses) {
                Throwable[] thrown = new Throwable[1];
                assertThatThrownBy(() -> handler.handleError(URI.create("https://api.openai.com/v1/chat/completions"),
                        HttpMethod.POST, response(status))).satisfies(t -> thrown[0] = t);
                out.add(policy.shouldRetry(thrown[0]));
            }
        }, properties);
        return out;
    }

    @Test
    void serverErrorsAreRetriedClientErrorsAreNot() {
        assertThat(retried(List.of(500, 502, 503, 504, 400, 401, 404, 429)))
                .containsExactly(true, true, true, true, false, false, false, false);
    }

    @Test
    void onClientErrorsRetriesClientErrorsToo() {
        assertThat(retried(List.of(400, 429, 503), "--spring.ai.retry.on-client-errors=true"))
                .containsExactly(true, true, true);
    }

    @Test
    void onHttpCodesAreRetriedEvenWhenTheyAreClientErrorsAndExcludedCodesAreNot() {
        assertThat(retried(List.of(429, 400, 503, 500),
                "--spring.ai.retry.on-http-codes=429", "--spring.ai.retry.exclude-on-http-codes=503"))
                .containsExactly(true, false, false, true);
    }
}
