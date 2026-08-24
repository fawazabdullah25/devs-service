package org.kstacks.devs.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "content_sections")
public class ContentSectionEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private LearningContentEntity content;

    @Column(nullable = false)
    private int position;

    @Column(name = "title_en", nullable = false, length = 240)
    private String titleEn;

    @Column(name = "title_ar", length = 240)
    private String titleAr;

    @Column(name = "description_en", length = 600)
    private String descriptionEn;

    @Column(name = "description_ar", length = 600)
    private String descriptionAr;

    protected ContentSectionEntity() {}

    public ContentSectionEntity(int position, String titleEn, String titleAr, String descriptionEn, String descriptionAr) {
        this.id = UUID.randomUUID();
        update(position, titleEn, titleAr, descriptionEn, descriptionAr);
    }

    void attachTo(LearningContentEntity content) { this.content = content; }

    public void update(int position, String titleEn, String titleAr, String descriptionEn, String descriptionAr) {
        this.position = position;
        this.titleEn = titleEn;
        this.titleAr = titleAr;
        this.descriptionEn = descriptionEn;
        this.descriptionAr = descriptionAr;
    }

    public void moveTo(int position) { this.position = position; }

    public UUID getId() { return id; }
    public LearningContentEntity getContent() { return content; }
    public int getPosition() { return position; }
    public String getTitleEn() { return titleEn; }
    public String getTitleAr() { return titleAr; }
    public String getDescriptionEn() { return descriptionEn; }
    public String getDescriptionAr() { return descriptionAr; }
}
