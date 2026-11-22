package com.semiconductorcompany.observability.agents;

import com.semiconductorcompany.observability.telemetry.EvidencePointer;
import java.time.Instant;
import java.util.List;

/**
 * One claim from a specialist. A probable cause carries the window it was active in, so the contradiction
 * gate can test it against the symptoms.
 */
public record Finding(
        String agent,
        Category category,
        String assetId,
        String claim,
        double confidence,
        List<EvidencePointer> evidence,
        Instant causeStart,
        Instant causeEnd,
        String recommendation,
        String note) {

    public enum Category { PROBABLE_CAUSE, SYMPTOM, CONTEXT }

    public Finding {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public static Finding symptom(String agent, String assetId, String claim, double confidence, List<EvidencePointer> evidence) {
        return new Finding(agent, Category.SYMPTOM, assetId, claim, confidence, evidence, null, null, null, null);
    }

    public Finding withCategory(Category newCategory, String newNote) {
        return new Finding(agent, newCategory, assetId, claim, confidence, evidence, causeStart, causeEnd, recommendation, newNote);
    }

    public Finding withRecommendation(String newRecommendation) {
        return new Finding(agent, category, assetId, claim, confidence, evidence, causeStart, causeEnd, newRecommendation, note);
    }

    public Finding withText(String newClaim, String newRecommendation) {
        return new Finding(agent, category, assetId, newClaim, confidence, evidence, causeStart, causeEnd, newRecommendation, note);
    }
}
