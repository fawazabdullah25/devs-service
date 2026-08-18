package org.kstacks.devs.content.application;

import org.junit.jupiter.api.Test;
import org.kstacks.devs.config.StaticHlsProperties;
import org.kstacks.devs.media.application.StaticHlsLocationResolver;
import org.kstacks.devs.media.domain.MediaAssetEntity;
import org.kstacks.devs.media.domain.MediaCaptionTrack;
import org.kstacks.devs.media.domain.MediaProvider;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentMapperTests {
    @Test
    void resolvesStaticHlsPathsInThePublicMediaContract() {
        var locations = new StaticHlsLocationResolver(new StaticHlsProperties(
            true, URI.create("https://video.example.test/"), "lessons", Duration.ofSeconds(2)
        ));
        var mapper = new ContentMapper(locations);
        var media = MediaAssetEntity.staticHls(
            "lessons/java/v1/master.m3u8",
            3_600,
            null,
            "v1",
            List.of(new MediaCaptionTrack(
                "ar", "العربية", "lessons/java/v1/captions/ar.vtt", false
            ))
        );

        var dto = mapper.toDto(media);

        assertThat(dto.provider()).isEqualTo(MediaProvider.STATIC_HLS);
        assertThat(dto.playbackId()).isNull();
        assertThat(dto.playbackUrl()).isEqualTo(
            URI.create("https://video.example.test/lessons/java/v1/master.m3u8")
        );
        assertThat(dto.captions()).singleElement().satisfies(caption -> {
            assertThat(caption.language()).isEqualTo("ar");
            assertThat(caption.url()).isEqualTo(
                URI.create("https://video.example.test/lessons/java/v1/captions/ar.vtt")
            );
        });
    }
}
