package com.semiconductorcompany.observability.incident;

/** Result of ingesting one alert: which asset it resolved to and which incident it joined. */
public record IngestedAlert(Alert alert, String incidentId, String assetId, boolean duplicate) {
}
