-- V2-I06-B adds the minimum facts needed by non-routine business types.

CREATE TABLE IF NOT EXISTS pis_v2.molecular_result (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    specimen_id UUID REFERENCES pis_v2.specimen(id),
    result_code VARCHAR(128) NOT NULL,
    result_data JSONB NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    completed_by_ref VARCHAR(128) NOT NULL,
    concurrency_version BIGINT NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_molecular_result_status CHECK (status_code IN ('COMPLETED', 'VOIDED')),
    CONSTRAINT ck_v2_molecular_result_version CHECK (concurrency_version >= 0)
);

CREATE INDEX IF NOT EXISTS ix_v2_molecular_result_case
    ON pis_v2.molecular_result (organization_reference, case_id, completed_at);

CREATE TABLE IF NOT EXISTS pis_v2.molecular_result_idempotency (
    id UUID PRIMARY KEY,
    operation_code VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL,
    result_id UUID NOT NULL REFERENCES pis_v2.molecular_result(id),
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_molecular_result_idempotency UNIQUE (operation_code, idempotency_key)
);

CREATE TABLE IF NOT EXISTS pis_v2.send_out (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    external_reference VARCHAR(256) NOT NULL,
    destination_name VARCHAR(256) NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    requested_by_ref VARCHAR(128) NOT NULL,
    result_data JSONB,
    result_received_at TIMESTAMPTZ,
    result_received_by_ref VARCHAR(128),
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_send_out_status CHECK (status_code IN ('REQUESTED', 'RESULT_RECEIVED', 'CANCELLED')),
    CONSTRAINT uq_v2_send_out_reference UNIQUE (organization_reference, external_reference)
);

CREATE INDEX IF NOT EXISTS ix_v2_send_out_case
    ON pis_v2.send_out (organization_reference, case_id, requested_at);

CREATE TABLE IF NOT EXISTS pis_v2.send_out_idempotency (
    id UUID PRIMARY KEY,
    operation_code VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL,
    send_out_id UUID NOT NULL REFERENCES pis_v2.send_out(id),
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_send_out_idempotency UNIQUE (operation_code, idempotency_key)
);

INSERT INTO pis_v2.slide_rule
    (id, organization_reference, business_type_id, rule_code, source_context_type, trigger_code, slide_type,
     stain_code, copies, active, configuration_version, created_at, updated_at, created_by_ref)
SELECT '00000000-0000-0000-0000-00000000b181', 'LOCAL_HOSPITAL', bt.id, 'CYTOLOGY-DIRECT', 'CYTOLOGY', 'MANUAL',
       'CYTOLOGY', 'CYTOLOGY', 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'
FROM pis_v2.business_type bt
WHERE bt.business_type_code = 'CYTOLOGY_NON_GYN'
ON CONFLICT (organization_reference, business_type_id, rule_code) DO NOTHING;

INSERT INTO pis_v2.application_item_mapping
    (id, application_item_code, business_type_id, default_specimen_kind_code, required, sequence_no, active,
     configuration_version, created_at, created_by_ref)
SELECT '00000000-0000-0000-0000-00000000b182', 'SYNTH-CONSULTATION', bt.id, 'TISSUE', TRUE, 1, TRUE, 1,
       CURRENT_TIMESTAMP, 'system'
FROM pis_v2.business_type bt
WHERE bt.business_type_code = 'REFERRAL'
ON CONFLICT (application_item_code) DO NOTHING;

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b218', 'PIS_V2', 'V2-I06-B', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE SET version_code = 'V2-I06-B', recorded_at = CURRENT_TIMESTAMP;
