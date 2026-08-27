ALTER TABLE learning_content
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE learning_content
    ADD COLUMN purge_after TIMESTAMP WITH TIME ZONE;

ALTER TABLE learning_content
    ADD CONSTRAINT ck_learning_content_deletion CHECK (
        (deleted_at IS NULL AND purge_after IS NULL)
        OR (deleted_at IS NOT NULL AND purge_after IS NOT NULL)
    );

ALTER TABLE content_units
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE content_units
    ADD COLUMN purge_after TIMESTAMP WITH TIME ZONE;

ALTER TABLE content_units
    ADD CONSTRAINT ck_content_unit_deletion CHECK (
        (deleted_at IS NULL AND purge_after IS NULL)
        OR (deleted_at IS NOT NULL AND purge_after IS NOT NULL)
    );

ALTER TABLE instructors
    ADD COLUMN account_subject VARCHAR(255);

CREATE UNIQUE INDEX uq_instructors_account_subject
    ON instructors (account_subject);

CREATE INDEX idx_learning_content_purge
    ON learning_content (purge_after);

CREATE INDEX idx_content_units_purge
    ON content_units (purge_after);
