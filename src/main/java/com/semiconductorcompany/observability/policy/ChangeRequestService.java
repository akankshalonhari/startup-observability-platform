package com.semiconductorcompany.observability.policy;

import com.semiconductorcompany.observability.agents.IncidentBrief;
import com.semiconductorcompany.observability.incident.IncidentService;
import com.semiconductorcompany.observability.integration.ServiceNowGateway;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

/** Turns a recommendation into a ServiceNow change request for a human to approve (I-2). Never executes it. */
@Service
public class ChangeRequestService {

    public record Command(String incidentId, Integer recommendationIndex, String assetId, String description, String requestedBy) {
    }

    public record ChangeRequest(
            String number,
            String incidentId,
            String assetId,
            String description,
            String rollbackPlan,
            ChangeLevel level,
            String status,
            String requestedBy,
            Instant raisedAt,
            String policyDecision) {
    }

    private final IncidentService incidents;
    private final ActionPolicy policy;
    private final ServiceNowGateway serviceNow;
    private final List<ChangeRequest> raised = new CopyOnWriteArrayList<>();

    public ChangeRequestService(IncidentService incidents, ActionPolicy policy, ServiceNowGateway serviceNow) {
        this.incidents = incidents;
        this.policy = policy;
        this.serviceNow = serviceNow;
    }

    public ChangeRequest raise(Command command) {
        String assetId = command.assetId();
        String description = command.description();
        if (command.incidentId() != null) {
            IncidentBrief brief = incidents.get(command.incidentId()).getBrief();
            if (brief == null) {
                throw new IllegalStateException("Incident " + command.incidentId() + " has no brief yet");
            }
            int index = Optional.ofNullable(command.recommendationIndex()).orElse(0);
            if (index < 0 || index >= brief.recommendations().size()) {
                throw new IllegalArgumentException("Brief has %d recommendations; index %d is out of range"
                        .formatted(brief.recommendations().size(), index));
            }
            IncidentBrief.Recommendation recommendation = brief.recommendations().get(index);
            assetId = recommendation.assetId();
            description = recommendation.text();
        }
        if (assetId == null || description == null) {
            throw new IllegalArgumentException("Give incidentId (and recommendationIndex), or assetId and description");
        }

        ActionPolicy.Decision decision = policy.mayRaiseChange(assetId);
        if (!decision.allowed()) {
            throw new PolicyViolationException(decision.reason());
        }
        String number = serviceNow.raiseChangeRequest(assetId, description, command.incidentId());
        ChangeRequest request = new ChangeRequest(number, command.incidentId(), assetId, description,
                "Revert to the pre-change configuration snapshot; implementer confirms service health afterwards",
                ChangeLevel.HUMAN_APPROVED_CHANGE, "PENDING_APPROVAL",
                Optional.ofNullable(command.requestedBy()).orElse("on-call"), Instant.now(), decision.reason());
        raised.add(request);
        return request;
    }

    public List<ChangeRequest> list() {
        return List.copyOf(raised);
    }
}
