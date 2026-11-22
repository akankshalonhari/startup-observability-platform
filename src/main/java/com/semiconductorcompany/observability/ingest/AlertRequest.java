package com.semiconductorcompany.observability.ingest;

import com.semiconductorcompany.observability.graph.NodeType;
import com.semiconductorcompany.observability.identity.SourceTool;
import com.semiconductorcompany.observability.identity.ToolRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** An alert as a tool bridge delivers it (C-3), still in that tool's own identifiers. */
public record AlertRequest(
        @NotNull SourceTool sourceTool,
        String externalId,
        String name,
        String ip,
        String hostId,
        NodeType assetType,
        @NotBlank String title,
        String severity,
        Instant firedAt) {

    public ToolRecord toToolRecord() {
        return new ToolRecord(sourceTool, externalId, name, ip, hostId, assetType);
    }
}
