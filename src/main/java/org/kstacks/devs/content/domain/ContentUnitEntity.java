package org.kstacks.devs.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import org.kstacks.devs.attachment.domain.UnitAttachmentEntity;
import org.kstacks.devs.media.domain.MediaAssetEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "content_units")
public class ContentUnitEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private LearningContentEntity content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private ContentSectionEntity section;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    private MediaAssetEntity media;

    @Column(nullable = false, length = 180)
    private String slug;

    @Column(nullable = false)
    private int position;

    @Column(name = "title_en", nullable = false, length = 240)
    private String titleEn;

    @Column(name = "title_ar", length = 240)
    private String titleAr;

    @Column(name = "summary_en", length = 600)
    private String summaryEn;

    @Column(name = "summary_ar", length = 600)
    private String summaryAr;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "purge_after")
    private Instant purgeAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "purge_state", nullable = false, length = 16)
    private TrashPurgeState purgeState = TrashPurgeState.NONE;

    @OneToMany(mappedBy = "unit", fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    @BatchSize(size = 50)
    private List<UnitAttachmentEntity> attachments = new ArrayList<>();

    protected ContentUnitEntity() {}

    public ContentUnitEntity(String slug, int position, String titleEn, String titleAr, String summaryEn, String summaryAr, MediaAssetEntity media) {
        this.id = UUID.randomUUID();
        this.slug = slug;
        this.position = position;
        this.titleEn = titleEn;
        this.titleAr = titleAr;
        this.summaryEn = summaryEn;
        this.summaryAr = summaryAr;
        this.media = media;
    }

    void attachTo(LearningContentEntity content) { this.content = content; }

    public void organize(ContentSectionEntity section, int position) {
        this.section = section;
        this.position = position;
    }

    /** Replace the lesson's current media asset while keeping curriculum ownership here. */
    public void replaceMedia(MediaAssetEntity media) { this.media = media; }

    public void updateMetadata(String slug, String titleEn, String titleAr, String summaryEn, String summaryAr) {
        this.slug = slug;
        this.titleEn = titleEn;
        this.titleAr = titleAr;
        this.summaryEn = summaryEn;
        this.summaryAr = summaryAr;
    }

    public void softDelete(Duration retention) {
        if (isPurgeClaimed()) throw new IllegalStateException("Lesson is already being purged");
        if (isDeleted()) return;
        this.deletedAt = Instant.now();
        this.purgeAfter = deletedAt.plus(retention);
        this.purgeState = TrashPurgeState.NONE;
    }

    public void restoreFromTrash() {
        if (isPurgeClaimed()) throw new IllegalStateException("Lesson is being purged and cannot be restored");
        this.deletedAt = null;
        this.purgeAfter = null;
        this.section = null;
        this.purgeState = TrashPurgeState.NONE;
    }

    /** Claim a due trash row before touching any external object. */
    public boolean claimForPurge(Instant now) {
        if (!isDeleted() || purgeAfter == null || purgeAfter.isAfter(now)) return false;
        if (purgeState == TrashPurgeState.CLAIMED) return true;
        purgeState = TrashPurgeState.CLAIMED;
        return true;
    }

    public boolean isPurgeClaimed() { return purgeState == TrashPurgeState.CLAIMED; }

    public boolean isDeleted() { return deletedAt != null; }

    public UUID getId() { return id; }
    public LearningContentEntity getContent() { return content; }
    public ContentSectionEntity getSection() { return section; }
    public MediaAssetEntity getMedia() { return media; }
    public String getSlug() { return slug; }
    public int getPosition() { return position; }
    public String getTitleEn() { return titleEn; }
    public String getTitleAr() { return titleAr; }
    public String getSummaryEn() { return summaryEn; }
    public String getSummaryAr() { return summaryAr; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getPurgeAfter() { return purgeAfter; }
    public TrashPurgeState getPurgeState() { return purgeState; }
    public List<UnitAttachmentEntity> getAttachments() { return List.copyOf(attachments); }
}
