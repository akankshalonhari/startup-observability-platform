package com.semiconductorcompany.observability.telemetry;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/** Stands in for Mimir, Loki and Tempo so the platform runs without the LGTM stack. */
@Component
public class InMemoryTelemetryStore implements TelemetryStore {

    private final List<SignalEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void record(SignalEvent event) {
        events.add(event);
    }

    @Override
    public List<SignalEvent> query(Collection<String> assetIds, SignalKind kind, Instant from, Instant to) {
        return events.stream()
                .filter(e -> e.kind() == kind && assetIds.contains(e.assetId()))
                .filter(e -> !e.timestamp().isBefore(from) && !e.timestamp().isAfter(to))
                .sorted(Comparator.comparing(SignalEvent::timestamp))
                .toList();
    }

    @Override
    public Optional<SignalEvent> lookup(EvidencePointer pointer) {
        return events.stream()
                .filter(e -> e.store().equals(pointer.store()) && e.ref().equals(pointer.ref()))
                .filter(e -> pointer.from() == null || !e.timestamp().isBefore(pointer.from()))
                .filter(e -> pointer.to() == null || !e.timestamp().isAfter(pointer.to()))
                .findFirst();
    }
}
