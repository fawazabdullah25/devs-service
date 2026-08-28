package org.kstacks.devs.content.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kstacks.devs.content.api.ContentDtos;
import org.kstacks.devs.content.domain.TagEntity;
import org.kstacks.devs.content.domain.TagGroup;
import org.kstacks.devs.content.domain.TagRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceTests {
    @Mock private TagRepository repository;

    @Test
    void createsAndNormalizesManagedTags() {
        when(repository.existsBySlug("frontend-basics")).thenReturn(false);
        when(repository.save(any(TagEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = new TagService(repository).create(new ContentDtos.TagCreateRequest(
            TagGroup.TOPIC, "Frontend basics", "أساسيات الواجهة", " Frontend-Basics "
        ));

        assertThat(created.group()).isEqualTo(TagGroup.TOPIC);
        assertThat(created.slug()).isEqualTo("frontend-basics");
        assertThat(created.name().en()).isEqualTo("Frontend basics");
    }

    @Test
    void refusesToDeleteAnAssignedTag() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(TagEntity.create(
            TagGroup.TOPIC, "web", "Web", null
        )));
        when(repository.countAssignments(id)).thenReturn(1L);

        assertThatThrownBy(() -> new TagService(repository).delete(id))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void refusesToMoveAnAssignedTagToAnotherGroup() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(TagEntity.create(
            TagGroup.TOPIC, "web", "Web", null
        )));
        when(repository.existsBySlugAndIdNot("web", id)).thenReturn(false);
        when(repository.countAssignments(id)).thenReturn(1L);

        assertThatThrownBy(() -> new TagService(repository).update(id, new ContentDtos.TagUpdateRequest(
            TagGroup.DIFFICULTY, "Web", null, "web"
        )))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(409));
    }
}
