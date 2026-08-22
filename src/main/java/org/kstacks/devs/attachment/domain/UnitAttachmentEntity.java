package org.kstacks.devs.attachment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.kstacks.devs.content.domain.ContentUnitEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "unit_attachments")
public class UnitAttachmentEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private ContentUnitEntity unit;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private AttachmentStatus status;
    @Column(name = "object_key", nullable = false) private String objectKey;
    @Column(name = "original_filename", nullable = false, length = 255) private String originalFilename;
    @Column(name = "content_type", nullable = false, length = 160) private String contentType;
    @Column(name = "content_length", nullable = false) private long contentLength;
    @Column(name = "title_en", nullable = false, length = 240) private String titleEn;
    @Column(name = "title_ar", length = 240) private String titleAr;
    @Column(nullable = false) private int position;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;
    @Column(name = "purge_after") private Instant purgeAfter;

    protected UnitAttachmentEntity() {}

    public static UnitAttachmentEntity uploading(ContentUnitEntity unit, String objectKey, String filename,
                                                   String contentType, long contentLength, String titleEn,
                                                   String titleAr, int position) {
        var now = Instant.now();
        var entity = new UnitAttachmentEntity();
        entity.id = UUID.randomUUID();
        entity.unit = unit;
        entity.status = AttachmentStatus.UPLOADING;
        entity.objectKey = objectKey;
        entity.originalFilename = filename;
        entity.contentType = contentType;
        entity.contentLength = contentLength;
        entity.titleEn = titleEn;
        entity.titleAr = titleAr;
        entity.position = position;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void ready() { status = AttachmentStatus.READY; updatedAt = Instant.now(); }
    public void update(String titleEn, String titleAr, int position) {
        this.titleEn = titleEn; this.titleAr = titleAr; this.position = position; this.updatedAt = Instant.now();
    }
    public void softDelete(Duration retention) {
        status = AttachmentStatus.DELETED; deletedAt = Instant.now(); purgeAfter = deletedAt.plus(retention); updatedAt = deletedAt;
    }
    public void restore() { status = AttachmentStatus.READY; deletedAt = null; purgeAfter = null; updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public ContentUnitEntity getUnit() { return unit; }
    public AttachmentStatus getStatus() { return status; }
    public String getObjectKey() { return objectKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getContentLength() { return contentLength; }
    public String getTitleEn() { return titleEn; }
    public String getTitleAr() { return titleAr; }
    public int getPosition() { return position; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getPurgeAfter() { return purgeAfter; }
}
