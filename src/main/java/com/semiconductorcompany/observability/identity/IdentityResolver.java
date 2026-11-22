package com.semiconductorcompany.observability.identity;

import com.semiconductorcompany.observability.graph.KnowledgeGraph;
import com.semiconductorcompany.observability.graph.Node;
import com.semiconductorcompany.observability.graph.NodeType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Identity resolver (G-1): one canonical identity per asset across every tool.
 * Match order: OTel host.id / Azure VMUUID, then the tool's own id, then IP, then normalised name.
 * Every alias a tool uses is kept on the node.
 */
@Component
public class IdentityResolver {

    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Set<NodeType> RESOLVABLE =
            EnumSet.of(NodeType.HOST, NodeType.VM, NodeType.NETWORK_DEVICE, NodeType.CLUSTER);

    private final KnowledgeGraph graph;
    /** Lookup key ("host.id:...", "turbonomic:...", "ip:...", "name:...") to canonical node id. */
    private final Map<String, String> index = new ConcurrentHashMap<>();

    public IdentityResolver(KnowledgeGraph graph) {
        this.graph = graph;
    }

    /** Lower case, domain stripped: "SGP-ESX-PROD-11" and "sgp-esx-prod-11.sgp.corp.example.com" match. */
    public static String normalise(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (IPV4.matcher(n).matches()) {
            return n;
        }
        int dot = n.indexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    /** Finds the canonical asset for a tool record, creating it if no tool has reported it before. */
    public synchronized Node resolve(ToolRecord record) {
        Node node = lookup(record).orElseGet(() -> graph.upsert(newNode(record)));
        String toolKey = record.tool().key();
        node.alias(toolKey, record.externalId() != null ? record.externalId() : record.name());
        if (record.externalId() != null) {
            node.alias(toolKey + ".name", record.name());
        }
        node.attr("host.id", record.hostId());
        node.attr("ip", record.ip());
        register(node);
        return node;
    }

    public Optional<Node> lookup(ToolRecord record) {
        return Stream.of(
                        key("host.id", record.hostId()),
                        key(record.tool().key(), record.externalId()),
                        key("ip", record.ip()),
                        key("name", normalise(record.name())))
                .filter(Objects::nonNull)
                .map(index::get)
                .filter(Objects::nonNull)
                .findFirst()
                .flatMap(graph::node);
    }

    /** Looks an asset up by any identifier any tool uses for it, without creating anything. */
    public Optional<Node> lookup(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        Optional<Node> direct = graph.node(identifier);
        if (direct.isPresent()) {
            return direct;
        }
        List<String> keys = new ArrayList<>();
        keys.add(key("host.id", identifier));
        for (SourceTool tool : SourceTool.values()) {
            keys.add(key(tool.key(), identifier));
        }
        keys.add(key("ip", identifier));
        keys.add(key("name", normalise(identifier)));
        return keys.stream().map(index::get).filter(Objects::nonNull).findFirst().flatMap(graph::node);
    }

    /** Indexes every identifier a node carries, so later tool records resolve to it. */
    public void register(Node node) {
        put("name", normalise(node.getName()), node);
        put("host.id", node.attribute("host.id"), node);
        put("ip", node.attribute("ip"), node);
        node.getAliases().forEach((tool, value) -> {
            if (tool.endsWith(".name")) {
                put("name", normalise(value), node);
            } else {
                put(tool, value, node);
            }
        });
    }

    public IdentityCoverage coverage() {
        List<Node> assets = graph.nodes(null).stream().filter(n -> RESOLVABLE.contains(n.getType())).toList();
        long multiTool = assets.stream()
                .filter(n -> n.getAliases().keySet().stream().filter(k -> !k.endsWith(".name")).count() >= 2)
                .count();
        return new IdentityCoverage(assets.size(), multiTool, assets.isEmpty() ? 0 : (double) multiTool / assets.size());
    }

    private Node newNode(ToolRecord record) {
        NodeType type = record.assetType() != null ? record.assetType() : NodeType.HOST;
        String name = Stream.of(normalise(record.name()), record.externalId(), record.hostId())
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A tool record needs a name, external id or host.id"));
        return new Node(idFor(type, name), type, name);
    }

    public static String idFor(NodeType type, String name) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', '-') + ":" + name;
    }

    private void put(String prefix, String value, Node node) {
        String key = key(prefix, value);
        if (key != null) {
            index.putIfAbsent(key, node.getId());
        }
    }

    private static String key(String prefix, String value) {
        return value == null || value.isBlank() ? null : prefix + ":" + value.trim().toLowerCase(Locale.ROOT);
    }
}
