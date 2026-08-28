package org.kstacks.devs.media.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the append-only media migrations leave only the static-HLS schema. */
@SpringBootTest
@ActiveProfiles("test")
class MediaSchemaMigrationDatabaseTests {
    @Autowired private JdbcTemplate jdbc;

    @Test
    void removesLegacyMediaColumnsAndCreatesCaptionUploadOwnership() {
        var legacyColumns = jdbc.queryForList("""
            select column_name
            from information_schema.columns
            where table_name = 'media_assets'
            """, String.class);

        assertThat(legacyColumns).doesNotContain(
            "provider_asset_id", "playback_id", "source_object_key",
            "source_filename", "source_content_type"
        );
        assertThat(legacyColumns).containsExactlyInAnyOrder(
            "id", "version", "provider", "status", "duration_seconds",
            "checksum_sha256", "failure_message", "created_at", "updated_at",
            "playback_path", "encoding_version", "deleted_at", "purge_after",
            "retained_for_unit_id", "deleted_from_status"
        );

        var captionColumns = jdbc.queryForList("""
            select column_name
            from information_schema.columns
            where table_name = 'caption_uploads'
            """, String.class);
        assertThat(captionColumns).contains(
            "id", "object_key", "content_type", "content_length", "status", "media_id"
        );
    }
}
