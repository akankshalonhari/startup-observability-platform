package com.semiconductorcompany.observability.agents;

import com.semiconductorcompany.observability.agents.Finding.Category;
import com.semiconductorcompany.observability.agents.IncidentBrief.Recommendation;
import com.semiconductorcompany.observability.agents.IncidentBrief.TimelineEntry;
import com.semiconductorcompany.observability.agents.validation.DroppedClaim;
import com.semiconductorcompany.observability.agents.validation.EvidenceGate;
import com.semiconductorcompany.observability.agents.validation.GateResult;
import com.semiconductorcompany.observability.agents.validation.ValidationGate;
import com.semiconductorcompany.observability.aiobs.AgentSpan;
import com.semiconductorcompany.observability.aiobs.AgentTelemetry;
import com.semiconductorcompany.observability.aiobs.PiiRedactor;
import com.semiconductorcompany.observability.config.PlatformProperties;
import com.semiconductorcompany.observability.graph.GraphReader;
import com.semiconductorcompany.observability.graph.Node;
import com.semiconductorcompany.observability.graph.NodeType;
import com.semiconductorcompany.observability.incident.Alert;
import com.semiconductorcompany.observability.incident.Incident;
import com.semiconductorcompany.observability.policy.ChangeLevel;
import com.semiconductorcompany.observability.telemetry.EvidencePointer;
import com.semiconductorcompany.observability.telemetry.SignalEvent;
import com.semiconductorcompany.observability.telemetry.TelemetryReader;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Supervisor (A-1): alert, then scope via the graph, fan out to specialists, validate, and write one brief.
 * It recommends; it never acts.
 */
@Service
public class SupervisorAgent {

    private static final Logger log = LoggerFactory.getLogger(SupervisorAgent.class);

    private record AgentRun(String agent, List<Finding> findings, List<ToolCall> toolCalls) {
    }

    private final GraphReader graph;
    private final TelemetryReader telemetry;
    private final List<SpecialistAgent> specialists;
    private final List<ValidationGate> gates;
    private final NarrativeWriter narrativeWriter;
    private final AgentTelemetry agentTelemetry;
    private final PiiRedactor redactor;
    private final PlatformProperties props;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SupervisorAgent(GraphReader graph, TelemetryReader telemetry, List<SpecialistAgent> specialists,
                           List<ValidationGate> gates, NarrativeWriter narrativeWriter, AgentTelemetry agentTelemetry,
                           PiiRedactor redactor, PlatformProperties props) {
        this.graph = graph;
        this.telemetry = telemetry;
        this.specialists = specialists;
        this.gates = gates;
        this.narrativeWriter = narrativeWriter;
        this.agentTelemetry = agentTelemetry;
        this.redactor = redactor;
        this.props = props;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    public IncidentBrief brief(Incident incident, List<Alert> alerts) {
        long started = System.nanoTime();
        Instant now = Instant.now();
        String traceId = UUID.randomUUID().toString().replace("-", "");

        // 1. Scope via the graph: what is near the alerts, and what changed there.
        Set<String> alerted = alerts.stream().map(Alert::assetId).collect(Collectors.toCollection(LinkedHashSet::new));
        AgentContext ctx = new AgentContext(incident, alerts,
                graph.neighbourhood(alerted, props.scopeHops(), now),
                graph.changesNear(alerted, props.scopeHops(), props.changeLookback(), now),
                incident.getOpenedAt().minus(props.evidenceLookback()), now);

        // 2. Fan out to the specialists in parallel.
        List<CompletableFuture<AgentRun>> futures = specialists.stream()
                .map(agent -> CompletableFuture.supplyAsync(() -> run(agent, ctx, traceId, now), executor))
                .toList();
        List<AgentRun> runs = futures.stream().map(CompletableFuture::join).toList();
        List<Finding> findings = runs.stream().flatMap(r -> r.findings().stream()).toList();

        // 3. Validation gates, in order: evidence, contradiction, safety.
        List<Finding> kept = findings;
        List<DroppedClaim> dropped = new ArrayList<>();
        List<String> gateLog = new ArrayList<>();
        for (ValidationGate gate : gates) {
            GateResult result = gate.apply(kept, ctx);
            kept = result.kept();
            dropped.addAll(result.dropped());
            result.notes().forEach(n -> gateLog.add(result.gate() + ": " + n));
            result.dropped().forEach(d -> gateLog.add(result.gate() + ": dropped [" + d.agent() + "] " + d.reason()));
        }
        recordEvidenceOutcome(runs, dropped);
        kept = kept.stream().map(f -> f.withText(redactor.redact(f.claim()), redactor.redact(f.recommendation()))).toList();

        // 4. One brief, routed to the owner of the probable cause.
        Finding cause = kept.stream()
                .filter(f -> f.category() == Category.PROBABLE_CAUSE)
                .max(Comparator.comparingDouble(Finding::confidence))
                .orElse(null);
        String routeAsset = cause != null ? cause.assetId() : alerts.get(0).assetId();
        Optional<Node> team = graph.owner(routeAsset, now);
        List<String> blastRadius = cause == null ? List.of() : graph.dependents(cause.assetId(), now).stream()
                .filter(n -> n.getType() == NodeType.SERVICE)
                .map(Node::getName)
                .toList();
        NarrativeWriter.Narrative narrative = narrativeWriter.write(
                new NarrativeWriter.Draft(alerts, cause, kept, blastRadius, team.map(Node::getName).orElse("unassigned")));

        long latencyMs = (System.nanoTime() - started) / 1_000_000;
        agentTelemetry.record(new AgentSpan(traceId, "supervisor", "brief", now, latencyMs, List.of(), kept.size(),
                narrative.modelVersion(), narrative.inputTokens(), narrative.outputTokens(), narrative.costUsd()));

        List<EvidencePointer> evidence = kept.stream().flatMap(f -> f.evidence().stream()).distinct().toList();
        List<ToolCall> toolCalls = runs.stream().flatMap(r -> r.toolCalls().stream()).toList();
        return new IncidentBrief(
                incident.getId(),
                now,
                redactor.redact(narrative.text()),
                cause == null ? null : new IncidentBrief.ProbableCause(cause.claim(), cause.assetId(), cause.agent(),
                        cause.confidence(), cause.evidence()),
                timeline(evidence),
                blastRadius,
                recommendations(kept, now),
                kept,
                dropped,
                team.map(Node::getId).orElse(null),
                new IncidentBrief.Audit(traceId, narrative.modelVersion(), List.of(narrative.prompt()), toolCalls,
                        evidence, gateLog, latencyMs));
    }

    private AgentRun run(SpecialistAgent agent, AgentContext ctx, String traceId, Instant now) {
        ReadOnlyTools tools = new ReadOnlyTools(agent.name(), graph, telemetry, now);
        long start = System.nanoTime();
        List<Finding> findings;
        try {
            findings = agent.analyse(ctx, tools);
        } catch (RuntimeException e) {
            log.warn("Agent {} failed; the brief goes out without it", agent.name(), e);
            findings = List.of();
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        agentTelemetry.record(new AgentSpan(traceId, agent.name(), "analyse", now, durationMs, tools.calls(),
                findings.size(), "rule-based", 0, 0, 0));
        return new AgentRun(agent.name(), findings, tools.calls());
    }

    private void recordEvidenceOutcome(List<AgentRun> runs, List<DroppedClaim> dropped) {
        Map<String, Long> failed = dropped.stream()
                .filter(d -> EvidenceGate.NAME.equals(d.gate()))
                .collect(Collectors.groupingBy(DroppedClaim::agent, Collectors.counting()));
        for (AgentRun run : runs) {
            int produced = run.findings().size();
            agentTelemetry.recordClaims(run.agent(), produced, produced - failed.getOrDefault(run.agent(), 0L).intValue());
        }
    }

    private List<TimelineEntry> timeline(List<EvidencePointer> evidence) {
        return evidence.stream()
                .map(p -> new TimelineEntry(p.from(), p.store(), p.ref(), describe(p)))
                .sorted(Comparator.comparing(TimelineEntry::at, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private String describe(EvidencePointer pointer) {
        if (EvidencePointer.SERVICENOW.equals(pointer.store())) {
            return graph.node(pointer.ref()).map(n -> n.attribute("number") + ": " + n.getName()).orElse(pointer.ref());
        }
        return telemetry.lookup(pointer).map(SignalEvent::message).orElse(pointer.ref());
    }

    private List<Recommendation> recommendations(List<Finding> findings, Instant now) {
        return findings.stream()
                .filter(f -> f.recommendation() != null)
                .sorted(Comparator.comparing((Finding f) -> f.category() != Category.PROBABLE_CAUSE)
                        .thenComparing(Finding::confidence, Comparator.reverseOrder()))
                .map(f -> new Recommendation(f.recommendation(), f.assetId(), graph.zoneTier(f.assetId(), now),
                        ChangeLevel.RECOMMEND_ONLY))
                .collect(Collectors.toMap(Recommendation::text, Function.identity(), (a, b) -> a, LinkedHashMap::new))
                .values().stream().toList();
    }
}
