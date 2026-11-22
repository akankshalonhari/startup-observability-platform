package com.semiconductorcompany.observability.demo;

import static com.semiconductorcompany.observability.demo.DemoEstate.DNS_RECORDS;
import static com.semiconductorcompany.observability.demo.DemoEstate.dnsRecordId;

import com.semiconductorcompany.observability.graph.NodeType;
import com.semiconductorcompany.observability.identity.SourceTool;
import com.semiconductorcompany.observability.incident.Incident;
import com.semiconductorcompany.observability.incident.IncidentService;
import com.semiconductorcompany.observability.incident.IngestedAlert;
import com.semiconductorcompany.observability.ingest.AlertRequest;
import com.semiconductorcompany.observability.ingest.ChangeEvent;
import com.semiconductorcompany.observability.ingest.ChangeIngestService;
import com.semiconductorcompany.observability.telemetry.SignalEvent;
import com.semiconductorcompany.observability.telemetry.TelemetryStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

/**
 * Replays "the Malaysia Tuesday" against the seeded estate. Gate 2 of the roadmap: the replay must find the
 * DNS change. t0 stands in for 02:14, the moment of the coincidental switch flap.
 */
@Service
public class MalaysiaReplay {

    static final String CHANGE_NUMBER = "CHG0049217";

    private final ChangeIngestService changes;
    private final TelemetryStore telemetry;
    private final IncidentService incidents;

    public MalaysiaReplay(ChangeIngestService changes, TelemetryStore telemetry, IncidentService incidents) {
        this.changes = changes;
        this.telemetry = telemetry;
        this.incidents = incidents;
    }

    public Incident run() {
        Instant t0 = Instant.now().minus(40, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);

        // The routine change, which until now lived only in ServiceNow (G-2).
        List<String> cis = new ArrayList<>(IntStream.rangeClosed(1, DNS_RECORDS)
                .mapToObj(i -> "hana-repl-%02d.mypen.corp".formatted(i)).toList());
        cis.add("ibx-grid-mypen-01");
        changes.ingest(new ChangeEvent(CHANGE_NUMBER, "Update Infoblox DNS scavenging policy for mypen.corp",
                t0.minus(8, ChronoUnit.HOURS), cis));

        // 02:05: the next scavenging run ages out the 14 HANA replication A-records.
        for (int i = 1; i <= DNS_RECORDS; i++) {
            telemetry.record(SignalEvent.log(dnsRecordId(i), t0.minus(9, ChronoUnit.MINUTES).plusSeconds(i * 2L),
                    "Scavenging removed stale A record hana-repl-%02d.mypen.corp".formatted(i),
                    Map.of("event", "DNS_RECORD_REMOVED", "level", "INFO", "source", "infoblox")));
        }

        // 02:14: the switch flap. Real, brief, and not the cause.
        String port = "interface:sw-core-02-gi1-0-24";
        telemetry.record(SignalEvent.log(port, t0, "Gi1/0/24 changed state to down",
                Map.of("event", "LINK_DOWN", "level", "WARN", "source", "logicmonitor")));
        telemetry.record(SignalEvent.log(port, t0.plusSeconds(40), "Gi1/0/24 changed state to up",
                Map.of("event", "LINK_UP", "level", "WARN", "source", "logicmonitor")));

        // From 02:19, HANA cannot resolve its replication peers, and keeps failing.
        int n = 0;
        for (Instant ts = t0.plus(5, ChronoUnit.MINUTES); ts.isBefore(Instant.now().minus(1, ChronoUnit.MINUTES)); ts = ts.plusSeconds(60)) {
            telemetry.record(SignalEvent.log("vm:mypen-hana-01", ts,
                    "sysrep: cannot resolve hana-repl-%02d.mypen.corp: Name or service not known".formatted(n++ % DNS_RECORDS + 1),
                    Map.of("level", "ERROR", "exception", "UnknownHostException", "component", "hana")));
        }
        telemetry.record(SignalEvent.metric("service:hana-sysrep", t0.plus(6, ChronoUnit.MINUTES),
                "hana_sysrep_status{sid=\"PRD\",site=\"MYPEN\"}", "System replication status ERROR",
                Map.of("metric", "hana_sysrep_status", "value", "ERROR")));
        telemetry.record(SignalEvent.trace("service:s4hana-prd", t0.plus(7, ChronoUnit.MINUTES),
                "4bf92f3577b34da6a3ce929d0e0e4736", "POST /sap/opu/odata/FI_POSTING into HANA: 5.2 s, error",
                Map.of("error", "true")));
        telemetry.record(SignalEvent.metric("host:mypen-esx-07", t0.plus(8, ChronoUnit.MINUTES),
                "turbonomic_vm_response_time_risk{host=\"mypen-esx-07\"}",
                "response-time risk on 2 VMs; CPU 41%, memory 63%, no capacity saturation",
                Map.of("source", "turbonomic")));
        telemetry.record(SignalEvent.metric("vm:mypen-hana-02", t0.plus(9, ChronoUnit.MINUTES),
                "azure_vm_network_in_bytes{vm=\"MYPEN-HANA-02\"}",
                "inbound replication traffic dropped to 0 B/s; VM heartbeat healthy",
                Map.of("source", "azure-monitor")));

        // The alert storm, each in its own tool's identifiers. It used to page three rotations.
        IngestedAlert first = incidents.ingest(new AlertRequest(SourceTool.LOGICMONITOR, DemoEstate.SWITCH_LOGICMONITOR_ID,
                "sw-core-02", null, null, NodeType.NETWORK_DEVICE, "Interface Gi1/0/24 operational status down", "warning",
                t0.plusSeconds(20)));
        incidents.ingest(new AlertRequest(SourceTool.AZURE_MONITOR, DemoEstate.HANA_01_VMUUID, "MYPEN-HANA-01", null,
                DemoEstate.HANA_01_VMUUID, NodeType.VM, "HANA system replication status ERROR", "critical",
                t0.plus(6, ChronoUnit.MINUTES)));
        incidents.ingest(new AlertRequest(SourceTool.TURBONOMIC, DemoEstate.ESX_07_TURBONOMIC_UUID,
                "mypen-esx-07.mypen.corp.example.com", null, null, NodeType.HOST, "VM response time risk", "major",
                t0.plus(8, ChronoUnit.MINUTES)));
        incidents.ingest(new AlertRequest(SourceTool.AZURE_MONITOR, DemoEstate.HANA_02_VMUUID, "MYPEN-HANA-02", null,
                DemoEstate.HANA_02_VMUUID, NodeType.VM, "Network in dropped to zero", "critical",
                t0.plus(9, ChronoUnit.MINUTES)));
        // Azure Monitor fires the hana-01 alert again: deduplicated.
        incidents.ingest(new AlertRequest(SourceTool.AZURE_MONITOR, DemoEstate.HANA_01_VMUUID, "MYPEN-HANA-01", null,
                DemoEstate.HANA_01_VMUUID, NodeType.VM, "HANA system replication status ERROR", "critical",
                t0.plus(11, ChronoUnit.MINUTES)));

        return incidents.triage(first.incidentId());
    }
}
