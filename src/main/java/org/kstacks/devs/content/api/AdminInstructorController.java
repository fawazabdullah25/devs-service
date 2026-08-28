package org.kstacks.devs.content.api;

import jakarta.validation.Valid;
import org.kstacks.devs.content.application.InstructorService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final org.kstacks.devs.content.application.InstructorAvatarService avatarService;

    public AdminInstructorController(InstructorService service, org.kstacks.devs.content.application.InstructorAvatarService avatarService) {
        this.service = service;
        this.avatarService = avatarService;
    }

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

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }

    @PostMapping("/{id}/avatar/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public ContentDtos.InstructorAvatarUploadGrant avatarUpload(
        @PathVariable UUID id,
        @Valid @RequestBody ContentDtos.InstructorAvatarUploadRequest request
    ) {
        return avatarService.requestUpload(id, request);
    }

    @PostMapping("/{id}/avatar/complete")
    public ContentDtos.InstructorAvatar avatarComplete(
        @PathVariable UUID id,
        @Valid @RequestBody ContentDtos.InstructorAvatarCompleteRequest request
    ) {
        return avatarService.complete(id, request);
    }

    @DeleteMapping("/{id}/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAvatar(@PathVariable UUID id) { avatarService.delete(id); }
}
