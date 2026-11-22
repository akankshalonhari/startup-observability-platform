package com.semiconductorcompany.observability.agents;

import com.semiconductorcompany.observability.graph.Node;
import com.semiconductorcompany.observability.telemetry.EvidencePointer;
import com.semiconductorcompany.observability.telemetry.SignalEvent;
import com.semiconductorcompany.observability.telemetry.SignalKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Log analyst: groups error logs (Loki) across the whole scope by exception. */
@Component
public class LogAnalystAgent implements SpecialistAgent {

    static final String NAME = "log-analyst";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Finding> analyse(AgentContext ctx, ReadOnlyTools tools) {
        Map<String, List<SignalEvent>> byException = tools.signals(ctx.scope().keySet(), SignalKind.LOG, ctx.windowStart(), ctx.windowEnd())
                .stream()
                .filter(s -> "ERROR".equals(s.attr("level")))
                .collect(Collectors.groupingBy(s -> s.attributes().getOrDefault("exception", s.message()),
                        LinkedHashMap::new, Collectors.toList()));

        List<Finding> out = new ArrayList<>();
        byException.forEach((exception, logs) -> {
            String assetId = logs.stream()
                    .collect(Collectors.groupingBy(SignalEvent::assetId, Collectors.counting()))
                    .entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
            SignalEvent first = logs.get(0);
            SignalEvent last = logs.get(logs.size() - 1);
            String asset = tools.node(assetId).map(Node::getName).orElse(assetId);
            // Cite the first and last occurrence so the time span is checkable.
            List<EvidencePointer> evidence = logs.size() == 1
                    ? List.of(EvidencePointer.of(first))
                    : List.of(EvidencePointer.of(first), EvidencePointer.of(last));
            out.add(Finding.symptom(NAME, assetId,
                    "%d x %s on %s between %s and %s, e.g. \"%s\"".formatted(logs.size(), exception, asset,
                            first.timestamp(), last.timestamp(), first.message()),
                    0.7, evidence));
        });
        return out;
    }
}
