-- V2-I06-C adds digital-slide metadata, material custody, search, QC and statistics facts.

ALTER TABLE pis_v2.block ADD COLUMN IF NOT EXISTS destroyed_at TIMESTAMPTZ;
ALTER TABLE pis_v2.block ADD COLUMN IF NOT EXISTS destroyed_by_ref VARCHAR(128);
ALTER TABLE pis_v2.block ADD COLUMN IF NOT EXISTS destruction_reason VARCHAR(2000);
ALTER TABLE pis_v2.block ADD COLUMN IF NOT EXISTS destruction_batch_reference VARCHAR(256);
ALTER TABLE pis_v2.slide ADD COLUMN IF NOT EXISTS destroyed_at TIMESTAMPTZ;
ALTER TABLE pis_v2.slide ADD COLUMN IF NOT EXISTS destroyed_by_ref VARCHAR(128);
ALTER TABLE pis_v2.slide ADD COLUMN IF NOT EXISTS destruction_reason VARCHAR(2000);
ALTER TABLE pis_v2.slide ADD COLUMN IF NOT EXISTS destruction_batch_reference VARCHAR(256);

CREATE TABLE IF NOT EXISTS pis_v2.digital_slide (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    block_id UUID REFERENCES pis_v2.block(id),
    slide_id UUID REFERENCES pis_v2.slide(id),
    binding_mode_code VARCHAR(32) NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    viewer_reference VARCHAR(512) NOT NULL,
    source_platform VARCHAR(256) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_digital_slide_binding_mode CHECK (binding_mode_code IN ('AUTOMATIC', 'MANUAL')),
    CONSTRAINT ck_v2_digital_slide_status CHECK (status_code IN ('ACTIVE', 'UNBOUND'))
);

CREATE INDEX IF NOT EXISTS ix_v2_digital_slide_case ON pis_v2.digital_slide (case_id, status_code, created_at);
CREATE INDEX IF NOT EXISTS ix_v2_digital_slide_slide ON pis_v2.digital_slide (slide_id, status_code);

CREATE TABLE IF NOT EXISTS pis_v2.archive_location (
    id UUID PRIMARY KEY,
    parent_id UUID REFERENCES pis_v2.archive_location(id),
    location_code VARCHAR(128) NOT NULL,
    location_name VARCHAR(256) NOT NULL,
    location_kind_code VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_archive_location_code UNIQUE (organization_reference, location_code)
);

CREATE TABLE IF NOT EXISTS pis_v2.material_archive_history (
    id UUID PRIMARY KEY,
    block_id UUID REFERENCES pis_v2.block(id),
    slide_id UUID REFERENCES pis_v2.slide(id),
    location_id UUID NOT NULL REFERENCES pis_v2.archive_location(id),
    event_code VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    occurred_by_ref VARCHAR(128) NOT NULL,
    reason VARCHAR(2000),
    CONSTRAINT ck_v2_archive_history_one_material CHECK ((block_id IS NOT NULL) <> (slide_id IS NOT NULL)
        AND event_code IN ('ARCHIVED', 'MOVED', 'REMOVED'))
);

CREATE TABLE IF NOT EXISTS pis_v2.block_archive_current (
    block_id UUID PRIMARY KEY REFERENCES pis_v2.block(id),
    location_id UUID NOT NULL REFERENCES pis_v2.archive_location(id),
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis_v2.slide_archive_current (
    slide_id UUID PRIMARY KEY REFERENCES pis_v2.slide(id),
    location_id UUID NOT NULL REFERENCES pis_v2.archive_location(id),
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis_v2.loan (
    id UUID PRIMARY KEY,
    borrower_reference VARCHAR(256) NOT NULL,
    purpose VARCHAR(2000) NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    borrowed_at TIMESTAMPTZ NOT NULL,
    borrowed_by_ref VARCHAR(128) NOT NULL,
    returned_at TIMESTAMPTZ,
    returned_by_ref VARCHAR(128),
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_loan_status CHECK (status_code IN ('BORROWED', 'RETURNED'))
);

CREATE TABLE IF NOT EXISTS pis_v2.loan_item (
    id UUID PRIMARY KEY,
    loan_id UUID NOT NULL REFERENCES pis_v2.loan(id),
    block_id UUID REFERENCES pis_v2.block(id),
    slide_id UUID REFERENCES pis_v2.slide(id),
    returned_at TIMESTAMPTZ,
    returned_by_ref VARCHAR(128),
    CONSTRAINT ck_v2_loan_item_one_material CHECK ((block_id IS NOT NULL) <> (slide_id IS NOT NULL)),
    CONSTRAINT uq_v2_loan_item_material UNIQUE (loan_id, block_id, slide_id)
);

CREATE TABLE IF NOT EXISTS pis_v2.material_destruction (
    id UUID PRIMARY KEY,
    block_id UUID REFERENCES pis_v2.block(id),
    slide_id UUID REFERENCES pis_v2.slide(id),
    destroyed_at TIMESTAMPTZ NOT NULL,
    destroyed_by_ref VARCHAR(128) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    batch_reference VARCHAR(256) NOT NULL,
    CONSTRAINT ck_v2_destruction_one_material CHECK ((block_id IS NOT NULL) <> (slide_id IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS pis_v2.custody_command_idempotency (
    id UUID PRIMARY KEY,
    operation_code VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL,
    result_entity_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_custody_idempotency UNIQUE (operation_code, idempotency_key)
);

CREATE TABLE IF NOT EXISTS pis_v2.qc_rule (
    id UUID PRIMARY KEY,
    rule_code VARCHAR(128) NOT NULL UNIQUE,
    rule_name VARCHAR(256) NOT NULL,
    metric_code VARCHAR(128) NOT NULL,
    warning_threshold NUMERIC(18,6) NOT NULL,
    overdue_threshold NUMERIC(18,6) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis_v2.qc_evaluation (
    id UUID PRIMARY KEY,
    rule_id UUID NOT NULL REFERENCES pis_v2.qc_rule(id),
    case_id UUID REFERENCES pis_v2.pathology_case(id),
    measure_value NUMERIC(18,6) NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    evaluated_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_qc_status CHECK (status_code IN ('NORMAL', 'WARNING', 'OVERDUE', 'ABNORMAL'))
);

INSERT INTO pis_v2.qc_rule (id, rule_code, rule_name, metric_code, warning_threshold, overdue_threshold, active,
                            created_at, created_by_ref)
VALUES
    (md5('PIS-V2-QC-ROUTINE-TAT')::uuid, 'ROUTINE_TAT', '常规 TAT', 'ROUTINE_TAT_HOURS', 48, 72, TRUE, CURRENT_TIMESTAMP, 'V2-I06-SEED'),
    (md5('PIS-V2-QC-FROZEN-TAT')::uuid, 'FROZEN_TAT', '冰冻 TAT', 'FROZEN_TAT_HOURS', 1, 2, TRUE, CURRENT_TIMESTAMP, 'V2-I06-SEED'),
    (md5('PIS-V2-QC-REPORT-WITHDRAW-RATE')::uuid, 'REPORT_WITHDRAW_RATE', '报告撤回率', 'REPORT_WITHDRAW_RATE', 0.05, 0.10, TRUE, CURRENT_TIMESTAMP, 'V2-I06-SEED'),
    (md5('PIS-V2-QC-SLIDE-REPRINT-RATE')::uuid, 'SLIDE_REPRINT_RATE', '切片重打率', 'SLIDE_REPRINT_RATE', 0.05, 0.10, TRUE, CURRENT_TIMESTAMP, 'V2-I06-SEED')
ON CONFLICT (rule_code) DO NOTHING;

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b219', 'PIS_V2', 'V2-I06-C', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE SET version_code = 'V2-I06-C', recorded_at = CURRENT_TIMESTAMP;
