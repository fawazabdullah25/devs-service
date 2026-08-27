package org.kstacks.devs.content.application;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.kstacks.devs.content.api.ContentDtos;
import org.kstacks.devs.content.domain.ContentKind;
import org.kstacks.devs.content.domain.ContentSectionEntity;
import org.kstacks.devs.content.domain.ContentUnitEntity;
import org.kstacks.devs.content.domain.ContentVisibility;
import org.kstacks.devs.content.domain.InstructorEntity;
import org.kstacks.devs.content.domain.InstructorRepository;
import org.kstacks.devs.content.domain.LearningContentEntity;
import org.kstacks.devs.content.domain.LearningContentRepository;
import org.kstacks.devs.content.domain.PublicationStatus;
import org.kstacks.devs.content.domain.SpokenLanguage;
import org.kstacks.devs.media.domain.MediaAssetEntity;
import org.kstacks.devs.media.domain.MediaAssetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * Database-backed regression coverage for the editorial contract.
 *
 * <p>These tests deliberately run through the services and migrated H2 schema,
 * rather than only asserting on managed entity fields. This catches lifecycle
 * filters, unique/check constraints, and persistence regressions together.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContentEditorialDatabaseTests {
    @Autowired private ContentService contentService;
    @Autowired private InstructorService instructorService;
    @Autowired private CurriculumService curriculumService;
    @Autowired private LearningContentRepository contents;
    @Autowired private InstructorRepository instructors;
    @Autowired private MediaAssetRepository mediaAssets;
    @Autowired private EntityManager entityManager;

    @Test
    void fullMetadataUpdatePersistsEditorialFieldsAndDoesNotMutateKind() {
        var content = saveContent("metadata", ContentKind.COURSE);
        var instructor = instructors.saveAndFlush(InstructorEntity.create(
            "Ada Lovelace", "آدا لوفليس", "Pioneer", "رائدة", "AL", null
        ));

        var updated = contentService.update(content.getId(), new ContentDtos.UpdateMetadataRequest(
            "Advanced Web", "ويب متقدم", content.getSlug(),
            "A bilingual summary", "ملخص ثنائي اللغة", "A detailed description", "وصف تفصيلي",
            ContentVisibility.AUTHENTICATED, SpokenLanguage.AR, "level-builder",
            List.of("topic-web", "git"), List.of(instructor.getId()), 3
        ));
        entityManager.flush();
        entityManager.clear();

        var persisted = contents.findDetailedById(content.getId()).orElseThrow();
        assertThat(updated.kind()).isEqualTo(ContentKind.COURSE);
        assertThat(persisted.getKind()).isEqualTo(ContentKind.COURSE);
        assertThat(persisted.getTitleEn()).isEqualTo("Advanced Web");
        assertThat(persisted.getTitleAr()).isEqualTo("ويب متقدم");
        assertThat(persisted.getSummaryEn()).isEqualTo("A bilingual summary");
        assertThat(persisted.getSummaryAr()).isEqualTo("ملخص ثنائي اللغة");
        assertThat(persisted.getDescriptionEn()).isEqualTo("A detailed description");
        assertThat(persisted.getDescriptionAr()).isEqualTo("وصف تفصيلي");
        assertThat(persisted.getVisibility()).isEqualTo(ContentVisibility.AUTHENTICATED);
        assertThat(persisted.getSpokenLanguage()).isEqualTo(SpokenLanguage.AR);
        assertThat(persisted.getLevelSlug()).isEqualTo("builder");
        assertThat(persisted.getTopicSlugs()).containsExactlyInAnyOrder("web", "git");
        assertThat(persisted.getInstructors()).extracting(InstructorEntity::getId).containsExactly(instructor.getId());
        assertThat(persisted.getFeaturedRank()).isEqualTo(3);
    }

    @Test
    void invalidReferencesAndDuplicateSlugAreRejected() {
        var content = saveContent("invalid-reference", ContentKind.COURSE);
        var duplicate = saveContent("duplicate-reference", ContentKind.COURSE);

        assertBadRequest(() -> contentService.update(content.getId(), update(content, content.getSlug() + "-other",
            List.of("does-not-exist"), List.of())));
        assertBadRequest(() -> contentService.update(content.getId(), update(content, content.getSlug(),
            List.of("web"), List.of(UUID.randomUUID()), "level-does-not-exist")));
        assertBadRequest(() -> contentService.update(content.getId(), update(content, content.getSlug(),
            List.of("web"), List.of(UUID.randomUUID()))));
        assertBadRequest(() -> contentService.update(content.getId(), update(content, content.getSlug(),
            List.of("web", "topic-web"), List.of())));
        assertConflict(() -> contentService.update(content.getId(), update(content, duplicate.getSlug(),
            List.of("web"), List.of())));
    }

    @Test
    void instructorCrudPreservesReservedAccountSubjectAndEnforcesNullableUniqueIndex() {
        var created = instructorService.create(new ContentDtos.InstructorCreateRequest(
            "Grace Hopper", "غريس هوبر", null, null, "GH", null
        ));
        var entity = instructors.findById(created.id()).orElseThrow();
        assertThat(entity.getAccountSubject()).isNull();

        entityManager.createNativeQuery("UPDATE instructors SET account_subject = :subject WHERE id = :id")
            .setParameter("subject", "account-subject-1")
            .setParameter("id", created.id())
            .executeUpdate();
        entityManager.clear();

        var updated = instructorService.update(created.id(), new ContentDtos.InstructorUpdateRequest(
            "Grace B. Hopper", "غريس ب. هوبر", "Compiler pioneer", null, "GBH", null
        ));
        assertThat(updated.nameEn()).isEqualTo("Grace B. Hopper");
        assertThat(instructors.findById(created.id()).orElseThrow().getAccountSubject())
            .isEqualTo("account-subject-1");

        var second = instructors.saveAndFlush(InstructorEntity.create("Second", null, null, null, "S", null));
        assertThatThrownBy(() -> entityManager.createNativeQuery(
            "UPDATE instructors SET account_subject = :subject WHERE id = :id"
        ).setParameter("subject", "account-subject-1").setParameter("id", second.getId()).executeUpdate())
            .isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
    }

    @Test
    void archiveThenUnarchiveReturnsDraft() {
        var content = saveContent("archive-cycle", ContentKind.COURSE);
        var archived = contentService.archive(content.getId());
        assertThat(archived.status()).isEqualTo(PublicationStatus.ARCHIVED);

        var restored = contentService.unarchive(content.getId());
        assertThat(restored.status()).isEqualTo(PublicationStatus.DRAFT);
        assertThat(contents.findById(content.getId()).orElseThrow().getStatus())
            .isEqualTo(PublicationStatus.DRAFT);
    }

    @Test
    void contentTrashIsExcludedFromAdminAndPublicReadsAndRestoresAsDraft() {
        var content = saveContent("content-trash", ContentKind.COURSE);
        content.publish();
        entityManager.flush();

        var beforeDelete = Instant.now();
        var deleted = contentService.delete(content.getId());
        assertThat(deleted.deletedAt()).isAfterOrEqualTo(beforeDelete);
        assertThat(deleted.purgeAfter()).isAfter(deleted.deletedAt());
        assertThat(contentService.adminContent()).noneMatch(item -> item.id().equals(content.getId()));
        assertThat(contentService.home().latest()).noneMatch(item -> item.id().equals(content.getId()));
        assertThat(contentService.catalog(null, null, null, null, null).items())
            .noneMatch(item -> item.id().equals(content.getId()));
        assertThat(contentService.deletedContent()).extracting(ContentDtos.LearningContent::id)
            .contains(content.getId());
        assertThatThrownBy(() -> contentService.getPublished(content.getSlug(), null))
            .isInstanceOf(ResponseStatusException.class);

        var restored = contentService.restore(content.getId());
        assertThat(restored.status()).isEqualTo(PublicationStatus.DRAFT);
        assertThat(restored.deletedAt()).isNull();
        assertThat(restored.purgeAfter()).isNull();
        assertThat(contentService.adminContent()).extracting(ContentDtos.LearningContent::id)
            .contains(content.getId());
    }

    @Test
    void lessonMetadataTrashAndRestoreUseActiveCurriculumAndSafePosition() {
        var content = saveContent("lesson-lifecycle", ContentKind.SERIES);
        var first = new ContentUnitEntity("first", 1, "First", null, null, null, null);
        var second = new ContentUnitEntity("second", 2, "Second", null, null, null, null);
        content.addUnit(first);
        content.addUnit(second);
        contents.saveAndFlush(content);

        contentService.updateUnit(content.getId(), first.getId(), new ContentDtos.UnitUpdateRequest(
            "first-edited", "First edited", "الأول", "Updated summary", "ملخص محدث"
        ));
        assertThat(contents.findDetailedById(content.getId()).orElseThrow().getUnits())
            .anyMatch(unit -> unit.getSlug().equals("first-edited") && unit.getTitleAr().equals("الأول"));

        var deleted = contentService.deleteUnit(content.getId(), first.getId());
        assertThat(deleted.deletedAt()).isNotNull();
        assertThat(deleted.sectionId()).isNull();
        assertThat(contentService.adminDetails(content.getId()).units())
            .extracting(ContentDtos.ContentUnit::id).containsExactly(second.getId());
        assertThat(contentService.deletedUnits(content.getId())).extracting(ContentDtos.ContentUnit::id)
            .containsExactly(first.getId());

        var restored = contentService.restoreUnit(content.getId(), first.getId());
        assertThat(restored.deletedAt()).isNull();
        assertThat(restored.sectionId()).isNull();
        assertThat(restored.position()).isEqualTo(1);
        assertThat(contentService.adminDetails(content.getId()).units())
            .extracting(ContentDtos.ContentUnit::id).containsExactly(first.getId(), second.getId());
    }

    @Test
    void publishingRejectsTrashedLessonsAndCurriculumCannotReferenceThem() {
        var content = saveContent("publish-trash", ContentKind.SERIES);
        var firstMedia = readyMedia("first");
        var secondMedia = readyMedia("second");
        mediaAssets.save(firstMedia);
        mediaAssets.save(secondMedia);
        content.addUnit(new ContentUnitEntity("first", 1, "First", null, null, null, firstMedia));
        content.addUnit(new ContentUnitEntity("second", 2, "Second", null, null, null, secondMedia));
        contents.saveAndFlush(content);
        var firstId = content.getUnits().get(0).getId();
        var secondId = content.getUnits().get(1).getId();

        contentService.deleteUnit(content.getId(), firstId);
        assertBadRequest(() -> contentService.publish(content.getId()));
        assertBadRequest(() -> curriculumService.replace(content.getId(), new ContentDtos.CurriculumRequest(
            List.of(), List.of(secondId, firstId)
        )));
    }

    private LearningContentEntity saveContent(String suffix, ContentKind kind) {
        return contents.saveAndFlush(LearningContentEntity.draft(
            suffix + "-" + UUID.randomUUID().toString().substring(0, 8),
            kind, ContentVisibility.PUBLIC, "Title", "Summary"
        ));
    }

    private ContentDtos.UpdateMetadataRequest update(
        LearningContentEntity content,
        String slug,
        List<String> topics,
        List<UUID> instructorIds
    ) {
        return update(content, slug, topics, instructorIds, "level-getting-started");
    }

    private ContentDtos.UpdateMetadataRequest update(
        LearningContentEntity content,
        String slug,
        List<String> topics,
        List<UUID> instructorIds,
        String level
    ) {
        return new ContentDtos.UpdateMetadataRequest(
            "Title", null, slug, "Summary", null, "Description", null,
            ContentVisibility.PUBLIC, SpokenLanguage.MIXED, level, topics, instructorIds, null
        );
    }

    private MediaAssetEntity readyMedia(String name) {
        return MediaAssetEntity.staticHls(
            "editorial-tests/" + name + "/" + UUID.randomUUID() + "/master.m3u8",
            60, null, "v1", List.of()
        );
    }

    private void assertBadRequest(ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode().value()).isEqualTo(400));
    }

    private void assertConflict(ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode().value()).isEqualTo(409));
    }
}
