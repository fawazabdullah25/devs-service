package org.kstacks.devs.content.application;

import org.kstacks.devs.content.api.ContentDtos;
import org.kstacks.devs.content.domain.ContentUnitEntity;
import org.kstacks.devs.content.domain.InstructorEntity;
import org.kstacks.devs.content.domain.LearningContentEntity;
import org.kstacks.devs.media.domain.MediaAssetEntity;
import org.kstacks.devs.media.domain.MediaProvider;
import org.kstacks.devs.media.domain.MediaStatus;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
public class ContentMapper {
    public ContentDtos.LearningContent toDto(LearningContentEntity entity) {
        var topics = entity.getTopicSlugs().stream()
            .sorted()
            .map(ReferenceCatalog::topic)
            .toList();
        var instructors = entity.getInstructors().stream()
            .sorted(Comparator.comparing(InstructorEntity::getNameEn))
            .map(this::toDto)
            .toList();
        var units = entity.getUnits().stream().map(this::toDto).toList();

        return new ContentDtos.LearningContent(
            entity.getId(), entity.getSlug(), entity.getKind(), entity.getStatus(), entity.getVisibility(),
            text(entity.getTitleEn(), entity.getTitleAr()),
            text(entity.getSummaryEn(), entity.getSummaryAr()),
            text(entity.getDescriptionEn(), entity.getDescriptionAr()),
            entity.getSpokenLanguage(), ReferenceCatalog.level(entity.getLevelSlug()), topics, instructors, units,
            entity.getCoverUrl(), entity.getFeaturedRank(), entity.getPublishedAt(), entity.getViews(), entity.getWatchedMinutes()
        );
    }

    private ContentDtos.Instructor toDto(InstructorEntity instructor) {
        return new ContentDtos.Instructor(
            instructor.getId(), text(instructor.getNameEn(), instructor.getNameAr()),
            text(instructor.getBioEn(), instructor.getBioAr()), instructor.getInitials(), instructor.getAvatarUrl()
        );
    }

    private ContentDtos.ContentUnit toDto(ContentUnitEntity unit) {
        return new ContentDtos.ContentUnit(
            unit.getId(), unit.getSlug(), unit.getPosition(), text(unit.getTitleEn(), unit.getTitleAr()),
            nullableText(unit.getSummaryEn(), unit.getSummaryAr()), toDto(unit.getMedia())
        );
    }

    private ContentDtos.MediaAsset toDto(MediaAssetEntity media) {
        if (media == null) {
            return new ContentDtos.MediaAsset(null, MediaStatus.UPLOADING, 0, null, null, MediaProvider.MUX);
        }
        return new ContentDtos.MediaAsset(
            media.getId(), media.getStatus(), media.getDurationSeconds(), media.getPlaybackId(), null, media.getProvider()
        );
    }

    private ContentDtos.LocalizedText text(String en, String ar) {
        return new ContentDtos.LocalizedText(en == null ? "" : en, ar);
    }

    private ContentDtos.LocalizedText nullableText(String en, String ar) {
        return en == null && ar == null ? null : text(en, ar);
    }
}
