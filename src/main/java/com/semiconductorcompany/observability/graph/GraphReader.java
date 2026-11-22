package com.semiconductorcompany.observability.graph;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Read-only view of the knowledge graph (G-4). Agents and the console only ever get this. */
public interface GraphReader {

    Optional<Node> node(String id);

    /** All nodes of a type, or every node when {@code type} is null. */
    List<Node> nodes(NodeType type);

    List<Node> outgoing(String nodeId, EdgeType type, Instant at);

    List<Node> incoming(String nodeId, EdgeType type, Instant at);

    /** Node id to hop distance, walking topology edges valid at {@code at}. */
    Map<String, Integer> neighbourhood(Collection<String> startIds, int maxHops, Instant at);

    /** Everything that depends on the asset, directly or transitively: its blast radius. */
    List<Node> dependents(String assetId, Instant at);

    /** Zone tier of an asset. An asset with no zone is treated as L0, so policy fails closed. */
    ZoneTier zoneTier(String assetId, Instant at);

    Optional<Node> owner(String assetId, Instant at);

    /** The core question: what changed within {@code hops} of these assets in the lookback window? */
    List<ChangeHit> changesNear(Collection<String> assetIds, int hops, Duration lookback, Instant at);
}
