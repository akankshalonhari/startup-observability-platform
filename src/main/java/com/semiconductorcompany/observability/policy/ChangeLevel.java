package com.semiconductorcompany.observability.policy;

/**
 * The change-control ladder (hard constraint 3). Phases 1 to 3 never go past raising a change for a human;
 * standard changes open per action class, outside OT, only after a measured track record.
 */
public enum ChangeLevel {
    RECOMMEND_ONLY, STANDARD_CHANGE, HUMAN_APPROVED_CHANGE
}
