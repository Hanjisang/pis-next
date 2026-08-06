CREATE TABLE IF NOT EXISTS pis.p18_technical_project_configuration (
    id UUID PRIMARY KEY,
    project_code VARCHAR(64) NOT NULL,
    version_label VARCHAR(32) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    project_type_code VARCHAR(32) NOT NULL,
    target_kind_code VARCHAR(32) NOT NULL,
    environment_code VARCHAR(16) NOT NULL,
    lifecycle_state_code VARCHAR(32) NOT NULL,
    configuration_digest VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (project_code, version_label),
    UNIQUE (configuration_digest),
    CHECK (project_type_code IN ('DEEP_SECTION', 'RECUT', 'WHITE_SLIDE', 'IHC', 'SPECIAL_STAIN')),
    CHECK (target_kind_code = 'ACTUAL_BLOCK'),
    CHECK (environment_code IN ('SYNTHETIC', 'LOCAL', 'FORMAL')),
    CHECK (lifecycle_state_code IN ('ACTIVE', 'RETIRED'))
);

CREATE TABLE IF NOT EXISTS pis.p18_technical_order (
    id UUID PRIMARY KEY,
    technical_order_no VARCHAR(64) NOT NULL,
    case_id UUID NOT NULL REFERENCES pis.pathology_case(id),
    order_kind_code VARCHAR(32) NOT NULL,
    order_lifecycle_state_code VARCHAR(32) NOT NULL,
    priority_code VARCHAR(32) NOT NULL,
    reason_text VARCHAR(2000) NOT NULL,
    ordering_actor_ref VARCHAR(128) NOT NULL,
    represented_actor_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    submitted_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    record_version_no INTEGER NOT NULL,
    concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (organization_reference, technical_order_no),
    CHECK (order_lifecycle_state_code IN ('DRAFT', 'SUBMITTED', 'RETURNED', 'ACCEPTED', 'IN_PROGRESS', 'WAITING_RESULT', 'PARTIALLY_COMPLETED', 'COMPLETED', 'CANCELLED')),
    CHECK (record_version_no > 0),
    CHECK (concurrency_version >= 0)
);

CREATE TABLE IF NOT EXISTS pis.p18_technical_order_project (
    id UUID PRIMARY KEY,
    technical_order_id UUID NOT NULL REFERENCES pis.p18_technical_order(id),
    project_no VARCHAR(64) NOT NULL,
    configuration_id UUID NOT NULL REFERENCES pis.p18_technical_project_configuration(id),
    project_type_code VARCHAR(32) NOT NULL,
    project_task_state_code VARCHAR(32) NOT NULL,
    review_state_code VARCHAR(32) NOT NULL,
    receiving_state_code VARCHAR(32) NOT NULL,
    execution_handoff_state_code VARCHAR(32) NOT NULL,
    result_state_code VARCHAR(32) NOT NULL,
    usage_code VARCHAR(32) NOT NULL,
    priority_code VARCHAR(32) NOT NULL,
    reason_text VARCHAR(2000) NOT NULL,
    assigned_actor_ref VARCHAR(128),
    record_version_no INTEGER NOT NULL,
    concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (technical_order_id, project_no),
    CHECK (project_task_state_code IN ('P08-SM-007-ST-01', 'P08-SM-007-ST-02', 'P08-SM-007-ST-03', 'P08-SM-007-ST-04')),
    CHECK (review_state_code IN ('NOT_REQUIRED', 'PENDING', 'APPROVED', 'REJECTED')),
    CHECK (receiving_state_code IN ('NOT_RECEIVED', 'RECEIVED', 'REFUSED')),
    CHECK (execution_handoff_state_code IN ('NOT_HANDOFF', 'HANDED_OFF')),
    CHECK (result_state_code IN ('NOT_EXPECTED', 'WAITING', 'REFERENCED', 'CLOSED')),
    CHECK (record_version_no > 0),
    CHECK (concurrency_version >= 0)
);

CREATE TABLE IF NOT EXISTS pis.p18_order_target (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL UNIQUE REFERENCES pis.p18_technical_order_project(id),
    case_id UUID NOT NULL REFERENCES pis.pathology_case(id),
    target_kind_code VARCHAR(32) NOT NULL,
    actual_block_formation_id UUID NOT NULL REFERENCES pis.p17_actual_block_formation(id),
    target_state_code VARCHAR(32) NOT NULL,
    target_version_no INTEGER NOT NULL,
    bound_at TIMESTAMPTZ NOT NULL,
    bound_by_ref VARCHAR(128) NOT NULL,
    CHECK (target_kind_code = 'ACTUAL_BLOCK'),
    CHECK (target_state_code IN ('VALID', 'CORRECTED', 'BLOCKED', 'VOID')),
    CHECK (target_version_no > 0)
);

CREATE TABLE IF NOT EXISTS pis.p18_order_target_history (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES pis.p18_technical_order_project(id),
    case_id UUID NOT NULL REFERENCES pis.pathology_case(id),
    target_kind_code VARCHAR(32) NOT NULL,
    actual_block_formation_id UUID NOT NULL REFERENCES pis.p17_actual_block_formation(id),
    target_state_code VARCHAR(32) NOT NULL,
    target_version_no INTEGER NOT NULL,
    change_kind_code VARCHAR(32) NOT NULL,
    reason_text VARCHAR(1000) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_by_ref VARCHAR(128) NOT NULL,
    CHECK (target_kind_code = 'ACTUAL_BLOCK'),
    CHECK (target_state_code IN ('VALID', 'CORRECTED', 'BLOCKED', 'VOID'))
);

CREATE TABLE IF NOT EXISTS pis.p18_planned_output (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES pis.p18_technical_order_project(id),
    sequence_no INTEGER NOT NULL,
    output_kind_code VARCHAR(32) NOT NULL,
    slide_purpose_code VARCHAR(32) NOT NULL,
    planned_layer_reference VARCHAR(128),
    planned_quantity INTEGER NOT NULL,
    planned_stain_project_code VARCHAR(64),
    planned_usage_code VARCHAR(32) NOT NULL,
    planned_label_quantity INTEGER NOT NULL,
    execution_note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (project_id, sequence_no),
    CHECK (output_kind_code IN ('PLANNED_SLIDE', 'PLANNED_TECHNICAL_OUTPUT')),
    CHECK (planned_quantity > 0),
    CHECK (planned_label_quantity >= 0)
);

CREATE TABLE IF NOT EXISTS pis.p18_project_review (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES pis.p18_technical_order_project(id),
    decision_code VARCHAR(32) NOT NULL,
    review_reason VARCHAR(1000) NOT NULL,
    reviewer_actor_ref VARCHAR(128) NOT NULL,
    reviewed_at TIMESTAMPTZ NOT NULL,
    project_version_no INTEGER NOT NULL,
    CHECK (decision_code IN ('APPROVED', 'REJECTED'))
);

CREATE TABLE IF NOT EXISTS pis.p18_project_responsibility_history (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES pis.p18_technical_order_project(id),
    responsibility_type_code VARCHAR(32) NOT NULL,
    from_actor_ref VARCHAR(128),
    to_actor_ref VARCHAR(128) NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    reason_text VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p18_project_change (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES pis.p18_technical_order_project(id),
    change_kind_code VARCHAR(32) NOT NULL,
    prior_version_no INTEGER NOT NULL,
    change_summary VARCHAR(2000) NOT NULL,
    changed_by_ref VARCHAR(128) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p18_project_cancellation (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES pis.p18_technical_order_project(id),
    cancellation_kind_code VARCHAR(32) NOT NULL,
    reason_text VARCHAR(1000) NOT NULL,
    impact_summary VARCHAR(1000) NOT NULL,
    cancelled_by_ref VARCHAR(128) NOT NULL,
    cancelled_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p18_project_result_reference (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES pis.p18_technical_order_project(id),
    result_reference_kind_code VARCHAR(32) NOT NULL,
    result_identity VARCHAR(128) NOT NULL,
    result_digest VARCHAR(128) NOT NULL,
    result_environment_code VARCHAR(32) NOT NULL,
    result_note VARCHAR(1000) NOT NULL,
    referenced_by_ref VARCHAR(128) NOT NULL,
    referenced_at TIMESTAMPTZ NOT NULL,
    UNIQUE (project_id, result_identity),
    CHECK (result_environment_code IN ('SYNTHETIC', 'DEV', 'NON-CLINICAL', 'FORMAL'))
);

CREATE TABLE IF NOT EXISTS pis.p18_order_state_history (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES pis.p18_technical_order(id),
    source_state_code VARCHAR(32) NOT NULL,
    target_state_code VARCHAR(32) NOT NULL,
    transition_reason VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p18_project_state_history (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES pis.p18_technical_order_project(id),
    source_state_code VARCHAR(32) NOT NULL,
    target_state_code VARCHAR(32) NOT NULL,
    transition_event_code VARCHAR(64) NOT NULL,
    expected_version BIGINT,
    resulting_version BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_by_ref VARCHAR(128) NOT NULL,
    reason_text VARCHAR(500) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_p18_order_queue
    ON pis.p18_technical_order(organization_reference, order_lifecycle_state_code, created_at);
CREATE INDEX IF NOT EXISTS idx_p18_project_queue
    ON pis.p18_technical_order_project(project_task_state_code, review_state_code, receiving_state_code, created_at);
CREATE INDEX IF NOT EXISTS idx_p18_target_block
    ON pis.p18_order_target(actual_block_formation_id, target_state_code);
CREATE INDEX IF NOT EXISTS idx_p18_audit_order
    ON pis.audit_event(target_object_id, created_at);

INSERT INTO pis.p18_technical_project_configuration
    (id, project_code, version_label, display_name, project_type_code, target_kind_code, environment_code,
     lifecycle_state_code, configuration_digest, created_at, created_by_ref)
VALUES
    ('00000000-0000-0000-0000-000000000181', 'P18-SYNTHETIC-DEEP-SECTION', 'SYNTHETIC-1', 'P18合成深切项目', 'DEEP_SECTION', 'ACTUAL_BLOCK', 'SYNTHETIC', 'ACTIVE', 'P18-SYNTHETIC-DEEP-SECTION-V1', CURRENT_TIMESTAMP, 'P18-MIGRATION'),
    ('00000000-0000-0000-0000-000000000182', 'P18-SYNTHETIC-RECUT', 'SYNTHETIC-1', 'P18合成重切项目', 'RECUT', 'ACTUAL_BLOCK', 'SYNTHETIC', 'ACTIVE', 'P18-SYNTHETIC-RECUT-V1', CURRENT_TIMESTAMP, 'P18-MIGRATION'),
    ('00000000-0000-0000-0000-000000000183', 'P18-SYNTHETIC-WHITE-SLIDE', 'SYNTHETIC-1', 'P18合成白片项目', 'WHITE_SLIDE', 'ACTUAL_BLOCK', 'SYNTHETIC', 'ACTIVE', 'P18-SYNTHETIC-WHITE-SLIDE-V1', CURRENT_TIMESTAMP, 'P18-MIGRATION'),
    ('00000000-0000-0000-0000-000000000184', 'P18-SYNTHETIC-IHC', 'SYNTHETIC-1', 'P18合成免疫组化项目', 'IHC', 'ACTUAL_BLOCK', 'SYNTHETIC', 'ACTIVE', 'P18-SYNTHETIC-IHC-V1', CURRENT_TIMESTAMP, 'P18-MIGRATION'),
    ('00000000-0000-0000-0000-000000000185', 'P18-SYNTHETIC-SPECIAL-STAIN', 'SYNTHETIC-1', 'P18合成特殊染色项目', 'SPECIAL_STAIN', 'ACTUAL_BLOCK', 'SYNTHETIC', 'ACTIVE', 'P18-SYNTHETIC-SPECIAL-STAIN-V1', CURRENT_TIMESTAMP, 'P18-MIGRATION')
ON CONFLICT (configuration_digest) DO NOTHING;

INSERT INTO pis.foundation_schema_metadata (metadata_id, schema_code, foundation_version)
VALUES ('00000000-0000-0000-0000-000000000018', 'PIS_NEXT', 'P18')
ON CONFLICT (schema_code) DO UPDATE SET foundation_version = 'P18';
