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

DELETE FROM pis_v2.idempotency_record;
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
