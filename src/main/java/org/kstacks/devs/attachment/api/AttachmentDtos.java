package org.kstacks.devs.attachment.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.kstacks.devs.attachment.domain.AttachmentStatus;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AttachmentDtos {
    private AttachmentDtos() {}

    public record Attachment(UUID id, String titleEn, String titleAr, String filename, String contentType,
                             long contentLength, int position, URI url, AttachmentStatus status,
                             Instant deletedAt, Instant purgeAfter) {}
    public record UploadRequest(
        @NotBlank @Size(max = 240) String title,
        @Size(max = 240) String titleAr,
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 160) String contentType,
        @Min(1) long contentLength
    ) {}
    public record UploadGrant(Attachment attachment, URI uploadUrl, String objectKey,
                              Map<String, String> headers, Instant expiresAt) {}
    public record UpdateRequest(
        @NotBlank @Size(max = 240) String title,
        @Size(max = 240) String titleAr,
        @NotNull @Min(1) @Max(1000) Integer position
    ) {}
    public record OrderRequest(@NotNull @Size(max = 20) List<UUID> attachmentIds) {}
}
