package org.kstacks.devs.media.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class MediaDtos {
    private MediaDtos() {}

    public record UploadRequest(
        @NotBlank String filename,
        @NotBlank String contentType,
        @Positive long contentLength
    ) {}

    public record UploadGrant(
        UUID mediaId,
        URI uploadUrl,
        String objectKey,
        Map<String, String> headers,
        Instant expiresAt
    ) {}

    public record ImportRequest(
        @NotBlank @Size(max = 1024) String objectKey,
        @NotBlank @Size(max = 180) String filename,
        @NotBlank @Size(max = 160) String contentType,
        @Pattern(regexp = "^[a-fA-F0-9]{64}$") String checksumSha256
    ) {}

    public record IngestResponse(UUID mediaId, String status, String providerAssetId) {}
    public record MediaStatusResponse(
        UUID mediaId,
        String status,
        String providerAssetId,
        String playbackId,
        long durationSeconds,
        String errorMessage
    ) {}
}
