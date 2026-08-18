package org.kstacks.devs.media.api;

import jakarta.validation.Valid;
import org.kstacks.devs.media.application.MediaService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/devs/api/v1/admin/media")
public class AdminMediaController {
    private final MediaService service;

    public AdminMediaController(MediaService service) {
        this.service = service;
    }

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaDtos.UploadGrant upload(@Valid @RequestBody MediaDtos.UploadRequest request) {
        return service.createUpload(request);
    }

    @PostMapping("/imports")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaDtos.IngestResponse importSource(@Valid @RequestBody MediaDtos.ImportRequest request) {
        return service.importAndIngest(request);
    }

    @PostMapping("/static-hls")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaDtos.StaticHlsRegistrationResponse registerStaticHls(
        @Valid @RequestBody MediaDtos.StaticHlsRegistrationRequest request
    ) {
        return service.registerStaticHls(request);
    }

    @PostMapping("/{id}/ingest")
    public MediaDtos.IngestResponse ingest(@PathVariable UUID id) {
        return service.ingest(id);
    }

    @GetMapping("/{id}")
    public MediaDtos.MediaStatusResponse status(@PathVariable UUID id) {
        return service.status(id);
    }
}
