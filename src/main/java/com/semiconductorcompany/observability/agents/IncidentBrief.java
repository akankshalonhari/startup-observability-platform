package com.semiconductorcompany.observability.agents;

import com.semiconductorcompany.observability.agents.validation.DroppedClaim;
import com.semiconductorcompany.observability.graph.ZoneTier;
import com.semiconductorcompany.observability.policy.ChangeLevel;
import com.semiconductorcompany.observability.telemetry.EvidencePointer;
import java.time.Instant;
import java.util.List;

/**
 * The one brief the on-call engineer gets: probable cause, evidence timeline, blast radius, triage steps.
 * The audit block stores prompts, tool calls, evidence pointers and model version (NFR auditability).
 */
public record IncidentBrief(
        String incidentId,
        Instant generatedAt,
        String summary,
        ProbableCause probableCause,
        List<TimelineEntry> timeline,
        List<String> blastRadius,
        List<Recommendation> recommendations,
        List<Finding> findings,
        List<DroppedClaim> droppedClaims,
        String routedToTeam,
        Audit audit) {

    public record ProbableCause(String claim, String assetId, String agent, double confidence, List<EvidencePointer> evidence) {
    }

    public record TimelineEntry(Instant at, String store, String ref, String description) {
    }

    public record Recommendation(String text, String assetId, ZoneTier zoneTier, ChangeLevel level) {
    }

    public record Audit(
            String traceId,
            String modelVersion,
            List<String> prompts,
            List<ToolCall> toolCalls,
            List<EvidencePointer> evidence,
            List<String> gateLog,
            long latencyMs) {
    }
}
