package com.semiconductorcompany.observability.policy;

import com.semiconductorcompany.observability.graph.GraphReader;
import com.semiconductorcompany.observability.graph.Node;
import com.semiconductorcompany.observability.graph.ZoneTier;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Runtime half of the OT guarantee (hard constraint 1c), checked against the zone tier in the graph.
 * The build-time half is policy/ot_observe_only.rego; network and IAM enforcement live outside this service.
 * There is no code path that executes remediation: the furthest the platform goes is a change request.
 */
@Component
public class ActionPolicy {

    public record Decision(boolean allowed, String reason) {
    }

    private final GraphReader graph;

    public ActionPolicy(GraphReader graph) {
        this.graph = graph;
    }

    public Decision mayRaiseChange(String assetId) {
        Node asset = graph.node(assetId).orElse(null);
        if (asset == null) {
            return new Decision(false, "Unknown asset " + assetId);
        }
        ZoneTier tier = graph.zoneTier(assetId, Instant.now());
        if (tier.isObserveOnly()) {
            return new Decision(false, "%s is in an L0 OT zone (or has no zone): observe-only, no write path. Contact the OT owner directly."
                    .formatted(asset.getName()));
        }
        return new Decision(true, "%s zone: raised as a change request for human approval".formatted(tier));
    }
}
