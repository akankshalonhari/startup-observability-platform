package com.semiconductorcompany.observability.ingest;

import com.semiconductorcompany.observability.graph.EdgeType;
import com.semiconductorcompany.observability.graph.NodeType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** A CMDB configuration item and its relations (G-2, G-3). {@code zone} and {@code owner} are node ids. */
public record CmdbItem(
        String id,
        NodeType type,
        String name,
        String zone,
        String owner,
        Map<String, String> aliases,
        Map<String, String> attributes,
        List<Relation> relations) {

    public CmdbItem {
        aliases = aliases == null ? Map.of() : aliases;
        attributes = attributes == null ? Map.of() : attributes;
        relations = relations == null ? List.of() : relations;
    }

    public record Relation(EdgeType type, String target, Instant validFrom, Instant validTo) {

        public static Relation of(EdgeType type, String target) {
            return new Relation(type, target, null, null);
        }
    }
}
