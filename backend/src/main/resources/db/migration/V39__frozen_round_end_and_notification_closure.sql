-- SRS-FC03C: close Frozen round cancellation, per-round timing, selected
-- Frozen -> Routine material conversion and the existing integration retry path.
-- Frozen and Routine remain two independent Case records.

ALTER TABLE pis_v2.frozen_round
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_by_ref VARCHAR(128),
    ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(2000);

ALTER TABLE pis_v2.frozen_round
    DROP CONSTRAINT IF EXISTS ck_v2_frozen_round_status;

ALTER TABLE pis_v2.frozen_round
    ADD CONSTRAINT ck_v2_frozen_round_status CHECK
        (status_code IN ('OPEN', 'PRODUCTION_COMPLETE', 'SIGNED', 'ENDED', 'CANCELLED'));

ALTER TABLE pis_v2.frozen_round
    DROP CONSTRAINT IF EXISTS ck_v2_frozen_round_cancellation;

ALTER TABLE pis_v2.frozen_round
    ADD CONSTRAINT ck_v2_frozen_round_cancellation CHECK (
        (status_code = 'CANCELLED' AND cancelled_at IS NOT NULL
            AND cancelled_by_ref IS NOT NULL AND cancellation_reason IS NOT NULL)
        OR (status_code <> 'CANCELLED' AND cancelled_at IS NULL
            AND cancelled_by_ref IS NULL AND cancellation_reason IS NULL)
    );

-- The relation is deliberately specific to Frozen End. It is not a generic
-- CaseRelation or SpecimenRelation and preserves both material identities.
CREATE TABLE IF NOT EXISTS pis_v2.frozen_end_specimen (
    id UUID PRIMARY KEY,
    frozen_end_id UUID NOT NULL REFERENCES pis_v2.frozen_end(id),
    frozen_specimen_id UUID NOT NULL REFERENCES pis_v2.specimen(id),
    routine_specimen_id UUID NOT NULL REFERENCES pis_v2.specimen(id),
    frozen_round_id UUID NOT NULL REFERENCES pis_v2.frozen_round(id),
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_frozen_end_specimen UNIQUE (frozen_end_id, frozen_specimen_id),
    CONSTRAINT uq_v2_frozen_end_routine_specimen UNIQUE (routine_specimen_id),
    CONSTRAINT ck_v2_frozen_end_specimen_distinct
        CHECK (frozen_specimen_id <> routine_specimen_id)
);

CREATE INDEX IF NOT EXISTS ix_v2_frozen_end_specimen_round
    ON pis_v2.frozen_end_specimen (frozen_round_id, created_at);

-- One Frozen Case can have at most one generated Routine Case, including
-- after the Routine Case is cancelled. The historical relation is retained.
CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_case_frozen_source
    ON pis_v2.pathology_case (frozen_source_case_id)
    WHERE frozen_source_case_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_v2_integration_frozen_notification
    ON pis_v2.integration_message_log (hospital_profile_code, business_key, created_at)
    WHERE capability_code = 'CLINICAL_RESULT_NOTIFICATION';

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b239', 'PIS_V2', 'FC03C-FROZEN-CLOSURE', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE
SET version_code = 'FC03C-FROZEN-CLOSURE', recorded_at = CURRENT_TIMESTAMP;
