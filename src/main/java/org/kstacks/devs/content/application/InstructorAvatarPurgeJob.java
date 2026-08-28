package org.kstacks.devs.content.application;

import org.kstacks.devs.config.AttachmentProperties;
import org.kstacks.devs.content.domain.InstructorAvatarRepository;
import org.kstacks.devs.content.domain.InstructorAvatarStatus;
import org.kstacks.devs.content.domain.InstructorRepository;
import org.kstacks.devs.media.application.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class InstructorAvatarPurgeJob {
    private static final Logger log = LoggerFactory.getLogger(InstructorAvatarPurgeJob.class);
    private final InstructorAvatarRepository repository;
    private final InstructorRepository instructors;
    private final ObjectStorage storage;
    private final AttachmentProperties properties;

    @org.springframework.beans.factory.annotation.Autowired
    public InstructorAvatarPurgeJob(InstructorAvatarRepository repository, InstructorRepository instructors, ObjectStorage storage, AttachmentProperties properties) {
        this.repository = repository;
        this.instructors = instructors;
        this.storage = storage;
        this.properties = properties;
    }

    /** Compatibility constructor for focused lifecycle tests. */
    public InstructorAvatarPurgeJob(InstructorAvatarRepository repository, ObjectStorage storage, AttachmentProperties properties) {
        this(repository, null, storage, properties);
    }

    @Scheduled(fixedDelayString = "${devs.media.purge-delay:PT1H}")
    @Transactional
    public void purgeExpired() {
        var now = Instant.now();
        for (var avatar : repository.findByStatusAndPurgeAfterLessThanEqual(InstructorAvatarStatus.DELETED, now)) {
            try {
                storage.delete(avatar.getObjectKey());
                repository.delete(avatar);
            } catch (RuntimeException exception) {
                log.warn("Instructor avatar purge failed for {} and will be retried", avatar.getId(), exception);
            }
        }
        var staleBefore = now.minus(properties.staleUploadAfter());
        for (var avatar : repository.findByStatusAndCreatedAtLessThanEqual(InstructorAvatarStatus.UPLOADING, staleBefore)) {
            try {
                if (storage.exists(avatar.getObjectKey())) storage.delete(avatar.getObjectKey());
                repository.delete(avatar);
            } catch (RuntimeException exception) {
                log.warn("Stale instructor avatar cleanup failed for {} and will be retried", avatar.getId(), exception);
            }
        }
        if (instructors != null) {
            for (var instructor : instructors.findAllByDeletedAtIsNotNullAndPurgeAfterLessThanEqualOrderByPurgeAfter(now)) {
                if (repository.countByInstructorId(instructor.getId()) == 0) instructors.delete(instructor);
            }
        }
    }
}
