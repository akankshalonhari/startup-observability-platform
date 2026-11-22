package com.semiconductorcompany.observability.agents.validation;

import com.semiconductorcompany.observability.agents.AgentContext;
import com.semiconductorcompany.observability.agents.Finding;
import java.util.List;

/** One of the three validation gates (A-3). The supervisor runs them in {@code @Order}. */
public interface ValidationGate {

    String name();

    GateResult apply(List<Finding> findings, AgentContext ctx);
}
