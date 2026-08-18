package org.kstacks.devs.media.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kstacks.devs.config.MediaProperties;
import org.kstacks.devs.config.StaticHlsProperties;
import org.kstacks.devs.media.api.MediaDtos;
import org.kstacks.devs.media.domain.MediaAssetEntity;
import org.kstacks.devs.media.domain.MediaAssetRepository;
import org.kstacks.devs.media.domain.MediaStatus;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceTests {
    @Mock
    private MediaAssetRepository repository;
    @Mock
    private ObjectStorage storage;
    @Mock
    private VideoProvider videoProvider;
    @Mock
    private StaticHlsPackageValidator staticHlsValidator;

    private MediaService service;
    private StaticHlsLocationResolver staticHlsLocations;

    @BeforeEach
    void setUp() {
        staticHlsLocations = new StaticHlsLocationResolver(new StaticHlsProperties(
            true, URI.create("https://video.example.test/"), "pilots", Duration.ofSeconds(2)
        ));
        service = new MediaService(
            repository,
            storage,
            videoProvider,
            new MediaProperties(1_000, 20),
            new ObjectMapper(),
            staticHlsValidator,
            staticHlsLocations
        );
        lenient().when(repository.save(any(MediaAssetEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsAPresignedUploadWithoutSendingVideoThroughTheService() {
        var expiresAt = Instant.now().plusSeconds(600);
        when(storage.signUpload(any(), any(), any(Long.class))).thenAnswer(invocation -> {
            var objectKey = invocation.getArgument(0, String.class);
            return new ObjectStorage.UploadGrant(
                URI.create("https://r2.example/upload"),
                objectKey,
                Map.of("Content-Type", "video/mp4"),
                expiresAt
            );
        });

        var response = service.createUpload(new MediaDtos.UploadRequest("../My lesson (final).mp4", "video/mp4", 900));

        assertThat(response.uploadUrl()).isEqualTo(URI.create("https://r2.example/upload"));
        assertThat(response.objectKey()).startsWith("source/").endsWith("/My-lesson-final-.mp4");
        assertThat(response.headers()).containsEntry("Content-Type", "video/mp4");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);

        var saved = ArgumentCaptor.forClass(MediaAssetEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(response.mediaId());
        assertThat(saved.getValue().getStatus()).isEqualTo(MediaStatus.UPLOADING);
        assertThat(saved.getValue().getSourceObjectKey()).isEqualTo(response.objectKey());
        assertThat(saved.getValue().getSourceFilename()).isEqualTo("My-lesson-final-.mp4");
    }

    @Test
    void rejectsNonVideoAndOversizedUploadsBeforeCreatingDatabaseRows() {
        assertStatus(
            HttpStatus.BAD_REQUEST,
            () -> service.createUpload(new MediaDtos.UploadRequest("notes.pdf", "application/pdf", 100))
        );
        assertStatus(
            HttpStatus.PAYLOAD_TOO_LARGE,
            () -> service.createUpload(new MediaDtos.UploadRequest("huge.mp4", "video/mp4", 1_001))
        );

        verify(repository, never()).save(any());
        verify(storage, never()).signUpload(any(), any(), any(Long.class));
    }

    @Test
    void submitsAnUploadedR2ObjectToMuxAndMarksItProcessing() {
        var media = MediaAssetEntity.uploading("source/video.mp4", "video.mp4", "video/mp4");
        var downloadUrl = URI.create("https://r2.example/download");
        when(repository.findById(media.getId())).thenReturn(Optional.of(media));
        when(storage.exists(media.getSourceObjectKey())).thenReturn(true);
        when(storage.signDownload(media.getSourceObjectKey())).thenReturn(downloadUrl);
        when(videoProvider.createAsset(downloadUrl)).thenReturn(new VideoProvider.CreatedAsset("mux-asset-1"));

        var response = service.ingest(media.getId());

        assertThat(response.status()).isEqualTo("PROCESSING");
        assertThat(response.providerAssetId()).isEqualTo("mux-asset-1");
        assertThat(media.getStatus()).isEqualTo(MediaStatus.PROCESSING);
        assertThat(media.getProviderAssetId()).isEqualTo("mux-asset-1");
    }

    @Test
    void refusesIngestUntilR2ConfirmsTheUploadExists() {
        var media = MediaAssetEntity.uploading("source/missing.mp4", "missing.mp4", "video/mp4");
        when(repository.findById(media.getId())).thenReturn(Optional.of(media));
        when(storage.exists(media.getSourceObjectKey())).thenReturn(false);

        assertStatus(HttpStatus.CONFLICT, () -> service.ingest(media.getId()));

        assertThat(media.getStatus()).isEqualTo(MediaStatus.UPLOADING);
        verify(videoProvider, never()).createAsset(any());
    }

    @Test
    void refusesToSubmitTheSameProcessingAssetTwice() {
        var media = MediaAssetEntity.uploading("source/video.mp4", "video.mp4", "video/mp4");
        media.markProcessing("mux-asset-1");
        when(repository.findById(media.getId())).thenReturn(Optional.of(media));

        assertStatus(HttpStatus.CONFLICT, () -> service.ingest(media.getId()));

        verify(storage, never()).exists(any());
        verify(videoProvider, never()).createAsset(any());
    }

    @Test
    void importsAnExistingR2ObjectAndNormalizesItsChecksum() {
        var checksum = "A".repeat(64);
        when(repository.findBySourceObjectKey("telegram/video.mp4")).thenReturn(Optional.empty());
        when(storage.exists("telegram/video.mp4")).thenReturn(true);
        when(storage.signDownload("telegram/video.mp4")).thenReturn(URI.create("https://r2.example/import"));
        when(videoProvider.createAsset(any())).thenReturn(new VideoProvider.CreatedAsset("mux-import-1"));

        var response = service.importAndIngest(new MediaDtos.ImportRequest(
            "telegram/video.mp4", "video.mp4", "VIDEO/MP4", checksum
        ));

        assertThat(response.status()).isEqualTo("PROCESSING");
        assertThat(response.providerAssetId()).isEqualTo("mux-import-1");
        var saved = ArgumentCaptor.forClass(MediaAssetEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSourceObjectKey()).isEqualTo("telegram/video.mp4");
        assertThat(saved.getValue().getStatus()).isEqualTo(MediaStatus.PROCESSING);
    }

    @Test
    void treatsARepeatedImportAsIdempotentOnceProcessingStarted() {
        var existing = MediaAssetEntity.imported("telegram/video.mp4", "video.mp4", "video/mp4", "a".repeat(64));
        existing.markProcessing("mux-existing");
        when(repository.findBySourceObjectKey("telegram/video.mp4")).thenReturn(Optional.of(existing));

        var response = service.importAndIngest(new MediaDtos.ImportRequest(
            "telegram/video.mp4", "video.mp4", "video/mp4", "a".repeat(64)
        ));

        assertThat(response.mediaId()).isEqualTo(existing.getId());
        assertThat(response.status()).isEqualTo("PROCESSING");
        assertThat(response.providerAssetId()).isEqualTo("mux-existing");
        verify(storage, never()).exists(any());
        verify(videoProvider, never()).createAsset(any());
    }

    @Test
    void rejectsUnsafeMissingAndNonVideoImports() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.importAndIngest(
            new MediaDtos.ImportRequest("../secret.mp4", "secret.mp4", "video/mp4", null)
        ));

        when(repository.findBySourceObjectKey("telegram/missing.mp4")).thenReturn(Optional.empty());
        when(storage.exists("telegram/missing.mp4")).thenReturn(false);
        assertStatus(HttpStatus.NOT_FOUND, () -> service.importAndIngest(
            new MediaDtos.ImportRequest("telegram/missing.mp4", "missing.mp4", "video/mp4", null)
        ));

        when(repository.findBySourceObjectKey("telegram/readme.txt")).thenReturn(Optional.empty());
        when(storage.exists("telegram/readme.txt")).thenReturn(true);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.importAndIngest(
            new MediaDtos.ImportRequest("telegram/readme.txt", "readme.txt", "text/plain", null)
        ));

        verify(videoProvider, never()).createAsset(any());
    }

    @Test
    void appliesAReadyMuxWebhookToTheMatchingAsset() {
        var media = MediaAssetEntity.uploading("source/video.mp4", "video.mp4", "video/mp4");
        media.markProcessing("mux-asset-1");
        when(repository.findByProviderAssetId("mux-asset-1")).thenReturn(Optional.of(media));

        service.receiveMuxEvent("""
            {
              "type": "video.asset.ready",
              "data": {
                "id": "mux-asset-1",
                "duration": 12.6,
                "playback_ids": [{"id": "playback-1", "policy": "public"}]
              }
            }
            """);

        assertThat(media.getStatus()).isEqualTo(MediaStatus.READY);
        assertThat(media.getPlaybackId()).isEqualTo("playback-1");
        assertThat(media.getDurationSeconds()).isEqualTo(13);
        assertThat(media.getFailureMessage()).isNull();
    }

    @Test
    void recordsMuxErrorsAndMissingPlaybackIdsAsFailures() {
        var errored = processing("mux-error");
        when(repository.findByProviderAssetId("mux-error")).thenReturn(Optional.of(errored));
        service.receiveMuxEvent("""
            {"type":"video.asset.errored","data":{"id":"mux-error","errors":{"messages":["Unsupported codec"]}}}
            """);
        assertThat(errored.getStatus()).isEqualTo(MediaStatus.FAILED);
        assertThat(errored.getFailureMessage()).isEqualTo("Unsupported codec");

        var noPlayback = processing("mux-no-playback");
        when(repository.findByProviderAssetId("mux-no-playback")).thenReturn(Optional.of(noPlayback));
        service.receiveMuxEvent("""
            {"type":"video.asset.ready","data":{"id":"mux-no-playback","duration":5,"playback_ids":[]}}
            """);
        assertThat(noPlayback.getStatus()).isEqualTo(MediaStatus.FAILED);
        assertThat(noPlayback.getFailureMessage()).contains("without a playback ID");
    }

    @Test
    void ignoresUnknownMuxEventsAndRejectsMalformedJson() {
        service.receiveMuxEvent("""
            {"type":"video.asset.ready","data":{"id":"unknown","playback_ids":[{"id":"ignored"}]}}
            """);
        verify(repository).findByProviderAssetId("unknown");

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.receiveMuxEvent("not-json"));
    }

    @Test
    void validatesAndRegistersAnImmutableStaticHlsPackageAsReady() {
        when(repository.findByPlaybackPath("pilots/course/v1/master.m3u8")).thenReturn(Optional.empty());

        var response = service.registerStaticHls(new MediaDtos.StaticHlsRegistrationRequest(
            "pilots/course/v1/master.m3u8",
            3_600,
            "A".repeat(64),
            "v1",
            java.util.List.of(
                new MediaDtos.CaptionTrackRequest("EN", "English", "pilots/course/v1/captions/en.vtt", true),
                new MediaDtos.CaptionTrackRequest("ar", "العربية", "pilots/course/v1/captions/ar.vtt", false)
            )
        ));

        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.playbackUrl()).isEqualTo(URI.create("https://video.example.test/pilots/course/v1/master.m3u8"));
        assertThat(response.captions()).extracting(MediaDtos.CaptionTrackResponse::language).containsExactly("en", "ar");

        var saved = ArgumentCaptor.forClass(MediaAssetEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getProvider().name()).isEqualTo("STATIC_HLS");
        assertThat(saved.getValue().getStatus()).isEqualTo(MediaStatus.READY);
        assertThat(saved.getValue().getPlaybackPath()).isEqualTo("pilots/course/v1/master.m3u8");
        assertThat(saved.getValue().getChecksumSha256()).isEqualTo("a".repeat(64));
        verify(staticHlsValidator).validate(
            org.mockito.ArgumentMatchers.eq("pilots/course/v1/master.m3u8"),
            org.mockito.ArgumentMatchers.anyList()
        );
    }

    @Test
    void rejectsUnsafeStaticHlsPathsAndAmbiguousCaptionDefaults() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.registerStaticHls(
            new MediaDtos.StaticHlsRegistrationRequest(
                "https://attacker.example/master.m3u8", 60, null, "v1", java.util.List.of()
            )
        ));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.registerStaticHls(
            new MediaDtos.StaticHlsRegistrationRequest(
                "pilots/course/v1/master.m3u8",
                60,
                null,
                "v1",
                java.util.List.of(
                    new MediaDtos.CaptionTrackRequest("en", "English", "pilots/course/v1/en.vtt", true),
                    new MediaDtos.CaptionTrackRequest("ar", "Arabic", "pilots/course/v1/ar.vtt", true)
                )
            )
        ));

        verify(staticHlsValidator, never()).validate(any(), any());
        verify(repository, never()).save(any());
    }

    private MediaAssetEntity processing(String assetId) {
        var media = MediaAssetEntity.uploading("source/" + assetId + ".mp4", assetId + ".mp4", "video/mp4");
        media.markProcessing(assetId);
        return media;
    }

    private void assertStatus(HttpStatus expected, Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(expected)
            );
    }
}
