package com.semiconductorcompany.observability.aiobs;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI observability (O-1): every agent run is recorded as a span and as Micrometer metrics, which
 * /actuator/prometheus exposes for Mimir. Every tool call is logged (NFR security).
 */
@Component
public class AgentTelemetry {

    private static final Logger log = LoggerFactory.getLogger(AgentTelemetry.class);
    private static final int MAX_SPANS = 500;

    private final MeterRegistry meters;
    private final Deque<AgentSpan> spans = new ConcurrentLinkedDeque<>();
    private final Map<String, Stats> stats = new ConcurrentHashMap<>();

    public AgentTelemetry(MeterRegistry meters) {
        this.meters = meters;
    }

    public void record(AgentSpan span) {
        spans.addFirst(span);
        while (spans.size() > MAX_SPANS) {
            spans.pollLast();
        }
        stats.computeIfAbsent(span.agent(), k -> new Stats()).add(span);
        meters.timer("genai.agent.duration", "agent", span.agent()).record(Duration.ofMillis(span.durationMs()));
        meters.counter("genai.agent.tokens", "agent", span.agent(), "type", "input").increment(span.inputTokens());
        meters.counter("genai.agent.tokens", "agent", span.agent(), "type", "output").increment(span.outputTokens());
        meters.counter("genai.agent.cost.usd", "agent", span.agent()).increment(span.costUsd());
        log.info("agent span {} tool calls {}", span.genAiAttributes(), span.toolCalls());
    }

    /** Evidence-gate outcome per agent: the "briefs whose cited evidence checks out" metric. */
    public void recordClaims(String agent, int produced, int passedEvidence) {
        stats.computeIfAbsent(agent, k -> new Stats()).claims(produced, passedEvidence);
        meters.counter("genai.agent.claims", "agent", agent, "evidence", "passed").increment(passedEvidence);
        meters.counter("genai.agent.claims", "agent", agent, "evidence", "failed").increment(produced - passedEvidence);
    }

    public List<AgentSpan> recent(int limit) {
        return spans.stream().limit(limit).toList();
    }

    public List<AgentSummary> summary() {
        return stats.entrySet().stream()
                .map(e -> e.getValue().summary(e.getKey()))
                .sorted(Comparator.comparing(AgentSummary::agent))
                .toList();
    }

    private static final class Stats {
        private long runs;
        private long durationMs;
        private long toolCalls;
        private long inputTokens;
        private long outputTokens;
        private double costUsd;
        private long claims;
        private long passed;

        synchronized void add(AgentSpan span) {
            runs++;
            durationMs += span.durationMs();
            toolCalls += span.toolCalls().size();
            inputTokens += span.inputTokens();
            outputTokens += span.outputTokens();
            costUsd += span.costUsd();
        }

        synchronized void claims(int produced, int passedEvidence) {
            claims += produced;
            passed += passedEvidence;
        }

        synchronized AgentSummary summary(String agent) {
            return new AgentSummary(agent, runs, runs == 0 ? 0 : (double) durationMs / runs, toolCalls,
                    inputTokens, outputTokens, costUsd, claims, passed, claims == 0 ? null : (double) passed / claims);
        }
    }
}
