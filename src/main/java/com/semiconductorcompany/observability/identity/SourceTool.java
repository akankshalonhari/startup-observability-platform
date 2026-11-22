package com.semiconductorcompany.observability.identity;

import java.util.Locale;

/** Tools bridged into the pipeline during migration (C-3), plus OpenTelemetry itself. */
public enum SourceTool {
    AZURE_MONITOR, CLOUDWATCH, LOGICMONITOR, TURBONOMIC, OTEL, SERVICENOW, INFOBLOX;

    /** Key used for this tool's alias on a graph node. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
