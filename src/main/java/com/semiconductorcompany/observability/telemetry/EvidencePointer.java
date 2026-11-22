package com.semiconductorcompany.observability.telemetry;

import com.semiconductorcompany.observability.graph.Node;
import java.time.Instant;

/**
 * An exact pointer into a store (hard constraint 4: evidence or nothing): a log id, PromQL, trace id or
 * change ticket, with a time window. Anyone can re-run it to check the claim.
 */
public record EvidencePointer(String store, String ref, Instant from, Instant to) {

    public static final String MIMIR = "mimir";
    public static final String LOKI = "loki";
    public static final String TEMPO = "tempo";
    public static final String SERVICENOW = "servicenow";

    public static EvidencePointer of(SignalEvent signal) {
        return new EvidencePointer(signal.store(), signal.ref(), signal.timestamp(), signal.timestamp());
    }

    /** Points at a change ticket by its graph node id. */
    public static EvidencePointer ofChange(Node change) {
        return new EvidencePointer(SERVICENOW, change.getId(), change.getTimestamp(), change.getTimestamp());
    }
}
