package org.kstacks.devs.media.application;

import org.kstacks.devs.content.domain.LearningContentRepository;
import org.kstacks.devs.content.domain.LearningContentEntity;
import org.kstacks.devs.media.api.MediaDtos;
import org.kstacks.devs.media.domain.ContentCoverEntity;
import org.kstacks.devs.media.domain.ContentCoverRepository;
import org.kstacks.devs.media.domain.CoverStatus;
import org.kstacks.devs.config.AttachmentProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CoverService {
    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
    private static final Map<String, String> CONTENT_TYPES = Map.of(
        "jpg", "image/jpeg",
        "jpeg", "image/jpeg",
        "png", "image/png",
        "webp", "image/webp",
        "avif", "image/avif"
    );
    private static final Set<String> ALLOWED_TYPES = Set.copyOf(CONTENT_TYPES.values());

    private final LearningContentRepository contents;
    private final ContentCoverRepository covers;
    private final ObjectStorage storage;
    private final AttachmentProperties storageProperties;
    private final org.kstacks.devs.config.MediaProperties mediaProperties;

    public CoverService(
        LearningContentRepository contents,
        ContentCoverRepository covers,
        ObjectStorage storage,
        AttachmentProperties storageProperties,
        org.kstacks.devs.config.MediaProperties mediaProperties
    ) {
        this.contents = contents;
        this.covers = covers;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.mediaProperties = mediaProperties;
    }

    @Transactional
    public MediaDtos.CoverUploadGrant requestUpload(UUID contentId, MediaDtos.CoverUploadRequest request) {
        requireEditableContent(contentId);
        var filename = safeFilename(request.filename());
        var extension = extension(filename);
        var contentType = request.contentType().trim().toLowerCase(Locale.ROOT);
        if (!CONTENT_TYPES.containsKey(extension) || !ALLOWED_TYPES.contains(contentType) ||
            !CONTENT_TYPES.get(extension).equals(contentType)) {
            throw badRequest("Cover must be a JPEG, PNG, WebP, or AVIF image");
        }
        if (request.contentLength() > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Cover exceeds the 10 MiB upload limit");
        }
        var objectKey = "cover/" + contentId + "/" + UUID.randomUUID() + "/" + filename;
        var cover = covers.save(ContentCoverEntity.uploading(
            contentId, objectKey, filename, contentType, request.contentLength()
        ));
        var grant = storage.signUpload(objectKey, contentType, request.contentLength(),
            "inline; filename*=UTF-8''" + encodeFilename(filename));
        return new MediaDtos.CoverUploadGrant(
            toDto(cover), grant.uploadUrl(), grant.objectKey(), grant.headers(), grant.expiresAt()
        );
    }

    @Transactional
    public MediaDtos.Cover complete(UUID contentId, MediaDtos.CoverCompleteRequest request) {
        var content = requireEditableContent(contentId);
        var cover = findOwned(contentId, request.coverId());
        if (cover.getStatus() != CoverStatus.UPLOADING) {
            if (cover.getStatus() == CoverStatus.READY) return toDto(cover);
            throw conflict("Cover is not awaiting upload confirmation");
        }
        if (!storage.exists(cover.getObjectKey())) {
            throw conflict("Cover upload is not complete");
        }
        var actualSize = storage.size(cover.getObjectKey());
        if (actualSize != cover.getContentLength()) {
            throw conflict("Uploaded cover size does not match the declared size");
        }
        covers.findFirstByContentIdAndStatusOrderByCreatedAtDesc(contentId, CoverStatus.READY)
            .filter(current -> !current.getId().equals(cover.getId()))
            .ifPresent(current -> {
                current.softDelete(mediaProperties.retention());
                covers.flush();
        });
        cover.ready();
        content.clearLegacyCoverUrl();
        return toDto(cover);
    }

    @Transactional
    public void delete(UUID contentId) {
        var content = requireEditableContent(contentId);
        covers.findFirstByContentIdAndStatusOrderByCreatedAtDesc(contentId, CoverStatus.READY)
            .ifPresent(current -> current.softDelete(mediaProperties.retention()));
        content.clearLegacyCoverUrl();
    }

    @Transactional(readOnly = true)
    public URI resolveActiveUrl(UUID contentId) {
        return covers.findFirstByContentIdAndStatusOrderByCreatedAtDesc(contentId, CoverStatus.READY)
            .map(cover -> storageProperties.publicBaseUrl().resolve(cover.getObjectKey()))
            .orElse(null);
    }

    public MediaDtos.Cover toDto(ContentCoverEntity cover) {
        var url = cover.getStatus() == CoverStatus.READY
            ? storageProperties.publicBaseUrl().resolve(cover.getObjectKey())
            : null;
        return new MediaDtos.Cover(
            cover.getId(), cover.getOriginalFilename(), cover.getContentType(), cover.getContentLength(),
            cover.getStatus().name(), url, cover.getCreatedAt(), cover.getUpdatedAt(),
            cover.getDeletedAt(), cover.getPurgeAfter()
        );
    }

    private ContentCoverEntity findOwned(UUID contentId, UUID coverId) {
        var cover = covers.findById(coverId).orElseThrow(() -> notFound("Cover not found"));
        if (!cover.getContentId().equals(contentId)) throw notFound("Cover not found");
        return cover;
    }

    private LearningContentEntity requireEditableContent(UUID contentId) {
        var content = contents.findById(contentId).orElseThrow(() -> notFound("Content not found"));
        if (content.isDeleted() || content.isPurgeClaimed()) {
            throw conflict("Trashed content cannot be changed");
        }
        return content;
    }

    private String safeFilename(String input) {
        var basename = input.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1).trim();
        var sanitized = basename.replaceAll("[^A-Za-z0-9._-]", "-").replaceAll("-+", "-");
        if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")) {
            throw badRequest("Cover filename is invalid");
        }
        return sanitized.substring(0, Math.min(sanitized.length(), 180));
    }

    private String extension(String filename) {
        var index = filename.lastIndexOf('.');
        return index < 1 ? "" : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String encodeFilename(String value) {
        var builder = new StringBuilder();
        for (byte octet : value.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            var unsigned = octet & 0xff;
            if ((unsigned >= 'a' && unsigned <= 'z') || (unsigned >= 'A' && unsigned <= 'Z') ||
                (unsigned >= '0' && unsigned <= '9') || "._-".indexOf(unsigned) >= 0) {
                builder.append((char) unsigned);
            } else {
                builder.append('%').append(String.format("%02X", unsigned));
            }
        }
        return builder.toString();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
