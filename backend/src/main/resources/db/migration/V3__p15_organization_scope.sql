ALTER TABLE pis.pathology_case
    ADD COLUMN IF NOT EXISTS organization_reference VARCHAR(128) NOT NULL DEFAULT 'LOCAL_HOSPITAL';

ALTER TABLE pis.specimen
    ADD COLUMN IF NOT EXISTS organization_reference VARCHAR(128) NOT NULL DEFAULT 'LOCAL_HOSPITAL';

CREATE INDEX IF NOT EXISTS idx_p15_case_scope ON pis.pathology_case(organization_reference, created_at);
CREATE INDEX IF NOT EXISTS idx_p15_specimen_scope ON pis.specimen(organization_reference, specimen_lifecycle_state_code, created_at);
