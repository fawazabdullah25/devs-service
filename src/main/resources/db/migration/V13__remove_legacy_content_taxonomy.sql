-- V9 copied the fixed topic/level values into content_tags and their
-- authoritative content_tag_assignments relation. Once the tag-based API is
-- deployed, the original columns and collection table are dead storage.
--
-- This migration intentionally follows the HLS migrations V11/V12. Do not
-- deploy it before the application version that reads `content_tags` is live.
DROP INDEX IF EXISTS idx_content_topics_slug;
DROP TABLE IF EXISTS content_topics;
ALTER TABLE learning_content DROP COLUMN IF EXISTS level_slug;
ALTER TABLE instructors DROP COLUMN IF EXISTS avatar_url;
ALTER TABLE instructors ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE instructors ADD COLUMN IF NOT EXISTS purge_after TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_instructors_purge ON instructors (purge_after);
