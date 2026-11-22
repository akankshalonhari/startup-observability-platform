package com.semiconductorcompany.observability.agents.validation;

import com.semiconductorcompany.observability.agents.AgentContext;
import com.semiconductorcompany.observability.agents.Finding;
import com.semiconductorcompany.observability.agents.Finding.Category;
import com.semiconductorcompany.observability.config.PlatformProperties;
import com.semiconductorcompany.observability.graph.GraphReader;
import com.semiconductorcompany.observability.graph.Node;
import com.semiconductorcompany.observability.incident.Alert;
import com.semiconductorcompany.observability.telemetry.EvidencePointer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Gate 2, contradiction resolution via the graph. A candidate cause is demoted to context when:
 * it recovered while symptoms carried on (the Malaysia switch flap), it started after the first symptom,
 * or the graph has no dependency path from it to anything symptomatic.
 */
@Component
@Order(2)
public class ContradictionGate implements ValidationGate {

    public static final String NAME = "contradiction";

    private final GraphReader graph;
    private final PlatformProperties props;

    public ContradictionGate(GraphReader graph, PlatformProperties props) {
        this.graph = graph;
        this.props = props;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public GateResult apply(List<Finding> findings, AgentContext ctx) {
        List<Finding> symptoms = findings.stream().filter(f -> f.category() == Category.SYMPTOM).toList();
        Instant firstSymptom = Stream.concat(
                        symptoms.stream().flatMap(f -> f.evidence().stream()).map(EvidencePointer::from),
                        ctx.alerts().stream().map(Alert::firedAt))
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        Instant lastSymptom = symptoms.stream()
                .flatMap(f -> f.evidence().stream())
                .map(EvidencePointer::to)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        Set<String> symptomatic = new HashSet<>(ctx.alertedAssetIds());
        symptoms.forEach(f -> symptomatic.add(f.assetId()));

        List<Finding> kept = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (Finding finding : findings) {
            String contradiction = finding.category() == Category.PROBABLE_CAUSE
                    ? contradiction(finding, firstSymptom, lastSymptom, symptomatic, ctx.windowEnd())
                    : null;
            if (contradiction == null) {
                kept.add(finding);
            } else {
                kept.add(finding.withCategory(Category.CONTEXT, contradiction));
                notes.add("demoted [%s] %s: %s".formatted(finding.agent(), finding.assetId(), contradiction));
            }
        }
        if (notes.isEmpty()) {
            notes.add("no contradictions between candidate causes and symptoms");
        }
        return new GateResult(NAME, kept, List.of(), notes);
    }

    private String contradiction(Finding cause, Instant firstSymptom, Instant lastSymptom, Set<String> symptomatic, Instant at) {
        if (cause.causeEnd() != null && lastSymptom != null && lastSymptom.isAfter(cause.causeEnd().plus(props.contradictionGrace()))) {
            return "coincidental: recovered at %s but symptoms continued until %s".formatted(cause.causeEnd(), lastSymptom);
        }
        if (cause.causeStart() != null && firstSymptom != null && cause.causeStart().isAfter(firstSymptom)) {
            return "started at %s, after the first symptom at %s".formatted(cause.causeStart(), firstSymptom);
        }
        Set<String> downstream = graph.dependents(cause.assetId(), at).stream().map(Node::getId).collect(Collectors.toSet());
        downstream.add(cause.assetId());
        if (Collections.disjoint(downstream, symptomatic)) {
            return "graph shows no dependency path from %s to any symptomatic asset".formatted(cause.assetId());
        }
        return null;
    }
}
