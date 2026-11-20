package io.github.hasanyelaldi.plimsoll.analysis;

import io.github.hasanyelaldi.plimsoll.model.CircuitBreaker;

/**
 * A circuit breaker on a layer of an edge, as the report lists it (ADR 0015).
 *
 * @param edge  the edge's location in the graph
 * @param layer the kind of the layer that carries the breaker
 */
public record BreakerListing(String edge, String layer, CircuitBreaker breaker) {}
