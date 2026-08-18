package org.kstacks.devs.content.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.kstacks.devs.content.domain.ContentKind;
import org.kstacks.devs.content.domain.ContentVisibility;
import org.kstacks.devs.content.domain.PublicationStatus;
import org.kstacks.devs.content.domain.SpokenLanguage;
import org.kstacks.devs.media.domain.MediaProvider;
import org.kstacks.devs.media.domain.MediaStatus;

import java.time.Instant;
import java.net.URI;
import java.util.List;
import java.util.UUID;

public final class ContentDtos {
    private ContentDtos() {}

    public record LocalizedText(String en, String ar) {}
    public record Instructor(UUID id, LocalizedText name, LocalizedText bio, String initials, String avatarUrl) {}
    public record Topic(String id, String slug, LocalizedText name) {}
    public record Level(String id, String slug, LocalizedText name) {}
    public record CaptionTrack(String language, String label, URI url, boolean defaultTrack) {}
    public record MediaAsset(
        UUID id,
        MediaStatus status,
        long durationSeconds,
        String playbackId,
        URI playbackUrl,
        String playbackToken,
        MediaProvider provider,
        List<CaptionTrack> captions
    ) {}
    public record ContentUnit(
        UUID id,
        String slug,
        int position,
        LocalizedText title,
        LocalizedText summary,
        MediaAsset media
    ) {}
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
        Level level,
        List<Topic> topics,
        List<Instructor> instructors,
        List<ContentUnit> units,
        String coverUrl,
        Integer featuredRank,
        Instant publishedAt,
        long views,
        long watchedMinutes
    ) {}
    public record Home(
        List<LearningContent> featured,
        List<LearningContent> latest,
        Counts counts
    ) {}
    public record Counts(long courses, long series, long lessons) {}
    public record Catalog(
        List<LearningContent> items,
        long totalItems,
        List<Topic> topics,
        List<Level> levels
    ) {}
    public record AdminSummary(
        long published,
        long drafts,
        long archived,
        long processingMedia,
        long views,
        long watchedMinutes
    ) {}

    public record MetadataRequest(
        @NotBlank @Size(max = 240) String title,
        @NotBlank @Size(max = 180)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
        @NotBlank @Size(max = 600) String summary,
        @NotNull ContentKind kind,
        @NotNull ContentVisibility visibility
    ) {}

    public record UnitRequest(
        @NotBlank @Size(max = 180)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
        @NotNull Integer position,
        @NotBlank @Size(max = 240) String title,
        @Size(max = 240) String titleAr,
        @Size(max = 600) String summary,
        @Size(max = 600) String summaryAr,
        @NotNull UUID mediaId
    ) {}
}
