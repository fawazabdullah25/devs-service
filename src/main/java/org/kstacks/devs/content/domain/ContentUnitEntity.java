package org.kstacks.devs.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.kstacks.devs.media.domain.MediaAssetEntity;

import java.util.UUID;

@Entity
@Table(name = "content_units")
public class ContentUnitEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private LearningContentEntity content;

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

    public UUID getId() { return id; }
    public MediaAssetEntity getMedia() { return media; }
    public String getSlug() { return slug; }
    public int getPosition() { return position; }
    public String getTitleEn() { return titleEn; }
    public String getTitleAr() { return titleAr; }
    public String getSummaryEn() { return summaryEn; }
    public String getSummaryAr() { return summaryAr; }
}
