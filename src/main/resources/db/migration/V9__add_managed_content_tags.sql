-- Tags replace the original fixed level/topic reference catalog. The legacy
-- columns/tables remain during the rolling deployment window and are kept as
-- compatibility data for older clients; new writes use these tables.
CREATE TABLE content_tags (
    id UUID PRIMARY KEY,
    tag_group VARCHAR(16) NOT NULL,
    slug VARCHAR(80) NOT NULL UNIQUE,
    name_en VARCHAR(160) NOT NULL,
    name_ar VARCHAR(160),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_content_tag_group CHECK (tag_group IN ('TOPIC', 'DIFFICULTY', 'GENERAL'))
);

CREATE TABLE content_tag_assignments (
    content_id UUID NOT NULL REFERENCES learning_content(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES content_tags(id) ON DELETE RESTRICT,
    PRIMARY KEY (content_id, tag_id)
);

CREATE INDEX idx_content_tag_assignments_tag ON content_tag_assignments (tag_id, content_id);
CREATE INDEX idx_content_tags_group ON content_tags (tag_group, name_en);

-- Stable IDs make this migration portable across PostgreSQL and the H2
-- PostgreSQL compatibility mode used by the service tests.
INSERT INTO content_tags (id, tag_group, slug, name_en, name_ar, created_at, updated_at) VALUES
    ('10000000-0000-0000-0000-000000000001', 'TOPIC', 'web', 'Web Development', 'تطوير الويب', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000002', 'TOPIC', 'backend', 'Backend', 'تطوير الخوادم', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000003', 'TOPIC', 'git', 'Git & Collaboration', 'جِت والعمل الجماعي', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000004', 'TOPIC', 'data', 'Data', 'البيانات', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000001', 'DIFFICULTY', 'getting-started', 'Getting started', 'تمهيدي', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000002', 'DIFFICULTY', 'builder', 'Builder', 'تطبيقي', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000003', 'DIFFICULTY', 'deep-dive', 'Deep dive', 'متقدم', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO content_tag_assignments (content_id, tag_id)
SELECT DISTINCT ct.content_id, t.id
FROM content_topics ct
JOIN content_tags t ON t.tag_group = 'TOPIC' AND t.slug = ct.topic_slug;

INSERT INTO content_tag_assignments (content_id, tag_id)
SELECT c.id, t.id
FROM learning_content c
JOIN content_tags t ON t.tag_group = 'DIFFICULTY' AND t.slug = c.level_slug;
