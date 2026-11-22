package com.semiconductorcompany.observability.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.semiconductorcompany.observability.agents.Finding;
import com.semiconductorcompany.observability.agents.IncidentBrief;
import com.semiconductorcompany.observability.graph.ZoneTier;
import com.semiconductorcompany.observability.incident.Incident;
import com.semiconductorcompany.observability.integration.PagerDutyGateway;
import com.semiconductorcompany.observability.policy.ChangeLevel;
import com.semiconductorcompany.observability.policy.ChangeRequestService;
import com.semiconductorcompany.observability.policy.PolicyViolationException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Gate 2 of the roadmap: the Malaysia replay finds the DNS change. */
@SpringBootTest
class MalaysiaReplayTest {

    @Autowired
    MalaysiaReplay replay;
    @Autowired
    PagerDutyGateway pagerDuty;
    @Autowired
    ChangeRequestService changeRequests;

    @Test
    void replayFindsTheDnsChangeAndPagesOneRotation() {
        Incident incident = replay.run();
        IncidentBrief brief = incident.getBrief();

        // One correlated incident from three tools, with the repeat alert deduplicated.
        assertThat(incident.getAlertIds()).hasSize(4);
        assertThat(incident.getSuppressedDuplicates()).isEqualTo(1);

        // Probable cause is the change ticket, cited.
        assertThat(brief.probableCause().claim()).contains(MalaysiaReplay.CHANGE_NUMBER);
        assertThat(brief.probableCause().evidence()).anyMatch(p -> p.store().equals("servicenow"));
        assertThat(brief.blastRadius()).contains("HANA system replication PRD", "S/4HANA PRD");

        // The switch flap is kept as context, flagged as coincidental.
        assertThat(brief.findings()).anyMatch(f -> f.assetId().equals("interface:sw-core-02-gi1-0-24")
                && f.category() == Finding.Category.CONTEXT
                && f.note().startsWith("coincidental"));

        // One rotation paged, the one that owns the cause.
        assertThat(brief.routedToTeam()).isEqualTo("team:network");
        assertThat(pagerDuty.pages()).filteredOn(p -> p.incidentId().equals(incident.getId())).hasSize(1);

        // Safety gate stripped the restart and shutdown suggestions; everything is recommend-only.
        assertThat(brief.recommendations()).isNotEmpty()
                .allMatch(r -> r.level() == ChangeLevel.RECOMMEND_ONLY)
                .noneMatch(r -> r.text().toLowerCase().contains("restart") || r.text().toLowerCase().contains("shut down"));
        assertThat(brief.audit().toolCalls()).isNotEmpty();

        // A recommendation becomes a change request pending human approval, never an action.
        int dnsFix = IntStream.range(0, brief.recommendations().size())
                .filter(i -> brief.recommendations().get(i).text().contains("A-records"))
                .findFirst().orElseThrow();
        assertThat(brief.recommendations().get(dnsFix).zoneTier()).isEqualTo(ZoneTier.L3);
        ChangeRequestService.ChangeRequest cr = changeRequests.raise(
                new ChangeRequestService.Command(incident.getId(), dnsFix, null, null, "test"));
        assertThat(cr.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(cr.level()).isEqualTo(ChangeLevel.HUMAN_APPROVED_CHANGE);
    }

    @Test
    void changeRequestsAreRefusedForOtAssets() {
        assertThatThrownBy(() -> changeRequests.raise(
                new ChangeRequestService.Command(null, null, "host:mypen-fab-gw-01", "Reboot the gateway", "test")))
                .isInstanceOf(PolicyViolationException.class)
                .hasMessageContaining("L0");
    }
}
