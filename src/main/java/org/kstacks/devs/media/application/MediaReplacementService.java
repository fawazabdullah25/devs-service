package org.kstacks.devs.media.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.kstacks.devs.config.MediaProperties;
import org.kstacks.devs.content.domain.ContentUnitEntity;
import org.kstacks.devs.media.api.MediaDtos;
import org.kstacks.devs.media.domain.MediaAssetEntity;
import org.kstacks.devs.media.domain.MediaAssetRepository;
import org.kstacks.devs.media.domain.MediaStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MediaReplacementService {
    private final EntityManager entityManager;
    private final MediaAssetRepository mediaRepository;
    private final MediaService mediaService;
    private final MediaProperties properties;

    public MediaReplacementService(
        EntityManager entityManager,
        MediaAssetRepository mediaRepository,
        MediaService mediaService,
        MediaProperties properties
    ) {
        this.entityManager = entityManager;
        this.mediaRepository = mediaRepository;
        this.mediaService = mediaService;
        this.properties = properties;
    }

    @Transactional
    public MediaDtos.MediaVersion replace(
        UUID contentId,
        UUID unitId,
        MediaDtos.MediaReplacementRequest request
    ) {
        var unit = findUnit(contentId, unitId, true);
        var replacement = findMedia(request.mediaId(), true);
        var previous = unit.getMedia();

        if (previous != null && previous.getId().equals(replacement.getId())) {
            return mediaService.toVersion(replacement, true);
        }
        assertAttachable(replacement);
        if (mediaRepository.countCurrentAttachments(replacement.getId()) > 0) {
            throw conflict("Media is already attached to another lesson");
        }
        if (previous != null) {
            if (previous.getStatus() != MediaStatus.READY || previous.getDeletedAt() != null) {
                throw conflict("The current lesson media is not eligible for version retention");
            }
            previous.retainForUnit(unitId, properties.versionRetention());
        }
        // Materialize the response before clearing the persistence context in
        // setCurrentMedia(). Caption tracks are a lazy element collection and
        // must not be read from a detached replacement asset.
        var result = mediaService.toVersion(replacement, true);
        entityManager.flush();
        setCurrentMedia(unitId, replacement);
        return result;
    }

    @Transactional(readOnly = true)
    public List<MediaDtos.MediaVersion> versions(UUID contentId, UUID unitId) {
        var unit = findUnit(contentId, unitId, false);
        var versions = new ArrayList<MediaDtos.MediaVersion>();
        if (unit.getMedia() != null) versions.add(mediaService.toVersion(unit.getMedia(), true));
        mediaRepository.findByRetainedForUnitIdOrderByCreatedAtDesc(unitId).stream()
            .map(media -> mediaService.toVersion(media, false))
            .forEach(versions::add);
        return versions;
    }

    @Transactional
    public List<MediaDtos.MediaVersion> rollback(UUID contentId, UUID unitId, UUID mediaId) {
        var unit = findUnit(contentId, unitId, true);
        var selected = findMedia(mediaId, true);
        if (!unitId.equals(selected.getRetainedForUnitId()) || selected.getStatus() != MediaStatus.READY ||
            selected.getDeletedAt() == null) {
            throw conflict("Media is not a retained version for this lesson");
        }
        var current = unit.getMedia();
        if (current == null || current.getStatus() != MediaStatus.READY || current.getDeletedAt() != null) {
            throw conflict("The current lesson media is not eligible for version retention");
        }
        current.retainForUnit(unitId, properties.versionRetention());
        selected.restoreAsCurrent(unitId);
        entityManager.flush();
        setCurrentMedia(unitId, selected);
        return versions(contentId, unitId);
    }

    private ContentUnitEntity findUnit(UUID contentId, UUID unitId, boolean lock) {
        var unit = entityManager.find(
            ContentUnitEntity.class,
            unitId,
            lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE
        );
        if (unit == null || unit.getContent() == null || !contentId.equals(unit.getContent().getId())) {
            throw notFound("Lesson not found");
        }
        return unit;
    }

    private MediaAssetEntity findMedia(UUID mediaId, boolean lock) {
        if (lock) {
            var media = entityManager.find(MediaAssetEntity.class, mediaId, LockModeType.PESSIMISTIC_WRITE);
            if (media == null) throw notFound("Media not found");
            return media;
        }
        return mediaRepository.findById(mediaId).orElseThrow(() -> notFound("Media not found"));
    }

    private void assertAttachable(MediaAssetEntity media) {
        if (!media.isAvailableForAttachment()) {
            throw conflict("Only a ready, nondeleted media asset can be attached");
        }
    }

    private void setCurrentMedia(UUID unitId, MediaAssetEntity media) {
        entityManager.createQuery("update ContentUnitEntity u set u.media = :media where u.id = :unitId")
            .setParameter("media", media)
            .setParameter("unitId", unitId)
            .executeUpdate();
        entityManager.clear();
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
