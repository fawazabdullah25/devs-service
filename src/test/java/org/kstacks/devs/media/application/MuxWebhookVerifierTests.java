package org.kstacks.devs.media.application;

import org.junit.jupiter.api.Test;
import org.kstacks.devs.config.MuxProperties;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MuxWebhookVerifierTests {
    private static final String SECRET = "webhook-secret";
    private final MuxWebhookVerifier verifier = new MuxWebhookVerifier(
        new MuxProperties(true, "id", "secret", SECRET, "public")
    );

    @Test
    void acceptsAValidCurrentSignature() throws Exception {
        var payload = "{\"type\":\"video.asset.ready\"}";
        var timestamp = Long.toString(Instant.now().getEpochSecond());
        assertThatCode(() -> verifier.verify(payload, signature(timestamp, payload))).doesNotThrowAnyException();
    }

    @Test
    void rejectsTamperingAndOldRequests() throws Exception {
        var payload = "{\"type\":\"video.asset.ready\"}";
        var current = Long.toString(Instant.now().getEpochSecond());
        var old = Long.toString(Instant.now().minusSeconds(600).getEpochSecond());
        assertThatThrownBy(() -> verifier.verify(payload + "x", signature(current, payload)))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> verifier.verify(payload, signature(old, payload)))
            .isInstanceOf(ResponseStatusException.class);
    }

    private String signature(String timestamp, String payload) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        var value = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
        return "t=" + timestamp + ",v1=" + value;
    }
}
