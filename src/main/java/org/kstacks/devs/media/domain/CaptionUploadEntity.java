package org.kstacks.devs.media.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks direct-to-R2 WebVTT uploads until they are referenced by a media
 * asset. Keeping this server-side prevents an administrator from claiming an
 * arbitrary object key and gives the purge job a safe owner for abandoned
 * caption objects.
 */
@Entity
@Table(name = "caption_uploads")
public class CaptionUploadEntity {
    @Id
    private UUID id;

    @Column(name = "object_key", nullable = false, unique = true, length = 1024)
    private String objectKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 160)
    private String contentType;

    @Column(name = "content_length", nullable = false)
    private long contentLength;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CaptionUploadStatus status;

    @Column(name = "media_id")
    private UUID mediaId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CaptionUploadEntity() {}

    public static CaptionUploadEntity uploading(
        UUID id,
        String objectKey,
        String filename,
        String contentType,
        long contentLength
    ) {
        var upload = new CaptionUploadEntity();
        upload.id = id;
        upload.objectKey = objectKey;
        upload.originalFilename = filename;
        upload.contentType = contentType;
        upload.contentLength = contentLength;
        upload.status = CaptionUploadStatus.UPLOADING;
        return upload;
    }

    public void complete() {
        if (status != CaptionUploadStatus.UPLOADING) {
            throw new IllegalStateException("Caption upload is not awaiting completion");
        }
        status = CaptionUploadStatus.COMPLETED;
    }

    public void attachTo(UUID mediaId) {
        if (mediaId == null) throw new IllegalArgumentException("Media ID is required");
        if (status != CaptionUploadStatus.COMPLETED && status != CaptionUploadStatus.ATTACHED) {
            throw new IllegalStateException("Caption upload is not complete");
        }
        if (this.mediaId != null && !this.mediaId.equals(mediaId)) {
            throw new IllegalStateException("Caption upload is already attached to another media asset");
        }
        this.mediaId = mediaId;
        this.status = CaptionUploadStatus.ATTACHED;
    }

    public UUID getId() { return id; }
    public String getObjectKey() { return objectKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getContentLength() { return contentLength; }
    public CaptionUploadStatus getStatus() { return status; }
    public UUID getMediaId() { return mediaId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}
