-- Wave A closes the technical-order configuration boundary without introducing a task engine.

ALTER TABLE pis_v2.technical_project
    ADD COLUMN IF NOT EXISTS capability_code VARCHAR(64) NOT NULL DEFAULT 'OTHER_TECHNICAL';
ALTER TABLE pis_v2.technical_project
    ADD COLUMN IF NOT EXISTS output_type_code VARCHAR(32) NOT NULL DEFAULT 'SLIDE';
ALTER TABLE pis_v2.technical_project
    ADD COLUMN IF NOT EXISTS requires_result BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pis_v2.technical_project
    ADD COLUMN IF NOT EXISTS device_type_code VARCHAR(64);
ALTER TABLE pis_v2.technical_project
    ADD COLUMN IF NOT EXISTS consumable_required BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE pis_v2.technical_project
SET capability_code = CASE project_code
        WHEN 'IHC-KI67' THEN 'IHC'
        WHEN 'SUPPLEMENTARY-GROSSING' THEN 'SUPPLEMENTARY_GROSSING'
        WHEN 'MOLECULAR-STRUCTURED' THEN 'MOLECULAR'
        ELSE capability_code
    END,
    output_type_code = CASE project_code
        WHEN 'IHC-KI67' THEN 'SLIDE'
        WHEN 'SUPPLEMENTARY-GROSSING' THEN 'MIXED'
        WHEN 'MOLECULAR-STRUCTURED' THEN 'RESULT'
        ELSE output_type_code
    END,
    requires_result = CASE WHEN project_code = 'MOLECULAR-STRUCTURED' THEN TRUE ELSE requires_result END,
    device_type_code = CASE WHEN project_code = 'IHC-KI67' THEN 'IHC_STAINER' ELSE device_type_code END,
    consumable_required = CASE WHEN project_code = 'IHC-KI67' THEN TRUE ELSE consumable_required END;

CREATE TABLE IF NOT EXISTS pis_v2.technical_order_device_attempt (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL REFERENCES pis_v2.technical_order_item(id),
    device_type_code VARCHAR(64) NOT NULL,
    adapter_code VARCHAR(128) NOT NULL,
    request_reference VARCHAR(256) NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    retry_count INTEGER NOT NULL,
    error_code VARCHAR(128),
    error_message VARCHAR(2000),
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_technical_device_attempt UNIQUE (item_id, request_reference),
    CONSTRAINT ck_v2_technical_device_attempt_status CHECK (status_code IN ('SUBMITTED', 'SUCCEEDED', 'FAILED', 'RETRY_SCHEDULED')),
    CONSTRAINT ck_v2_technical_device_attempt_retry CHECK (retry_count >= 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.technical_order_quality_evaluation (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL REFERENCES pis_v2.technical_order_item(id),
    technical_output_id UUID REFERENCES pis_v2.technical_order_output(id),
    output_id UUID,
    result_code VARCHAR(32) NOT NULL,
    score NUMERIC(18, 6),
    note VARCHAR(2000),
    evaluated_at TIMESTAMPTZ NOT NULL,
    evaluated_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_technical_quality_result CHECK (result_code IN ('PASS', 'WARNING', 'FAIL'))
);

CREATE INDEX IF NOT EXISTS ix_v2_technical_quality_item
    ON pis_v2.technical_order_quality_evaluation (item_id, evaluated_at DESC);

CREATE TABLE IF NOT EXISTS pis_v2.technical_order_fee_status (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL UNIQUE REFERENCES pis_v2.technical_order_item(id),
    status_code VARCHAR(32) NOT NULL,
    external_reference VARCHAR(256),
    failure_reason VARCHAR(2000),
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_technical_fee_status CHECK (status_code IN ('NOT_SENT', 'PENDING', 'SUCCEEDED', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS pis_v2.technical_order_consumption (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL REFERENCES pis_v2.technical_order_item(id),
    consumable_batch_id UUID NOT NULL REFERENCES pis_v2.consumable_batch(id),
    quantity NUMERIC(18, 3) NOT NULL,
    unit_code VARCHAR(64) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    occurred_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_technical_consumption_quantity CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS ix_v2_technical_consumption_item
    ON pis_v2.technical_order_consumption (item_id, occurred_at DESC);

CREATE TABLE IF NOT EXISTS pis_v2.technical_order_label_print (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL REFERENCES pis_v2.technical_order_item(id),
    technical_output_id UUID NOT NULL REFERENCES pis_v2.technical_order_output(id),
    output_id UUID NOT NULL,
    output_kind VARCHAR(32) NOT NULL,
    print_version INTEGER NOT NULL,
    label_code VARCHAR(256) NOT NULL,
    result_code VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(2000),
    printed_at TIMESTAMPTZ NOT NULL,
    printed_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_technical_label_print UNIQUE (item_id, output_id, print_version),
    CONSTRAINT ck_v2_technical_label_print_version CHECK (print_version > 0),
    CONSTRAINT ck_v2_technical_label_print_result CHECK (result_code IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS ix_v2_technical_label_print_item
    ON pis_v2.technical_order_label_print (item_id, printed_at DESC);

INSERT INTO pis_v2.technical_project
    (id, organization_reference, business_type_id, project_code, project_name,
     capability_code, output_type_code, enabled, allowed_target_types,
     produces_slide, produces_block, produces_structured_result, requires_result,
     device_type_code, consumable_required, default_slide_type, parameters_schema,
     result_schema, fee_mapping, display_configuration, required_before_sign_out_default,
     configuration_version, created_at, created_by_ref, updated_at, updated_by_ref)
SELECT md5('PIS-V2-I04-TECHNICAL-PROJECT:RECUT:' || bt.business_type_code)::uuid,
       'LOCAL_HOSPITAL', bt.id, 'RECUT', '重切', 'RECUT', 'SLIDE', TRUE, 'BLOCK,SLIDE',
       TRUE, FALSE, FALSE, FALSE, NULL, FALSE, 'RECUT',
       '{"fields":[{"code":"reason","required":true,"type":"TEXT"}]}'::jsonb,
       NULL, '{"externalFeeCode":"SYNTH-RECUT"}'::jsonb, '{"color":"violet"}'::jsonb,
       FALSE, 1, CURRENT_TIMESTAMP, 'V2-I04-WAVE-A', CURRENT_TIMESTAMP, 'V2-I04-WAVE-A'
FROM pis_v2.business_type bt
ON CONFLICT (organization_reference, business_type_id, project_code) DO NOTHING;

INSERT INTO pis_v2.technical_project
    (id, organization_reference, business_type_id, project_code, project_name,
     capability_code, output_type_code, enabled, allowed_target_types,
     produces_slide, produces_block, produces_structured_result, requires_result,
     device_type_code, consumable_required, default_slide_type, parameters_schema,
     result_schema, fee_mapping, display_configuration, required_before_sign_out_default,
     configuration_version, created_at, created_by_ref, updated_at, updated_by_ref)
SELECT md5('PIS-V2-I04-TECHNICAL-PROJECT:DEEP-SECTION:' || bt.business_type_code)::uuid,
       'LOCAL_HOSPITAL', bt.id, 'DEEP-SECTION', '深切', 'DEEP_SECTION', 'SLIDE', TRUE, 'BLOCK',
       TRUE, FALSE, FALSE, FALSE, NULL, FALSE, 'DEEP_SECTION',
       '{"fields":[{"code":"depth","required":true,"type":"TEXT"}]}'::jsonb,
       NULL, '{"externalFeeCode":"SYNTH-DEEP-SECTION"}'::jsonb, '{"color":"indigo"}'::jsonb,
       FALSE, 1, CURRENT_TIMESTAMP, 'V2-I04-WAVE-A', CURRENT_TIMESTAMP, 'V2-I04-WAVE-A'
FROM pis_v2.business_type bt
ON CONFLICT (organization_reference, business_type_id, project_code) DO NOTHING;

INSERT INTO pis_v2.technical_project
    (id, organization_reference, business_type_id, project_code, project_name,
     capability_code, output_type_code, enabled, allowed_target_types,
     produces_slide, produces_block, produces_structured_result, requires_result,
     device_type_code, consumable_required, default_slide_type, parameters_schema,
     result_schema, fee_mapping, display_configuration, required_before_sign_out_default,
     configuration_version, created_at, created_by_ref, updated_at, updated_by_ref)
SELECT md5('PIS-V2-I04-TECHNICAL-PROJECT:SPECIAL-STAIN:' || bt.business_type_code)::uuid,
       'LOCAL_HOSPITAL', bt.id, 'SPECIAL-STAIN', '特殊染色', 'SPECIAL_STAIN', 'SLIDE', TRUE, 'BLOCK,SLIDE',
       TRUE, FALSE, FALSE, FALSE, 'SPECIAL_STAINER', TRUE, 'SPECIAL_STAIN',
       '{"fields":[{"code":"stain","required":true,"type":"TEXT"}]}'::jsonb,
       NULL, '{"externalFeeCode":"SYNTH-SPECIAL-STAIN"}'::jsonb, '{"color":"teal"}'::jsonb,
       FALSE, 1, CURRENT_TIMESTAMP, 'V2-I04-WAVE-A', CURRENT_TIMESTAMP, 'V2-I04-WAVE-A'
FROM pis_v2.business_type bt
ON CONFLICT (organization_reference, business_type_id, project_code) DO NOTHING;

INSERT INTO pis_v2.technical_project
    (id, organization_reference, business_type_id, project_code, project_name,
     capability_code, output_type_code, enabled, allowed_target_types,
     produces_slide, produces_block, produces_structured_result, requires_result,
     device_type_code, consumable_required, default_slide_type, parameters_schema,
     result_schema, fee_mapping, display_configuration, required_before_sign_out_default,
     configuration_version, created_at, created_by_ref, updated_at, updated_by_ref)
SELECT md5('PIS-V2-I04-TECHNICAL-PROJECT:WHITE-SLIDE:' || bt.business_type_code)::uuid,
       'LOCAL_HOSPITAL', bt.id, 'WHITE-SLIDE', '白片', 'WHITE_SLIDE', 'SLIDE', TRUE, 'BLOCK',
       TRUE, FALSE, FALSE, FALSE, NULL, FALSE, 'WHITE_SLIDE',
       '{"fields":[{"code":"quantity","required":true,"type":"INTEGER"}]}'::jsonb,
       NULL, '{"externalFeeCode":"SYNTH-WHITE-SLIDE"}'::jsonb, '{"color":"slate"}'::jsonb,
       FALSE, 1, CURRENT_TIMESTAMP, 'V2-I04-WAVE-A', CURRENT_TIMESTAMP, 'V2-I04-WAVE-A'
FROM pis_v2.business_type bt
ON CONFLICT (organization_reference, business_type_id, project_code) DO NOTHING;

INSERT INTO pis_v2.technical_project
    (id, organization_reference, business_type_id, project_code, project_name,
     capability_code, output_type_code, enabled, allowed_target_types,
     produces_slide, produces_block, produces_structured_result, requires_result,
     device_type_code, consumable_required, default_slide_type, parameters_schema,
     result_schema, fee_mapping, display_configuration, required_before_sign_out_default,
     configuration_version, created_at, created_by_ref, updated_at, updated_by_ref)
SELECT md5('PIS-V2-I04-TECHNICAL-PROJECT:OTHER-TECHNICAL:' || bt.business_type_code)::uuid,
       'LOCAL_HOSPITAL', bt.id, 'OTHER-TECHNICAL', '其他技术项目', 'OTHER_TECHNICAL', 'RESULT', TRUE,
       'CASE,SPECIMEN,BLOCK,SLIDE', FALSE, FALSE, TRUE, TRUE, NULL, FALSE, NULL,
       '{"fields":[{"code":"projectDetail","required":true,"type":"TEXT"}]}'::jsonb,
       '{"fields":[{"code":"conclusion","required":true,"type":"TEXTAREA"}]}'::jsonb,
       '{"externalFeeCode":"SYNTH-OTHER-TECHNICAL"}'::jsonb, '{"color":"blue"}'::jsonb,
       FALSE, 1, CURRENT_TIMESTAMP, 'V2-I04-WAVE-A', CURRENT_TIMESTAMP, 'V2-I04-WAVE-A'
FROM pis_v2.business_type bt
ON CONFLICT (organization_reference, business_type_id, project_code) DO NOTHING;

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b444', 'PIS_V2', 'V2-I04-WAVE-A', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE SET version_code = 'V2-I04-WAVE-A', recorded_at = CURRENT_TIMESTAMP;
