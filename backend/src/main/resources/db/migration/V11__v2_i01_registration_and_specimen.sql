-- V2-I01 is intentionally isolated from the legacy PIS schema.
CREATE SCHEMA IF NOT EXISTS pis_v2;

CREATE TABLE IF NOT EXISTS pis_v2.business_type (
    id UUID PRIMARY KEY,
    business_type_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    modality_code VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_business_type_code UNIQUE (business_type_code),
    CONSTRAINT ck_v2_business_type_version CHECK (configuration_version > 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.application_item_mapping (
    id UUID PRIMARY KEY,
    application_item_code VARCHAR(128) NOT NULL,
    business_type_id UUID NOT NULL REFERENCES pis_v2.business_type(id),
    default_specimen_kind_code VARCHAR(64),
    required BOOLEAN NOT NULL,
    sequence_no INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_application_item UNIQUE (application_item_code),
    CONSTRAINT ck_v2_application_item_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_v2_application_item_version CHECK (configuration_version > 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.pathology_number_rule (
    id UUID PRIMARY KEY,
    business_type_id UUID NOT NULL REFERENCES pis_v2.business_type(id),
    organization_reference VARCHAR(128) NOT NULL,
    number_kind_code VARCHAR(32) NOT NULL,
    prefix VARCHAR(32) NOT NULL,
    scope_code VARCHAR(32) NOT NULL,
    padding_width INTEGER NOT NULL,
    next_serial BIGINT NOT NULL,
    active BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_number_rule UNIQUE (organization_reference, business_type_id, number_kind_code),
    CONSTRAINT ck_v2_number_kind CHECK (number_kind_code IN ('CASE', 'SPECIMEN')),
    CONSTRAINT ck_v2_number_scope CHECK (scope_code IN ('ORGANIZATION', 'BUSINESS_TYPE')),
    CONSTRAINT ck_v2_number_width CHECK (padding_width BETWEEN 1 AND 12),
    CONSTRAINT ck_v2_number_serial CHECK (next_serial > 0),
    CONSTRAINT ck_v2_number_version CHECK (configuration_version > 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.pathology_case (
    id UUID PRIMARY KEY,
    case_no VARCHAR(128) NOT NULL,
    source_system_code VARCHAR(128) NOT NULL,
    external_application_id VARCHAR(256) NOT NULL,
    application_item_code VARCHAR(128) NOT NULL,
    business_type_id UUID NOT NULL REFERENCES pis_v2.business_type(id),
    lifecycle_state_code VARCHAR(32) NOT NULL,
    concurrency_version BIGINT NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_case_no UNIQUE (organization_reference, case_no),
    CONSTRAINT ck_v2_case_state CHECK (lifecycle_state_code IN
        ('P08-SM-002-ST-02', 'P08-SM-002-ST-03', 'P08-SM-002-ST-04')),
    CONSTRAINT ck_v2_case_version CHECK (concurrency_version >= 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.case_context_snapshot (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    patient_reference VARCHAR(256) NOT NULL,
    visit_reference VARCHAR(256),
    snapshot_version_no INTEGER NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    captured_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_case_snapshot UNIQUE (case_id, snapshot_version_no),
    CONSTRAINT ck_v2_case_snapshot_version CHECK (snapshot_version_no > 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.case_state_history (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    source_state_code VARCHAR(32) NOT NULL,
    target_state_code VARCHAR(32) NOT NULL,
    event_code VARCHAR(64) NOT NULL,
    expected_version BIGINT NOT NULL,
    resulting_version BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_case_history_version CHECK (expected_version >= 0 AND resulting_version >= expected_version)
);

CREATE TABLE IF NOT EXISTS pis_v2.specimen (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    specimen_no VARCHAR(128) NOT NULL,
    specimen_kind_code VARCHAR(64) NOT NULL,
    source_kind_code VARCHAR(64) NOT NULL,
    source_reference VARCHAR(256) NOT NULL,
    collection_site VARCHAR(500) NOT NULL,
    collection_method_code VARCHAR(64) NOT NULL,
    label_code VARCHAR(256),
    lifecycle_state_code VARCHAR(32) NOT NULL,
    concurrency_version BIGINT NOT NULL,
    received_at TIMESTAMPTZ,
    received_by_ref VARCHAR(128),
    isolation_reason VARCHAR(2000),
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_specimen_no UNIQUE (organization_reference, specimen_no),
    CONSTRAINT ck_v2_specimen_state CHECK (lifecycle_state_code IN
        ('P08-SM-003-ST-01', 'P08-SM-003-ST-02', 'P08-SM-003-ST-03', 'P08-SM-003-ST-04')),
    CONSTRAINT ck_v2_specimen_version CHECK (concurrency_version >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_specimen_label
    ON pis_v2.specimen (organization_reference, label_code)
    WHERE label_code IS NOT NULL;

CREATE TABLE IF NOT EXISTS pis_v2.specimen_state_history (
    id UUID PRIMARY KEY,
    specimen_id UUID NOT NULL REFERENCES pis_v2.specimen(id),
    source_state_code VARCHAR(32) NOT NULL,
    target_state_code VARCHAR(32) NOT NULL,
    event_code VARCHAR(64) NOT NULL,
    expected_version BIGINT NOT NULL,
    resulting_version BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_specimen_history_version CHECK (expected_version >= 0 AND resulting_version >= expected_version)
);

CREATE TABLE IF NOT EXISTS pis_v2.specimen_receipt_fact (
    id UUID PRIMARY KEY,
    specimen_id UUID NOT NULL REFERENCES pis_v2.specimen(id),
    verification_state_code VARCHAR(32) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    received_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis_v2.specimen_exception (
    id UUID PRIMARY KEY,
    specimen_id UUID NOT NULL REFERENCES pis_v2.specimen(id),
    exception_kind_code VARCHAR(64) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis_v2.idempotency_record (
    id UUID PRIMARY KEY,
    operation_code VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL,
    result_kind_code VARCHAR(32) NOT NULL,
    result_case_id UUID,
    result_specimen_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_idempotency UNIQUE (operation_code, idempotency_key),
    CONSTRAINT ck_v2_idempotency_result CHECK (
        (result_kind_code = 'CASE' AND result_case_id IS NOT NULL AND result_specimen_id IS NULL)
        OR (result_kind_code = 'SPECIMEN' AND result_case_id IS NULL AND result_specimen_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_v2_case_queue
    ON pis_v2.pathology_case (organization_reference, lifecycle_state_code, created_at);
CREATE INDEX IF NOT EXISTS idx_v2_specimen_queue
    ON pis_v2.specimen (organization_reference, lifecycle_state_code, created_at);
CREATE INDEX IF NOT EXISTS idx_v2_specimen_case
    ON pis_v2.specimen (case_id, created_at);

INSERT INTO pis_v2.business_type
    (id, business_type_code, display_name, modality_code, active, configuration_version, created_at, created_by_ref)
SELECT md5('PIS-V2-BUSINESS-TYPE:' || seed.business_type_code)::uuid,
       seed.business_type_code, seed.display_name, seed.modality_code, TRUE, 1, CURRENT_TIMESTAMP, 'V2-I01-SEED'
FROM (VALUES
    ('HISTOLOGY', '组织病理', 'TISSUE'),
    ('FROZEN', '冰冻病理', 'FROZEN'),
    ('CYTOLOGY_GYN', '妇科细胞学', 'CYTOLOGY'),
    ('CYTOLOGY_NON_GYN', '非妇科细胞学', 'CYTOLOGY'),
    ('CYTOLOGY_FNA', '细针穿刺细胞学', 'CYTOLOGY'),
    ('MOLECULAR', '分子病理', 'MOLECULAR'),
    ('REFERRAL', '会诊转诊', 'REFERRAL'),
    ('MULTIMODAL', '多模态诊断', 'MULTIMODAL')
) AS seed(business_type_code, display_name, modality_code)
ON CONFLICT (business_type_code) DO NOTHING;

INSERT INTO pis_v2.application_item_mapping
    (id, application_item_code, business_type_id, default_specimen_kind_code, required, sequence_no,
     active, configuration_version, created_at, created_by_ref)
SELECT md5('PIS-V2-APPLICATION-ITEM:' || seed.application_item_code)::uuid,
       seed.application_item_code, bt.id, seed.default_specimen_kind_code, TRUE, seed.sequence_no,
       TRUE, 1, CURRENT_TIMESTAMP, 'V2-I01-SEED'
FROM (VALUES
    ('SYNTH-HISTOLOGY', 'HISTOLOGY', 'TISSUE', 1),
    ('SYNTH-FROZEN', 'FROZEN', 'TISSUE', 2),
    ('SYNTH-CYTOLOGY', 'CYTOLOGY_NON_GYN', 'FLUID', 3),
    ('SYNTH-MOLECULAR', 'MOLECULAR', 'TISSUE', 4)
) AS seed(application_item_code, business_type_code, default_specimen_kind_code, sequence_no)
JOIN pis_v2.business_type bt ON bt.business_type_code = seed.business_type_code
ON CONFLICT (application_item_code) DO NOTHING;

INSERT INTO pis_v2.pathology_number_rule
    (id, business_type_id, organization_reference, number_kind_code, prefix, scope_code,
     padding_width, next_serial, active, configuration_version, created_at, updated_at, created_by_ref)
SELECT md5('PIS-V2-NUMBER-RULE:' || bt.business_type_code || ':CASE')::uuid,
       bt.id, 'LOCAL_HOSPITAL', 'CASE',
       CASE bt.business_type_code
           WHEN 'HISTOLOGY' THEN 'H-'
           WHEN 'FROZEN' THEN 'F-'
           WHEN 'CYTOLOGY_GYN' THEN 'G-'
           WHEN 'CYTOLOGY_NON_GYN' THEN 'C-'
           WHEN 'CYTOLOGY_FNA' THEN 'N-'
           WHEN 'MOLECULAR' THEN 'M-'
           WHEN 'REFERRAL' THEN 'R-'
           ELSE 'X-'
       END,
       'ORGANIZATION', 6, 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'V2-I01-SEED'
FROM pis_v2.business_type bt
ON CONFLICT (organization_reference, business_type_id, number_kind_code) DO NOTHING;

INSERT INTO pis_v2.pathology_number_rule
    (id, business_type_id, organization_reference, number_kind_code, prefix, scope_code,
     padding_width, next_serial, active, configuration_version, created_at, updated_at, created_by_ref)
SELECT md5('PIS-V2-NUMBER-RULE:' || bt.business_type_code || ':SPECIMEN')::uuid,
       bt.id, 'LOCAL_HOSPITAL', 'SPECIMEN',
       CASE bt.business_type_code
           WHEN 'HISTOLOGY' THEN 'HS-'
           WHEN 'FROZEN' THEN 'FS-'
           WHEN 'CYTOLOGY_GYN' THEN 'GS-'
           WHEN 'CYTOLOGY_NON_GYN' THEN 'CS-'
           WHEN 'CYTOLOGY_FNA' THEN 'NS-'
           WHEN 'MOLECULAR' THEN 'MS-'
           WHEN 'REFERRAL' THEN 'RS-'
           ELSE 'XS-'
       END,
       'ORGANIZATION', 7, 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'V2-I01-SEED'
FROM pis_v2.business_type bt
ON CONFLICT (organization_reference, business_type_id, number_kind_code) DO NOTHING;

CREATE TABLE IF NOT EXISTS pis_v2.schema_metadata (
    id UUID PRIMARY KEY,
    schema_code VARCHAR(64) NOT NULL UNIQUE,
    version_code VARCHAR(64) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL
);

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b201', 'PIS_V2', 'V2-I01', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE SET version_code = 'V2-I01', recorded_at = CURRENT_TIMESTAMP;
