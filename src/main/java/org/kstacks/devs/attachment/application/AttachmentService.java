package org.kstacks.devs.attachment.application;

import org.kstacks.devs.attachment.api.AttachmentDtos;
import org.kstacks.devs.attachment.domain.AttachmentStatus;
import org.kstacks.devs.attachment.domain.UnitAttachmentEntity;
import org.kstacks.devs.attachment.domain.UnitAttachmentRepository;
import org.kstacks.devs.config.AttachmentProperties;
import org.kstacks.devs.content.domain.ContentUnitRepository;
import org.kstacks.devs.media.application.ObjectStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "pdf", "zip", "ppt", "pptx", "doc", "docx", "txt", "md",
        "png", "jpg", "jpeg", "webp", "gif",
        "c", "h", "cpp", "hpp", "cs", "go", "java", "js", "jsx", "json", "kt", "php", "py", "rb",
        "rs", "sh", "sql", "swift", "ts", "tsx", "xml", "yaml", "yml", "css"
    );

    private final ContentUnitRepository units;
    private final UnitAttachmentRepository attachments;
    private final ObjectStorage storage;
    private final AttachmentProperties properties;
    private final AttachmentLocationResolver locations;

    public AttachmentService(ContentUnitRepository units, UnitAttachmentRepository attachments, ObjectStorage storage,
                             AttachmentProperties properties, AttachmentLocationResolver locations) {
        this.units = units; this.attachments = attachments; this.storage = storage;
        this.properties = properties; this.locations = locations;
    }

    @Transactional
    public AttachmentDtos.UploadGrant requestUpload(UUID unitId, AttachmentDtos.UploadRequest request) {
        var unit = units.findById(unitId).orElseThrow(() -> notFound("Lesson not found"));
        if (attachments.countByUnitIdAndStatusNot(unitId, AttachmentStatus.DELETED) >= properties.maxPerUnit()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This lesson has reached its attachment limit");
        }
        if (request.contentLength() > properties.maxUploadBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Attachment exceeds the configured upload limit");
        }
        var filename = normalizedFilename(request.filename());
        var extension = extension(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) throw badRequest("This attachment file type is not allowed");
        var safeContentType = safeContentType(extension);
        var objectKey = "attachments/" + unitId + "/" + UUID.randomUUID() + "/" + filename;
        var position = unit.getAttachments().stream()
            .filter(item -> item.getStatus() != AttachmentStatus.DELETED)
            .mapToInt(UnitAttachmentEntity::getPosition).max().orElse(0) + 1;
        var entity = UnitAttachmentEntity.uploading(unit, objectKey, request.filename().trim(), safeContentType,
            request.contentLength(), request.title().trim(), clean(request.titleAr()), position);
        // Keep the object path UUID independent from the database UUID; both remain unguessable.
        attachments.save(entity);
        var disposition = (extension.equals("pdf") ? "inline" : "attachment") + "; filename*=UTF-8''" + encodeFilename(filename);
        var grant = storage.signUpload(objectKey, safeContentType, request.contentLength(), disposition);
        return new AttachmentDtos.UploadGrant(toDto(entity), grant.uploadUrl(), grant.objectKey(), grant.headers(), grant.expiresAt());
    }

    @Transactional
    public AttachmentDtos.Attachment complete(UUID unitId, UUID attachmentId) {
        var attachment = findOwned(unitId, attachmentId);
        if (attachment.getStatus() != AttachmentStatus.UPLOADING) throw conflict("Attachment is not awaiting upload confirmation");
        if (!storage.exists(attachment.getObjectKey())) throw conflict("Attachment upload is not complete");
        var actualSize = storage.size(attachment.getObjectKey());
        if (actualSize >= 0 && actualSize != attachment.getContentLength()) {
            throw conflict("Uploaded attachment size does not match the declared size");
        }
        attachment.ready();
        return toDto(attachment);
    }

    @Transactional
    public AttachmentDtos.Attachment update(UUID unitId, UUID attachmentId, AttachmentDtos.UpdateRequest request) {
        var attachment = findOwned(unitId, attachmentId);
        if (attachment.getStatus() != AttachmentStatus.READY) throw conflict("Only ready attachments can be edited");
        attachment.update(request.title().trim(), clean(request.titleAr()), request.position());
        return toDto(attachment);
    }

    @Transactional
    public void delete(UUID unitId, UUID attachmentId) {
        var attachment = findOwned(unitId, attachmentId);
        if (attachment.getStatus() == AttachmentStatus.DELETED) return;
        attachment.softDelete(properties.retention());
    }

    @Transactional
    public AttachmentDtos.Attachment restore(UUID unitId, UUID attachmentId) {
        var attachment = findOwned(unitId, attachmentId);
        if (attachment.getStatus() != AttachmentStatus.DELETED) throw conflict("Attachment is not deleted");
        attachment.restore();
        return toDto(attachment);
    }

    @Transactional(readOnly = true)
    public java.util.List<AttachmentDtos.Attachment> deleted(UUID unitId) {
        if (!units.existsById(unitId)) throw notFound("Lesson not found");
        return attachments.findByUnitIdAndStatusOrderByPosition(unitId, AttachmentStatus.DELETED)
            .stream().map(this::toDto).toList();
    }

    @Transactional
    public java.util.List<AttachmentDtos.Attachment> reorder(UUID unitId, AttachmentDtos.OrderRequest request) {
        if (!units.existsById(unitId)) throw notFound("Lesson not found");
        var ready = attachments.findByUnitIdAndStatusOrderByPosition(unitId, AttachmentStatus.READY);
        if (ready.size() != request.attachmentIds().size() || !ready.stream().map(UnitAttachmentEntity::getId).collect(java.util.stream.Collectors.toSet())
            .equals(Set.copyOf(request.attachmentIds()))) throw badRequest("Order must contain every ready attachment exactly once");
        var byId = ready.stream().collect(java.util.stream.Collectors.toMap(UnitAttachmentEntity::getId, item -> item));
        for (int index = 0; index < request.attachmentIds().size(); index++) {
            var item = byId.get(request.attachmentIds().get(index));
            item.update(item.getTitleEn(), item.getTitleAr(), index + 1);
        }
        return ready.stream().sorted(Comparator.comparingInt(UnitAttachmentEntity::getPosition)).map(this::toDto).toList();
    }

    public AttachmentDtos.Attachment toDto(UnitAttachmentEntity entity) {
        var url = entity.getStatus() == AttachmentStatus.READY ? locations.resolve(entity.getObjectKey()) : null;
        return new AttachmentDtos.Attachment(entity.getId(), entity.getTitleEn(), entity.getTitleAr(), entity.getOriginalFilename(),
            entity.getContentType(), entity.getContentLength(), entity.getPosition(), url, entity.getStatus(),
            entity.getDeletedAt(), entity.getPurgeAfter());
    }

    private UnitAttachmentEntity findOwned(UUID unitId, UUID attachmentId) {
        var entity = attachments.findById(attachmentId).orElseThrow(() -> notFound("Attachment not found"));
        if (!entity.getUnit().getId().equals(unitId)) throw notFound("Attachment not found");
        return entity;
    }
    private String normalizedFilename(String value) {
        var leaf = value.replace('\\', '/'); leaf = leaf.substring(leaf.lastIndexOf('/') + 1).trim();
        var safe = leaf.replaceAll("[^A-Za-z0-9._-]", "-").replaceAll("-+", "-");
        if (safe.isBlank() || safe.equals(".") || safe.equals("..")) throw badRequest("Attachment filename is invalid");
        return safe.length() <= 180 ? safe : safe.substring(safe.length() - 180);
    }
    private String extension(String filename) {
        var index = filename.lastIndexOf('.'); return index < 1 ? "" : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }
    private String safeContentType(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }
    private String encodeFilename(String value) {
        var builder = new StringBuilder();
        for (byte octet : value.getBytes(StandardCharsets.UTF_8)) {
            var unsigned = octet & 0xff;
            if ((unsigned >= 'a' && unsigned <= 'z') || (unsigned >= 'A' && unsigned <= 'Z') ||
                (unsigned >= '0' && unsigned <= '9') || "._-".indexOf(unsigned) >= 0) builder.append((char) unsigned);
            else builder.append('%').append(String.format("%02X", unsigned));
        }
        return builder.toString();
    }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
