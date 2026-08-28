package org.kstacks.devs.content.application;

import org.junit.jupiter.api.Test;
import org.kstacks.devs.config.AttachmentProperties;
import org.kstacks.devs.content.domain.InstructorAvatarEntity;
import org.kstacks.devs.content.domain.InstructorAvatarRepository;
import org.kstacks.devs.content.domain.InstructorAvatarStatus;
import org.kstacks.devs.content.domain.InstructorEntity;
import org.kstacks.devs.content.domain.InstructorRepository;
import org.kstacks.devs.media.application.ObjectStorage;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstructorAvatarPurgeJobTests {
    @Mock private InstructorAvatarRepository avatars;
    @Mock private InstructorRepository instructors;
    @Mock private ObjectStorage storage;

    @Test
    void deletesAvatarObjectBeforeRemovingItsRowAndThenRetiredProfile() {
        var avatar = InstructorAvatarEntity.uploading(
            UUID.randomUUID(), "instructor-avatar/profile/avatar.png", "avatar.png", "image/png", 10
        );
        avatar.softDelete(Duration.ZERO.plusSeconds(1));
        var profile = InstructorEntity.create("Ada", null, "", null, "A");
        profile.softDelete(Duration.ZERO.plusSeconds(1));
        var now = Instant.now().plusSeconds(2);

        when(avatars.findByStatusAndPurgeAfterLessThanEqual(eq(InstructorAvatarStatus.DELETED), any()))
            .thenReturn(List.of(avatar));
        when(avatars.findByStatusAndCreatedAtLessThanEqual(eq(InstructorAvatarStatus.UPLOADING), any()))
            .thenReturn(List.of());
        when(avatars.countByInstructorId(profile.getId())).thenReturn(0L);
        when(instructors.findAllByDeletedAtIsNotNullAndPurgeAfterLessThanEqualOrderByPurgeAfter(any()))
            .thenReturn(List.of(profile));

        new InstructorAvatarPurgeJob(
            avatars, instructors, storage,
            new AttachmentProperties(URI.create("https://assets.example/"), 1_000_000, 20,
                Duration.ofDays(7), Duration.ofHours(1), Duration.ofHours(24))
        ).purgeExpired();

        verify(storage).delete("instructor-avatar/profile/avatar.png");
        verify(avatars).delete(avatar);
        verify(instructors).delete(profile);
    }
}
