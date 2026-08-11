package org.kstacks.devs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("devs.media")
public record MediaProperties(long maxUploadBytes, long uploadExpiryMinutes) {}
