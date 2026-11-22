package com.semiconductorcompany.observability.graph;

/** Zone tier carried by every asset (hard constraint 1c). L0 is OT and fab-adjacent: observe-only. */
public enum ZoneTier {
    L0, L1, L2, L3;

    public boolean isObserveOnly() {
        return this == L0;
    }
}
