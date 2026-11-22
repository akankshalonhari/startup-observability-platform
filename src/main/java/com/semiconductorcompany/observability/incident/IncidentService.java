package com.semiconductorcompany.observability.incident;

import com.semiconductorcompany.observability.agents.IncidentBrief;
import com.semiconductorcompany.observability.agents.SupervisorAgent;
import com.semiconductorcompany.observability.config.PlatformProperties;
import com.semiconductorcompany.observability.graph.EdgeType;
import com.semiconductorcompany.observability.graph.KnowledgeGraph;
import com.semiconductorcompany.observability.graph.Node;
import com.semiconductorcompany.observability.graph.NodeType;
import com.semiconductorcompany.observability.identity.IdentityResolver;
import com.semiconductorcompany.observability.identity.SourceTool;
import com.semiconductorcompany.observability.ingest.AlertRequest;
import com.semiconductorcompany.observability.integration.PagerDutyGateway;
import com.semiconductorcompany.observability.integration.ServiceNowGateway;
import com.semiconductorcompany.observability.web.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Incident flow (I-1, A-4 dedup and correlation). Alerts close in time and in the graph join one incident.
 * Once the storm settles, the supervisor writes the brief and exactly one rotation is paged: the owner of
 * the probable cause, not the owner of whichever alert fired first.
 */
@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final KnowledgeGraph graph;
    private final IdentityResolver resolver;
    private final SupervisorAgent supervisor;
    private final PagerDutyGateway pagerDuty;
    private final ServiceNowGateway serviceNow;
    private final PlatformProperties props;

    private final Map<String, Incident> incidents = new ConcurrentHashMap<>();
    private final Map<String, Alert> alerts = new ConcurrentHashMap<>();
    private final AtomicInteger alertSeq = new AtomicInteger();
    private final AtomicInteger incidentSeq = new AtomicInteger();

    public IncidentService(KnowledgeGraph graph, IdentityResolver resolver, SupervisorAgent supervisor,
                           PagerDutyGateway pagerDuty, ServiceNowGateway serviceNow, PlatformProperties props) {
        this.graph = graph;
        this.resolver = resolver;
        this.supervisor = supervisor;
        this.pagerDuty = pagerDuty;
        this.serviceNow = serviceNow;
        this.props = props;
    }

    public synchronized IngestedAlert ingest(AlertRequest request) {
        Node asset = resolver.resolve(request.toToolRecord());
        Instant firedAt = Optional.ofNullable(request.firedAt()).orElseGet(Instant::now);
        Optional<Incident> existing = correlate(asset.getId(), firedAt);
        if (existing.isPresent() && isDuplicate(existing.get(), request.sourceTool(), asset.getId(), request.title())) {
            existing.get().suppressDuplicate();
            return new IngestedAlert(null, existing.get().getId(), asset.getId(), true);
        }

        Alert alert = new Alert("ALR-%05d".formatted(alertSeq.incrementAndGet()), request.sourceTool(), asset.getId(),
                Optional.ofNullable(request.severity()).orElse("warning"), request.title(), firedAt);
        Incident incident = existing.orElseGet(() -> open(firedAt));
        alerts.put(alert.id(), alert);
        incident.add(alert);

        graph.upsert(new Node(alert.id(), NodeType.ALERT, alert.title(), firedAt)
                .attr("source", alert.source().key())
                .attr("severity", alert.severity()));
        graph.connect(alert.id(), EdgeType.ABOUT, asset.getId());
        graph.connect(alert.id(), EdgeType.PART_OF, incident.getId());
        return new IngestedAlert(alert, incident.getId(), asset.getId(), false);
    }

    /** Briefs incidents whose alert storm has gone quiet for the hold-down period. */
    @Scheduled(fixedDelayString = "${platform.triage-interval-ms:30000}")
    public void triageDue() {
        Instant quietSince = Instant.now().minus(props.triageHoldDown());
        incidents.values().stream()
                .filter(i -> i.getStatus() == Incident.Status.OPEN && i.getLastActivityAt().isBefore(quietSince))
                .map(Incident::getId)
                .toList()
                .forEach(this::triage);
    }

    public synchronized Incident triage(String incidentId) {
        Incident incident = get(incidentId);
        List<Alert> incidentAlerts = alertsOf(incident);
        IncidentBrief brief = supervisor.brief(incident, incidentAlerts);
        incident.briefed(brief);

        if (incident.getPagedTeam() == null) {
            Optional<Node> team = graph.node(brief.routedToTeam());
            if (team.isEmpty()) {
                log.warn("Incident {} has no owning team in the graph; not paging", incidentId);
                return incident;
            }
            pagerDuty.page(team.get(), incident.getId(), brief.summary());
            String ci = brief.probableCause() != null ? brief.probableCause().assetId() : incidentAlerts.get(0).assetId();
            incident.paged(team.get().getId(), serviceNow.openIncident(incident.getId(), ci, brief.summary()));
        }
        return incident;
    }

    public Incident get(String incidentId) {
        Incident incident = incidents.get(incidentId);
        if (incident == null) {
            throw new NotFoundException("Incident " + incidentId);
        }
        return incident;
    }

    public List<Incident> list() {
        return incidents.values().stream().sorted(Comparator.comparing(Incident::getOpenedAt).reversed()).toList();
    }

    public List<Alert> alertsOf(Incident incident) {
        return incident.getAlertIds().stream().map(alerts::get).toList();
    }

    private Optional<Incident> correlate(String assetId, Instant firedAt) {
        Duration window = props.correlationWindow();
        return incidents.values().stream()
                .filter(i -> !firedAt.isBefore(i.getOpenedAt().minus(window)) && !firedAt.isAfter(i.getLastAlertAt().plus(window)))
                .filter(i -> graph.neighbourhood(i.getAssetIds(), props.correlationHops(), firedAt).containsKey(assetId))
                .max(Comparator.comparing(Incident::getLastAlertAt));
    }

    private boolean isDuplicate(Incident incident, SourceTool tool, String assetId, String title) {
        return alertsOf(incident).stream().anyMatch(a ->
                a.source() == tool && a.assetId().equals(assetId) && a.title().equalsIgnoreCase(title));
    }

    private Incident open(Instant firedAt) {
        Incident incident = new Incident("INC-%05d".formatted(incidentSeq.incrementAndGet()), firedAt);
        incidents.put(incident.getId(), incident);
        graph.upsert(new Node(incident.getId(), NodeType.INCIDENT, "Correlated incident " + incident.getId(), firedAt));
        return incident;
    }
}
