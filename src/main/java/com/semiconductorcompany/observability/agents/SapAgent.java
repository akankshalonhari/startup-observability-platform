package com.semiconductorcompany.observability.agents;

import com.semiconductorcompany.observability.agents.Finding.Category;
import com.semiconductorcompany.observability.graph.Node;
import com.semiconductorcompany.observability.graph.NodeType;
import com.semiconductorcompany.observability.telemetry.EvidencePointer;
import com.semiconductorcompany.observability.telemetry.SignalEvent;
import com.semiconductorcompany.observability.telemetry.SignalKind;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** SAP specialist: HANA system replication status (Mimir) and failing S/4 traces into HANA (Tempo). */
@Component
public class SapAgent implements SpecialistAgent {

    static final String NAME = "sap";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Finding> analyse(AgentContext ctx, ReadOnlyTools tools) {
        List<Node> services = tools.nodesInScope(ctx, NodeType.SERVICE).stream()
                .filter(s -> "sap".equals(s.attribute("platform")))
                .toList();
        List<Finding> out = new ArrayList<>();
        for (Node service : services) {
            List<String> ids = List.of(service.getId());
            List<SignalEvent> unhealthy = tools.signals(ids, SignalKind.METRIC, ctx.windowStart(), ctx.windowEnd()).stream()
                    .filter(s -> "hana_sysrep_status".equals(s.attr("metric")) && !"ACTIVE".equals(s.attr("value")))
                    .toList();
            List<SignalEvent> errorTraces = tools.signals(ids, SignalKind.TRACE, ctx.windowStart(), ctx.windowEnd()).stream()
                    .filter(s -> "true".equals(s.attr("error")))
                    .toList();
            if (unhealthy.isEmpty() && errorTraces.isEmpty()) {
                continue;
            }
            List<EvidencePointer> evidence = Stream.concat(unhealthy.stream().limit(1), errorTraces.stream().limit(2))
                    .map(EvidencePointer::of)
                    .toList();
            if (!unhealthy.isEmpty()) {
                SignalEvent first = unhealthy.get(0);
                out.add(new Finding(NAME, Category.SYMPTOM, service.getId(),
                        "HANA system replication for %s is %s since %s".formatted(service.getName(), first.attr("value"), first.timestamp()),
                        0.8, evidence, null, null,
                        "Confirm the replication endpoints resolve before touching replication. "
                                + "Restart hdbnameserver on the secondary if replication does not resume.", null));
            } else {
                out.add(Finding.symptom(NAME, service.getId(),
                        "%s has %d failing traces into HANA since %s".formatted(service.getName(), errorTraces.size(), errorTraces.get(0).timestamp()),
                        0.7, evidence));
            }
        }
        return out;
    }
}
