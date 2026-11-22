package com.semiconductorcompany.observability.incident;

import com.semiconductorcompany.observability.agents.IncidentBrief;
import com.semiconductorcompany.observability.web.NotFoundException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
class IncidentController {

    private final IncidentService incidents;

    IncidentController(IncidentService incidents) {
        this.incidents = incidents;
    }

    @GetMapping
    List<Incident> list() {
        return incidents.list();
    }

    @GetMapping("/{id}")
    Incident get(@PathVariable String id) {
        return incidents.get(id);
    }

    @GetMapping("/{id}/alerts")
    List<Alert> alerts(@PathVariable String id) {
        return incidents.alertsOf(incidents.get(id));
    }

    @GetMapping("/{id}/brief")
    IncidentBrief brief(@PathVariable String id) {
        IncidentBrief brief = incidents.get(id).getBrief();
        if (brief == null) {
            throw new NotFoundException("Brief for " + id + " (not triaged yet; POST /triage)");
        }
        return brief;
    }

    /** Runs the supervisor now instead of waiting for the hold-down. */
    @PostMapping("/{id}/triage")
    Incident triage(@PathVariable String id) {
        return incidents.triage(id);
    }
}
