-- FC03B closes the direct Cytology Specimen -> Slide path without introducing
-- parallel Cytology material entities or a technical workflow state machine.

ALTER TABLE pis_v2.specimen
    ADD COLUMN IF NOT EXISTS preparation_method_code VARCHAR(64);

ALTER TABLE pis_v2.slide
    ADD COLUMN IF NOT EXISTS stain_code VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_cytology_slide_rule_output_active
    ON pis_v2.slide (specimen_id, source_context_type, rule_code, occurrence_no)
    WHERE specimen_id IS NOT NULL AND source_context_type = 'CYTOLOGY' AND deleted_at IS NULL;

ALTER TABLE pis_v2.material_process_fact DROP CONSTRAINT IF EXISTS ck_v2_material_process_phase;
ALTER TABLE pis_v2.material_process_fact DROP CONSTRAINT IF EXISTS ck_v2_material_process_target;
ALTER TABLE pis_v2.material_process_fact ADD CONSTRAINT ck_v2_material_process_phase CHECK (
    phase_code IN ('DEHYDRATION', 'EMBEDDING', 'SECTIONING', 'PREPARATION', 'STAINING', 'COVERSLIPPING')
);
ALTER TABLE pis_v2.material_process_fact ADD CONSTRAINT ck_v2_material_process_target CHECK (
    (target_kind_code = 'BLOCK' AND block_id IS NOT NULL AND slide_id IS NULL
        AND phase_code IN ('DEHYDRATION', 'EMBEDDING'))
    OR
    (target_kind_code = 'SLIDE' AND slide_id IS NOT NULL AND block_id IS NULL
        AND phase_code IN ('DEHYDRATION', 'EMBEDDING', 'SECTIONING', 'PREPARATION', 'STAINING', 'COVERSLIPPING'))
);

ALTER TABLE pis_v2.material_rework DROP CONSTRAINT IF EXISTS ck_v2_material_rework_completion;
ALTER TABLE pis_v2.material_rework ADD CONSTRAINT ck_v2_material_rework_completion CHECK (
    (status_code = 'COMPLETED' AND completed_at IS NOT NULL AND completed_by_ref IS NOT NULL
        AND (rework_type_code NOT IN ('RECUT', 'REMAKE', 'REPREPARATION') OR replacement_slide_id IS NOT NULL))
    OR (status_code <> 'COMPLETED' AND replacement_slide_id IS NULL AND completed_at IS NULL
        AND completed_by_ref IS NULL)
);

CREATE INDEX IF NOT EXISTS ix_v2_cytology_specimen_preparation
    ON pis_v2.specimen (case_id, preparation_method_code)
    WHERE deleted_at IS NULL;

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b380', 'PIS_V2', 'FC03B-CYTOLOGY-PRODUCTION', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE
SET version_code = 'FC03B-CYTOLOGY-PRODUCTION', recorded_at = CURRENT_TIMESTAMP;
