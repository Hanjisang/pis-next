-- V2-I04 creates TechnicalProject and TechnicalOrder facts without introducing a task workflow.

DROP INDEX IF EXISTS pis_v2.uq_v2_slide_rule_output_active;
CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_slide_rule_output_active
    ON pis_v2.slide (block_id, source_context_type, source_context_id, rule_code, occurrence_no)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS pis_v2.technical_project (
    id UUID PRIMARY KEY,
    organization_reference VARCHAR(128) NOT NULL,
    business_type_id UUID NOT NULL REFERENCES pis_v2.business_type(id),
    project_code VARCHAR(128) NOT NULL,
    project_name VARCHAR(256) NOT NULL,
    enabled BOOLEAN NOT NULL,
    allowed_target_types VARCHAR(512) NOT NULL,
    produces_slide BOOLEAN NOT NULL,
    produces_block BOOLEAN NOT NULL,
    produces_structured_result BOOLEAN NOT NULL,
    default_slide_type VARCHAR(64),
    parameters_schema JSONB,
    result_schema JSONB,
    fee_mapping JSONB,
    display_configuration JSONB,
    required_before_sign_out_default BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_technical_project_code UNIQUE (organization_reference, business_type_id, project_code),
    CONSTRAINT ck_v2_technical_project_version CHECK (configuration_version > 0),
    CONSTRAINT ck_v2_technical_project_output CHECK (
        produces_slide OR produces_block OR produces_structured_result
    )
);

CREATE TABLE IF NOT EXISTS pis_v2.technical_order_sequence (
    organization_reference VARCHAR(128) NOT NULL,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    next_serial BIGINT NOT NULL,
    CONSTRAINT pk_v2_technical_order_sequence PRIMARY KEY (organization_reference, case_id),
    CONSTRAINT ck_v2_technical_order_serial CHECK (next_serial > 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.technical_order (
    id UUID PRIMARY KEY,
    organization_reference VARCHAR(128) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    diagnosis_id UUID NOT NULL REFERENCES pis_v2.diagnosis(id),
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    required_before_sign_out BOOLEAN NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    cancelled_at TIMESTAMPTZ,
    cancelled_by_ref VARCHAR(128),
    cancellation_reason VARCHAR(2000),
    concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_technical_order_no UNIQUE (organization_reference, case_id, order_no),
    CONSTRAINT ck_v2_technical_order_status CHECK (status_code IN ('PENDING', 'EXECUTING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_v2_technical_order_version CHECK (concurrency_version >= 0),
    CONSTRAINT ck_v2_technical_order_cancelled_fields CHECK (
        (status_code = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by_ref IS NOT NULL
            AND cancellation_reason IS NOT NULL)
        OR status_code <> 'CANCELLED'
    )
);

CREATE TABLE IF NOT EXISTS pis_v2.technical_order_item (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES pis_v2.technical_order(id),
    technical_project_id UUID NOT NULL REFERENCES pis_v2.technical_project(id),
    project_code_snapshot VARCHAR(128) NOT NULL,
    project_name_snapshot VARCHAR(256) NOT NULL,
    project_configuration_version INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    parameters JSONB NOT NULL,
    note TEXT,
    cancelled_at TIMESTAMPTZ,
    cancelled_by_ref VARCHAR(128),
    cancellation_reason VARCHAR(2000),
    concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_technical_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_v2_technical_order_item_version CHECK (concurrency_version >= 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.technical_order_target (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL REFERENCES pis_v2.technical_order_item(id),
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    target_type VARCHAR(32) NOT NULL,
    case_target_id UUID REFERENCES pis_v2.pathology_case(id),
    specimen_target_id UUID REFERENCES pis_v2.specimen(id),
    block_target_id UUID REFERENCES pis_v2.block(id),
    slide_target_id UUID REFERENCES pis_v2.slide(id),
    target_display_code VARCHAR(256),
    concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_technical_order_target_type CHECK (
        (target_type = 'CASE' AND case_target_id IS NOT NULL AND specimen_target_id IS NULL
            AND block_target_id IS NULL AND slide_target_id IS NULL)
        OR (target_type = 'SPECIMEN' AND case_target_id IS NULL AND specimen_target_id IS NOT NULL
            AND block_target_id IS NULL AND slide_target_id IS NULL)
        OR (target_type = 'BLOCK' AND case_target_id IS NULL AND specimen_target_id IS NULL
            AND block_target_id IS NOT NULL AND slide_target_id IS NULL)
        OR (target_type = 'SLIDE' AND case_target_id IS NULL AND specimen_target_id IS NULL
            AND block_target_id IS NULL AND slide_target_id IS NOT NULL)
    ),
    CONSTRAINT ck_v2_technical_order_target_version CHECK (concurrency_version >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_technical_order_target_case
    ON pis_v2.technical_order_target (item_id, case_target_id) WHERE case_target_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_technical_order_target_specimen
    ON pis_v2.technical_order_target (item_id, specimen_target_id) WHERE specimen_target_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_technical_order_target_block
    ON pis_v2.technical_order_target (item_id, block_target_id) WHERE block_target_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_technical_order_target_slide
    ON pis_v2.technical_order_target (item_id, slide_target_id) WHERE slide_target_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS pis_v2.technical_order_item_result (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL UNIQUE REFERENCES pis_v2.technical_order_item(id),
    result_schema_snapshot JSONB,
    result_data JSONB NOT NULL,
    concurrency_version BIGINT NOT NULL,
    entered_at TIMESTAMPTZ NOT NULL,
    entered_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_technical_order_result_version CHECK (concurrency_version >= 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.technical_order_output (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL REFERENCES pis_v2.technical_order_item(id),
    target_id UUID REFERENCES pis_v2.technical_order_target(id),
    output_kind VARCHAR(32) NOT NULL,
    grossing_output_id UUID REFERENCES pis_v2.grossing(id),
    block_output_id UUID REFERENCES pis_v2.block(id),
    slide_output_id UUID REFERENCES pis_v2.slide(id),
    result_output_id UUID REFERENCES pis_v2.technical_order_item_result(id),
    occurrence_no INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_technical_order_output UNIQUE (item_id, target_id, output_kind, occurrence_no),
    CONSTRAINT ck_v2_technical_order_output_kind CHECK (
        (output_kind = 'GROSSING' AND grossing_output_id IS NOT NULL AND block_output_id IS NULL
            AND slide_output_id IS NULL AND result_output_id IS NULL)
        OR (output_kind = 'BLOCK' AND grossing_output_id IS NULL AND block_output_id IS NOT NULL
            AND slide_output_id IS NULL AND result_output_id IS NULL)
        OR (output_kind = 'SLIDE' AND grossing_output_id IS NULL AND block_output_id IS NULL
            AND slide_output_id IS NOT NULL AND result_output_id IS NULL)
        OR (output_kind = 'RESULT' AND grossing_output_id IS NULL AND block_output_id IS NULL
            AND slide_output_id IS NULL AND result_output_id IS NOT NULL)
    ),
    CONSTRAINT ck_v2_technical_order_output_occurrence CHECK (occurrence_no > 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.technical_order_idempotency (
    id UUID PRIMARY KEY,
    operation_code VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL,
    result_kind_code VARCHAR(64) NOT NULL,
    result_entity_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_technical_order_idempotency UNIQUE (operation_code, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_v2_technical_order_diagnosis
    ON pis_v2.technical_order (diagnosis_id, status_code, created_at);
CREATE INDEX IF NOT EXISTS idx_v2_technical_order_case
    ON pis_v2.technical_order (case_id, status_code, created_at);
CREATE INDEX IF NOT EXISTS idx_v2_technical_order_item_order
    ON pis_v2.technical_order_item (order_id, created_at);
CREATE INDEX IF NOT EXISTS idx_v2_technical_order_target_case
    ON pis_v2.technical_order_target (case_id, target_type);
CREATE INDEX IF NOT EXISTS idx_v2_technical_order_output_item
    ON pis_v2.technical_order_output (item_id, output_kind, occurrence_no);

INSERT INTO pis_v2.technical_project
    (id, organization_reference, business_type_id, project_code, project_name, enabled,
     allowed_target_types, produces_slide, produces_block, produces_structured_result,
     default_slide_type, parameters_schema, result_schema, fee_mapping, display_configuration,
     required_before_sign_out_default, configuration_version, created_at, created_by_ref,
     updated_at, updated_by_ref)
SELECT md5('PIS-V2-I04-TECHNICAL-PROJECT:IHC-KI67:' || bt.business_type_code)::uuid,
       'LOCAL_HOSPITAL', bt.id, 'IHC-KI67', 'Ki67免疫组化', TRUE, 'BLOCK,SLIDE', TRUE, FALSE, FALSE,
       'IHC', '{"fields":[{"code":"antibody","required":true,"type":"TEXT"}]}'::jsonb,
       NULL, '{"externalFeeCode":"SYNTH-IHC-KI67"}'::jsonb, '{"color":"amber"}'::jsonb,
       TRUE, 1, CURRENT_TIMESTAMP, 'V2-I04-SEED', CURRENT_TIMESTAMP, 'V2-I04-SEED'
FROM pis_v2.business_type bt
ON CONFLICT (organization_reference, business_type_id, project_code) DO NOTHING;

INSERT INTO pis_v2.technical_project
    (id, organization_reference, business_type_id, project_code, project_name, enabled,
     allowed_target_types, produces_slide, produces_block, produces_structured_result,
     default_slide_type, parameters_schema, result_schema, fee_mapping, display_configuration,
     required_before_sign_out_default, configuration_version, created_at, created_by_ref,
     updated_at, updated_by_ref)
SELECT md5('PIS-V2-I04-TECHNICAL-PROJECT:SUPPLEMENTARY-GROSSING:' || bt.business_type_code)::uuid,
       'LOCAL_HOSPITAL', bt.id, 'SUPPLEMENTARY-GROSSING', '补充取材', TRUE, 'CASE,SPECIMEN', TRUE, TRUE, FALSE,
       'HE', '{"fields":[{"code":"specimenId","required":true,"type":"REFERENCE"},{"code":"blockCode","required":true,"type":"TEXT"}]}'::jsonb,
       NULL, '{"externalFeeCode":"SYNTH-SUPPLEMENTARY-GROSSING"}'::jsonb, '{"color":"green"}'::jsonb,
       TRUE, 1, CURRENT_TIMESTAMP, 'V2-I04-SEED', CURRENT_TIMESTAMP, 'V2-I04-SEED'
FROM pis_v2.business_type bt
ON CONFLICT (organization_reference, business_type_id, project_code) DO NOTHING;

INSERT INTO pis_v2.technical_project
    (id, organization_reference, business_type_id, project_code, project_name, enabled,
     allowed_target_types, produces_slide, produces_block, produces_structured_result,
     default_slide_type, parameters_schema, result_schema, fee_mapping, display_configuration,
     required_before_sign_out_default, configuration_version, created_at, created_by_ref,
     updated_at, updated_by_ref)
SELECT md5('PIS-V2-I04-TECHNICAL-PROJECT:MOLECULAR-STRUCTURED:' || bt.business_type_code)::uuid,
       'LOCAL_HOSPITAL', bt.id, 'MOLECULAR-STRUCTURED', '结构化检测结果', TRUE, 'CASE,SPECIMEN,BLOCK,SLIDE', FALSE, FALSE, TRUE,
       NULL, '{"fields":[{"code":"panel","required":true,"type":"TEXT"}]}'::jsonb,
       '{"fields":[{"code":"mutationDetected","type":"BOOLEAN"},{"code":"interpretation","type":"TEXTAREA"}]}'::jsonb,
       '{"externalFeeCode":"SYNTH-MOLECULAR"}'::jsonb, '{"color":"blue"}'::jsonb,
       TRUE, 1, CURRENT_TIMESTAMP, 'V2-I04-SEED', CURRENT_TIMESTAMP, 'V2-I04-SEED'
FROM pis_v2.business_type bt
ON CONFLICT (organization_reference, business_type_id, project_code) DO NOTHING;

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b401', 'PIS_V2', 'V2-I04', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE SET version_code = 'V2-I04', recorded_at = CURRENT_TIMESTAMP;
