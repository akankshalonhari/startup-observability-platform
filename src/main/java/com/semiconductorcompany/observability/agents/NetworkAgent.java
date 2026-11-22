package com.semiconductorcompany.observability.agents;

import static java.util.Comparator.comparing;

import com.semiconductorcompany.observability.agents.Finding.Category;
import com.semiconductorcompany.observability.graph.ChangeHit;
import com.semiconductorcompany.observability.graph.Node;
import com.semiconductorcompany.observability.graph.NodeType;
import com.semiconductorcompany.observability.telemetry.EvidencePointer;
import com.semiconductorcompany.observability.telemetry.SignalEvent;
import com.semiconductorcompany.observability.telemetry.SignalKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Network specialist: interface flaps (LogicMonitor) and DNS/network changes (Infoblox, ServiceNow). */
@Component
public class NetworkAgent implements SpecialistAgent {

    static final String NAME = "network";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Finding> analyse(AgentContext ctx, ReadOnlyTools tools) {
        List<Finding> findings = new ArrayList<>(interfaceFlaps(ctx, tools));
        findings.addAll(networkChanges(ctx, tools));
        return findings;
    }

    private List<Finding> interfaceFlaps(AgentContext ctx, ReadOnlyTools tools) {
        Set<String> ports = SpecialistAgent.ids(tools.nodesInScope(ctx, NodeType.INTERFACE));
        Map<String, List<SignalEvent>> linkEvents = tools.signals(ports, SignalKind.LOG, ctx.windowStart(), ctx.windowEnd()).stream()
                .filter(s -> isEvent(s, "LINK_DOWN") || isEvent(s, "LINK_UP"))
                .collect(Collectors.groupingBy(SignalEvent::assetId, LinkedHashMap::new, Collectors.toList()));

        List<Finding> out = new ArrayList<>();
        linkEvents.forEach((portId, events) -> {
            Optional<SignalEvent> down = events.stream().filter(e -> isEvent(e, "LINK_DOWN")).min(comparing(SignalEvent::timestamp));
            if (down.isEmpty()) {
                return;
            }
            Instant downAt = down.get().timestamp();
            Optional<SignalEvent> up = events.stream()
                    .filter(e -> isEvent(e, "LINK_UP") && e.timestamp().isAfter(downAt))
                    .min(comparing(SignalEvent::timestamp));
            String port = tools.node(portId).map(Node::getName).orElse(portId);
            String claim = up.map(u -> "Interface %s flapped: down at %s, back up at %s".formatted(port, downAt, u.timestamp()))
                    .orElse("Interface %s has been down since %s".formatted(port, downAt));
            List<EvidencePointer> evidence = Stream.concat(down.stream(), up.stream()).map(EvidencePointer::of).toList();
            out.add(new Finding(NAME, Category.PROBABLE_CAUSE, portId, claim, 0.5, evidence,
                    downAt, up.map(SignalEvent::timestamp).orElse(null),
                    "Check the error counters on %s. Shut down and re-enable the port if errors persist.".formatted(port), null));
        });
        return out;
    }

    private List<Finding> networkChanges(AgentContext ctx, ReadOnlyTools tools) {
        Set<String> networkAssets = SpecialistAgent.ids(
                tools.nodesInScope(ctx, NodeType.DNS_RECORD, NodeType.NETWORK_DEVICE, NodeType.INTERFACE));
        List<SignalEvent> removals = tools.signals(networkAssets, SignalKind.LOG, ctx.windowStart(), ctx.windowEnd()).stream()
                .filter(s -> isEvent(s, "DNS_RECORD_REMOVED"))
                .toList();

        List<Finding> out = new ArrayList<>();
        for (ChangeHit hit : tools.changes(ctx)) {
            List<String> touched = hit.touchedAssetIds().stream().filter(networkAssets::contains).toList();
            if (touched.isEmpty()) {
                continue;
            }
            Node change = hit.change();
            String number = change.attribute("number");
            List<SignalEvent> removed = removals.stream()
                    .filter(s -> touched.contains(s.assetId()))
                    .sorted(comparing(SignalEvent::timestamp))
                    .toList();

            List<EvidencePointer> evidence = new ArrayList<>();
            evidence.add(EvidencePointer.ofChange(change));
            removed.stream().limit(3).map(EvidencePointer::of).forEach(evidence::add);

            if (removed.isEmpty()) {
                out.add(new Finding(NAME, Category.PROBABLE_CAUSE, touched.get(0),
                        "Change %s (\"%s\") modified %d network/DNS assets within %d hops of the alerts"
                                .formatted(number, change.getName(), touched.size(), hit.hops()),
                        0.55, evidence, change.getTimestamp(), null,
                        "Review change %s with its implementer.".formatted(number), null));
            } else {
                out.add(new Finding(NAME, Category.PROBABLE_CAUSE, touched.get(0),
                        "Change %s (\"%s\") modified %d DNS records; %d were removed by scavenging starting %s"
                                .formatted(number, change.getName(), touched.size(), removed.size(), removed.get(0).timestamp()),
                        0.9, evidence, removed.get(0).timestamp(), null,
                        "Raise a change to restore the %d removed A-records and exclude them from the scavenging policy, with %s as the rollback reference."
                                .formatted(removed.size(), number), null));
            }
        }
        return out;
    }

    private static boolean isEvent(SignalEvent signal, String event) {
        return event.equals(signal.attr("event"));
    }
}
