package org.kstacks.devs.content.application;

import org.kstacks.devs.content.domain.ContentUnitRepository;
import org.kstacks.devs.content.domain.LearningContentRepository;
import org.kstacks.devs.media.application.ContentObjectCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Bounded, retryable hard purge for rows whose seven-day trash retention has
 * elapsed. The claim is committed before R2 calls; the row therefore remains
 * a durable retry record if the process stops or storage is unavailable.
 */
@Component
public class TrashPurgeJob {
    private static final Logger log = LoggerFactory.getLogger(TrashPurgeJob.class);

    private final LearningContentRepository contents;
    private final ContentUnitRepository units;
    private final TrashPurgeCoordinator coordinator;
    private final ContentObjectCleanupService objectCleanup;

    public TrashPurgeJob(
        LearningContentRepository contents,
        ContentUnitRepository units,
        TrashPurgeCoordinator coordinator,
        ContentObjectCleanupService objectCleanup
    ) {
        this.contents = contents;
        this.units = units;
        this.coordinator = coordinator;
        this.objectCleanup = objectCleanup;
    }

    @Scheduled(fixedDelayString = "${devs.trash.purge-delay:PT1H}")
    public void purgeExpired() {
        var now = Instant.now();
        // Whole aggregates take precedence. A unit under a due content row is
        // skipped by claimUnit, so one lesson cannot be purged independently
        // while its parent is being removed.
        for (var contentId : contents.findDuePurgeIds(now)) purgeContent(contentId);
        for (var unitId : units.findDuePurgeIds(now)) purgeUnit(unitId);
    }

    private void purgeContent(java.util.UUID contentId) {
        try {
            if (!coordinator.claimContent(contentId, Instant.now())) return;
            var plan = objectCleanup.prepareContentObjects(contentId);
            objectCleanup.execute(plan);
            coordinator.finalizeContent(contentId);
        } catch (RuntimeException exception) {
            log.warn("Content purge failed for {} and will be retried", contentId, exception);
        }
    }

    private void purgeUnit(java.util.UUID unitId) {
        try {
            if (!coordinator.claimUnit(unitId, Instant.now())) return;
            var plan = objectCleanup.prepareUnitObjects(unitId);
            objectCleanup.execute(plan);
            coordinator.finalizeUnit(unitId);
        } catch (RuntimeException exception) {
            log.warn("Lesson purge failed for {} and will be retried", unitId, exception);
        }
    }
}
