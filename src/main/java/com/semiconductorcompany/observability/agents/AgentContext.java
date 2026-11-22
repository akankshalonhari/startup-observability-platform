package com.semiconductorcompany.observability.agents;

import com.semiconductorcompany.observability.graph.ChangeHit;
import com.semiconductorcompany.observability.incident.Alert;
import com.semiconductorcompany.observability.incident.Incident;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** What the supervisor hands every specialist: the incident, its graph scope and the nearby changes. */
public record AgentContext(
        Incident incident,
        List<Alert> alerts,
        Map<String, Integer> scope,
        List<ChangeHit> changes,
        Instant windowStart,
        Instant windowEnd) {

    public Set<String> alertedAssetIds() {
        return alerts.stream().map(Alert::assetId).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
