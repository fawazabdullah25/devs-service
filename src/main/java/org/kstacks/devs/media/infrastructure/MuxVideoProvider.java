package org.kstacks.devs.media.infrastructure;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.kstacks.devs.config.MuxProperties;
import org.kstacks.devs.media.application.VideoProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "devs.mux.enabled", havingValue = "true")
public class MuxVideoProvider implements VideoProvider {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper;
    private final MuxProperties properties;

    public MuxVideoProvider(ObjectMapper mapper, MuxProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    public CreatedAsset createAsset(URI sourceUrl) {
        try {
            var payload = mapper.writeValueAsString(Map.of(
                "inputs", List.of(Map.of("url", sourceUrl.toString())),
                "playback_policies", List.of(properties.playbackPolicy()),
                "video_quality", "basic"
            ));
            var credentials = Base64.getEncoder().encodeToString(
                (require(properties.tokenId(), "Mux token ID") + ":" + require(properties.tokenSecret(), "Mux token secret"))
                    .getBytes(StandardCharsets.UTF_8)
            );
            var request = HttpRequest.newBuilder(URI.create("https://api.mux.com/video/v1/assets"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Mux rejected the asset request");
            }
            var assetId = mapper.readTree(response.body()).path("data").path("id").asText();
            if (assetId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Mux returned no asset ID");
            return new CreatedAsset(assetId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Mux request was interrupted", exception);
        } catch (java.io.IOException | JacksonException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Mux request failed", exception);
        }
    }

    private String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required when Mux is enabled");
        return value;
    }
}
