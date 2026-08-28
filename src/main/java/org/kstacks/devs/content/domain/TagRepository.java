package org.kstacks.devs.content.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<TagEntity, UUID> {
    List<TagEntity> findAllByOrderByGroupAscNameEnAsc();
    List<TagEntity> findAllByGroupOrderByNameEnAsc(TagGroup group);
    Optional<TagEntity> findBySlug(String slug);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, UUID id);

    @org.springframework.data.jpa.repository.Query(value = "select count(*) from content_tag_assignments where tag_id = :tagId", nativeQuery = true)
    long countAssignments(@org.springframework.data.repository.query.Param("tagId") UUID tagId);
}
