package com.semiconductorcompany.observability.telemetry;

/** Write side, used only by the collection pipeline. Agents get {@link TelemetryReader}. */
public interface TelemetryStore extends TelemetryReader {

    void record(SignalEvent event);
}
