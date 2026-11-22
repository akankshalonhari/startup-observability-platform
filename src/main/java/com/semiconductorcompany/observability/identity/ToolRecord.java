package com.semiconductorcompany.observability.identity;

import com.semiconductorcompany.observability.graph.NodeType;
import jakarta.validation.constraints.NotNull;

/**
 * How one tool describes an asset. {@code hostId} is the OTel host.id, which equals the Azure VMUUID:
 * the real join key between the two.
 */
public record ToolRecord(
        @NotNull SourceTool tool,
        String externalId,
        String name,
        String ip,
        String hostId,
        NodeType assetType) {
}
