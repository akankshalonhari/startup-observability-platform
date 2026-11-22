package com.semiconductorcompany.observability.identity;

import com.semiconductorcompany.observability.graph.Node;
import com.semiconductorcompany.observability.web.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/identity")
class IdentityController {

    private final IdentityResolver resolver;

    IdentityController(IdentityResolver resolver) {
        this.resolver = resolver;
    }

    @PostMapping("/resolve")
    Node resolve(@Valid @RequestBody ToolRecord record) {
        return resolver.resolve(record);
    }

    @GetMapping("/lookup")
    Node lookup(@RequestParam String identifier) {
        return resolver.lookup(identifier).orElseThrow(() -> new NotFoundException("Asset '" + identifier + "'"));
    }

    @GetMapping("/coverage")
    IdentityCoverage coverage() {
        return resolver.coverage();
    }
}
