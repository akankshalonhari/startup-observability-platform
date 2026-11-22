package com.semiconductorcompany.observability.graph;

import java.util.ArrayList;
import java.util.List;

/** A change that modified something within N hops of the alerted assets. */
public record ChangeHit(Node change, List<String> touchedAssetIds, int hops) {

    ChangeHit merge(ChangeHit other) {
        List<String> touched = new ArrayList<>(touchedAssetIds);
        other.touchedAssetIds.stream().filter(id -> !touched.contains(id)).forEach(touched::add);
        return new ChangeHit(change, List.copyOf(touched), Math.min(hops, other.hops));
    }
}
