package com.example.gateway;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.util.concurrent.CompletableFuture;

public class CustomersClient {

    private static final String VISITS = "visits";

    @Retry(name = "customers")
    @CircuitBreaker(name = "customers")
    @TimeLimiter(name = "customers")
    public CompletableFuture<String> getOwner(int id) {
        return null;
    }

    @Retry(name = VISITS)
    public String getVisits(int petId) {
        return null;
    }

    @TimeLimiter(name = "customers")
    public String syncLookup() {
        return null;
    }

    @Retry(name = "customers")
    public void notInTheHint() {}

    public String plain() {
        return null;
    }
}
