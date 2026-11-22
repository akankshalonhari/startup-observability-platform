package com.semiconductorcompany.observability.agents.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.semiconductorcompany.observability.agents.AgentContext;
import com.semiconductorcompany.observability.agents.Finding;
import com.semiconductorcompany.observability.graph.EdgeType;
import com.semiconductorcompany.observability.graph.KnowledgeGraph;
import com.semiconductorcompany.observability.graph.Node;
import com.semiconductorcompany.observability.graph.NodeType;
import com.semiconductorcompany.observability.telemetry.EvidencePointer;
import com.semiconductorcompany.observability.telemetry.InMemoryTelemetryStore;
import com.semiconductorcompany.observability.telemetry.SignalEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ValidationGatesTest {

    private final KnowledgeGraph graph = new KnowledgeGraph();
    private final InMemoryTelemetryStore telemetry = new InMemoryTelemetryStore();
    private final Instant now = Instant.now();
    private final AgentContext ctx = new AgentContext(null, List.of(), Map.of(), List.of(), now.minusSeconds(3600), now);

    @BeforeEach
    void estate() {
        graph.upsert(new Node("zone:it", NodeType.ZONE, "IT").attr("tier", "L3"));
        graph.upsert(new Node("zone:ot", NodeType.ZONE, "OT").attr("tier", "L0"));
        graph.upsert(new Node("host:app", NodeType.HOST, "app"));
        graph.upsert(new Node("host:plc-gw", NodeType.HOST, "plc-gw"));
        graph.connect("host:app", EdgeType.IN_ZONE, "zone:it");
        graph.connect("host:plc-gw", EdgeType.IN_ZONE, "zone:ot");
    }

    @Test
    void evidenceGateDropsUncitedAndUnresolvableClaims() {
        SignalEvent log = SignalEvent.log("host:app", now.minusSeconds(60), "boom", Map.of("level", "ERROR"));
        telemetry.record(log);
        Finding cited = Finding.symptom("logs", "host:app", "errors on app", 0.7, List.of(EvidencePointer.of(log)));
        Finding uncited = Finding.symptom("logs", "host:app", "probably memory", 0.4, List.of());
        Finding invented = Finding.symptom("logs", "host:app", "made-up log", 0.9,
                List.of(new EvidencePointer(EvidencePointer.LOKI, "log-does-not-exist", now, now)));

        GateResult result = new EvidenceGate(graph, telemetry).apply(List.of(cited, uncited, invented), ctx);

        assertThat(result.kept()).containsExactly(cited);
        assertThat(result.dropped()).extracting(DroppedClaim::reason)
                .anyMatch(r -> r.equals("uncited claim"))
                .anyMatch(r -> r.startsWith("evidence does not resolve"));
    }

    @Test
    void safetyGateStripsActionStrings() {
        Finding finding = new Finding("sap", Finding.Category.SYMPTOM, "host:app", "replication down", 0.8, List.of(),
                null, null, "Check that the endpoints resolve. Restart hdbnameserver if it does not recover.", null);

        GateResult result = new SafetyAuditGate(graph).apply(List.of(finding), ctx);

        assertThat(result.kept().get(0).recommendation()).isEqualTo("Check that the endpoints resolve.");
    }

    @Test
    void safetyGateRemovesEveryRecommendationOnL0() {
        Finding finding = new Finding("infra", Finding.Category.SYMPTOM, "host:plc-gw", "gateway slow", 0.6, List.of(),
                null, null, "Check the gateway's uplink.", null);

        GateResult result = new SafetyAuditGate(graph).apply(List.of(finding), ctx);

        assertThat(result.kept().get(0).recommendation()).isNull();
        assertThat(result.notes()).anyMatch(n -> n.contains("L0 is observe-only"));
    }

    @Test
    void assetsWithNoZoneAreTreatedAsL0() {
        graph.upsert(new Node("host:unknown", NodeType.HOST, "unknown"));
        assertThat(graph.zoneTier("host:unknown", now).isObserveOnly()).isTrue();
    }
}
