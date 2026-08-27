package org.kstacks.devs.content.api;

import jakarta.validation.Valid;
import org.kstacks.devs.content.application.InstructorService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/devs/api/v1/admin/instructors")
public class AdminInstructorController {
    private final InstructorService service;

    public AdminInstructorController(InstructorService service) { this.service = service; }

    @GetMapping
    public List<ContentDtos.InstructorProfile> list() { return service.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContentDtos.InstructorProfile create(@Valid @RequestBody ContentDtos.InstructorCreateRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}")
    public ContentDtos.InstructorProfile update(
        @PathVariable UUID id,
        @Valid @RequestBody ContentDtos.InstructorUpdateRequest request
    ) {
        return service.update(id, request);
    }
}
