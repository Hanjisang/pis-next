-- V2-I05 creates immutable report snapshots and report-template versions.
-- It deliberately does not introduce ReportVersion or a persisted preview entity.

CREATE TABLE IF NOT EXISTS pis_v2.report_template (
    id UUID PRIMARY KEY,
    organization_reference VARCHAR(128) NOT NULL,
    business_type_id UUID NOT NULL REFERENCES pis_v2.business_type(id),
    template_code VARCHAR(128) NOT NULL,
    template_name VARCHAR(256) NOT NULL,
    enabled BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_report_template_code UNIQUE (organization_reference, template_code),
    CONSTRAINT ck_v2_report_template_version CHECK (configuration_version > 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.report_template_version (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES pis_v2.report_template(id),
    version_no INTEGER NOT NULL,
    definition JSONB NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    published_at TIMESTAMPTZ,
    published_by_ref VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    concurrency_version BIGINT NOT NULL,
    CONSTRAINT uq_v2_report_template_version UNIQUE (template_id, version_no),
    CONSTRAINT ck_v2_report_template_version_no CHECK (version_no > 0),
    CONSTRAINT ck_v2_report_template_version_status CHECK (status_code IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_v2_report_template_published_fields CHECK (
        (status_code = 'DRAFT' AND published_at IS NULL AND published_by_ref IS NULL)
        OR (status_code = 'PUBLISHED' AND published_at IS NOT NULL AND published_by_ref IS NOT NULL)
    ),
    CONSTRAINT ck_v2_report_template_version_lock CHECK (concurrency_version >= 0)
);

CREATE INDEX IF NOT EXISTS idx_v2_report_template_business_type
    ON pis_v2.report_template (organization_reference, business_type_id, enabled);
CREATE INDEX IF NOT EXISTS idx_v2_report_template_published
    ON pis_v2.report_template_version (template_id, status_code, version_no DESC);

CREATE OR REPLACE FUNCTION pis_v2.prevent_published_report_template_version_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status_code = 'PUBLISHED' THEN
        RAISE EXCEPTION 'published report template versions are immutable';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_v2_report_template_version_immutable
    ON pis_v2.report_template_version;
CREATE TRIGGER trg_v2_report_template_version_immutable
    BEFORE UPDATE ON pis_v2.report_template_version
    FOR EACH ROW EXECUTE FUNCTION pis_v2.prevent_published_report_template_version_update();

CREATE TABLE IF NOT EXISTS pis_v2.report (
    id UUID PRIMARY KEY,
    report_no VARCHAR(64) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    diagnosis_id UUID NOT NULL REFERENCES pis_v2.diagnosis(id),
    template_version_id UUID NOT NULL REFERENCES pis_v2.report_template_version(id),
    report_nature_code VARCHAR(32) NOT NULL,
    prior_report_id UUID REFERENCES pis_v2.report(id),
    status_code VARCHAR(32) NOT NULL,
    diagnosis_snapshot JSONB NOT NULL,
    responsibility_snapshot JSONB NOT NULL,
    case_snapshot JSONB NOT NULL,
    material_snapshot JSONB NOT NULL,
    technical_result_snapshot JSONB NOT NULL,
    supplemental_content TEXT,
    rendered_content TEXT NOT NULL,
    rendered_content_hash VARCHAR(128) NOT NULL,
    pdf_file_reference VARCHAR(256) NOT NULL,
    pdf_content_hash VARCHAR(128) NOT NULL,
    signed_by_ref VARCHAR(128) NOT NULL,
    signed_at TIMESTAMPTZ NOT NULL,
    withdrawn_by_ref VARCHAR(128),
    withdrawn_at TIMESTAMPTZ,
    withdrawal_reason VARCHAR(2000),
    concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_report_no UNIQUE (organization_reference, case_id, report_no),
    CONSTRAINT ck_v2_report_nature CHECK (report_nature_code IN ('ORIGINAL', 'SUPPLEMENTAL')),
    CONSTRAINT ck_v2_report_status CHECK (status_code IN ('EFFECTIVE', 'WITHDRAWN')),
    CONSTRAINT ck_v2_report_supplemental_link CHECK (
        (report_nature_code = 'ORIGINAL' AND prior_report_id IS NULL)
        OR (report_nature_code = 'SUPPLEMENTAL' AND prior_report_id IS NOT NULL)
    ),
    CONSTRAINT ck_v2_report_withdrawn_fields CHECK (
        (status_code = 'EFFECTIVE' AND withdrawn_by_ref IS NULL AND withdrawn_at IS NULL AND withdrawal_reason IS NULL)
        OR (status_code = 'WITHDRAWN' AND withdrawn_by_ref IS NOT NULL AND withdrawn_at IS NOT NULL
            AND withdrawal_reason IS NOT NULL)
    ),
    CONSTRAINT ck_v2_report_version CHECK (concurrency_version >= 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.report_pdf_output (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL UNIQUE REFERENCES pis_v2.report(id),
    file_reference VARCHAR(256) NOT NULL UNIQUE,
    content BYTEA NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);

CREATE OR REPLACE FUNCTION pis_v2.prevent_report_snapshot_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'v2 reports are immutable medical records';
    END IF;
    IF OLD.report_no IS DISTINCT FROM NEW.report_no
       OR OLD.organization_reference IS DISTINCT FROM NEW.organization_reference
       OR OLD.case_id IS DISTINCT FROM NEW.case_id
       OR OLD.diagnosis_id IS DISTINCT FROM NEW.diagnosis_id
       OR OLD.template_version_id IS DISTINCT FROM NEW.template_version_id
       OR OLD.report_nature_code IS DISTINCT FROM NEW.report_nature_code
       OR OLD.prior_report_id IS DISTINCT FROM NEW.prior_report_id
       OR OLD.diagnosis_snapshot IS DISTINCT FROM NEW.diagnosis_snapshot
       OR OLD.responsibility_snapshot IS DISTINCT FROM NEW.responsibility_snapshot
       OR OLD.case_snapshot IS DISTINCT FROM NEW.case_snapshot
       OR OLD.material_snapshot IS DISTINCT FROM NEW.material_snapshot
       OR OLD.technical_result_snapshot IS DISTINCT FROM NEW.technical_result_snapshot
       OR OLD.supplemental_content IS DISTINCT FROM NEW.supplemental_content
       OR OLD.rendered_content IS DISTINCT FROM NEW.rendered_content
       OR OLD.rendered_content_hash IS DISTINCT FROM NEW.rendered_content_hash
       OR OLD.pdf_file_reference IS DISTINCT FROM NEW.pdf_file_reference
       OR OLD.pdf_content_hash IS DISTINCT FROM NEW.pdf_content_hash
       OR OLD.signed_by_ref IS DISTINCT FROM NEW.signed_by_ref
       OR OLD.signed_at IS DISTINCT FROM NEW.signed_at
       OR OLD.created_at IS DISTINCT FROM NEW.created_at
       OR OLD.created_by_ref IS DISTINCT FROM NEW.created_by_ref THEN
        RAISE EXCEPTION 'v2 report snapshots are immutable';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_v2_report_snapshot_immutable ON pis_v2.report;
CREATE TRIGGER trg_v2_report_snapshot_immutable
    BEFORE UPDATE OR DELETE ON pis_v2.report
    FOR EACH ROW EXECUTE FUNCTION pis_v2.prevent_report_snapshot_update();

CREATE OR REPLACE FUNCTION pis_v2.prevent_report_pdf_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'v2 report PDF outputs are immutable';
END;
$$;

DROP TRIGGER IF EXISTS trg_v2_report_pdf_immutable ON pis_v2.report_pdf_output;
CREATE TRIGGER trg_v2_report_pdf_immutable
    BEFORE UPDATE OR DELETE ON pis_v2.report_pdf_output
    FOR EACH ROW EXECUTE FUNCTION pis_v2.prevent_report_pdf_update();

CREATE TABLE IF NOT EXISTS pis_v2.report_command_idempotency (
    id UUID PRIMARY KEY,
    operation_code VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL,
    result_report_id UUID NOT NULL REFERENCES pis_v2.report(id),
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_report_command_idempotency UNIQUE (operation_code, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_v2_report_case_history
    ON pis_v2.report (organization_reference, case_id, signed_at DESC, id);
CREATE INDEX IF NOT EXISTS idx_v2_report_diagnosis_history
    ON pis_v2.report (organization_reference, diagnosis_id, signed_at DESC, id);
CREATE INDEX IF NOT EXISTS idx_v2_report_effective
    ON pis_v2.report (organization_reference, case_id, status_code, report_nature_code, signed_at DESC);

INSERT INTO pis_v2.report_template
    (id, organization_reference, business_type_id, template_code, template_name, enabled,
     configuration_version, created_at, created_by_ref, updated_at, updated_by_ref)
SELECT md5('PIS-V2-I05-REPORT-TEMPLATE:' || bt.business_type_code)::uuid,
       'LOCAL_HOSPITAL', bt.id, 'DEFAULT-REPORT-' || bt.business_type_code,
       'V2默认报告模板-' || bt.display_name, TRUE, 1, CURRENT_TIMESTAMP, 'V2-I05-SEED',
       CURRENT_TIMESTAMP, 'V2-I05-SEED'
FROM pis_v2.business_type bt
ON CONFLICT (organization_reference, template_code) DO NOTHING;

INSERT INTO pis_v2.report_template_version
    (id, template_id, version_no, definition, status_code, published_at, published_by_ref,
     created_at, created_by_ref, concurrency_version)
SELECT md5('PIS-V2-I05-REPORT-TEMPLATE-V1:' || bt.business_type_code)::uuid,
       md5('PIS-V2-I05-REPORT-TEMPLATE:' || bt.business_type_code)::uuid, 1,
       '{"sections":["CASE","PATIENT","APPLICATION","MATERIAL","DIAGNOSIS","RESPONSIBILITY","TECHNICAL_RESULTS","SIGN_OUT"],"displayStructuredData":true}'::jsonb,
       'PUBLISHED', CURRENT_TIMESTAMP, 'V2-I05-SEED', CURRENT_TIMESTAMP, 'V2-I05-SEED', 0
FROM pis_v2.business_type bt
ON CONFLICT (template_id, version_no) DO NOTHING;

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b501', 'PIS_V2', 'V2-I05', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE SET version_code = 'V2-I05', recorded_at = CURRENT_TIMESTAMP;
