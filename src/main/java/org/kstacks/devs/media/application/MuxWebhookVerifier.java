package org.kstacks.devs.media.application;

import org.kstacks.devs.config.MuxProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class MuxWebhookVerifier {
    private static final long TOLERANCE_SECONDS = 300;
    private final MuxProperties properties;

    public MuxWebhookVerifier(MuxProperties properties) {
        this.properties = properties;
    }

    public void verify(String payload, String signatureHeader) {
        if (!properties.enabled() || properties.webhookSecret() == null || properties.webhookSecret().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Mux webhooks are not configured");
        }
        var timestamp = value(signatureHeader, "t");
        var signature = value(signatureHeader, "v1");
        if (timestamp == null || signature == null) throw unauthorized();
        try {
            var seconds = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - seconds) > TOLERANCE_SECONDS) throw unauthorized();
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var expected = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            var provided = HexFormat.of().parseHex(signature);
            if (!MessageDigest.isEqual(expected, provided)) throw unauthorized();
        } catch (IllegalArgumentException exception) {
            throw unauthorized();
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private String value(String header, String key) {
        if (header == null) return null;
        for (var part : header.split(",")) {
            var pair = part.trim().split("=", 2);
            if (pair.length == 2 && pair[0].equals(key)) return pair[1];
        }
        return null;
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Mux signature");
    }
}
