ALTER TABLE media_assets ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE media_assets ADD COLUMN purge_after TIMESTAMP WITH TIME ZONE;
ALTER TABLE media_assets ADD COLUMN retained_for_unit_id UUID REFERENCES content_units(id) ON DELETE RESTRICT;
ALTER TABLE media_assets ADD COLUMN deleted_from_status VARCHAR(16);

ALTER TABLE media_assets DROP CONSTRAINT ck_media_status;
ALTER TABLE media_assets ADD CONSTRAINT ck_media_status
    CHECK (status IN ('UPLOADING', 'PROCESSING', 'READY', 'FAILED', 'DELETED'));

ALTER TABLE media_assets DROP CONSTRAINT ck_static_hls_ready;
ALTER TABLE media_assets ADD CONSTRAINT ck_static_hls_ready
    CHECK (
        provider <> 'STATIC_HLS'
        OR (
            status IN ('READY', 'DELETED')
            AND playback_path IS NOT NULL
            AND duration_seconds > 0
            AND encoding_version IS NOT NULL
        )
    );

ALTER TABLE media_assets ADD CONSTRAINT ck_media_lifecycle
    CHECK (
        (status = 'DELETED' AND retained_for_unit_id IS NULL AND deleted_at IS NOT NULL AND purge_after IS NOT NULL)
        OR (status = 'READY' AND retained_for_unit_id IS NOT NULL AND deleted_at IS NOT NULL AND purge_after IS NOT NULL)
        OR (status IN ('UPLOADING', 'PROCESSING', 'READY', 'FAILED')
            AND retained_for_unit_id IS NULL AND deleted_at IS NULL AND purge_after IS NULL)
    );

CREATE INDEX idx_media_assets_purge ON media_assets (purge_after);
CREATE INDEX idx_media_assets_retained_unit ON media_assets (retained_for_unit_id);
CREATE UNIQUE INDEX uq_content_units_media_id ON content_units (media_id);

CREATE TABLE content_covers (
    id UUID PRIMARY KEY,
    content_id UUID NOT NULL REFERENCES learning_content(id) ON DELETE CASCADE,
    object_key TEXT NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(160) NOT NULL,
    content_length BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    purge_after TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_content_cover_status CHECK (status IN ('UPLOADING', 'READY', 'DELETED')),
    CONSTRAINT ck_content_cover_length CHECK (content_length > 0),
    CONSTRAINT ck_content_cover_lifecycle CHECK (
        (status = 'DELETED' AND deleted_at IS NOT NULL AND purge_after IS NOT NULL)
        OR (status IN ('UPLOADING', 'READY') AND deleted_at IS NULL AND purge_after IS NULL)
    )
);

CREATE INDEX idx_content_covers_content_status ON content_covers (content_id, status);
CREATE INDEX idx_content_covers_purge ON content_covers (purge_after);
