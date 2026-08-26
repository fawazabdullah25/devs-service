package org.kstacks.devs.content.application;

import jakarta.persistence.EntityManager;
import org.kstacks.devs.content.api.ContentDtos;
import org.kstacks.devs.content.domain.ContentKind;
import org.kstacks.devs.content.domain.ContentSectionEntity;
import org.kstacks.devs.content.domain.ContentUnitEntity;
import org.kstacks.devs.content.domain.LearningContentRepository;
import org.kstacks.devs.content.domain.PublicationStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

@Service
public class CurriculumService {
    private final LearningContentRepository repository;
    private final ContentMapper mapper;
    private final EntityManager entityManager;

    public CurriculumService(LearningContentRepository repository, ContentMapper mapper, EntityManager entityManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Transactional
    public ContentDtos.LearningContent replace(UUID contentId, ContentDtos.CurriculumRequest request) {
        var content = repository.findDetailedById(contentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found"));
        if (content.getKind() != ContentKind.SERIES) {
            throw badRequest("Only series can contain sections");
        }

        var units = new HashMap<UUID, ContentUnitEntity>();
        content.getUnits().forEach(unit -> units.put(unit.getId(), unit));
        validateCompleteCurriculum(request, units);

        var existingSections = new HashMap<UUID, ContentSectionEntity>();
        content.getSections().forEach(section -> existingSections.put(section.getId(), section));
        validateSectionIds(request, existingSections);
        validatePublishedCurriculum(content.getStatus(), request);

        var temporaryUnitPosition = Math.max(
                content.getUnits().stream()
                        .mapToInt(ContentUnitEntity::getPosition)
                        .max()
                        .orElse(0),
                content.getUnits().size()) + 1;

        for (var unit : content.getUnits()) {
            unit.organize(null, temporaryUnitPosition++);
        }

        var temporarySectionPosition = Math.max(
                content.getSections().stream()
                        .mapToInt(ContentSectionEntity::getPosition)
                        .max()
                        .orElse(0),
                request.sections().size()) + 1;

        for (var section : content.getSections()) {
            section.moveTo(temporarySectionPosition++);
        }

        entityManager.flush();

        var retainedSectionIds = new HashSet<UUID>();
        request.sections().stream()
                .map(ContentDtos.CurriculumSectionRequest::id)
                .filter(java.util.Objects::nonNull)
                .forEach(retainedSectionIds::add);
        content.getSections().stream()
                .filter(section -> !retainedSectionIds.contains(section.getId()))
                .toList()
                .forEach(content::removeSection);

        var globalPosition = 1;
        for (var sectionIndex = 0; sectionIndex < request.sections().size(); sectionIndex++) {
            var sectionRequest = request.sections().get(sectionIndex);
            var section = sectionRequest.id() == null
                    ? new ContentSectionEntity(sectionIndex + 1, sectionRequest.title().trim(),
                            clean(sectionRequest.titleAr()),
                            clean(sectionRequest.description()), clean(sectionRequest.descriptionAr()))
                    : existingSections.get(sectionRequest.id());
            if (sectionRequest.id() == null)
                content.addSection(section);
            else
                section.update(sectionIndex + 1, sectionRequest.title().trim(), clean(sectionRequest.titleAr()),
                        clean(sectionRequest.description()), clean(sectionRequest.descriptionAr()));

            for (var unitId : sectionRequest.unitIds()) {
                units.get(unitId).organize(section, globalPosition++);
            }
        }
        for (var unitId : request.unsectionedUnitIds()) {
            units.get(unitId).organize(null, globalPosition++);
        }

        content.markCurriculumChanged();
        entityManager.flush();
        return mapper.toDto(content);
    }

    private void validateCompleteCurriculum(ContentDtos.CurriculumRequest request, Map<UUID, ContentUnitEntity> units) {
        var supplied = new HashSet<UUID>();
        request.sections().forEach(section -> section.unitIds().forEach(id -> addUnitId(id, supplied, units)));
        request.unsectionedUnitIds().forEach(id -> addUnitId(id, supplied, units));
        if (supplied.size() != units.size()) {
            throw badRequest("Every lesson must appear exactly once in the curriculum");
        }
    }

    private void addUnitId(UUID id, HashSet<UUID> supplied, Map<UUID, ContentUnitEntity> units) {
        if (!units.containsKey(id))
            throw badRequest("Curriculum contains a lesson that does not belong to this series");
        if (!supplied.add(id))
            throw badRequest("A lesson cannot appear more than once in the curriculum");
    }

    private void validateSectionIds(ContentDtos.CurriculumRequest request, Map<UUID, ContentSectionEntity> existing) {
        var supplied = new HashSet<UUID>();
        request.sections().stream().map(ContentDtos.CurriculumSectionRequest::id)
                .filter(java.util.Objects::nonNull)
                .forEach(id -> {
                    if (!existing.containsKey(id))
                        throw badRequest("Curriculum contains a section that does not belong to this series");
                    if (!supplied.add(id))
                        throw badRequest("A section cannot appear more than once in the curriculum");
                });
    }

    private void validatePublishedCurriculum(PublicationStatus status, ContentDtos.CurriculumRequest request) {
        if (status != PublicationStatus.PUBLISHED || request.sections().isEmpty())
            return;
        if (!request.unsectionedUnitIds().isEmpty()) {
            throw badRequest("Published sectioned series cannot contain unsectioned lessons");
        }
        if (request.sections().stream().anyMatch(section -> section.unitIds().isEmpty())) {
            throw badRequest("Published series sections cannot be empty");
        }
    }

    private String clean(String value) {
        if (value == null || value.isBlank())
            return null;
        return value.trim();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
