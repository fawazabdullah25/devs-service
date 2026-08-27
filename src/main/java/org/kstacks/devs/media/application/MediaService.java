package org.kstacks.devs.media.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.kstacks.devs.config.MediaProperties;
import org.kstacks.devs.media.api.MediaDtos;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class MediaService {
    private final MediaAssetRepository repository;
    private final ObjectStorage storage;
    private final VideoProvider videoProvider;
    private final MediaProperties properties;
    private final ObjectMapper mapper;
    private final StaticHlsPackageValidator staticHlsValidator;
    private final StaticHlsLocationResolver staticHlsLocations;

    public MediaService(
        MediaAssetRepository repository,
        ObjectStorage storage,
        VideoProvider videoProvider,
        MediaProperties properties,
        ObjectMapper mapper,
        StaticHlsPackageValidator staticHlsValidator,
        StaticHlsLocationResolver staticHlsLocations
    ) {
        this.repository = repository;
        this.storage = storage;
        this.videoProvider = videoProvider;
        this.properties = properties;
        this.mapper = mapper;
        this.staticHlsValidator = staticHlsValidator;
        this.staticHlsLocations = staticHlsLocations;
    }

    @Transactional
    public MediaDtos.UploadGrant createUpload(MediaDtos.UploadRequest request) {
        if (!request.contentType().toLowerCase(Locale.ROOT).startsWith("video/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only video uploads are accepted");
        }
        if (request.contentLength() > properties.maxUploadBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Video exceeds the configured upload limit");
        }
        var filename = safeFilename(request.filename());
        var objectKey = "source/" + UUID.randomUUID() + "/" + filename;
        var media = repository.save(MediaAssetEntity.uploading(objectKey, filename, request.contentType()));
        var grant = storage.signUpload(objectKey, request.contentType(), request.contentLength());
        return new MediaDtos.UploadGrant(media.getId(), grant.uploadUrl(), grant.objectKey(), grant.headers(), grant.expiresAt());
    }

    @Transactional
    public MediaDtos.IngestResponse ingest(UUID mediaId) {
        var media = repository.findById(mediaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
        if (media.getStatus() != MediaStatus.UPLOADING && media.getStatus() != MediaStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Media has already been submitted for processing");
        }
        if (!storage.exists(media.getSourceObjectKey())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The source upload is not complete");
        }
        var asset = videoProvider.createAsset(storage.signDownload(media.getSourceObjectKey()));
        media.markProcessing(asset.assetId());
        return new MediaDtos.IngestResponse(media.getId(), media.getStatus().name(), asset.assetId());
    }

    @Transactional
    public MediaDtos.IngestResponse importAndIngest(MediaDtos.ImportRequest request) {
        var objectKey = request.objectKey().trim();
        if (objectKey.startsWith("/") || objectKey.contains("..") || objectKey.contains("\\")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Object key is invalid");
        }
        var existing = repository.findBySourceObjectKey(objectKey).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == MediaStatus.UPLOADING || existing.getStatus() == MediaStatus.FAILED) {
                return ingest(existing.getId());
            }
            return new MediaDtos.IngestResponse(existing.getId(), existing.getStatus().name(), existing.getProviderAssetId());
        }
        if (!storage.exists(objectKey)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Source object was not found in R2");
        }
        var contentType = request.contentType().toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("video/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only video imports are accepted");
        }
        var media = repository.save(MediaAssetEntity.imported(
            objectKey,
            safeFilename(request.filename()),
            contentType,
            request.checksumSha256() == null ? null : request.checksumSha256().toLowerCase(Locale.ROOT)
        ));
        var asset = videoProvider.createAsset(storage.signDownload(objectKey));
        media.markProcessing(asset.assetId());
        return new MediaDtos.IngestResponse(media.getId(), media.getStatus().name(), asset.assetId());
    }

    @Transactional
    public MediaDtos.StaticHlsRegistrationResponse registerStaticHls(MediaDtos.StaticHlsRegistrationRequest request) {
        var manifestPath = staticHlsLocations.manifestPath(request.manifestPath());
        var captions = normalizeCaptions(request.captions());
        var checksum = request.checksumSha256() == null ? null : request.checksumSha256().toLowerCase(Locale.ROOT);
        var encodingVersion = request.encodingVersion().trim();

        var existing = repository.findByPlaybackPath(manifestPath).orElse(null);
        if (existing != null) {
            if (existing.getDurationSeconds() != request.durationSeconds() ||
                !Objects.equals(existing.getChecksumSha256(), checksum) ||
                !Objects.equals(existing.getEncodingVersion(), encodingVersion) ||
                !sameCaptions(existing.getCaptionTracks(), captions)) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This immutable HLS path is already registered with different metadata"
                );
            }
            return staticHlsResponse(existing);
        }

        staticHlsValidator.validate(manifestPath, captions);
        var media = repository.save(MediaAssetEntity.staticHls(
            manifestPath,
            request.durationSeconds(),
            checksum,
            encodingVersion,
            captions
        ));
        return staticHlsResponse(media);
    }

    @Transactional(readOnly = true)
    public MediaDtos.MediaStatusResponse status(UUID mediaId) {
        var media = repository.findById(mediaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
        var current = currentAttachments().get(media.getId());
        return new MediaDtos.MediaStatusResponse(
            media.getId(),
            media.getStatus().name(),
            media.getProvider().name(),
            media.getProviderAssetId(),
            media.getPlaybackId(),
            media.getPlaybackPath() == null ? null : staticHlsLocations.resolve(media.getPlaybackPath()),
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

    @Transactional(readOnly = true)
    public List<MediaDtos.MediaLibraryItem> list(MediaStatus requestedStatus, Boolean deleted) {
        var attachments = currentAttachments();
        Predicate<MediaAssetEntity> statusFilter = requestedStatus == null
            ? media -> true
            : media -> media.getStatus() == requestedStatus;
        // The global library intentionally excludes retained historical
        // versions. They remain discoverable only through a lesson's version
        // endpoint, where their ownership is explicit. The trash view is
        // likewise limited to directly deleted, unattached rows.
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
        var media = repository.findById(mediaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
        if (media.getStatus() == MediaStatus.DELETED) return;
        if (media.getRetainedForUnitId() != null || repository.countCurrentAttachments(mediaId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attached media cannot be deleted from the media library");
        }
        media.softDelete(properties.retention());
    }

    @Transactional
    public MediaDtos.MediaLibraryItem restore(UUID mediaId) {
        var media = repository.findById(mediaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
        if (media.getStatus() != MediaStatus.DELETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Media is not deleted");
        }
        // A deleted asset should never be attachable, but this guard keeps a
        // legacy row or a concurrent/manual DB change from being restored
        // while it is already referenced by a lesson.
        if (repository.countCurrentAttachments(mediaId) > 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Attached media cannot be restored from the media library"
            );
        }
        media.restoreUnattached();
        return toLibraryItem(media, currentAttachments().get(media.getId()));
    }

    MediaDtos.MediaVersion toVersion(MediaAssetEntity media, boolean current) {
        return new MediaDtos.MediaVersion(
            media.getId(),
            current,
            media.getProvider(),
            media.getStatus(),
            media.getProviderAssetId(),
            media.getPlaybackId(),
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
            media.getProvider(),
            media.getStatus(),
            media.getProviderAssetId(),
            media.getPlaybackId(),
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

    private URI playbackUrl(MediaAssetEntity media) {
        return media.getPlaybackPath() == null ? null : staticHlsLocations.resolve(media.getPlaybackPath());
    }

    private Map<UUID, MediaDtos.CurrentAttachment> currentAttachments() {
        return repository.findCurrentAttachmentRows().stream().collect(Collectors.toMap(
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

    @Transactional
    public void receiveMuxEvent(String body) {
        try {
            var event = mapper.readTree(body);
            var type = event.path("type").asText();
            var data = event.path("data");
            var assetId = data.path("id").asText();
            if (assetId.isBlank()) return;
            var media = repository.findByProviderAssetId(assetId).orElse(null);
            if (media == null) return;

            if ("video.asset.ready".equals(type)) {
                var playbackId = firstPlaybackId(data);
                if (playbackId == null) {
                    media.markFailed("Mux marked the asset ready without a playback ID");
                    return;
                }
                media.markReady(playbackId, Math.round(data.path("duration").asDouble(0)));
            } else if ("video.asset.errored".equals(type)) {
                media.markFailed(errorMessage(data));
            }
        } catch (JacksonException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Mux webhook payload", exception);
        }
    }

    private String firstPlaybackId(JsonNode data) {
        var playbackIds = data.path("playback_ids");
        if (!playbackIds.isArray() || playbackIds.size() == 0) return null;
        var id = playbackIds.get(0).path("id").asText();
        return id.isBlank() ? null : id;
    }

    private String errorMessage(JsonNode data) {
        var messages = data.path("errors").path("messages");
        return messages.isArray() && messages.size() > 0 ? messages.get(0).asText() : "Mux could not process this video";
    }

    private String safeFilename(String input) {
        var basename = input.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1);
        var sanitized = basename.replaceAll("[^A-Za-z0-9._-]", "-").replaceAll("-+", "-");
        if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")) sanitized = "video-upload";
        return sanitized.substring(0, Math.min(sanitized.length(), 180));
    }

    private List<MediaCaptionTrack> normalizeCaptions(List<MediaDtos.CaptionTrackRequest> requests) {
        if (requests == null || requests.isEmpty()) return List.of();
        var languages = new HashSet<String>();
        var paths = new HashSet<String>();
        var defaults = 0;
        var tracks = new java.util.ArrayList<MediaCaptionTrack>();
        for (var request : requests) {
            var language = request.language().trim().toLowerCase(Locale.ROOT);
            var path = staticHlsLocations.captionPath(request.path());
            if (!languages.add(language)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Caption languages must be unique");
            }
            if (!paths.add(path)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Caption paths must be unique");
            }
            if (request.defaultTrack() && ++defaults > 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only one caption track can be the default");
            }
            tracks.add(new MediaCaptionTrack(language, request.label().trim(), path, request.defaultTrack()));
        }
        return List.copyOf(tracks);
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
}
