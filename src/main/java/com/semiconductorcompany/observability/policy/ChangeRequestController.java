package com.semiconductorcompany.observability.policy;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/change-requests")
class ChangeRequestController {

    private final ChangeRequestService changeRequests;

    ChangeRequestController(ChangeRequestService changeRequests) {
        this.changeRequests = changeRequests;
    }

    @PostMapping
    ChangeRequestService.ChangeRequest raise(@RequestBody ChangeRequestService.Command command) {
        return changeRequests.raise(command);
    }

    @GetMapping
    List<ChangeRequestService.ChangeRequest> list() {
        return changeRequests.list();
    }
}
