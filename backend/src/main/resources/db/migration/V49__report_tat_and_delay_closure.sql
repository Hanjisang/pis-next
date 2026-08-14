-- RPT-022/RPT-035/STAT-007/STAT-008/CFG-008: report TAT policy and delay facts.
-- No clinical threshold is seeded. Each hospital must explicitly configure and enable a policy.

CREATE TABLE pis_v2.report_tat_policy (
    id UUID PRIMARY KEY,
    organization_reference VARCHAR(128) NOT NULL,
    business_type_id UUID NOT NULL REFERENCES pis_v2.business_type(id),
    start_anchor_code VARCHAR(32) NOT NULL,
    warning_minutes INTEGER NOT NULL,
    target_minutes INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_report_tat_policy UNIQUE (organization_reference, business_type_id),
    CONSTRAINT ck_v2_report_tat_anchor CHECK (start_anchor_code = 'CASE_REGISTERED'),
    CONSTRAINT ck_v2_report_tat_thresholds CHECK
        (warning_minutes > 0 AND target_minutes > warning_minutes AND target_minutes <= 525600),
    CONSTRAINT ck_v2_report_tat_version CHECK (configuration_version > 0)
);

CREATE TABLE pis_v2.report_delay_declaration (
    id UUID PRIMARY KEY,
    organization_reference VARCHAR(128) NOT NULL,
    diagnosis_id UUID NOT NULL REFERENCES pis_v2.diagnosis(id),
    policy_id UUID NOT NULL REFERENCES pis_v2.report_tat_policy(id),
    policy_version INTEGER NOT NULL,
    tat_due_at TIMESTAMPTZ NOT NULL,
    reason_code VARCHAR(32) NOT NULL,
    reason_detail VARCHAR(1000) NOT NULL,
    expected_sign_at TIMESTAMPTZ NOT NULL,
    declared_at TIMESTAMPTZ NOT NULL,
    declared_by_ref VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    resolved_at TIMESTAMPTZ,
    resolved_by_ref VARCHAR(128),
    resolution_note VARCHAR(1000),
    resolution_idempotency_key VARCHAR(128),
    concurrency_version BIGINT NOT NULL,
    CONSTRAINT uq_v2_report_delay_idempotency UNIQUE (organization_reference, idempotency_key),
    CONSTRAINT uq_v2_report_delay_resolution_key UNIQUE (organization_reference, resolution_idempotency_key),
    CONSTRAINT ck_v2_report_delay_reason CHECK (reason_code IN
        ('TECHNICAL_WORK', 'CONSULTATION', 'MATERIAL_PENDING', 'CLINICAL_INFORMATION', 'OTHER')),
    CONSTRAINT ck_v2_report_delay_expected CHECK
        (expected_sign_at > declared_at AND expected_sign_at > tat_due_at),
    CONSTRAINT ck_v2_report_delay_resolution CHECK
        ((resolved_at IS NULL AND resolved_by_ref IS NULL AND resolution_note IS NULL AND resolution_idempotency_key IS NULL)
         OR
         (resolved_at IS NOT NULL AND resolved_by_ref IS NOT NULL AND resolution_note IS NOT NULL)),
    CONSTRAINT ck_v2_report_delay_version CHECK (policy_version > 0 AND concurrency_version >= 0)
);

CREATE UNIQUE INDEX uq_v2_report_delay_active
    ON pis_v2.report_delay_declaration (diagnosis_id)
    WHERE resolved_at IS NULL;

CREATE INDEX ix_v2_report_delay_scope
    ON pis_v2.report_delay_declaration (organization_reference, declared_at DESC);

UPDATE pis_v2.schema_metadata
SET version_code = 'REPORT-TAT-DELAY-CLOSURE', recorded_at = CURRENT_TIMESTAMP
WHERE schema_code = 'PIS_V2';
