package com.semiconductorcompany.observability.ingest;

import com.semiconductorcompany.observability.graph.EdgeType;
import com.semiconductorcompany.observability.graph.KnowledgeGraph;
import com.semiconductorcompany.observability.graph.Node;
import com.semiconductorcompany.observability.identity.IdentityResolver;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CmdbIngestService {

    private final KnowledgeGraph graph;
    private final IdentityResolver resolver;

    public CmdbIngestService(KnowledgeGraph graph, IdentityResolver resolver) {
        this.graph = graph;
        this.resolver = resolver;
    }

    /** Applies nodes first, then edges, so items may reference each other in any order. */
    public int apply(List<CmdbItem> items) {
        for (CmdbItem item : items) {
            if (item.id() == null || item.type() == null || item.name() == null) {
                throw new IllegalArgumentException("CMDB items need id, type and name: " + item);
            }
            Node node = new Node(item.id(), item.type(), item.name());
            item.aliases().forEach(node::alias);
            item.attributes().forEach(node::attr);
            graph.upsert(node);
        }
        for (CmdbItem item : items) {
            if (item.zone() != null) {
                graph.connect(item.id(), EdgeType.IN_ZONE, item.zone());
            }
            if (item.owner() != null) {
                graph.connect(item.owner(), EdgeType.OWNS, item.id());
            }
            item.relations().forEach(r -> graph.connect(item.id(), r.type(), r.target(), r.validFrom(), r.validTo()));
            graph.node(item.id()).ifPresent(resolver::register);
        }
        return items.size();
    }
}
