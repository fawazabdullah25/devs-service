package org.kstacks.devs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("devs.r2")
public record R2Properties(
    boolean enabled,
    String endpoint,
    String region,
    String bucket,
    String accessKeyId,
    String secretAccessKey
) {}
