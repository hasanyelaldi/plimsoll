package com.example.owners;

import static org.springframework.http.HttpMethod.PUT;

import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

public class OwnersClient {

    private final WebClient.Builder webClientBuilder;
    private final RestClient restClient;

    public OwnersClient(WebClient.Builder webClientBuilder, RestClient restClient) {
        this.webClientBuilder = webClientBuilder;
        this.restClient = restClient;
    }

    public Object create(Object owner) {
        return webClientBuilder.build()
            .post()
            .uri("http://customers-service/owners")
            .bodyValue(owner)
            .retrieve();
    }

    public Object find(int id) {
        return restClient.method(HttpMethod.GET).uri("http://customers-service/owners/{id}", id).retrieve();
    }

    public Object dynamic(HttpMethod method) {
        return restClient.method(method).uri("http://customers-service/owners").retrieve();
    }

    public Object mismatch() {
        return webClientBuilder.build().get().uri("http://customers-service/owners").retrieve();
    }

    public void noCall() {}

    public Object readThenWrite(Object owner) {
        Object read = restClient.get().uri("http://customers-service/owners/1").retrieve();
        return restClient.method(PUT).uri("http://customers-service/owners/1").body(owner).retrieve();
    }
}
