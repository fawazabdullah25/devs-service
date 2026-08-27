-- A durable claim separates the database decision to purge from the
-- idempotent external-object cleanup. Rows left CLAIMED after a crash are
-- picked up again by the same retryable worker.
ALTER TABLE learning_content
    ADD COLUMN purge_state VARCHAR(16) NOT NULL DEFAULT 'NONE';

ALTER TABLE learning_content
    ADD CONSTRAINT ck_learning_content_purge_state
    CHECK (purge_state IN ('NONE', 'CLAIMED'));

ALTER TABLE content_units
    ADD COLUMN purge_state VARCHAR(16) NOT NULL DEFAULT 'NONE';

ALTER TABLE content_units
    ADD CONSTRAINT ck_content_unit_purge_state
    CHECK (purge_state IN ('NONE', 'CLAIMED'));

CREATE INDEX idx_learning_content_purge_state
    ON learning_content (purge_state, purge_after);

CREATE INDEX idx_content_units_purge_state
    ON content_units (purge_state, purge_after);
