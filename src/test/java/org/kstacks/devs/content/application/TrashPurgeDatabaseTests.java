package org.kstacks.devs.content.application;

import org.junit.jupiter.api.Test;
import org.kstacks.devs.attachment.domain.AttachmentStatus;
import org.kstacks.devs.attachment.domain.UnitAttachmentEntity;
import org.kstacks.devs.attachment.domain.UnitAttachmentRepository;
import org.kstacks.devs.content.domain.ContentKind;
import org.kstacks.devs.content.domain.ContentUnitEntity;
import org.kstacks.devs.content.domain.ContentUnitRepository;
import org.kstacks.devs.content.domain.ContentVisibility;
import org.kstacks.devs.content.domain.LearningContentEntity;
import org.kstacks.devs.content.domain.LearningContentRepository;
import org.kstacks.devs.media.application.ContentObjectCleanupService;
import org.kstacks.devs.media.application.ObjectStorage;
import org.kstacks.devs.media.domain.ContentCoverEntity;
import org.kstacks.devs.media.domain.ContentCoverRepository;
import org.kstacks.devs.media.domain.CoverStatus;
import org.kstacks.devs.media.domain.CaptionUploadEntity;
import org.kstacks.devs.media.domain.CaptionUploadRepository;
import org.kstacks.devs.media.domain.MediaAssetEntity;
import org.kstacks.devs.media.domain.MediaAssetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/** Exercises the durable claim and FK-safe hard purge path on H2. */
@SpringBootTest
@ActiveProfiles("test")
class TrashPurgeDatabaseTests {
    @Autowired private TrashPurgeJob job;
    @Autowired private LearningContentRepository contents;
    @Autowired private ContentUnitRepository units;
    @Autowired private UnitAttachmentRepository attachments;
    @Autowired private ContentCoverRepository covers;
    @Autowired private MediaAssetRepository media;
    @Autowired private CaptionUploadRepository captionUploads;

    @MockitoBean private ObjectStorage storage;

    @Test
    void purgingOneLessonRemovesItsObjectsButLeavesTheSiblingLesson() {
        var content = LearningContentEntity.draft(
            "lesson-purge-" + java.util.UUID.randomUUID(), ContentKind.SERIES,
            ContentVisibility.PUBLIC, "Lesson purge", "Lesson purge test"
        );
        var removedMedia = MediaAssetEntity.staticHls("pilots/purge/removed/master.m3u8", 60, null, "v1", List.of());
        var siblingMedia = MediaAssetEntity.staticHls("pilots/purge/sibling/master.m3u8", 60, null, "v1", List.of());
        media.saveAll(List.of(removedMedia, siblingMedia));
        var removed = new ContentUnitEntity("removed", 1, "Removed", null, null, null, removedMedia);
        var sibling = new ContentUnitEntity("sibling", 2, "Sibling", null, null, null, siblingMedia);
        content.addUnit(removed);
        content.addUnit(sibling);
        contents.saveAndFlush(content);
        var attachment = UnitAttachmentEntity.uploading(
            removed, "attachments/" + removed.getId() + "/file/notes.pdf", "notes.pdf",
            "application/pdf", 10, "Notes", null, 1
        );
        attachment.ready();
        attachments.save(attachment);
        var captionUpload = CaptionUploadEntity.uploading(
            java.util.UUID.randomUUID(),
            "pilots/captions/" + removedMedia.getId() + "/en.vtt",
            "en.vtt", "text/vtt", 32
        );
        captionUpload.complete();
        captionUpload.attachTo(removedMedia.getId());
        captionUploads.save(captionUpload);
        removed.softDelete(Duration.ZERO);
        units.saveAndFlush(removed);

        job.purgeExpired();

        assertThat(units.findById(removed.getId())).isEmpty();
        assertThat(units.findById(sibling.getId())).isPresent();
        assertThat(media.findById(removedMedia.getId())).isEmpty();
        assertThat(media.findById(siblingMedia.getId())).isPresent();
        verify(storage).delete("attachments/" + removed.getId() + "/file/notes.pdf");
        verify(storage).delete("pilots/captions/" + removedMedia.getId() + "/en.vtt");
        verify(storage).deletePrefix("pilots/purge/removed/");
    }

    @Test
    void contentPurgeHandlesUploadingAndDeletedCoversAndRemovesTheAggregate() {
        var content = LearningContentEntity.draft(
            "aggregate-purge-" + java.util.UUID.randomUUID(), ContentKind.COURSE,
            ContentVisibility.PUBLIC, "Aggregate purge", "Aggregate purge test"
        );
        var asset = MediaAssetEntity.staticHls("pilots/purge/aggregate/master.m3u8", 60, null, "v1", List.of());
        media.save(asset);
        var unit = new ContentUnitEntity("lesson", 1, "Lesson", null, null, null, asset);
        content.addUnit(unit);
        contents.saveAndFlush(content);
        var uploadingCover = ContentCoverEntity.uploading(
            content.getId(), "cover/" + content.getId() + "/upload/cover.png", "cover.png", "image/png", 10
        );
        var deletedCover = ContentCoverEntity.uploading(
            content.getId(), "cover/" + content.getId() + "/deleted/cover.png", "cover.png", "image/png", 10
        );
        deletedCover.ready();
        deletedCover.softDelete(Duration.ZERO);
        covers.saveAll(List.of(uploadingCover, deletedCover));
        content.softDelete(Duration.ZERO);
        contents.saveAndFlush(content);

        job.purgeExpired();

        assertThat(contents.findById(content.getId())).isEmpty();
        assertThat(units.findById(unit.getId())).isEmpty();
        assertThat(media.findById(asset.getId())).isEmpty();
        assertThat(covers.findById(uploadingCover.getId())).isEmpty();
        assertThat(covers.findById(deletedCover.getId())).isEmpty();
        verify(storage).delete("cover/" + content.getId() + "/upload/cover.png");
        verify(storage).delete("cover/" + content.getId() + "/deleted/cover.png");
        verify(storage).deletePrefix("pilots/purge/aggregate/");
    }

    @Test
    void storageFailureLeavesAClaimedLessonForAnIdempotentRetry() {
        var content = LearningContentEntity.draft(
            "retry-purge-" + java.util.UUID.randomUUID(), ContentKind.COURSE,
            ContentVisibility.PUBLIC, "Retry purge", "Retry purge test"
        );
        var asset = MediaAssetEntity.staticHls("pilots/purge/retry/master.m3u8", 60, null, "v1", List.of());
        media.save(asset);
        var unit = new ContentUnitEntity("lesson", 1, "Lesson", null, null, null, asset);
        content.addUnit(unit);
        contents.saveAndFlush(content);
        unit.softDelete(Duration.ZERO);
        units.saveAndFlush(unit);
        doThrow(new IllegalStateException("temporary R2 outage"))
            .when(storage).deletePrefix("pilots/purge/retry/");

        job.purgeExpired();

        var claimed = units.findById(unit.getId()).orElseThrow();
        assertThat(claimed.isPurgeClaimed()).isTrue();
        assertThat(claimed.isDeleted()).isTrue();
        assertThat(media.findById(asset.getId())).isPresent();

        reset(storage);
        job.purgeExpired();

        assertThat(units.findById(unit.getId())).isEmpty();
        assertThat(media.findById(asset.getId())).isEmpty();
    }
}
