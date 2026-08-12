-- SRS-FC02A: close application intake, registration acceptance, number correction,
-- cancellation history and registration printing without changing prior migrations.

ALTER TABLE pis_v2.pathology_application
    ADD COLUMN IF NOT EXISTS patient_info_source_code VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS patient_identity_no VARCHAR(128),
    ADD COLUMN IF NOT EXISTS visit_card_no VARCHAR(128),
    ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(128),
    ADD COLUMN IF NOT EXISTS age_value INTEGER,
    ADD COLUMN IF NOT EXISTS age_unit_code VARCHAR(16),
    ADD COLUMN IF NOT EXISTS ward_reference VARCHAR(256),
    ADD COLUMN IF NOT EXISTS bed_reference VARCHAR(128),
    ADD COLUMN IF NOT EXISTS surgery_name VARCHAR(1000);

ALTER TABLE pis_v2.pathology_application
    ADD CONSTRAINT ck_v2_application_patient_source
        CHECK (patient_info_source_code IN ('HIS', 'MANUAL', 'INTEGRATION')),
    ADD CONSTRAINT ck_v2_application_age
        CHECK ((age_value IS NULL AND age_unit_code IS NULL)
            OR (age_value >= 0 AND age_unit_code IN ('YEAR', 'MONTH', 'DAY'))),
    ADD CONSTRAINT ck_v2_application_visit_type
        CHECK (visit_type_code IS NULL OR visit_type_code IN
            ('OUTPATIENT', 'INPATIENT', 'EMERGENCY', 'PHYSICAL_EXAM', 'OTHER'));

ALTER TABLE pis_v2.pathology_application_item
    DROP CONSTRAINT IF EXISTS ck_v2_pathology_application_item_status;

ALTER TABLE pis_v2.pathology_application_item
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_by_ref VARCHAR(128),
    ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejected_by_ref VARCHAR(128),
    ADD COLUMN IF NOT EXISTS rejection_reason_code VARCHAR(128),
    ADD COLUMN IF NOT EXISTS rejection_reason_text VARCHAR(2000),
    ADD CONSTRAINT ck_v2_pathology_application_item_status CHECK
        (status_code IN ('PENDING', 'REGISTERED', 'REJECTED', 'CANCELLED'));

ALTER TABLE pis_v2.pathology_application_delivery
    ADD COLUMN IF NOT EXISTS incoming_specimen_reference VARCHAR(256),
    ADD COLUMN IF NOT EXISTS reason_code VARCHAR(128),
    ADD COLUMN IF NOT EXISTS patient_match BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS application_match BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS quantity_match BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS specimen_match BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS container_match BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS fixation_match BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_application_delivery_accepted
    ON pis_v2.pathology_application_delivery (application_item_id)
    WHERE verification_status_code = 'ACCEPTED';

ALTER TABLE pis_v2.pathology_application_barcode_print
    ADD COLUMN IF NOT EXISTS operation_code VARCHAR(16) NOT NULL DEFAULT 'PRINT',
    ADD COLUMN IF NOT EXISTS copies INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS rendered_label VARCHAR(4000),
    ADD CONSTRAINT ck_v2_application_barcode_operation CHECK (operation_code IN ('PRINT', 'REPRINT')),
    ADD CONSTRAINT ck_v2_application_barcode_copies CHECK (copies > 0);

ALTER TABLE pis_v2.pathology_registration_receipt_print
    ADD COLUMN IF NOT EXISTS operation_code VARCHAR(16) NOT NULL DEFAULT 'PRINT',
    ADD COLUMN IF NOT EXISTS copies INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS rendered_receipt VARCHAR(10000),
    ADD CONSTRAINT ck_v2_registration_receipt_operation CHECK (operation_code IN ('PRINT', 'REPRINT')),
    ADD CONSTRAINT ck_v2_registration_receipt_copies CHECK (copies > 0);

CREATE TABLE IF NOT EXISTS pis_v2.pathology_registration_label_print (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    specimen_id UUID NOT NULL REFERENCES pis_v2.specimen(id),
    pathology_no VARCHAR(128) NOT NULL,
    specimen_code VARCHAR(128) NOT NULL,
    operation_code VARCHAR(16) NOT NULL,
    copies INTEGER NOT NULL,
    printer_profile_code VARCHAR(128) NOT NULL,
    rendered_label VARCHAR(4000) NOT NULL,
    result_code VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(2000),
    requested_at TIMESTAMPTZ NOT NULL,
    requested_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_registration_label_operation CHECK (operation_code IN ('PRINT', 'REPRINT')),
    CONSTRAINT ck_v2_registration_label_copies CHECK (copies > 0),
    CONSTRAINT ck_v2_registration_label_result CHECK (result_code IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_v2_registration_label_case
    ON pis_v2.pathology_registration_label_print (case_id, requested_at);

ALTER TABLE pis_v2.case_context_snapshot
    ADD COLUMN IF NOT EXISTS patient_name VARCHAR(256),
    ADD COLUMN IF NOT EXISTS patient_sex_code VARCHAR(32),
    ADD COLUMN IF NOT EXISTS patient_birth_date DATE,
    ADD COLUMN IF NOT EXISTS age_value INTEGER,
    ADD COLUMN IF NOT EXISTS age_unit_code VARCHAR(16),
    ADD COLUMN IF NOT EXISTS visit_type_code VARCHAR(32),
    ADD COLUMN IF NOT EXISTS application_department VARCHAR(256),
    ADD COLUMN IF NOT EXISTS applicant_reference VARCHAR(256),
    ADD COLUMN IF NOT EXISTS clinical_diagnosis VARCHAR(4000),
    ADD COLUMN IF NOT EXISTS medical_history VARCHAR(10000),
    ADD COLUMN IF NOT EXISTS surgery_name VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS operation_finding VARCHAR(10000);

ALTER TABLE pis_v2.pathology_case
    DROP CONSTRAINT IF EXISTS uq_v2_case_no;

CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_case_no_active
    ON pis_v2.pathology_case (organization_reference, case_no)
    WHERE number_binding_active = TRUE;

CREATE TABLE IF NOT EXISTS pis_v2.pathology_number_history (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    old_pathology_no VARCHAR(128) NOT NULL,
    new_pathology_no VARCHAR(128),
    operation_code VARCHAR(32) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    changed_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_pathology_number_operation CHECK
        (operation_code IN ('CORRECTION', 'CANCELLATION_RELEASE')),
    CONSTRAINT ck_v2_pathology_number_correction CHECK
        ((operation_code = 'CORRECTION' AND new_pathology_no IS NOT NULL)
         OR operation_code = 'CANCELLATION_RELEASE')
);

CREATE INDEX IF NOT EXISTS idx_v2_pathology_number_history_lookup
    ON pis_v2.pathology_number_history (organization_reference, old_pathology_no, changed_at);
CREATE INDEX IF NOT EXISTS idx_v2_pathology_number_history_case
    ON pis_v2.pathology_number_history (case_id, changed_at);

UPDATE pis_v2.schema_metadata
   SET version_code = 'APPLICATION-REGISTRATION-CLOSURE', recorded_at = CURRENT_TIMESTAMP
 WHERE schema_code = 'PIS_V2';
