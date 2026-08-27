package org.kstacks.devs.media.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kstacks.devs.config.AttachmentProperties;
import org.kstacks.devs.config.MediaProperties;
import org.kstacks.devs.media.api.MediaDtos;
import org.kstacks.devs.media.domain.ContentCoverEntity;
import org.kstacks.devs.media.domain.ContentCoverRepository;
import org.kstacks.devs.media.domain.CoverStatus;
import org.kstacks.devs.content.domain.LearningContentRepository;
import org.kstacks.devs.content.domain.LearningContentEntity;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoverServiceTests {
    @Mock private LearningContentRepository contents;
    @Mock private LearningContentEntity content;
    @Mock private ContentCoverRepository covers;
    @Mock private ObjectStorage storage;

    private CoverService service;
    private final UUID contentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var attachmentProperties = new AttachmentProperties(
            URI.create("https://cdn.example.test/"), 1_000, 20,
            Duration.ofDays(7), Duration.ofHours(1), Duration.ofHours(24)
        );
        service = new CoverService(
            contents, covers, storage, attachmentProperties, new MediaProperties(1_000, 20)
        );
        lenient().when(contents.findById(contentId)).thenReturn(Optional.of(content));
        lenient().when(content.isDeleted()).thenReturn(false);
        lenient().when(content.isPurgeClaimed()).thenReturn(false);
        lenient().when(covers.save(any(ContentCoverEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void validatesCoverTypeAndSizeBeforeCreatingAnUploadRow() {
        assertThatThrownBy(() -> service.requestUpload(contentId, new MediaDtos.CoverUploadRequest(
            "cover.gif", "image/gif", 100
        ))).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.requestUpload(contentId, new MediaDtos.CoverUploadRequest(
            "cover.png", "image/png", 10L * 1024 * 1024 + 1
        ))).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void rejectsNewCoverUploadsForTrashedContent() {
        when(content.isDeleted()).thenReturn(true);

        assertThatThrownBy(() -> service.requestUpload(contentId, new MediaDtos.CoverUploadRequest(
            "cover.png", "image/png", 100
        ))).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(storage, never()).signUpload(any(), any(), any(Long.class), any());
    }

    @Test
    void signsAnImageUploadUnderTheContentScopedCoverPrefix() {
        when(storage.signUpload(any(), any(), any(Long.class), any())).thenAnswer(invocation ->
            new ObjectStorage.UploadGrant(
                URI.create("https://r2.example.test/upload"), invocation.getArgument(0),
                Map.of("Content-Type", invocation.getArgument(1)), Instant.now().plusSeconds(600)
            )
        );

        var result = service.requestUpload(contentId, new MediaDtos.CoverUploadRequest(
            "../course cover.webp", "image/webp", 800
        ));

        assertThat(result.cover().status()).isEqualTo(CoverStatus.UPLOADING.name());
        assertThat(result.objectKey()).startsWith("cover/" + contentId + "/").endsWith("/course-cover.webp");
        verify(storage).signUpload(result.objectKey(), "image/webp", 800, "inline; filename*=UTF-8''course-cover.webp");
    }

    @Test
    void confirmsExactObjectSizeAndRetiresThePreviousActiveCover() {
        var next = ContentCoverEntity.uploading(contentId, "cover/next/cover.png", "cover.png", "image/png", 900);
        var previous = ContentCoverEntity.uploading(contentId, "cover/old/cover.png", "old.png", "image/png", 700);
        previous.ready();
        when(covers.findById(next.getId())).thenReturn(Optional.of(next));
        when(storage.exists(next.getObjectKey())).thenReturn(true);
        when(storage.size(next.getObjectKey())).thenReturn(900L);
        when(covers.findFirstByContentIdAndStatusOrderByCreatedAtDesc(contentId, CoverStatus.READY))
            .thenReturn(Optional.of(previous));

        var result = service.complete(contentId, new MediaDtos.CoverCompleteRequest(next.getId()));

        assertThat(result.status()).isEqualTo(CoverStatus.READY.name());
        assertThat(next.getStatus()).isEqualTo(CoverStatus.READY);
        assertThat(previous.getStatus()).isEqualTo(CoverStatus.DELETED);
        assertThat(previous.getPurgeAfter()).isAfter(previous.getDeletedAt());
        verify(content).clearLegacyCoverUrl();
        verify(covers).flush();
    }
}
