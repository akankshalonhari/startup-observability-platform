package com.semiconductorcompany.observability.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("platform")
public record PlatformProperties(
        @DefaultValue("2") int scopeHops,
        @DefaultValue("7d") Duration changeLookback,
        @DefaultValue("2h") Duration evidenceLookback,
        @DefaultValue("3") int correlationHops,
        @DefaultValue("30m") Duration correlationWindow,
        @DefaultValue("2m") Duration triageHoldDown,
        @DefaultValue("5m") Duration contradictionGrace) {
}
