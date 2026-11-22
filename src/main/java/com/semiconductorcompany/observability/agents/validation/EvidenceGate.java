package com.semiconductorcompany.observability.agents.validation;

import com.semiconductorcompany.observability.agents.AgentContext;
import com.semiconductorcompany.observability.agents.Finding;
import com.semiconductorcompany.observability.graph.GraphReader;
import com.semiconductorcompany.observability.telemetry.EvidencePointer;
import com.semiconductorcompany.observability.telemetry.TelemetryReader;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Gate 1, evidence check: a claim with no pointers, or a pointer that does not resolve, is dropped. */
@Component
@Order(1)
public class EvidenceGate implements ValidationGate {

    public static final String NAME = "evidence";

    private final GraphReader graph;
    private final TelemetryReader telemetry;

    public EvidenceGate(GraphReader graph, TelemetryReader telemetry) {
        this.graph = graph;
        this.telemetry = telemetry;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public GateResult apply(List<Finding> findings, AgentContext ctx) {
        List<Finding> kept = new ArrayList<>();
        List<DroppedClaim> dropped = new ArrayList<>();
        for (Finding finding : findings) {
            if (finding.evidence().isEmpty()) {
                dropped.add(DroppedClaim.of(NAME, finding, "uncited claim"));
                continue;
            }
            List<String> broken = finding.evidence().stream()
                    .filter(p -> !resolves(p))
                    .map(p -> p.store() + ":" + p.ref())
                    .toList();
            if (broken.isEmpty()) {
                kept.add(finding);
            } else {
                dropped.add(DroppedClaim.of(NAME, finding, "evidence does not resolve: " + broken));
            }
        }
        return new GateResult(NAME, kept, dropped,
                List.of("%d of %d claims cite evidence that checks out".formatted(kept.size(), findings.size())));
    }

    private boolean resolves(EvidencePointer pointer) {
        if (EvidencePointer.SERVICENOW.equals(pointer.store())) {
            return graph.node(pointer.ref()).isPresent();
        }
        return telemetry.lookup(pointer).isPresent();
    }
}
