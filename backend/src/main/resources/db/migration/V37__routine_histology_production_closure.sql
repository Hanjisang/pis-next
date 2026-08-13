-- FC03A closes routine Block -> Slide production while keeping technical stages as optional facts.

ALTER TABLE pis_v2.material_process_fact DROP CONSTRAINT IF EXISTS uq_v2_material_process_fact;
ALTER TABLE pis_v2.material_process_fact DROP CONSTRAINT IF EXISTS ck_v2_material_process_phase;
ALTER TABLE pis_v2.material_process_fact DROP CONSTRAINT IF EXISTS ck_v2_material_process_time;
ALTER TABLE pis_v2.material_process_fact DROP CONSTRAINT IF EXISTS ck_v2_material_process_operator;
ALTER TABLE pis_v2.material_process_fact ALTER COLUMN slide_id DROP NOT NULL;
ALTER TABLE pis_v2.material_process_fact ADD COLUMN IF NOT EXISTS block_id UUID REFERENCES pis_v2.block(id);
ALTER TABLE pis_v2.material_process_fact ADD COLUMN IF NOT EXISTS target_kind_code VARCHAR(16);
ALTER TABLE pis_v2.material_process_fact ADD COLUMN IF NOT EXISTS equipment_id UUID REFERENCES pis_v2.equipment(id);
ALTER TABLE pis_v2.material_process_fact ADD COLUMN IF NOT EXISTS stain_code VARCHAR(64);
ALTER TABLE pis_v2.material_process_fact ADD COLUMN IF NOT EXISTS exception_resolved_at TIMESTAMPTZ;
ALTER TABLE pis_v2.material_process_fact ADD COLUMN IF NOT EXISTS exception_resolved_by_ref VARCHAR(128);
ALTER TABLE pis_v2.material_process_fact ADD COLUMN IF NOT EXISTS exception_resolution_note VARCHAR(2000);

UPDATE pis_v2.material_process_fact
SET phase_code = 'COVERSLIPPING'
WHERE phase_code = 'MOUNTING';

-- V36 recorded every phase against a slide. Re-home one representative legacy block-stage fact
-- per Block. Additional historical slide-level facts are retained so an upgrade never collapses
-- or deletes medical trace history when a Block already has multiple Slides.
WITH legacy_block_stage AS (
    SELECT fact.id,
           source_slide.block_id,
           ROW_NUMBER() OVER (
               PARTITION BY source_slide.block_id, fact.phase_code
               ORDER BY fact.updated_at, fact.id
           ) AS occurrence_rank
    FROM pis_v2.material_process_fact fact
    JOIN pis_v2.slide source_slide ON source_slide.id = fact.slide_id
    WHERE fact.phase_code IN ('DEHYDRATION', 'EMBEDDING')
      AND source_slide.block_id IS NOT NULL
)
UPDATE pis_v2.material_process_fact fact
SET block_id = legacy.block_id,
    slide_id = NULL,
    target_kind_code = 'BLOCK'
FROM legacy_block_stage legacy
WHERE fact.id = legacy.id
  AND legacy.occurrence_rank = 1;

UPDATE pis_v2.material_process_fact
SET target_kind_code = 'SLIDE'
WHERE target_kind_code IS NULL;

ALTER TABLE pis_v2.material_process_fact ALTER COLUMN target_kind_code SET NOT NULL;
ALTER TABLE pis_v2.material_process_fact ADD CONSTRAINT ck_v2_material_process_phase CHECK (
    phase_code IN ('DEHYDRATION', 'EMBEDDING', 'SECTIONING', 'STAINING', 'COVERSLIPPING')
);
ALTER TABLE pis_v2.material_process_fact ADD CONSTRAINT ck_v2_material_process_target CHECK (
    (target_kind_code = 'BLOCK' AND block_id IS NOT NULL AND slide_id IS NULL
        AND phase_code IN ('DEHYDRATION', 'EMBEDDING'))
    OR
    (target_kind_code = 'SLIDE' AND slide_id IS NOT NULL AND block_id IS NULL
        AND phase_code IN ('DEHYDRATION', 'EMBEDDING', 'SECTIONING', 'STAINING', 'COVERSLIPPING'))
);
ALTER TABLE pis_v2.material_process_fact ADD CONSTRAINT ck_v2_material_process_time CHECK (
    completed_at IS NULL OR started_at IS NOT NULL
);
ALTER TABLE pis_v2.material_process_fact ADD CONSTRAINT ck_v2_material_process_operator CHECK (
    (started_at IS NULL AND operator_ref IS NULL)
    OR (started_at IS NOT NULL AND operator_ref IS NOT NULL)
);
ALTER TABLE pis_v2.material_process_fact ADD CONSTRAINT ck_v2_material_exception_resolution CHECK (
    (exception_resolved_at IS NULL AND exception_resolved_by_ref IS NULL)
    OR (exception_resolved_at IS NOT NULL AND exception_resolved_by_ref IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_material_process_block_stage
    ON pis_v2.material_process_fact (block_id, phase_code) WHERE block_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_material_process_slide_stage
    ON pis_v2.material_process_fact (slide_id, phase_code) WHERE slide_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_v2_material_process_attention
    ON pis_v2.material_process_fact (organization_reference, exception_resolved_at, updated_at)
    WHERE exception_code IS NOT NULL;

CREATE TABLE IF NOT EXISTS pis_v2.material_process_fact_correction (
    id UUID PRIMARY KEY,
    process_fact_id UUID NOT NULL REFERENCES pis_v2.material_process_fact(id),
    prior_completed_at TIMESTAMPTZ,
    prior_operator_ref VARCHAR(128),
    prior_equipment_id UUID REFERENCES pis_v2.equipment(id),
    prior_note VARCHAR(2000),
    corrected_completed_at TIMESTAMPTZ,
    corrected_operator_ref VARCHAR(128),
    corrected_equipment_id UUID REFERENCES pis_v2.equipment(id),
    corrected_note VARCHAR(2000),
    reason VARCHAR(2000) NOT NULL,
    corrected_at TIMESTAMPTZ NOT NULL,
    corrected_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis_v2.slide_code_history (
    id UUID PRIMARY KEY,
    slide_id UUID NOT NULL REFERENCES pis_v2.slide(id),
    old_slide_code VARCHAR(128) NOT NULL,
    new_slide_code VARCHAR(128) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    changed_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_v2_slide_code_history_slide
    ON pis_v2.slide_code_history (slide_id, changed_at);

CREATE TABLE IF NOT EXISTS pis_v2.slide_completion_correction (
    id UUID PRIMARY KEY,
    slide_id UUID NOT NULL REFERENCES pis_v2.slide(id),
    prior_completed_at TIMESTAMPTZ NOT NULL,
    prior_completed_by_ref VARCHAR(128) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    corrected_at TIMESTAMPTZ NOT NULL,
    corrected_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_v2_slide_completion_correction_slide
    ON pis_v2.slide_completion_correction (slide_id, corrected_at);

ALTER TABLE pis_v2.material_rework DROP CONSTRAINT IF EXISTS ck_v2_material_rework_completion;
ALTER TABLE pis_v2.material_rework ADD CONSTRAINT ck_v2_material_rework_completion CHECK (
    (status_code = 'COMPLETED' AND completed_at IS NOT NULL AND completed_by_ref IS NOT NULL
        AND (rework_type_code NOT IN ('RECUT', 'REMAKE') OR replacement_slide_id IS NOT NULL))
    OR (status_code <> 'COMPLETED' AND replacement_slide_id IS NULL AND completed_at IS NULL
        AND completed_by_ref IS NULL)
);

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b370', 'PIS_V2', 'FC03A-ROUTINE-PRODUCTION', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE
SET version_code = 'FC03A-ROUTINE-PRODUCTION', recorded_at = CURRENT_TIMESTAMP;
