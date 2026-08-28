package org.kstacks.devs.media.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kstacks.devs.config.MediaProperties;
import org.kstacks.devs.config.StaticHlsProperties;
import org.kstacks.devs.media.api.MediaDtos;
import org.kstacks.devs.media.domain.MediaAssetEntity;
import org.kstacks.devs.media.domain.MediaAssetRepository;
import org.kstacks.devs.media.domain.CaptionUploadRepository;
import org.kstacks.devs.media.domain.MediaStatus;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaLifecycleServiceTests {
    @Mock private MediaAssetRepository repository;
    @Mock private ObjectStorage storage;
    @Mock private CaptionUploadRepository captionUploads;
    @Mock private StaticHlsPackageValidator validator;

    private MediaService service;

    @BeforeEach
    void setUp() {
        var staticProperties = new StaticHlsProperties(
            true, URI.create("https://video.example.test/"), "pilots", Duration.ofSeconds(2)
        );
        service = new MediaService(
            repository, captionUploads, storage, new MediaProperties(1_000, 20), validator,
            new StaticHlsLocationResolver(staticProperties)
        );
        lenient().when(repository.findCurrentAttachmentRows()).thenReturn(List.of());
    }

    @Test
    void rejectsDeletingMediaThatIsCurrentlyAttached() {
        var media = readyMedia();
        when(repository.findById(media.getId())).thenReturn(Optional.of(media));
        when(repository.countCurrentAttachments(media.getId())).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(media.getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(media.getStatus()).isEqualTo(MediaStatus.READY);
    }

    @Test
    void softDeletesAndRestoresAnUnattachedMediaAsset() {
        var media = readyMedia();
        when(repository.findById(media.getId())).thenReturn(Optional.of(media));
        when(repository.countCurrentAttachments(media.getId())).thenReturn(0L);

        service.delete(media.getId());

        assertThat(media.getStatus()).isEqualTo(MediaStatus.DELETED);
        assertThat(media.getDeletedAt()).isNotNull();
        assertThat(media.getPurgeAfter()).isAfter(media.getDeletedAt());

        var restored = service.restore(media.getId());

        assertThat(restored.status()).isEqualTo(MediaStatus.READY);
        assertThat(media.getStatus()).isEqualTo(MediaStatus.READY);
        assertThat(media.getDeletedAt()).isNull();
    }

    @Test
    void listsCurrentAttachmentMetadataAlongsideMedia() {
        var media = readyMedia();
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(media));
        when(repository.findCurrentAttachmentRows()).thenReturn(List.<Object[]>of(new Object[] {
            media.getId(),
            java.util.UUID.randomUUID(),
            java.util.UUID.randomUUID(),
            "Content title",
            null,
            "Lesson title",
            null
        }));

        var result = service.list(null, null);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.mediaId()).isEqualTo(media.getId());
            assertThat(item.currentAttachment()).isNotNull();
            assertThat(item.currentAttachment().contentTitle()).isEqualTo("Content title");
            assertThat(item.currentAttachment().unitTitle()).isEqualTo("Lesson title");
        });
    }

    @Test
    void keepsDeletedAndRetainedHistoryOutOfTheActiveLibrary() {
        var active = readyMedia();
        var deleted = readyMedia();
        deleted.softDelete(Duration.ofDays(7));
        var retained = readyMedia();
        retained.retainForUnit(java.util.UUID.randomUUID(), Duration.ofDays(30));
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(active, deleted, retained));

        assertThat(service.list(null, false)).extracting(MediaDtos.MediaLibraryItem::mediaId)
            .containsExactly(active.getId());
        assertThat(service.list(null, null)).extracting(MediaDtos.MediaLibraryItem::mediaId)
            .containsExactly(active.getId());
        assertThat(service.list(null, true)).extracting(MediaDtos.MediaLibraryItem::mediaId)
            .containsExactly(deleted.getId());
    }

    @Test
    void refusesRestoringADeletedMediaThatIsReattachedBeforeTheRequest() {
        var media = readyMedia();
        media.softDelete(Duration.ofDays(7));
        when(repository.findById(media.getId())).thenReturn(Optional.of(media));
        when(repository.countCurrentAttachments(media.getId())).thenReturn(1L);

        // The restore endpoint must not create a second attachment. A deleted
        // row cannot normally be attached, but this guards a race/legacy row.
        assertThatThrownBy(() -> service.restore(media.getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(repository, never()).save(any());
    }

    private MediaAssetEntity readyMedia() {
        return MediaAssetEntity.staticHls("pilots/example/v1/master.m3u8", 60, null, "v1", List.of());
    }
}
