package com.semiconductorcompany.observability.aiobs;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-observability")
class AiObservabilityController {

    private final AgentTelemetry telemetry;

    AiObservabilityController(AgentTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @GetMapping("/agents")
    List<AgentSummary> agents() {
        return telemetry.summary();
    }

    @GetMapping("/spans")
    List<AgentSpan> spans(@RequestParam(defaultValue = "50") int limit) {
        return telemetry.recent(limit);
    }
}
