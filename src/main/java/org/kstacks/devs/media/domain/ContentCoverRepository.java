package org.kstacks.devs.media.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentCoverRepository extends JpaRepository<ContentCoverEntity, UUID> {
    Optional<ContentCoverEntity> findFirstByContentIdAndStatusOrderByCreatedAtDesc(UUID contentId, CoverStatus status);
    List<ContentCoverEntity> findByContentIdOrderByCreatedAtDesc(UUID contentId);
    List<ContentCoverEntity> findByContentIdAndStatusOrderByCreatedAtDesc(UUID contentId, CoverStatus status);
    List<ContentCoverEntity> findByStatusAndPurgeAfterLessThanEqual(CoverStatus status, Instant cutoff);
    List<ContentCoverEntity> findByStatusAndCreatedAtLessThanEqual(CoverStatus status, Instant cutoff);
}
