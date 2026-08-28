-- Standalone WebVTT uploads are staged here before a media asset references
-- them. This is intentionally separate from the immutable HLS package.
CREATE TABLE caption_uploads (
    id UUID PRIMARY KEY,
    object_key VARCHAR(1024) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(160) NOT NULL,
    content_length BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    media_id UUID REFERENCES media_assets(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_caption_upload_status CHECK (status IN ('UPLOADING', 'COMPLETED', 'ATTACHED', 'DELETED')),
    CONSTRAINT ck_caption_upload_length CHECK (content_length > 0),
    CONSTRAINT ck_caption_upload_media CHECK (
        (status = 'ATTACHED' AND media_id IS NOT NULL)
        OR (status IN ('UPLOADING', 'COMPLETED', 'DELETED'))
    )
);

CREATE INDEX idx_caption_uploads_media ON caption_uploads (media_id);
CREATE INDEX idx_caption_uploads_cleanup ON caption_uploads (status, created_at);
