package com.semiconductorcompany.observability.agents.validation;

import com.semiconductorcompany.observability.agents.Finding;
import java.util.List;

public record GateResult(String gate, List<Finding> kept, List<DroppedClaim> dropped, List<String> notes) {
}
