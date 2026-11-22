package com.semiconductorcompany.observability.aiobs;

/** Per-agent quality, cost and latency for the reliability team. */
public record AgentSummary(
        String agent,
        long runs,
        double avgLatencyMs,
        long toolCalls,
        long inputTokens,
        long outputTokens,
        double costUsd,
        long claims,
        long claimsWithValidEvidence,
        Double evidencePassRate) {
}
