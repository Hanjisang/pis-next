-- V2-I03 creates the continuous Diagnosis, template version and responsibility chain.

CREATE TABLE IF NOT EXISTS pis_v2.diagnosis_template (
    id UUID PRIMARY KEY,
    organization_reference VARCHAR(128) NOT NULL,
    template_code VARCHAR(128) NOT NULL,
    template_name VARCHAR(256) NOT NULL,
    business_type_id UUID NOT NULL REFERENCES pis_v2.business_type(id),
    scope_code VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL,
    concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_diagnosis_template_code UNIQUE (organization_reference, template_code),
    CONSTRAINT ck_v2_diagnosis_template_version CHECK (concurrency_version >= 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.diagnosis_template_version (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES pis_v2.diagnosis_template(id),
    version_no INTEGER NOT NULL,
    schema_definition JSONB NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    published_at TIMESTAMPTZ,
    published_by_ref VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    concurrency_version BIGINT NOT NULL,
    CONSTRAINT uq_v2_diagnosis_template_version UNIQUE (template_id, version_no),
    CONSTRAINT ck_v2_diagnosis_template_version_no CHECK (version_no > 0),
    CONSTRAINT ck_v2_diagnosis_template_version_status CHECK (status_code IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_v2_diagnosis_template_published_fields CHECK (
        (status_code = 'DRAFT' AND published_at IS NULL AND published_by_ref IS NULL)
        OR (status_code = 'PUBLISHED' AND published_at IS NOT NULL AND published_by_ref IS NOT NULL)
    ),
    CONSTRAINT ck_v2_diagnosis_template_version_lock CHECK (concurrency_version >= 0)
);

CREATE INDEX IF NOT EXISTS idx_v2_diagnosis_template_published
    ON pis_v2.diagnosis_template_version (template_id, status_code, version_no DESC);

CREATE OR REPLACE FUNCTION pis_v2.prevent_published_diagnosis_template_version_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status_code = 'PUBLISHED' THEN
        RAISE EXCEPTION 'published diagnosis template versions are immutable';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_v2_diagnosis_template_version_immutable
    ON pis_v2.diagnosis_template_version;
CREATE TRIGGER trg_v2_diagnosis_template_version_immutable
    BEFORE UPDATE ON pis_v2.diagnosis_template_version
    FOR EACH ROW EXECUTE FUNCTION pis_v2.prevent_published_diagnosis_template_version_update();

CREATE TABLE IF NOT EXISTS pis_v2.diagnosis (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    context_type VARCHAR(32) NOT NULL,
    context_id UUID NOT NULL,
    template_version_id UUID NOT NULL REFERENCES pis_v2.diagnosis_template_version(id),
    structured_data JSONB NOT NULL,
    microscopic_description TEXT,
    diagnosis_text TEXT,
    comment_text TEXT,
    concurrency_version BIGINT NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_diagnosis_context UNIQUE (organization_reference, case_id, context_type, context_id),
    CONSTRAINT ck_v2_diagnosis_context_type CHECK (context_type IN ('CASE', 'FROZEN_ROUND')),
    CONSTRAINT ck_v2_diagnosis_case_context CHECK (context_type <> 'CASE' OR context_id = case_id),
    CONSTRAINT ck_v2_diagnosis_version CHECK (concurrency_version >= 0)
);

CREATE INDEX IF NOT EXISTS idx_v2_diagnosis_case ON pis_v2.diagnosis (case_id, context_type);
CREATE INDEX IF NOT EXISTS idx_v2_diagnosis_template ON pis_v2.diagnosis (template_version_id);

CREATE TABLE IF NOT EXISTS pis_v2.responsibility_unit (
    id UUID PRIMARY KEY,
    diagnosis_id UUID NOT NULL REFERENCES pis_v2.diagnosis(id),
    role_code VARCHAR(32) NOT NULL,
    doctor_id VARCHAR(128) NOT NULL,
    sequence_no INTEGER NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    end_reason VARCHAR(2000),
    assignment_source_code VARCHAR(32) NOT NULL,
    assignment_reason VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    concurrency_version BIGINT NOT NULL,
    CONSTRAINT uq_v2_responsibility_sequence UNIQUE (diagnosis_id, sequence_no),
    CONSTRAINT ck_v2_responsibility_role CHECK (role_code IN ('INITIAL', 'REVIEW', 'AUDIT')),
    CONSTRAINT ck_v2_responsibility_source CHECK (assignment_source_code IN
        ('PUBLIC_POOL', 'MANUAL', 'SELF_CLAIM', 'REASSIGN')),
    CONSTRAINT ck_v2_responsibility_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_v2_responsibility_version CHECK (concurrency_version >= 0),
    CONSTRAINT ck_v2_responsibility_end_reason CHECK (ended_at IS NULL OR end_reason IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_responsibility_open_role
    ON pis_v2.responsibility_unit (diagnosis_id, role_code)
    WHERE completed_at IS NULL AND ended_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_v2_responsibility_diagnosis
    ON pis_v2.responsibility_unit (diagnosis_id, sequence_no);
CREATE INDEX IF NOT EXISTS idx_v2_responsibility_doctor
    ON pis_v2.responsibility_unit (doctor_id, role_code, completed_at, ended_at);

CREATE TABLE IF NOT EXISTS pis_v2.assignment_rule (
    id UUID PRIMARY KEY,
    organization_reference VARCHAR(128) NOT NULL,
    campus_code VARCHAR(128) NOT NULL,
    business_type_code VARCHAR(64) NOT NULL,
    department_code VARCHAR(128) NOT NULL,
    site_code VARCHAR(256) NOT NULL,
    diagnosis_group_code VARCHAR(128) NOT NULL,
    doctor_id VARCHAR(128),
    priority INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL,
    concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_assignment_rule_priority CHECK (priority >= 0),
    CONSTRAINT ck_v2_assignment_rule_version CHECK (concurrency_version >= 0)
);

CREATE INDEX IF NOT EXISTS idx_v2_assignment_rule_match
    ON pis_v2.assignment_rule (organization_reference, campus_code, business_type_code,
                               department_code, site_code, diagnosis_group_code, priority);

CREATE TABLE IF NOT EXISTS pis_v2.diagnosis_command_idempotency (
    id UUID PRIMARY KEY,
    operation_code VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL,
    result_kind_code VARCHAR(64) NOT NULL,
    result_entity_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_diagnosis_command_idempotency UNIQUE (operation_code, idempotency_key)
);

INSERT INTO pis_v2.diagnosis_template
    (id, organization_reference, template_code, template_name, business_type_id, scope_code, enabled,
     concurrency_version, created_at, created_by_ref, updated_at, updated_by_ref)
SELECT md5('PIS-V2-I03-DIAGNOSIS-TEMPLATE:' || bt.business_type_code)::uuid,
       'LOCAL_HOSPITAL', 'DEFAULT-' || bt.business_type_code, 'V2默认诊断模板-' || bt.display_name,
       bt.id, 'LOCAL_HOSPITAL', TRUE, 0, CURRENT_TIMESTAMP, 'V2-I03-SEED', CURRENT_TIMESTAMP, 'V2-I03-SEED'
FROM pis_v2.business_type bt
ON CONFLICT (organization_reference, template_code) DO NOTHING;

INSERT INTO pis_v2.diagnosis_template_version
    (id, template_id, version_no, schema_definition, status_code, published_at, published_by_ref,
     created_at, created_by_ref, concurrency_version)
SELECT md5('PIS-V2-I03-DIAGNOSIS-TEMPLATE-V1:' || bt.business_type_code)::uuid,
       md5('PIS-V2-I03-DIAGNOSIS-TEMPLATE:' || bt.business_type_code)::uuid, 1,
       '{"components":[{"type":"TEXTAREA","code":"microscopicDescription","label":"镜下所见"},{"type":"TEXTAREA","code":"diagnosisText","label":"诊断意见"},{"type":"TEXTAREA","code":"comment","label":"备注"}],"version":1}'::jsonb,
       'PUBLISHED', CURRENT_TIMESTAMP, 'V2-I03-SEED', CURRENT_TIMESTAMP, 'V2-I03-SEED', 0
FROM pis_v2.business_type bt
ON CONFLICT (template_id, version_no) DO NOTHING;

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b301', 'PIS_V2', 'V2-I03', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE SET version_code = 'V2-I03', recorded_at = CURRENT_TIMESTAMP;
