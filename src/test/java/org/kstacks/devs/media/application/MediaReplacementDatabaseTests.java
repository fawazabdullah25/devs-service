package org.kstacks.devs.media.application;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.kstacks.devs.content.domain.ContentKind;
import org.kstacks.devs.content.domain.ContentUnitEntity;
import org.kstacks.devs.content.domain.ContentVisibility;
import org.kstacks.devs.content.domain.LearningContentEntity;
import org.kstacks.devs.content.domain.LearningContentRepository;
import org.kstacks.devs.media.api.MediaDtos;
import org.kstacks.devs.media.domain.MediaAssetEntity;
import org.kstacks.devs.media.domain.MediaAssetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies media replacement and rollback against the real migrated schema. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MediaReplacementDatabaseTests {
    @Autowired private MediaReplacementService service;
    @Autowired private LearningContentRepository contents;
    @Autowired private MediaAssetRepository mediaRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void replacesAndRollsBackAReadyLessonMediaWithRetainedVersions() {
        var content = LearningContentEntity.draft(
            "media-versioning-" + java.util.UUID.randomUUID(), ContentKind.SERIES,
            ContentVisibility.PUBLIC, "Media versioning", "Media versioning test"
        );
        var original = MediaAssetEntity.staticHls(
            "pilots/media-versioning/original/master.m3u8", 60, null, "v1", List.of()
        );
        var replacement = MediaAssetEntity.staticHls(
            "pilots/media-versioning/replacement/master.m3u8", 90, null, "v1", List.of()
        );
        mediaRepository.save(original);
        mediaRepository.save(replacement);
        var unit = new ContentUnitEntity("lesson", 1, "Lesson", null, null, null, original);
        content.addUnit(unit);
        contents.saveAndFlush(content);

        var replaced = service.replace(
            content.getId(), unit.getId(), new MediaDtos.MediaReplacementRequest(replacement.getId())
        );

        assertThat(replaced.mediaId()).isEqualTo(replacement.getId());
        entityManager.clear();
        var currentAfterReplace = entityManager.find(ContentUnitEntity.class, unit.getId());
        assertThat(currentAfterReplace.getMedia().getId()).isEqualTo(replacement.getId());
        var retained = mediaRepository.findByRetainedForUnitIdOrderByCreatedAtDesc(unit.getId());
        assertThat(retained).extracting(MediaAssetEntity::getId).containsExactly(original.getId());
        assertThat(retained.getFirst().getDeletedAt()).isNotNull();

        var versions = service.rollback(content.getId(), unit.getId(), original.getId());

        assertThat(versions).filteredOn(MediaDtos.MediaVersion::current)
            .extracting(MediaDtos.MediaVersion::mediaId).containsExactly(original.getId());
        entityManager.clear();
        var currentAfterRollback = entityManager.find(ContentUnitEntity.class, unit.getId());
        assertThat(currentAfterRollback.getMedia().getId()).isEqualTo(original.getId());
        assertThat(mediaRepository.findByRetainedForUnitIdOrderByCreatedAtDesc(unit.getId()))
            .extracting(MediaAssetEntity::getId).containsExactly(replacement.getId());
    }
}
