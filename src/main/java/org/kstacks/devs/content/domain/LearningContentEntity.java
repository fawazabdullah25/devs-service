package org.kstacks.devs.content.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "learning_content")
public class LearningContentEntity {
    @Id
    private UUID id;

    @Version
    private long version;

    @Column(nullable = false, unique = true, length = 180)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ContentKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PublicationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ContentVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "spoken_language", nullable = false, length = 16)
    private SpokenLanguage spokenLanguage;

    @Column(name = "title_en", nullable = false, length = 240)
    private String titleEn;

    @Column(name = "title_ar", length = 240)
    private String titleAr;

    @Column(name = "summary_en", nullable = false, length = 600)
    private String summaryEn;

    @Column(name = "summary_ar", length = 600)
    private String summaryAr;

    @Column(name = "description_en", nullable = false)
    private String descriptionEn;

    @Column(name = "description_ar")
    private String descriptionAr;

    @Column(name = "level_slug", nullable = false, length = 80)
    private String levelSlug;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(name = "featured_rank")
    private Integer featuredRank;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private long views;

    @Column(name = "watched_minutes", nullable = false)
    private long watchedMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "content_topics", joinColumns = @JoinColumn(name = "content_id"))
    @Column(name = "topic_slug", nullable = false, length = 80)
    private Set<String> topicSlugs = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "content_instructors",
        joinColumns = @JoinColumn(name = "content_id"),
        inverseJoinColumns = @JoinColumn(name = "instructor_id")
    )
    private Set<InstructorEntity> instructors = new LinkedHashSet<>();

    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ContentUnitEntity> units = new ArrayList<>();

    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private Set<ContentSectionEntity> sections = new LinkedHashSet<>();

    protected LearningContentEntity() {}

    public static LearningContentEntity draft(String slug, ContentKind kind, ContentVisibility visibility, String title, String summary) {
        var content = new LearningContentEntity();
        content.id = UUID.randomUUID();
        content.slug = slug;
        content.kind = kind;
        content.status = PublicationStatus.DRAFT;
        content.visibility = visibility;
        content.spokenLanguage = SpokenLanguage.MIXED;
        content.titleEn = title;
        content.summaryEn = summary;
        content.descriptionEn = summary;
        content.levelSlug = "getting-started";
        return content;
    }

    public void updateMetadata(String slug, ContentKind kind, ContentVisibility visibility, String title, String summary) {
        if (this.descriptionEn.isBlank() || this.descriptionEn.equals(this.summaryEn)) {
            this.descriptionEn = summary;
        }
        this.slug = slug;
        this.kind = kind;
        this.visibility = visibility;
        this.titleEn = title;
        this.summaryEn = summary;
    }

    public void addUnit(ContentUnitEntity unit) {
        unit.attachTo(this);
        units.add(unit);
    }

    public void addSection(ContentSectionEntity section) {
        section.attachTo(this);
        sections.add(section);
    }

    public void removeSection(ContentSectionEntity section) { sections.remove(section); }

    public void markCurriculumChanged() { updatedAt = Instant.now(); }

    public void publish() {
        this.status = PublicationStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void archive() { this.status = PublicationStatus.ARCHIVED; }

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public ContentKind getKind() { return kind; }
    public PublicationStatus getStatus() { return status; }
    public ContentVisibility getVisibility() { return visibility; }
    public SpokenLanguage getSpokenLanguage() { return spokenLanguage; }
    public String getTitleEn() { return titleEn; }
    public String getTitleAr() { return titleAr; }
    public String getSummaryEn() { return summaryEn; }
    public String getSummaryAr() { return summaryAr; }
    public String getDescriptionEn() { return descriptionEn; }
    public String getDescriptionAr() { return descriptionAr; }
    public String getLevelSlug() { return levelSlug; }
    public String getCoverUrl() { return coverUrl; }
    public Integer getFeaturedRank() { return featuredRank; }
    public Instant getPublishedAt() { return publishedAt; }
    public long getViews() { return views; }
    public long getWatchedMinutes() { return watchedMinutes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<String> getTopicSlugs() { return Set.copyOf(topicSlugs); }
    public Set<InstructorEntity> getInstructors() { return Set.copyOf(instructors); }
    public List<ContentUnitEntity> getUnits() { return List.copyOf(units); }
    public List<ContentSectionEntity> getSections() {
        return sections.stream().sorted(Comparator.comparingInt(ContentSectionEntity::getPosition)).toList();
    }
}
