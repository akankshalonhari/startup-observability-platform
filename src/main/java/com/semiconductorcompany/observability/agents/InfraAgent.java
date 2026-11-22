package com.semiconductorcompany.observability.agents;

import com.semiconductorcompany.observability.graph.Node;
import com.semiconductorcompany.observability.graph.NodeType;
import com.semiconductorcompany.observability.telemetry.EvidencePointer;
import com.semiconductorcompany.observability.telemetry.SignalEvent;
import com.semiconductorcompany.observability.telemetry.SignalKind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Infra/compute specialist: hypervisor hosts and clusters, from Turbonomic metrics. */
@Component
public class InfraAgent implements SpecialistAgent {

    static final String NAME = "infra";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Finding> analyse(AgentContext ctx, ReadOnlyTools tools) {
        List<Node> compute = tools.nodesInScope(ctx, NodeType.HOST, NodeType.CLUSTER);
        Map<String, List<SignalEvent>> byAsset = tools.signals(SpecialistAgent.ids(compute), SignalKind.METRIC, ctx.windowStart(), ctx.windowEnd())
                .stream()
                .filter(s -> "turbonomic".equals(s.attr("source")))
                .collect(Collectors.groupingBy(SignalEvent::assetId, LinkedHashMap::new, Collectors.toList()));
        return byAsset.entrySet().stream()
                .map(e -> {
                    SignalEvent latest = e.getValue().get(e.getValue().size() - 1);
                    String name = tools.node(e.getKey()).map(Node::getName).orElse(e.getKey());
                    return Finding.symptom(NAME, e.getKey(), "%s: %s".formatted(name, latest.message()), 0.6,
                            e.getValue().stream().limit(2).map(EvidencePointer::of).toList());
                })
                .toList();
    }
}
