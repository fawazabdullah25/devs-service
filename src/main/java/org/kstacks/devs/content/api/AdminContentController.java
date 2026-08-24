package org.kstacks.devs.content.api;

import jakarta.validation.Valid;
import org.kstacks.devs.content.application.ContentService;
import org.kstacks.devs.content.application.CurriculumService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/devs/api/v1/admin")
public class AdminContentController {
    private final ContentService service;
    private final CurriculumService curriculumService;

    public AdminContentController(ContentService service, CurriculumService curriculumService) {
        this.service = service;
        this.curriculumService = curriculumService;
    }

    @GetMapping("/content")
    public List<ContentDtos.LearningContent> content() {
        return service.adminContent();
    }

    @GetMapping("/analytics/summary")
    public ContentDtos.AdminSummary summary() {
        return service.adminSummary();
    }

    @PostMapping("/content")
    @ResponseStatus(HttpStatus.CREATED)
    public ContentDtos.LearningContent create(@Valid @RequestBody ContentDtos.MetadataRequest request) {
        return service.create(request);
    }

    @PatchMapping("/content/{id}")
    public ContentDtos.LearningContent update(@PathVariable UUID id, @Valid @RequestBody ContentDtos.MetadataRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/content/{id}/units")
    @ResponseStatus(HttpStatus.CREATED)
    public ContentDtos.LearningContent addUnit(@PathVariable UUID id, @Valid @RequestBody ContentDtos.UnitRequest request) {
        return service.addUnit(id, request);
    }

    @PutMapping("/content/{id}/curriculum")
    public ContentDtos.LearningContent replaceCurriculum(
        @PathVariable UUID id,
        @Valid @RequestBody ContentDtos.CurriculumRequest request
    ) {
        return curriculumService.replace(id, request);
    }

    @PostMapping("/content/{id}/publish")
    public ContentDtos.LearningContent publish(@PathVariable UUID id) {
        return service.publish(id);
    }

    @PostMapping("/content/{id}/archive")
    public ContentDtos.LearningContent archive(@PathVariable UUID id) {
        return service.archive(id);
    }
}
