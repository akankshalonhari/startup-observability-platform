package com.semiconductorcompany.observability.telemetry;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Read-only access to the signal stores. Production adapters run PromQL on Mimir, LogQL on Loki, TraceQL on Tempo. */
public interface TelemetryReader {

    List<SignalEvent> query(Collection<String> assetIds, SignalKind kind, Instant from, Instant to);

    /** The signal an evidence pointer refers to, if it really exists in that store and window. */
    Optional<SignalEvent> lookup(EvidencePointer pointer);
}
