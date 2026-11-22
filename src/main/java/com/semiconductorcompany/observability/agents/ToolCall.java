package com.semiconductorcompany.observability.agents;

/** One read-only tool call by an agent, kept for audit. */
public record ToolCall(String agent, String tool, String arguments, int results, long micros) {
}
