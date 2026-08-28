package org.kstacks.devs.content.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.kstacks.devs.attachment.api.AttachmentDtos;
import org.kstacks.devs.content.domain.ContentKind;
import org.kstacks.devs.content.domain.ContentVisibility;
import org.kstacks.devs.content.domain.PublicationStatus;
import org.kstacks.devs.content.domain.SpokenLanguage;
import org.kstacks.devs.content.domain.TagGroup;
import org.kstacks.devs.media.domain.MediaStatus;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ContentDtos {
    private ContentDtos() {}

    public record LocalizedText(String en, String ar) {}
    public record Instructor(UUID id, LocalizedText name, LocalizedText bio, String initials, String avatarUrl) {}
    public record InstructorProfile(
        UUID id,
        String nameEn,
        String nameAr,
        String bioEn,
        String bioAr,
        String initials,
        String avatarUrl,
        String accountSubject
    ) {}
    public record Tag(UUID id, TagGroup group, String slug, LocalizedText name) {}
    public record ReferenceData(List<Tag> tags, List<InstructorProfile> instructors) {}
    public record CaptionTrack(String language, String label, String path, URI url, boolean defaultTrack) {}
    public record MediaAsset(
        UUID id,
        MediaStatus status,
        long durationSeconds,
        URI playbackUrl,
        List<CaptionTrack> captions,
        String encodingVersion,
        String technicalPath,
        Instant updatedAt
    ) {
        public MediaAsset(
            UUID id,
            MediaStatus status,
            long durationSeconds,
            URI playbackUrl,
            List<CaptionTrack> captions
        ) {
            this(id, status, durationSeconds, playbackUrl, captions, null, null, null);
        }
    }
    public record ContentSection(
        UUID id,
        int position,
        LocalizedText title,
        LocalizedText description
    ) {}
    public record ContentUnit(
        UUID id,
        String slug,
        int position,
        UUID sectionId,
        LocalizedText title,
        LocalizedText summary,
        MediaAsset media,
        List<AttachmentDtos.Attachment> attachments,
        Instant deletedAt,
        Instant purgeAfter
    ) {
        public ContentUnit(
            UUID id,
            String slug,
            int position,
            UUID sectionId,
            LocalizedText title,
            LocalizedText summary,
            MediaAsset media,
            List<AttachmentDtos.Attachment> attachments
        ) {
            this(id, slug, position, sectionId, title, summary, media, attachments, null, null);
        }
    }
    public record LearningContent(
        UUID id,
        String slug,
        ContentKind kind,
        PublicationStatus status,
        ContentVisibility visibility,
        LocalizedText title,
        LocalizedText summary,
        LocalizedText description,
        SpokenLanguage spokenLanguage,
        List<Tag> tags,
        List<Instructor> instructors,
        List<ContentSection> sections,
        List<ContentUnit> units,
        String coverUrl,
        Integer featuredRank,
        Instant publishedAt,
        long views,
        long watchedMinutes,
        Instant deletedAt,
        Instant purgeAfter
    ) {
        public LearningContent(
            UUID id,
            String slug,
            ContentKind kind,
            PublicationStatus status,
            ContentVisibility visibility,
            LocalizedText title,
            LocalizedText summary,
            LocalizedText description,
            SpokenLanguage spokenLanguage,
            List<Tag> tags,
            List<Instructor> instructors,
            List<ContentSection> sections,
            List<ContentUnit> units,
            String coverUrl,
            Integer featuredRank,
            Instant publishedAt,
            long views,
            long watchedMinutes
        ) {
            this(id, slug, kind, status, visibility, title, summary, description, spokenLanguage, tags,
                instructors, sections, units, coverUrl, featuredRank, publishedAt, views, watchedMinutes, null, null);
        }

    }
    public record Home(
        List<LearningContent> featured,
        List<LearningContent> latest,
        Counts counts
    ) {}
    public record Counts(long courses, long series, long lessons) {}
    public record Catalog(
        List<LearningContent> items,
        long totalItems,
        List<Tag> tags
    ) {}
    public record AdminSummary(
        long published,
        long drafts,
        long archived,
        long processingMedia,
        long views,
        long watchedMinutes
    ) {}

    public record CreateMetadataRequest(
        @NotBlank @Size(max = 240) String title,
        @NotBlank @Size(max = 180)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
        @NotBlank @Size(max = 600) String summary,
        @NotNull ContentKind kind,
        @NotNull ContentVisibility visibility
    ) {}

    public record UpdateMetadataRequest(
        @NotBlank @Size(max = 240) String title,
        @Size(max = 240) String titleAr,
        @NotBlank @Size(max = 180)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
        @NotBlank @Size(max = 600) String summary,
        @Size(max = 600) String summaryAr,
        @NotBlank @Size(max = 20000) String description,
        @Size(max = 20000) String descriptionAr,
        @NotNull ContentVisibility visibility,
        @NotNull SpokenLanguage spokenLanguage,
        @Size(max = 40) List<@NotBlank @Size(max = 80) String> tagSlugs,
        @NotNull @Size(max = 20) List<@NotNull UUID> instructorIds,
        @jakarta.validation.constraints.Positive Integer featuredRank
    ) {
        /** Tags submitted by the client. */
        public List<String> effectiveTagSlugs() {
            return tagSlugs == null ? List.of() : tagSlugs;
        }

    }

    public record InstructorCreateRequest(
        @NotBlank @Size(max = 160) String nameEn,
        @Size(max = 160) String nameAr,
        @Size(max = 600) String bioEn,
        @Size(max = 600) String bioAr,
        @NotBlank @Size(max = 8) String initials
    ) {}

    public record InstructorUpdateRequest(
        @NotBlank @Size(max = 160) String nameEn,
        @Size(max = 160) String nameAr,
        @Size(max = 600) String bioEn,
        @Size(max = 600) String bioAr,
        @NotBlank @Size(max = 8) String initials
    ) {}

    public record InstructorAvatarUploadRequest(
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 160) String contentType,
        @jakarta.validation.constraints.Positive long contentLength
    ) {}

    public record InstructorAvatar(
        UUID id,
        String filename,
        String contentType,
        long contentLength,
        String status,
        URI url,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        Instant purgeAfter
    ) {}

    public record InstructorAvatarUploadGrant(
        InstructorAvatar avatar,
        URI uploadUrl,
        String objectKey,
        java.util.Map<String, String> headers,
        Instant expiresAt
    ) {}

    public record InstructorAvatarCompleteRequest(@NotNull UUID avatarId) {}

    public record UnitUpdateRequest(
        @NotBlank @Size(max = 180)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
        @NotBlank @Size(max = 240) String title,
        @Size(max = 240) String titleAr,
        @Size(max = 600) String summary,
        @Size(max = 600) String summaryAr,
        UUID sectionId
    ) {
        public UnitUpdateRequest(String slug, String title, String titleAr, String summary, String summaryAr) {
            this(slug, title, titleAr, summary, summaryAr, null);
        }
    }

    public record TagCreateRequest(
        @NotNull TagGroup group,
        @NotBlank @Size(max = 160) String nameEn,
        @Size(max = 160) String nameAr,
        @NotBlank @Size(max = 80)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug
    ) {}

    public record TagUpdateRequest(
        @NotNull TagGroup group,
        @NotBlank @Size(max = 160) String nameEn,
        @Size(max = 160) String nameAr,
        @NotBlank @Size(max = 80)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug
    ) {}

    public record UnitRequest(
        @NotBlank @Size(max = 180)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
        @NotNull Integer position,
        @NotBlank @Size(max = 240) String title,
        @Size(max = 240) String titleAr,
        @Size(max = 600) String summary,
        @Size(max = 600) String summaryAr,
        @NotNull UUID mediaId,
        UUID sectionId
    ) {}

    public record CurriculumSectionRequest(
        UUID id,
        @NotBlank @Size(max = 240) String title,
        @Size(max = 240) String titleAr,
        @Size(max = 600) String description,
        @Size(max = 600) String descriptionAr,
        @NotNull List<@NotNull UUID> unitIds
    ) {}

    public record CurriculumRequest(
        @NotNull List<@jakarta.validation.Valid CurriculumSectionRequest> sections,
        @NotNull List<@NotNull UUID> unsectionedUnitIds
    ) {}
}
