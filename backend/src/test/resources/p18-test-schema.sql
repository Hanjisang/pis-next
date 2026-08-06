CREATE TABLE IF NOT EXISTS pis.p18_technical_project_configuration (
    id UUID PRIMARY KEY, project_code VARCHAR(64) NOT NULL, version_label VARCHAR(32) NOT NULL,
    display_name VARCHAR(200) NOT NULL, project_type_code VARCHAR(32) NOT NULL, target_kind_code VARCHAR(32) NOT NULL,
    environment_code VARCHAR(16) NOT NULL, lifecycle_state_code VARCHAR(32) NOT NULL, configuration_digest VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE(project_code, version_label), UNIQUE(configuration_digest)
);
CREATE TABLE IF NOT EXISTS pis.p18_technical_order (
    id UUID PRIMARY KEY, technical_order_no VARCHAR(64) NOT NULL, case_id UUID NOT NULL,
    order_kind_code VARCHAR(32) NOT NULL, order_lifecycle_state_code VARCHAR(32) NOT NULL, priority_code VARCHAR(32) NOT NULL,
    reason_text VARCHAR(2000) NOT NULL, ordering_actor_ref VARCHAR(128) NOT NULL, represented_actor_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL, submitted_at TIMESTAMP WITH TIME ZONE, cancelled_at TIMESTAMP WITH TIME ZONE,
    record_version_no INTEGER NOT NULL, concurrency_version BIGINT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL, UNIQUE(organization_reference, technical_order_no)
);
CREATE TABLE IF NOT EXISTS pis.p18_technical_order_project (
    id UUID PRIMARY KEY, technical_order_id UUID NOT NULL, project_no VARCHAR(64) NOT NULL, configuration_id UUID NOT NULL,
    project_type_code VARCHAR(32) NOT NULL, project_task_state_code VARCHAR(32) NOT NULL, review_state_code VARCHAR(32) NOT NULL,
    receiving_state_code VARCHAR(32) NOT NULL, execution_handoff_state_code VARCHAR(32) NOT NULL, result_state_code VARCHAR(32) NOT NULL,
    usage_code VARCHAR(32) NOT NULL, priority_code VARCHAR(32) NOT NULL, reason_text VARCHAR(2000) NOT NULL,
    assigned_actor_ref VARCHAR(128), record_version_no INTEGER NOT NULL, concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE(technical_order_id, project_no)
);
CREATE TABLE IF NOT EXISTS pis.p18_order_target (
    id UUID PRIMARY KEY, project_id UUID NOT NULL UNIQUE, case_id UUID NOT NULL, target_kind_code VARCHAR(32) NOT NULL,
    actual_block_formation_id UUID NOT NULL, target_state_code VARCHAR(32) NOT NULL, target_version_no INTEGER NOT NULL,
    bound_at TIMESTAMP WITH TIME ZONE NOT NULL, bound_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis.p18_order_target_history (
    id UUID PRIMARY KEY, project_id UUID NOT NULL, case_id UUID NOT NULL, target_kind_code VARCHAR(32) NOT NULL,
    actual_block_formation_id UUID NOT NULL, target_state_code VARCHAR(32) NOT NULL, target_version_no INTEGER NOT NULL,
    change_kind_code VARCHAR(32) NOT NULL, reason_text VARCHAR(1000) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis.p18_planned_output (
    id UUID PRIMARY KEY, project_id UUID NOT NULL, sequence_no INTEGER NOT NULL, output_kind_code VARCHAR(32) NOT NULL,
    slide_purpose_code VARCHAR(32) NOT NULL, planned_layer_reference VARCHAR(128), planned_quantity INTEGER NOT NULL,
    planned_stain_project_code VARCHAR(64), planned_usage_code VARCHAR(32) NOT NULL, planned_label_quantity INTEGER NOT NULL,
    execution_note VARCHAR(1000), created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE(project_id, sequence_no)
);
CREATE TABLE IF NOT EXISTS pis.p18_project_review (
    id UUID PRIMARY KEY, project_id UUID NOT NULL, decision_code VARCHAR(32) NOT NULL, review_reason VARCHAR(1000) NOT NULL,
    reviewer_actor_ref VARCHAR(128) NOT NULL, reviewed_at TIMESTAMP WITH TIME ZONE NOT NULL, project_version_no INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS pis.p18_project_responsibility_history (
    id UUID PRIMARY KEY, project_id UUID NOT NULL, responsibility_type_code VARCHAR(32) NOT NULL, from_actor_ref VARCHAR(128),
    to_actor_ref VARCHAR(128) NOT NULL, action_code VARCHAR(32) NOT NULL, reason_text VARCHAR(1000),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, recorded_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis.p18_project_change (
    id UUID PRIMARY KEY, project_id UUID NOT NULL, change_kind_code VARCHAR(32) NOT NULL, prior_version_no INTEGER NOT NULL,
    change_summary VARCHAR(2000) NOT NULL, changed_by_ref VARCHAR(128) NOT NULL, changed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE IF NOT EXISTS pis.p18_project_cancellation (
    id UUID PRIMARY KEY, project_id UUID NOT NULL, cancellation_kind_code VARCHAR(32) NOT NULL, reason_text VARCHAR(1000) NOT NULL,
    impact_summary VARCHAR(1000) NOT NULL, cancelled_by_ref VARCHAR(128) NOT NULL, cancelled_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE IF NOT EXISTS pis.p18_project_result_reference (
    id UUID PRIMARY KEY, project_id UUID NOT NULL, result_reference_kind_code VARCHAR(32) NOT NULL, result_identity VARCHAR(128) NOT NULL,
    result_digest VARCHAR(128) NOT NULL, result_environment_code VARCHAR(32) NOT NULL, result_note VARCHAR(1000) NOT NULL,
    referenced_by_ref VARCHAR(128) NOT NULL, referenced_at TIMESTAMP WITH TIME ZONE NOT NULL, UNIQUE(project_id, result_identity)
);
CREATE TABLE IF NOT EXISTS pis.p18_order_state_history (
    id UUID PRIMARY KEY, order_id UUID NOT NULL, source_state_code VARCHAR(32) NOT NULL, target_state_code VARCHAR(32) NOT NULL,
    transition_reason VARCHAR(500) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, recorded_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis.p18_project_state_history (
    id UUID PRIMARY KEY, project_id UUID NOT NULL, source_state_code VARCHAR(32) NOT NULL, target_state_code VARCHAR(32) NOT NULL,
    transition_event_code VARCHAR(64) NOT NULL, expected_version BIGINT, resulting_version BIGINT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, recorded_by_ref VARCHAR(128) NOT NULL, reason_text VARCHAR(500) NOT NULL
);

MERGE INTO pis.p18_technical_project_configuration
    (id, project_code, version_label, display_name, project_type_code, target_kind_code, environment_code,
     lifecycle_state_code, configuration_digest, created_at, created_by_ref)
KEY (id)
VALUES
    ('00000000-0000-0000-0000-000000000181', 'P18-SYNTHETIC-DEEP-SECTION', 'SYNTHETIC-1', 'synthetic deep section', 'DEEP_SECTION', 'ACTUAL_BLOCK', 'SYNTHETIC', 'ACTIVE', 'P18-SYNTHETIC-DEEP-SECTION-V1', CURRENT_TIMESTAMP, 'P18-TEST'),
    ('00000000-0000-0000-0000-000000000184', 'P18-SYNTHETIC-IHC', 'SYNTHETIC-1', 'synthetic IHC', 'IHC', 'ACTUAL_BLOCK', 'SYNTHETIC', 'ACTIVE', 'P18-SYNTHETIC-IHC-V1', CURRENT_TIMESTAMP, 'P18-TEST');
