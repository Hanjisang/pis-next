-- FC02B closes specimen, grossing, imaging and block facts without introducing workflow tasks.

ALTER TABLE pis_v2.specimen
    ADD COLUMN IF NOT EXISTS specimen_name VARCHAR(500),
    ADD COLUMN IF NOT EXISTS creation_source_code VARCHAR(32) NOT NULL DEFAULT 'REGISTRATION';

UPDATE pis_v2.specimen
SET specimen_name = COALESCE(NULLIF(collection_site, ''), specimen_kind_code)
WHERE specimen_name IS NULL OR specimen_name = '';

ALTER TABLE pis_v2.specimen
    ALTER COLUMN specimen_name SET NOT NULL,
    ALTER COLUMN collection_site DROP NOT NULL,
    ALTER COLUMN collection_method_code DROP NOT NULL;

ALTER TABLE pis_v2.specimen
    ADD CONSTRAINT ck_v2_specimen_creation_source
    CHECK (creation_source_code IN ('REGISTRATION', 'GROSSING_ADD', 'GROSSING_SPLIT', 'EXTERNAL_INPUT', 'FROZEN_REMAINDER'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_initial_grossing_active
    ON pis_v2.grossing (case_id)
    WHERE source_type = 'INITIAL' AND deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS pis_v2.grossing_correction_history (
    id UUID PRIMARY KEY,
    grossing_id UUID NOT NULL REFERENCES pis_v2.grossing(id),
    reason VARCHAR(2000) NOT NULL,
    prior_gross_description TEXT NOT NULL,
    corrected_gross_description TEXT NOT NULL,
    prior_instruction TEXT,
    corrected_instruction TEXT,
    corrected_at TIMESTAMPTZ NOT NULL,
    corrected_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_v2_grossing_correction_history
    ON pis_v2.grossing_correction_history (organization_reference, grossing_id, corrected_at);

CREATE TABLE IF NOT EXISTS pis_v2.grossing_specimen_correction_history (
    id UUID PRIMARY KEY,
    grossing_id UUID NOT NULL REFERENCES pis_v2.grossing(id),
    specimen_id UUID NOT NULL REFERENCES pis_v2.specimen(id),
    prior_description TEXT,
    corrected_description TEXT NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    corrected_at TIMESTAMPTZ NOT NULL,
    corrected_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_v2_grossing_specimen_correction
    ON pis_v2.grossing_specimen_correction_history
       (organization_reference, grossing_id, specimen_id, corrected_at);

ALTER TABLE pis_v2.block
    ALTER COLUMN grossing_id DROP NOT NULL,
    ALTER COLUMN specimen_id DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS sampling_description VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS quantity INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS note VARCHAR(2000);

ALTER TABLE pis_v2.block
    ADD CONSTRAINT ck_v2_block_quantity CHECK (quantity = 1),
    ADD CONSTRAINT ck_v2_block_local_lineage CHECK (
        external_source_flag OR (grossing_id IS NOT NULL AND specimen_id IS NOT NULL)
    );

CREATE TABLE IF NOT EXISTS pis_v2.block_code_history (
    id UUID PRIMARY KEY,
    block_id UUID NOT NULL REFERENCES pis_v2.block(id),
    old_block_code VARCHAR(64) NOT NULL,
    new_block_code VARCHAR(64) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    changed_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_v2_block_code_history
    ON pis_v2.block_code_history (organization_reference, block_id, changed_at);

CREATE TABLE IF NOT EXISTS pis_v2.block_verification_policy (
    id UUID PRIMARY KEY,
    organization_reference VARCHAR(128) NOT NULL,
    business_type_id UUID NOT NULL REFERENCES pis_v2.business_type(id),
    verification_required BOOLEAN NOT NULL,
    dual_check_required BOOLEAN NOT NULL,
    same_user_allowed BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_block_verification_policy UNIQUE (organization_reference, business_type_id),
    CONSTRAINT ck_v2_block_verification_policy_version CHECK (configuration_version > 0),
    CONSTRAINT ck_v2_block_verification_policy_dual CHECK (
        NOT dual_check_required OR verification_required
    )
);

CREATE TABLE IF NOT EXISTS pis_v2.block_verification (
    id UUID PRIMARY KEY,
    block_id UUID NOT NULL REFERENCES pis_v2.block(id),
    verification_result_code VARCHAR(32) NOT NULL,
    verified_code VARCHAR(64) NOT NULL,
    verified_specimen_id UUID REFERENCES pis_v2.specimen(id),
    verified_quantity INTEGER NOT NULL,
    reason VARCHAR(2000),
    verified_at TIMESTAMPTZ NOT NULL,
    verified_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_block_verification_result CHECK (verification_result_code IN ('PASSED', 'FAILED')),
    CONSTRAINT ck_v2_block_verification_quantity CHECK (verified_quantity = 1),
    CONSTRAINT ck_v2_block_verification_failure_reason CHECK (
        verification_result_code = 'PASSED' OR reason IS NOT NULL
    )
);

CREATE INDEX IF NOT EXISTS idx_v2_block_verification_latest
    ON pis_v2.block_verification (organization_reference, block_id, verified_at DESC);

INSERT INTO pis_v2.block_verification_policy
    (id, organization_reference, business_type_id, verification_required, dual_check_required,
     same_user_allowed, configuration_version, updated_at, updated_by_ref)
SELECT md5('PIS-V2-FC02B-BLOCK-VERIFY:' || bt.business_type_code)::uuid,
       'LOCAL_HOSPITAL', bt.id, FALSE, FALSE, TRUE, 1, CURRENT_TIMESTAMP, 'V36-MIGRATION'
FROM pis_v2.business_type bt
ON CONFLICT (organization_reference, business_type_id) DO NOTHING;

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b236', 'PIS_V2', 'V36-FC02B', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE SET version_code = 'V36-FC02B', recorded_at = CURRENT_TIMESTAMP;
