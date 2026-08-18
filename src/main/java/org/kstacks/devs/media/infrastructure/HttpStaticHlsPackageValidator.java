package org.kstacks.devs.media.infrastructure;

import org.kstacks.devs.config.StaticHlsProperties;
import org.kstacks.devs.media.application.StaticHlsLocationResolver;
import org.kstacks.devs.media.application.StaticHlsPackageValidator;
import org.kstacks.devs.media.domain.MediaCaptionTrack;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class HttpStaticHlsPackageValidator implements StaticHlsPackageValidator {
    private static final int MAX_TEXT_FILE_BYTES = 256 * 1024;

    private final StaticHlsProperties properties;
    private final StaticHlsLocationResolver locations;
    private final HttpClient client;

    @Autowired
    public HttpStaticHlsPackageValidator(StaticHlsProperties properties, StaticHlsLocationResolver locations) {
        this(properties, locations, HttpClient.newBuilder()
            .connectTimeout(properties.validationTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build());
    }

    HttpStaticHlsPackageValidator(
        StaticHlsProperties properties,
        StaticHlsLocationResolver locations,
        HttpClient client
    ) {
        this.properties = properties;
        this.locations = locations;
        this.client = client;
    }

    @Override
    public void validate(String manifestPath, List<MediaCaptionTrack> captions) {
        if (!properties.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Static HLS registration is disabled");
        }

        var master = get(manifestPath, "master playlist");
        if (!master.startsWith("#EXTM3U") || !master.contains("#EXT-X-STREAM-INF:")) {
            throw invalidRemote("The remote master playlist is not a valid HLS multivariant playlist");
        }

        var variants = master.lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .map(reference -> locations.childPath(manifestPath, reference))
            .distinct()
            .toList();
        if (variants.isEmpty()) {
            throw invalidRemote("The remote master playlist does not declare any renditions");
        }
        for (var variant : variants) {
            var playlist = get(variant, "rendition playlist");
            if (!playlist.startsWith("#EXTM3U") || !playlist.contains("#EXT-X-TARGETDURATION:") ||
                !playlist.contains("#EXTINF:")) {
                throw invalidRemote("A remote rendition playlist is incomplete");
            }
        }

        for (var caption : captions) {
            var vtt = get(caption.getPath(), "caption track");
            if (!vtt.startsWith("WEBVTT")) {
                throw invalidRemote("A remote caption track does not start with WEBVTT");
            }
        }
    }

    private String get(String path, String description) {
        var request = HttpRequest.newBuilder(locations.resolve(path))
            .timeout(properties.validationTimeout())
            .header("Accept", "application/vnd.apple.mpegurl, application/x-mpegURL, text/vtt, text/plain")
            .GET()
            .build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                try (var ignored = response.body()) {
                    throw invalidRemote("The remote " + description + " returned HTTP " + response.statusCode());
                }
            }
            try (var body = response.body()) {
                var bytes = body.readNBytes(MAX_TEXT_FILE_BYTES + 1);
                if (bytes.length > MAX_TEXT_FILE_BYTES) {
                    throw invalidRemote("The remote " + description + " is unexpectedly large");
                }
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw invalidRemote("Static HLS validation was interrupted", exception);
        } catch (IOException exception) {
            throw invalidRemote("The remote " + description + " could not be validated", exception);
        }
    }

    private ResponseStatusException invalidRemote(String message) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }

    private ResponseStatusException invalidRemote(String message, Exception cause) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message, cause);
    }
}
