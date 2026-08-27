package org.kstacks.devs.media.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kstacks.devs.config.MediaProperties;
import org.kstacks.devs.config.StaticHlsProperties;
import org.kstacks.devs.media.domain.MediaAssetEntity;
import org.kstacks.devs.media.domain.MediaAssetRepository;
import org.kstacks.devs.media.domain.MediaStatus;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaPurgeJobTests {
    @Mock private MediaAssetRepository repository;
    @Mock private ObjectStorage storage;

    private MediaPurgeJob job;

    @BeforeEach
    void setUp() {
        var locations = new StaticHlsLocationResolver(new StaticHlsProperties(
            true, URI.create("https://video.example.test/"), "pilots", Duration.ofSeconds(2)
        ));
        job = new MediaPurgeJob(
            repository, storage, locations, new MediaProperties(1_000, 20)
        );
        when(repository.findByStatusAndCreatedAtLessThanEqual(eq(MediaStatus.UPLOADING), any()))
            .thenReturn(List.of());
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
}
