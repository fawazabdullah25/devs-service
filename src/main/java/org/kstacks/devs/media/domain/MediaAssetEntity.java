package org.kstacks.devs.media.domain;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "media_assets")
public class MediaAssetEntity {
    @Id
    private UUID id;

    @Version
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MediaProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MediaStatus status;

    @Column(name = "provider_asset_id")
    private String providerAssetId;

    @Column(name = "playback_id")
    private String playbackId;

    @Column(name = "playback_path")
    private String playbackPath;

    @Column(name = "encoding_version", length = 120)
    private String encodingVersion;

    @ElementCollection
    @CollectionTable(name = "media_caption_tracks", joinColumns = @JoinColumn(name = "media_id"))
    private List<MediaCaptionTrack> captionTracks = new ArrayList<>();

    @Column(name = "source_object_key")
    private String sourceObjectKey;

    @Column(name = "source_filename")
    private String sourceFilename;

    @Column(name = "source_content_type", length = 160)
    private String sourceContentType;

    @Column(name = "duration_seconds", nullable = false)
    private long durationSeconds;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "purge_after")
    private Instant purgeAfter;

    @Column(name = "retained_for_unit_id")
    private UUID retainedForUnitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "deleted_from_status", length = 16)
    private MediaStatus deletedFromStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MediaAssetEntity() {}

    public static MediaAssetEntity uploading(String objectKey, String filename, String contentType) {
        var media = new MediaAssetEntity();
        media.id = UUID.randomUUID();
        media.provider = MediaProvider.MUX;
        media.status = MediaStatus.UPLOADING;
        media.sourceObjectKey = objectKey;
        media.sourceFilename = filename;
        media.sourceContentType = contentType;
        return media;
    }

    public static MediaAssetEntity imported(String objectKey, String filename, String contentType, String checksumSha256) {
        var media = uploading(objectKey, filename, contentType);
        media.checksumSha256 = checksumSha256;
        return media;
    }

    public static MediaAssetEntity staticHls(
        String playbackPath,
        long durationSeconds,
        String checksumSha256,
        String encodingVersion,
        List<MediaCaptionTrack> captionTracks
    ) {
        var media = new MediaAssetEntity();
        media.id = UUID.randomUUID();
        media.provider = MediaProvider.STATIC_HLS;
        media.status = MediaStatus.READY;
        media.playbackPath = playbackPath;
        media.durationSeconds = durationSeconds;
        media.checksumSha256 = checksumSha256;
        media.encodingVersion = encodingVersion;
        media.captionTracks.addAll(captionTracks);
        return media;
    }

    public void markProcessing(String providerAssetId) {
        this.providerAssetId = providerAssetId;
        this.status = MediaStatus.PROCESSING;
        this.failureMessage = null;
        this.deletedAt = null;
        this.purgeAfter = null;
        this.retainedForUnitId = null;
        this.deletedFromStatus = null;
    }

    public void markReady(String playbackId, long durationSeconds) {
        this.playbackId = playbackId;
        this.durationSeconds = Math.max(0, durationSeconds);
        this.status = MediaStatus.READY;
        this.failureMessage = null;
        this.deletedAt = null;
        this.purgeAfter = null;
        this.retainedForUnitId = null;
        this.deletedFromStatus = null;
    }

    public void markFailed(String message) {
        this.status = MediaStatus.FAILED;
        this.failureMessage = message == null ? "Media processing failed" : message.substring(0, Math.min(message.length(), 2000));
    }

    public void softDelete(Duration retention) {
        if (retainedForUnitId != null) {
            throw new IllegalStateException("A retained media version cannot be directly deleted");
        }
        var now = Instant.now();
        this.deletedFromStatus = this.status;
        this.status = MediaStatus.DELETED;
        this.deletedAt = now;
        this.purgeAfter = now.plus(retention);
    }

    public void retainForUnit(UUID unitId, Duration retention) {
        if (status != MediaStatus.READY || deletedAt != null || retainedForUnitId != null) {
            throw new IllegalStateException("Only a current ready media asset can be retained");
        }
        var now = Instant.now();
        this.retainedForUnitId = unitId;
        this.deletedAt = now;
        this.purgeAfter = now.plus(retention);
        this.deletedFromStatus = null;
    }

    public void restoreAsCurrent(UUID unitId) {
        if (!unitId.equals(retainedForUnitId) || status != MediaStatus.READY) {
            throw new IllegalStateException("Media is not a retained version for this lesson");
        }
        this.retainedForUnitId = null;
        this.deletedAt = null;
        this.purgeAfter = null;
        this.deletedFromStatus = null;
    }

    public void restoreUnattached() {
        if (status != MediaStatus.DELETED || retainedForUnitId != null) {
            throw new IllegalStateException("Media is not an unattached deleted asset");
        }
        status = deletedFromStatus == null ? MediaStatus.READY : deletedFromStatus;
        deletedAt = null;
        purgeAfter = null;
        deletedFromStatus = null;
    }

    /**
     * Drop a historical-retention marker when its lesson is being removed but
     * this media row is still current in another lesson. The media remains a
     * normal current asset; its object must not be deleted by the purge.
     */
    public void clearRetentionForUnit(UUID unitId) {
        if (!unitId.equals(retainedForUnitId)) {
            throw new IllegalStateException("Media is not retained for this lesson");
        }
        retainedForUnitId = null;
        deletedAt = null;
        purgeAfter = null;
        deletedFromStatus = null;
    }

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public MediaProvider getProvider() { return provider; }
    public MediaStatus getStatus() { return status; }
    public String getProviderAssetId() { return providerAssetId; }
    public String getPlaybackId() { return playbackId; }
    public String getPlaybackPath() { return playbackPath; }
    public String getEncodingVersion() { return encodingVersion; }
    public List<MediaCaptionTrack> getCaptionTracks() { return List.copyOf(captionTracks); }
    public String getSourceObjectKey() { return sourceObjectKey; }
    public String getSourceFilename() { return sourceFilename; }
    public String getSourceContentType() { return sourceContentType; }
    public long getDurationSeconds() { return durationSeconds; }
    public String getChecksumSha256() { return checksumSha256; }
    public String getFailureMessage() { return failureMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getPurgeAfter() { return purgeAfter; }
    public UUID getRetainedForUnitId() { return retainedForUnitId; }
    public boolean isAvailableForAttachment() {
        return status == MediaStatus.READY && deletedAt == null && retainedForUnitId == null;
    }
}
