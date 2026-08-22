package org.kstacks.devs.attachment.application;

import org.kstacks.devs.attachment.domain.AttachmentStatus;
import org.kstacks.devs.attachment.domain.UnitAttachmentRepository;
import org.kstacks.devs.media.application.ObjectStorage;
import org.kstacks.devs.config.AttachmentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class AttachmentPurgeJob {
    private static final Logger log = LoggerFactory.getLogger(AttachmentPurgeJob.class);
    private final UnitAttachmentRepository repository;
    private final ObjectStorage storage;
    private final AttachmentProperties properties;

    public AttachmentPurgeJob(UnitAttachmentRepository repository, ObjectStorage storage, AttachmentProperties properties) {
        this.repository = repository; this.storage = storage; this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${devs.attachments.purge-delay:PT1H}")
    @Transactional
    public void purgeExpired() {
        for (var attachment : repository.findByStatusAndPurgeAfterLessThanEqual(AttachmentStatus.DELETED, Instant.now())) {
            try {
                storage.delete(attachment.getObjectKey());
                repository.delete(attachment);
            } catch (RuntimeException exception) {
                log.warn("Attachment purge failed for {} and will be retried", attachment.getId(), exception);
            }
        }
        var staleBefore = Instant.now().minus(properties.staleUploadAfter());
        for (var attachment : repository.findByStatusAndCreatedAtLessThanEqual(AttachmentStatus.UPLOADING, staleBefore)) {
            try {
                if (storage.exists(attachment.getObjectKey())) storage.delete(attachment.getObjectKey());
                repository.delete(attachment);
            } catch (RuntimeException exception) {
                log.warn("Stale attachment cleanup failed for {} and will be retried", attachment.getId(), exception);
            }
        }
    }
}
