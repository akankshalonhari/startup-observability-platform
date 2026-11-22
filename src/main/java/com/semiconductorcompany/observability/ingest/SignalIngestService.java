package com.semiconductorcompany.observability.ingest;

import com.semiconductorcompany.observability.graph.Node;
import com.semiconductorcompany.observability.graph.NodeType;
import com.semiconductorcompany.observability.identity.IdentityResolver;
import com.semiconductorcompany.observability.identity.SourceTool;
import com.semiconductorcompany.observability.identity.ToolRecord;
import com.semiconductorcompany.observability.telemetry.SignalEvent;
import com.semiconductorcompany.observability.telemetry.SignalKind;
import com.semiconductorcompany.observability.telemetry.TelemetryStore;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Attaches collector signals to canonical assets using the OTel resource attributes, then stores them. */
@Service
public class SignalIngestService {

    private final IdentityResolver resolver;
    private final TelemetryStore telemetry;

    public SignalIngestService(IdentityResolver resolver, TelemetryStore telemetry) {
        this.resolver = resolver;
        this.telemetry = telemetry;
    }

    public SignalEvent ingest(SignalRequest request) {
        ResourceAttributes resource = request.resource();
        String assetId = resolver.lookup(resource.serviceName())
                .filter(n -> n.getType() == NodeType.SERVICE)
                .map(Node::getId)
                .orElseGet(() -> resolveHost(resource).getId());
        if (request.kind() != SignalKind.LOG && (request.ref() == null || request.ref().isBlank())) {
            throw new IllegalArgumentException("Metrics need their PromQL and traces their trace id in 'ref'");
        }
        String id = "sig-" + UUID.randomUUID().toString().substring(0, 12);
        String ref = request.kind() == SignalKind.LOG ? id : request.ref();
        SignalEvent event = new SignalEvent(id, request.kind(), assetId,
                Optional.ofNullable(request.timestamp()).orElseGet(Instant::now), ref, request.message(), request.attributes());
        telemetry.record(event);
        return event;
    }

    private Node resolveHost(ResourceAttributes resource) {
        if (resource.hostId() == null && resource.hostName() == null) {
            throw new IllegalArgumentException("Resource needs host.id or host.name");
        }
        Node host = resolver.resolve(new ToolRecord(SourceTool.OTEL, resource.hostId(), resource.hostName(), null,
                resource.hostId(), NodeType.HOST));
        host.attr("site.code", resource.siteCode());
        host.attr("deployment.environment", resource.deploymentEnvironment());
        return host;
    }
}
