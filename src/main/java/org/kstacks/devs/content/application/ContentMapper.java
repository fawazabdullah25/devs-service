package org.kstacks.devs.content.application;

import org.kstacks.devs.attachment.application.AttachmentService;
import org.kstacks.devs.attachment.domain.AttachmentStatus;
import org.kstacks.devs.content.api.ContentDtos;
import org.kstacks.devs.content.domain.ContentSectionEntity;
import org.kstacks.devs.content.domain.ContentUnitEntity;
import org.kstacks.devs.content.domain.InstructorEntity;
import org.kstacks.devs.content.domain.LearningContentEntity;
import org.kstacks.devs.media.application.CoverService;
import org.kstacks.devs.media.application.StaticHlsLocationResolver;
import org.kstacks.devs.media.domain.MediaAssetEntity;
import org.kstacks.devs.media.domain.MediaStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
public class ContentMapper {
    private final StaticHlsLocationResolver staticHlsLocations;
    private final AttachmentService attachmentService;
    private final CoverService coverService;
    private final InstructorAvatarService instructorAvatarService;

    @Autowired
    public ContentMapper(
        StaticHlsLocationResolver staticHlsLocations,
        AttachmentService attachmentService,
        CoverService coverService,
        InstructorAvatarService instructorAvatarService
    ) {
        this.staticHlsLocations = staticHlsLocations;
        this.attachmentService = attachmentService;
        this.coverService = coverService;
        this.instructorAvatarService = instructorAvatarService;
    }

    ContentMapper(StaticHlsLocationResolver staticHlsLocations, AttachmentService attachmentService, CoverService coverService) {
        this(staticHlsLocations, attachmentService, coverService, null);
    }

    ContentMapper(StaticHlsLocationResolver staticHlsLocations, AttachmentService attachmentService) {
        this(staticHlsLocations, attachmentService, null, null);
    }

    ContentMapper(StaticHlsLocationResolver staticHlsLocations) {
        this(staticHlsLocations, null, null, null);
    }

    public ContentDtos.LearningContent toDto(LearningContentEntity entity) {
        var tags = entity.getTags().stream()
            .sorted(Comparator.comparing((org.kstacks.devs.content.domain.TagEntity tag) -> tag.getGroup().name())
                .thenComparing(org.kstacks.devs.content.domain.TagEntity::getNameEn))
            .map(tag -> new ContentDtos.Tag(tag.getId(), tag.getGroup(), tag.getSlug(),
                text(tag.getNameEn(), tag.getNameAr())))
            .toList();
        var instructors = entity.getInstructors().stream()
            .sorted(Comparator.comparing(InstructorEntity::getNameEn))
            .map(this::toDto)
            .toList();
        var sections = entity.getSections().stream()
            .sorted(Comparator.comparingInt(ContentSectionEntity::getPosition))
            .map(section -> new ContentDtos.ContentSection(
                section.getId(), section.getPosition(), text(section.getTitleEn(), section.getTitleAr()),
                nullableText(section.getDescriptionEn(), section.getDescriptionAr())
            ))
            .toList();
        var units = entity.getActiveUnits().stream()
            .sorted(Comparator.comparingInt(ContentUnitEntity::getPosition))
            .map(this::toDto).toList();

        return new ContentDtos.LearningContent(
            entity.getId(), entity.getSlug(), entity.getKind(), entity.getStatus(), entity.getVisibility(),
            text(entity.getTitleEn(), entity.getTitleAr()),
            text(entity.getSummaryEn(), entity.getSummaryAr()),
            text(entity.getDescriptionEn(), entity.getDescriptionAr()),
            entity.getSpokenLanguage(), tags, instructors, sections, units,
            coverUrl(entity), entity.getFeaturedRank(), entity.getPublishedAt(), entity.getViews(), entity.getWatchedMinutes(),
            entity.getDeletedAt(), entity.getPurgeAfter()
        );
    }

    public ContentDtos.InstructorProfile toProfile(InstructorEntity instructor) {
        return new ContentDtos.InstructorProfile(
            instructor.getId(), instructor.getNameEn(), instructor.getNameAr(), instructor.getBioEn(), instructor.getBioAr(),
            instructor.getInitials(), avatarUrl(instructor), instructor.getAccountSubject()
        );
    }

    private ContentDtos.Instructor toDto(InstructorEntity instructor) {
        return new ContentDtos.Instructor(
            instructor.getId(), text(instructor.getNameEn(), instructor.getNameAr()),
            text(instructor.getBioEn(), instructor.getBioAr()), instructor.getInitials(), avatarUrl(instructor)
        );
    }

    private String avatarUrl(InstructorEntity instructor) {
        if (instructorAvatarService == null) return null;
        var active = instructorAvatarService.resolveActiveUrl(instructor.getId());
        return active == null ? null : active.toString();
    }

    public ContentDtos.ContentUnit toUnitDto(ContentUnitEntity unit) {
        return new ContentDtos.ContentUnit(
            unit.getId(), unit.getSlug(), unit.getPosition(), unit.getSection() == null ? null : unit.getSection().getId(),
            text(unit.getTitleEn(), unit.getTitleAr()),
            nullableText(unit.getSummaryEn(), unit.getSummaryAr()), toDto(unit.getMedia()),
            unit.getAttachments().stream()
                .filter(attachment -> attachment.getStatus() == AttachmentStatus.READY)
                .map(attachment -> attachmentService == null ? null : attachmentService.toDto(attachment))
                .filter(java.util.Objects::nonNull)
                .toList(),
            unit.getDeletedAt(), unit.getPurgeAfter()
        );
    }

    private ContentDtos.ContentUnit toDto(ContentUnitEntity unit) { return toUnitDto(unit); }

    ContentDtos.MediaAsset toDto(MediaAssetEntity media) {
        if (media == null) {
            return new ContentDtos.MediaAsset(
                null, MediaStatus.READY, 0, null, java.util.List.of()
            );
        }
        var playbackUrl = media.getPlaybackPath() == null ? null : staticHlsLocations.resolve(media.getPlaybackPath());
        var captions = media.getCaptionTracks().stream()
            .map(track -> new ContentDtos.CaptionTrack(
                track.getLanguage(),
                track.getLabel(),
                track.getPath(),
                staticHlsLocations.resolve(track.getPath()),
                track.isDefaultTrack()
            ))
            .toList();
        return new ContentDtos.MediaAsset(
            media.getId(), media.getStatus(), media.getDurationSeconds(), playbackUrl,
            captions, media.getEncodingVersion(), media.getPlaybackPath(), media.getUpdatedAt()
        );
    }

    private String coverUrl(LearningContentEntity entity) {
        if (coverService == null) return entity.getCoverUrl();
        var active = coverService.resolveActiveUrl(entity.getId());
        return active == null ? entity.getCoverUrl() : active.toString();
    }

    private ContentDtos.LocalizedText text(String en, String ar) {
        return new ContentDtos.LocalizedText(en == null ? "" : en, ar);
    }

    private ContentDtos.LocalizedText nullableText(String en, String ar) {
        return en == null && ar == null ? null : text(en, ar);
    }
}
