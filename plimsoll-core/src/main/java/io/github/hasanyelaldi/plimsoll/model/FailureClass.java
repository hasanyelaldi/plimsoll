package io.github.hasanyelaldi.plimsoll.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * How a failure looks to one layer of an edge (ADR 0014). Only {@link #NOT_SENT} means the callee cannot have
 * processed the request.
 */
public enum FailureClass {
    /** Connection failure, connect timeout, or a stream the callee refused: the request was not processed. */
    NOT_SENT("not-sent"),
    /** Connection closed or reset after the request was sent, with no response. */
    RESET("reset"),
    /** No response within the layer's per-try timeout. */
    TIMEOUT("timeout"),
    STATUS_500("status-500"),
    STATUS_502("status-502"),
    STATUS_503("status-503"),
    STATUS_504("status-504"),
    STATUS_5XX_OTHER("status-5xx-other"),
    STATUS_409("status-409"),
    STATUS_429("status-429"),
    STATUS_4XX_OTHER("status-4xx-other");

    private final String id;

    FailureClass(String id) {
        this.id = id;
    }

    /** The name used in specs, the knowledge base and reports, e.g. {@code status-503}. */
    public String id() {
        return id;
    }

    public boolean processed() {
        return this != NOT_SENT;
    }

    /** The class of a response with this status; empty for a status that is not a failure (below 400). */
    public static Optional<FailureClass> status(int code) {
        return Optional.ofNullable(switch (code) {
            case 500 -> STATUS_500;
            case 502 -> STATUS_502;
            case 503 -> STATUS_503;
            case 504 -> STATUS_504;
            case 409 -> STATUS_409;
            case 429 -> STATUS_429;
            default -> code >= 500 && code <= 599 ? STATUS_5XX_OTHER
                    : code >= 400 && code <= 499 ? STATUS_4XX_OTHER : null;
        });
    }

    /** {@code status-503}, or a bare status code such as {@code 503}. */
    public static Optional<FailureClass> parse(String text) {
        if (text.matches("\\d{3}")) {
            return status(Integer.parseInt(text));
        }
        return Arrays.stream(values()).filter(c -> c.id.equals(text)).findFirst();
    }

    @Override
    public String toString() {
        return id;
    }
}
