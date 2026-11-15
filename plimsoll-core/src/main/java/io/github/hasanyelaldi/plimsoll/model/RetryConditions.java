package io.github.hasanyelaldi.plimsoll.model;

import java.util.Map;
import java.util.Set;

/**
 * Which failures a layer retries, and as what a failure reaches the layer outside it once this layer gives up
 * (ADR 0014).
 *
 * @param retried the classes the layer retries; UNKNOWN means every class
 * @param givesUpAs the classes a failure of each key class may have for the layer outside; a class that is not a key
 *     passes on as it is; UNKNOWN means the layer outside may see any class
 */
public record RetryConditions(Val<Set<FailureClass>> retried, Val<Map<FailureClass, Set<FailureClass>>> givesUpAs) {

    /** Nothing known: every class retried, any class passed on. */
    public static RetryConditions unknown(String why) {
        return new RetryConditions(Val.unknown(why), Val.unknown(why));
    }
}
