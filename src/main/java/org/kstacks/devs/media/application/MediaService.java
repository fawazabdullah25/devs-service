package org.kstacks.devs.media.application;

import org.kstacks.devs.config.MediaProperties;
import org.kstacks.devs.media.api.MediaDtos;
import org.kstacks.devs.media.domain.CaptionUploadEntity;
import org.kstacks.devs.media.domain.CaptionUploadRepository;
import org.kstacks.devs.media.domain.CaptionUploadStatus;
import org.kstacks.devs.media.domain.MediaAssetEntity;
import org.kstacks.devs.media.domain.MediaAssetRepository;
import org.kstacks.devs.media.domain.MediaCaptionTrack;
import org.kstacks.devs.media.domain.MediaStatus;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * Application service for the static-HLS media catalog.
 *
 * <p>Video bytes are prepared and published by the encoding pipeline. Devs
 * only registers an immutable public HLS package after validating its master,
 * renditions, and optional caption files. Caption metadata is intentionally
 * mutable because changing a label/default track must not require re-encoding
 * or replacing the video package.</p>
 */
@Service
public class MediaService {
    private static final long MAX_CAPTION_UPLOAD_BYTES = 5L * 1024 * 1024;
    private static final int MAX_CAPTION_PATH_LENGTH = 1024;
    private static final Pattern LANGUAGE = Pattern.compile("^[a-z]{2,8}(?:-[a-z0-9]{1,8})*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> CAPTION_CONTENT_TYPES = Set.of(
        "text/vtt", "text/plain", "application/vtt"
    );

    private final MediaAssetRepository repository;
    private final CaptionUploadRepository captionUploads;
    private final ObjectStorage storage;
    private final MediaProperties properties;
    private final StaticHlsPackageValidator staticHlsValidator;
    private final StaticHlsLocationResolver staticHlsLocations;

    public MediaService(
        MediaAssetRepository repository,
        CaptionUploadRepository captionUploads,
        ObjectStorage storage,
        MediaProperties properties,
        StaticHlsPackageValidator staticHlsValidator,
        StaticHlsLocationResolver staticHlsLocations
    ) {
        this.repository = repository;
        this.captionUploads = captionUploads;
        this.storage = storage;
        this.properties = properties;
        this.staticHlsValidator = staticHlsValidator;
        this.staticHlsLocations = staticHlsLocations;
    }

    /**
     * Registers a pre-built HLS package. The duration supplied by a client is
     * deliberately not accepted: all renditions are inspected and the value
     * persisted in the database is calculated from their EXTINF entries.
     */
    @Transactional
    public MediaDtos.StaticHlsRegistrationResponse registerStaticHls(MediaDtos.StaticHlsRegistrationRequest request) {
        if (request == null) throw badRequest("Static HLS registration is required");
        var manifestPath = staticHlsLocations.manifestPath(request.manifestPath());
        var captions = normalizeCaptions(request.captions());
        var checksum = normalizeChecksum(request.checksumSha256());
        var encodingVersion = required(request.encodingVersion(), "Encoding version");
        var validation = staticHlsValidator.validate(manifestPath, captions);
        if (validation == null) {
            throw new IllegalStateException("Static HLS validator returned no validation result");
        }

        var existing = repository.findByPlaybackPath(manifestPath).orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.getChecksumSha256(), checksum) ||
                !Objects.equals(existing.getEncodingVersion(), encodingVersion)) {
                throw conflict("This immutable HLS path is already registered with different metadata");
            }
            // Caption metadata is the one intentionally mutable part of an
            // asset. This also makes registration idempotent when an operator
            // adds a VTT file after the initial package registration.
            if (!sameCaptions(existing.getCaptionTracks(), captions)) {
                staticHlsValidator.validateCaptions(captions);
                existing.replaceCaptionTracks(captions);
            }
            attachCaptionUploads(existing.getId(), captions);
            return staticHlsResponse(existing);
        }

        var media = repository.save(MediaAssetEntity.staticHls(
            manifestPath,
            validation.durationSeconds(),
            checksum,
            encodingVersion,
            captions
        ));
        attachCaptionUploads(media.getId(), captions);
        return staticHlsResponse(media);
    }

    /** Replace caption metadata without changing the immutable HLS package. */
    @Transactional
    public MediaDtos.MediaStatusResponse updateCaptions(UUID mediaId, MediaDtos.CaptionUpdateRequest request) {
        if (request == null) throw badRequest("Caption update is required");
        var media = find(mediaId);
        if (media.getStatus() != MediaStatus.READY || media.getDeletedAt() != null ||
            media.getRetainedForUnitId() != null) {
            throw conflict("Only the current ready media asset can have captions edited");
        }
        var captions = normalizeCaptions(request.captions());
        staticHlsValidator.validateCaptions(captions);
        media.replaceCaptionTracks(captions);
        attachCaptionUploads(mediaId, captions);
        return statusResponse(media);
    }

    /** Start a direct-to-R2 upload for a standalone WebVTT file. */
    @Transactional
    public MediaDtos.CaptionUploadGrant requestCaptionUpload(MediaDtos.CaptionUploadRequest request) {
        if (request == null) throw badRequest("Caption upload metadata is required");
        var filename = safeFilename(request.filename());
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".vtt")) {
            throw badRequest("Caption files must use the .vtt extension");
        }
        if (request.contentType() == null || request.contentType().isBlank()) {
            throw badRequest("Caption content type is required");
        }
        var contentType = request.contentType().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!CAPTION_CONTENT_TYPES.contains(contentType)) {
            throw badRequest("Caption uploads must use a WebVTT content type");
        }
        if (request.contentLength() <= 0) {
            throw badRequest("Caption content length must be positive");
        }
        if (request.contentLength() > MAX_CAPTION_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Caption exceeds the 5 MiB upload limit");
        }

        var uploadId = UUID.randomUUID();
        var objectKey = staticHlsLocations.captionUploadPath(uploadId, filename);
        var upload = captionUploads.save(CaptionUploadEntity.uploading(
            uploadId, objectKey, filename, contentType, request.contentLength()
        ));
        var disposition = "inline; filename*=UTF-8''" + encodeFilename(filename);
        var grant = storage.signUpload(objectKey, contentType, request.contentLength(), disposition);
        return new MediaDtos.CaptionUploadGrant(
            upload.getId(), grant.uploadUrl(), grant.objectKey(), grant.headers(), grant.expiresAt()
        );
    }

    /** Confirm, size-check, and WebVTT-validate a completed direct upload. */
    @Transactional
    public MediaDtos.CaptionUploadCompleteResponse completeCaptionUpload(UUID uploadId) {
        var upload = captionUploads.findById(uploadId)
            .orElseThrow(() -> notFound("Caption upload not found"));
        if (upload.getStatus() == CaptionUploadStatus.COMPLETED || upload.getStatus() == CaptionUploadStatus.ATTACHED) {
            return new MediaDtos.CaptionUploadCompleteResponse(upload.getId(), upload.getObjectKey());
        }
        if (upload.getStatus() != CaptionUploadStatus.UPLOADING) {
            throw conflict("Caption upload is no longer available");
        }
        if (!storage.exists(upload.getObjectKey())) {
            throw conflict("Caption upload is not complete");
        }
        var actualSize = storage.size(upload.getObjectKey());
        if (actualSize >= 0 && actualSize != upload.getContentLength()) {
            throw conflict("Uploaded caption size does not match the declared size");
        }
        staticHlsValidator.validateCaptions(List.of(new MediaCaptionTrack(
            "und", "Caption", upload.getObjectKey(), false
        )));
        upload.complete();
        return new MediaDtos.CaptionUploadCompleteResponse(upload.getId(), upload.getObjectKey());
    }

    @Transactional(readOnly = true)
    public MediaDtos.MediaStatusResponse status(UUID mediaId) {
        return statusResponse(find(mediaId));
    }

    @Transactional(readOnly = true)
    public List<MediaDtos.MediaLibraryItem> list(MediaStatus requestedStatus, Boolean deleted) {
        var attachments = currentAttachments();
        Predicate<MediaAssetEntity> statusFilter = requestedStatus == null
            ? media -> true
            : media -> media.getStatus() == requestedStatus;
        // The global library intentionally excludes retained historical
        // versions. They remain discoverable only through a lesson's version
        // endpoint, where their ownership is explicit.
        Predicate<MediaAssetEntity> deletionFilter = Boolean.TRUE.equals(deleted)
            ? media -> media.getStatus() == MediaStatus.DELETED
            : media -> media.getStatus() != MediaStatus.DELETED
                && media.getDeletedAt() == null
                && media.getRetainedForUnitId() == null;
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
            .filter(statusFilter.and(deletionFilter))
            .map(media -> toLibraryItem(media, attachments.get(media.getId())))
            .toList();
    }

    @Transactional
    public void delete(UUID mediaId) {
        var media = find(mediaId);
        if (media.getStatus() == MediaStatus.DELETED) return;
        if (media.getRetainedForUnitId() != null || repository.countCurrentAttachments(mediaId) > 0) {
            throw conflict("Attached media cannot be deleted from the media library");
        }
        media.softDelete(properties.retention());
    }

    @Transactional
    public MediaDtos.MediaLibraryItem restore(UUID mediaId) {
        var media = find(mediaId);
        if (media.getStatus() != MediaStatus.DELETED) {
            throw conflict("Media is not deleted");
        }
        if (repository.countCurrentAttachments(mediaId) > 0) {
            throw conflict("Attached media cannot be restored from the media library");
        }
        media.restoreUnattached();
        return toLibraryItem(media, currentAttachments().get(media.getId()));
    }

    MediaDtos.MediaVersion toVersion(MediaAssetEntity media, boolean current) {
        return new MediaDtos.MediaVersion(
            media.getId(),
            current,
            media.getStatus(),
            media.getPlaybackPath(),
            playbackUrl(media),
            media.getDurationSeconds(),
            media.getEncodingVersion(),
            media.getChecksumSha256(),
            captionResponses(media.getCaptionTracks()),
            media.getCreatedAt(),
            media.getUpdatedAt(),
            media.getDeletedAt(),
            media.getPurgeAfter()
        );
    }

    private MediaDtos.MediaLibraryItem toLibraryItem(
        MediaAssetEntity media,
        MediaDtos.CurrentAttachment current
    ) {
        return new MediaDtos.MediaLibraryItem(
            media.getId(),
            media.getStatus(),
            media.getPlaybackPath(),
            playbackUrl(media),
            media.getDurationSeconds(),
            media.getEncodingVersion(),
            media.getChecksumSha256(),
            captionResponses(media.getCaptionTracks()),
            media.getCreatedAt(),
            media.getUpdatedAt(),
            media.getDeletedAt(),
            media.getPurgeAfter(),
            media.getRetainedForUnitId(),
            current
        );
    }

    private MediaDtos.MediaStatusResponse statusResponse(MediaAssetEntity media) {
        var current = currentAttachments().get(media.getId());
        return new MediaDtos.MediaStatusResponse(
            media.getId(),
            media.getStatus().name(),
            playbackUrl(media),
            media.getDurationSeconds(),
            captionResponses(media.getCaptionTracks()),
            media.getFailureMessage(),
            media.getPlaybackPath(),
            media.getEncodingVersion(),
            media.getChecksumSha256(),
            media.getCreatedAt(),
            media.getUpdatedAt(),
            media.getDeletedAt(),
            media.getPurgeAfter(),
            media.getRetainedForUnitId(),
            current
        );
    }

    private MediaAssetEntity find(UUID mediaId) {
        return repository.findById(mediaId)
            .orElseThrow(() -> notFound("Media not found"));
    }

    private URI playbackUrl(MediaAssetEntity media) {
        return media.getPlaybackPath() == null ? null : staticHlsLocations.resolve(media.getPlaybackPath());
    }

    private Map<UUID, MediaDtos.CurrentAttachment> currentAttachments() {
        var rows = repository.findCurrentAttachmentRows();
        if (rows == null) return Map.of();
        return rows.stream().collect(Collectors.toMap(
            row -> (UUID) row[0],
            row -> new MediaDtos.CurrentAttachment(
                (UUID) row[2],
                firstNonBlank((String) row[3], (String) row[4]),
                (UUID) row[1],
                firstNonBlank((String) row[5], (String) row[6])
            ),
            (left, right) -> left
        ));
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private List<MediaCaptionTrack> normalizeCaptions(List<MediaDtos.CaptionTrackRequest> requests) {
        if (requests == null || requests.isEmpty()) return List.of();
        if (requests.size() > 20) throw badRequest("At most 20 caption tracks are allowed");
        var languages = new HashSet<String>();
        var paths = new HashSet<String>();
        var defaults = 0;
        var tracks = new java.util.ArrayList<MediaCaptionTrack>();
        for (var request : requests) {
            if (request == null) throw badRequest("Caption tracks cannot be null");
            var language = required(request.language(), "Caption language").toLowerCase(Locale.ROOT);
            if (!LANGUAGE.matcher(language).matches()) throw badRequest("Caption language must be a valid BCP-47 tag");
            var label = required(request.label(), "Caption label");
            if (label.length() > 120) throw badRequest("Caption labels must be at most 120 characters");
            var rawPath = required(request.path(), "Caption path");
            if (rawPath.length() > MAX_CAPTION_PATH_LENGTH) throw badRequest("Caption paths are too long");
            var path = staticHlsLocations.captionPath(rawPath);
            if (!languages.add(language)) throw badRequest("Caption languages must be unique");
            if (!paths.add(path)) throw badRequest("Caption paths must be unique");
            if (request.defaultTrack() && ++defaults > 1) {
                throw badRequest("Only one caption track can be the default");
            }
            tracks.add(new MediaCaptionTrack(language, label, path, request.defaultTrack()));
        }
        return List.copyOf(tracks);
    }

    private void attachCaptionUploads(UUID mediaId, List<MediaCaptionTrack> tracks) {
        var paths = tracks.stream().map(MediaCaptionTrack::getPath).toList();
        if (paths.isEmpty() || captionUploads == null) return;
        var uploads = captionUploads.findByObjectKeyIn(paths);
        if (uploads == null) return;
        for (var upload : uploads) {
            if (upload.getMediaId() != null && !mediaId.equals(upload.getMediaId())) {
                throw conflict("A caption file is already attached to another media asset");
            }
            if (upload.getStatus() != CaptionUploadStatus.COMPLETED &&
                !(upload.getStatus() == CaptionUploadStatus.ATTACHED && mediaId.equals(upload.getMediaId()))) {
                throw conflict("Caption uploads must be completed before they can be attached");
            }
            upload.attachTo(mediaId);
        }
    }

    private MediaDtos.StaticHlsRegistrationResponse staticHlsResponse(MediaAssetEntity media) {
        return new MediaDtos.StaticHlsRegistrationResponse(
            media.getId(),
            media.getStatus().name(),
            staticHlsLocations.resolve(media.getPlaybackPath()),
            media.getDurationSeconds(),
            media.getEncodingVersion(),
            captionResponses(media.getCaptionTracks())
        );
    }

    private List<MediaDtos.CaptionTrackResponse> captionResponses(List<MediaCaptionTrack> tracks) {
        return tracks.stream()
            .map(track -> new MediaDtos.CaptionTrackResponse(
                track.getLanguage(),
                track.getLabel(),
                track.getPath(),
                staticHlsLocations.resolve(track.getPath()),
                track.isDefaultTrack()
            ))
            .toList();
    }

    private boolean sameCaptions(List<MediaCaptionTrack> left, List<MediaCaptionTrack> right) {
        if (left.size() != right.size()) return false;
        return left.stream().allMatch(expected -> right.stream().anyMatch(actual ->
            expected.getLanguage().equals(actual.getLanguage()) &&
                expected.getLabel().equals(actual.getLabel()) &&
                expected.getPath().equals(actual.getPath()) &&
                expected.isDefaultTrack() == actual.isDefaultTrack()
        ));
    }

    private String safeFilename(String input) {
        if (input == null) throw badRequest("Caption filename is required");
        var basename = input.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1).trim();
        var sanitized = basename.replaceAll("[^A-Za-z0-9._-]", "-").replaceAll("-+", "-");
        if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")) {
            throw badRequest("Caption filename is invalid");
        }
        if (!sanitized.toLowerCase(Locale.ROOT).endsWith(".vtt")) {
            throw badRequest("Caption files must use the .vtt extension");
        }
        if (sanitized.length() <= 180) return sanitized;
        var extension = ".vtt";
        return sanitized.substring(0, 180 - extension.length()) + extension;
    }

    private String normalizeChecksum(String value) {
        if (value == null || value.isBlank()) return null;
        var checksum = value.trim().toLowerCase(Locale.ROOT);
        if (!SHA256.matcher(checksum).matches()) throw badRequest("Checksum must be a SHA-256 hexadecimal digest");
        return checksum;
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw badRequest(field + " is required");
        return value.trim();
    }

    private String encodeFilename(String value) {
        var builder = new StringBuilder();
        for (byte octet : value.getBytes(StandardCharsets.UTF_8)) {
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
