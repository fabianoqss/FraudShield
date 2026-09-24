package com.fraudetection.account_service.pix;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "pix")
public record PixProperties(
        @DefaultValue("5m") Duration lookupTtl,
        @DefaultValue("20") int lookupsPerMinute
) {
}
