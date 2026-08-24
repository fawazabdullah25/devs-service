package org.kstacks.devs.content.domain;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LearningContentRepositoryTests {
    @Autowired private LearningContentRepository repository;
    @Autowired private EntityManager entityManager;

    @Test
    void detailedLookupLoadsSectionsAndTheirLessons() {
        var content = LearningContentEntity.draft(
            "repository-sections", ContentKind.SERIES, ContentVisibility.PUBLIC, "Repository sections", "Summary"
        );
        var section = new ContentSectionEntity(1, "Foundations", null, null, null);
        var lesson = new ContentUnitEntity("lesson", 1, "Lesson", null, null, null, null);
        content.addSection(section);
        content.addUnit(lesson);
        lesson.organize(section, 1);
        repository.saveAndFlush(content);
        entityManager.clear();

        var loaded = repository.findDetailedById(content.getId()).orElseThrow();

        assertThat(loaded.getSections()).extracting(ContentSectionEntity::getTitleEn)
            .containsExactly("Foundations");
        assertThat(loaded.getUnits()).extracting(unit -> unit.getSection().getId())
            .containsExactly(section.getId());
        assertThat(loaded.getUnits().getFirst().getAttachments()).isEmpty();
    }
}
