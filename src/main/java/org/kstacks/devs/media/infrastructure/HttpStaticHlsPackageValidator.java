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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HttpStaticHlsPackageValidator implements StaticHlsPackageValidator {
    private static final int MAX_TEXT_FILE_BYTES = 256 * 1024;
    private static final Pattern STREAM_INFO = Pattern.compile("^#EXT-X-STREAM-INF:", Pattern.CASE_INSENSITIVE);
    private static final Pattern TARGET_DURATION = Pattern.compile("^#EXT-X-TARGETDURATION:(\\d+)$");
    private static final Pattern EXTINF = Pattern.compile("^#EXTINF:([^,]+),.*$");
    private static final Pattern WEB_VTT_HEADER = Pattern.compile("^WEBVTT(?:[ \\t].*)?$");
    private static final double MIN_DURATION_TOLERANCE_SECONDS = 2.0;
    private static final double RELATIVE_DURATION_TOLERANCE = 0.001;

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
    public ValidationResult validate(String manifestPath, List<MediaCaptionTrack> captions) {
        if (!properties.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Static HLS registration is disabled");
        }

        var master = get(manifestPath, "master playlist");
        var masterLines = master.lines().map(String::trim).toList();
        if (masterLines.isEmpty() || !masterLines.getFirst().equals("#EXTM3U") ||
            masterLines.stream().noneMatch(line -> STREAM_INFO.matcher(line).find())) {
            throw invalidRemote("The remote master playlist is not a valid HLS multivariant playlist");
        }

        var variants = variantPaths(manifestPath, master);
        if (variants.isEmpty()) {
            throw invalidRemote("The remote master playlist does not declare any renditions");
        }
        var durations = new ArrayList<Double>();
        for (var variant : variants) {
            var playlist = get(variant, "rendition playlist");
            durations.add(renditionDuration(variant, playlist));
        }
        var expected = durations.getFirst();
        var tolerance = Math.max(MIN_DURATION_TOLERANCE_SECONDS, expected * RELATIVE_DURATION_TOLERANCE);
        if (durations.stream().anyMatch(actual -> Math.abs(actual - expected) > tolerance)) {
            throw invalidRemote("HLS rendition durations differ by more than the allowed tolerance");
        }

        validateCaptions(captions);
        return new ValidationResult(Math.max(1, Math.round(expected)));
    }

    @Override
    public void validateCaptions(List<MediaCaptionTrack> captions) {
        for (var caption : captions) validateCaption(caption.getPath());
    }

    private void validateCaption(String path) {
        var vtt = get(path, "caption track");
        var firstLine = vtt.lines().findFirst().map(String::trim).orElse("");
        if (!WEB_VTT_HEADER.matcher(firstLine).matches()) {
            throw invalidRemote("A remote caption track does not start with WEBVTT");
        }
    }

    private List<String> variantPaths(String manifestPath, String master) {
        var lines = master.lines().map(String::trim).toList();
        var variants = new ArrayList<String>();
        for (var index = 0; index < lines.size(); index++) {
            if (!STREAM_INFO.matcher(lines.get(index)).find()) continue;
            String reference = null;
            for (var child = index + 1; child < lines.size(); child++) {
                var line = lines.get(child);
                if (line.isEmpty() || line.startsWith("#")) continue;
                reference = line;
                break;
            }
            if (reference == null) throw invalidRemote("The HLS master playlist has a stream without a rendition path");
            var variant = locations.childPath(manifestPath, reference);
            if (variants.contains(variant)) {
                throw invalidRemote("The HLS master playlist declares a rendition more than once");
            }
            variants.add(variant);
        }
        return List.copyOf(variants);
    }

    private double renditionDuration(String playlistPath, String playlist) {
        var lines = playlist.lines().map(String::trim).toList();
        if (lines.isEmpty() || !lines.getFirst().equals("#EXTM3U") ||
            lines.stream().noneMatch("#EXT-X-ENDLIST"::equals)) {
            throw invalidRemote("A remote rendition playlist is incomplete; VOD playlists must end with EXT-X-ENDLIST");
        }
        var hasTargetDuration = false;
        var total = 0.0;
        var segments = 0;
        for (var index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            var target = TARGET_DURATION.matcher(line);
            if (target.matches()) {
                hasTargetDuration = Integer.parseInt(target.group(1)) > 0;
                continue;
            }
            var extinf = EXTINF.matcher(line);
            if (!extinf.matches()) continue;
            final double duration;
            try {
                duration = Double.parseDouble(extinf.group(1).trim());
            } catch (NumberFormatException exception) {
                throw invalidRemote("A remote rendition contains an invalid EXTINF duration", exception);
            }
            if (!Double.isFinite(duration) || duration <= 0) {
                throw invalidRemote("A remote rendition contains a non-positive EXTINF duration");
            }
            var hasSegment = false;
            for (var child = index + 1; child < lines.size(); child++) {
                var reference = lines.get(child);
                if (reference.isEmpty() || reference.startsWith("#")) continue;
                locations.childPath(playlistPath, reference);
                hasSegment = true;
                index = child;
                break;
            }
            if (!hasSegment) throw invalidRemote("A remote rendition has EXTINF without a segment path");
            total += duration;
            segments++;
        }
        if (!hasTargetDuration || segments == 0 || !Double.isFinite(total) || total <= 0) {
            throw invalidRemote("A remote rendition playlist is incomplete");
        }
        return total;
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
