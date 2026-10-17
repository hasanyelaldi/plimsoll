package com.example.gateway;

import io.github.resilience4j.retry.annotation.*;

@Retry(name = "visits")
public class VisitsClient {

    public String list() {
        return null;
    }
}
