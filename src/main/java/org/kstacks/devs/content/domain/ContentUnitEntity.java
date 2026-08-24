package org.kstacks.devs.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    public List<UnitAttachmentEntity> getAttachments() { return List.copyOf(attachments); }
}
