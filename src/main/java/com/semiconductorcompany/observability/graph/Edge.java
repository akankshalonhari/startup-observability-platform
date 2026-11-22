package com.semiconductorcompany.observability.graph;

import java.time.Instant;

/** A directed, time-bounded edge, so the graph can answer what the estate looked like at incident time. */
public record Edge(String from, EdgeType type, String to, Instant validFrom, Instant validTo) {

    public boolean isValidAt(Instant at) {
        return (validFrom == null || !at.isBefore(validFrom)) && (validTo == null || at.isBefore(validTo));
    }

    public String other(String nodeId) {
        return from.equals(nodeId) ? to : from;
    }
}
