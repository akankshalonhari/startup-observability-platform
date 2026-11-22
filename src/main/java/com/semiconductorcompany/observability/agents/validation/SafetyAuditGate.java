package com.semiconductorcompany.observability.agents.validation;

import com.semiconductorcompany.observability.agents.AgentContext;
import com.semiconductorcompany.observability.agents.Finding;
import com.semiconductorcompany.observability.graph.GraphReader;
import com.semiconductorcompany.observability.graph.ZoneTier;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Gate 3, safety audit: strips action strings from recommendations, and removes every recommendation on an
 * L0 (OT) asset. What is left is advice for a human, never a command for a machine.
 */
@Component
@Order(3)
public class SafetyAuditGate implements ValidationGate {

    public static final String NAME = "safety";

    static final Pattern ACTION = Pattern.compile(
            "\\b(restart|reboot|shut ?down|power ?off|kill|delete|drop|wipe|failover|re-?register|push (the )?config|systemctl|kubectl|ssh|sudo|rm -)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SENTENCE = Pattern.compile("(?<=[.;!?])\\s+");

    private final GraphReader graph;

    public SafetyAuditGate(GraphReader graph) {
        this.graph = graph;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public GateResult apply(List<Finding> findings, AgentContext ctx) {
        List<Finding> kept = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (Finding finding : findings) {
            if (finding.recommendation() == null) {
                kept.add(finding);
                continue;
            }
            ZoneTier tier = graph.zoneTier(finding.assetId(), ctx.windowEnd());
            if (tier.isObserveOnly()) {
                kept.add(finding.withRecommendation(null));
                notes.add("removed recommendation on %s: L0 is observe-only".formatted(finding.assetId()));
                continue;
            }
            List<String> safe = new ArrayList<>();
            List<String> stripped = new ArrayList<>();
            for (String sentence : SENTENCE.split(finding.recommendation().trim())) {
                (ACTION.matcher(sentence).find() ? stripped : safe).add(sentence);
            }
            if (!stripped.isEmpty()) {
                notes.add("stripped action string(s) from [%s]: %s".formatted(finding.agent(), stripped));
            }
            kept.add(finding.withRecommendation(safe.isEmpty() ? null : String.join(" ", safe)));
        }
        if (notes.isEmpty()) {
            notes.add("no action strings found");
        }
        return new GateResult(NAME, kept, List.of(), notes);
    }
}
