CREATE SCHEMA IF NOT EXISTS pis;

ALTER TABLE pis.specimen ADD COLUMN IF NOT EXISTS specimen_kind_code VARCHAR(32) DEFAULT 'SYNTHETIC';
ALTER TABLE pis.specimen ADD COLUMN IF NOT EXISTS specimen_source_code VARCHAR(32) DEFAULT 'LOCAL';
ALTER TABLE pis.specimen ADD COLUMN IF NOT EXISTS collection_method_code VARCHAR(32) DEFAULT 'DIRECT';
ALTER TABLE pis.specimen ADD COLUMN IF NOT EXISTS record_version_no INTEGER DEFAULT 1;
ALTER TABLE pis.specimen ADD COLUMN IF NOT EXISTS concurrency_version BIGINT DEFAULT 0;
ALTER TABLE pis.specimen ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE pis.specimen ADD COLUMN IF NOT EXISTS created_by_ref VARCHAR(128) DEFAULT 'P17-TEST';

CREATE TABLE IF NOT EXISTS pis.foundation_schema_metadata (
    metadata_id UUID PRIMARY KEY,
    schema_code VARCHAR(64) NOT NULL UNIQUE,
    foundation_version VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pis.p16_idempotency_key (
    id UUID PRIMARY KEY,
    operation_code VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL,
    result_object_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (operation_code, idempotency_key)
);

CREATE TABLE IF NOT EXISTS pis.state_transition_history (
    id UUID PRIMARY KEY,
    object_id UUID NOT NULL,
    object_kind_code VARCHAR(64) NOT NULL,
    state_machine_code VARCHAR(64) NOT NULL,
    source_state_code VARCHAR(64) NOT NULL,
    target_state_code VARCHAR(64) NOT NULL,
    transition_event_code VARCHAR(128) NOT NULL,
    expected_version BIGINT,
    resulting_version BIGINT,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_by_ref VARCHAR(128) NOT NULL,
    reason VARCHAR(500) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.operation_responsibility (
    id UUID PRIMARY KEY,
    object_id UUID NOT NULL,
    object_kind_code VARCHAR(64) NOT NULL,
    responsibility_type_code VARCHAR(64) NOT NULL,
    responsible_actor_ref VARCHAR(128) NOT NULL,
    actual_actor_ref VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.audit_event (
    id UUID PRIMARY KEY,
    operation_code VARCHAR(64) NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    actor_ref VARCHAR(128) NOT NULL,
    subject_type_code VARCHAR(32) NOT NULL,
    target_object_id UUID,
    target_object_kind_code VARCHAR(32),
    authorization_outcome VARCHAR(32) NOT NULL,
    processing_outcome VARCHAR(32) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.outbox_event (
    id UUID PRIMARY KEY,
    event_identity VARCHAR(128) NOT NULL UNIQUE,
    event_type_code VARCHAR(128) NOT NULL,
    subject_id UUID NOT NULL,
    subject_kind_code VARCHAR(64) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL,
    publish_state_code VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_task (
    id UUID PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL,
    tissue_block_id UUID NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    task_state_code VARCHAR(64) NOT NULL,
    assigned_actor_ref VARCHAR(128),
    actual_actor_ref VARCHAR(128),
    assigned_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    record_version_no INTEGER NOT NULL,
    concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (organization_reference, task_no)
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_task_assignment (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    from_actor_ref VARCHAR(128),
    to_actor_ref VARCHAR(128) NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    reason VARCHAR(1000),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_program (
    id UUID PRIMARY KEY,
    program_code VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    environment_code VARCHAR(32) NOT NULL,
    lifecycle_state_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_program_version (
    id UUID PRIMARY KEY,
    program_id UUID NOT NULL,
    version_label VARCHAR(64) NOT NULL,
    version_state_code VARCHAR(32) NOT NULL,
    version_digest VARCHAR(128) NOT NULL UNIQUE,
    parameter_reference VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (program_id, version_label)
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_program_step (
    id UUID PRIMARY KEY,
    program_version_id UUID NOT NULL,
    step_sequence INTEGER NOT NULL,
    step_code VARCHAR(64) NOT NULL,
    parameter_reference VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (program_version_id, step_sequence)
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_batch (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    batch_no VARCHAR(64) NOT NULL,
    program_version_id UUID,
    program_version_snapshot VARCHAR(500) NOT NULL,
    execution_mode_code VARCHAR(32) NOT NULL,
    device_identity_ref VARCHAR(128),
    organization_reference VARCHAR(128) NOT NULL,
    batch_state_code VARCHAR(64) NOT NULL,
    assigned_actor_ref VARCHAR(128),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    interrupted_at TIMESTAMP WITH TIME ZONE,
    failure_reason_code VARCHAR(64),
    record_version_no INTEGER NOT NULL,
    concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (task_id),
    UNIQUE (organization_reference, batch_no)
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_batch_member (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL,
    tissue_block_id UUID NOT NULL,
    tissue_box_identity_id UUID,
    planned_block_no_snapshot VARCHAR(64) NOT NULL,
    member_state_code VARCHAR(64) NOT NULL,
    impact_state_code VARCHAR(64) NOT NULL,
    can_enter_embedding BOOLEAN NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    concurrency_version BIGINT NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (batch_id, tissue_block_id)
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_run (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL,
    run_no INTEGER NOT NULL,
    execution_mode_code VARCHAR(32) NOT NULL,
    device_identity_ref VARCHAR(128),
    external_run_id VARCHAR(128) UNIQUE,
    program_version_snapshot VARCHAR(500) NOT NULL,
    run_state_code VARCHAR(64) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    validated_at TIMESTAMP WITH TIME ZONE,
    confirmed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (batch_id, run_no)
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_run_step (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    step_sequence INTEGER NOT NULL,
    step_code VARCHAR(64) NOT NULL,
    step_state_code VARCHAR(64) NOT NULL,
    observed_reference VARCHAR(500),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (run_id, step_sequence)
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_raw_result (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    external_message_id VARCHAR(128) NOT NULL UNIQUE,
    external_payload_digest VARCHAR(128) NOT NULL,
    raw_state_code VARCHAR(64) NOT NULL,
    raw_payload_reference VARCHAR(500),
    device_occurred_at TIMESTAMP WITH TIME ZONE,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_result (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    member_id UUID NOT NULL,
    result_state_code VARCHAR(64) NOT NULL,
    can_enter_embedding BOOLEAN NOT NULL,
    result_summary VARCHAR(2000) NOT NULL,
    validated_at TIMESTAMP WITH TIME ZONE,
    validated_by_ref VARCHAR(128),
    confirmed_at TIMESTAMP WITH TIME ZONE,
    confirmed_by_ref VARCHAR(128),
    record_version_no INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (run_id, member_id)
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_exception (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL,
    run_id UUID,
    member_id UUID,
    exception_code VARCHAR(64) NOT NULL,
    severity_code VARCHAR(32) NOT NULL,
    exception_state_code VARCHAR(64) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    affected_scope VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by_ref VARCHAR(128)
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_member_impact (
    id UUID PRIMARY KEY,
    exception_id UUID NOT NULL,
    member_id UUID NOT NULL,
    impact_state_code VARCHAR(64) NOT NULL,
    can_continue BOOLEAN NOT NULL,
    requires_reprocess BOOLEAN NOT NULL,
    isolation_required BOOLEAN NOT NULL,
    decision_reason VARCHAR(1000) NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (exception_id, member_id)
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_recovery (
    id UUID PRIMARY KEY,
    exception_id UUID NOT NULL,
    recovery_kind_code VARCHAR(64) NOT NULL,
    recovery_state_code VARCHAR(64) NOT NULL,
    decision_reason VARCHAR(1000) NOT NULL,
    approved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    approved_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_reprocess (
    id UUID PRIMARY KEY,
    original_batch_id UUID NOT NULL,
    original_run_id UUID,
    original_member_id UUID,
    replacement_task_id UUID NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    approved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    approved_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p17_embedding_task (
    id UUID PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL,
    tissue_block_id UUID NOT NULL,
    processing_result_id UUID NOT NULL,
    task_attempt_no INTEGER NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    task_state_code VARCHAR(64) NOT NULL,
    assigned_actor_ref VARCHAR(128),
    actual_actor_ref VARCHAR(128),
    embedding_requirement_snapshot VARCHAR(2000),
    orientation_reference VARCHAR(256),
    assigned_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    record_version_no INTEGER NOT NULL,
    concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    rework_of_formation_id UUID,
    UNIQUE (organization_reference, task_no),
    UNIQUE (tissue_block_id, processing_result_id, task_attempt_no)
);

CREATE TABLE IF NOT EXISTS pis.p17_embedding_task_assignment (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    from_actor_ref VARCHAR(128),
    to_actor_ref VARCHAR(128) NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    reason VARCHAR(1000),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p17_embedding_fact (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL UNIQUE,
    tissue_block_id UUID NOT NULL,
    processing_result_id UUID NOT NULL,
    embedding_state_code VARCHAR(64) NOT NULL,
    requirement_snapshot VARCHAR(2000) NOT NULL,
    orientation_reference VARCHAR(256),
    actual_actor_ref VARCHAR(128) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    record_version_no INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p17_actual_block_formation (
    id UUID PRIMARY KEY,
    tissue_block_id UUID NOT NULL,
    embedding_fact_id UUID NOT NULL UNIQUE,
    processing_result_id UUID NOT NULL,
    formation_version_no INTEGER NOT NULL,
    inherited_block_no VARCHAR(64) NOT NULL,
    current_valid BOOLEAN NOT NULL,
    formation_state_code VARCHAR(64) NOT NULL,
    formed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    formed_by_ref VARCHAR(128) NOT NULL,
    supersedes_formation_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tissue_block_id, formation_version_no)
);

CREATE TABLE IF NOT EXISTS pis.p17_actual_block_replacement (
    id UUID PRIMARY KEY,
    original_formation_id UUID NOT NULL,
    replacement_formation_id UUID NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (original_formation_id, replacement_formation_id)
);

MERGE INTO pis.p17_processing_program (id, program_code, display_name, environment_code, lifecycle_state_code, created_at, created_by_ref)
KEY (program_code)
VALUES ('00000000-0000-0000-0000-000000000017', 'P17-SYNTHETIC-REFERENCE', 'P17 synthetic reference program', 'SYNTHETIC', 'ACTIVE', CURRENT_TIMESTAMP, 'P17-TEST');

MERGE INTO pis.p17_processing_program_version (id, program_id, version_label, version_state_code, version_digest, parameter_reference, created_at, created_by_ref)
KEY (version_digest)
VALUES ('00000000-0000-0000-0000-000000000018', '00000000-0000-0000-0000-000000000017', 'SYNTHETIC-1', 'ACTIVE', 'P17-SYNTHETIC-REFERENCE-V1', 'P17-SYNTHETIC-REFERENCE-PARAMETERS', CURRENT_TIMESTAMP, 'P17-TEST');

MERGE INTO pis.p17_processing_program_step (id, program_version_id, step_sequence, step_code, parameter_reference, created_at, created_by_ref)
KEY (program_version_id, step_sequence)
VALUES ('00000000-0000-0000-0000-000000000019', '00000000-0000-0000-0000-000000000018', 1, 'SYNTHETIC-PROCESSING', 'P17-SYNTHETIC-REFERENCE-STEP-1', CURRENT_TIMESTAMP, 'P17-TEST');
