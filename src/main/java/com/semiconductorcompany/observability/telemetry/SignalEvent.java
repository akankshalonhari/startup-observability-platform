package com.semiconductorcompany.observability.telemetry;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One telemetry datum, already attached to a canonical asset id.
 * {@code ref} is what evidence cites: a PromQL query for metrics, a log line id for logs, a trace id for traces.
 */
public record SignalEvent(
        String id,
        SignalKind kind,
        String assetId,
        Instant timestamp,
        String ref,
        String message,
        Map<String, String> attributes) {

    public SignalEvent {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static SignalEvent log(String assetId, Instant at, String message, Map<String, String> attributes) {
        String id = "log-" + UUID.randomUUID().toString().substring(0, 12);
        return new SignalEvent(id, SignalKind.LOG, assetId, at, id, message, attributes);
    }

    public static SignalEvent metric(String assetId, Instant at, String promQl, String message, Map<String, String> attributes) {
        return new SignalEvent(newId(), SignalKind.METRIC, assetId, at, promQl, message, attributes);
    }

    public static SignalEvent trace(String assetId, Instant at, String traceId, String message, Map<String, String> attributes) {
        return new SignalEvent(newId(), SignalKind.TRACE, assetId, at, traceId, message, attributes);
    }

    /** The LGTM store this signal lives in. */
    public String store() {
        return switch (kind) {
            case METRIC -> EvidencePointer.MIMIR;
            case LOG -> EvidencePointer.LOKI;
            case TRACE -> EvidencePointer.TEMPO;
        };
    }

    public String attr(String key) {
        return attributes.get(key);
    }

    private static String newId() {
        return "sig-" + UUID.randomUUID().toString().substring(0, 12);
    }
}
