package org.kstacks.devs.media.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kstacks.devs.config.MediaProperties;
import org.kstacks.devs.config.StaticHlsProperties;
import org.kstacks.devs.media.domain.MediaAssetEntity;
import org.kstacks.devs.media.domain.MediaAssetRepository;
import org.kstacks.devs.media.domain.CaptionUploadStatus;
import org.kstacks.devs.media.domain.CaptionUploadRepository;
import org.kstacks.devs.media.domain.CaptionUploadEntity;
import org.kstacks.devs.media.domain.MediaStatus;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaPurgeJobTests {
    @Mock private MediaAssetRepository repository;
    @Mock private CaptionUploadRepository captionUploads;
    @Mock private ObjectStorage storage;

    private MediaPurgeJob job;

    @BeforeEach
    void setUp() {
        var locations = new StaticHlsLocationResolver(new StaticHlsProperties(
            true, URI.create("https://video.example.test/"), "pilots", Duration.ofSeconds(2)
        ));
        job = new MediaPurgeJob(
            repository, captionUploads, storage, locations, new MediaProperties(1_000, 20)
        );
        lenient().when(captionUploads.findByStatusAndCreatedAtLessThanEqual(eq(CaptionUploadStatus.COMPLETED), any()))
            .thenReturn(List.of());
        lenient().when(captionUploads.findByMediaId(any())).thenReturn(List.of());
    }

    @Test
    void removesTheWholeStaticHlsPackageBeforeDeletingItsRow() {
        var media = MediaAssetEntity.staticHls("pilots/course/v1/master.m3u8", 60, null, "v1", List.of());
        media.softDelete(Duration.ofDays(7));
        when(repository.findExpiredForPurge(any(), eq(MediaStatus.DELETED))).thenReturn(List.of(media));

        job.purgeExpired();

        verify(storage).deletePrefix("pilots/course/v1/");
        verify(repository).delete(media);
    }

    @Test
    void keepsTheDatabaseRowWhenObjectCleanupFailsSoTheJobCanRetry() {
        var media = MediaAssetEntity.staticHls("pilots/course/v1/master.m3u8", 60, null, "v1", List.of());
        media.softDelete(Duration.ofDays(7));
        when(repository.findExpiredForPurge(any(), eq(MediaStatus.DELETED))).thenReturn(List.of(media));
        doThrow(new IllegalStateException("R2 unavailable")).when(storage).deletePrefix("pilots/course/v1/");

        job.purgeExpired();

        verify(repository, never()).delete(media);
    }

    @Test
    void removesManagedCaptionObjectsBeforeDeletingTheirMediaRow() {
        var media = MediaAssetEntity.staticHls("pilots/course/v1/master.m3u8", 60, null, "v1", List.of());
        media.softDelete(Duration.ofDays(7));
        var upload = CaptionUploadEntity.uploading(
            java.util.UUID.randomUUID(), "pilots/captions/managed/en.vtt", "en.vtt", "text/vtt", 32
        );
        upload.complete();
        upload.attachTo(media.getId());
        when(repository.findExpiredForPurge(any(), eq(MediaStatus.DELETED))).thenReturn(List.of(media));
        when(captionUploads.findByMediaId(media.getId())).thenReturn(List.of(upload));

        job.purgeExpired();

        verify(storage).deletePrefix("pilots/course/v1/");
        verify(storage).delete("pilots/captions/managed/en.vtt");
        verify(captionUploads).delete(upload);
        verify(repository).delete(media);
    }

    @Test
    void removesCompletedCaptionUploadsAfterTheStaleUploadWindow() {
        var upload = CaptionUploadEntity.uploading(
            java.util.UUID.randomUUID(), "pilots/captions/orphan/en.vtt", "en.vtt", "text/vtt", 32
        );
        upload.complete();
        when(captionUploads.findByStatusAndCreatedAtLessThanEqual(eq(CaptionUploadStatus.COMPLETED), any()))
            .thenReturn(List.of(upload));

        job.purgeExpired();

        verify(storage).delete("pilots/captions/orphan/en.vtt");
        verify(captionUploads).delete(upload);
    }
}
