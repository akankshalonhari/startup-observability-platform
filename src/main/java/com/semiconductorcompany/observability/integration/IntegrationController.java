package com.semiconductorcompany.observability.integration;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Shows what the stubbed PagerDuty and ServiceNow integrations would have sent. */
@RestController
@RequestMapping("/api/integrations")
class IntegrationController {

    private final PagerDutyGateway pagerDuty;
    private final ServiceNowGateway serviceNow;

    IntegrationController(PagerDutyGateway pagerDuty, ServiceNowGateway serviceNow) {
        this.pagerDuty = pagerDuty;
        this.serviceNow = serviceNow;
    }

    @GetMapping("/pagerduty/pages")
    List<PagerDutyGateway.Page> pages() {
        return pagerDuty.pages();
    }

    @GetMapping("/servicenow/tickets")
    List<ServiceNowGateway.Ticket> tickets() {
        return serviceNow.tickets();
    }
}
