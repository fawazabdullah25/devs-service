package org.kstacks.devs.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "instructor_avatars")
public class InstructorAvatarEntity {
    @Id
    private UUID id;

    @Column(name = "instructor_id", nullable = false)
    private UUID instructorId;

    @Column(name = "object_key", nullable = false, unique = true)
    private String objectKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 160)
    private String contentType;

    @Column(name = "content_length", nullable = false)
    private long contentLength;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InstructorAvatarStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "purge_after")
    private Instant purgeAfter;

    protected InstructorAvatarEntity() {}

    public static InstructorAvatarEntity uploading(UUID instructorId, String objectKey, String filename,
                                                    String contentType, long contentLength) {
        var avatar = new InstructorAvatarEntity();
        avatar.id = UUID.randomUUID();
        avatar.instructorId = instructorId;
        avatar.objectKey = objectKey;
        avatar.originalFilename = filename;
        avatar.contentType = contentType;
        avatar.contentLength = contentLength;
        avatar.status = InstructorAvatarStatus.UPLOADING;
        return avatar;
    }

    public void ready() {
        status = InstructorAvatarStatus.READY;
        deletedAt = null;
        purgeAfter = null;
        updatedAt = Instant.now();
    }

    public void softDelete(Duration retention) {
        var now = Instant.now();
        status = InstructorAvatarStatus.DELETED;
        deletedAt = now;
        purgeAfter = now.plus(retention);
        updatedAt = now;
    }

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getInstructorId() { return instructorId; }
    public String getObjectKey() { return objectKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getContentLength() { return contentLength; }
    public InstructorAvatarStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getPurgeAfter() { return purgeAfter; }
}
