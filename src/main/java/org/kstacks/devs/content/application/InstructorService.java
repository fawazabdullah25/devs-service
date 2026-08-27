package org.kstacks.devs.content.application;

import org.kstacks.devs.content.api.ContentDtos;
import org.kstacks.devs.content.domain.InstructorEntity;
import org.kstacks.devs.content.domain.InstructorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class InstructorService {
    private final InstructorRepository repository;
    private final ContentMapper mapper;

    public InstructorService(InstructorRepository repository, ContentMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ContentDtos.InstructorProfile> list() {
        return repository.findAllByOrderByNameEnAsc().stream().map(mapper::toProfile).toList();
    }

    @Transactional
    public ContentDtos.InstructorProfile create(ContentDtos.InstructorCreateRequest request) {
        var instructor = InstructorEntity.create(
            required(request.nameEn(), "Name is required"),
            clean(request.nameAr()),
            clean(request.bioEn()),
            clean(request.bioAr()),
            required(request.initials(), "Initials are required"),
            clean(request.avatarUrl())
        );
        return mapper.toProfile(repository.save(instructor));
    }

    @Transactional
    public ContentDtos.InstructorProfile update(UUID id, ContentDtos.InstructorUpdateRequest request) {
        var instructor = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instructor not found"));
        // accountSubject intentionally is not accepted by the request and is left untouched.
        instructor.update(
            required(request.nameEn(), "Name is required"),
            clean(request.nameAr()),
            clean(request.bioEn()),
            clean(request.bioAr()),
            required(request.initials(), "Initials are required"),
            clean(request.avatarUrl())
        );
        return mapper.toProfile(instructor);
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return value.trim();
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
