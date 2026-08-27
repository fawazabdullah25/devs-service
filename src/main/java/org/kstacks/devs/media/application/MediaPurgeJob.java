package org.kstacks.devs.media.application;

import org.kstacks.devs.config.MediaProperties;
import org.kstacks.devs.media.domain.MediaAssetRepository;
import org.kstacks.devs.media.domain.MediaProvider;
import org.kstacks.devs.media.domain.MediaStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class MediaPurgeJob {
    private static final Logger log = LoggerFactory.getLogger(MediaPurgeJob.class);

    private final MediaAssetRepository repository;
    private final ObjectStorage storage;
    private final StaticHlsLocationResolver staticHlsLocations;
    private final MediaProperties properties;

    public MediaPurgeJob(
        MediaAssetRepository repository,
        ObjectStorage storage,
        StaticHlsLocationResolver staticHlsLocations,
        MediaProperties properties
    ) {
        this.repository = repository;
        this.storage = storage;
        this.staticHlsLocations = staticHlsLocations;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${devs.media.purge-delay:PT1H}")
    @Transactional
    public void purgeExpired() {
        for (var media : repository.findExpiredForPurge(Instant.now(), MediaStatus.DELETED)) {
            try {
                cleanupObjects(media);
                repository.delete(media);
            } catch (RuntimeException exception) {
                log.warn("Media purge failed for {} and will be retried", media.getId(), exception);
            }
        }
        var staleBefore = Instant.now().minus(properties.staleUploadAfter());
        for (var media : repository.findByStatusAndCreatedAtLessThanEqual(MediaStatus.UPLOADING, staleBefore)) {
            try {
                if (media.getSourceObjectKey() != null && storage.exists(media.getSourceObjectKey())) {
                    storage.delete(media.getSourceObjectKey());
                }
                repository.delete(media);
            } catch (RuntimeException exception) {
                log.warn("Stale media cleanup failed for {} and will be retried", media.getId(), exception);
            }
        }
    }

    private void cleanupObjects(org.kstacks.devs.media.domain.MediaAssetEntity media) {
        if (media.getProvider() == MediaProvider.STATIC_HLS) {
            if (media.getPlaybackPath() == null) {
                throw new IllegalStateException("Static HLS media has no playback manifest path");
            }
            storage.deletePrefix(staticHlsLocations.packagePrefix(media.getPlaybackPath()));
            return;
        }
        if (media.getSourceObjectKey() != null) storage.delete(media.getSourceObjectKey());
    }
}
