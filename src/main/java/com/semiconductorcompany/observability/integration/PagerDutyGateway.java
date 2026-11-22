package com.semiconductorcompany.observability.integration;

import com.semiconductorcompany.observability.graph.Node;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Stub of the PagerDuty Events API: records one page per correlated incident instead of calling out. */
@Component
public class PagerDutyGateway {

    private static final Logger log = LoggerFactory.getLogger(PagerDutyGateway.class);

    public record Page(String incidentId, String teamId, String rotation, String summary, Instant at) {
    }

    private final List<Page> pages = new CopyOnWriteArrayList<>();

    public Page page(Node team, String incidentId, String summary) {
        Page page = new Page(incidentId, team.getId(), team.attribute("rotation"), summary, Instant.now());
        pages.add(page);
        log.info("PagerDuty: paging rotation {} for {}", page.rotation(), incidentId);
        return page;
    }

    public List<Page> pages() {
        return List.copyOf(pages);
    }
}
