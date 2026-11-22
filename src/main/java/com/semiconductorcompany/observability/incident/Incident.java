package com.semiconductorcompany.observability.incident;

import com.semiconductorcompany.observability.agents.IncidentBrief;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/** One correlated incident: many alerts, one brief, one page (I-1). */
public class Incident {

    public enum Status { OPEN, BRIEFED }

    private final String id;
    private final Instant openedAt;
    private final List<String> alertIds = new CopyOnWriteArrayList<>();
    private final Set<String> assetIds = new CopyOnWriteArraySet<>();
    private volatile Instant lastAlertAt;
    /** Wall-clock time the last alert arrived; the triage hold-down counts from here. */
    private volatile Instant lastActivityAt;
    private volatile Status status = Status.OPEN;
    private volatile int suppressedDuplicates;
    private volatile IncidentBrief brief;
    private volatile String pagedTeam;
    private volatile String serviceNowIncident;

    public Incident(String id, Instant openedAt) {
        this.id = id;
        this.openedAt = openedAt;
        this.lastAlertAt = openedAt;
        this.lastActivityAt = Instant.now();
    }

    synchronized void add(Alert alert) {
        alertIds.add(alert.id());
        assetIds.add(alert.assetId());
        if (alert.firedAt().isAfter(lastAlertAt)) {
            lastAlertAt = alert.firedAt();
        }
        lastActivityAt = Instant.now();
        // New evidence: the brief gets rewritten, but nobody is paged a second time.
        status = Status.OPEN;
    }

    synchronized void suppressDuplicate() {
        suppressedDuplicates++;
    }

    void briefed(IncidentBrief brief) {
        this.brief = brief;
        this.status = Status.BRIEFED;
    }

    void paged(String teamId, String serviceNowIncident) {
        this.pagedTeam = teamId;
        this.serviceNowIncident = serviceNowIncident;
    }

    public String getId() {
        return id;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public List<String> getAlertIds() {
        return List.copyOf(alertIds);
    }

    public Set<String> getAssetIds() {
        return Set.copyOf(assetIds);
    }

    public Instant getLastAlertAt() {
        return lastAlertAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public Status getStatus() {
        return status;
    }

    public int getSuppressedDuplicates() {
        return suppressedDuplicates;
    }

    public IncidentBrief getBrief() {
        return brief;
    }

    public String getPagedTeam() {
        return pagedTeam;
    }

    public String getServiceNowIncident() {
        return serviceNowIncident;
    }
}
