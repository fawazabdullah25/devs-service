package org.kstacks.devs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("devs.attachments")
public record AttachmentProperties(
    URI publicBaseUrl,
    long maxUploadBytes,
    int maxPerUnit,
    Duration retention,
    Duration purgeDelay,
    Duration staleUploadAfter
) {
    public AttachmentProperties {
        if (maxUploadBytes < 1) throw new IllegalArgumentException("Attachment upload limit must be positive");
        if (maxPerUnit < 1) throw new IllegalArgumentException("Attachment count limit must be positive");
        if (retention.isNegative() || retention.isZero()) throw new IllegalArgumentException("Attachment retention must be positive");
        if (staleUploadAfter.isNegative() || staleUploadAfter.isZero()) throw new IllegalArgumentException("Stale upload window must be positive");
    }
}
