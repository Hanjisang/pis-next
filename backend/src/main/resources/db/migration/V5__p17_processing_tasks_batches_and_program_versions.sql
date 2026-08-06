CREATE TABLE IF NOT EXISTS pis.p17_processing_task (
    id UUID PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL,
    tissue_block_id UUID NOT NULL REFERENCES pis.tissue_block(id),
    organization_reference VARCHAR(128) NOT NULL,
    task_state_code VARCHAR(64) NOT NULL,
    assigned_actor_ref VARCHAR(128),
    actual_actor_ref VARCHAR(128),
    assigned_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    record_version_no INTEGER NOT NULL,
    concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (organization_reference, task_no)
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_task_assignment (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES pis.p17_processing_task(id),
    from_actor_ref VARCHAR(128),
    to_actor_ref VARCHAR(128) NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    reason VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_program (
    id UUID PRIMARY KEY,
    program_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    environment_code VARCHAR(32) NOT NULL,
    lifecycle_state_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (program_code),
    CHECK (environment_code IN ('SYNTHETIC', 'FORMAL'))
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_program_version (
    id UUID PRIMARY KEY,
    program_id UUID NOT NULL REFERENCES pis.p17_processing_program(id),
    version_label VARCHAR(64) NOT NULL,
    version_state_code VARCHAR(32) NOT NULL,
    version_digest VARCHAR(128) NOT NULL,
    parameter_reference VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (program_id, version_label),
    UNIQUE (version_digest)
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_program_step (
    id UUID PRIMARY KEY,
    program_version_id UUID NOT NULL REFERENCES pis.p17_processing_program_version(id),
    step_sequence INTEGER NOT NULL,
    step_code VARCHAR(64) NOT NULL,
    parameter_reference VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (program_version_id, step_sequence)
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_batch (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES pis.p17_processing_task(id),
    batch_no VARCHAR(64) NOT NULL,
    program_version_id UUID REFERENCES pis.p17_processing_program_version(id),
    program_version_snapshot VARCHAR(500) NOT NULL,
    execution_mode_code VARCHAR(32) NOT NULL,
    device_identity_ref VARCHAR(128),
    organization_reference VARCHAR(128) NOT NULL,
    batch_state_code VARCHAR(64) NOT NULL,
    assigned_actor_ref VARCHAR(128),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    interrupted_at TIMESTAMPTZ,
    failure_reason_code VARCHAR(64),
    record_version_no INTEGER NOT NULL,
    concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (task_id),
    UNIQUE (organization_reference, batch_no),
    CHECK (execution_mode_code IN ('HUMAN', 'DEVICE'))
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_batch_member (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES pis.p17_processing_batch(id),
    tissue_block_id UUID NOT NULL REFERENCES pis.tissue_block(id),
    tissue_box_identity_id UUID REFERENCES pis.tissue_box_identity(id),
    planned_block_no_snapshot VARCHAR(64) NOT NULL,
    member_state_code VARCHAR(64) NOT NULL,
    impact_state_code VARCHAR(64) NOT NULL,
    can_enter_embedding BOOLEAN NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    concurrency_version BIGINT NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (batch_id, tissue_block_id)
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_run (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES pis.p17_processing_batch(id),
    run_no INTEGER NOT NULL,
    execution_mode_code VARCHAR(32) NOT NULL,
    device_identity_ref VARCHAR(128),
    external_run_id VARCHAR(128),
    program_version_snapshot VARCHAR(500) NOT NULL,
    run_state_code VARCHAR(64) NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    validated_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (batch_id, run_no),
    UNIQUE (external_run_id)
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_run_step (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES pis.p17_processing_run(id),
    step_sequence INTEGER NOT NULL,
    step_code VARCHAR(64) NOT NULL,
    step_state_code VARCHAR(64) NOT NULL,
    observed_reference VARCHAR(500),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (run_id, step_sequence)
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_raw_result (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES pis.p17_processing_run(id),
    external_message_id VARCHAR(128) NOT NULL,
    external_payload_digest VARCHAR(128) NOT NULL,
    raw_state_code VARCHAR(64) NOT NULL,
    raw_payload_reference VARCHAR(500),
    device_occurred_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL,
    received_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (external_message_id)
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_result (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES pis.p17_processing_run(id),
    member_id UUID NOT NULL REFERENCES pis.p17_processing_batch_member(id),
    result_state_code VARCHAR(64) NOT NULL,
    can_enter_embedding BOOLEAN NOT NULL,
    result_summary VARCHAR(2000) NOT NULL,
    validated_at TIMESTAMPTZ,
    validated_by_ref VARCHAR(128),
    confirmed_at TIMESTAMPTZ,
    confirmed_by_ref VARCHAR(128),
    record_version_no INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (run_id, member_id)
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_exception (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES pis.p17_processing_batch(id),
    run_id UUID REFERENCES pis.p17_processing_run(id),
    member_id UUID REFERENCES pis.p17_processing_batch_member(id),
    exception_code VARCHAR(64) NOT NULL,
    severity_code VARCHAR(32) NOT NULL,
    exception_state_code VARCHAR(64) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    affected_scope VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    resolved_at TIMESTAMPTZ,
    resolved_by_ref VARCHAR(128)
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_member_impact (
    id UUID PRIMARY KEY,
    exception_id UUID NOT NULL REFERENCES pis.p17_processing_exception(id),
    member_id UUID NOT NULL REFERENCES pis.p17_processing_batch_member(id),
    impact_state_code VARCHAR(64) NOT NULL,
    can_continue BOOLEAN NOT NULL,
    requires_reprocess BOOLEAN NOT NULL,
    isolation_required BOOLEAN NOT NULL,
    decision_reason VARCHAR(1000) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    decided_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (exception_id, member_id)
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_recovery (
    id UUID PRIMARY KEY,
    exception_id UUID NOT NULL REFERENCES pis.p17_processing_exception(id),
    recovery_kind_code VARCHAR(64) NOT NULL,
    recovery_state_code VARCHAR(64) NOT NULL,
    decision_reason VARCHAR(1000) NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    approved_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p17_processing_reprocess (
    id UUID PRIMARY KEY,
    original_batch_id UUID NOT NULL REFERENCES pis.p17_processing_batch(id),
    original_run_id UUID REFERENCES pis.p17_processing_run(id),
    original_member_id UUID REFERENCES pis.p17_processing_batch_member(id),
    replacement_task_id UUID NOT NULL REFERENCES pis.p17_processing_task(id),
    reason VARCHAR(1000) NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    approved_by_ref VARCHAR(128) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_p17_processing_queue ON pis.p17_processing_task(organization_reference, task_state_code, created_at);
CREATE INDEX IF NOT EXISTS idx_p17_processing_batch_state ON pis.p17_processing_batch(organization_reference, batch_state_code, created_at);
CREATE INDEX IF NOT EXISTS idx_p17_processing_member_block ON pis.p17_processing_batch_member(tissue_block_id, member_state_code);
CREATE INDEX IF NOT EXISTS idx_p17_processing_exception ON pis.p17_processing_exception(batch_id, exception_state_code, created_at);

INSERT INTO pis.p17_processing_program
    (id, program_code, display_name, environment_code, lifecycle_state_code, created_at, created_by_ref)
VALUES
    ('00000000-0000-0000-0000-000000000017', 'P17-SYNTHETIC-REFERENCE', 'P17合成参考组织处理程序', 'SYNTHETIC', 'ACTIVE', CURRENT_TIMESTAMP, 'P17-MIGRATION')
ON CONFLICT (program_code) DO NOTHING;

INSERT INTO pis.p17_processing_program_version
    (id, program_id, version_label, version_state_code, version_digest, parameter_reference, created_at, created_by_ref)
VALUES
    ('00000000-0000-0000-0000-000000000018', '00000000-0000-0000-0000-000000000017', 'SYNTHETIC-1', 'ACTIVE', 'P17-SYNTHETIC-REFERENCE-V1', 'P17-SYNTHETIC-REFERENCE-PARAMETERS', CURRENT_TIMESTAMP, 'P17-MIGRATION')
ON CONFLICT (version_digest) DO NOTHING;

INSERT INTO pis.p17_processing_program_step
    (id, program_version_id, step_sequence, step_code, parameter_reference, created_at, created_by_ref)
VALUES
    ('00000000-0000-0000-0000-000000000019', '00000000-0000-0000-0000-000000000018', 1, 'SYNTHETIC-PROCESSING', 'P17-SYNTHETIC-REFERENCE-STEP-1', CURRENT_TIMESTAMP, 'P17-MIGRATION')
ON CONFLICT (program_version_id, step_sequence) DO NOTHING;

INSERT INTO pis.foundation_schema_metadata (metadata_id, schema_code, foundation_version)
VALUES ('00000000-0000-0000-0000-000000000017', 'PIS_NEXT', 'P17')
ON CONFLICT (schema_code) DO UPDATE SET foundation_version = 'P17';
