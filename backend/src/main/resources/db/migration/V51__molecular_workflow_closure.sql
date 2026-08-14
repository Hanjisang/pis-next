-- MOL-001..018: one molecular execution chain with immutable result linkage.

ALTER TABLE pis_v2.molecular_test
    ADD COLUMN IF NOT EXISTS result_id UUID REFERENCES pis_v2.molecular_result(id),
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS started_by_ref VARCHAR(128),
    ADD COLUMN IF NOT EXISTS completed_by_ref VARCHAR(128),
    ADD COLUMN IF NOT EXISTS concurrency_version BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_molecular_test_result
    ON pis_v2.molecular_test(result_id) WHERE result_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS pis_v2.molecular_test_attachment (
    id UUID PRIMARY KEY,
    molecular_test_id UUID NOT NULL REFERENCES pis_v2.molecular_test(id),
    digital_slide_id UUID REFERENCES pis_v2.digital_slide(id),
    attachment_reference VARCHAR(1024),
    description VARCHAR(512),
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_molecular_attachment_source CHECK (
        (digital_slide_id IS NOT NULL AND attachment_reference IS NULL)
        OR (digital_slide_id IS NULL AND attachment_reference IS NOT NULL)
    )
);

CREATE TABLE IF NOT EXISTS pis_v2.molecular_instrument_attempt (
    id UUID PRIMARY KEY,
    molecular_test_id UUID NOT NULL REFERENCES pis_v2.molecular_test(id),
    instrument_id UUID NOT NULL REFERENCES pis_v2.molecular_instrument(id),
    adapter_code VARCHAR(128) NOT NULL,
    attempt_no INTEGER NOT NULL,
    request_reference VARCHAR(256) NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    response_reference VARCHAR(512),
    error_code VARCHAR(128),
    error_message VARCHAR(2000),
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    requested_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_molecular_attempt UNIQUE (molecular_test_id, attempt_no),
    CONSTRAINT ck_v2_molecular_attempt_status CHECK (status_code IN ('ACCEPTED', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS pis_v2.molecular_command_idempotency (
    id UUID PRIMARY KEY,
    organization_reference VARCHAR(128) NOT NULL,
    operation_code VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL,
    result_entity_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_molecular_command UNIQUE
        (organization_reference, operation_code, idempotency_key)
);

CREATE INDEX IF NOT EXISTS ix_v2_molecular_test_case
    ON pis_v2.molecular_test(organization_reference, case_id, created_at);
CREATE INDEX IF NOT EXISTS ix_v2_molecular_attachment_test
    ON pis_v2.molecular_test_attachment(molecular_test_id, created_at);

INSERT INTO pis_v2.diagnosis_template_version
    (id, template_id, version_no, schema_definition, status_code, published_at, published_by_ref,
     created_at, created_by_ref, concurrency_version)
SELECT md5('PIS-V2-MOLECULAR-DIAGNOSIS-V2')::uuid, dt.id, 2,
       '{"version":2,"standardCode":"MOLECULAR-STRUCTURED","components":[{"type":"TEXTAREA","code":"testSummary","label":"检测项目与标本摘要","required":true},{"type":"TEXTAREA","code":"qualityStatement","label":"检测质量说明","required":true},{"type":"TEXTAREA","code":"variantInterpretation","label":"结构化结果解释","required":true},{"type":"TEXTAREA","code":"diagnosisText","label":"分子病理诊断","required":true},{"type":"TEXTAREA","code":"comment","label":"备注与建议"}]}'::jsonb,
       'PUBLISHED', CURRENT_TIMESTAMP, 'V2-MOLECULAR-SEED', CURRENT_TIMESTAMP, 'V2-MOLECULAR-SEED', 0
FROM pis_v2.business_type bt
JOIN pis_v2.diagnosis_template dt ON dt.business_type_id = bt.id
 AND dt.organization_reference = 'LOCAL_HOSPITAL'
 AND dt.template_code = 'DEFAULT-' || bt.business_type_code
WHERE bt.business_type_code = 'MOLECULAR'
ON CONFLICT (template_id, version_no) DO NOTHING;

INSERT INTO pis_v2.report_template_version
    (id, template_id, version_no, definition, status_code, published_at, published_by_ref,
     created_at, created_by_ref, concurrency_version)
SELECT md5('PIS-V2-MOLECULAR-REPORT-V2')::uuid, rt.id, 2,
       jsonb_build_object('schemaVersion', 1, 'title', '分子病理报告', 'category', 'GENERAL',
           'page', jsonb_build_object('size', 'A4', 'showPageNumber', true),
           'sections', jsonb_build_array(
             jsonb_build_object('code','BASIC','label','基本信息','source','CASE','fields',jsonb_build_array('pathologyNo','patientReference','visitReference')),
             jsonb_build_object('code','MOLECULAR','label','分子检测结果','source','MOLECULAR','fields',jsonb_build_array('detectionNo','projectCode','structuredResult','analysisResult')),
             jsonb_build_object('code','DIAGNOSIS','label','分子病理诊断','source','DIAGNOSIS','fields',jsonb_build_array('structuredData','diagnosisText','comment')),
             jsonb_build_object('code','SIGNATURE','label','签发信息','source','SIGNATURE','fields',jsonb_build_array('signedBy','signedAt')))),
       'PUBLISHED', CURRENT_TIMESTAMP, 'V2-MOLECULAR-SEED', CURRENT_TIMESTAMP, 'V2-MOLECULAR-SEED', 0
FROM pis_v2.business_type bt
JOIN pis_v2.report_template rt ON rt.business_type_id = bt.id
 AND rt.organization_reference = 'LOCAL_HOSPITAL'
 AND rt.template_code = 'DEFAULT-REPORT-' || bt.business_type_code
WHERE bt.business_type_code = 'MOLECULAR'
ON CONFLICT (template_id, version_no) DO NOTHING;

UPDATE pis_v2.schema_metadata
SET version_code = 'MOLECULAR-WORKFLOW-CLOSURE', recorded_at = CURRENT_TIMESTAMP
WHERE schema_code = 'PIS_V2';
