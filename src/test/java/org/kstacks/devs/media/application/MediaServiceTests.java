package org.kstacks.devs.media.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kstacks.devs.config.MediaProperties;
import org.kstacks.devs.config.StaticHlsProperties;
import org.kstacks.devs.media.api.MediaDtos;
import org.kstacks.devs.media.domain.CaptionUploadEntity;
import org.kstacks.devs.media.domain.CaptionUploadRepository;
import org.kstacks.devs.media.domain.MediaAssetEntity;
import org.kstacks.devs.media.domain.MediaAssetRepository;
import org.kstacks.devs.media.domain.MediaCaptionTrack;
import org.kstacks.devs.media.domain.MediaStatus;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceTests {
    @Mock private MediaAssetRepository repository;
    @Mock private CaptionUploadRepository captionUploads;
    @Mock private ObjectStorage storage;
    @Mock private StaticHlsPackageValidator staticHlsValidator;

    private MediaService service;
    private StaticHlsLocationResolver locations;

    @BeforeEach
    void setUp() {
        locations = new StaticHlsLocationResolver(new StaticHlsProperties(
            true, URI.create("https://video.example.test/"), "pilots", Duration.ofSeconds(2)
        ));
        service = new MediaService(
            repository,
            captionUploads,
            storage,
            new MediaProperties(1_000, 20),
            staticHlsValidator,
            locations
        );
        lenient().when(repository.save(any(MediaAssetEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(captionUploads.save(any(CaptionUploadEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(repository.findCurrentAttachmentRows()).thenReturn(List.of());
        lenient().when(captionUploads.findByObjectKeyIn(anyList())).thenReturn(List.of());
    }

    @Test
    void validatesAndRegistersAStaticHlsPackageWithComputedDuration() {
        when(repository.findByPlaybackPath("pilots/course/v1/master.m3u8")).thenReturn(Optional.empty());
        when(staticHlsValidator.validate(eq("pilots/course/v1/master.m3u8"), anyList()))
            .thenReturn(new StaticHlsPackageValidator.ValidationResult(3_600));

        var response = service.registerStaticHls(new MediaDtos.StaticHlsRegistrationRequest(
            "pilots/course/v1/master.m3u8",
            "A".repeat(64),
            "v1",
            List.of(
                new MediaDtos.CaptionTrackRequest("EN", "English", "pilots/course/v1/captions/en.vtt", true),
                new MediaDtos.CaptionTrackRequest("ar", "العربية", "pilots/course/v1/captions/ar.vtt", false)
            )
        ));

        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.durationSeconds()).isEqualTo(3_600);
        assertThat(response.playbackUrl()).isEqualTo(
            URI.create("https://video.example.test/pilots/course/v1/master.m3u8")
        );
        assertThat(response.captions()).extracting(MediaDtos.CaptionTrackResponse::language)
            .containsExactly("en", "ar");

        var saved = ArgumentCaptor.forClass(MediaAssetEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(MediaStatus.READY);
        assertThat(saved.getValue().getPlaybackPath()).isEqualTo("pilots/course/v1/master.m3u8");
        assertThat(saved.getValue().getDurationSeconds()).isEqualTo(3_600);
        assertThat(saved.getValue().getChecksumSha256()).isEqualTo("a".repeat(64));
        verify(staticHlsValidator).validate(eq("pilots/course/v1/master.m3u8"), anyList());
    }

    @Test
    void treatsTheSameStaticHlsRegistrationAsIdempotent() {
        var media = MediaAssetEntity.staticHls(
            "pilots/course/v1/master.m3u8", 3_600, "a".repeat(64), "v1", List.of()
        );
        when(repository.findByPlaybackPath("pilots/course/v1/master.m3u8")).thenReturn(Optional.of(media));
        when(staticHlsValidator.validate(any(), anyList()))
            .thenReturn(new StaticHlsPackageValidator.ValidationResult(3_600));

        var response = service.registerStaticHls(new MediaDtos.StaticHlsRegistrationRequest(
            "pilots/course/v1/master.m3u8", "A".repeat(64), "v1", List.of()
        ));

        assertThat(response.mediaId()).isEqualTo(media.getId());
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsChangingImmutablePackageMetadataButAllowsCaptionChanges() {
        var media = MediaAssetEntity.staticHls(
            "pilots/course/v1/master.m3u8", 3_600, "a".repeat(64), "v1", List.of()
        );
        when(repository.findByPlaybackPath("pilots/course/v1/master.m3u8")).thenReturn(Optional.of(media));
        when(staticHlsValidator.validate(any(), anyList()))
            .thenReturn(new StaticHlsPackageValidator.ValidationResult(3_600));

        assertStatus(HttpStatus.CONFLICT, () -> service.registerStaticHls(new MediaDtos.StaticHlsRegistrationRequest(
            "pilots/course/v1/master.m3u8", "b".repeat(64), "v1", List.of()
        )));

        var captions = List.of(new MediaDtos.CaptionTrackRequest(
            "en", "English", "pilots/course/v1/captions/en.vtt", true
        ));
        service.registerStaticHls(new MediaDtos.StaticHlsRegistrationRequest(
            "pilots/course/v1/master.m3u8", "a".repeat(64), "v1", captions
        ));
        assertThat(media.getCaptionTracks()).singleElement().satisfies(track -> {
            assertThat(track.getLanguage()).isEqualTo("en");
            assertThat(track.isDefaultTrack()).isTrue();
        });
        verify(staticHlsValidator).validateCaptions(anyList());
    }

    @Test
    void rejectsUnsafeStaticHlsPathsAndAmbiguousCaptionDefaults() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.registerStaticHls(
            new MediaDtos.StaticHlsRegistrationRequest(
                "https://attacker.example/master.m3u8", null, "v1", List.of()
            )
        ));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.registerStaticHls(
            new MediaDtos.StaticHlsRegistrationRequest(
                "pilots/course/v1/master.m3u8", null, "v1", List.of(
                    new MediaDtos.CaptionTrackRequest("en", "English", "pilots/course/v1/en.vtt", true),
                    new MediaDtos.CaptionTrackRequest("ar", "Arabic", "pilots/course/v1/ar.vtt", true)
                )
            )
        ));
        verify(staticHlsValidator, never()).validate(any(), anyList());
        verify(repository, never()).save(any());
    }

    @Test
    void updatesCaptionsWithoutChangingTheVideoAsset() {
        var media = MediaAssetEntity.staticHls(
            "pilots/course/v1/master.m3u8", 60, null, "v1", List.of()
        );
        when(repository.findById(media.getId())).thenReturn(Optional.of(media));
        var request = new MediaDtos.CaptionUpdateRequest(List.of(
            new MediaDtos.CaptionTrackRequest("en", "English", "pilots/course/v1/captions/en.vtt", true)
        ));

        var response = service.updateCaptions(media.getId(), request);

        assertThat(response.mediaId()).isEqualTo(media.getId());
        assertThat(response.captions()).singleElement().satisfies(caption -> {
            assertThat(caption.path()).isEqualTo("pilots/course/v1/captions/en.vtt");
            assertThat(caption.url()).isEqualTo(
                URI.create("https://video.example.test/pilots/course/v1/captions/en.vtt")
            );
        });
        assertThat(media.getPlaybackPath()).isEqualTo("pilots/course/v1/master.m3u8");
        assertThat(media.getDurationSeconds()).isEqualTo(60);
        verify(staticHlsValidator).validateCaptions(anyList());
    }

    @Test
    void rejectsCaptionUpdatesForRetainedMedia() {
        var media = MediaAssetEntity.staticHls(
            "pilots/course/v1/master.m3u8", 60, null, "v1", List.of()
        );
        media.retainForUnit(java.util.UUID.randomUUID(), Duration.ofDays(30));
        when(repository.findById(media.getId())).thenReturn(Optional.of(media));

        assertStatus(HttpStatus.CONFLICT, () -> service.updateCaptions(
            media.getId(), new MediaDtos.CaptionUpdateRequest(List.of())
        ));
        verify(staticHlsValidator, never()).validateCaptions(anyList());
    }

    @Test
    void startsAndCompletesAManagedVttUpload() {
        when(storage.signUpload(any(), any(), any(Long.class), any()))
            .thenAnswer(invocation -> new ObjectStorage.UploadGrant(
                URI.create("https://r2.example.test/upload"), invocation.getArgument(0),
                Map.of("Content-Type", invocation.getArgument(1)), Instant.now().plusSeconds(600)
            ));
        var grant = service.requestCaptionUpload(new MediaDtos.CaptionUploadRequest(
            "../captions/en.vtt", "text/vtt", 128
        ));
        assertThat(grant.objectKey()).startsWith("pilots/captions/").endsWith("/en.vtt");
        verify(storage).signUpload(eq(grant.objectKey()), eq("text/vtt"), eq(128L), any());

        var upload = CaptionUploadEntity.uploading(
            grant.uploadId(), grant.objectKey(), "captions/en.vtt", "text/vtt", 128
        );
        when(captionUploads.findById(grant.uploadId())).thenReturn(Optional.of(upload));
        when(storage.exists(grant.objectKey())).thenReturn(true);
        when(storage.size(grant.objectKey())).thenReturn(128L);

        var result = service.completeCaptionUpload(grant.uploadId());
        assertThat(result.path()).isEqualTo(grant.objectKey());
        verify(staticHlsValidator).validateCaptions(anyList());
    }

    @Test
    void rejectsUnsafeCaptionUploadsBeforeCreatingAnR2Grant() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.requestCaptionUpload(
            new MediaDtos.CaptionUploadRequest("captions/en.srt", "text/vtt", 100)
        ));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.requestCaptionUpload(
            new MediaDtos.CaptionUploadRequest("captions/en.vtt", "application/pdf", 100)
        ));
        assertStatus(HttpStatus.PAYLOAD_TOO_LARGE, () -> service.requestCaptionUpload(
            new MediaDtos.CaptionUploadRequest("captions/en.vtt", "text/vtt", 5L * 1024 * 1024 + 1)
        ));
        verify(captionUploads, never()).save(any());
        verify(storage, never()).signUpload(any(), any(), any(Long.class), any());
    }

    private void assertStatus(HttpStatus expected, Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(expected)
            );
    }
}
