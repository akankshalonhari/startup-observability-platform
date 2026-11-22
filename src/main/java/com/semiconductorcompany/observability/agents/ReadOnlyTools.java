package com.semiconductorcompany.observability.agents;

import com.semiconductorcompany.observability.graph.ChangeHit;
import com.semiconductorcompany.observability.graph.EdgeType;
import com.semiconductorcompany.observability.graph.GraphReader;
import com.semiconductorcompany.observability.graph.Node;
import com.semiconductorcompany.observability.graph.NodeType;
import com.semiconductorcompany.observability.telemetry.SignalEvent;
import com.semiconductorcompany.observability.telemetry.SignalKind;
import com.semiconductorcompany.observability.telemetry.TelemetryReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The only tools a specialist agent gets (A-2). It holds read-only views, so nothing reachable from here
 * can write to an asset, page anyone or touch ITSM. Every public method must be declared as a read tool in
 * resources/policy/agent-tools.json; OtObserveOnlyPolicyTest fails the build otherwise.
 */
public class ReadOnlyTools {

    private final String agent;
    private final GraphReader graph;
    private final TelemetryReader telemetry;
    private final Instant at;
    private final List<ToolCall> calls = Collections.synchronizedList(new ArrayList<>());

    public ReadOnlyTools(String agent, GraphReader graph, TelemetryReader telemetry, Instant at) {
        this.agent = agent;
        this.graph = graph;
        this.telemetry = telemetry;
        this.at = at;
    }

    public Optional<Node> node(String id) {
        return call("node", id, () -> graph.node(id));
    }

    public List<Node> nodesInScope(AgentContext ctx, NodeType... types) {
        Set<NodeType> wanted = types.length == 0 ? EnumSet.allOf(NodeType.class) : EnumSet.copyOf(Arrays.asList(types));
        return call("nodesInScope", wanted.toString(), () -> ctx.scope().keySet().stream()
                .map(graph::node)
                .flatMap(Optional::stream)
                .filter(n -> wanted.contains(n.getType()))
                .toList());
    }

    public List<Node> outgoing(String nodeId, EdgeType type) {
        return call("outgoing", nodeId + " " + type, () -> graph.outgoing(nodeId, type, at));
    }

    public List<SignalEvent> signals(Collection<String> assetIds, SignalKind kind, Instant from, Instant to) {
        return call("signals", "%s on %d assets %s..%s".formatted(kind, assetIds.size(), from, to),
                () -> telemetry.query(assetIds, kind, from, to));
    }

    public List<ChangeHit> changes(AgentContext ctx) {
        return call("changes", ctx.alertedAssetIds().toString(), ctx::changes);
    }

    List<ToolCall> calls() {
        return List.copyOf(calls);
    }

    private <T> T call(String tool, String arguments, Supplier<T> body) {
        long start = System.nanoTime();
        T result = body.get();
        int size = result instanceof Collection<?> c ? c.size() : result instanceof Optional<?> o ? (o.isPresent() ? 1 : 0) : 1;
        calls.add(new ToolCall(agent, tool, arguments, size, (System.nanoTime() - start) / 1_000));
        return result;
    }
}
