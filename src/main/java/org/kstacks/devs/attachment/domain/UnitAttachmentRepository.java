package org.kstacks.devs.attachment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UnitAttachmentRepository extends JpaRepository<UnitAttachmentEntity, UUID> {
    long countByUnitIdAndStatusNot(UUID unitId, AttachmentStatus status);
    List<UnitAttachmentEntity> findByUnitIdAndStatusOrderByPosition(UUID unitId, AttachmentStatus status);
    List<UnitAttachmentEntity> findByStatusAndPurgeAfterLessThanEqual(AttachmentStatus status, Instant purgeAfter);
    List<UnitAttachmentEntity> findByStatusAndCreatedAtLessThanEqual(AttachmentStatus status, Instant createdAt);
}
