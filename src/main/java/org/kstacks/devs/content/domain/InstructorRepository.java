package org.kstacks.devs.content.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InstructorRepository extends JpaRepository<InstructorEntity, UUID> {
    List<InstructorEntity> findAllByOrderByNameEnAsc();
    List<InstructorEntity> findAllByDeletedAtIsNullOrderByNameEnAsc();
    List<InstructorEntity> findAllByDeletedAtIsNotNullAndPurgeAfterLessThanEqualOrderByPurgeAfter( java.time.Instant cutoff);
    boolean existsByIdAndDeletedAtIsNull(UUID id);

    @org.springframework.data.jpa.repository.Query(value = "select count(*) from content_instructors where instructor_id = :id", nativeQuery = true)
    long countContentAssignments(@org.springframework.data.repository.query.Param("id") UUID id);
}
