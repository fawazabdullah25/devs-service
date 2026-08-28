-- Devs now has one media pipeline: pre-built static HLS packages. Tighten the
-- old V1-V8 discriminator before removing provider-specific columns. Creating
-- the constraint intentionally fails if a deployment still contains a
-- non-static row; no data is silently discarded.
ALTER TABLE media_assets DROP CONSTRAINT ck_media_provider;
ALTER TABLE media_assets ADD CONSTRAINT ck_media_provider
    CHECK (provider = 'STATIC_HLS');

ALTER TABLE media_assets DROP CONSTRAINT ck_media_status;
ALTER TABLE media_assets ADD CONSTRAINT ck_media_status
    CHECK (status IN ('READY', 'DELETED'));

DROP INDEX IF EXISTS idx_media_provider_asset;
DROP INDEX IF EXISTS uq_media_assets_source_object_key;
ALTER TABLE media_assets DROP COLUMN provider_asset_id;
ALTER TABLE media_assets DROP COLUMN playback_id;
ALTER TABLE media_assets DROP COLUMN source_object_key;
ALTER TABLE media_assets DROP COLUMN source_filename;
ALTER TABLE media_assets DROP COLUMN source_content_type;
