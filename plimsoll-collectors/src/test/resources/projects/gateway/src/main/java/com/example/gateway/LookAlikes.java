package com.example.gateway;

import com.example.custom.Retry;
import org.springframework.retry.annotation.Retryable;

/** Annotations with the same simple name from other libraries are not Resilience4j. */
public class LookAlikes {

    @Retry(name = "customers")
    public void custom() {}

    @Retryable
    public void springRetry() {}

    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "qualified")
    public void qualified() {}
}
