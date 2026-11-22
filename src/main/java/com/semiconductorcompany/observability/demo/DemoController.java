package com.semiconductorcompany.observability.demo;

import com.semiconductorcompany.observability.incident.Incident;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
@ConditionalOnProperty(name = "platform.demo.seed", havingValue = "true", matchIfMissing = true)
class DemoController {

    private final MalaysiaReplay replay;

    DemoController(MalaysiaReplay replay) {
        this.replay = replay;
    }

    /** Replays the reference incident and returns the correlated incident with its brief. */
    @PostMapping("/malaysia-replay")
    Incident malaysiaReplay() {
        return replay.run();
    }
}
