CREATE TABLE unit_attachments (
    id UUID PRIMARY KEY,
    unit_id UUID NOT NULL REFERENCES content_units(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL,
    object_key TEXT NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(160) NOT NULL,
    content_length BIGINT NOT NULL,
    title_en VARCHAR(240) NOT NULL,
    title_ar VARCHAR(240),
    position INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    purge_after TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_unit_attachment_status CHECK (status IN ('UPLOADING', 'READY', 'DELETED')),
    CONSTRAINT ck_unit_attachment_length CHECK (content_length > 0),
    CONSTRAINT ck_unit_attachment_position CHECK (position > 0),
    CONSTRAINT ck_unit_attachment_deletion CHECK (
        (status = 'DELETED' AND deleted_at IS NOT NULL AND purge_after IS NOT NULL)
        OR (status <> 'DELETED' AND deleted_at IS NULL AND purge_after IS NULL)
    )
);

CREATE INDEX idx_unit_attachments_active ON unit_attachments (unit_id, status, position);
CREATE INDEX idx_unit_attachments_purge ON unit_attachments (status, purge_after);
