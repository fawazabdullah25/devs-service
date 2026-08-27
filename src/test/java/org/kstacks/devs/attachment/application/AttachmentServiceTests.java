package org.kstacks.devs.attachment.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kstacks.devs.attachment.api.AttachmentDtos;
import org.kstacks.devs.attachment.domain.AttachmentStatus;
import org.kstacks.devs.attachment.domain.UnitAttachmentEntity;
import org.kstacks.devs.attachment.domain.UnitAttachmentRepository;
import org.kstacks.devs.config.AttachmentProperties;
import org.kstacks.devs.content.domain.ContentUnitEntity;
import org.kstacks.devs.content.domain.ContentUnitRepository;
import org.kstacks.devs.content.domain.ContentKind;
import org.kstacks.devs.content.domain.ContentVisibility;
import org.kstacks.devs.content.domain.LearningContentEntity;
import org.kstacks.devs.media.application.ObjectStorage;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTests {
    @Mock private ContentUnitRepository units;
    @Mock private UnitAttachmentRepository attachments;
    @Mock private ObjectStorage storage;
    private AttachmentService service;
    private ContentUnitEntity unit;

    @BeforeEach
    void setUp() {
        var properties = new AttachmentProperties(
            URI.create("https://video.example.test/"), 1_000, 20, Duration.ofDays(7), Duration.ofHours(1), Duration.ofHours(24)
        );
        service = new AttachmentService(units, attachments, storage, properties, new AttachmentLocationResolver(properties));
        unit = new ContentUnitEntity("lesson", 1, "Lesson", null, null, null, null);
        var content = LearningContentEntity.draft(
            "attachment-test", ContentKind.COURSE, ContentVisibility.PUBLIC, "Course", "Summary"
        );
        content.addUnit(unit);
    }

    @Test
    void signsDirectUploadsWithSafeResponseMetadata() {
        when(units.findById(unit.getId())).thenReturn(Optional.of(unit));
        when(attachments.countByUnitIdAndStatusNot(unit.getId(), AttachmentStatus.DELETED)).thenReturn(0L);
        when(storage.signUpload(any(), any(), any(Long.class), any())).thenAnswer(invocation ->
            new ObjectStorage.UploadGrant(
                URI.create("https://r2.example.test/upload"), invocation.getArgument(0),
                Map.of("Content-Type", invocation.getArgument(1)), Instant.now().plusSeconds(600)
            )
        );

        var result = service.requestUpload(unit.getId(), new AttachmentDtos.UploadRequest(
            "Lesson notes", "ملاحظات الدرس", "../notes.pdf", "text/html", 900
        ));

        assertThat(result.attachment().status()).isEqualTo(AttachmentStatus.UPLOADING);
        assertThat(result.attachment().contentType()).isEqualTo("application/pdf");
        assertThat(result.objectKey()).startsWith("attachments/" + unit.getId()).endsWith("/notes.pdf");
        verify(storage).signUpload(result.objectKey(), "application/pdf", 900, "inline; filename*=UTF-8''notes.pdf");
    }

    @Test
    void confirmsOnlyObjectsWithTheDeclaredSize() {
        when(units.findById(unit.getId())).thenReturn(Optional.of(unit));
        when(attachments.countByUnitIdAndStatusNot(unit.getId(), AttachmentStatus.DELETED)).thenReturn(0L);
        when(storage.signUpload(any(), any(), any(Long.class), any())).thenReturn(new ObjectStorage.UploadGrant(
            URI.create("https://r2.example.test/upload"), "ignored", Map.of(), Instant.now().plusSeconds(600)
        ));
        var grant = service.requestUpload(unit.getId(), new AttachmentDtos.UploadRequest(
            "Starter", null, "starter.zip", "application/zip", 500
        ));
        var saved = ArgumentCaptor.forClass(UnitAttachmentEntity.class);
        verify(attachments).save(saved.capture());
        var entity = saved.getValue();
        when(attachments.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(storage.exists(entity.getObjectKey())).thenReturn(true);
        when(storage.size(entity.getObjectKey())).thenReturn(499L);

        assertThatThrownBy(() -> service.complete(unit.getId(), entity.getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(entity.getStatus()).isEqualTo(AttachmentStatus.UPLOADING);
    }

    @Test
    void rejectsUnsupportedAndOversizedFilesBeforeSigning() {
        when(units.findById(unit.getId())).thenReturn(Optional.of(unit));
        when(attachments.countByUnitIdAndStatusNot(unit.getId(), AttachmentStatus.DELETED)).thenReturn(0L);

        assertThatThrownBy(() -> service.requestUpload(unit.getId(), new AttachmentDtos.UploadRequest(
            "Page", null, "page.html", "text/html", 100
        ))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.requestUpload(unit.getId(), new AttachmentDtos.UploadRequest(
            "Huge", null, "huge.pdf", "application/pdf", 1_001
        ))).isInstanceOf(ResponseStatusException.class);

        verify(storage, never()).signUpload(any(), any(), any(Long.class), any());
    }
}
