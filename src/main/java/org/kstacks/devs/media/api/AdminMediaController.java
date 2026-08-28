package org.kstacks.devs.media.api;

import jakarta.validation.Valid;
import org.kstacks.devs.media.application.MediaService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    public java.util.List<MediaDtos.MediaLibraryItem> list(
        @RequestParam(required = false) org.kstacks.devs.media.domain.MediaStatus status,
        @RequestParam(required = false) Boolean deleted
    ) {
        return service.list(status, deleted);
    }

    @PostMapping("/caption-uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaDtos.CaptionUploadGrant captionUpload(@Valid @RequestBody MediaDtos.CaptionUploadRequest request) {
        return service.requestCaptionUpload(request);
    }

    @PostMapping("/caption-uploads/{uploadId}/complete")
    public MediaDtos.CaptionUploadCompleteResponse completeCaptionUpload(@PathVariable UUID uploadId) {
        return service.completeCaptionUpload(uploadId);
    }

    @PostMapping("/static-hls")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaDtos.StaticHlsRegistrationResponse registerStaticHls(
        @Valid @RequestBody MediaDtos.StaticHlsRegistrationRequest request
    ) {
        return service.registerStaticHls(request);
    }

    @GetMapping("/{id}")
    public MediaDtos.MediaStatusResponse status(@PathVariable UUID id) {
        return service.status(id);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}/captions")
    public MediaDtos.MediaStatusResponse updateCaptions(
        @PathVariable UUID id,
        @Valid @RequestBody MediaDtos.CaptionUpdateRequest request
    ) {
        return service.updateCaptions(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @GetMapping("/deleted")
    public java.util.List<MediaDtos.MediaLibraryItem> deleted() {
        return service.list(null, true);
    }

    @PostMapping("/{id}/restore")
    public MediaDtos.MediaLibraryItem restore(@PathVariable UUID id) {
        return service.restore(id);
    }
}
