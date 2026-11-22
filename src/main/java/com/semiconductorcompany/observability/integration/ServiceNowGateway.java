package com.semiconductorcompany.observability.integration;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Stub of the ServiceNow Table API: incidents mapped to a CMDB CI, and change requests. Records, does not call out. */
@Component
public class ServiceNowGateway {

    private static final Logger log = LoggerFactory.getLogger(ServiceNowGateway.class);

    public record Ticket(String number, String table, String ciId, String description, String platformIncidentId, Instant createdAt) {
    }

    private final AtomicInteger incidentSeq = new AtomicInteger(10_000);
    private final AtomicInteger changeSeq = new AtomicInteger(60_000);
    private final List<Ticket> tickets = new CopyOnWriteArrayList<>();

    public String openIncident(String platformIncidentId, String ciId, String description) {
        return create("INC00" + incidentSeq.incrementAndGet(), "incident", ciId, description, platformIncidentId);
    }

    public String raiseChangeRequest(String ciId, String description, String platformIncidentId) {
        return create("CHG00" + changeSeq.incrementAndGet(), "change_request", ciId, description, platformIncidentId);
    }

    public List<Ticket> tickets() {
        return List.copyOf(tickets);
    }

    private String create(String number, String table, String ciId, String description, String platformIncidentId) {
        tickets.add(new Ticket(number, table, ciId, description, platformIncidentId, Instant.now()));
        log.info("ServiceNow: created {} {} on CI {}", table, number, ciId);
        return number;
    }
}
