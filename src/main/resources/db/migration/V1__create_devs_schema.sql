CREATE TABLE instructors (
    id UUID PRIMARY KEY,
    name_en VARCHAR(160) NOT NULL,
    name_ar VARCHAR(160),
    bio_en TEXT NOT NULL DEFAULT '',
    bio_ar TEXT,
    initials VARCHAR(8) NOT NULL,
    avatar_url TEXT
);

CREATE TABLE learning_content (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    slug VARCHAR(180) NOT NULL UNIQUE,
    kind VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    visibility VARCHAR(24) NOT NULL,
    spoken_language VARCHAR(16) NOT NULL,
    title_en VARCHAR(240) NOT NULL,
    title_ar VARCHAR(240),
    summary_en VARCHAR(600) NOT NULL,
    summary_ar VARCHAR(600),
    description_en TEXT NOT NULL DEFAULT '',
    description_ar TEXT,
    level_slug VARCHAR(80) NOT NULL DEFAULT 'getting-started',
    cover_url TEXT,
    featured_rank INTEGER,
    published_at TIMESTAMP WITH TIME ZONE,
    views BIGINT NOT NULL DEFAULT 0,
    watched_minutes BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_learning_content_kind CHECK (kind IN ('COURSE', 'SERIES')),
    CONSTRAINT ck_learning_content_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_learning_content_visibility CHECK (visibility IN ('PUBLIC', 'AUTHENTICATED', 'STUDENT_ONLY')),
    CONSTRAINT ck_learning_content_language CHECK (spoken_language IN ('AR', 'EN', 'MIXED'))
);

CREATE INDEX idx_learning_content_publication ON learning_content (status, visibility, published_at DESC);
CREATE INDEX idx_learning_content_featured ON learning_content (featured_rank);

CREATE TABLE content_topics (
    content_id UUID NOT NULL REFERENCES learning_content(id) ON DELETE CASCADE,
    topic_slug VARCHAR(80) NOT NULL,
    PRIMARY KEY (content_id, topic_slug)
);

CREATE INDEX idx_content_topics_slug ON content_topics (topic_slug);

CREATE TABLE content_instructors (
    content_id UUID NOT NULL REFERENCES learning_content(id) ON DELETE CASCADE,
    instructor_id UUID NOT NULL REFERENCES instructors(id) ON DELETE RESTRICT,
    PRIMARY KEY (content_id, instructor_id)
);

CREATE TABLE media_assets (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    provider VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    provider_asset_id VARCHAR(255),
    playback_id VARCHAR(255),
    source_object_key TEXT,
    source_filename TEXT,
    source_content_type VARCHAR(160),
    duration_seconds BIGINT NOT NULL DEFAULT 0,
    checksum_sha256 VARCHAR(64),
    failure_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_media_provider CHECK (provider IN ('MUX', 'LOCAL')),
    CONSTRAINT ck_media_status CHECK (status IN ('UPLOADING', 'PROCESSING', 'READY', 'FAILED'))
);

CREATE UNIQUE INDEX idx_media_provider_asset ON media_assets (provider, provider_asset_id);

CREATE TABLE content_units (
    id UUID PRIMARY KEY,
    content_id UUID NOT NULL REFERENCES learning_content(id) ON DELETE CASCADE,
    media_id UUID REFERENCES media_assets(id) ON DELETE RESTRICT,
    slug VARCHAR(180) NOT NULL,
    position INTEGER NOT NULL,
    title_en VARCHAR(240) NOT NULL,
    title_ar VARCHAR(240),
    summary_en VARCHAR(600),
    summary_ar VARCHAR(600),
    CONSTRAINT uq_content_unit_slug UNIQUE (content_id, slug),
    CONSTRAINT uq_content_unit_position UNIQUE (content_id, position),
    CONSTRAINT ck_content_unit_position CHECK (position > 0)
);

CREATE INDEX idx_content_units_content ON content_units (content_id, position);
