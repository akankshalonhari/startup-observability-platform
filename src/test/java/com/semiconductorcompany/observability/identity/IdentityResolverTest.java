package com.semiconductorcompany.observability.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.semiconductorcompany.observability.graph.KnowledgeGraph;
import com.semiconductorcompany.observability.graph.Node;
import com.semiconductorcompany.observability.graph.NodeType;
import org.junit.jupiter.api.Test;

class IdentityResolverTest {

    private final KnowledgeGraph graph = new KnowledgeGraph();
    private final IdentityResolver resolver = new IdentityResolver(graph);

    @Test
    void normalisesTheThreeSpellings() {
        assertThat(IdentityResolver.normalise("SGP-ESX-PROD-11")).isEqualTo("sgp-esx-prod-11");
        assertThat(IdentityResolver.normalise("sgp-esx-prod-11.sgp.corp.example.com")).isEqualTo("sgp-esx-prod-11");
        assertThat(IdentityResolver.normalise("10.20.4.11")).isEqualTo("10.20.4.11");
    }

    @Test
    void fourToolsResolveToOneIdentity() {
        String vmuuid = "9C4E2B10-55AA-4F0E-8E21-6D7B3A9C1F11";
        Node turbonomic = resolver.resolve(new ToolRecord(SourceTool.TURBONOMIC, "turbo-pm-1a2b3c",
                "sgp-esx-prod-11.sgp.corp.example.com", "10.20.4.11", null, NodeType.HOST));
        Node azure = resolver.resolve(new ToolRecord(SourceTool.AZURE_MONITOR, vmuuid, "SGP-ESX-PROD-11", null, vmuuid, NodeType.HOST));
        // OTel host.id equals the Azure VMUUID: the join key, even in a different case.
        Node otel = resolver.resolve(new ToolRecord(SourceTool.OTEL, null, "sap-hana-prd", null, vmuuid.toLowerCase(), NodeType.HOST));
        // Different name, same IP.
        Node logicMonitor = resolver.resolve(new ToolRecord(SourceTool.LOGICMONITOR, "1182", "SGP-ESX-PROD-11-MGMT",
                "10.20.4.11", null, NodeType.HOST));

        assertThat(azure.getId()).isEqualTo(turbonomic.getId());
        assertThat(otel.getId()).isEqualTo(turbonomic.getId());
        assertThat(logicMonitor.getId()).isEqualTo(turbonomic.getId());
        assertThat(graph.nodes(NodeType.HOST)).hasSize(1);
        assertThat(turbonomic.getAliases()).containsKeys("turbonomic", "azure_monitor", "otel", "logicmonitor");
        assertThat(resolver.coverage().seenByTwoOrMoreTools()).isEqualTo(1);
    }

    @Test
    void lookupFindsAnAssetByAnyToolsIdentifier() {
        resolver.resolve(new ToolRecord(SourceTool.LOGICMONITOR, "4471", "sw-core-02", null, null, NodeType.NETWORK_DEVICE));

        assertThat(resolver.lookup("4471")).map(Node::getId).contains("network-device:sw-core-02");
        assertThat(resolver.lookup("SW-CORE-02.mypen.corp")).map(Node::getId).contains("network-device:sw-core-02");
        assertThat(resolver.lookup("nope")).isEmpty();
    }
}
