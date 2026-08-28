package org.kstacks.devs.content.application;

import org.kstacks.devs.content.api.ContentDtos;
import org.kstacks.devs.content.domain.ContentKind;
import org.kstacks.devs.content.domain.ContentUnitEntity;
import org.kstacks.devs.content.domain.InstructorEntity;
import org.kstacks.devs.content.domain.InstructorRepository;
import org.kstacks.devs.content.domain.LearningContentEntity;
import org.kstacks.devs.content.domain.LearningContentRepository;
import org.kstacks.devs.content.domain.PublicationStatus;
import org.kstacks.devs.content.domain.SpokenLanguage;
import org.kstacks.devs.content.domain.TagEntity;
import org.kstacks.devs.content.domain.TagGroup;
import org.kstacks.devs.content.domain.TagRepository;
import org.kstacks.devs.media.domain.MediaAssetRepository;
import org.kstacks.devs.media.domain.MediaStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.kstacks.devs.content.application.ContentSpecifications.active;
import static org.kstacks.devs.content.application.ContentSpecifications.kind;
import static org.kstacks.devs.content.application.ContentSpecifications.language;
import static org.kstacks.devs.content.application.ContentSpecifications.publicVisibility;
import static org.kstacks.devs.content.application.ContentSpecifications.published;
import static org.kstacks.devs.content.application.ContentSpecifications.query;
import static org.kstacks.devs.content.application.ContentSpecifications.tag;

/** Application operations for the learning catalog and its editorial lifecycle. */
@Service
public class ContentService {
    private static final Duration TRASH_RETENTION = Duration.ofDays(7);

    private final LearningContentRepository repository;
    private final MediaAssetRepository mediaRepository;
    private final ContentMapper mapper;
    private final ContentAccessPolicy accessPolicy;
    private final InstructorRepository instructorRepository;
    private final TagRepository tagRepository;

    @Autowired
    public ContentService(
        LearningContentRepository repository,
        MediaAssetRepository mediaRepository,
        ContentMapper mapper,
        ContentAccessPolicy accessPolicy,
        InstructorRepository instructorRepository,
        TagRepository tagRepository
    ) {
        this.repository = repository;
        this.mediaRepository = mediaRepository;
        this.mapper = mapper;
        this.accessPolicy = accessPolicy;
        this.instructorRepository = instructorRepository;
        this.tagRepository = tagRepository;
    }

    /** Compatibility constructor for unit tests that predate instructor support. */
    public ContentService(
        LearningContentRepository repository,
        MediaAssetRepository mediaRepository,
        ContentMapper mapper,
        ContentAccessPolicy accessPolicy
    ) {
        this(repository, mediaRepository, mapper, accessPolicy, null, null);
    }

    @Transactional(readOnly = true)
    public ContentDtos.Home home() {
        var content = repository.findAll(
            published().and(publicVisibility()).and(active()),
            Sort.by(Sort.Direction.DESC, "publishedAt")
        );
        var featured = content.stream()
            .filter(item -> item.getFeaturedRank() != null)
            .sorted(Comparator.comparing(LearningContentEntity::getFeaturedRank))
            .map(mapper::toDto)
            .toList();
        if (featured.size() < 4) featured = List.of();
        var latest = content.stream().map(mapper::toDto).toList();
        var courses = content.stream().filter(item -> item.getKind() == ContentKind.COURSE).count();
        var series = content.stream().filter(item -> item.getKind() == ContentKind.SERIES).count();
        var lessons = content.stream().mapToLong(item -> item.getActiveUnits().size()).sum();
        return new ContentDtos.Home(featured, latest, new ContentDtos.Counts(courses, series, lessons));
    }

    @Transactional(readOnly = true)
    public ContentDtos.Catalog catalog(
        String text,
        ContentKind contentKind,
        String tagSlug,
        SpokenLanguage spokenLanguage
    ) {
        var specification = published().and(publicVisibility()).and(active()).and(query(text)).and(kind(contentKind))
            .and(tag(normalizeTag(tagSlug))).and(language(spokenLanguage));
        var items = repository.findAll(specification, Sort.by(Sort.Direction.DESC, "publishedAt"))
            .stream().map(mapper::toDto).toList();
        return new ContentDtos.Catalog(items, items.size(), allTags());
    }

    @Transactional(readOnly = true)
    public ContentDtos.LearningContent getPublished(String slug, Authentication authentication) {
        var content = repository.findDetailedBySlugAndDeletedAtIsNull(slug)
            .filter(item -> item.getStatus() == PublicationStatus.PUBLISHED)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found"));
        accessPolicy.assertCanView(content, authentication);
        return mapper.toDto(content);
    }

    @Transactional(readOnly = true)
    public List<ContentDtos.LearningContent> adminContent() {
        return repository.findAllByDeletedAtIsNullOrderByUpdatedAtDesc().stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ContentDtos.LearningContent> deletedContent() {
        return repository.findAllByDeletedAtIsNotNullOrderByDeletedAtDesc().stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ContentDtos.LearningContent adminDetails(UUID id) { return mapper.toDto(find(id)); }

    @Transactional(readOnly = true)
    public ContentDtos.ReferenceData referenceData() {
        var instructors = instructorRepository == null
            ? List.<ContentDtos.InstructorProfile>of()
            : instructorRepository.findAllByDeletedAtIsNullOrderByNameEnAsc().stream().map(mapper::toProfile).toList();
        return new ContentDtos.ReferenceData(allTags(), instructors);
    }

    @Transactional(readOnly = true)
    public ContentDtos.AdminSummary adminSummary() {
        var content = repository.findAllByDeletedAtIsNullOrderByUpdatedAtDesc();
        var processingMedia = content.stream()
            .flatMap(item -> item.getActiveUnits().stream())
            .map(ContentUnitEntity::getMedia)
            .filter(java.util.Objects::nonNull)
            .filter(media -> media.getStatus() == MediaStatus.PROCESSING)
            .count();
        return new ContentDtos.AdminSummary(
            content.stream().filter(item -> item.getStatus() == PublicationStatus.PUBLISHED).count(),
            content.stream().filter(item -> item.getStatus() == PublicationStatus.DRAFT).count(),
            content.stream().filter(item -> item.getStatus() == PublicationStatus.ARCHIVED).count(),
            processingMedia,
            content.stream().mapToLong(LearningContentEntity::getViews).sum(),
            content.stream().mapToLong(LearningContentEntity::getWatchedMinutes).sum()
        );
    }

    @Transactional
    public ContentDtos.LearningContent create(ContentDtos.CreateMetadataRequest request) {
        var slug = slug(request.slug(), "Slug");
        if (repository.existsBySlug(slug)) throw conflict("Slug is already in use");
        try {
            return mapper.toDto(repository.save(LearningContentEntity.draft(
                slug,
                request.kind(),
                request.visibility(),
                required(request.title(), "Title", 240),
                required(request.summary(), "Summary", 600)
            )));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Slug is already in use");
        }
    }

    @Transactional
    public ContentDtos.LearningContent update(UUID id, ContentDtos.UpdateMetadataRequest request) {
        var content = find(id);
        var slug = slug(request.slug(), "Slug");
        if (repository.existsBySlugAndIdNot(slug, id)) throw conflict("Slug is already in use");

        var tags = resolveTags(request);
        var instructors = resolveInstructors(request.instructorIds());
        var featuredRank = request.featuredRank();
        if (featuredRank != null && featuredRank < 1) throw badRequest("Featured rank must be positive");

        content.updateMetadata(
            slug,
            required(request.title(), "Title", 240),
            clean(request.titleAr()),
            required(request.summary(), "Summary", 600),
            clean(request.summaryAr()),
            required(request.description(), "Description", 20000),
            clean(request.descriptionAr()),
            request.visibility(),
            request.spokenLanguage(),
            tags,
            instructors,
            featuredRank
        );
        return mapper.toDto(content);
    }

    @Transactional
    public ContentDtos.LearningContent addUnit(UUID contentId, ContentDtos.UnitRequest request) {
        var content = find(contentId);
        if (request.position() == null || request.position() < 1) throw badRequest("Position must be greater than zero");
        var unitSlug = slug(request.slug(), "Unit slug");
        if (content.getUnits().stream().anyMatch(unit -> unit.getSlug().equals(unitSlug) || unit.getPosition() == request.position())) {
            throw conflict("Unit slug and position must be unique within the content");
        }
        if (content.getKind() == ContentKind.COURSE && !content.getActiveUnits().isEmpty()) {
            throw conflict("A course can contain only one unit");
        }
        var media = mediaRepository.findById(request.mediaId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
        if (!media.isAvailableForAttachment()) {
            throw conflict("Only ready, nondeleted media can be attached to a lesson");
        }
        if (mediaRepository.countCurrentAttachments(media.getId()) > 0) {
            throw conflict("Media is already attached to another lesson");
        }
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
            unitSlug, request.position(), required(request.title(), "Unit title", 240),
            clean(request.titleAr()), clean(request.summary()), clean(request.summaryAr()), media
        );
        unit.organize(section, request.position());
        content.addUnit(unit);
        return mapper.toDto(content);
    }

    @Transactional
    public ContentDtos.LearningContent publish(UUID id) {
        var content = find(id);
        var units = content.getActiveUnits();
        if (units.isEmpty()) throw badRequest("At least one learning unit is required");
        if (content.getKind() == ContentKind.COURSE && units.size() != 1) throw badRequest("A course must contain exactly one unit");
        if (content.getKind() == ContentKind.SERIES && units.size() < 2) throw badRequest("A series must contain at least two units");
        if (content.getKind() == ContentKind.COURSE && !content.getSections().isEmpty()) throw badRequest("Courses cannot contain sections");
        if (content.getKind() == ContentKind.SERIES && !content.getSections().isEmpty()) {
            if (units.stream().anyMatch(unit -> unit.getSection() == null)) {
                throw badRequest("Every lesson must belong to a section before publishing");
            }
            if (content.getSections().stream().anyMatch(section -> units.stream()
                .noneMatch(unit -> section.equals(unit.getSection())))) {
                throw badRequest("Every section must contain at least one lesson before publishing");
            }
        }
        if (units.stream().anyMatch(unit -> unit.getMedia() == null || unit.getMedia().getStatus() != MediaStatus.READY)) {
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

    @Transactional
    public ContentDtos.LearningContent unarchive(UUID id) {
        var content = find(id);
        content.unarchive();
        return mapper.toDto(content);
    }

    @Transactional
    public ContentDtos.LearningContent delete(UUID id) {
        var content = findIncludingDeleted(id);
        if (content.isPurgeClaimed()) throw conflict("Content is being purged and cannot be changed");
        content.softDelete(TRASH_RETENTION);
        return mapper.toDto(content);
    }

    @Transactional
    public ContentDtos.LearningContent restore(UUID id) {
        var content = findIncludingDeleted(id);
        if (content.isPurgeClaimed()) throw conflict("Content is being purged and cannot be restored");
        if (content.isDeleted()) content.restoreFromTrash();
        return mapper.toDto(content);
    }

    @Transactional
    public ContentDtos.LearningContent updateUnit(UUID contentId, UUID unitId, ContentDtos.UnitUpdateRequest request) {
        var content = find(contentId);
        var unit = activeUnit(content, unitId);
        var slug = slug(request.slug(), "Unit slug");
        if (content.getUnits().stream().anyMatch(candidate -> !candidate.getId().equals(unitId) && candidate.getSlug().equals(slug))) {
            throw conflict("Unit slug must be unique within the content");
        }
        if (request.sectionId() != null && content.getKind() == ContentKind.COURSE) {
            throw badRequest("Courses cannot contain sections");
        }
        var section = request.sectionId() == null ? null : content.getSections().stream()
            .filter(candidate -> candidate.getId().equals(request.sectionId()))
            .findFirst()
            .orElseThrow(() -> badRequest("Section does not belong to this series"));
        if (content.getKind() == ContentKind.SERIES && content.getStatus() == PublicationStatus.PUBLISHED
            && !content.getSections().isEmpty() && section == null) {
            throw badRequest("Published sectioned series require a destination section");
        }
        unit.updateMetadata(
            slug,
            required(request.title(), "Unit title", 240),
            clean(request.titleAr()),
            clean(request.summary()),
            clean(request.summaryAr())
        );
        unit.organize(section, unit.getPosition());
        return mapper.toDto(content);
    }

    @Transactional
    public ContentDtos.ContentUnit deleteUnit(UUID contentId, UUID unitId) {
        var content = find(contentId);
        var unit = activeUnit(content, unitId);
        unit.organize(null, nextPositionAfterAll(content));
        unit.softDelete(TRASH_RETENTION);
        return mapper.toUnitDto(unit);
    }

    @Transactional(readOnly = true)
    public List<ContentDtos.ContentUnit> deletedUnits(UUID contentId) {
        var content = find(contentId);
        return content.getUnits().stream()
            .filter(ContentUnitEntity::isDeleted)
            .sorted(Comparator.comparing(ContentUnitEntity::getDeletedAt).reversed())
            .map(mapper::toUnitDto)
            .toList();
    }

    @Transactional
    public ContentDtos.ContentUnit restoreUnit(UUID contentId, UUID unitId) {
        var content = find(contentId);
        var unit = content.getUnits().stream().filter(candidate -> candidate.getId().equals(unitId)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found"));
        if (unit.isDeleted()) {
            unit.restoreFromTrash();
            unit.organize(null, nextUnusedPosition(content, unit));
        }
        return mapper.toUnitDto(unit);
    }

    /** Internal hook for a future purge worker; this method intentionally does not delete anything. */
    @Transactional(readOnly = true)
    public List<LearningContentEntity> contentDueForPurge(Instant now) {
        return repository.findAllByDeletedAtIsNotNullAndPurgeAfterLessThanEqualOrderByPurgeAfter(now);
    }

    private List<ContentDtos.Tag> allTags() {
        return tagRepository == null ? List.of() : tagRepository.findAllByOrderByGroupAscNameEnAsc().stream()
            .map(tag -> new ContentDtos.Tag(tag.getId(), tag.getGroup(), tag.getSlug(),
                new ContentDtos.LocalizedText(tag.getNameEn(), tag.getNameAr())))
            .toList();
    }

    private Set<TagEntity> resolveTags(ContentDtos.UpdateMetadataRequest request) {
        if (tagRepository == null) throw badRequest("Tag support is unavailable");
        var requested = request.effectiveTagSlugs();
        if (requested == null) throw badRequest("Tags are required");
        var tags = new LinkedHashSet<TagEntity>();
        var difficultyCount = 0;
        for (var value : requested) {
            var slug = normalizeTag(value);
            var tag = tagRepository.findBySlug(slug)
                .orElseThrow(() -> badRequest("Unknown tag: " + value));
            if (!tags.add(tag)) throw badRequest("A tag cannot be selected more than once");
            if (tag.getGroup() == TagGroup.DIFFICULTY && ++difficultyCount > 1) {
                throw badRequest("Only one difficulty tag can be selected");
            }
        }
        return tags;
    }

    private Set<InstructorEntity> resolveInstructors(List<UUID> ids) {
        if (ids == null) throw badRequest("Instructors are required");
        var unique = new LinkedHashSet<>(ids);
        if (unique.size() != ids.size()) throw badRequest("An instructor cannot be selected more than once");
        if (unique.isEmpty()) return Set.of();
        if (instructorRepository == null) throw badRequest("Instructor support is unavailable");
        var instructors = instructorRepository.findAllById(unique);
        if (instructors.size() != unique.size()) throw badRequest("One or more instructors do not exist");
        return new LinkedHashSet<>(instructors);
    }

    private ContentUnitEntity activeUnit(LearningContentEntity content, UUID unitId) {
        return content.getUnits().stream()
            .filter(unit -> unit.getId().equals(unitId) && !unit.isDeleted())
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found"));
    }

    private int nextUnusedPosition(LearningContentEntity content, ContentUnitEntity excluded) {
        var used = content.getUnits().stream()
            .filter(unit -> !unit.getId().equals(excluded.getId()))
            .map(ContentUnitEntity::getPosition)
            .collect(java.util.stream.Collectors.toSet());
        var position = 1;
        while (used.contains(position)) position++;
        return position;
    }

    private int nextPositionAfterAll(LearningContentEntity content) {
        return content.getUnits().stream().mapToInt(ContentUnitEntity::getPosition).max().orElse(0) + 1;
    }

    private LearningContentEntity find(UUID id) {
        return repository.findDetailedByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found"));
    }

    private LearningContentEntity findIncludingDeleted(UUID id) {
        return repository.findDetailedById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found"));
    }

    private String normalizeTag(String value) {
        if (value == null) return null;
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) throw badRequest(name + " is required");
        return value.trim();
    }

    private String required(String value, String name, int maxLength) {
        var normalized = required(value, name);
        if (normalized.length() > maxLength) throw badRequest(name + " is too long");
        return normalized;
    }

    private String slug(String value, String name) {
        var normalized = required(value, name, 180);
        if (!normalized.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$")) {
            throw badRequest(name + " must contain only lowercase letters, numbers, and hyphens");
        }
        return normalized;
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
