package com.semiconductorcompany.observability.ingest;

import com.semiconductorcompany.observability.incident.IncidentService;
import com.semiconductorcompany.observability.incident.IngestedAlert;
import com.semiconductorcompany.observability.telemetry.SignalEvent;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Entry points for collectors, tool bridges, ServiceNow and the CMDB. */
@RestController
@RequestMapping("/api/ingest")
class IngestController {

    private final IncidentService incidents;
    private final SignalIngestService signals;
    private final ChangeIngestService changes;
    private final CmdbIngestService cmdb;

    IngestController(IncidentService incidents, SignalIngestService signals, ChangeIngestService changes, CmdbIngestService cmdb) {
        this.incidents = incidents;
        this.signals = signals;
        this.changes = changes;
        this.cmdb = cmdb;
    }

    @PostMapping("/alerts")
    IngestedAlert alert(@Valid @RequestBody AlertRequest request) {
        return incidents.ingest(request);
    }

    @PostMapping("/signals")
    SignalEvent signal(@Valid @RequestBody SignalRequest request) {
        return signals.ingest(request);
    }

    @PostMapping("/changes")
    ChangeIngestService.Result change(@Valid @RequestBody ChangeEvent event) {
        return changes.ingest(event);
    }

    @PostMapping("/cmdb")
    Map<String, Integer> cmdb(@RequestBody List<CmdbItem> items) {
        return Map.of("applied", cmdb.apply(items));
    }
}
