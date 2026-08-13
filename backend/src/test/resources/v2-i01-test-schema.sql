-- Lightweight H2 fixture for web/application tests only.
-- PostgreSQL-specific constraints, Flyway behavior, and V33 -> V34 upgrade semantics are verified
-- by Testcontainers integration tests and must not be inferred from this compatibility schema.
CREATE SCHEMA IF NOT EXISTS pis;
CREATE SCHEMA IF NOT EXISTS pis_v2;

CREATE TABLE IF NOT EXISTS pis_v2.hospital_profile (
    id UUID PRIMARY KEY, profile_code VARCHAR(128) NOT NULL UNIQUE
);
CREATE TABLE IF NOT EXISTS pis_v2.hospital_campus (
    id UUID PRIMARY KEY, hospital_profile_id UUID NOT NULL, campus_code VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.hospital_department (
    id UUID PRIMARY KEY, hospital_profile_id UUID NOT NULL, department_code VARCHAR(128) NOT NULL,
    department_name VARCHAR(256) NOT NULL
);
MERGE INTO pis_v2.hospital_profile (id, profile_code) KEY(profile_code)
VALUES ('10000000-0000-0000-0000-000000000001', 'LOCAL_HOSPITAL');
MERGE INTO pis_v2.hospital_campus (id, hospital_profile_id, campus_code) KEY(id)
VALUES ('10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'MAIN');
MERGE INTO pis_v2.hospital_department (id, hospital_profile_id, department_code, department_name) KEY(id)
VALUES
('10000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'REGISTRATION', '登记组'),
('10000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000001', 'TECHNICAL', '技术组'),
('10000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000001', 'PATHOLOGY', '病理科'),
('10000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000001', 'ADMINISTRATION', '系统管理');

CREATE TABLE IF NOT EXISTS pis_v2.auth_user (
    id UUID PRIMARY KEY, username VARCHAR(128) NOT NULL UNIQUE, display_name VARCHAR(256) NOT NULL,
    password_digest VARCHAR(1024) NOT NULL, role_code VARCHAR(64) NOT NULL,
    hospital_scope VARCHAR(128) NOT NULL, department_scope VARCHAR(128), task_scope VARCHAR(2000),
    enabled BOOLEAN NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, hospital_profile_id UUID, campus_id UUID, department_id UUID
);
CREATE TABLE IF NOT EXISTS pis_v2.auth_user_permission (
    user_id UUID NOT NULL, permission_code VARCHAR(128) NOT NULL, PRIMARY KEY (user_id, permission_code)
);
CREATE TABLE IF NOT EXISTS pis_v2.doctor_identity (
    id UUID PRIMARY KEY, user_id UUID NOT NULL UNIQUE, doctor_code VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(256) NOT NULL, title VARCHAR(128), department VARCHAR(256), department_id UUID,
    enabled BOOLEAN NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.audit_event (
    id UUID PRIMARY KEY,
    operation_code VARCHAR(128), permission_code VARCHAR(64), actor_ref VARCHAR(128),
    subject_type_code VARCHAR(64), target_object_id UUID, target_object_kind_code VARCHAR(64),
    authorization_outcome VARCHAR(32), processing_outcome VARCHAR(64), correlation_id VARCHAR(128),
    reason VARCHAR(2000), category_code VARCHAR(32), changes_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE
);
CREATE TABLE IF NOT EXISTS pis.outbox_event (
    id UUID PRIMARY KEY,
    event_identity VARCHAR(128), event_type_code VARCHAR(128), subject_id UUID,
    subject_kind_code VARCHAR(64), aggregate_version BIGINT, correlation_id VARCHAR(128),
    payload_digest VARCHAR(128), publish_state_code VARCHAR(32), occurred_at TIMESTAMP WITH TIME ZONE,
    created_by_ref VARCHAR(128)
);

CREATE TABLE IF NOT EXISTS pis_v2.business_type (
    id UUID PRIMARY KEY, business_type_code VARCHAR(64) NOT NULL UNIQUE, display_name VARCHAR(200) NOT NULL,
    modality_code VARCHAR(64) NOT NULL, active BOOLEAN NOT NULL, configuration_version INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.application_item_mapping (
    id UUID PRIMARY KEY, application_item_code VARCHAR(128) NOT NULL UNIQUE, business_type_id UUID NOT NULL,
    default_specimen_kind_code VARCHAR(64), required BOOLEAN NOT NULL, sequence_no INTEGER NOT NULL,
    active BOOLEAN NOT NULL, configuration_version INTEGER NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.pathology_number_rule (
    id UUID PRIMARY KEY, business_type_id UUID NOT NULL, organization_reference VARCHAR(128) NOT NULL,
    number_kind_code VARCHAR(32) NOT NULL, prefix VARCHAR(32) NOT NULL, scope_code VARCHAR(32) NOT NULL,
    padding_width INTEGER NOT NULL, next_serial BIGINT NOT NULL, active BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (organization_reference, business_type_id, number_kind_code)
);
CREATE TABLE IF NOT EXISTS pis_v2.pathology_case (
    id UUID PRIMARY KEY, case_no VARCHAR(128) NOT NULL, source_system_code VARCHAR(128) NOT NULL,
    external_application_id VARCHAR(256) NOT NULL, application_item_code VARCHAR(128) NOT NULL,
    business_type_id UUID NOT NULL, lifecycle_state_code VARCHAR(32) NOT NULL,
    number_binding_active BOOLEAN NOT NULL, concurrency_version BIGINT NOT NULL,
    frozen_source_case_id UUID,
    organization_reference VARCHAR(128) NOT NULL, cancelled_at TIMESTAMP WITH TIME ZONE,
    cancelled_by_ref VARCHAR(128), cancellation_reason VARCHAR(2000), created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL, UNIQUE (organization_reference, case_no)
);
CREATE TABLE IF NOT EXISTS pis_v2.case_context_snapshot (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, patient_reference VARCHAR(256) NOT NULL,
    visit_reference VARCHAR(256), snapshot_version_no INTEGER NOT NULL, captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    captured_by_ref VARCHAR(128) NOT NULL, UNIQUE (case_id, snapshot_version_no)
);
CREATE TABLE IF NOT EXISTS pis_v2.specimen (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, specimen_no VARCHAR(128) NOT NULL,
    specimen_code VARCHAR(128) NOT NULL, specimen_name VARCHAR(500) NOT NULL,
    specimen_kind_code VARCHAR(64) NOT NULL, creation_source_code VARCHAR(32) NOT NULL,
    source_kind_code VARCHAR(64) NOT NULL, source_reference VARCHAR(256) NOT NULL,
    collection_site VARCHAR(500), collection_method_code VARCHAR(64), preparation_method_code VARCHAR(64),
    label_code VARCHAR(256), deleted_at TIMESTAMP WITH TIME ZONE, deleted_by_ref VARCHAR(128),
    deletion_reason VARCHAR(2000), concurrency_version BIGINT NOT NULL, organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_by_ref VARCHAR(128) NOT NULL,
    specimen_code_active VARCHAR(128) AS (CASE WHEN deleted_at IS NULL THEN specimen_code ELSE NULL END),
    label_code_active VARCHAR(256) AS (CASE WHEN deleted_at IS NULL THEN label_code ELSE NULL END),
    UNIQUE (organization_reference, specimen_no)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_test_specimen_code_active
    ON pis_v2.specimen (case_id, specimen_code_active);
CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_test_specimen_label_active
    ON pis_v2.specimen (organization_reference, label_code_active);
CREATE TABLE IF NOT EXISTS pis_v2.idempotency_record (
    id UUID PRIMARY KEY, operation_code VARCHAR(128) NOT NULL, idempotency_key VARCHAR(256) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL, result_kind_code VARCHAR(32) NOT NULL, result_case_id UUID,
    result_specimen_id UUID, created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (operation_code, idempotency_key)
);

CREATE TABLE IF NOT EXISTS pis_v2.grossing_sequence (
    organization_reference VARCHAR(128) NOT NULL, case_id UUID NOT NULL,
    next_serial BIGINT NOT NULL, PRIMARY KEY (organization_reference, case_id)
);
CREATE TABLE IF NOT EXISTS pis_v2.grossing (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, grossing_no VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL, source_reference_id UUID, gross_description VARCHAR(4000) NOT NULL,
    grossing_instruction VARCHAR(4000), grossing_doctor_id VARCHAR(128) NOT NULL, recorder_id VARCHAR(128) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL, completed_at TIMESTAMP WITH TIME ZONE,
    completed_by_ref VARCHAR(128), deleted_at TIMESTAMP WITH TIME ZONE, deleted_by_ref VARCHAR(128),
    deletion_reason VARCHAR(2000), concurrency_version BIGINT NOT NULL, organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (organization_reference, case_id, grossing_no)
);
CREATE TABLE IF NOT EXISTS pis_v2.grossing_specimen (
    grossing_id UUID NOT NULL, specimen_id UUID NOT NULL, sequence_no INTEGER NOT NULL,
    material_description VARCHAR(4000), concurrency_version BIGINT NOT NULL, deleted_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (grossing_id, specimen_id), UNIQUE (grossing_id, sequence_no)
);
CREATE TABLE IF NOT EXISTS pis_v2.block (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, grossing_id UUID, specimen_id UUID,
    block_code VARCHAR(64) NOT NULL, block_type VARCHAR(64) NOT NULL, external_source_flag BOOLEAN NOT NULL,
    external_source_reference VARCHAR(256), sampling_description VARCHAR(2000), quantity INTEGER NOT NULL,
    note VARCHAR(2000), deleted_at TIMESTAMP WITH TIME ZONE, deleted_by_ref VARCHAR(128),
    deletion_reason VARCHAR(2000), concurrency_version BIGINT NOT NULL, organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_by_ref VARCHAR(128) NOT NULL,
    block_code_active VARCHAR(64) AS (CASE WHEN deleted_at IS NULL THEN block_code ELSE NULL END)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_test_block_code_active
    ON pis_v2.block (case_id, block_code_active);
CREATE TABLE IF NOT EXISTS pis_v2.slide_rule (
    id UUID PRIMARY KEY, organization_reference VARCHAR(128) NOT NULL, business_type_id UUID NOT NULL,
    rule_code VARCHAR(64) NOT NULL, source_context_type VARCHAR(32) NOT NULL, trigger_code VARCHAR(64) NOT NULL,
    slide_type VARCHAR(64) NOT NULL, stain_code VARCHAR(64) NOT NULL, copies INTEGER NOT NULL,
    active BOOLEAN NOT NULL, configuration_version INTEGER NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (organization_reference, business_type_id, rule_code)
);
CREATE TABLE IF NOT EXISTS pis_v2.slide (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, block_id UUID, specimen_id UUID, slide_code VARCHAR(128) NOT NULL,
    slide_type VARCHAR(64) NOT NULL, stain_code VARCHAR(64), source_context_type VARCHAR(32) NOT NULL, source_context_id UUID,
    rule_code VARCHAR(64) NOT NULL, occurrence_no INTEGER NOT NULL, required BOOLEAN NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE, completed_by_ref VARCHAR(128), deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by_ref VARCHAR(128), deletion_reason VARCHAR(2000), concurrency_version BIGINT NOT NULL,
    organization_reference VARCHAR(128) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    slide_code_active VARCHAR(128) AS (CASE WHEN deleted_at IS NULL THEN slide_code ELSE NULL END)
);
CREATE TABLE IF NOT EXISTS pis_v2.material_process_fact (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, slide_id UUID, block_id UUID,
    target_kind_code VARCHAR(16) NOT NULL,
    phase_code VARCHAR(32) NOT NULL, started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE, operator_ref VARCHAR(128),
    device_reference VARCHAR(256), equipment_id UUID, batch_reference VARCHAR(256), stain_code VARCHAR(64),
    exception_code VARCHAR(64), exception_note VARCHAR(2000),
    exception_resolved_at TIMESTAMP WITH TIME ZONE, exception_resolved_by_ref VARCHAR(128),
    exception_resolution_note VARCHAR(2000),
    concurrency_version BIGINT NOT NULL, organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    target_identity UUID AS (COALESCE(block_id, slide_id)),
    UNIQUE (target_kind_code, target_identity, phase_code)
);
CREATE TABLE IF NOT EXISTS pis_v2.material_process_fact_correction (
    id UUID PRIMARY KEY, process_fact_id UUID NOT NULL, prior_completed_at TIMESTAMP WITH TIME ZONE,
    prior_operator_ref VARCHAR(128), prior_equipment_id UUID, prior_note VARCHAR(2000),
    corrected_completed_at TIMESTAMP WITH TIME ZONE, corrected_operator_ref VARCHAR(128),
    corrected_equipment_id UUID, corrected_note VARCHAR(2000), reason VARCHAR(2000) NOT NULL,
    corrected_at TIMESTAMP WITH TIME ZONE NOT NULL, corrected_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.slide_code_history (
    id UUID PRIMARY KEY, slide_id UUID NOT NULL, old_slide_code VARCHAR(128) NOT NULL,
    new_slide_code VARCHAR(128) NOT NULL, reason VARCHAR(2000) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL, changed_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.slide_completion_correction (
    id UUID PRIMARY KEY, slide_id UUID NOT NULL, prior_completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    prior_completed_by_ref VARCHAR(128) NOT NULL, reason VARCHAR(2000) NOT NULL,
    corrected_at TIMESTAMP WITH TIME ZONE NOT NULL, corrected_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);
ALTER TABLE pis_v2.block ADD IF NOT EXISTS destroyed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE pis_v2.block ADD IF NOT EXISTS destroyed_by_ref VARCHAR(128);
ALTER TABLE pis_v2.block ADD IF NOT EXISTS destruction_reason VARCHAR(2000);
ALTER TABLE pis_v2.block ADD IF NOT EXISTS destruction_batch_reference VARCHAR(256);
ALTER TABLE pis_v2.slide ADD IF NOT EXISTS destroyed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE pis_v2.slide ADD IF NOT EXISTS destroyed_by_ref VARCHAR(128);
ALTER TABLE pis_v2.slide ADD IF NOT EXISTS destruction_reason VARCHAR(2000);
ALTER TABLE pis_v2.slide ADD IF NOT EXISTS destruction_batch_reference VARCHAR(256);
ALTER TABLE pis_v2.specimen ADD IF NOT EXISTS preparation_method_code VARCHAR(64);
ALTER TABLE pis_v2.slide ADD IF NOT EXISTS stain_code VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_test_slide_code_active
    ON pis_v2.slide (case_id, slide_code_active);
CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_test_cytology_output
    ON pis_v2.slide (specimen_id, source_context_type, rule_code, occurrence_no);
CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_test_frozen_output
    ON pis_v2.slide (specimen_id, source_context_type, source_context_id, rule_code, occurrence_no);
CREATE TABLE IF NOT EXISTS pis_v2.print_rule (
    id UUID PRIMARY KEY, organization_reference VARCHAR(128) NOT NULL, business_type_id UUID,
    entity_kind_code VARCHAR(32) NOT NULL, trigger_code VARCHAR(64) NOT NULL, printer_profile_code VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL, configuration_version INTEGER NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.print_log (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, entity_kind_code VARCHAR(32) NOT NULL, entity_id UUID NOT NULL,
    business_code VARCHAR(128) NOT NULL, printer_profile_code VARCHAR(128) NOT NULL, operator_ref VARCHAR(128) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL, result_code VARCHAR(32) NOT NULL, failure_reason VARCHAR(2000)
);
CREATE TABLE IF NOT EXISTS pis_v2.material_command_idempotency (
    id UUID PRIMARY KEY, operation_code VARCHAR(128) NOT NULL, idempotency_key VARCHAR(256) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL, result_kind_code VARCHAR(64) NOT NULL, result_entity_id UUID,
    result_count INTEGER, created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (operation_code, idempotency_key)
);
CREATE TABLE IF NOT EXISTS pis_v2.diagnosis_template (
    id UUID PRIMARY KEY, organization_reference VARCHAR(128) NOT NULL, template_code VARCHAR(128) NOT NULL,
    template_name VARCHAR(256) NOT NULL, business_type_id UUID NOT NULL, scope_code VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL, concurrency_version BIGINT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL, UNIQUE (organization_reference, template_code)
);
CREATE TABLE IF NOT EXISTS pis_v2.diagnosis_template_version (
    id UUID PRIMARY KEY, template_id UUID NOT NULL, version_no INTEGER NOT NULL,
    schema_definition VARCHAR(20000) NOT NULL, status_code VARCHAR(32) NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE, published_by_ref VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    concurrency_version BIGINT NOT NULL, UNIQUE (template_id, version_no)
);
CREATE INDEX IF NOT EXISTS idx_v2_test_template_published
    ON pis_v2.diagnosis_template_version (template_id, status_code, version_no);
CREATE TABLE IF NOT EXISTS pis_v2.diagnosis (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, context_type VARCHAR(32) NOT NULL, context_id UUID NOT NULL,
    template_version_id UUID NOT NULL, structured_data VARCHAR(20000) NOT NULL,
    microscopic_description VARCHAR(10000), diagnosis_text VARCHAR(10000), comment_text VARCHAR(10000),
    concurrency_version BIGINT NOT NULL, organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (organization_reference, case_id, context_type, context_id)
);
CREATE INDEX IF NOT EXISTS idx_v2_test_diagnosis_case ON pis_v2.diagnosis (case_id, context_type);
CREATE TABLE IF NOT EXISTS pis_v2.responsibility_unit (
    id UUID PRIMARY KEY, diagnosis_id UUID NOT NULL, role_code VARCHAR(32) NOT NULL, doctor_id VARCHAR(128) NOT NULL,
    sequence_no INTEGER NOT NULL, accepted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE, ended_at TIMESTAMP WITH TIME ZONE, end_reason VARCHAR(2000),
    assignment_source_code VARCHAR(32) NOT NULL, assignment_reason VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    concurrency_version BIGINT NOT NULL, UNIQUE (diagnosis_id, sequence_no)
);
CREATE INDEX IF NOT EXISTS idx_v2_test_responsibility_open
    ON pis_v2.responsibility_unit (diagnosis_id, role_code, completed_at, ended_at);
CREATE TABLE IF NOT EXISTS pis_v2.assignment_rule (
    id UUID PRIMARY KEY, organization_reference VARCHAR(128) NOT NULL, campus_code VARCHAR(128) NOT NULL,
    business_type_code VARCHAR(64) NOT NULL, department_code VARCHAR(128) NOT NULL, site_code VARCHAR(256) NOT NULL,
    diagnosis_group_code VARCHAR(128) NOT NULL, doctor_id VARCHAR(128), priority INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL, concurrency_version BIGINT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.diagnosis_command_idempotency (
    id UUID PRIMARY KEY, operation_code VARCHAR(128) NOT NULL, idempotency_key VARCHAR(256) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL, result_kind_code VARCHAR(64) NOT NULL, result_entity_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (operation_code, idempotency_key)
);
CREATE TABLE IF NOT EXISTS pis_v2.technical_project (
    id UUID PRIMARY KEY, organization_reference VARCHAR(128) NOT NULL, business_type_id UUID NOT NULL,
    project_code VARCHAR(128) NOT NULL, project_name VARCHAR(256) NOT NULL,
    capability_code VARCHAR(64) NOT NULL DEFAULT 'OTHER_TECHNICAL',
    output_type_code VARCHAR(32) NOT NULL DEFAULT 'SLIDE', enabled BOOLEAN NOT NULL,
    allowed_target_types VARCHAR(512) NOT NULL, produces_slide BOOLEAN NOT NULL, produces_block BOOLEAN NOT NULL,
    produces_structured_result BOOLEAN NOT NULL, requires_result BOOLEAN NOT NULL DEFAULT FALSE,
    device_type_code VARCHAR(64), consumable_required BOOLEAN NOT NULL DEFAULT FALSE,
    default_slide_type VARCHAR(64), parameters_schema VARCHAR(20000),
    result_schema VARCHAR(20000), fee_mapping VARCHAR(20000), display_configuration VARCHAR(20000),
    required_before_sign_out_default BOOLEAN NOT NULL, configuration_version INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (organization_reference, business_type_id, project_code)
);
CREATE TABLE IF NOT EXISTS pis_v2.technical_order_sequence (
    organization_reference VARCHAR(128) NOT NULL, case_id UUID NOT NULL, next_serial BIGINT NOT NULL,
    PRIMARY KEY (organization_reference, case_id)
);
CREATE TABLE IF NOT EXISTS pis_v2.technical_order (
    id UUID PRIMARY KEY, organization_reference VARCHAR(128) NOT NULL, order_no VARCHAR(64) NOT NULL,
    diagnosis_id UUID NOT NULL, case_id UUID NOT NULL, required_before_sign_out BOOLEAN NOT NULL,
    status_code VARCHAR(32) NOT NULL, cancelled_at TIMESTAMP WITH TIME ZONE, cancelled_by_ref VARCHAR(128),
    cancellation_reason VARCHAR(2000), concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (organization_reference, case_id, order_no)
);
CREATE INDEX IF NOT EXISTS idx_v2_test_technical_order_diagnosis
    ON pis_v2.technical_order (diagnosis_id, status_code, created_at);
CREATE TABLE IF NOT EXISTS pis_v2.technical_order_item (
    id UUID PRIMARY KEY, order_id UUID NOT NULL, technical_project_id UUID NOT NULL,
    project_code_snapshot VARCHAR(128) NOT NULL, project_name_snapshot VARCHAR(256) NOT NULL,
    project_configuration_version INTEGER NOT NULL, quantity INTEGER NOT NULL, parameters VARCHAR(20000) NOT NULL,
    note VARCHAR(10000), cancelled_at TIMESTAMP WITH TIME ZONE, cancelled_by_ref VARCHAR(128),
    cancellation_reason VARCHAR(2000), concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.technical_order_target (
    id UUID PRIMARY KEY, item_id UUID NOT NULL, case_id UUID NOT NULL, target_type VARCHAR(32) NOT NULL,
    case_target_id UUID, specimen_target_id UUID, block_target_id UUID, slide_target_id UUID,
    target_display_code VARCHAR(256), concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    CHECK (
        (target_type = 'CASE' AND case_target_id IS NOT NULL AND specimen_target_id IS NULL AND block_target_id IS NULL AND slide_target_id IS NULL)
        OR (target_type = 'SPECIMEN' AND case_target_id IS NULL AND specimen_target_id IS NOT NULL AND block_target_id IS NULL AND slide_target_id IS NULL)
        OR (target_type = 'BLOCK' AND case_target_id IS NULL AND specimen_target_id IS NULL AND block_target_id IS NOT NULL AND slide_target_id IS NULL)
        OR (target_type = 'SLIDE' AND case_target_id IS NULL AND specimen_target_id IS NULL AND block_target_id IS NULL AND slide_target_id IS NOT NULL)
    )
);
CREATE TABLE IF NOT EXISTS pis_v2.technical_order_item_result (
    id UUID PRIMARY KEY, item_id UUID NOT NULL UNIQUE, result_schema_snapshot VARCHAR(20000),
    result_data VARCHAR(20000) NOT NULL, concurrency_version BIGINT NOT NULL,
    entered_at TIMESTAMP WITH TIME ZONE NOT NULL, entered_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.technical_order_output (
    id UUID PRIMARY KEY, item_id UUID NOT NULL, target_id UUID, output_kind VARCHAR(32) NOT NULL,
    grossing_output_id UUID, block_output_id UUID, slide_output_id UUID, result_output_id UUID,
    occurrence_no INTEGER NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (item_id, target_id, output_kind, occurrence_no),
    CHECK (
        (output_kind = 'GROSSING' AND grossing_output_id IS NOT NULL AND block_output_id IS NULL AND slide_output_id IS NULL AND result_output_id IS NULL)
        OR (output_kind = 'BLOCK' AND grossing_output_id IS NULL AND block_output_id IS NOT NULL AND slide_output_id IS NULL AND result_output_id IS NULL)
        OR (output_kind = 'SLIDE' AND grossing_output_id IS NULL AND block_output_id IS NULL AND slide_output_id IS NOT NULL AND result_output_id IS NULL)
        OR (output_kind = 'RESULT' AND grossing_output_id IS NULL AND block_output_id IS NULL AND slide_output_id IS NULL AND result_output_id IS NOT NULL)
    )
);
CREATE TABLE IF NOT EXISTS pis_v2.technical_order_idempotency (
    id UUID PRIMARY KEY, operation_code VARCHAR(128) NOT NULL, idempotency_key VARCHAR(256) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL, result_kind_code VARCHAR(64) NOT NULL, result_entity_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (operation_code, idempotency_key)
);

CREATE TABLE IF NOT EXISTS pis_v2.grossing_correction_history (
    id UUID PRIMARY KEY, grossing_id UUID NOT NULL, reason VARCHAR(2000) NOT NULL,
    prior_gross_description VARCHAR(4000) NOT NULL, corrected_gross_description VARCHAR(4000) NOT NULL,
    prior_instruction VARCHAR(4000), corrected_instruction VARCHAR(4000),
    corrected_at TIMESTAMP WITH TIME ZONE NOT NULL, corrected_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.grossing_specimen_correction_history (
    id UUID PRIMARY KEY, grossing_id UUID NOT NULL, specimen_id UUID NOT NULL,
    prior_description VARCHAR(4000), corrected_description VARCHAR(4000) NOT NULL,
    reason VARCHAR(2000) NOT NULL, corrected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    corrected_by_ref VARCHAR(128) NOT NULL, organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.block_code_history (
    id UUID PRIMARY KEY, block_id UUID NOT NULL, old_block_code VARCHAR(64) NOT NULL,
    new_block_code VARCHAR(64) NOT NULL, reason VARCHAR(2000) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL, changed_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.block_verification_policy (
    id UUID PRIMARY KEY, organization_reference VARCHAR(128) NOT NULL, business_type_id UUID NOT NULL,
    verification_required BOOLEAN NOT NULL, dual_check_required BOOLEAN NOT NULL,
    same_user_allowed BOOLEAN NOT NULL, configuration_version INTEGER NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (organization_reference, business_type_id)
);
CREATE TABLE IF NOT EXISTS pis_v2.block_verification (
    id UUID PRIMARY KEY, block_id UUID NOT NULL, verification_result_code VARCHAR(32) NOT NULL,
    verified_code VARCHAR(64) NOT NULL, verified_specimen_id UUID, verified_quantity INTEGER NOT NULL,
    reason VARCHAR(2000), verified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_by_ref VARCHAR(128) NOT NULL, organization_reference VARCHAR(128) NOT NULL
);

DELETE FROM pis_v2.idempotency_record;
DELETE FROM pis_v2.technical_order_idempotency;
DELETE FROM pis_v2.technical_order_output;
DELETE FROM pis_v2.technical_order_item_result;
DELETE FROM pis_v2.technical_order_target;
DELETE FROM pis_v2.technical_order_item;
DELETE FROM pis_v2.technical_order;
DELETE FROM pis_v2.technical_order_sequence;
DELETE FROM pis_v2.technical_project;
DELETE FROM pis_v2.diagnosis_command_idempotency;
DELETE FROM pis_v2.responsibility_unit;
DELETE FROM pis_v2.diagnosis;
DELETE FROM pis_v2.diagnosis_template_version;
DELETE FROM pis_v2.assignment_rule;
DELETE FROM pis_v2.diagnosis_template;
DELETE FROM pis_v2.material_command_idempotency;
DELETE FROM pis_v2.material_process_fact_correction;
DELETE FROM pis_v2.material_process_fact;
DELETE FROM pis_v2.slide_code_history;
DELETE FROM pis_v2.slide_completion_correction;
DELETE FROM pis_v2.print_log;
DELETE FROM pis_v2.slide;
DELETE FROM pis_v2.block_verification;
DELETE FROM pis_v2.block_code_history;
DELETE FROM pis_v2.block;
DELETE FROM pis_v2.grossing_specimen_correction_history;
DELETE FROM pis_v2.grossing_correction_history;
DELETE FROM pis_v2.grossing_specimen;
DELETE FROM pis_v2.grossing_sequence;
DELETE FROM pis_v2.grossing;
DELETE FROM pis_v2.print_rule;
DELETE FROM pis_v2.slide_rule;
DELETE FROM pis_v2.specimen;
DELETE FROM pis_v2.case_context_snapshot;
DELETE FROM pis_v2.pathology_case;
DELETE FROM pis_v2.pathology_number_rule;
DELETE FROM pis_v2.application_item_mapping;
DELETE FROM pis_v2.block_verification_policy;
DELETE FROM pis_v2.business_type;
DELETE FROM pis.audit_event;
DELETE FROM pis.outbox_event;

INSERT INTO pis_v2.business_type
    (id, business_type_code, display_name, modality_code, active, configuration_version, created_at, created_by_ref)
VALUES
    ('00000000-0000-0000-0000-00000000b001', 'HISTOLOGY', '组织病理', 'TISSUE', TRUE, 1,
     CURRENT_TIMESTAMP, 'TEST'),
    ('00000000-0000-0000-0000-00000000b101', 'FROZEN', '冰冻病理', 'FROZEN', TRUE, 1,
     CURRENT_TIMESTAMP, 'TEST'),
    ('00000000-0000-0000-0000-00000000b102', 'CYTOLOGY_NON_GYN', '非妇科细胞学', 'CYTOLOGY', TRUE, 1,
     CURRENT_TIMESTAMP, 'TEST'),
    ('00000000-0000-0000-0000-00000000b103', 'MOLECULAR', '分子病理', 'MOLECULAR', TRUE, 1,
     CURRENT_TIMESTAMP, 'TEST');
INSERT INTO pis_v2.diagnosis_template
    (id, organization_reference, template_code, template_name, business_type_id, scope_code, enabled,
     concurrency_version, created_at, created_by_ref, updated_at, updated_by_ref)
VALUES ('00000000-0000-0000-0000-00000000b020', 'LOCAL_HOSPITAL', 'DEFAULT-HISTOLOGY', 'V2默认诊断模板',
        '00000000-0000-0000-0000-00000000b001', 'LOCAL_HOSPITAL', TRUE, 0, CURRENT_TIMESTAMP, 'TEST',
        CURRENT_TIMESTAMP, 'TEST');
INSERT INTO pis_v2.diagnosis_template_version
    (id, template_id, version_no, schema_definition, status_code, published_at, published_by_ref,
     created_at, created_by_ref, concurrency_version)
VALUES ('00000000-0000-0000-0000-00000000b021', '00000000-0000-0000-0000-00000000b020', 1,
        '{"components":[{"type":"TEXTAREA","code":"diagnosisText"}],"version":1}', 'PUBLISHED',
        CURRENT_TIMESTAMP, 'TEST', CURRENT_TIMESTAMP, 'TEST', 0);
INSERT INTO pis_v2.technical_project
    (id, organization_reference, business_type_id, project_code, project_name, capability_code, output_type_code, enabled,
     allowed_target_types, produces_slide, produces_block, produces_structured_result, default_slide_type,
     requires_result, device_type_code, consumable_required, parameters_schema, result_schema, fee_mapping, display_configuration, required_before_sign_out_default,
     configuration_version, created_at, created_by_ref, updated_at, updated_by_ref)
VALUES
    ('00000000-0000-0000-0000-00000000b401', 'LOCAL_HOSPITAL', '00000000-0000-0000-0000-00000000b001',
     'IHC-KI67', 'Ki67免疫组化', 'IHC', 'SLIDE', TRUE, 'BLOCK,SLIDE', TRUE, FALSE, FALSE, 'IHC', FALSE, 'IHC_STAINER', TRUE,
     '{"fields":[{"code":"antibody","required":true,"type":"TEXT"}]}', NULL,
     '{"externalFeeCode":"SYNTH-IHC-KI67"}', '{"color":"amber"}', TRUE, 1,
     CURRENT_TIMESTAMP, 'TEST', CURRENT_TIMESTAMP, 'TEST'),
    ('00000000-0000-0000-0000-00000000b402', 'LOCAL_HOSPITAL', '00000000-0000-0000-0000-00000000b001',
     'SUPPLEMENTARY-GROSSING', '补充取材', 'SUPPLEMENTARY_GROSSING', 'MIXED', TRUE, 'CASE,SPECIMEN', TRUE, TRUE, FALSE, 'HE', FALSE, NULL, FALSE,
     '{"fields":[{"code":"specimenId","required":true,"type":"REFERENCE"},{"code":"blockCode","required":true,"type":"TEXT"}]}', NULL,
     '{"externalFeeCode":"SYNTH-SUPPLEMENTARY-GROSSING"}', '{"color":"green"}', TRUE, 1,
     CURRENT_TIMESTAMP, 'TEST', CURRENT_TIMESTAMP, 'TEST'),
    ('00000000-0000-0000-0000-00000000b403', 'LOCAL_HOSPITAL', '00000000-0000-0000-0000-00000000b001',
     'MOLECULAR-STRUCTURED', '结构化检测结果', 'MOLECULAR', 'RESULT', TRUE, 'CASE,SPECIMEN,BLOCK,SLIDE', FALSE, FALSE, TRUE, NULL, TRUE, NULL, FALSE,
     '{"fields":[{"code":"panel","required":true,"type":"TEXT"}]}',
     '{"fields":[{"code":"mutationDetected","type":"BOOLEAN"},{"code":"interpretation","type":"TEXTAREA"}]}',
     '{"externalFeeCode":"SYNTH-MOLECULAR"}', '{"color":"blue"}', TRUE, 1,
     CURRENT_TIMESTAMP, 'TEST', CURRENT_TIMESTAMP, 'TEST');
INSERT INTO pis_v2.application_item_mapping
    (id, application_item_code, business_type_id, default_specimen_kind_code, required, sequence_no,
     active, configuration_version, created_at, created_by_ref)
VALUES
    ('00000000-0000-0000-0000-00000000b002', 'SYNTH-HISTOLOGY',
     '00000000-0000-0000-0000-00000000b001', 'TISSUE', TRUE, 1, TRUE, 1, CURRENT_TIMESTAMP, 'TEST'),
    ('00000000-0000-0000-0000-00000000b112', 'SYNTH-FROZEN',
     '00000000-0000-0000-0000-00000000b101', 'TISSUE', TRUE, 2, TRUE, 1, CURRENT_TIMESTAMP, 'TEST'),
    ('00000000-0000-0000-0000-00000000b113', 'SYNTH-CYTOLOGY',
     '00000000-0000-0000-0000-00000000b102', 'FLUID', TRUE, 3, TRUE, 1, CURRENT_TIMESTAMP, 'TEST'),
    ('00000000-0000-0000-0000-00000000b114', 'SYNTH-MOLECULAR',
     '00000000-0000-0000-0000-00000000b103', 'TISSUE', TRUE, 4, TRUE, 1, CURRENT_TIMESTAMP, 'TEST');
INSERT INTO pis_v2.pathology_number_rule
    (id, business_type_id, organization_reference, number_kind_code, prefix, scope_code, padding_width,
     next_serial, active, configuration_version, created_at, updated_at, created_by_ref)
VALUES
    ('00000000-0000-0000-0000-00000000b003', '00000000-0000-0000-0000-00000000b001', 'LOCAL_HOSPITAL',
     'CASE', 'H-', 'ORGANIZATION', 6, 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TEST'),
    ('00000000-0000-0000-0000-00000000b004', '00000000-0000-0000-0000-00000000b001', 'LOCAL_HOSPITAL',
     'SPECIMEN', 'HS-', 'ORGANIZATION', 7, 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TEST'),
    ('00000000-0000-0000-0000-00000000b121', '00000000-0000-0000-0000-00000000b101', 'LOCAL_HOSPITAL',
     'CASE', 'F-', 'ORGANIZATION', 6, 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TEST'),
    ('00000000-0000-0000-0000-00000000b122', '00000000-0000-0000-0000-00000000b101', 'LOCAL_HOSPITAL',
     'SPECIMEN', 'FS-', 'ORGANIZATION', 7, 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TEST'),
    ('00000000-0000-0000-0000-00000000b123', '00000000-0000-0000-0000-00000000b102', 'LOCAL_HOSPITAL',
     'CASE', 'C-', 'ORGANIZATION', 6, 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TEST'),
    ('00000000-0000-0000-0000-00000000b124', '00000000-0000-0000-0000-00000000b102', 'LOCAL_HOSPITAL',
     'SPECIMEN', 'CS-', 'ORGANIZATION', 7, 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TEST'),
    ('00000000-0000-0000-0000-00000000b125', '00000000-0000-0000-0000-00000000b103', 'LOCAL_HOSPITAL',
     'CASE', 'M-', 'ORGANIZATION', 6, 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TEST'),
    ('00000000-0000-0000-0000-00000000b126', '00000000-0000-0000-0000-00000000b103', 'LOCAL_HOSPITAL',
     'SPECIMEN', 'MS-', 'ORGANIZATION', 7, 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TEST');
INSERT INTO pis_v2.slide_rule
    (id, organization_reference, business_type_id, rule_code, source_context_type, trigger_code,
     slide_type, stain_code, copies, active, configuration_version, created_at, updated_at, created_by_ref)
VALUES ('00000000-0000-0000-0000-00000000b010', 'LOCAL_HOSPITAL',
        '00000000-0000-0000-0000-00000000b001', 'INITIAL-HE', 'INITIAL', 'ON_GROSSING_COMPLETE',
        'HE', 'HE', 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TEST');
INSERT INTO pis_v2.print_rule
    (id, organization_reference, business_type_id, entity_kind_code, trigger_code, printer_profile_code,
     active, configuration_version, created_at, updated_at, created_by_ref)
VALUES ('00000000-0000-0000-0000-00000000b011', 'LOCAL_HOSPITAL', NULL, 'SLIDE', 'ON_GROSSING_COMPLETE',
        'MOCK://SYNTH-PRINTER', TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TEST');

CREATE TABLE IF NOT EXISTS pis_v2.report_template (
    id UUID PRIMARY KEY, organization_reference VARCHAR(128) NOT NULL, business_type_id UUID NOT NULL,
    template_code VARCHAR(128) NOT NULL, template_name VARCHAR(256) NOT NULL, enabled BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL, UNIQUE (organization_reference, template_code)
);
CREATE TABLE IF NOT EXISTS pis_v2.report_template_version (
    id UUID PRIMARY KEY, template_id UUID NOT NULL, version_no INTEGER NOT NULL, definition VARCHAR(20000) NOT NULL,
    status_code VARCHAR(32) NOT NULL, published_at TIMESTAMP WITH TIME ZONE, published_by_ref VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    concurrency_version BIGINT NOT NULL, UNIQUE (template_id, version_no)
);
CREATE TABLE IF NOT EXISTS pis_v2.report (
    id UUID PRIMARY KEY, report_no VARCHAR(64) NOT NULL, organization_reference VARCHAR(128) NOT NULL,
    case_id UUID NOT NULL, diagnosis_id UUID NOT NULL, template_version_id UUID NOT NULL,
    report_nature_code VARCHAR(32) NOT NULL, prior_report_id UUID, status_code VARCHAR(32) NOT NULL,
    diagnosis_snapshot VARCHAR(20000) NOT NULL, responsibility_snapshot VARCHAR(20000) NOT NULL,
    case_snapshot VARCHAR(20000) NOT NULL, material_snapshot VARCHAR(50000) NOT NULL,
    technical_result_snapshot VARCHAR(50000) NOT NULL, supplemental_content VARCHAR(10000),
    rendered_content VARCHAR(100000) NOT NULL, rendered_content_hash VARCHAR(128) NOT NULL,
    pdf_file_reference VARCHAR(256) NOT NULL, pdf_content_hash VARCHAR(128) NOT NULL,
    signed_by_ref VARCHAR(128) NOT NULL, signed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    withdrawn_by_ref VARCHAR(128), withdrawn_at TIMESTAMP WITH TIME ZONE, withdrawal_reason VARCHAR(2000),
    concurrency_version BIGINT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL, UNIQUE (organization_reference, case_id, report_no)
);
CREATE TABLE IF NOT EXISTS pis_v2.report_pdf_output (
    id UUID PRIMARY KEY, report_id UUID NOT NULL UNIQUE, file_reference VARCHAR(256) NOT NULL UNIQUE,
    content VARBINARY NOT NULL, content_hash VARCHAR(128) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.report_command_idempotency (
    id UUID PRIMARY KEY, operation_code VARCHAR(128) NOT NULL, idempotency_key VARCHAR(256) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL, result_report_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (operation_code, idempotency_key)
);
CREATE TABLE IF NOT EXISTS pis_v2.digital_slide (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, block_id UUID, slide_id UUID,
    binding_mode_code VARCHAR(32) NOT NULL, status_code VARCHAR(32) NOT NULL,
    viewer_reference VARCHAR(512) NOT NULL, source_platform VARCHAR(256) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.archive_location (
    id UUID PRIMARY KEY, parent_id UUID, location_code VARCHAR(128) NOT NULL,
    location_name VARCHAR(256) NOT NULL, location_kind_code VARCHAR(64) NOT NULL, active BOOLEAN NOT NULL,
    organization_reference VARCHAR(128) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL, UNIQUE (organization_reference, location_code)
);
CREATE TABLE IF NOT EXISTS pis_v2.material_archive_history (
    id UUID PRIMARY KEY, block_id UUID, slide_id UUID, location_id UUID NOT NULL,
    event_code VARCHAR(32) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    occurred_by_ref VARCHAR(128) NOT NULL, reason VARCHAR(2000)
);
CREATE TABLE IF NOT EXISTS pis_v2.block_archive_current (
    block_id UUID PRIMARY KEY, location_id UUID NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.slide_archive_current (
    slide_id UUID PRIMARY KEY, location_id UUID NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.loan (
    id UUID PRIMARY KEY, borrower_reference VARCHAR(256) NOT NULL, purpose VARCHAR(2000) NOT NULL,
    status_code VARCHAR(32) NOT NULL, borrowed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    borrowed_by_ref VARCHAR(128) NOT NULL, returned_at TIMESTAMP WITH TIME ZONE,
    returned_by_ref VARCHAR(128), organization_reference VARCHAR(128) NOT NULL,
    borrower_department VARCHAR(256), expected_return_at TIMESTAMP WITH TIME ZONE
);
CREATE TABLE IF NOT EXISTS pis_v2.loan_item (
    id UUID PRIMARY KEY, loan_id UUID NOT NULL, block_id UUID, slide_id UUID,
    returned_at TIMESTAMP WITH TIME ZONE, returned_by_ref VARCHAR(128)
);
CREATE TABLE IF NOT EXISTS pis_v2.material_destruction (
    id UUID PRIMARY KEY, block_id UUID, slide_id UUID, destroyed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    destroyed_by_ref VARCHAR(128) NOT NULL, reason VARCHAR(2000) NOT NULL, batch_reference VARCHAR(256) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.custody_command_idempotency (
    id UUID PRIMARY KEY, operation_code VARCHAR(128) NOT NULL, idempotency_key VARCHAR(256) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL, result_entity_id UUID, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL, UNIQUE (operation_code, idempotency_key)
);
CREATE TABLE IF NOT EXISTS pis_v2.qc_rule (
    id UUID PRIMARY KEY, rule_code VARCHAR(128) NOT NULL UNIQUE, rule_name VARCHAR(256) NOT NULL,
    metric_code VARCHAR(128) NOT NULL, warning_threshold NUMERIC(18,6) NOT NULL,
    overdue_threshold NUMERIC(18,6) NOT NULL, active BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.qc_evaluation (
    id UUID PRIMARY KEY, rule_id UUID NOT NULL, case_id UUID, measure_value NUMERIC(18,6) NOT NULL,
    status_code VARCHAR(32) NOT NULL, evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    evaluated_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.molecular_result (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, specimen_id UUID, result_code VARCHAR(128) NOT NULL,
    result_data VARCHAR(50000) NOT NULL, status_code VARCHAR(32) NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL, completed_by_ref VARCHAR(128) NOT NULL,
    concurrency_version BIGINT NOT NULL, organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.molecular_result_idempotency (
    id UUID PRIMARY KEY, operation_code VARCHAR(128) NOT NULL, idempotency_key VARCHAR(128) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL, result_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (operation_code, idempotency_key)
);
CREATE TABLE IF NOT EXISTS pis_v2.send_out (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, external_reference VARCHAR(256) NOT NULL,
    destination_name VARCHAR(256) NOT NULL, status_code VARCHAR(32) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL, requested_by_ref VARCHAR(128) NOT NULL,
    result_data VARCHAR(50000), result_received_at TIMESTAMP WITH TIME ZONE,
    result_received_by_ref VARCHAR(128), organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (organization_reference, external_reference)
);
CREATE TABLE IF NOT EXISTS pis_v2.send_out_idempotency (
    id UUID PRIMARY KEY, operation_code VARCHAR(128) NOT NULL, idempotency_key VARCHAR(128) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL, send_out_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (operation_code, idempotency_key)
);

DELETE FROM pis_v2.report_command_idempotency;
DELETE FROM pis_v2.report_pdf_output;
DELETE FROM pis_v2.report;
DELETE FROM pis_v2.report_template_version;
DELETE FROM pis_v2.report_template;
INSERT INTO pis_v2.report_template
    (id, organization_reference, business_type_id, template_code, template_name, enabled,
     configuration_version, created_at, created_by_ref, updated_at, updated_by_ref)
VALUES ('00000000-0000-0000-0000-00000000b501', 'LOCAL_HOSPITAL', '00000000-0000-0000-0000-00000000b001',
        'DEFAULT-REPORT-HISTOLOGY', 'V2报告模板', TRUE, 1, CURRENT_TIMESTAMP, 'TEST', CURRENT_TIMESTAMP, 'TEST');
INSERT INTO pis_v2.report_template_version
    (id, template_id, version_no, definition, status_code, published_at, published_by_ref,
     created_at, created_by_ref, concurrency_version)
VALUES ('00000000-0000-0000-0000-00000000b502', '00000000-0000-0000-0000-00000000b501', 1,
        '{"sections":["CASE","PATIENT","MATERIAL","DIAGNOSIS","RESPONSIBILITY","TECHNICAL_RESULTS","SIGN_OUT"]}',
        'PUBLISHED', CURRENT_TIMESTAMP, 'TEST', CURRENT_TIMESTAMP, 'TEST', 0);

CREATE TABLE IF NOT EXISTS pis_v2.frozen_round (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, round_no INTEGER NOT NULL,
    status_code VARCHAR(32) NOT NULL, arrival_time TIMESTAMP WITH TIME ZONE NOT NULL,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL, grossing_start_time TIMESTAMP WITH TIME ZONE,
    slide_completed_time TIMESTAMP WITH TIME ZONE, diagnosis_signed_time TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE, ended_by_ref VARCHAR(128), cancelled_at TIMESTAMP WITH TIME ZONE,
    cancelled_by_ref VARCHAR(128), cancellation_reason VARCHAR(2000), concurrency_version BIGINT NOT NULL,
    organization_reference VARCHAR(128) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL, UNIQUE (case_id, round_no)
);
CREATE TABLE IF NOT EXISTS pis_v2.frozen_round_specimen (
    frozen_round_id UUID NOT NULL, specimen_id UUID NOT NULL, sequence_no INTEGER NOT NULL,
    linked_at TIMESTAMP WITH TIME ZONE NOT NULL, linked_by_ref VARCHAR(128) NOT NULL,
    PRIMARY KEY (frozen_round_id, specimen_id), UNIQUE (frozen_round_id, sequence_no)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_frozen_round_specimen_global
    ON pis_v2.frozen_round_specimen (specimen_id);
CREATE TABLE IF NOT EXISTS pis_v2.frozen_end (
    id UUID PRIMARY KEY, frozen_case_id UUID NOT NULL UNIQUE, routine_case_id UUID NOT NULL UNIQUE,
    idempotency_key VARCHAR(256) NOT NULL UNIQUE, ended_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.frozen_end_specimen (
    id UUID PRIMARY KEY, frozen_end_id UUID NOT NULL, frozen_specimen_id UUID NOT NULL,
    routine_specimen_id UUID NOT NULL, frozen_round_id UUID NOT NULL,
    organization_reference VARCHAR(128) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL, UNIQUE (frozen_end_id, frozen_specimen_id),
    UNIQUE (routine_specimen_id)
);
CREATE TABLE IF NOT EXISTS pis_v2.frozen_tat_alert_action (
    id UUID PRIMARY KEY, frozen_round_id UUID NOT NULL, organization_reference VARCHAR(128) NOT NULL,
    tat_status_code VARCHAR(16) NOT NULL, action_code VARCHAR(32) NOT NULL, note VARCHAR(1000),
    acted_at TIMESTAMP WITH TIME ZONE NOT NULL, acted_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (frozen_round_id, organization_reference, tat_status_code, action_code)
);
MERGE INTO pis_v2.qc_rule
    (id, rule_code, rule_name, metric_code, warning_threshold, overdue_threshold, active, created_at, created_by_ref)
KEY (rule_code) VALUES
    ('00000000-0000-0000-0000-00000000c390', 'FROZEN_TAT', '冰冻 TAT', 'FROZEN_TAT_HOURS', 1, 2, TRUE,
     CURRENT_TIMESTAMP, 'TEST');
CREATE TABLE IF NOT EXISTS pis_v2.integration_message_log (
    id UUID PRIMARY KEY, hospital_profile_code VARCHAR(128) NOT NULL, direction_code VARCHAR(16) NOT NULL,
    source_system_code VARCHAR(128) NOT NULL, target_system_code VARCHAR(128) NOT NULL,
    message_id VARCHAR(256) NOT NULL, capability_code VARCHAR(64) NOT NULL, business_key VARCHAR(256) NOT NULL,
    request_reference VARCHAR(1024) NOT NULL, request_digest VARCHAR(128) NOT NULL, response_summary VARCHAR(2000),
    status_code VARCHAR(32) NOT NULL, error_code VARCHAR(128), error_message VARCHAR(2000), retry_count INTEGER NOT NULL,
    max_retries INTEGER NOT NULL, next_retry_at TIMESTAMP WITH TIME ZONE, last_attempt_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (hospital_profile_code, source_system_code, target_system_code, message_id)
);
CREATE TABLE IF NOT EXISTS pis_v2.integration_attempt (
    id UUID PRIMARY KEY, message_log_id UUID NOT NULL, attempt_no INTEGER NOT NULL, adapter_code VARCHAR(128) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL, completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    result_code VARCHAR(32) NOT NULL, response_summary VARCHAR(2000), error_code VARCHAR(128),
    error_message VARCHAR(2000), retryable BOOLEAN NOT NULL, UNIQUE (message_log_id, attempt_no)
);
CREATE TABLE IF NOT EXISTS pis_v2.integration_dead_letter (
    id UUID PRIMARY KEY, message_log_id UUID NOT NULL UNIQUE, reason VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by_ref VARCHAR(128), resolution VARCHAR(2000)
);

CREATE TABLE IF NOT EXISTS pis_v2.pathology_application (
    id UUID PRIMARY KEY, application_no VARCHAR(128) NOT NULL, source_type_code VARCHAR(32) NOT NULL,
    source_system_code VARCHAR(128) NOT NULL, patient_reference VARCHAR(256) NOT NULL, patient_name VARCHAR(256),
    patient_sex_code VARCHAR(32), patient_birth_date DATE, visit_reference VARCHAR(256), visit_type_code VARCHAR(32),
    application_department VARCHAR(256), applicant_reference VARCHAR(256), applied_at TIMESTAMP WITH TIME ZONE NOT NULL,
    clinical_diagnosis VARCHAR(4000), medical_history VARCHAR(10000), operation_finding VARCHAR(10000),
    examination_purpose VARCHAR(4000), specimen_description VARCHAR(10000), note VARCHAR(10000),
    status_code VARCHAR(32) NOT NULL, cancelled_at TIMESTAMP WITH TIME ZONE, cancelled_by_ref VARCHAR(128),
    cancellation_reason VARCHAR(2000), organization_reference VARCHAR(128) NOT NULL, concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (organization_reference, application_no)
);
CREATE TABLE IF NOT EXISTS pis_v2.pathology_application_item (
    id UUID PRIMARY KEY, application_id UUID NOT NULL, external_item_code VARCHAR(128) NOT NULL,
    item_name VARCHAR(256), mapping_id UUID, business_type_id UUID, specimen_kind_code VARCHAR(64),
    specimen_description VARCHAR(4000), sequence_no INTEGER NOT NULL, status_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.pathology_application_case (
    id UUID PRIMARY KEY, application_id UUID NOT NULL, application_item_id UUID NOT NULL UNIQUE,
    case_id UUID NOT NULL UNIQUE, linked_at TIMESTAMP WITH TIME ZONE NOT NULL, linked_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.pathology_application_delivery (
    id UUID PRIMARY KEY, application_id UUID NOT NULL, application_item_id UUID, specimen_label_code VARCHAR(256),
    patient_reference VARCHAR(256) NOT NULL, actual_specimen_description VARCHAR(10000),
    verification_status_code VARCHAR(32) NOT NULL, rejection_reason VARCHAR(2000), delivered_by_ref VARCHAR(128) NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE NOT NULL, organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.pathology_application_barcode_print (
    id UUID PRIMARY KEY, application_id UUID NOT NULL, application_item_id UUID, barcode_value VARCHAR(256) NOT NULL,
    print_version INTEGER NOT NULL, printer_profile_code VARCHAR(128) NOT NULL, result_code VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(2000), requested_at TIMESTAMP WITH TIME ZONE NOT NULL, requested_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.pathology_registration_receipt_print (
    id UUID PRIMARY KEY, application_id UUID NOT NULL, case_id UUID, receipt_kind_code VARCHAR(32) NOT NULL,
    printer_profile_code VARCHAR(128) NOT NULL, result_code VARCHAR(32) NOT NULL, failure_reason VARCHAR(2000),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL, requested_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis_v2.grossing_image (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, grossing_id UUID NOT NULL, specimen_id UUID,
    image_name VARCHAR(256) NOT NULL, media_type VARCHAR(128) NOT NULL, storage_reference VARCHAR(1024) NOT NULL,
    metadata_json VARCHAR(20000), captured_at TIMESTAMP WITH TIME ZONE NOT NULL, captured_by_ref VARCHAR(128) NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE, deleted_by_ref VARCHAR(128), deletion_reason VARCHAR(2000),
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.grossing_image_annotation (
    id UUID PRIMARY KEY, image_id UUID NOT NULL, annotation_type_code VARCHAR(32) NOT NULL,
    geometry_json VARCHAR(20000) NOT NULL, label VARCHAR(256), note VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_by_ref VARCHAR(128) NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE, deleted_by_ref VARCHAR(128)
);
CREATE TABLE IF NOT EXISTS pis_v2.grossing_image_measurement (
    id UUID PRIMARY KEY, image_id UUID NOT NULL, geometry_json VARCHAR(20000) NOT NULL,
    "value" NUMERIC(18,6) NOT NULL, unit_code VARCHAR(32) NOT NULL, measurement_mode_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL
);

DELETE FROM pis_v2.grossing_image_annotation;
DELETE FROM pis_v2.grossing_image_measurement;
DELETE FROM pis_v2.grossing_image;

ALTER TABLE pis_v2.specimen ADD IF NOT EXISTS laterality_code VARCHAR(32);
ALTER TABLE pis_v2.specimen ADD IF NOT EXISTS quantity_value NUMERIC(12,3);
ALTER TABLE pis_v2.specimen ADD IF NOT EXISTS quantity_unit_code VARCHAR(32);
ALTER TABLE pis_v2.specimen ADD IF NOT EXISTS description VARCHAR(4000);
ALTER TABLE pis_v2.specimen ADD IF NOT EXISTS removed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE pis_v2.specimen ADD IF NOT EXISTS fixed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE pis_v2.specimen ADD IF NOT EXISTS received_at TIMESTAMP WITH TIME ZONE;
CREATE TABLE IF NOT EXISTS pis_v2.specimen_receiving_fact (
    id UUID PRIMARY KEY, specimen_id UUID NOT NULL, verification_code VARCHAR(64) NOT NULL,
    actual_description VARCHAR(4000), reason VARCHAR(2000), received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_by_ref VARCHAR(128) NOT NULL, organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.specimen_split (
    id UUID PRIMARY KEY, source_specimen_id UUID NOT NULL, child_specimen_id UUID NOT NULL UNIQUE,
    quantity_value NUMERIC(12,3), reason VARCHAR(2000) NOT NULL, organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL
);

DELETE FROM pis_v2.specimen_receiving_fact;
DELETE FROM pis_v2.specimen_split;

MERGE INTO pis_v2.block_verification_policy
    (id, organization_reference, business_type_id, verification_required, dual_check_required,
     same_user_allowed, configuration_version, updated_at, updated_by_ref)
KEY (organization_reference, business_type_id)
SELECT RANDOM_UUID(), 'LOCAL_HOSPITAL', id, FALSE, FALSE, TRUE, 1, CURRENT_TIMESTAMP, 'TEST-SCHEMA'
FROM pis_v2.business_type;

CREATE TABLE IF NOT EXISTS pis_v2.material_rework (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, original_slide_id UUID NOT NULL,
    rework_type_code VARCHAR(64) NOT NULL, reason VARCHAR(2000) NOT NULL,
    status_code VARCHAR(32) NOT NULL, requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    requested_by_ref VARCHAR(128) NOT NULL, replacement_slide_id UUID,
    completed_at TIMESTAMP WITH TIME ZONE, completed_by_ref VARCHAR(128),
    organization_reference VARCHAR(128) NOT NULL, concurrency_version BIGINT NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS pis_v2.digital_slide_annotation (
    id UUID PRIMARY KEY, digital_slide_id UUID NOT NULL, annotation_type_code VARCHAR(32) NOT NULL,
    geometry_json VARCHAR(20000) NOT NULL, label VARCHAR(256), note VARCHAR(4000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_by_ref VARCHAR(128) NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE, organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.digital_slide_measurement (
    id UUID PRIMARY KEY, digital_slide_id UUID NOT NULL, geometry_json VARCHAR(20000) NOT NULL,
    measurement_value NUMERIC(18,6) NOT NULL, unit_code VARCHAR(32) NOT NULL, measurement_mode_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.digital_slide_screenshot (
    id UUID PRIMARY KEY, digital_slide_id UUID NOT NULL, viewport_json VARCHAR(20000) NOT NULL,
    storage_reference VARCHAR(1024) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL, organization_reference VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis_v2.case_favorite (
    case_id UUID NOT NULL, user_reference VARCHAR(256) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (case_id, user_reference, organization_reference)
);
CREATE TABLE IF NOT EXISTS pis_v2.case_follow_up (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, follow_up_date DATE NOT NULL,
    plan VARCHAR(4000) NOT NULL, content VARCHAR(10000), result VARCHAR(10000),
    operator_ref VARCHAR(128) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE, organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.case_consultation (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, consultation_at TIMESTAMP WITH TIME ZONE NOT NULL,
    initiator_ref VARCHAR(128) NOT NULL, participant_refs VARCHAR(4000) NOT NULL, reason VARCHAR(4000) NOT NULL,
    discussion VARCHAR(10000), conclusion VARCHAR(10000), note VARCHAR(4000), attachment_reference VARCHAR(1024),
    recorded_by_ref VARCHAR(128) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis_v2.notification (
    id UUID PRIMARY KEY, recipient_reference VARCHAR(256) NOT NULL, type_code VARCHAR(64) NOT NULL,
    title VARCHAR(512) NOT NULL, body VARCHAR(4000) NOT NULL, business_path VARCHAR(1024),
    priority_code VARCHAR(32) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE, organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.staff_schedule (
    id UUID PRIMARY KEY, staff_reference VARCHAR(256) NOT NULL, schedule_date DATE NOT NULL,
    shift_code VARCHAR(64) NOT NULL, work_area VARCHAR(256) NOT NULL, note VARCHAR(2000),
    organization_reference VARCHAR(128) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.quality_document (
    id UUID PRIMARY KEY, title VARCHAR(512) NOT NULL, document_no VARCHAR(128) NOT NULL,
    category_code VARCHAR(64) NOT NULL, version_label VARCHAR(64) NOT NULL,
    effective_at TIMESTAMP WITH TIME ZONE, owner_reference VARCHAR(256) NOT NULL,
    status_code VARCHAR(32) NOT NULL, content_reference VARCHAR(1024) NOT NULL,
    previous_document_id UUID, organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE, reviewed_by_ref VARCHAR(128), archived_at TIMESTAMP WITH TIME ZONE
);
CREATE TABLE IF NOT EXISTS pis_v2.equipment (
    id UUID PRIMARY KEY, equipment_code VARCHAR(128) NOT NULL, name VARCHAR(256) NOT NULL,
    category_code VARCHAR(128) NOT NULL, manufacturer VARCHAR(256), model VARCHAR(256), serial_no VARCHAR(256),
    location_reference VARCHAR(256), custodian_reference VARCHAR(256), purchase_date DATE, warranty_until DATE,
    calibration_due_at DATE, status_code VARCHAR(32) NOT NULL, organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.equipment_event (
    id UUID PRIMARY KEY, equipment_id UUID NOT NULL, event_code VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, operator_reference VARCHAR(256) NOT NULL,
    description VARCHAR(4000), amount NUMERIC(18,2), organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.consumable_catalog (
    id UUID PRIMARY KEY, material_code VARCHAR(128) NOT NULL, name VARCHAR(256) NOT NULL,
    category_code VARCHAR(128) NOT NULL, specification VARCHAR(512), unit_code VARCHAR(64) NOT NULL,
    manufacturer VARCHAR(256), supplier VARCHAR(256), hazardous BOOLEAN NOT NULL, active BOOLEAN NOT NULL,
    organization_reference VARCHAR(128) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.consumable_batch (
    id UUID PRIMARY KEY, catalog_id UUID NOT NULL, batch_no VARCHAR(128) NOT NULL, expiry_date DATE,
    storage_location VARCHAR(256), organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.consumable_transaction (
    id UUID PRIMARY KEY, batch_id UUID NOT NULL, direction_code VARCHAR(32) NOT NULL,
    quantity NUMERIC(18,3) NOT NULL, reason VARCHAR(2000) NOT NULL, source_reference VARCHAR(256),
    operator_reference VARCHAR(256) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.consumable_requisition (
    id UUID PRIMARY KEY, request_no VARCHAR(128) NOT NULL, requester_reference VARCHAR(256) NOT NULL,
    department_reference VARCHAR(256) NOT NULL, purpose VARCHAR(2000) NOT NULL, status_code VARCHAR(32) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL, decided_at TIMESTAMP WITH TIME ZONE,
    decided_by_ref VARCHAR(128), organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.consumable_requisition_item (
    id UUID PRIMARY KEY, requisition_id UUID NOT NULL, catalog_id UUID NOT NULL, quantity NUMERIC(18,3) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.consumable_quality_evaluation (
    id UUID PRIMARY KEY, batch_id UUID NOT NULL, result_code VARCHAR(32) NOT NULL, note VARCHAR(2000),
    evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL, evaluated_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.technical_order_device_attempt (
    id UUID PRIMARY KEY, item_id UUID NOT NULL, device_type_code VARCHAR(64) NOT NULL,
    adapter_code VARCHAR(128) NOT NULL, request_reference VARCHAR(256) NOT NULL,
    status_code VARCHAR(32) NOT NULL, retry_count INTEGER NOT NULL, error_code VARCHAR(128),
    error_message VARCHAR(2000), requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE, organization_reference VARCHAR(128) NOT NULL,
    UNIQUE (item_id, request_reference)
);
CREATE TABLE IF NOT EXISTS pis_v2.technical_order_quality_evaluation (
    id UUID PRIMARY KEY, item_id UUID NOT NULL, technical_output_id UUID, output_id UUID, result_code VARCHAR(32) NOT NULL,
    score NUMERIC(18,6), note VARCHAR(2000), evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    evaluated_by_ref VARCHAR(128) NOT NULL, organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.technical_order_fee_status (
    id UUID PRIMARY KEY, item_id UUID NOT NULL UNIQUE, status_code VARCHAR(32) NOT NULL,
    external_reference VARCHAR(256), failure_reason VARCHAR(2000), updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL, organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.technical_order_consumption (
    id UUID PRIMARY KEY, item_id UUID NOT NULL, consumable_batch_id UUID NOT NULL,
    quantity NUMERIC(18,3) NOT NULL, unit_code VARCHAR(64) NOT NULL, reason VARCHAR(2000) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, occurred_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.technical_order_label_print (
    id UUID PRIMARY KEY, item_id UUID NOT NULL, technical_output_id UUID NOT NULL, output_id UUID NOT NULL,
    output_kind VARCHAR(32) NOT NULL,
    print_version INTEGER NOT NULL, label_code VARCHAR(256) NOT NULL, result_code VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(2000), printed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    printed_by_ref VARCHAR(128) NOT NULL, organization_reference VARCHAR(128) NOT NULL,
    UNIQUE (item_id, output_id, print_version)
);
CREATE TABLE IF NOT EXISTS pis_v2.procurement_request (
    id UUID PRIMARY KEY, request_no VARCHAR(128) NOT NULL, requester_reference VARCHAR(256) NOT NULL,
    department_reference VARCHAR(256) NOT NULL, reason VARCHAR(2000) NOT NULL, status_code VARCHAR(32) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL, organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.procurement_item (
    id UUID PRIMARY KEY, request_id UUID NOT NULL, material_reference VARCHAR(256) NOT NULL,
    quantity NUMERIC(18,3) NOT NULL, estimated_amount NUMERIC(18,2) NOT NULL, supplier VARCHAR(256)
);
CREATE TABLE IF NOT EXISTS pis_v2.procurement_approval (
    id UUID PRIMARY KEY, request_id UUID NOT NULL, approval_sequence INTEGER NOT NULL,
    approver_reference VARCHAR(256) NOT NULL, decision_code VARCHAR(32) NOT NULL, comment VARCHAR(2000),
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL, organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.procurement_attachment (
    id UUID PRIMARY KEY, request_id UUID NOT NULL, attachment_kind_code VARCHAR(64) NOT NULL,
    storage_reference VARCHAR(1024) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL, organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.department_space (
    id UUID PRIMARY KEY, parent_id UUID, space_code VARCHAR(128) NOT NULL, name VARCHAR(256) NOT NULL,
    zone_code VARCHAR(64) NOT NULL, area_value NUMERIC(18,3), administrator_reference VARCHAR(256),
    description VARCHAR(2000), view_reference VARCHAR(1024), active BOOLEAN NOT NULL,
    organization_reference VARCHAR(128) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.space_environment_record (
    id UUID PRIMARY KEY, space_id UUID NOT NULL, metric_code VARCHAR(64) NOT NULL,
    measure_value NUMERIC(18,6) NOT NULL, unit_code VARCHAR(32) NOT NULL,
    measured_at TIMESTAMP WITH TIME ZONE NOT NULL, source_reference VARCHAR(256),
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.space_safety_check (
    id UUID PRIMARY KEY, space_id UUID NOT NULL, check_code VARCHAR(64) NOT NULL, result_code VARCHAR(32) NOT NULL,
    note VARCHAR(2000), checked_at TIMESTAMP WITH TIME ZONE NOT NULL, checked_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.critical_value (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, value_type_code VARCHAR(128) NOT NULL, grade_code VARCHAR(32) NOT NULL,
    trigger_reference VARCHAR(512), status_code VARCHAR(32) NOT NULL, due_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.critical_value_notification (
    id UUID PRIMARY KEY, critical_value_id UUID NOT NULL, department_reference VARCHAR(256) NOT NULL,
    recipient_reference VARCHAR(256) NOT NULL, method_code VARCHAR(64) NOT NULL,
    notified_at TIMESTAMP WITH TIME ZONE NOT NULL, notified_by_ref VARCHAR(128) NOT NULL,
    acknowledgement_at TIMESTAMP WITH TIME ZONE, acknowledged_by_ref VARCHAR(128),
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.critical_value_feedback (
    id UUID PRIMARY KEY, critical_value_id UUID NOT NULL, content VARCHAR(4000) NOT NULL,
    feedback_at TIMESTAMP WITH TIME ZONE NOT NULL, feedback_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.report_distribution (
    id UUID PRIMARY KEY, report_id UUID NOT NULL, target_code VARCHAR(64) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL, sent_at TIMESTAMP WITH TIME ZONE,
    status_code VARCHAR(32) NOT NULL, retry_count INTEGER NOT NULL, last_error VARCHAR(2000),
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.report_print_record (
    id UUID PRIMARY KEY, report_id UUID NOT NULL, identity_reference VARCHAR(256) NOT NULL,
    terminal_reference VARCHAR(256), printer_reference VARCHAR(256), printed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    result_code VARCHAR(32) NOT NULL, copy_count INTEGER NOT NULL, organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.common_address (
    id UUID PRIMARY KEY, address_name VARCHAR(256) NOT NULL, recipient_name VARCHAR(256) NOT NULL,
    phone VARCHAR(128), address_text VARCHAR(2000) NOT NULL, organization_reference VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.logistics_package (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, consultation_id UUID, courier_company VARCHAR(256) NOT NULL,
    tracking_no VARCHAR(256), sender_reference VARCHAR(256) NOT NULL, recipient_reference VARCHAR(256) NOT NULL,
    address_text VARCHAR(2000) NOT NULL, status_code VARCHAR(32) NOT NULL, sent_at TIMESTAMP WITH TIME ZONE,
    organization_reference VARCHAR(128) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.logistics_package_item (
    id UUID PRIMARY KEY, package_id UUID NOT NULL, block_id UUID, slide_id UUID, document_reference VARCHAR(1024)
);
CREATE TABLE IF NOT EXISTS pis_v2.logistics_event (
    id UUID PRIMARY KEY, package_id UUID NOT NULL, status_code VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, note VARCHAR(2000), recorded_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.molecular_project (
    id UUID PRIMARY KEY, project_code VARCHAR(128) NOT NULL, project_name VARCHAR(256) NOT NULL,
    project_type_code VARCHAR(64) NOT NULL, enabled BOOLEAN NOT NULL, organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.molecular_instrument (
    id UUID PRIMARY KEY, instrument_code VARCHAR(128) NOT NULL, name VARCHAR(256) NOT NULL,
    adapter_code VARCHAR(128) NOT NULL, enabled BOOLEAN NOT NULL, organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.molecular_reagent_kit (
    id UUID PRIMARY KEY, kit_code VARCHAR(128) NOT NULL, manufacturer VARCHAR(256), batch_no VARCHAR(128) NOT NULL,
    expiry_date DATE, enabled BOOLEAN NOT NULL, organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.molecular_test (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, specimen_id UUID, project_id UUID NOT NULL,
    detection_no VARCHAR(128) NOT NULL, instrument_id UUID, reagent_kit_id UUID, raw_data_reference VARCHAR(1024),
    structured_result VARCHAR(20000), analysis_result VARCHAR(20000), status_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, completed_at TIMESTAMP WITH TIME ZONE,
    created_by_ref VARCHAR(128) NOT NULL, organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.digital_slide_archive (
    id UUID PRIMARY KEY, digital_slide_id UUID NOT NULL, storage_path VARCHAR(2048) NOT NULL,
    storage_tier VARCHAR(64) NOT NULL, filename VARCHAR(512) NOT NULL, format_code VARCHAR(64) NOT NULL,
    pathology_no VARCHAR(128), slide_no VARCHAR(128), patient_reference VARCHAR(256), organ_reference VARCHAR(256),
    integrity_digest VARCHAR(256), status_code VARCHAR(32) NOT NULL, imported_at TIMESTAMP WITH TIME ZONE NOT NULL,
    restored_at TIMESTAMP WITH TIME ZONE, organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.regional_share (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, receiving_organization VARCHAR(512) NOT NULL,
    receiving_doctor VARCHAR(256), expires_at TIMESTAMP WITH TIME ZONE, patient_authorized BOOLEAN NOT NULL,
    status_code VARCHAR(32) NOT NULL, requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    requested_by_ref VARCHAR(128) NOT NULL, organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.regional_share_item (
    id UUID PRIMARY KEY, share_id UUID NOT NULL, report_id UUID, digital_slide_id UUID, attachment_reference VARCHAR(1024)
);
CREATE TABLE IF NOT EXISTS pis_v2.regional_share_access (
    id UUID PRIMARY KEY, share_id UUID NOT NULL, accessor_reference VARCHAR(256) NOT NULL,
    accessed_at TIMESTAMP WITH TIME ZONE NOT NULL, action_code VARCHAR(64) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.income_fact (
    id UUID PRIMARY KEY, case_id UUID, project_code VARCHAR(128) NOT NULL, amount NUMERIC(18,2) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, source_reference VARCHAR(256) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.migration_job (
    id UUID PRIMARY KEY, source_code VARCHAR(128) NOT NULL, mode_code VARCHAR(32) NOT NULL,
    status_code VARCHAR(32) NOT NULL, started_at TIMESTAMP WITH TIME ZONE, completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, created_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.migration_record (
    id UUID PRIMARY KEY, job_id UUID NOT NULL, legacy_type VARCHAR(128) NOT NULL, legacy_key VARCHAR(256) NOT NULL,
    local_type VARCHAR(128), local_id UUID, record_status VARCHAR(32) NOT NULL, raw_reference VARCHAR(1024),
    mapped_at TIMESTAMP WITH TIME ZONE, organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.migration_error (
    id UUID PRIMARY KEY, job_id UUID NOT NULL, record_id UUID, error_code VARCHAR(128) NOT NULL,
    error_message VARCHAR(4000) NOT NULL, retry_count INTEGER NOT NULL, resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by_ref VARCHAR(128), organization_reference VARCHAR(128) NOT NULL
);

-- FC02A lightweight H2 compatibility only. PostgreSQL migrations and Testcontainers
-- remain the source of truth for partial indexes and constraint semantics.
ALTER TABLE pis_v2.pathology_application ADD IF NOT EXISTS patient_info_source_code VARCHAR(32) DEFAULT 'MANUAL' NOT NULL;
ALTER TABLE pis_v2.pathology_application ADD IF NOT EXISTS patient_identity_no VARCHAR(128);
ALTER TABLE pis_v2.pathology_application ADD IF NOT EXISTS visit_card_no VARCHAR(128);
ALTER TABLE pis_v2.pathology_application ADD IF NOT EXISTS contact_phone VARCHAR(128);
ALTER TABLE pis_v2.pathology_application ADD IF NOT EXISTS age_value INTEGER;
ALTER TABLE pis_v2.pathology_application ADD IF NOT EXISTS age_unit_code VARCHAR(16);
ALTER TABLE pis_v2.pathology_application ADD IF NOT EXISTS ward_reference VARCHAR(256);
ALTER TABLE pis_v2.pathology_application ADD IF NOT EXISTS bed_reference VARCHAR(128);
ALTER TABLE pis_v2.pathology_application ADD IF NOT EXISTS surgery_name VARCHAR(1000);

ALTER TABLE pis_v2.pathology_application_item ADD IF NOT EXISTS cancelled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE pis_v2.pathology_application_item ADD IF NOT EXISTS cancelled_by_ref VARCHAR(128);
ALTER TABLE pis_v2.pathology_application_item ADD IF NOT EXISTS cancellation_reason VARCHAR(2000);
ALTER TABLE pis_v2.pathology_application_item ADD IF NOT EXISTS rejected_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE pis_v2.pathology_application_item ADD IF NOT EXISTS rejected_by_ref VARCHAR(128);
ALTER TABLE pis_v2.pathology_application_item ADD IF NOT EXISTS rejection_reason_code VARCHAR(128);
ALTER TABLE pis_v2.pathology_application_item ADD IF NOT EXISTS rejection_reason_text VARCHAR(2000);

ALTER TABLE pis_v2.pathology_application_delivery ADD IF NOT EXISTS incoming_specimen_reference VARCHAR(256);
ALTER TABLE pis_v2.pathology_application_delivery ADD IF NOT EXISTS reason_code VARCHAR(128);
ALTER TABLE pis_v2.pathology_application_delivery ADD IF NOT EXISTS patient_match BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE pis_v2.pathology_application_delivery ADD IF NOT EXISTS application_match BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE pis_v2.pathology_application_delivery ADD IF NOT EXISTS quantity_match BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE pis_v2.pathology_application_delivery ADD IF NOT EXISTS specimen_match BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE pis_v2.pathology_application_delivery ADD IF NOT EXISTS container_match BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE pis_v2.pathology_application_delivery ADD IF NOT EXISTS fixation_match BOOLEAN DEFAULT FALSE NOT NULL;

ALTER TABLE pis_v2.pathology_application_barcode_print ADD IF NOT EXISTS operation_code VARCHAR(16) DEFAULT 'PRINT' NOT NULL;
ALTER TABLE pis_v2.pathology_application_barcode_print ADD IF NOT EXISTS copies INTEGER DEFAULT 1 NOT NULL;
ALTER TABLE pis_v2.pathology_application_barcode_print ADD IF NOT EXISTS rendered_label VARCHAR(4000);
ALTER TABLE pis_v2.pathology_registration_receipt_print ADD IF NOT EXISTS operation_code VARCHAR(16) DEFAULT 'PRINT' NOT NULL;
ALTER TABLE pis_v2.pathology_registration_receipt_print ADD IF NOT EXISTS copies INTEGER DEFAULT 1 NOT NULL;
ALTER TABLE pis_v2.pathology_registration_receipt_print ADD IF NOT EXISTS rendered_receipt VARCHAR(10000);

ALTER TABLE pis_v2.case_context_snapshot ADD IF NOT EXISTS patient_name VARCHAR(256);
ALTER TABLE pis_v2.case_context_snapshot ADD IF NOT EXISTS patient_sex_code VARCHAR(32);
ALTER TABLE pis_v2.case_context_snapshot ADD IF NOT EXISTS patient_birth_date DATE;
ALTER TABLE pis_v2.case_context_snapshot ADD IF NOT EXISTS age_value INTEGER;
ALTER TABLE pis_v2.case_context_snapshot ADD IF NOT EXISTS age_unit_code VARCHAR(16);
ALTER TABLE pis_v2.case_context_snapshot ADD IF NOT EXISTS visit_type_code VARCHAR(32);
ALTER TABLE pis_v2.case_context_snapshot ADD IF NOT EXISTS application_department VARCHAR(256);
ALTER TABLE pis_v2.case_context_snapshot ADD IF NOT EXISTS applicant_reference VARCHAR(256);
ALTER TABLE pis_v2.case_context_snapshot ADD IF NOT EXISTS clinical_diagnosis VARCHAR(4000);
ALTER TABLE pis_v2.case_context_snapshot ADD IF NOT EXISTS medical_history VARCHAR(10000);
ALTER TABLE pis_v2.case_context_snapshot ADD IF NOT EXISTS surgery_name VARCHAR(1000);
ALTER TABLE pis_v2.case_context_snapshot ADD IF NOT EXISTS operation_finding VARCHAR(10000);

CREATE TABLE IF NOT EXISTS pis_v2.pathology_number_history (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, old_pathology_no VARCHAR(128) NOT NULL,
    new_pathology_no VARCHAR(128), operation_code VARCHAR(32) NOT NULL, reason VARCHAR(2000) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL, changed_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.pathology_registration_label_print (
    id UUID PRIMARY KEY, case_id UUID NOT NULL, specimen_id UUID NOT NULL,
    pathology_no VARCHAR(128) NOT NULL, specimen_code VARCHAR(128) NOT NULL,
    operation_code VARCHAR(16) NOT NULL, copies INTEGER NOT NULL, printer_profile_code VARCHAR(128) NOT NULL,
    rendered_label VARCHAR(4000) NOT NULL, result_code VARCHAR(32) NOT NULL, failure_reason VARCHAR(2000),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL, requested_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);
