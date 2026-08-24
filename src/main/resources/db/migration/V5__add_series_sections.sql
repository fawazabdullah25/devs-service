CREATE TABLE content_sections (
    id UUID PRIMARY KEY,
    content_id UUID NOT NULL REFERENCES learning_content(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    title_en VARCHAR(240) NOT NULL,
    title_ar VARCHAR(240),
    description_en VARCHAR(600),
    description_ar VARCHAR(600),
    CONSTRAINT uq_content_section_position UNIQUE (content_id, position),
    CONSTRAINT ck_content_section_position CHECK (position > 0)
);

CREATE INDEX idx_content_sections_content ON content_sections (content_id, position);

ALTER TABLE content_units
    ADD COLUMN section_id UUID REFERENCES content_sections(id) ON DELETE SET NULL;

CREATE INDEX idx_content_units_section ON content_units (section_id, position);
