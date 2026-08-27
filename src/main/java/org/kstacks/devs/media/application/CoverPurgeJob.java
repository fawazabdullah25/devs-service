package org.kstacks.devs.media.application;

import org.kstacks.devs.config.MediaProperties;
import org.kstacks.devs.media.domain.ContentCoverRepository;
import org.kstacks.devs.media.domain.CoverStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class CoverPurgeJob {
    private static final Logger log = LoggerFactory.getLogger(CoverPurgeJob.class);
    private final ContentCoverRepository repository;
    private final ObjectStorage storage;
    private final MediaProperties properties;

    public CoverPurgeJob(ContentCoverRepository repository, ObjectStorage storage, MediaProperties properties) {
        this.repository = repository;
        this.storage = storage;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${devs.media.purge-delay:PT1H}")
    @Transactional
    public void purgeExpired() {
        var now = Instant.now();
        for (var cover : repository.findByStatusAndPurgeAfterLessThanEqual(CoverStatus.DELETED, now)) {
            try {
                storage.delete(cover.getObjectKey());
                repository.delete(cover);
            } catch (RuntimeException exception) {
                log.warn("Cover purge failed for {} and will be retried", cover.getId(), exception);
            }
        }
        var staleBefore = now.minus(properties.staleUploadAfter());
        for (var cover : repository.findByStatusAndCreatedAtLessThanEqual(CoverStatus.UPLOADING, staleBefore)) {
            try {
                if (storage.exists(cover.getObjectKey())) storage.delete(cover.getObjectKey());
                repository.delete(cover);
            } catch (RuntimeException exception) {
                log.warn("Stale cover cleanup failed for {} and will be retried", cover.getId(), exception);
            }
        }
    }
}
