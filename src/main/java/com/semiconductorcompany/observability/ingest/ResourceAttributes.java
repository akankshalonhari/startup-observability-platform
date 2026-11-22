package com.semiconductorcompany.observability.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.semiconductorcompany.observability.graph.ZoneTier;

/** The one OpenTelemetry resource schema every collector stamps on every signal (C-1). */
public record ResourceAttributes(
        @JsonProperty("host.id") String hostId,
        @JsonProperty("host.name") String hostName,
        @JsonProperty("service.name") String serviceName,
        @JsonProperty("site.code") String siteCode,
        @JsonProperty("zone.tier") ZoneTier zoneTier,
        @JsonProperty("deployment.environment") String deploymentEnvironment) {
}
