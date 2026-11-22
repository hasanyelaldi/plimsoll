package io.github.hasanyelaldi.plimsoll.collect.oracle;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.reactivestreams.Publisher;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.AbstractServerHttpRequest;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.SslInfo;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** A GET through a reactive application context's HTTP handler, without a socket; for oracle tests. */
final class InMemoryHttp {

    record Answer(int status, String body) {}

    private InMemoryHttp() {}

    static Answer get(ApplicationContext context, String path) {
        Response response = new Response();
        WebHttpHandlerBuilder.applicationContext(context).build()
                .handle(new Request(URI.create("http://localhost" + path)), response)
                .block(Duration.ofSeconds(10));
        return new Answer(response.getStatusCode() == null ? 200 : response.getStatusCode().value(),
                response.body.toString(StandardCharsets.UTF_8));
    }

    private static final class Request extends AbstractServerHttpRequest {
        Request(URI uri) {
            super(HttpMethod.GET, uri, null, new HttpHeaders());
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
        final ByteArrayOutputStream body = new ByteArrayOutputStream();

        Response() {
            super(DefaultDataBufferFactory.sharedInstance);
        }

        @Override
        public <T> T getNativeResponse() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Mono<Void> writeWithInternal(Publisher<? extends DataBuffer> data) {
            return Flux.from(data).doOnNext(this::keep).then();
        }

        @Override
        protected Mono<Void> writeAndFlushWithInternal(Publisher<? extends Publisher<? extends DataBuffer>> data) {
            return Flux.from(data).concatMap(Flux::from).doOnNext(this::keep).then();
        }

        private void keep(DataBuffer b) {
            byte[] bytes = new byte[b.readableByteCount()];
            b.read(bytes);
            body.writeBytes(bytes);
            DataBufferUtils.release(b);
        }

        @Override
        protected void applyStatusCode() {}

        @Override
        protected void applyHeaders() {}

        @Override
        protected void applyCookies() {}
    }
}
