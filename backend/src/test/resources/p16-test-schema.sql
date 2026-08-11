CREATE SCHEMA IF NOT EXISTS pis;
CREATE TABLE IF NOT EXISTS pis.pathology_case (
    id UUID PRIMARY KEY, case_no VARCHAR(64) NOT NULL UNIQUE, organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis.specimen (
    id UUID PRIMARY KEY, case_id UUID NOT NULL REFERENCES pis.pathology_case(id), specimen_no VARCHAR(64) NOT NULL UNIQUE,
    specimen_lifecycle_state_code VARCHAR(32) NOT NULL, organization_reference VARCHAR(128) NOT NULL,
    collection_site_text VARCHAR(500) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis.grossing_batch (
    id UUID PRIMARY KEY, batch_no VARCHAR(64) NOT NULL, organization_reference VARCHAR(128) NOT NULL,
    task_state_code VARCHAR(32) NOT NULL, batch_state_code VARCHAR(64) NOT NULL, assigned_actor_ref VARCHAR(128),
    actual_actor_ref VARCHAR(128), started_at TIMESTAMP WITH TIME ZONE, paused_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE, handed_off_at TIMESTAMP WITH TIME ZONE, record_version_no INTEGER NOT NULL,
    concurrency_version BIGINT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (organization_reference, batch_no)
);
CREATE TABLE IF NOT EXISTS pis.grossing_batch_specimen (
    id UUID PRIMARY KEY, batch_id UUID NOT NULL REFERENCES pis.grossing_batch(id), specimen_id UUID NOT NULL REFERENCES pis.specimen(id),
    case_id UUID NOT NULL REFERENCES pis.pathology_case(id), identity_verified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL, UNIQUE (batch_id, specimen_id)
);
CREATE TABLE IF NOT EXISTS pis.grossing_record (
    id UUID PRIMARY KEY, batch_id UUID NOT NULL REFERENCES pis.grossing_batch(id), specimen_id UUID NOT NULL REFERENCES pis.specimen(id),
    record_version_no INTEGER NOT NULL, identity_verified BOOLEAN NOT NULL, patient_identity_verified BOOLEAN NOT NULL,
    gross_appearance_text VARCHAR(2000) NOT NULL, quantity_value DECIMAL(12,3) NOT NULL, quantity_unit_code VARCHAR(32) NOT NULL,
    gross_description_text VARCHAR(5000) NOT NULL, correction_reason VARCHAR(1000), review_actor_ref VARCHAR(128),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL, UNIQUE(batch_id, specimen_id, record_version_no)
);
CREATE TABLE IF NOT EXISTS pis.tissue_block (
    id UUID PRIMARY KEY, case_id UUID NOT NULL REFERENCES pis.pathology_case(id), specimen_id UUID NOT NULL REFERENCES pis.specimen(id),
    batch_id UUID NOT NULL REFERENCES pis.grossing_batch(id), block_no VARCHAR(64) NOT NULL, block_kind_code VARCHAR(32) NOT NULL,
    source_material_kind_code VARCHAR(32) NOT NULL, block_lifecycle_state_code VARCHAR(32) NOT NULL,
    physical_formed_at TIMESTAMP WITH TIME ZONE, tissue_box_identity_id UUID, record_version_no INTEGER NOT NULL,
    concurrency_version BIGINT NOT NULL, organization_reference VARCHAR(128) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL, UNIQUE(organization_reference, block_no)
);
CREATE TABLE IF NOT EXISTS pis.tissue_sample (
    id UUID PRIMARY KEY, sample_no VARCHAR(64) NOT NULL, batch_id UUID NOT NULL REFERENCES pis.grossing_batch(id),
    grossing_record_id UUID NOT NULL REFERENCES pis.grossing_record(id), specimen_id UUID NOT NULL REFERENCES pis.specimen(id),
    source_site_text VARCHAR(500) NOT NULL, sample_description_text VARCHAR(2000) NOT NULL, quantity_value DECIMAL(12,3) NOT NULL,
    quantity_unit_code VARCHAR(32) NOT NULL, sample_state_code VARCHAR(32) NOT NULL, concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL, UNIQUE(batch_id, sample_no)
);
CREATE TABLE IF NOT EXISTS pis.tissue_block_sample (
    id UUID PRIMARY KEY, block_id UUID NOT NULL REFERENCES pis.tissue_block(id), sample_id UUID NOT NULL REFERENCES pis.tissue_sample(id),
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL, assigned_by_ref VARCHAR(128) NOT NULL, UNIQUE(sample_id)
);
CREATE TABLE IF NOT EXISTS pis.tissue_box_identity (
    id UUID PRIMARY KEY, block_id UUID NOT NULL UNIQUE REFERENCES pis.tissue_block(id), tissue_box_no VARCHAR(64) NOT NULL,
    box_state_code VARCHAR(32) NOT NULL, organization_reference VARCHAR(128) NOT NULL, assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL, UNIQUE(organization_reference, tissue_box_no)
);
CREATE TABLE IF NOT EXISTS pis.label_identity (
    id UUID PRIMARY KEY, target_kind_code VARCHAR(32) NOT NULL, target_object_id UUID NOT NULL, target_version BIGINT NOT NULL,
    label_version_no INTEGER NOT NULL, label_state_code VARCHAR(32) NOT NULL, template_logic_version VARCHAR(64) NOT NULL,
    display_snapshot_text VARCHAR(5000) NOT NULL, barcode_payload VARCHAR(500) NOT NULL, organization_reference VARCHAR(128) NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL, generated_by_ref VARCHAR(128) NOT NULL,
    UNIQUE(target_kind_code, target_object_id, label_version_no)
);
CREATE TABLE IF NOT EXISTS pis.label_print_request (
    id UUID PRIMARY KEY, label_id UUID NOT NULL REFERENCES pis.label_identity(id), idempotency_key VARCHAR(128) NOT NULL,
    request_kind_code VARCHAR(32) NOT NULL, original_label_id UUID REFERENCES pis.label_identity(id), reason VARCHAR(1000),
    request_state_code VARCHAR(32) NOT NULL, requested_at TIMESTAMP WITH TIME ZONE NOT NULL, requested_by_ref VARCHAR(128) NOT NULL,
    UNIQUE(label_id, idempotency_key)
);
CREATE TABLE IF NOT EXISTS pis.label_print_attempt (
    id UUID PRIMARY KEY, print_request_id UUID NOT NULL REFERENCES pis.label_print_request(id), attempt_no INTEGER NOT NULL,
    attempt_state_code VARCHAR(32) NOT NULL, adapter_outcome_code VARCHAR(64) NOT NULL, result_note VARCHAR(1000),
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL, attempted_by_ref VARCHAR(128) NOT NULL, UNIQUE(print_request_id, attempt_no)
);
CREATE TABLE IF NOT EXISTS pis.p16_idempotency_key (
    id UUID PRIMARY KEY, operation_code VARCHAR(64) NOT NULL, idempotency_key VARCHAR(128) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL, result_object_id UUID, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL, UNIQUE(operation_code, idempotency_key)
);
CREATE TABLE IF NOT EXISTS pis.state_transition_history (
    id UUID PRIMARY KEY, object_id UUID NOT NULL, object_kind_code VARCHAR(64) NOT NULL, state_machine_code VARCHAR(64) NOT NULL,
    source_state_code VARCHAR(64) NOT NULL, target_state_code VARCHAR(64) NOT NULL, transition_event_code VARCHAR(128) NOT NULL,
    expected_version BIGINT, resulting_version BIGINT, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_by_ref VARCHAR(128) NOT NULL, reason VARCHAR(500) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis.operation_responsibility (
    id UUID PRIMARY KEY, object_id UUID NOT NULL, object_kind_code VARCHAR(64) NOT NULL, responsibility_type_code VARCHAR(64) NOT NULL,
    responsible_actor_ref VARCHAR(128) NOT NULL, actual_actor_ref VARCHAR(128) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis.audit_event (
    id UUID PRIMARY KEY, operation_code VARCHAR(64) NOT NULL, permission_code VARCHAR(64) NOT NULL, actor_ref VARCHAR(128) NOT NULL,
    subject_type_code VARCHAR(32) NOT NULL, target_object_id UUID, target_object_kind_code VARCHAR(64),
    authorization_outcome VARCHAR(32) NOT NULL, processing_outcome VARCHAR(64) NOT NULL, correlation_id VARCHAR(128) NOT NULL,
    reason VARCHAR(500), category_code VARCHAR(32), changes_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE IF NOT EXISTS pis.outbox_event (
    id UUID PRIMARY KEY, event_identity VARCHAR(128) NOT NULL UNIQUE, event_type_code VARCHAR(128) NOT NULL,
    subject_id UUID NOT NULL, subject_kind_code VARCHAR(64) NOT NULL, aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(128) NOT NULL, payload_digest VARCHAR(128) NOT NULL, publish_state_code VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL
);
