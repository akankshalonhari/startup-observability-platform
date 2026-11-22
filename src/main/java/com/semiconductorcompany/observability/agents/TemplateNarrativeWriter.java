package com.semiconductorcompany.observability.agents;

import com.semiconductorcompany.observability.telemetry.EvidencePointer;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Deterministic stand-in for an LLM. It builds the prompt a model would receive, so the audit trail and
 * token accounting are real, then fills a template. Which AI platform is approved is an open question in the PRD.
 */
@Component
public class TemplateNarrativeWriter implements NarrativeWriter {

    static final String MODEL = "template-v1";

    @Override
    public Narrative write(Draft draft) {
        String prompt = "Write a three-sentence incident brief for the on-call engineer. "
                + "Use only the validated findings below and cite nothing else.\n"
                + draft.findings().stream()
                        .map(f -> "- [%s/%s] %s (evidence: %s)".formatted(f.agent(), f.category(), f.claim(),
                                f.evidence().stream().map(EvidencePointer::ref).collect(Collectors.joining(", "))))
                        .collect(Collectors.joining("\n"));
        String tools = draft.alerts().stream().map(a -> a.source().name()).distinct().sorted().collect(Collectors.joining(", "));
        String text = draft.cause() == null
                ? "%d alerts from %s were correlated into one incident, but no probable cause passed validation. Start from the evidence timeline. Routed to %s."
                        .formatted(draft.alerts().size(), tools, draft.routedTo())
                : "%d alerts from %s were correlated into one incident. Probable cause (%s agent, %.0f%% confidence): %s. Blast radius: %s. Routed to %s; every recommendation is recommend-only and goes through change control."
                        .formatted(draft.alerts().size(), tools, draft.cause().agent(), draft.cause().confidence() * 100,
                                draft.cause().claim(),
                                draft.blastRadius().isEmpty() ? "none found in the graph" : String.join(", ", draft.blastRadius()),
                                draft.routedTo());
        return new Narrative(text, prompt, MODEL, prompt.length() / 4, text.length() / 4, 0.0);
    }
}
