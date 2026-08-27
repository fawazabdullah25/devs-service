package org.kstacks.devs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;

@ConfigurationProperties("devs.media")
public record MediaProperties(
    long maxUploadBytes,
    long uploadExpiryMinutes,
    Duration retention,
    Duration versionRetention,
    Duration purgeDelay,
    Duration staleUploadAfter
) {
    @ConstructorBinding
    public MediaProperties {
        if (maxUploadBytes < 1) throw new IllegalArgumentException("Media upload limit must be positive");
        if (uploadExpiryMinutes < 1) throw new IllegalArgumentException("Media upload expiry must be positive");
        if (retention == null || retention.isNegative() || retention.isZero()) throw new IllegalArgumentException("Media retention must be positive");
        if (versionRetention == null || versionRetention.isNegative() || versionRetention.isZero()) throw new IllegalArgumentException("Media version retention must be positive");
        if (purgeDelay == null || purgeDelay.isNegative() || purgeDelay.isZero()) throw new IllegalArgumentException("Media purge delay must be positive");
        if (staleUploadAfter == null || staleUploadAfter.isNegative() || staleUploadAfter.isZero()) throw new IllegalArgumentException("Stale media upload window must be positive");
    }

    /** Compatibility constructor for focused unit tests and small integrations. */
    public MediaProperties(long maxUploadBytes, long uploadExpiryMinutes) {
        this(maxUploadBytes, uploadExpiryMinutes, Duration.ofDays(7), Duration.ofDays(30), Duration.ofHours(1), Duration.ofHours(24));
    }
}
