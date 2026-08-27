package org.kstacks.devs.media.api;

import jakarta.validation.Valid;
import org.kstacks.devs.media.application.CoverService;
import org.kstacks.devs.media.application.MediaReplacementService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/devs/api/v1/admin/content")
public class AdminContentMediaController {
    private final MediaReplacementService replacementService;
    private final CoverService coverService;

    public AdminContentMediaController(MediaReplacementService replacementService, CoverService coverService) {
        this.replacementService = replacementService;
        this.coverService = coverService;
    }

    @PutMapping("/{contentId}/units/{unitId}/media")
    public MediaDtos.MediaVersion replace(
        @PathVariable UUID contentId,
        @PathVariable UUID unitId,
        @Valid @RequestBody MediaDtos.MediaReplacementRequest request
    ) {
        return replacementService.replace(contentId, unitId, request);
    }

    @GetMapping("/{contentId}/units/{unitId}/media/versions")
    public List<MediaDtos.MediaVersion> versions(
        @PathVariable UUID contentId,
        @PathVariable UUID unitId
    ) {
        return replacementService.versions(contentId, unitId);
    }

    @PostMapping("/{contentId}/units/{unitId}/media/versions/{mediaId}/restore")
    public List<MediaDtos.MediaVersion> rollback(
        @PathVariable UUID contentId,
        @PathVariable UUID unitId,
        @PathVariable UUID mediaId
    ) {
        return replacementService.rollback(contentId, unitId, mediaId);
    }

    @PostMapping("/{contentId}/cover/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaDtos.CoverUploadGrant coverUpload(
        @PathVariable UUID contentId,
        @Valid @RequestBody MediaDtos.CoverUploadRequest request
    ) {
        return coverService.requestUpload(contentId, request);
    }

    @PostMapping("/{contentId}/cover/complete")
    public MediaDtos.Cover coverComplete(
        @PathVariable UUID contentId,
        @Valid @RequestBody MediaDtos.CoverCompleteRequest request
    ) {
        return coverService.complete(contentId, request);
    }

    @DeleteMapping("/{contentId}/cover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCover(@PathVariable UUID contentId) {
        coverService.delete(contentId);
    }
}
