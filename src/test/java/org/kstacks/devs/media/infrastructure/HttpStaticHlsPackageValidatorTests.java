package org.kstacks.devs.media.infrastructure;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kstacks.devs.config.StaticHlsProperties;
import org.kstacks.devs.media.application.StaticHlsLocationResolver;
import org.kstacks.devs.media.domain.MediaCaptionTrack;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpStaticHlsPackageValidatorTests {
    private HttpServer server;
    private HttpStaticHlsPackageValidator validator;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        var properties = new StaticHlsProperties(
            true,
            URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
            "pilots",
            Duration.ofSeconds(2)
        );
        validator = new HttpStaticHlsPackageValidator(properties, new StaticHlsLocationResolver(properties));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void validatesMasterRenditionsAndStrictWebVtt() {
        respond("/pilots/lesson/v1/master.m3u8", """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
            1080p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1280x720
            720p/index.m3u8
            """);
        respond("/pilots/lesson/v1/1080p/index.m3u8", variant());
        respond("/pilots/lesson/v1/720p/index.m3u8", variant());
        respond("/pilots/lesson/v1/captions/en.vtt", "WEBVTT\n\n00:00.000 --> 00:02.000\nHello\n");

        var result = validator.validate(
            "pilots/lesson/v1/master.m3u8",
            List.of(new MediaCaptionTrack("en", "English", "pilots/lesson/v1/captions/en.vtt", true))
        );
        assertThat(result.durationSeconds()).isEqualTo(6);
    }

    @Test
    void rejectsMissingRenditionsAndCaptionFilesWithBytesBeforeWebVtt() {
        respond("/pilots/broken/v1/master.m3u8", "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nmissing.m3u8\n");
        respond("/pilots/broken/v1/missing.m3u8", variant());
        respond("/pilots/broken/v1/captions/en.vtt", "\ufeffWEBVTT\n");

        assertThatThrownBy(() -> validator.validate(
            "pilots/broken/v1/master.m3u8",
            List.of(new MediaCaptionTrack("en", "English", "pilots/broken/v1/captions/en.vtt", false))
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(exception.getReason()).contains("WEBVTT");
        });
    }

    @Test
    void computesDurationFromEverySegmentAndRejectsMismatchedRenditions() {
        respond("/pilots/duration/v1/master.m3u8", """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=5000000
            1080p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2800000
            720p/index.m3u8
            """);
        respond("/pilots/duration/v1/1080p/index.m3u8", """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.25,
            first.m4s
            #EXTINF:5.75,
            second.m4s
            #EXT-X-ENDLIST
            """);
        respond("/pilots/duration/v1/720p/index.m3u8", """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            first.m4s
            #EXT-X-ENDLIST
            """);

        assertThatThrownBy(() -> validator.validate("pilots/duration/v1/master.m3u8", List.of()))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getReason()).contains("durations differ"));

        respond("/pilots/duration/v1/720p/index.m3u8", """
            #EXTM3U
            #EXT-X-TARGETDURATION:7
            #EXTINF:6.2,
            first.m4s
            #EXTINF:5.8,
            second.m4s
            #EXT-X-ENDLIST
            """);
        assertThat(validator.validate("pilots/duration/v1/master.m3u8", List.of()).durationSeconds())
            .isEqualTo(12);
    }

    @Test
    void rejectsAnIncompleteRenditionWithoutEndListOrSegmentPath() {
        respond("/pilots/incomplete/v1/master.m3u8", """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1
            index.m3u8
            """);
        respond("/pilots/incomplete/v1/index.m3u8", """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            """);

        assertThatThrownBy(() -> validator.validate("pilots/incomplete/v1/master.m3u8", List.of()))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getReason()).contains("incomplete"));
    }

    private String variant() {
        return "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\nseg_00001.m4s\n#EXT-X-ENDLIST\n";
    }

    private void respond(String path, String body) {
        try {
            server.removeContext(path);
        } catch (IllegalArgumentException ignored) {
            // The first response for a path has no existing context.
        }
        server.createContext(path, exchange -> write(exchange, body));
    }

    private void write(HttpExchange exchange, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (var response = exchange.getResponseBody()) {
            response.write(bytes);
        }
    }
}
