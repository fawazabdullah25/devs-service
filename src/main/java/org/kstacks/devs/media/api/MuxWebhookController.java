package org.kstacks.devs.media.api;

import org.kstacks.devs.media.application.MediaService;
import org.kstacks.devs.media.application.MuxWebhookVerifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/devs/api/v1/webhooks")
public class MuxWebhookController {
    private final MuxWebhookVerifier verifier;
    private final MediaService service;

    public MuxWebhookController(MuxWebhookVerifier verifier, MediaService service) {
        this.verifier = verifier;
        this.service = service;
    }

    @PostMapping("/mux")
    public ResponseEntity<Void> mux(
        @RequestHeader(name = "Mux-Signature", required = false) String signature,
        @RequestBody String body
    ) {
        verifier.verify(body, signature);
        service.receiveMuxEvent(body);
        return ResponseEntity.noContent().build();
    }
}
