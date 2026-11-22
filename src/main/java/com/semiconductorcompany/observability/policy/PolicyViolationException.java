package com.semiconductorcompany.observability.policy;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class PolicyViolationException extends ResponseStatusException {

    public PolicyViolationException(String reason) {
        super(HttpStatus.FORBIDDEN, reason);
    }
}
