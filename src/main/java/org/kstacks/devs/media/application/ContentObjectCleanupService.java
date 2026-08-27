package org.kstacks.devs.media.application;

import jakarta.persistence.EntityManager;
import org.kstacks.devs.content.domain.ContentUnitEntity;
import org.kstacks.devs.content.domain.LearningContentEntity;
import org.kstacks.devs.media.domain.ContentCoverEntity;
import org.kstacks.devs.media.domain.ContentCoverRepository;
import org.kstacks.devs.media.domain.MediaAssetEntity;
import org.kstacks.devs.media.domain.MediaProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Plans and executes the external-object side of a trash purge.
 *
 * <p>Planning is a short read-only database transaction. Execution is
 * deliberately outside a database transaction: the durable purge claim held
 * by the content/lesson row prevents restores and new child writes while the
 * idempotent R2 deletes run. If the process stops after object deletion, the
 * claim remains and the next run repeats the deletes before retrying the DB
 * phase. This avoids holding a DB transaction open across network calls while
 * still making failure recoverable.</p>
 */
@Service
public class ContentObjectCleanupService {
    private final EntityManager entityManager;
    private final ContentCoverRepository covers;
    private final ObjectStorage storage;
    private final StaticHlsLocationResolver staticHlsLocations;

    public ContentObjectCleanupService(
        EntityManager entityManager,
        ContentCoverRepository covers,
        ObjectStorage storage,
        StaticHlsLocationResolver staticHlsLocations
    ) {
        this.entityManager = entityManager;
        this.covers = covers;
        this.storage = storage;
        this.staticHlsLocations = staticHlsLocations;
    }

    /** Exact object keys and validated immutable package prefixes to remove. */
    public record CleanupPlan(List<String> objectKeys, List<String> packagePrefixes) {
        public CleanupPlan {
            objectKeys = List.copyOf(new LinkedHashSet<>(objectKeys));
            packagePrefixes = List.copyOf(new LinkedHashSet<>(packagePrefixes));
        }
    }

    @Transactional(readOnly = true)
    public CleanupPlan prepareContentObjects(UUID contentId) {
        var content = entityManager.find(LearningContentEntity.class, contentId);
        if (content == null) return new CleanupPlan(List.of(), List.of());
        var unitIds = entityManager.createQuery(
            "select u.id from ContentUnitEntity u where u.content.id = :contentId", UUID.class
        ).setParameter("contentId", contentId).getResultList();
        return plan(unitIds, contentId, true);
    }

    @Transactional(readOnly = true)
    public CleanupPlan prepareUnitObjects(UUID unitId) {
        var unit = entityManager.find(ContentUnitEntity.class, unitId);
        if (unit == null) return new CleanupPlan(List.of(), List.of());
        var contentId = unit.getContent() == null ? null : unit.getContent().getId();
        return plan(List.of(unitId), contentId, false);
    }

    /**
     * Execute only the object keys captured by a plan. S3/R2 delete is
     * idempotent, so a missing object on a retry is treated as success by the
     * provider implementation.
     */
    public void execute(CleanupPlan plan) {
        for (var objectKey : plan.objectKeys()) storage.delete(objectKey);
        for (var prefix : plan.packagePrefixes()) storage.deletePrefix(prefix);
    }

    /**
     * Compatibility hook for callers that already claim a content row. New
     * purge code should call prepare + execute separately so network I/O is
     * visibly outside the planning transaction.
     */
    @Transactional(readOnly = true)
    public void cleanupObjectsForContent(UUID contentId) {
        execute(planContentObjectsInTransaction(contentId));
    }

    private CleanupPlan plan(List<UUID> unitIds, UUID contentId, boolean wholeContent) {
        var objectKeys = new LinkedHashSet<String>();
        var packagePrefixes = new LinkedHashSet<String>();

        var attachmentQuery = wholeContent
            ? "select a.objectKey from UnitAttachmentEntity a where a.unit.content.id = :contentId"
            : "select a.objectKey from UnitAttachmentEntity a where a.unit.id = :unitId";
        var attachmentQueryBuilder = entityManager.createQuery(attachmentQuery, String.class);
        attachmentQueryBuilder.setParameter(wholeContent ? "contentId" : "unitId", wholeContent ? contentId : unitIds.getFirst());
        objectKeys.addAll(attachmentQueryBuilder.getResultList());

        if (wholeContent) {
            covers.findByContentIdOrderByCreatedAtDesc(contentId).stream()
                .map(ContentCoverEntity::getObjectKey)
                .forEach(objectKeys::add);
        }

        var media = new ArrayList<MediaAssetEntity>();
        var currentMediaQuery = wholeContent
            ? "select distinct u.media from ContentUnitEntity u where u.content.id = :contentId and u.media is not null"
            : "select u.media from ContentUnitEntity u where u.id = :unitId and u.media is not null";
        var currentQuery = entityManager.createQuery(currentMediaQuery, MediaAssetEntity.class);
        currentQuery.setParameter(wholeContent ? "contentId" : "unitId", wholeContent ? contentId : unitIds.getFirst());
        media.addAll(currentQuery.getResultList());

        if (!unitIds.isEmpty()) {
            var retainedQuery = entityManager.createQuery(
                "select m from MediaAssetEntity m where m.retainedForUnitId in :unitIds", MediaAssetEntity.class
            ).setParameter("unitIds", unitIds);
            media.addAll(retainedQuery.getResultList());
        }

        var seen = new LinkedHashSet<UUID>();
        for (var asset : media) {
            if (!seen.add(asset.getId()) || isSharedOutsideScope(asset, contentId, unitIds, wholeContent)) continue;
            addMediaObject(asset, objectKeys, packagePrefixes);
        }
        return new CleanupPlan(new ArrayList<>(objectKeys), new ArrayList<>(packagePrefixes));
    }

    private boolean isSharedOutsideScope(
        MediaAssetEntity media,
        UUID contentId,
        List<UUID> unitIds,
        boolean wholeContent
    ) {
        var currentQuery = entityManager.createQuery(
            wholeContent
                ? "select count(u) from ContentUnitEntity u where u.media = :media and u.content.id <> :contentId"
                : "select count(u) from ContentUnitEntity u where u.media = :media and u.id <> :unitId",
            Long.class
        ).setParameter("media", media);
        currentQuery.setParameter(wholeContent ? "contentId" : "unitId", wholeContent ? contentId : unitIds.getFirst());
        if (currentQuery.getSingleResult() > 0) return true;

        var retainedUnitId = media.getRetainedForUnitId();
        return retainedUnitId != null && !unitIds.contains(retainedUnitId);
    }

    private void addMediaObject(
        MediaAssetEntity media,
        Set<String> objectKeys,
        Set<String> packagePrefixes
    ) {
        if (media.getProvider() == MediaProvider.STATIC_HLS) {
            if (media.getPlaybackPath() == null) {
                throw new IllegalStateException("Static HLS media has no playback manifest path");
            }
            packagePrefixes.add(staticHlsLocations.packagePrefix(media.getPlaybackPath()));
        } else if (media.getSourceObjectKey() != null) {
            objectKeys.add(media.getSourceObjectKey());
        }
    }

    private CleanupPlan planContentObjectsInTransaction(UUID contentId) {
        var content = entityManager.find(LearningContentEntity.class, contentId);
        if (content == null) return new CleanupPlan(List.of(), List.of());
        var unitIds = entityManager.createQuery(
            "select u.id from ContentUnitEntity u where u.content.id = :contentId", UUID.class
        ).setParameter("contentId", contentId).getResultList();
        return plan(unitIds, contentId, true);
    }
}
