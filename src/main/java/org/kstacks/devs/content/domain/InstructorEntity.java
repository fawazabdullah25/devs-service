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

    @Column(name = "account_subject", unique = true, length = 255)
    private String accountSubject;

    protected InstructorEntity() {}

    public static InstructorEntity create(
        String nameEn,
        String nameAr,
        String bioEn,
        String bioAr,
        String initials,
        String avatarUrl
    ) {
        var instructor = new InstructorEntity();
        instructor.id = UUID.randomUUID();
        instructor.update(nameEn, nameAr, bioEn, bioAr, initials, avatarUrl);
        return instructor;
    }

    public void update(
        String nameEn,
        String nameAr,
        String bioEn,
        String bioAr,
        String initials,
        String avatarUrl
    ) {
        this.nameEn = nameEn;
        this.nameAr = nameAr;
        this.bioEn = bioEn == null ? "" : bioEn;
        this.bioAr = bioAr;
        this.initials = initials;
        this.avatarUrl = avatarUrl;
    }

    public UUID getId() { return id; }
    public String getNameEn() { return nameEn; }
    public String getNameAr() { return nameAr; }
    public String getBioEn() { return bioEn; }
    public String getBioAr() { return bioAr; }
    public String getInitials() { return initials; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getAccountSubject() { return accountSubject; }
}
