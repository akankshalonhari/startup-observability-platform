package com.semiconductorcompany.observability.agents;

import com.semiconductorcompany.observability.incident.Alert;
import java.util.List;

/** Writes the human-readable part of the brief. This is where an approved LLM plugs in. */
public interface NarrativeWriter {

    record Draft(List<Alert> alerts, Finding cause, List<Finding> findings, List<String> blastRadius, String routedTo) {
    }

    record Narrative(String text, String prompt, String modelVersion, long inputTokens, long outputTokens, double costUsd) {
    }

    Narrative write(Draft draft);
}
