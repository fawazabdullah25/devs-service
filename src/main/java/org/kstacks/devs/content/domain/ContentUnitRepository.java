package org.kstacks.devs.content.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ContentUnitRepository extends JpaRepository<ContentUnitEntity, UUID> {
    List<ContentUnitEntity> findByContentIdAndDeletedAtIsNullOrderByPosition(UUID contentId);
    List<ContentUnitEntity> findByContentIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(UUID contentId);
    List<ContentUnitEntity> findByDeletedAtIsNotNullAndPurgeAfterLessThanEqualOrderByPurgeAfter(Instant now);

    @org.springframework.data.jpa.repository.Query("""
        select u.id from ContentUnitEntity u
        where u.deletedAt is not null and u.purgeAfter <= :now
        order by u.purgeAfter asc
        """)
    List<UUID> findDuePurgeIds(@org.springframework.data.repository.query.Param("now") Instant now);
}
