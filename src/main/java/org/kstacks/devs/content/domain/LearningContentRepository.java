package org.kstacks.devs.content.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

public interface LearningContentRepository extends JpaRepository<LearningContentEntity, UUID>, JpaSpecificationExecutor<LearningContentEntity> {
    boolean existsBySlugAndIdNot(String slug, UUID id);
    boolean existsBySlug(String slug);
    long countByStatus(PublicationStatus status);
    long countByStatusAndDeletedAtIsNull(PublicationStatus status);

    @EntityGraph(attributePaths = {"units", "units.media"})
    Optional<LearningContentEntity> findDetailedBySlug(String slug);

    @EntityGraph(attributePaths = {"units", "units.media"})
    Optional<LearningContentEntity> findDetailedBySlugAndDeletedAtIsNull(String slug);

    @EntityGraph(attributePaths = {"units", "units.media"})
    List<LearningContentEntity> findAllByOrderByUpdatedAtDesc();

    @EntityGraph(attributePaths = {"units", "units.media"})
    List<LearningContentEntity> findAllByDeletedAtIsNullOrderByUpdatedAtDesc();

    @EntityGraph(attributePaths = {"units", "units.media"})
    List<LearningContentEntity> findAllByDeletedAtIsNotNullOrderByDeletedAtDesc();

    @EntityGraph(attributePaths = {"units", "units.media"})
    List<LearningContentEntity> findAllByDeletedAtIsNotNullAndPurgeAfterLessThanEqualOrderByPurgeAfter(
        Instant now
    );

    @org.springframework.data.jpa.repository.Query("""
        select c.id from LearningContentEntity c
        where c.deletedAt is not null and c.purgeAfter <= :now
        order by c.purgeAfter asc
        """)
    List<UUID> findDuePurgeIds(@org.springframework.data.repository.query.Param("now") Instant now);

    @EntityGraph(attributePaths = {"units", "units.media"})
    Optional<LearningContentEntity> findDetailedById(UUID id);

    @EntityGraph(attributePaths = {"units", "units.media"})
    Optional<LearningContentEntity> findDetailedByIdAndDeletedAtIsNull(UUID id);
}
