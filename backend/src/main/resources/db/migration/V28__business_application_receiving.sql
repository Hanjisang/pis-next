-- Phase A: application, delivery verification, and registration facts.
-- Application is intentionally independent from Case: one application item may
-- create one Case, while one Application may create multiple Cases.

CREATE TABLE IF NOT EXISTS pis_v2.pathology_application (
    id UUID PRIMARY KEY,
    application_no VARCHAR(128) NOT NULL,
    source_type_code VARCHAR(32) NOT NULL,
    source_system_code VARCHAR(128) NOT NULL,
    patient_reference VARCHAR(256) NOT NULL,
    patient_name VARCHAR(256),
    patient_sex_code VARCHAR(32),
    patient_birth_date DATE,
    visit_reference VARCHAR(256),
    visit_type_code VARCHAR(32),
    application_department VARCHAR(256),
    applicant_reference VARCHAR(256),
    applied_at TIMESTAMPTZ NOT NULL,
    clinical_diagnosis VARCHAR(4000),
    medical_history VARCHAR(10000),
    operation_finding VARCHAR(10000),
    examination_purpose VARCHAR(4000),
    specimen_description VARCHAR(10000),
    note VARCHAR(10000),
    status_code VARCHAR(32) NOT NULL,
    cancelled_at TIMESTAMPTZ,
    cancelled_by_ref VARCHAR(128),
    cancellation_reason VARCHAR(2000),
    organization_reference VARCHAR(128) NOT NULL,
    concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_pathology_application_no UNIQUE (organization_reference, application_no),
    CONSTRAINT ck_v2_pathology_application_source CHECK (source_type_code IN ('HIS', 'CLINICAL', 'MANUAL')),
    CONSTRAINT ck_v2_pathology_application_status CHECK (status_code IN ('RECEIVED', 'PARTIALLY_REGISTERED', 'REGISTERED', 'CANCELLED')),
    CONSTRAINT ck_v2_pathology_application_version CHECK (concurrency_version >= 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.pathology_application_item (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES pis_v2.pathology_application(id),
    external_item_code VARCHAR(128) NOT NULL,
    item_name VARCHAR(256),
    mapping_id UUID REFERENCES pis_v2.application_item_mapping(id),
    business_type_id UUID REFERENCES pis_v2.business_type(id),
    specimen_kind_code VARCHAR(64),
    specimen_description VARCHAR(4000),
    sequence_no INTEGER NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_pathology_application_item_status CHECK (status_code IN ('PENDING', 'REGISTERED', 'REJECTED')),
    CONSTRAINT ck_v2_pathology_application_item_sequence CHECK (sequence_no > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_pathology_application_item_pending_sequence
    ON pis_v2.pathology_application_item (application_id, sequence_no)
    WHERE status_code = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_v2_pathology_application_queue
    ON pis_v2.pathology_application (organization_reference, status_code, applied_at);
CREATE INDEX IF NOT EXISTS idx_v2_pathology_application_item_queue
    ON pis_v2.pathology_application_item (application_id, status_code, sequence_no);

CREATE TABLE IF NOT EXISTS pis_v2.pathology_application_case (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES pis_v2.pathology_application(id),
    application_item_id UUID NOT NULL REFERENCES pis_v2.pathology_application_item(id),
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    linked_at TIMESTAMPTZ NOT NULL,
    linked_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_pathology_application_item_case UNIQUE (application_item_id),
    CONSTRAINT uq_v2_pathology_application_case UNIQUE (case_id)
);

CREATE TABLE IF NOT EXISTS pis_v2.pathology_application_delivery (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES pis_v2.pathology_application(id),
    application_item_id UUID REFERENCES pis_v2.pathology_application_item(id),
    specimen_label_code VARCHAR(256),
    patient_reference VARCHAR(256) NOT NULL,
    actual_specimen_description VARCHAR(10000),
    verification_status_code VARCHAR(32) NOT NULL,
    rejection_reason VARCHAR(2000),
    delivered_by_ref VARCHAR(128) NOT NULL,
    delivered_at TIMESTAMPTZ NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_pathology_application_delivery_status CHECK (
        verification_status_code IN ('ACCEPTED', 'SUPPLEMENT_REQUIRED', 'REJECTED')
    )
);

CREATE INDEX IF NOT EXISTS idx_v2_pathology_application_delivery
    ON pis_v2.pathology_application_delivery (organization_reference, delivered_at);

CREATE TABLE IF NOT EXISTS pis_v2.pathology_application_barcode_print (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES pis_v2.pathology_application(id),
    application_item_id UUID REFERENCES pis_v2.pathology_application_item(id),
    barcode_value VARCHAR(256) NOT NULL,
    print_version INTEGER NOT NULL,
    printer_profile_code VARCHAR(128) NOT NULL,
    result_code VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(2000),
    requested_at TIMESTAMPTZ NOT NULL,
    requested_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_pathology_application_barcode_result CHECK (result_code IN ('SUCCESS', 'FAILED')),
    CONSTRAINT ck_v2_pathology_application_barcode_version CHECK (print_version > 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.pathology_registration_receipt_print (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES pis_v2.pathology_application(id),
    case_id UUID REFERENCES pis_v2.pathology_case(id),
    receipt_kind_code VARCHAR(32) NOT NULL,
    printer_profile_code VARCHAR(128) NOT NULL,
    result_code VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(2000),
    requested_at TIMESTAMPTZ NOT NULL,
    requested_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_registration_receipt_kind CHECK (receipt_kind_code IN ('REGISTRATION', 'PATIENT')),
    CONSTRAINT ck_v2_registration_receipt_result CHECK (result_code IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_v2_registration_receipt_application
    ON pis_v2.pathology_registration_receipt_print (application_id, requested_at);
