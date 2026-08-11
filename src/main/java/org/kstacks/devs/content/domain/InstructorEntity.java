package org.kstacks.devs.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "instructors")
public class InstructorEntity {
    @Id
    private UUID id;

    @Column(name = "name_en", nullable = false, length = 160)
    private String nameEn;

    @Column(name = "name_ar", length = 160)
    private String nameAr;

    @Column(name = "bio_en", nullable = false)
    private String bioEn;

    @Column(name = "bio_ar")
    private String bioAr;

    @Column(nullable = false, length = 8)
    private String initials;

    @Column(name = "avatar_url")
    private String avatarUrl;

    protected InstructorEntity() {}

    public UUID getId() { return id; }
    public String getNameEn() { return nameEn; }
    public String getNameAr() { return nameAr; }
    public String getBioEn() { return bioEn; }
    public String getBioAr() { return bioAr; }
    public String getInitials() { return initials; }
    public String getAvatarUrl() { return avatarUrl; }
}
