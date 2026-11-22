package com.semiconductorcompany.observability.agents;

import com.semiconductorcompany.observability.graph.Node;
import com.semiconductorcompany.observability.graph.NodeType;
import com.semiconductorcompany.observability.telemetry.EvidencePointer;
import com.semiconductorcompany.observability.telemetry.SignalEvent;
import com.semiconductorcompany.observability.telemetry.SignalKind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Cloud specialist: VMs watched by Azure Monitor or CloudWatch. */
@Component
public class CloudAgent implements SpecialistAgent {

    static final String NAME = "cloud";
    private static final Set<String> SOURCES = Set.of("azure-monitor", "cloudwatch");

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Finding> analyse(AgentContext ctx, ReadOnlyTools tools) {
        List<Node> cloudAssets = tools.nodesInScope(ctx, NodeType.VM, NodeType.HOST).stream()
                .filter(n -> n.attribute("cloud") != null)
                .toList();
        Map<String, List<SignalEvent>> byAsset = tools.signals(SpecialistAgent.ids(cloudAssets), SignalKind.METRIC, ctx.windowStart(), ctx.windowEnd())
                .stream()
                .filter(s -> SOURCES.contains(s.attr("source")))
                .collect(Collectors.groupingBy(SignalEvent::assetId, LinkedHashMap::new, Collectors.toList()));
        return byAsset.entrySet().stream()
                .map(e -> {
                    SignalEvent latest = e.getValue().get(e.getValue().size() - 1);
                    Node asset = tools.node(e.getKey()).orElseThrow();
                    return Finding.symptom(NAME, e.getKey(),
                            "%s (%s): %s".formatted(asset.getName(), latest.attr("source"), latest.message()), 0.6,
                            e.getValue().stream().limit(2).map(EvidencePointer::of).toList());
                })
                .toList();
    }
}
