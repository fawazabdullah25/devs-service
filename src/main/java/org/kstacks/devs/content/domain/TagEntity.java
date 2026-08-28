package org.kstacks.devs.content.domain;

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

@Entity
@Table(name = "content_tags")
public class TagEntity {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_group", nullable = false, length = 16)
    private TagGroup group;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(name = "name_en", nullable = false, length = 160)
    private String nameEn;

    @Column(name = "name_ar", length = 160)
    private String nameAr;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TagEntity() {}

    public static TagEntity create(TagGroup group, String slug, String nameEn, String nameAr) {
        var tag = new TagEntity();
        tag.id = UUID.randomUUID();
        tag.update(group, slug, nameEn, nameAr);
        return tag;
    }

    public void update(TagGroup group, String slug, String nameEn, String nameAr) {
        this.group = group;
        this.slug = slug;
        this.nameEn = nameEn;
        this.nameAr = nameAr;
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
    public TagGroup getGroup() { return group; }
    public String getSlug() { return slug; }
    public String getNameEn() { return nameEn; }
    public String getNameAr() { return nameAr; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
