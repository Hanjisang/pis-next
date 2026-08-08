-- V2-I01A removes the accidental workflow-state model from the V2 core objects.

ALTER TABLE pis_v2.pathology_case
    DROP CONSTRAINT IF EXISTS ck_v2_case_state;

ALTER TABLE pis_v2.pathology_case
    ADD COLUMN IF NOT EXISTS number_binding_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_by_ref VARCHAR(128),
    ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(2000);

UPDATE pis_v2.pathology_case
   SET lifecycle_state_code = CASE lifecycle_state_code
       WHEN 'P08-SM-002-ST-04' THEN 'CANCELLED'
       ELSE 'ACTIVE'
   END,
       number_binding_active = CASE lifecycle_state_code
       WHEN 'P08-SM-002-ST-04' THEN FALSE
       ELSE number_binding_active
   END;

ALTER TABLE pis_v2.pathology_case
    ADD CONSTRAINT ck_v2_case_state CHECK (lifecycle_state_code IN ('ACTIVE', 'CANCELLED'));

DROP TABLE IF EXISTS pis_v2.case_state_history;

ALTER TABLE pis_v2.specimen
    ADD COLUMN IF NOT EXISTS specimen_code VARCHAR(128),
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_by_ref VARCHAR(128),
    ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_by_ref VARCHAR(128) NOT NULL DEFAULT 'V2-I01A-MIGRATION';

UPDATE pis_v2.specimen
   SET specimen_code = specimen_no
 WHERE specimen_code IS NULL;

ALTER TABLE pis_v2.specimen
    ALTER COLUMN specimen_code SET NOT NULL,
    DROP CONSTRAINT IF EXISTS ck_v2_specimen_state;

DROP INDEX IF EXISTS pis_v2.uq_v2_specimen_label;
DROP TABLE IF EXISTS pis_v2.specimen_state_history;
DROP TABLE IF EXISTS pis_v2.specimen_receipt_fact;
DROP TABLE IF EXISTS pis_v2.specimen_exception;

ALTER TABLE pis_v2.specimen
    DROP COLUMN IF EXISTS lifecycle_state_code,
    DROP COLUMN IF EXISTS received_at,
    DROP COLUMN IF EXISTS received_by_ref,
    DROP COLUMN IF EXISTS isolation_reason;

CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_specimen_code_active
    ON pis_v2.specimen (case_id, specimen_code)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_specimen_label_active
    ON pis_v2.specimen (organization_reference, label_code)
    WHERE label_code IS NOT NULL AND deleted_at IS NULL;

COMMENT ON TABLE pis_v2.pathology_case IS
    'V2 Case source of truth; lifecycle is only ACTIVE or CANCELLED.';
COMMENT ON TABLE pis_v2.specimen IS
    'V2 Specimen source of truth; mutable facts with soft deletion, no workflow lifecycle.';

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b201', 'PIS_V2', 'V2-I01A', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE SET version_code = 'V2-I01A', recorded_at = CURRENT_TIMESTAMP;
