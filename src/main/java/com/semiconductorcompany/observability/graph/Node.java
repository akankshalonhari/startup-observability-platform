package com.semiconductorcompany.observability.graph;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A graph node. Assets keep one canonical id plus every alias each tool uses for them (G-1).
 * Telemetry is not stored here: attributes hold references (queries, IDs) into Mimir, Loki and Tempo.
 */
public class Node {

    private final String id;
    private final NodeType type;
    private final String name;
    /** When it happened, for CHANGE, ALERT and INCIDENT nodes. */
    private final Instant timestamp;
    /** Tool key (e.g. "turbonomic") to that tool's id; "tool.name" keys hold the tool's spelling of the name. */
    private final Map<String, String> aliases = new ConcurrentHashMap<>();
    private final Map<String, String> attributes = new ConcurrentHashMap<>();

    public Node(String id, NodeType type, String name) {
        this(id, type, name, null);
    }

    public Node(String id, NodeType type, String name, Instant timestamp) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.timestamp = timestamp;
    }

    public Node alias(String tool, String value) {
        if (tool != null && value != null) {
            aliases.put(tool, value);
        }
        return this;
    }

    public Node attr(String key, String value) {
        if (key != null && value != null) {
            attributes.put(key, value);
        }
        return this;
    }

    public String attribute(String key) {
        return attributes.get(key);
    }

    public String getId() {
        return id;
    }

    public NodeType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, String> getAliases() {
        return aliases;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }
}
