package org.kstacks.devs.content.application;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.kstacks.devs.content.api.ContentDtos;
import org.kstacks.devs.content.domain.ContentKind;
import org.kstacks.devs.content.domain.ContentSectionEntity;
import org.kstacks.devs.content.domain.ContentUnitEntity;
import org.kstacks.devs.content.domain.ContentVisibility;
import org.kstacks.devs.content.domain.LearningContentEntity;
import org.kstacks.devs.content.domain.LearningContentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises curriculum replacement against the migrated schema instead of mocks.
 *
 * <p>The position columns are protected by database uniqueness and positive-value
 * constraints. Reordering all of the rows in one request must therefore flush
 * successfully before the final hierarchy is persisted.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CurriculumServiceDatabaseTests {
    @Autowired private CurriculumService service;
    @Autowired private LearningContentRepository repository;
    @Autowired private EntityManager entityManager;

    @Test
    void reorderingExistingSectionsAndLessonsFlushesAndPersistsTheFinalHierarchy() {
        var content = LearningContentEntity.draft(
            "database-curriculum-reorder", ContentKind.SERIES, ContentVisibility.PUBLIC,
            "Database curriculum", "Curriculum persistence test"
        );
        var foundations = new ContentSectionEntity(1, "Foundations", null, null, null);
        var practice = new ContentSectionEntity(2, "Practice", null, null, null);
        var first = new ContentUnitEntity("first", 1, "First", null, null, null, null);
        var second = new ContentUnitEntity("second", 2, "Second", null, null, null, null);
        var third = new ContentUnitEntity("third", 3, "Third", null, null, null, null);

        content.addSection(foundations);
        content.addSection(practice);
        content.addUnit(first);
        content.addUnit(second);
        content.addUnit(third);
        first.organize(foundations, 1);
        second.organize(foundations, 2);
        third.organize(practice, 3);
        repository.saveAndFlush(content);

        // Reversing both sections and lessons is the collision-prone operation
        // that previously attempted to use invalid negative temporary positions.
        service.replace(content.getId(), new ContentDtos.CurriculumRequest(
            List.of(
                curriculumSection(practice, third.getId(), first.getId()),
                curriculumSection(foundations, second.getId())
            ),
            List.of()
        ));

        // Force a fresh read from the database, rather than asserting only on
        // the already-managed entities in the current persistence context.
        entityManager.clear();
        var persisted = repository.findDetailedById(content.getId()).orElseThrow();

        assertThat(persisted.getSections())
            .extracting(ContentSectionEntity::getTitleEn)
            .containsExactly("Practice", "Foundations");
        assertThat(persisted.getSections())
            .extracting(ContentSectionEntity::getPosition)
            .containsExactly(1, 2);
        assertThat(persisted.getUnits())
            .extracting(ContentUnitEntity::getSlug)
            .containsExactly("third", "first", "second");
        assertThat(persisted.getUnits())
            .extracting(ContentUnitEntity::getPosition)
            .containsExactly(1, 2, 3);
        assertThat(persisted.getUnits().get(0).getSection().getId())
            .isEqualTo(persisted.getSections().get(0).getId());
        assertThat(persisted.getUnits().get(1).getSection().getId())
            .isEqualTo(persisted.getSections().get(0).getId());
        assertThat(persisted.getUnits().get(2).getSection().getId())
            .isEqualTo(persisted.getSections().get(1).getId());
    }

    private ContentDtos.CurriculumSectionRequest curriculumSection(
        ContentSectionEntity section,
        java.util.UUID... unitIds
    ) {
        return new ContentDtos.CurriculumSectionRequest(
            section.getId(), section.getTitleEn(), null, null, null, List.of(unitIds)
        );
    }
}
