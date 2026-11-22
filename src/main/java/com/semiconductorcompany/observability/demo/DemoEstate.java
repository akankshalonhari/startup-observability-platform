package com.semiconductorcompany.observability.demo;

import static com.semiconductorcompany.observability.graph.EdgeType.CONNECTS_VIA;
import static com.semiconductorcompany.observability.graph.EdgeType.DEPENDS_ON;
import static com.semiconductorcompany.observability.graph.EdgeType.DEPLOYED_ON;
import static com.semiconductorcompany.observability.graph.EdgeType.LOCATED_IN;
import static com.semiconductorcompany.observability.graph.EdgeType.MEMBER_OF;
import static com.semiconductorcompany.observability.graph.EdgeType.ON;
import static com.semiconductorcompany.observability.graph.EdgeType.RESOLVES_VIA;
import static com.semiconductorcompany.observability.graph.EdgeType.RUNS_ON;

import com.semiconductorcompany.observability.graph.NodeType;
import com.semiconductorcompany.observability.identity.IdentityResolver;
import com.semiconductorcompany.observability.identity.SourceTool;
import com.semiconductorcompany.observability.identity.ToolRecord;
import com.semiconductorcompany.observability.ingest.CmdbIngestService;
import com.semiconductorcompany.observability.ingest.CmdbItem;
import com.semiconductorcompany.observability.ingest.CmdbItem.Relation;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Seeds the Penang estate behind the reference incident, plus the PRD's sgp-esx-prod-11 identity sample.
 * The failure path is S/4HANA, then HANA, then system replication, then 14 DNS A-records.
 */
@Component
@ConditionalOnProperty(name = "platform.demo.seed", havingValue = "true", matchIfMissing = true)
public class DemoEstate implements ApplicationRunner {

    static final String HANA_01_VMUUID = "4210a3f2-7c1e-4b8e-9d3a-1f6b2c9e0a11";
    static final String HANA_02_VMUUID = "4210b7c4-2d9f-4e1a-8b6c-3e7d5f1a2b22";
    static final String ESX_07_TURBONOMIC_UUID = "turbo-pm-7f3e9a";
    static final String SWITCH_LOGICMONITOR_ID = "4471";
    static final int DNS_RECORDS = 14;

    private final CmdbIngestService cmdb;
    private final IdentityResolver resolver;

    public DemoEstate(CmdbIngestService cmdb, IdentityResolver resolver) {
        this.cmdb = cmdb;
        this.resolver = resolver;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedMalaysia();
        seedIdentitySample();
    }

    static String dnsRecordId(int i) {
        return "dns-record:hana-repl-%02d".formatted(i);
    }

    private void seedMalaysia() {
        Instant vmMoved = Instant.now().minus(30, ChronoUnit.DAYS);
        List<CmdbItem> items = new ArrayList<>();

        items.add(item("site:mypen", NodeType.SITE, "Penang, Malaysia", null, null, Map.of(), Map.of("site.code", "MYPEN")));
        items.add(item("zone:mypen-l3", NodeType.ZONE, "MYPEN corporate IT", null, null, Map.of(), Map.of("tier", "L3")));
        items.add(item("zone:mypen-l2", NodeType.ZONE, "MYPEN network core", null, null, Map.of(), Map.of("tier", "L2")));
        items.add(item("zone:mypen-l0", NodeType.ZONE, "MYPEN fab OT", null, null, Map.of(), Map.of("tier", "L0")));

        items.add(item("team:network", NodeType.TEAM, "Network", null, null, Map.of(), Map.of("rotation", "network-oncall")));
        items.add(item("team:sap-basis", NodeType.TEAM, "SAP Basis", null, null, Map.of(), Map.of("rotation", "sap-basis-primary")));
        items.add(item("team:infra-platform", NodeType.TEAM, "Infra Platform", null, null, Map.of(), Map.of("rotation", "infra-platform-oncall")));
        items.add(item("team:ot-engineering", NodeType.TEAM, "OT Engineering", null, null, Map.of(), Map.of("rotation", "ot-fab-mypen")));

        // Network and DNS layer
        items.add(item("network-device:sw-core-02", NodeType.NETWORK_DEVICE, "sw-core-02", "zone:mypen-l2", "team:network",
                Map.of("logicmonitor", SWITCH_LOGICMONITOR_ID), Map.of("ip", "10.40.0.2", "vendor", "cisco"),
                Relation.of(LOCATED_IN, "site:mypen")));
        items.add(item("interface:sw-core-02-gi1-0-24", NodeType.INTERFACE, "sw-core-02 Gi1/0/24", "zone:mypen-l2", "team:network",
                Map.of(), Map.of(), Relation.of(ON, "network-device:sw-core-02")));
        items.add(item("interface:sw-core-02-gi1-0-31", NodeType.INTERFACE, "sw-core-02 Gi1/0/31", "zone:mypen-l2", "team:network",
                Map.of(), Map.of(), Relation.of(ON, "network-device:sw-core-02")));
        items.add(item("network-device:mypen-ibx-01", NodeType.NETWORK_DEVICE, "mypen-ibx-01", "zone:mypen-l2", "team:network",
                Map.of("infoblox", "ibx-grid-mypen-01"), Map.of("vendor", "infoblox"), Relation.of(LOCATED_IN, "site:mypen")));
        for (int i = 1; i <= DNS_RECORDS; i++) {
            items.add(item(dnsRecordId(i), NodeType.DNS_RECORD, "hana-repl-%02d.mypen.corp".formatted(i), "zone:mypen-l3", "team:network",
                    Map.of(), Map.of("record.type", "A", "address", "10.40.12." + (10 + i))));
        }

        // Compute: Host, VM and Service are separate nodes, not one "Server"
        items.add(item("cluster:mypen-esx-01", NodeType.CLUSTER, "mypen-esx-01", "zone:mypen-l3", "team:infra-platform",
                Map.of(), Map.of(), Relation.of(LOCATED_IN, "site:mypen")));
        items.add(item("host:mypen-esx-07", NodeType.HOST, "mypen-esx-07", "zone:mypen-l3", "team:infra-platform",
                Map.of("turbonomic", ESX_07_TURBONOMIC_UUID, "turbonomic.name", "mypen-esx-07.mypen.corp.example.com"),
                Map.of("ip", "10.40.8.7", "kind", "PhysicalMachine"),
                Relation.of(MEMBER_OF, "cluster:mypen-esx-01"), Relation.of(LOCATED_IN, "site:mypen"),
                Relation.of(CONNECTS_VIA, "interface:sw-core-02-gi1-0-24")));
        items.add(item("host:mypen-esx-08", NodeType.HOST, "mypen-esx-08", "zone:mypen-l3", "team:infra-platform",
                Map.of("turbonomic", "turbo-pm-8a41c2"), Map.of("ip", "10.40.8.8", "kind", "PhysicalMachine"),
                Relation.of(MEMBER_OF, "cluster:mypen-esx-01"), Relation.of(LOCATED_IN, "site:mypen")));
        items.add(item("vm:mypen-hana-01", NodeType.VM, "mypen-hana-01", "zone:mypen-l3", "team:infra-platform",
                Map.of("azure_monitor", HANA_01_VMUUID, "azure_monitor.name", "MYPEN-HANA-01"),
                Map.of("host.id", HANA_01_VMUUID, "ip", "10.40.10.11", "cloud", "azure-arc"),
                Relation.of(RUNS_ON, "host:mypen-esx-07")));
        // hana-02 moved hosts a month ago; the old edge is kept with its validity window.
        items.add(item("vm:mypen-hana-02", NodeType.VM, "mypen-hana-02", "zone:mypen-l3", "team:infra-platform",
                Map.of("azure_monitor", HANA_02_VMUUID, "azure_monitor.name", "MYPEN-HANA-02"),
                Map.of("host.id", HANA_02_VMUUID, "ip", "10.40.10.12", "cloud", "azure-arc"),
                new Relation(RUNS_ON, "host:mypen-esx-07", null, vmMoved),
                new Relation(RUNS_ON, "host:mypen-esx-08", vmMoved, null)));
        items.add(item("vm:mypen-s4app-01", NodeType.VM, "mypen-s4app-01", "zone:mypen-l3", "team:infra-platform",
                Map.of(), Map.of("ip", "10.40.10.21", "cloud", "azure-arc"), Relation.of(RUNS_ON, "host:mypen-esx-08")));

        items.add(item("service:s4hana-prd", NodeType.SERVICE, "S/4HANA PRD", "zone:mypen-l3", "team:sap-basis",
                Map.of(), Map.of("platform", "sap"),
                Relation.of(DEPLOYED_ON, "vm:mypen-s4app-01"), Relation.of(DEPENDS_ON, "service:hana-prd-db")));
        items.add(item("service:hana-prd-db", NodeType.SERVICE, "HANA PRD database", "zone:mypen-l3", "team:sap-basis",
                Map.of(), Map.of("platform", "sap"),
                Relation.of(DEPLOYED_ON, "vm:mypen-hana-01"), Relation.of(DEPENDS_ON, "service:hana-sysrep")));
        List<Relation> sysrep = new ArrayList<>(List.of(
                Relation.of(DEPLOYED_ON, "vm:mypen-hana-01"), Relation.of(DEPLOYED_ON, "vm:mypen-hana-02")));
        IntStream.rangeClosed(1, DNS_RECORDS).forEach(i -> sysrep.add(Relation.of(RESOLVES_VIA, dnsRecordId(i))));
        items.add(new CmdbItem("service:hana-sysrep", NodeType.SERVICE, "HANA system replication PRD", "zone:mypen-l3",
                "team:sap-basis", Map.of(), Map.of("platform", "sap"), sysrep));

        // OT: in scope for observation, never for action
        items.add(item("host:mypen-fab-gw-01", NodeType.HOST, "mypen-fab-gw-01", "zone:mypen-l0", "team:ot-engineering",
                Map.of(), Map.of("ip", "10.60.0.5", "role", "OPC-UA gateway"),
                Relation.of(CONNECTS_VIA, "interface:sw-core-02-gi1-0-31"), Relation.of(LOCATED_IN, "site:mypen")));

        cmdb.apply(items);
    }

    /** The PRD's sample: one machine, four tools, three spellings. They must resolve to one identity. */
    private void seedIdentitySample() {
        String vmuuid = "9C4E2B10-55AA-4F0E-8E21-6D7B3A9C1F11";
        resolver.resolve(new ToolRecord(SourceTool.TURBONOMIC, "turbo-pm-1a2b3c", "sgp-esx-prod-11.sgp.corp.example.com",
                "10.20.4.11", null, NodeType.HOST));
        resolver.resolve(new ToolRecord(SourceTool.AZURE_MONITOR, vmuuid, "SGP-ESX-PROD-11", null, vmuuid, NodeType.HOST));
        resolver.resolve(new ToolRecord(SourceTool.OTEL, null, "sgp-esx-prod-11", null, vmuuid.toLowerCase(), NodeType.HOST));
        resolver.resolve(new ToolRecord(SourceTool.LOGICMONITOR, "1182", "SGP-ESX-PROD-11-MGMT", "10.20.4.11", null, NodeType.HOST));
    }

    private static CmdbItem item(String id, NodeType type, String name, String zone, String owner,
                                 Map<String, String> aliases, Map<String, String> attributes, Relation... relations) {
        return new CmdbItem(id, type, name, zone, owner, aliases, attributes, List.of(relations));
    }
}
