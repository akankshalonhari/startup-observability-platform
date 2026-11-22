package com.semiconductorcompany.observability.incident;

import com.semiconductorcompany.observability.identity.SourceTool;
import java.time.Instant;

/** A normalised alert, attached to a canonical asset. */
public record Alert(String id, SourceTool source, String assetId, String severity, String title, Instant firedAt) {
}
