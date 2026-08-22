package org.kstacks.devs.attachment.api;

import jakarta.validation.Valid;
import org.kstacks.devs.attachment.application.AttachmentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/devs/api/v1/admin/units/{unitId}/attachments")
public class AdminAttachmentController {
    private final AttachmentService service;

    public AdminAttachmentController(AttachmentService service) { this.service = service; }

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentDtos.UploadGrant upload(@PathVariable UUID unitId, @Valid @RequestBody AttachmentDtos.UploadRequest request) {
        return service.requestUpload(unitId, request);
    }

    @PostMapping("/{attachmentId}/complete")
    public AttachmentDtos.Attachment complete(@PathVariable UUID unitId, @PathVariable UUID attachmentId) {
        return service.complete(unitId, attachmentId);
    }

    @PatchMapping("/{attachmentId}")
    public AttachmentDtos.Attachment update(@PathVariable UUID unitId, @PathVariable UUID attachmentId,
                                             @Valid @RequestBody AttachmentDtos.UpdateRequest request) {
        return service.update(unitId, attachmentId, request);
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID unitId, @PathVariable UUID attachmentId) { service.delete(unitId, attachmentId); }

    @PostMapping("/{attachmentId}/restore")
    public AttachmentDtos.Attachment restore(@PathVariable UUID unitId, @PathVariable UUID attachmentId) {
        return service.restore(unitId, attachmentId);
    }

    @GetMapping("/deleted")
    public List<AttachmentDtos.Attachment> deleted(@PathVariable UUID unitId) { return service.deleted(unitId); }

    @PutMapping("/order")
    public List<AttachmentDtos.Attachment> reorder(@PathVariable UUID unitId,
                                                    @Valid @RequestBody AttachmentDtos.OrderRequest request) {
        return service.reorder(unitId, request);
    }
}
