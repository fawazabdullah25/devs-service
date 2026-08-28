package org.kstacks.devs.content.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface InstructorAvatarRepository extends JpaRepository<InstructorAvatarEntity, UUID> {
    List<InstructorAvatarEntity> findAllByInstructorId(UUID instructorId);
    long countByInstructorId(UUID instructorId);
    java.util.Optional<InstructorAvatarEntity> findFirstByInstructorIdAndStatusOrderByCreatedAtDesc(
        UUID instructorId, InstructorAvatarStatus status
    );
    List<InstructorAvatarEntity> findByStatusAndPurgeAfterLessThanEqual(InstructorAvatarStatus status, Instant cutoff);
    List<InstructorAvatarEntity> findByStatusAndCreatedAtLessThanEqual(InstructorAvatarStatus status, Instant cutoff);
}
