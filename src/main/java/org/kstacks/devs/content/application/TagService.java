package org.kstacks.devs.content.application;

import org.kstacks.devs.content.api.ContentDtos;
import org.kstacks.devs.content.domain.TagEntity;
import org.kstacks.devs.content.domain.TagRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TagService {
    private final TagRepository repository;

    public TagService(TagRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public List<ContentDtos.Tag> list() {
        return repository.findAllByOrderByGroupAscNameEnAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public ContentDtos.Tag create(ContentDtos.TagCreateRequest request) {
        var slug = slug(request.slug());
        if (repository.existsBySlug(slug)) throw conflict("Tag slug is already in use");
        try {
            return toDto(repository.save(TagEntity.create(
                request.group(), slug, required(request.nameEn(), "English name"), clean(request.nameAr())
            )));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Tag slug is already in use");
        }
    }

    @Transactional
    public ContentDtos.Tag update(UUID id, ContentDtos.TagUpdateRequest request) {
        var tag = repository.findById(id).orElseThrow(() -> notFound("Tag not found"));
        var slug = slug(request.slug());
        if (repository.existsBySlugAndIdNot(slug, id)) throw conflict("Tag slug is already in use");
        if (tag.getGroup() != request.group() && repository.countAssignments(id) > 0) {
            throw conflict("An assigned tag cannot change groups");
        }
        tag.update(request.group(), slug, required(request.nameEn(), "English name"), clean(request.nameAr()));
        return toDto(tag);
    }

    @Transactional
    public void delete(UUID id) {
        var tag = repository.findById(id).orElseThrow(() -> notFound("Tag not found"));
        if (repository.countAssignments(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tag is assigned to content and cannot be deleted");
        }
        repository.delete(tag);
    }

    public ContentDtos.Tag toDto(TagEntity tag) {
        return new ContentDtos.Tag(tag.getId(), tag.getGroup(), tag.getSlug(),
            new ContentDtos.LocalizedText(tag.getNameEn(), tag.getNameAr()));
    }

    private String slug(String value) {
        var normalized = required(value, "Slug").toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Slug must contain only lowercase letters, numbers, and hyphens");
        }
        return normalized;
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " is required");
        return value.trim();
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
