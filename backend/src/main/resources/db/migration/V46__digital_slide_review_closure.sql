ALTER TABLE pis_v2.digital_slide_screenshot
    ADD COLUMN IF NOT EXISTS media_type VARCHAR(128);
ALTER TABLE pis_v2.digital_slide_screenshot
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);
ALTER TABLE pis_v2.digital_slide_screenshot
    ADD COLUMN IF NOT EXISTS content_data BYTEA;

ALTER TABLE pis_v2.digital_slide_screenshot
    DROP CONSTRAINT IF EXISTS ck_v2_digital_screenshot_content;
ALTER TABLE pis_v2.digital_slide_screenshot
    ADD CONSTRAINT ck_v2_digital_screenshot_content CHECK (
        (content_data IS NULL AND content_hash IS NULL AND media_type IS NULL)
        OR (content_data IS NOT NULL AND content_hash IS NOT NULL AND media_type IS NOT NULL)
    );

CREATE TABLE IF NOT EXISTS pis_v2.digital_review_command_idempotency (
    id UUID PRIMARY KEY,
    operation_code VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    payload_digest VARCHAR(64) NOT NULL,
    result_entity_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_digital_review_idempotency UNIQUE
        (organization_reference, operation_code, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_v2_digital_screenshot_slide
    ON pis_v2.digital_slide_screenshot (digital_slide_id, created_at);
