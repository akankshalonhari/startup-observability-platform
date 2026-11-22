package com.semiconductorcompany.observability.ingest;

import com.semiconductorcompany.observability.telemetry.SignalKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

/** A signal from an OpenTelemetry collector. {@code ref} is the PromQL or trace id; logs get an id assigned. */
public record SignalRequest(
        @NotNull @Valid ResourceAttributes resource,
        @NotNull SignalKind kind,
        String ref,
        @NotBlank String message,
        Instant timestamp,
        Map<String, String> attributes) {
}
