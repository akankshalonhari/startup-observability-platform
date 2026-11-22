package com.semiconductorcompany.observability.agents;

import com.semiconductorcompany.observability.graph.Node;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A domain specialist (A-2). These are deterministic rules today; a model-backed agent implements the
 * same interface and gets the same read-only tools, so the gates and audit trail do not change.
 */
public interface SpecialistAgent {

    String name();

    /** Findings about the incident; empty when nothing in scope belongs to this domain. */
    List<Finding> analyse(AgentContext ctx, ReadOnlyTools tools);

    static Set<String> ids(Collection<Node> nodes) {
        return nodes.stream().map(Node::getId).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
