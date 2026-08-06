CREATE SCHEMA IF NOT EXISTS pis;

CREATE TABLE IF NOT EXISTS pis.patient_context_reference (
    id UUID PRIMARY KEY,
    source_system_code VARCHAR(64) NOT NULL,
    external_patient_id VARCHAR(128) NOT NULL,
    patient_namespace_code VARCHAR(64) NOT NULL,
    reference_state_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (source_system_code, external_patient_id, patient_namespace_code)
);

CREATE TABLE IF NOT EXISTS pis.visit_context_reference (
    id UUID PRIMARY KEY,
    source_system_code VARCHAR(64) NOT NULL,
    external_visit_id VARCHAR(128) NOT NULL,
    visit_namespace_code VARCHAR(64) NOT NULL,
    patient_reference_id UUID NOT NULL REFERENCES pis.patient_context_reference(id),
    reference_state_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (source_system_code, external_visit_id, visit_namespace_code)
);

CREATE TABLE IF NOT EXISTS pis.patient_visit_snapshot (
    id UUID PRIMARY KEY,
    patient_reference_id UUID NOT NULL REFERENCES pis.patient_context_reference(id),
    visit_reference_id UUID REFERENCES pis.visit_context_reference(id),
    snapshot_version_no INTEGER NOT NULL,
    snapshot_digest VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (patient_reference_id, visit_reference_id, snapshot_version_no)
);

CREATE TABLE IF NOT EXISTS pis.pathology_request (
    id UUID PRIMARY KEY,
    source_system_code VARCHAR(64) NOT NULL,
    application_no VARCHAR(64) NOT NULL UNIQUE,
    application_lifecycle_state_code VARCHAR(32) NOT NULL,
    patient_reference_id UUID REFERENCES pis.patient_context_reference(id),
    visit_reference_id UUID REFERENCES pis.visit_context_reference(id),
    request_received_at TIMESTAMPTZ NOT NULL,
    request_channel_code VARCHAR(32) NOT NULL,
    request_content_text VARCHAR(2000),
    pathology_modality_code VARCHAR(32) NOT NULL,
    record_version_no INTEGER NOT NULL,
    concurrency_version BIGINT NOT NULL,
    source_message_identity VARCHAR(128),
    source_message_digest VARCHAR(128) NOT NULL,
    manual_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (source_system_code, source_message_identity)
);

CREATE TABLE IF NOT EXISTS pis.external_request_reference (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES pis.pathology_request(id),
    source_system_code VARCHAR(64) NOT NULL,
    external_request_id VARCHAR(128) NOT NULL,
    external_request_kind_code VARCHAR(32) NOT NULL,
    idempotency_digest VARCHAR(128) NOT NULL,
    first_received_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (source_system_code, external_request_id)
);

CREATE TABLE IF NOT EXISTS pis.inbound_raw_message (
    id UUID PRIMARY KEY,
    source_system_code VARCHAR(64) NOT NULL,
    source_message_identity VARCHAR(128) NOT NULL,
    source_message_version VARCHAR(64) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL,
    raw_payload_reference VARCHAR(256) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    processing_state_code VARCHAR(32) NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (source_system_code, source_message_identity)
);

CREATE TABLE IF NOT EXISTS pis.inbox_consumption (
    id UUID PRIMARY KEY,
    source_system_code VARCHAR(64) NOT NULL,
    source_message_identity VARCHAR(128) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL,
    first_result_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (source_system_code, source_message_identity)
);

CREATE TABLE IF NOT EXISTS pis.pathology_case (
    id UUID PRIMARY KEY,
    case_no VARCHAR(64) NOT NULL UNIQUE,
    case_lifecycle_state_code VARCHAR(32) NOT NULL,
    request_id UUID NOT NULL REFERENCES pis.pathology_request(id),
    patient_visit_snapshot_id UUID NOT NULL REFERENCES pis.patient_visit_snapshot(id),
    pathology_modality_code VARCHAR(32) NOT NULL,
    case_source_code VARCHAR(32) NOT NULL,
    case_established_at TIMESTAMPTZ NOT NULL,
    case_effective_at TIMESTAMPTZ NOT NULL,
    record_version_no INTEGER NOT NULL,
    concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (request_id)
);

CREATE TABLE IF NOT EXISTS pis.specimen (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis.pathology_case(id),
    specimen_no VARCHAR(64) NOT NULL UNIQUE,
    specimen_kind_code VARCHAR(32) NOT NULL,
    specimen_source_code VARCHAR(32) NOT NULL,
    collection_site_text VARCHAR(500) NOT NULL,
    collection_method_code VARCHAR(32) NOT NULL,
    specimen_lifecycle_state_code VARCHAR(32) NOT NULL,
    received_at TIMESTAMPTZ,
    received_by_ref VARCHAR(128),
    specimen_difference_code VARCHAR(64),
    record_version_no INTEGER NOT NULL,
    concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.specimen_container (
    id UUID PRIMARY KEY,
    specimen_id UUID NOT NULL REFERENCES pis.specimen(id),
    container_barcode VARCHAR(128) NOT NULL UNIQUE,
    expected_quantity INTEGER NOT NULL CHECK (expected_quantity > 0),
    actual_quantity INTEGER,
    container_state_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.clinical_state_current (
    object_id UUID NOT NULL,
    object_kind_code VARCHAR(32) NOT NULL,
    state_machine_code VARCHAR(32) NOT NULL,
    state_code VARCHAR(32) NOT NULL,
    concurrency_version BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    PRIMARY KEY (object_id, state_machine_code)
);

CREATE TABLE IF NOT EXISTS pis.state_transition_history (
    id UUID PRIMARY KEY,
    object_id UUID NOT NULL,
    object_kind_code VARCHAR(32) NOT NULL,
    state_machine_code VARCHAR(32) NOT NULL,
    source_state_code VARCHAR(32) NOT NULL,
    target_state_code VARCHAR(32) NOT NULL,
    transition_event_code VARCHAR(64) NOT NULL,
    expected_version BIGINT,
    resulting_version BIGINT,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_by_ref VARCHAR(128) NOT NULL,
    reason VARCHAR(500) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.operation_responsibility (
    id UUID PRIMARY KEY,
    object_id UUID NOT NULL,
    object_kind_code VARCHAR(32) NOT NULL,
    responsibility_type_code VARCHAR(32) NOT NULL,
    responsible_actor_ref VARCHAR(128) NOT NULL,
    actual_actor_ref VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.handoff_record (
    id UUID PRIMARY KEY,
    object_id UUID NOT NULL,
    object_kind_code VARCHAR(32) NOT NULL,
    from_actor_ref VARCHAR(128) NOT NULL,
    to_actor_ref VARCHAR(128) NOT NULL,
    handoff_state_code VARCHAR(32) NOT NULL,
    handoff_digest VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (object_id, handoff_digest)
);

CREATE TABLE IF NOT EXISTS pis.business_exception (
    id UUID PRIMARY KEY,
    object_id UUID NOT NULL,
    object_kind_code VARCHAR(32) NOT NULL,
    error_code VARCHAR(32) NOT NULL,
    exception_state_code VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.audit_event (
    id UUID PRIMARY KEY,
    operation_code VARCHAR(64) NOT NULL,
    permission_code VARCHAR(32) NOT NULL,
    actor_ref VARCHAR(128) NOT NULL,
    subject_type_code VARCHAR(32) NOT NULL,
    target_object_id UUID,
    target_object_kind_code VARCHAR(32),
    authorization_outcome VARCHAR(32) NOT NULL,
    processing_outcome VARCHAR(32) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.outbox_event (
    id UUID PRIMARY KEY,
    event_identity VARCHAR(128) NOT NULL UNIQUE,
    event_type_code VARCHAR(64) NOT NULL,
    subject_id UUID NOT NULL,
    subject_kind_code VARCHAR(32) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL,
    publish_state_code VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_p15_specimen_queue ON pis.specimen(specimen_lifecycle_state_code, created_at);
CREATE INDEX IF NOT EXISTS idx_p15_case_request ON pis.pathology_case(request_id);
CREATE INDEX IF NOT EXISTS idx_p15_audit_target ON pis.audit_event(target_object_id, created_at);

INSERT INTO pis.foundation_schema_metadata (metadata_id, schema_code, foundation_version)
VALUES ('00000000-0000-0000-0000-000000000015', 'PIS_NEXT', 'P15')
ON CONFLICT (schema_code) DO UPDATE SET foundation_version = 'P15';
