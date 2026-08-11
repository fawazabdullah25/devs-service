package org.kstacks.devs.media.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.kstacks.devs.config.MediaProperties;
import org.kstacks.devs.media.api.MediaDtos;
import org.kstacks.devs.media.domain.MediaAssetEntity;
import org.kstacks.devs.media.domain.MediaAssetRepository;
import org.kstacks.devs.media.domain.MediaStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@Service
public class MediaService {
    private final MediaAssetRepository repository;
    private final ObjectStorage storage;
    private final VideoProvider videoProvider;
    private final MediaProperties properties;
    private final ObjectMapper mapper;

    public MediaService(MediaAssetRepository repository, ObjectStorage storage, VideoProvider videoProvider, MediaProperties properties, ObjectMapper mapper) {
        this.repository = repository;
        this.storage = storage;
        this.videoProvider = videoProvider;
        this.properties = properties;
        this.mapper = mapper;
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

    @Transactional(readOnly = true)
    public MediaDtos.MediaStatusResponse status(UUID mediaId) {
        var media = repository.findById(mediaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
        return new MediaDtos.MediaStatusResponse(
            media.getId(),
            media.getStatus().name(),
            media.getProviderAssetId(),
            media.getPlaybackId(),
            media.getDurationSeconds(),
            media.getFailureMessage()
        );
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
}
