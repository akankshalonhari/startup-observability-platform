package com.semiconductorcompany.observability.ingest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** A ServiceNow change, with the CIs it touched as ServiceNow names them (G-2). */
public record ChangeEvent(
        @NotBlank String number,
        @NotBlank String summary,
        @NotNull Instant implementedAt,
        @NotEmpty List<String> affectedCis) {
}
