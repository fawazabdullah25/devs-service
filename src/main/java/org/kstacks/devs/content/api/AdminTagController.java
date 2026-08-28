package org.kstacks.devs.content.api;

import jakarta.validation.Valid;
import org.kstacks.devs.content.application.TagService;
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
@RequestMapping("/devs/api/v1/admin/tags")
public class AdminTagController {
    private final TagService service;

    public AdminTagController(TagService service) { this.service = service; }

    @GetMapping
    public List<ContentDtos.Tag> list() { return service.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContentDtos.Tag create(@Valid @RequestBody ContentDtos.TagCreateRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}")
    public ContentDtos.Tag update(@PathVariable UUID id, @Valid @RequestBody ContentDtos.TagUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
