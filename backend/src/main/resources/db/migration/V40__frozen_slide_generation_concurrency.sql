-- SRS-FC03C: protect required Frozen Specimen -> Slide outputs under concurrent generation.
-- This is a context-specific constraint on the unified Slide table; it does not
-- create a FrozenSlide entity and it leaves slide.block_id nullable globally.
CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_frozen_slide_specimen_context_output
    ON pis_v2.slide (specimen_id, source_context_type, source_context_id, rule_code, occurrence_no)
    WHERE specimen_id IS NOT NULL
      AND source_context_type = 'FROZEN_ROUND'
      AND deleted_at IS NULL;

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b240', 'PIS_V2', 'FC03C-FROZEN-SLIDE-CONCURRENCY', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE
SET version_code = 'FC03C-FROZEN-SLIDE-CONCURRENCY', recorded_at = CURRENT_TIMESTAMP;
