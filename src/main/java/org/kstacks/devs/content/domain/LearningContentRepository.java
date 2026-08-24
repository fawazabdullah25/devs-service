package org.kstacks.devs.content.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LearningContentRepository extends JpaRepository<LearningContentEntity, UUID>, JpaSpecificationExecutor<LearningContentEntity> {
    boolean existsBySlugAndIdNot(String slug, UUID id);
    boolean existsBySlug(String slug);
    long countByStatus(PublicationStatus status);

    @EntityGraph(attributePaths = {"units", "units.media", "sections", "instructors", "topicSlugs"})
    Optional<LearningContentEntity> findDetailedBySlug(String slug);

    @EntityGraph(attributePaths = {"units", "units.media", "sections", "instructors", "topicSlugs"})
    List<LearningContentEntity> findAllByOrderByUpdatedAtDesc();

    @EntityGraph(attributePaths = {"units", "units.media", "sections", "instructors", "topicSlugs"})
    Optional<LearningContentEntity> findDetailedById(UUID id);
}
