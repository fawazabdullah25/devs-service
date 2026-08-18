package org.kstacks.devs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("devs.static-hls")
public record StaticHlsProperties(
    boolean enabled,
    URI baseUrl,
    String allowedPathPrefix,
    Duration validationTimeout
) {
    public StaticHlsProperties {
        if (baseUrl == null) baseUrl = URI.create("https://static-hls.invalid/");
        if (allowedPathPrefix == null) allowedPathPrefix = "";
        if (validationTimeout == null || validationTimeout.isNegative() || validationTimeout.isZero()) {
            validationTimeout = Duration.ofSeconds(10);
        }
    }
}
