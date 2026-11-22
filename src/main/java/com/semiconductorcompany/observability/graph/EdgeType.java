package com.semiconductorcompany.observability.graph;

import java.util.EnumSet;
import java.util.Set;

/** v1 ontology edges. Every edge carries valid_from / valid_to (see {@link Edge}). */
public enum EdgeType {
    LOCATED_IN, IN_ZONE, RUNS_ON, MEMBER_OF, DEPLOYED_ON, DEPENDS_ON, RESOLVES_VIA, CONNECTS_VIA, ON,
    OWNS, MODIFIED, ABOUT, PART_OF;

    /**
     * Edges along which a failure can travel. Scoping and correlation walk only these, so that sharing a
     * site, zone or cluster does not pull every asset into every incident.
     */
    public static final Set<EdgeType> TOPOLOGY =
            EnumSet.of(RUNS_ON, DEPLOYED_ON, DEPENDS_ON, RESOLVES_VIA, CONNECTS_VIA, ON);
}
