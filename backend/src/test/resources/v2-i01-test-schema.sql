CREATE SCHEMA IF NOT EXISTS pis;
CREATE SCHEMA IF NOT EXISTS pis_v2;

CREATE TABLE IF NOT EXISTS pis.audit_event (
    id UUID PRIMARY KEY,
    operation_code VARCHAR(128), permission_code VARCHAR(64), actor_ref VARCHAR(128),
    subject_type_code VARCHAR(64), target_object_id UUID, target_object_kind_code VARCHAR(64),
    authorization_outcome VARCHAR(32), processing_outcome VARCHAR(64), correlation_id VARCHAR(128),
    reason VARCHAR(2000), created_at TIMESTAMP WITH TIME ZONE
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
    specimen_code VARCHAR(128) NOT NULL, specimen_kind_code VARCHAR(64) NOT NULL,
    source_kind_code VARCHAR(64) NOT NULL, source_reference VARCHAR(256) NOT NULL,
    collection_site VARCHAR(500) NOT NULL, collection_method_code VARCHAR(64) NOT NULL,
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
    id UUID PRIMARY KEY, case_id UUID NOT NULL, grossing_id UUID NOT NULL, specimen_id UUID NOT NULL,
    block_code VARCHAR(64) NOT NULL, block_type VARCHAR(64) NOT NULL, external_source_flag BOOLEAN NOT NULL,
    external_source_reference VARCHAR(256), deleted_at TIMESTAMP WITH TIME ZONE, deleted_by_ref VARCHAR(128),
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
    slide_type VARCHAR(64) NOT NULL, source_context_type VARCHAR(32) NOT NULL, source_context_id UUID,
    rule_code VARCHAR(64) NOT NULL, occurrence_no INTEGER NOT NULL, required BOOLEAN NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE, completed_by_ref VARCHAR(128), deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by_ref VARCHAR(128), deletion_reason VARCHAR(2000), concurrency_version BIGINT NOT NULL,
    organization_reference VARCHAR(128) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    slide_code_active VARCHAR(128) AS (CASE WHEN deleted_at IS NULL THEN slide_code ELSE NULL END)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_test_slide_code_active
    ON pis_v2.slide (case_id, slide_code_active);
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
    project_code VARCHAR(128) NOT NULL, project_name VARCHAR(256) NOT NULL, enabled BOOLEAN NOT NULL,
    allowed_target_types VARCHAR(512) NOT NULL, produces_slide BOOLEAN NOT NULL, produces_block BOOLEAN NOT NULL,
    produces_structured_result BOOLEAN NOT NULL, default_slide_type VARCHAR(64), parameters_schema VARCHAR(20000),
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
DELETE FROM pis_v2.print_log;
DELETE FROM pis_v2.slide;
DELETE FROM pis_v2.block;
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
DELETE FROM pis_v2.business_type;
DELETE FROM pis.audit_event;
DELETE FROM pis.outbox_event;

INSERT INTO pis_v2.business_type
    (id, business_type_code, display_name, modality_code, active, configuration_version, created_at, created_by_ref)
VALUES ('00000000-0000-0000-0000-00000000b001', 'HISTOLOGY', '组织病理', 'TISSUE', TRUE, 1,
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
    (id, organization_reference, business_type_id, project_code, project_name, enabled,
     allowed_target_types, produces_slide, produces_block, produces_structured_result, default_slide_type,
     parameters_schema, result_schema, fee_mapping, display_configuration, required_before_sign_out_default,
     configuration_version, created_at, created_by_ref, updated_at, updated_by_ref)
VALUES
    ('00000000-0000-0000-0000-00000000b401', 'LOCAL_HOSPITAL', '00000000-0000-0000-0000-00000000b001',
     'IHC-KI67', 'Ki67免疫组化', TRUE, 'BLOCK,SLIDE', TRUE, FALSE, FALSE, 'IHC',
     '{"fields":[{"code":"antibody","required":true,"type":"TEXT"}]}', NULL,
     '{"externalFeeCode":"SYNTH-IHC-KI67"}', '{"color":"amber"}', TRUE, 1,
     CURRENT_TIMESTAMP, 'TEST', CURRENT_TIMESTAMP, 'TEST'),
    ('00000000-0000-0000-0000-00000000b402', 'LOCAL_HOSPITAL', '00000000-0000-0000-0000-00000000b001',
     'SUPPLEMENTARY-GROSSING', '补充取材', TRUE, 'CASE,SPECIMEN', TRUE, TRUE, FALSE, 'HE',
     '{"fields":[{"code":"specimenId","required":true,"type":"REFERENCE"},{"code":"blockCode","required":true,"type":"TEXT"}]}', NULL,
     '{"externalFeeCode":"SYNTH-SUPPLEMENTARY-GROSSING"}', '{"color":"green"}', TRUE, 1,
     CURRENT_TIMESTAMP, 'TEST', CURRENT_TIMESTAMP, 'TEST'),
    ('00000000-0000-0000-0000-00000000b403', 'LOCAL_HOSPITAL', '00000000-0000-0000-0000-00000000b001',
     'MOLECULAR-STRUCTURED', '结构化检测结果', TRUE, 'CASE,SPECIMEN,BLOCK,SLIDE', FALSE, FALSE, TRUE, NULL,
     '{"fields":[{"code":"panel","required":true,"type":"TEXT"}]}',
     '{"fields":[{"code":"mutationDetected","type":"BOOLEAN"},{"code":"interpretation","type":"TEXTAREA"}]}',
     '{"externalFeeCode":"SYNTH-MOLECULAR"}', '{"color":"blue"}', TRUE, 1,
     CURRENT_TIMESTAMP, 'TEST', CURRENT_TIMESTAMP, 'TEST');
INSERT INTO pis_v2.application_item_mapping
    (id, application_item_code, business_type_id, default_specimen_kind_code, required, sequence_no,
     active, configuration_version, created_at, created_by_ref)
VALUES ('00000000-0000-0000-0000-00000000b002', 'SYNTH-HISTOLOGY',
        '00000000-0000-0000-0000-00000000b001', 'TISSUE', TRUE, 1, TRUE, 1, CURRENT_TIMESTAMP, 'TEST');
INSERT INTO pis_v2.pathology_number_rule
    (id, business_type_id, organization_reference, number_kind_code, prefix, scope_code, padding_width,
     next_serial, active, configuration_version, created_at, updated_at, created_by_ref)
VALUES
    ('00000000-0000-0000-0000-00000000b003', '00000000-0000-0000-0000-00000000b001', 'LOCAL_HOSPITAL',
     'CASE', 'H-', 'ORGANIZATION', 6, 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TEST'),
    ('00000000-0000-0000-0000-00000000b004', '00000000-0000-0000-0000-00000000b001', 'LOCAL_HOSPITAL',
     'SPECIMEN', 'HS-', 'ORGANIZATION', 7, 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TEST');
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
        'SYNTH-PRINTER', TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TEST');

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
