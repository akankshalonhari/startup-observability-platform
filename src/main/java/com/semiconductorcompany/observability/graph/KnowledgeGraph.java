package com.semiconductorcompany.observability.graph;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * In-memory knowledge graph: the semantic layer. Which graph engine to use (Neptune, Memgraph, FalkorDB)
 * is an open question in the PRD, so this class is the seam a real engine would sit behind.
 */
@Component
public class KnowledgeGraph implements GraphReader {

    private final Map<String, Node> nodes = new ConcurrentHashMap<>();
    private final Map<String, List<Edge>> adjacency = new ConcurrentHashMap<>();

    /** Inserts a node, or merges its aliases and attributes into the existing one. */
    public Node upsert(Node node) {
        return nodes.merge(node.getId(), node, (existing, incoming) -> {
            existing.getAliases().putAll(incoming.getAliases());
            existing.getAttributes().putAll(incoming.getAttributes());
            return existing;
        });
    }

    public Edge connect(String from, EdgeType type, String to) {
        return connect(from, type, to, null, null);
    }

    public synchronized Edge connect(String from, EdgeType type, String to, Instant validFrom, Instant validTo) {
        if (!nodes.containsKey(from) || !nodes.containsKey(to)) {
            throw new IllegalArgumentException("Unknown node in edge %s -%s-> %s".formatted(from, type, to));
        }
        Edge edge = new Edge(from, type, to, validFrom, validTo);
        List<Edge> fromEdges = adjacency.computeIfAbsent(from, k -> new CopyOnWriteArrayList<>());
        if (fromEdges.contains(edge)) {
            return edge;
        }
        fromEdges.add(edge);
        adjacency.computeIfAbsent(to, k -> new CopyOnWriteArrayList<>()).add(edge);
        return edge;
    }

    public List<Edge> edges(String nodeId) {
        return adjacency.getOrDefault(nodeId, List.of());
    }

    @Override
    public Optional<Node> node(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(nodes.get(id));
    }

    @Override
    public List<Node> nodes(NodeType type) {
        return nodes.values().stream()
                .filter(n -> type == null || n.getType() == type)
                .sorted(Comparator.comparing(Node::getId))
                .toList();
    }

    @Override
    public List<Node> outgoing(String nodeId, EdgeType type, Instant at) {
        return edges(nodeId).stream()
                .filter(e -> e.type() == type && e.from().equals(nodeId) && e.isValidAt(at))
                .map(e -> nodes.get(e.to()))
                .toList();
    }

    @Override
    public List<Node> incoming(String nodeId, EdgeType type, Instant at) {
        return edges(nodeId).stream()
                .filter(e -> e.type() == type && e.to().equals(nodeId) && e.isValidAt(at))
                .map(e -> nodes.get(e.from()))
                .toList();
    }

    @Override
    public Map<String, Integer> neighbourhood(Collection<String> startIds, int maxHops, Instant at) {
        Map<String, Integer> distance = new LinkedHashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        for (String id : startIds) {
            if (nodes.containsKey(id) && distance.putIfAbsent(id, 0) == null) {
                queue.add(id);
            }
        }
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int hops = distance.get(current);
            if (hops == maxHops) {
                continue;
            }
            for (Edge edge : edges(current)) {
                if (!EdgeType.TOPOLOGY.contains(edge.type()) || !edge.isValidAt(at)) {
                    continue;
                }
                String next = edge.other(current);
                if (distance.putIfAbsent(next, hops + 1) == null) {
                    queue.add(next);
                }
            }
        }
        return distance;
    }

    @Override
    public List<Node> dependents(String assetId, Instant at) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(List.of(assetId));
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (Edge edge : edges(current)) {
                // An edge "a DEPENDS_ON b" points at what a needs, so walking it backwards finds dependents.
                if (EdgeType.TOPOLOGY.contains(edge.type()) && edge.to().equals(current) && edge.isValidAt(at)
                        && seen.add(edge.from())) {
                    queue.add(edge.from());
                }
            }
        }
        seen.remove(assetId);
        return seen.stream().map(nodes::get).toList();
    }

    @Override
    public ZoneTier zoneTier(String assetId, Instant at) {
        return outgoing(assetId, EdgeType.IN_ZONE, at).stream()
                .map(zone -> zone.attribute("tier"))
                .filter(tier -> tier != null)
                .map(ZoneTier::valueOf)
                .findFirst()
                .orElse(ZoneTier.L0);
    }

    @Override
    public Optional<Node> owner(String assetId, Instant at) {
        return incoming(assetId, EdgeType.OWNS, at).stream().filter(n -> n.getType() == NodeType.TEAM).findFirst();
    }

    @Override
    public List<ChangeHit> changesNear(Collection<String> assetIds, int hops, Duration lookback, Instant at) {
        Instant since = at.minus(lookback);
        Map<String, ChangeHit> hits = new LinkedHashMap<>();
        neighbourhood(assetIds, hops, at).forEach((assetId, distance) -> {
            for (Node change : incoming(assetId, EdgeType.MODIFIED, at)) {
                Instant when = change.getTimestamp();
                if (when != null && !when.isBefore(since) && !when.isAfter(at)) {
                    hits.merge(change.getId(), new ChangeHit(change, List.of(assetId), distance), ChangeHit::merge);
                }
            }
        });
        return hits.values().stream()
                .sorted(Comparator.comparingInt(ChangeHit::hops)
                        .thenComparing(h -> h.change().getTimestamp(), Comparator.reverseOrder()))
                .toList();
    }
}
