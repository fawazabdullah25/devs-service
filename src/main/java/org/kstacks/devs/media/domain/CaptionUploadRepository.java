package org.kstacks.devs.media.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaptionUploadRepository extends JpaRepository<CaptionUploadEntity, UUID> {
    List<CaptionUploadEntity> findByObjectKeyIn(Collection<String> objectKeys);
    List<CaptionUploadEntity> findByMediaId(UUID mediaId);
    List<CaptionUploadEntity> findByStatusAndCreatedAtLessThanEqual(CaptionUploadStatus status, Instant cutoff);
}
