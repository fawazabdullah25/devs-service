package org.kstacks.devs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("devs.mux")
public record MuxProperties(
    boolean enabled,
    String tokenId,
    String tokenSecret,
    String webhookSecret,
    String playbackPolicy
) {}
