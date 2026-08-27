package org.kstacks.devs.media.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.kstacks.devs.media.domain.MediaProvider;
import org.kstacks.devs.media.domain.MediaStatus;

import java.net.URI;
import java.time.Instant;
import java.util.List;
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
    public record CaptionTrackRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$") String language,
        @NotBlank @Size(max = 120) String label,
        @NotBlank @Size(max = 1024) String path,
        boolean defaultTrack
    ) {}
    public record StaticHlsRegistrationRequest(
        @NotBlank @Size(max = 1024) String manifestPath,
        @Positive long durationSeconds,
        @Pattern(regexp = "^[a-fA-F0-9]{64}$") String checksumSha256,
        @NotBlank @Size(max = 120) String encodingVersion,
        @Size(max = 20) List<@Valid CaptionTrackRequest> captions
    ) {}
    public record CaptionTrackResponse(
        String language,
        String label,
        URI url,
        boolean defaultTrack
    ) {}
    public record StaticHlsRegistrationResponse(
        UUID mediaId,
        String status,
        URI playbackUrl,
        long durationSeconds,
        String encodingVersion,
        List<CaptionTrackResponse> captions
    ) {}
    public record MediaStatusResponse(
        UUID mediaId,
        String status,
        String provider,
        String providerAssetId,
        String playbackId,
        URI playbackUrl,
        long durationSeconds,
        List<CaptionTrackResponse> captions,
        String errorMessage,
        String playbackPath,
        String encodingVersion,
        String checksumSha256,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        Instant purgeAfter,
        UUID retainedForUnitId,
        CurrentAttachment currentAttachment
    ) {}

    public record CurrentAttachment(
        UUID contentId,
        String contentTitle,
        UUID unitId,
        String unitTitle
    ) {}

    public record MediaLibraryItem(
        UUID mediaId,
        MediaProvider provider,
        MediaStatus status,
        String providerAssetId,
        String playbackId,
        String playbackPath,
        URI playbackUrl,
        long durationSeconds,
        String encodingVersion,
        String checksumSha256,
        List<CaptionTrackResponse> captions,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        Instant purgeAfter,
        UUID retainedForUnitId,
        CurrentAttachment currentAttachment
    ) {}

    public record MediaReplacementRequest(@jakarta.validation.constraints.NotNull UUID mediaId) {}

    public record MediaVersion(
        UUID mediaId,
        boolean current,
        MediaProvider provider,
        MediaStatus status,
        String providerAssetId,
        String playbackId,
        String playbackPath,
        URI playbackUrl,
        long durationSeconds,
        String encodingVersion,
        String checksumSha256,
        List<CaptionTrackResponse> captions,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        Instant purgeAfter
    ) {}

    public record CoverUploadRequest(
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 160) String contentType,
        @Positive long contentLength
    ) {}

    public record Cover(
        UUID id,
        String filename,
        String contentType,
        long contentLength,
        String status,
        URI url,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        Instant purgeAfter
    ) {}

    public record CoverUploadGrant(
        Cover cover,
        URI uploadUrl,
        String objectKey,
        Map<String, String> headers,
        Instant expiresAt
    ) {}

    public record CoverCompleteRequest(@jakarta.validation.constraints.NotNull UUID coverId) {}
}
