package com.semiconductorcompany.observability.ingest;

import com.semiconductorcompany.observability.graph.EdgeType;
import com.semiconductorcompany.observability.graph.KnowledgeGraph;
import com.semiconductorcompany.observability.graph.Node;
import com.semiconductorcompany.observability.graph.NodeType;
import com.semiconductorcompany.observability.identity.IdentityResolver;
import com.semiconductorcompany.observability.identity.SourceTool;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * ServiceNow change events feed the graph directly (G-2): the Malaysia root cause lived only in a change ticket.
 */
@Service
public class ChangeIngestService {

    private final KnowledgeGraph graph;
    private final IdentityResolver resolver;

    public ChangeIngestService(KnowledgeGraph graph, IdentityResolver resolver) {
        this.graph = graph;
        this.resolver = resolver;
    }

    public record Result(String changeId, List<String> linkedAssets, List<String> unresolvedCis) {
    }

    public Result ingest(ChangeEvent event) {
        String id = "change:" + event.number();
        graph.upsert(new Node(id, NodeType.CHANGE, event.summary(), event.implementedAt())
                .alias(SourceTool.SERVICENOW.key(), event.number())
                .attr("number", event.number()));
        List<String> linked = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (String ci : event.affectedCis()) {
            resolver.lookup(ci).ifPresentOrElse(asset -> {
                graph.connect(id, EdgeType.MODIFIED, asset.getId());
                linked.add(asset.getId());
            }, () -> unresolved.add(ci));
        }
        return new Result(id, linked, unresolved);
    }
}
