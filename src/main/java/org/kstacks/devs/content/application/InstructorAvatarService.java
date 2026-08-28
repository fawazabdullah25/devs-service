package org.kstacks.devs.content.application;

import org.kstacks.devs.config.AttachmentProperties;
import org.kstacks.devs.content.api.ContentDtos;
import org.kstacks.devs.content.domain.InstructorAvatarEntity;
import org.kstacks.devs.content.domain.InstructorAvatarRepository;
import org.kstacks.devs.content.domain.InstructorAvatarStatus;
import org.kstacks.devs.content.domain.InstructorRepository;
import org.kstacks.devs.media.application.ObjectStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Direct-to-R2 avatar upload lifecycle for reusable instructor profiles. */
@Service
public class InstructorAvatarService {
    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> CONTENT_TYPES = Map.of(
        "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png",
        "webp", "image/webp", "avif", "image/avif"
    );
    private static final Set<String> ALLOWED_TYPES = Set.copyOf(CONTENT_TYPES.values());

    private final InstructorRepository instructors;
    private final InstructorAvatarRepository avatars;
    private final ObjectStorage storage;
    private final AttachmentProperties properties;

    public InstructorAvatarService(
        InstructorRepository instructors,
        InstructorAvatarRepository avatars,
        ObjectStorage storage,
        AttachmentProperties properties
    ) {
        this.instructors = instructors;
        this.avatars = avatars;
        this.storage = storage;
        this.properties = properties;
    }

    @Transactional
    public ContentDtos.InstructorAvatarUploadGrant requestUpload(UUID instructorId, ContentDtos.InstructorAvatarUploadRequest request) {
        requireInstructor(instructorId);
        var filename = safeFilename(request.filename());
        var extension = extension(filename);
        var contentType = request.contentType().trim().toLowerCase(Locale.ROOT);
        if (!CONTENT_TYPES.containsKey(extension) || !ALLOWED_TYPES.contains(contentType) ||
            !CONTENT_TYPES.get(extension).equals(contentType)) {
            throw badRequest("Avatar must be a JPEG, PNG, WebP, or AVIF image");
        }
        if (request.contentLength() > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Avatar exceeds the 5 MiB upload limit");
        }
        var objectKey = "instructor-avatar/" + instructorId + "/" + UUID.randomUUID() + "/" + filename;
        var avatar = avatars.save(InstructorAvatarEntity.uploading(
            instructorId, objectKey, filename, contentType, request.contentLength()
        ));
        var grant = storage.signUpload(objectKey, contentType, request.contentLength(),
            "inline; filename*=UTF-8''" + encodeFilename(filename));
        return new ContentDtos.InstructorAvatarUploadGrant(
            toDto(avatar), grant.uploadUrl(), grant.objectKey(), grant.headers(), grant.expiresAt()
        );
    }

    @Transactional
    public ContentDtos.InstructorAvatar complete(UUID instructorId, ContentDtos.InstructorAvatarCompleteRequest request) {
        requireInstructor(instructorId);
        var avatar = owned(instructorId, request.avatarId());
        if (avatar.getStatus() != InstructorAvatarStatus.UPLOADING) {
            if (avatar.getStatus() == InstructorAvatarStatus.READY) return toDto(avatar);
            throw conflict("Avatar is not awaiting upload confirmation");
        }
        if (!storage.exists(avatar.getObjectKey())) throw conflict("Avatar upload is not complete");
        if (storage.size(avatar.getObjectKey()) != avatar.getContentLength()) {
            throw conflict("Uploaded avatar size does not match the declared size");
        }
        avatars.findFirstByInstructorIdAndStatusOrderByCreatedAtDesc(instructorId, InstructorAvatarStatus.READY)
            .filter(current -> !current.getId().equals(avatar.getId()))
            .ifPresent(current -> {
                current.softDelete(properties.retention());
                avatars.flush();
            });
        avatar.ready();
        return toDto(avatar);
    }

    @Transactional
    public void delete(UUID instructorId) {
        requireInstructor(instructorId);
        avatars.findFirstByInstructorIdAndStatusOrderByCreatedAtDesc(instructorId, InstructorAvatarStatus.READY)
            .ifPresent(avatar -> avatar.softDelete(properties.retention()));
    }

    /** Retire every object owned by a profile that is being deleted. */
    @Transactional
    public void retireForInstructor(UUID instructorId) {
        avatars.findAllByInstructorId(instructorId).stream()
            .filter(avatar -> avatar.getStatus() != InstructorAvatarStatus.DELETED)
            .forEach(avatar -> avatar.softDelete(properties.retention()));
    }

    @Transactional(readOnly = true)
    public URI resolveActiveUrl(UUID instructorId) {
        return avatars.findFirstByInstructorIdAndStatusOrderByCreatedAtDesc(instructorId, InstructorAvatarStatus.READY)
            .map(avatar -> properties.publicBaseUrl().resolve(avatar.getObjectKey()))
            .orElse(null);
    }

    public ContentDtos.InstructorAvatar toDto(InstructorAvatarEntity avatar) {
        var url = avatar.getStatus() == InstructorAvatarStatus.READY
            ? properties.publicBaseUrl().resolve(avatar.getObjectKey()) : null;
        return new ContentDtos.InstructorAvatar(
            avatar.getId(), avatar.getOriginalFilename(), avatar.getContentType(), avatar.getContentLength(),
            avatar.getStatus().name(), url, avatar.getCreatedAt(), avatar.getUpdatedAt(),
            avatar.getDeletedAt(), avatar.getPurgeAfter()
        );
    }

    private void requireInstructor(UUID id) {
        if (!instructors.existsByIdAndDeletedAtIsNull(id)) throw notFound("Instructor not found");
    }

    private InstructorAvatarEntity owned(UUID instructorId, UUID avatarId) {
        var avatar = avatars.findById(avatarId).orElseThrow(() -> notFound("Avatar not found"));
        if (!avatar.getInstructorId().equals(instructorId)) throw notFound("Avatar not found");
        return avatar;
    }

    private String safeFilename(String input) {
        var basename = input.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1).trim();
        var sanitized = basename.replaceAll("[^A-Za-z0-9._-]", "-").replaceAll("-+", "-");
        if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")) throw badRequest("Avatar filename is invalid");
        return sanitized.substring(0, Math.min(sanitized.length(), 180));
    }

    private String extension(String filename) {
        var index = filename.lastIndexOf('.');
        return index < 1 ? "" : filename.substring(index + 1).toLowerCase(Locale.ROOT);
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

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
