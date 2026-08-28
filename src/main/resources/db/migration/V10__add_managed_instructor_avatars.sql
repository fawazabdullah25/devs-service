CREATE TABLE instructor_avatars (
    id UUID PRIMARY KEY,
    instructor_id UUID NOT NULL REFERENCES instructors(id) ON DELETE CASCADE,
    object_key TEXT NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(160) NOT NULL,
    content_length BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    purge_after TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_instructor_avatar_status CHECK (status IN ('UPLOADING', 'READY', 'DELETED')),
    CONSTRAINT ck_instructor_avatar_length CHECK (content_length > 0),
    CONSTRAINT ck_instructor_avatar_lifecycle CHECK (
        (status = 'DELETED' AND deleted_at IS NOT NULL AND purge_after IS NOT NULL)
        OR (status IN ('UPLOADING', 'READY') AND deleted_at IS NULL AND purge_after IS NULL)
    )
);

CREATE INDEX idx_instructor_avatars_instructor_status
    ON instructor_avatars (instructor_id, status, created_at);
CREATE INDEX idx_instructor_avatars_purge
    ON instructor_avatars (status, purge_after);
