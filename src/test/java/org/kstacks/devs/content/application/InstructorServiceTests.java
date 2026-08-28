package org.kstacks.devs.content.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kstacks.devs.config.AttachmentProperties;
import org.kstacks.devs.content.domain.InstructorEntity;
import org.kstacks.devs.content.domain.InstructorRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstructorServiceTests {
    @Mock private InstructorRepository repository;
    @Mock private ContentMapper mapper;
    @Mock private InstructorAvatarService avatars;

    @Test
    void refusesToDeleteAnAssignedInstructorWithoutRetiringItsAvatar() {
        var id = UUID.randomUUID();
        var instructor = InstructorEntity.create("Ada", null, null, null, "A");
        when(repository.findById(id)).thenReturn(Optional.of(instructor));
        when(repository.countContentAssignments(id)).thenReturn(1L);

        assertThatThrownBy(() -> service().delete(id))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(409));

        verify(avatars, never()).retireForInstructor(id);
        assertThat(instructor.isDeleted()).isFalse();
    }

    @Test
    void softDeletesUnassignedInstructorAndRetiresEveryAvatarForPurge() {
        var id = UUID.randomUUID();
        var instructor = InstructorEntity.create("Ada", null, null, null, "A");
        when(repository.findById(id)).thenReturn(Optional.of(instructor));
        when(repository.countContentAssignments(id)).thenReturn(0L);

        service().delete(id);

        verify(avatars).retireForInstructor(id);
        assertThat(instructor.isDeleted()).isTrue();
        assertThat(instructor.getPurgeAfter()).isAfter(instructor.getDeletedAt());
    }

    private InstructorService service() {
        return new InstructorService(repository, mapper, avatars,
            new AttachmentProperties(URI.create("https://assets.example/"), 1_000_000, 20,
                Duration.ofDays(7), Duration.ofHours(1), Duration.ofHours(24)));
    }
}
