package org.kstacks.devs.media.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.kstacks.devs.media.domain.MediaStatus;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MediaDtos {
    private MediaDtos() {}

    public record CaptionTrackRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$") String language,
        @NotBlank @Size(max = 120) String label,
        @NotBlank @Size(max = 1024) String path,
        boolean defaultTrack
    ) {}

    /** Replaces only caption metadata and VTT references; the HLS package stays immutable. */
    public record CaptionUpdateRequest(
        @NotNull @Size(max = 20) List<@Valid CaptionTrackRequest> captions
    ) {}

    /** Starts a managed direct-to-R2 upload for a standalone WebVTT file. */
    public record CaptionUploadRequest(
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 160) String contentType,
        @Positive long contentLength
    ) {}

    public record CaptionUploadGrant(
        UUID uploadId,
        URI uploadUrl,
        String objectKey,
        Map<String, String> headers,
        Instant expiresAt
    ) {}

    public record CaptionUploadCompleteResponse(UUID uploadId, String path) {}

    public record StaticHlsRegistrationRequest(
        @NotBlank @Size(max = 1024) String manifestPath,
        @Pattern(regexp = "^[a-fA-F0-9]{64}$") String checksumSha256,
        @NotBlank @Size(max = 120) String encodingVersion,
        @Size(max = 20) List<@Valid CaptionTrackRequest> captions
    ) {}
    public record CaptionTrackResponse(
        String language,
        String label,
        String path,
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
        MediaStatus status,
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
        MediaStatus status,
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
