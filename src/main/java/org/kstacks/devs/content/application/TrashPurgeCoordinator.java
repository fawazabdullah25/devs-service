package org.kstacks.devs.content.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.kstacks.devs.content.domain.ContentUnitEntity;
import org.kstacks.devs.content.domain.LearningContentEntity;
import org.kstacks.devs.content.domain.TrashPurgeState;
import org.kstacks.devs.media.domain.MediaAssetEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Short database transactions for claiming and finalizing trash purges.
 * External object deletion is intentionally performed by the scheduled job
 * between these transactions.
 */
@Service
public class TrashPurgeCoordinator {
    private final EntityManager entityManager;

    public TrashPurgeCoordinator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public boolean claimContent(UUID contentId, Instant now) {
        var content = entityManager.find(LearningContentEntity.class, contentId, LockModeType.PESSIMISTIC_WRITE);
        if (content == null || !content.isDeleted() || content.getPurgeAfter() == null || content.getPurgeAfter().isAfter(now)) {
            return false;
        }
        // A lesson claim that predates the parent claim owns its own retry.
        // Let it finish before the aggregate is removed.
        var claimedChildren = entityManager.createQuery(
            "select count(u) from ContentUnitEntity u " +
                "where u.content.id = :contentId and u.purgeState = :claimed",
            Long.class
        ).setParameter("contentId", contentId)
            .setParameter("claimed", TrashPurgeState.CLAIMED)
            .getSingleResult();
        if (claimedChildren > 0) return false;
        return content.claimForPurge(now);
    }

    @Transactional
    public boolean claimUnit(UUID unitId, Instant now) {
        var contentId = entityManager.createQuery(
            "select u.content.id from ContentUnitEntity u where u.id = :unitId", UUID.class
        ).setParameter("unitId", unitId).getResultStream().findFirst().orElse(null);
        if (contentId == null) return false;

        // Always lock the parent before the child. Content claims use the same
        // order, which prevents a content/lesson purge deadlock.
        var content = entityManager.find(LearningContentEntity.class, contentId, LockModeType.PESSIMISTIC_WRITE);
        if (content == null || content.isPurgeClaimed() || due(content, now)) return false;
        var unit = entityManager.find(ContentUnitEntity.class, unitId, LockModeType.PESSIMISTIC_WRITE);
        return unit != null && unit.claimForPurge(now);
    }

    @Transactional
    public boolean finalizeUnit(UUID unitId) {
        var contentId = entityManager.createQuery(
            "select u.content.id from ContentUnitEntity u where u.id = :unitId", UUID.class
        ).setParameter("unitId", unitId).getResultStream().findFirst().orElse(null);
        if (contentId == null) return true;
        // Match claimUnit's parent-first lock order. This prevents a parent
        // purge and a child purge from waiting on one another indefinitely.
        var content = entityManager.find(LearningContentEntity.class, contentId, LockModeType.PESSIMISTIC_WRITE);
        var unit = entityManager.find(ContentUnitEntity.class, unitId, LockModeType.PESSIMISTIC_WRITE);
        if (content == null || unit == null) return true;
        if (content.isPurgeClaimed() || !unit.isPurgeClaimed()) return false;

        var media = mediaForUnits(List.of(unitId));
        entityManager.createQuery("update ContentUnitEntity u set u.media = null where u.id = :unitId")
            .setParameter("unitId", unitId)
            .executeUpdate();
        entityManager.flush();
        removeOwnedMedia(media, Set.of(unitId), unitId, false);
        entityManager.flush();

        entityManager.remove(unit);
        entityManager.flush();
        return true;
    }

    @Transactional
    public boolean finalizeContent(UUID contentId) {
        var content = entityManager.find(LearningContentEntity.class, contentId, LockModeType.PESSIMISTIC_WRITE);
        if (content == null) return true;
        if (!content.isPurgeClaimed()) return false;

        var unitIds = entityManager.createQuery(
            "select u.id from ContentUnitEntity u where u.content.id = :contentId", UUID.class
        ).setParameter("contentId", contentId).getResultList();
        var media = mediaForUnits(unitIds);
        entityManager.createQuery("update ContentUnitEntity u set u.media = null where u.content.id = :contentId")
            .setParameter("contentId", contentId)
            .executeUpdate();
        entityManager.flush();
        removeOwnedMedia(media, Set.copyOf(unitIds), contentId, true);
        entityManager.flush();

        // Unit attachments, sections, topics, instructors, and covers have
        // database-level cascade rules. Current media references and retained
        // version FKs were handled above before this aggregate delete.
        entityManager.remove(content);
        entityManager.flush();
        return true;
    }

    private Map<UUID, MediaAssetEntity> mediaForUnits(List<UUID> unitIds) {
        var media = new LinkedHashMap<UUID, MediaAssetEntity>();
        if (unitIds.isEmpty()) return media;
        entityManager.createQuery(
            "select distinct u.media from ContentUnitEntity u " +
                "where u.id in :unitIds and u.media is not null",
            MediaAssetEntity.class
        ).setParameter("unitIds", unitIds).getResultList()
            .forEach(item -> media.put(item.getId(), item));
        entityManager.createQuery(
            "select m from MediaAssetEntity m where m.retainedForUnitId in :unitIds",
            MediaAssetEntity.class
        ).setParameter("unitIds", unitIds).getResultList()
            .forEach(item -> media.put(item.getId(), item));
        return media;
    }

    private void removeOwnedMedia(
        Map<UUID, MediaAssetEntity> media,
        Set<UUID> unitIds,
        UUID scopeId,
        boolean wholeContent
    ) {
        for (var item : media.values()) {
            var currentOutside = entityManager.createQuery(
                wholeContent
                    ? "select count(u) from ContentUnitEntity u where u.media = :media and u.content.id <> :scopeId"
                    : "select count(u) from ContentUnitEntity u where u.media = :media and u.id <> :scopeId",
                Long.class
            ).setParameter("media", item)
                .setParameter("scopeId", scopeId)
                .getSingleResult();
            var retainedUnit = item.getRetainedForUnitId();
            if (currentOutside > 0) {
                if (retainedUnit != null && unitIds.contains(retainedUnit)) {
                    item.clearRetentionForUnit(retainedUnit);
                }
                continue;
            }
            if (retainedUnit != null && !unitIds.contains(retainedUnit)) continue;
            entityManager.remove(item);
        }
    }

    private boolean due(LearningContentEntity content, Instant now) {
        return content.isDeleted() && content.getPurgeAfter() != null && !content.getPurgeAfter().isAfter(now);
    }
}
