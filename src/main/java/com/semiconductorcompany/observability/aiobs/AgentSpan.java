package com.semiconductorcompany.observability.aiobs;

import com.semiconductorcompany.observability.agents.ToolCall;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** One agent invocation: what O-1 requires every agent to emit (tool calls, tokens, latency, cost). */
public record AgentSpan(
        String traceId,
        String agent,
        String operation,
        Instant startedAt,
        long durationMs,
        List<ToolCall> toolCalls,
        int findings,
        String model,
        long inputTokens,
        long outputTokens,
        double costUsd) {

    /** The span as OpenTelemetry GenAI semantic-convention attributes, as it would be exported to Tempo. */
    public Map<String, Object> genAiAttributes() {
        return Map.of(
                "gen_ai.operation.name", operation,
                "gen_ai.agent.name", agent,
                "gen_ai.request.model", model,
                "gen_ai.usage.input_tokens", inputTokens,
                "gen_ai.usage.output_tokens", outputTokens);
    }
}
