package com.semiconductorcompany.observability.agents.validation;

import com.semiconductorcompany.observability.agents.Finding;

/** A claim a gate removed before it reached a human, and why. */
public record DroppedClaim(String gate, String agent, String claim, String reason) {

    static DroppedClaim of(String gate, Finding finding, String reason) {
        return new DroppedClaim(gate, finding.agent(), finding.claim(), reason);
    }
}
