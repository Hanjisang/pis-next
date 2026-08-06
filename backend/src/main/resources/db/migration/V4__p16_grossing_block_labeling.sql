CREATE TABLE IF NOT EXISTS pis.grossing_batch (
    id UUID PRIMARY KEY,
    batch_no VARCHAR(64) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    task_state_code VARCHAR(32) NOT NULL,
    batch_state_code VARCHAR(64) NOT NULL,
    assigned_actor_ref VARCHAR(128),
    actual_actor_ref VARCHAR(128),
    started_at TIMESTAMPTZ,
    paused_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    handed_off_at TIMESTAMPTZ,
    record_version_no INTEGER NOT NULL,
    concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (organization_reference, batch_no)
);

CREATE TABLE IF NOT EXISTS pis.grossing_batch_specimen (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES pis.grossing_batch(id),
    specimen_id UUID NOT NULL REFERENCES pis.specimen(id),
    case_id UUID NOT NULL REFERENCES pis.pathology_case(id),
    identity_verified_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (batch_id, specimen_id)
);

CREATE TABLE IF NOT EXISTS pis.grossing_record (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES pis.grossing_batch(id),
    specimen_id UUID NOT NULL REFERENCES pis.specimen(id),
    record_version_no INTEGER NOT NULL,
    identity_verified BOOLEAN NOT NULL,
    patient_identity_verified BOOLEAN NOT NULL,
    gross_appearance_text VARCHAR(2000) NOT NULL,
    quantity_value DECIMAL(12,3) NOT NULL CHECK (quantity_value > 0),
    quantity_unit_code VARCHAR(32) NOT NULL,
    gross_description_text VARCHAR(5000) NOT NULL,
    correction_reason VARCHAR(1000),
    review_actor_ref VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (batch_id, specimen_id, record_version_no)
);

CREATE TABLE IF NOT EXISTS pis.tissue_block (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis.pathology_case(id),
    specimen_id UUID NOT NULL REFERENCES pis.specimen(id),
    batch_id UUID NOT NULL REFERENCES pis.grossing_batch(id),
    block_no VARCHAR(64) NOT NULL,
    block_kind_code VARCHAR(32) NOT NULL,
    source_material_kind_code VARCHAR(32) NOT NULL,
    block_lifecycle_state_code VARCHAR(32) NOT NULL,
    physical_formed_at TIMESTAMPTZ,
    tissue_box_identity_id UUID,
    record_version_no INTEGER NOT NULL,
    concurrency_version BIGINT NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (organization_reference, block_no)
);

CREATE TABLE IF NOT EXISTS pis.tissue_sample (
    id UUID PRIMARY KEY,
    sample_no VARCHAR(64) NOT NULL,
    batch_id UUID NOT NULL REFERENCES pis.grossing_batch(id),
    grossing_record_id UUID NOT NULL REFERENCES pis.grossing_record(id),
    specimen_id UUID NOT NULL REFERENCES pis.specimen(id),
    source_site_text VARCHAR(500) NOT NULL,
    sample_description_text VARCHAR(2000) NOT NULL,
    quantity_value DECIMAL(12,3) NOT NULL CHECK (quantity_value > 0),
    quantity_unit_code VARCHAR(32) NOT NULL,
    sample_state_code VARCHAR(32) NOT NULL,
    concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (batch_id, sample_no)
);

CREATE TABLE IF NOT EXISTS pis.tissue_block_sample (
    id UUID PRIMARY KEY,
    block_id UUID NOT NULL REFERENCES pis.tissue_block(id),
    sample_id UUID NOT NULL REFERENCES pis.tissue_sample(id),
    assigned_at TIMESTAMPTZ NOT NULL,
    assigned_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (sample_id)
);

CREATE TABLE IF NOT EXISTS pis.tissue_box_identity (
    id UUID PRIMARY KEY,
    block_id UUID NOT NULL UNIQUE REFERENCES pis.tissue_block(id),
    tissue_box_no VARCHAR(64) NOT NULL,
    box_state_code VARCHAR(32) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (organization_reference, tissue_box_no)
);

CREATE TABLE IF NOT EXISTS pis.label_identity (
    id UUID PRIMARY KEY,
    target_kind_code VARCHAR(32) NOT NULL,
    target_object_id UUID NOT NULL,
    target_version BIGINT NOT NULL,
    label_version_no INTEGER NOT NULL,
    label_state_code VARCHAR(32) NOT NULL,
    template_logic_version VARCHAR(64) NOT NULL,
    display_snapshot_text VARCHAR(5000) NOT NULL,
    barcode_payload VARCHAR(500) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    generated_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (target_kind_code, target_object_id, label_version_no)
);

CREATE TABLE IF NOT EXISTS pis.label_print_request (
    id UUID PRIMARY KEY,
    label_id UUID NOT NULL REFERENCES pis.label_identity(id),
    idempotency_key VARCHAR(128) NOT NULL,
    request_kind_code VARCHAR(32) NOT NULL,
    original_label_id UUID REFERENCES pis.label_identity(id),
    reason VARCHAR(1000),
    request_state_code VARCHAR(32) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    requested_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (label_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS pis.label_print_attempt (
    id UUID PRIMARY KEY,
    print_request_id UUID NOT NULL REFERENCES pis.label_print_request(id),
    attempt_no INTEGER NOT NULL,
    attempt_state_code VARCHAR(32) NOT NULL,
    adapter_outcome_code VARCHAR(64) NOT NULL,
    result_note VARCHAR(1000),
    attempted_at TIMESTAMPTZ NOT NULL,
    attempted_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (print_request_id, attempt_no)
);

CREATE TABLE IF NOT EXISTS pis.p16_idempotency_key (
    id UUID PRIMARY KEY,
    operation_code VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL,
    result_object_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (operation_code, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_p16_batch_queue ON pis.grossing_batch(organization_reference, batch_state_code, created_at);
CREATE INDEX IF NOT EXISTS idx_p16_batch_specimen ON pis.grossing_batch_specimen(specimen_id, batch_id);
CREATE INDEX IF NOT EXISTS idx_p16_sample_source ON pis.tissue_sample(specimen_id, batch_id);
CREATE INDEX IF NOT EXISTS idx_p16_label_target ON pis.label_identity(target_object_id, generated_at);

INSERT INTO pis.foundation_schema_metadata (metadata_id, schema_code, foundation_version)
VALUES ('00000000-0000-0000-0000-000000000016', 'PIS_NEXT', 'P16')
ON CONFLICT (schema_code) DO UPDATE SET foundation_version = 'P16';
