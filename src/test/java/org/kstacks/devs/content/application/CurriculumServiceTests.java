package org.kstacks.devs.content.application;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kstacks.devs.content.api.ContentDtos;
import org.kstacks.devs.content.domain.ContentKind;
import org.kstacks.devs.content.domain.ContentSectionEntity;
import org.kstacks.devs.content.domain.ContentUnitEntity;
import org.kstacks.devs.content.domain.ContentVisibility;
import org.kstacks.devs.content.domain.LearningContentEntity;
import org.kstacks.devs.content.domain.LearningContentRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurriculumServiceTests {
    @Mock private LearningContentRepository repository;
    @Mock private ContentMapper mapper;
    @Mock private EntityManager entityManager;
    private CurriculumService service;

    @BeforeEach
    void setUp() {
        service = new CurriculumService(repository, mapper, entityManager);
    }

    @Test
    void replacesTheCompleteCurriculumAndRenumbersLessonsAtomically() {
        var content = series();
        var first = unit("first", 1);
        var second = unit("second", 2);
        var third = unit("third", 3);
        content.addUnit(first);
        content.addUnit(second);
        content.addUnit(third);
        when(repository.findDetailedById(content.getId())).thenReturn(Optional.of(content));

        service.replace(content.getId(), new ContentDtos.CurriculumRequest(
            List.of(
                section("Foundations", first.getId(), second.getId()),
                section("Practice", third.getId())
            ),
            List.of()
        ));

        assertThat(content.getSections()).extracting(ContentSectionEntity::getTitleEn)
            .containsExactly("Foundations", "Practice");
        assertThat(first.getPosition()).isEqualTo(1);
        assertThat(second.getPosition()).isEqualTo(2);
        assertThat(third.getPosition()).isEqualTo(3);
        assertThat(first.getSection()).isEqualTo(content.getSections().get(0));
        assertThat(third.getSection()).isEqualTo(content.getSections().get(1));
    }

    @Test
    void deletingASectionPreservesItsLessonsAsUnsectioned() {
        var content = series();
        var lesson = unit("lesson", 1);
        var section = new ContentSectionEntity(1, "Start", null, null, null);
        content.addSection(section);
        content.addUnit(lesson);
        lesson.organize(section, 1);
        when(repository.findDetailedById(content.getId())).thenReturn(Optional.of(content));

        service.replace(content.getId(), new ContentDtos.CurriculumRequest(List.of(), List.of(lesson.getId())));

        assertThat(content.getSections()).isEmpty();
        assertThat(lesson.getSection()).isNull();
        assertThat(lesson.getPosition()).isEqualTo(1);
    }

    @Test
    void rejectsIncompleteAndDuplicatedCurriculaBeforeChangingLessons() {
        var content = series();
        var first = unit("first", 1);
        var second = unit("second", 2);
        content.addUnit(first);
        content.addUnit(second);
        when(repository.findDetailedById(content.getId())).thenReturn(Optional.of(content));

        assertThatThrownBy(() -> service.replace(content.getId(), new ContentDtos.CurriculumRequest(
            List.of(section("Repeated", first.getId(), first.getId())),
            List.of(second.getId())
        ))).isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(first.getPosition()).isEqualTo(1);
        assertThat(second.getPosition()).isEqualTo(2);

        assertThatThrownBy(() -> service.replace(content.getId(), new ContentDtos.CurriculumRequest(
            List.of(section("Missing", first.getId())),
            List.of()
        ))).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void publishedSectionedSeriesMustRemainComplete() {
        var content = series();
        var first = unit("first", 1);
        var second = unit("second", 2);
        content.addUnit(first);
        content.addUnit(second);
        content.publish();
        when(repository.findDetailedById(content.getId())).thenReturn(Optional.of(content));

        assertThatThrownBy(() -> service.replace(content.getId(), new ContentDtos.CurriculumRequest(
            List.of(section("Empty"), section("Lessons", first.getId())),
            List.of(second.getId())
        ))).isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void coursesCannotContainSections() {
        var content = LearningContentEntity.draft(
            "course", ContentKind.COURSE, ContentVisibility.PUBLIC, "Course", "Summary"
        );
        when(repository.findDetailedById(content.getId())).thenReturn(Optional.of(content));

        assertThatThrownBy(() -> service.replace(content.getId(), new ContentDtos.CurriculumRequest(
            List.of(section("Invalid")), List.of()
        ))).isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private LearningContentEntity series() {
        return LearningContentEntity.draft(
            "series", ContentKind.SERIES, ContentVisibility.PUBLIC, "Series", "Summary"
        );
    }

    private ContentUnitEntity unit(String slug, int position) {
        return new ContentUnitEntity(slug, position, slug, null, null, null, null);
    }

    private ContentDtos.CurriculumSectionRequest section(String title, UUID... unitIds) {
        return new ContentDtos.CurriculumSectionRequest(
            null, title, null, null, null, List.of(unitIds)
        );
    }
}
