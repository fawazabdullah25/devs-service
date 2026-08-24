package org.kstacks.devs.content.application;

import org.kstacks.devs.content.api.ContentDtos;
import org.kstacks.devs.content.domain.ContentKind;
import org.kstacks.devs.content.domain.ContentUnitEntity;
import org.kstacks.devs.content.domain.LearningContentEntity;
import org.kstacks.devs.content.domain.LearningContentRepository;
import org.kstacks.devs.content.domain.PublicationStatus;
import org.kstacks.devs.content.domain.SpokenLanguage;
import org.kstacks.devs.media.domain.MediaAssetRepository;
import org.kstacks.devs.media.domain.MediaStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.UUID;

import static org.kstacks.devs.content.application.ContentSpecifications.kind;
import static org.kstacks.devs.content.application.ContentSpecifications.language;
import static org.kstacks.devs.content.application.ContentSpecifications.level;
import static org.kstacks.devs.content.application.ContentSpecifications.publicVisibility;
import static org.kstacks.devs.content.application.ContentSpecifications.published;
import static org.kstacks.devs.content.application.ContentSpecifications.query;
import static org.kstacks.devs.content.application.ContentSpecifications.topic;

@Service
public class ContentService {
    private final LearningContentRepository repository;
    private final MediaAssetRepository mediaRepository;
    private final ContentMapper mapper;
    private final ContentAccessPolicy accessPolicy;

    public ContentService(LearningContentRepository repository, MediaAssetRepository mediaRepository, ContentMapper mapper, ContentAccessPolicy accessPolicy) {
        this.repository = repository;
        this.mediaRepository = mediaRepository;
        this.mapper = mapper;
        this.accessPolicy = accessPolicy;
    }

    @Transactional(readOnly = true)
    public ContentDtos.Home home() {
        var content = repository.findAll(published().and(publicVisibility()), Sort.by(Sort.Direction.DESC, "publishedAt"));
        var featured = content.stream()
            .filter(item -> item.getFeaturedRank() != null)
            .sorted(Comparator.comparing(LearningContentEntity::getFeaturedRank))
            .map(mapper::toDto)
            .toList();
        if (featured.size() < 4) featured = java.util.List.of();
        var latest = content.stream().map(mapper::toDto).toList();
        var courses = content.stream().filter(item -> item.getKind() == ContentKind.COURSE).count();
        var series = content.stream().filter(item -> item.getKind() == ContentKind.SERIES).count();
        var lessons = content.stream().mapToLong(item -> item.getUnits().size()).sum();
        return new ContentDtos.Home(featured, latest, new ContentDtos.Counts(courses, series, lessons));
    }

    @Transactional(readOnly = true)
    public ContentDtos.Catalog catalog(String text, ContentKind contentKind, String topicSlug, String levelSlug, SpokenLanguage spokenLanguage) {
        var specification = published().and(publicVisibility()).and(query(text)).and(kind(contentKind))
            .and(topic(topicSlug)).and(level(levelSlug)).and(language(spokenLanguage));
        var items = repository.findAll(specification, Sort.by(Sort.Direction.DESC, "publishedAt"))
            .stream().map(mapper::toDto).toList();
        return new ContentDtos.Catalog(items, items.size(), ReferenceCatalog.topics(), ReferenceCatalog.levels());
    }

    @Transactional(readOnly = true)
    public ContentDtos.LearningContent getPublished(String slug, Authentication authentication) {
        var content = repository.findDetailedBySlug(slug)
            .filter(item -> item.getStatus() == PublicationStatus.PUBLISHED)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found"));
        accessPolicy.assertCanView(content, authentication);
        return mapper.toDto(content);
    }

    @Transactional(readOnly = true)
    public java.util.List<ContentDtos.LearningContent> adminContent() {
        return repository.findAllByOrderByUpdatedAtDesc().stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ContentDtos.AdminSummary adminSummary() {
        var content = repository.findAll();
        return new ContentDtos.AdminSummary(
            repository.countByStatus(PublicationStatus.PUBLISHED),
            repository.countByStatus(PublicationStatus.DRAFT),
            repository.countByStatus(PublicationStatus.ARCHIVED),
            mediaRepository.countByStatus(MediaStatus.PROCESSING),
            content.stream().mapToLong(LearningContentEntity::getViews).sum(),
            content.stream().mapToLong(LearningContentEntity::getWatchedMinutes).sum()
        );
    }

    @Transactional
    public ContentDtos.LearningContent create(ContentDtos.MetadataRequest request) {
        if (repository.existsBySlug(request.slug())) throw conflict("Slug is already in use");
        try {
            return mapper.toDto(repository.save(LearningContentEntity.draft(
                request.slug(), request.kind(), request.visibility(), request.title().trim(), request.summary().trim()
            )));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Slug is already in use");
        }
    }

    @Transactional
    public ContentDtos.LearningContent update(UUID id, ContentDtos.MetadataRequest request) {
        var content = find(id);
        if (repository.existsBySlugAndIdNot(request.slug(), id)) throw conflict("Slug is already in use");
        content.updateMetadata(request.slug(), request.kind(), request.visibility(), request.title().trim(), request.summary().trim());
        return mapper.toDto(content);
    }

    @Transactional
    public ContentDtos.LearningContent addUnit(UUID contentId, ContentDtos.UnitRequest request) {
        var content = find(contentId);
        if (request.position() < 1) throw badRequest("Position must be greater than zero");
        if (content.getUnits().stream().anyMatch(unit -> unit.getSlug().equals(request.slug()) || unit.getPosition() == request.position())) {
            throw conflict("Unit slug and position must be unique within the content");
        }
        var media = mediaRepository.findById(request.mediaId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
        var section = request.sectionId() == null ? null : content.getSections().stream()
            .filter(candidate -> candidate.getId().equals(request.sectionId()))
            .findFirst()
            .orElseThrow(() -> badRequest("Section does not belong to this series"));
        if (content.getKind() == ContentKind.COURSE && section != null) {
            throw badRequest("Courses cannot contain sections");
        }
        if (content.getKind() == ContentKind.SERIES && content.getStatus() == PublicationStatus.PUBLISHED
            && !content.getSections().isEmpty() && section == null) {
            throw badRequest("Published sectioned series require a destination section");
        }
        var unit = new ContentUnitEntity(
            request.slug(), request.position(), request.title().trim(), request.titleAr(), request.summary(), request.summaryAr(), media
        );
        unit.organize(section, request.position());
        content.addUnit(unit);
        return mapper.toDto(content);
    }

    @Transactional
    public ContentDtos.LearningContent publish(UUID id) {
        var content = find(id);
        if (content.getUnits().isEmpty()) throw badRequest("At least one learning unit is required");
        if (content.getKind() == ContentKind.COURSE && content.getUnits().size() != 1) throw badRequest("A course must contain exactly one unit");
        if (content.getKind() == ContentKind.SERIES && content.getUnits().size() < 2) throw badRequest("A series must contain at least two units");
        if (content.getKind() == ContentKind.COURSE && !content.getSections().isEmpty()) throw badRequest("Courses cannot contain sections");
        if (content.getKind() == ContentKind.SERIES && !content.getSections().isEmpty()) {
            if (content.getUnits().stream().anyMatch(unit -> unit.getSection() == null)) {
                throw badRequest("Every lesson must belong to a section before publishing");
            }
            if (content.getSections().stream().anyMatch(section -> content.getUnits().stream()
                .noneMatch(unit -> section.equals(unit.getSection())))) {
                throw badRequest("Every section must contain at least one lesson before publishing");
            }
        }
        if (content.getUnits().stream().anyMatch(unit -> unit.getMedia() == null || unit.getMedia().getStatus() != MediaStatus.READY)) {
            throw badRequest("Every unit must have ready media before publishing");
        }
        content.publish();
        return mapper.toDto(content);
    }

    @Transactional
    public ContentDtos.LearningContent archive(UUID id) {
        var content = find(id);
        content.archive();
        return mapper.toDto(content);
    }

    private LearningContentEntity find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found"));
    }

    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
