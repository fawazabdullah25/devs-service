package org.kstacks.devs.media.domain;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAssetEntity, UUID> {
    Optional<MediaAssetEntity> findByPlaybackPath(String playbackPath);
    long countByStatus(MediaStatus status);

    List<MediaAssetEntity> findByRetainedForUnitIdOrderByCreatedAtDesc(UUID unitId);

    List<MediaAssetEntity> findByStatusAndCreatedAtLessThanEqual(MediaStatus status, Instant cutoff);

    @Query("""
        select m from MediaAssetEntity m
        where m.purgeAfter is not null and m.purgeAfter <= :cutoff
          and (m.status = :deleted or m.retainedForUnitId is not null)
          and not exists (select u.id from ContentUnitEntity u where u.media = m)
        order by m.purgeAfter asc
        """)
    List<MediaAssetEntity> findExpiredForPurge(
        @Param("cutoff") Instant cutoff,
        @Param("deleted") MediaStatus deleted
    );

    @Query("""
        select u.media.id, u.id, c.id, c.titleEn, c.titleAr, u.titleEn, u.titleAr
        from ContentUnitEntity u join u.content c
        where u.media is not null
        """)
    List<Object[]> findCurrentAttachmentRows();

    @Query("select count(u) from ContentUnitEntity u where u.media.id = :mediaId")
    long countCurrentAttachments(@Param("mediaId") UUID mediaId);
}
