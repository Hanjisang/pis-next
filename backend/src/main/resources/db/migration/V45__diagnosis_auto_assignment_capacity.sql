ALTER TABLE pis_v2.assignment_rule
    ADD COLUMN IF NOT EXISTS daily_case_limit INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pis_v2.assignment_rule
    DROP CONSTRAINT IF EXISTS ck_v2_assignment_rule_daily_limit;
ALTER TABLE pis_v2.assignment_rule
    ADD CONSTRAINT ck_v2_assignment_rule_daily_limit CHECK (daily_case_limit >= 0);

UPDATE pis_v2.assignment_rule
   SET enabled = FALSE
 WHERE enabled = TRUE AND (doctor_id IS NULL OR BTRIM(doctor_id) = '');

ALTER TABLE pis_v2.assignment_rule
    DROP CONSTRAINT IF EXISTS ck_v2_assignment_rule_enabled_doctor;
ALTER TABLE pis_v2.assignment_rule
    ADD CONSTRAINT ck_v2_assignment_rule_enabled_doctor
        CHECK (enabled = FALSE OR (doctor_id IS NOT NULL AND BTRIM(doctor_id) <> ''));

ALTER TABLE pis_v2.responsibility_unit
    DROP CONSTRAINT IF EXISTS ck_v2_responsibility_source;
ALTER TABLE pis_v2.responsibility_unit
    ADD CONSTRAINT ck_v2_responsibility_source CHECK (assignment_source_code IN
        ('PUBLIC_POOL', 'MANUAL', 'SELF_CLAIM', 'REASSIGN', 'AUTO'));

CREATE TABLE IF NOT EXISTS pis_v2.diagnosis_auto_assignment_fact (
    id UUID PRIMARY KEY,
    responsibility_id UUID NOT NULL UNIQUE REFERENCES pis_v2.responsibility_unit(id),
    assignment_rule_id UUID NOT NULL REFERENCES pis_v2.assignment_rule(id),
    diagnosis_group_code VARCHAR(128) NOT NULL,
    matched_campus_code VARCHAR(128) NOT NULL,
    matched_department_code VARCHAR(256) NOT NULL,
    matched_site_code VARCHAR(500) NOT NULL,
    daily_assigned_count_before INTEGER NOT NULL,
    daily_case_limit_snapshot INTEGER NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    assigned_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_auto_assignment_count CHECK (daily_assigned_count_before >= 0),
    CONSTRAINT ck_v2_auto_assignment_limit CHECK (daily_case_limit_snapshot >= 0)
);

CREATE INDEX IF NOT EXISTS idx_v2_auto_assignment_rule
    ON pis_v2.diagnosis_auto_assignment_fact (assignment_rule_id, assigned_at);
CREATE INDEX IF NOT EXISTS idx_v2_auto_assignment_group
    ON pis_v2.diagnosis_auto_assignment_fact
       (organization_reference, diagnosis_group_code, assigned_at);
