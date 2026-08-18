ALTER TABLE media_assets ADD COLUMN playback_path TEXT;
ALTER TABLE media_assets ADD COLUMN encoding_version VARCHAR(120);

ALTER TABLE media_assets DROP CONSTRAINT ck_media_provider;
ALTER TABLE media_assets ADD CONSTRAINT ck_media_provider
    CHECK (provider IN ('MUX', 'STATIC_HLS', 'LOCAL'));
ALTER TABLE media_assets ADD CONSTRAINT ck_static_hls_ready
    CHECK (
        provider <> 'STATIC_HLS'
        OR (
            status = 'READY'
            AND playback_path IS NOT NULL
            AND duration_seconds > 0
            AND encoding_version IS NOT NULL
        )
    );

CREATE UNIQUE INDEX uq_media_assets_playback_path
    ON media_assets (playback_path);

CREATE TABLE media_caption_tracks (
    media_id UUID NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
    language VARCHAR(35) NOT NULL,
    label VARCHAR(120) NOT NULL,
    path TEXT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (media_id, language),
    CONSTRAINT uq_media_caption_path UNIQUE (media_id, path)
);
