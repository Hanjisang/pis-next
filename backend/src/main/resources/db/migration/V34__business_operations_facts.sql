-- Business operation facts for the non-linear supporting domains. These tables
-- keep each real fact independent; none of them is a generic task/workflow table.

CREATE TABLE IF NOT EXISTS pis_v2.notification (
    id UUID PRIMARY KEY,
    recipient_reference VARCHAR(256) NOT NULL,
    type_code VARCHAR(64) NOT NULL,
    title VARCHAR(512) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    business_path VARCHAR(1024),
    priority_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ,
    organization_reference VARCHAR(128) NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_v2_notification_recipient ON pis_v2.notification
    (organization_reference, recipient_reference, read_at, created_at);

CREATE TABLE IF NOT EXISTS pis_v2.staff_schedule (
    id UUID PRIMARY KEY,
    staff_reference VARCHAR(256) NOT NULL,
    schedule_date DATE NOT NULL,
    shift_code VARCHAR(64) NOT NULL,
    work_area VARCHAR(256) NOT NULL,
    note VARCHAR(2000),
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_staff_schedule UNIQUE (organization_reference, staff_reference, schedule_date, shift_code)
);

CREATE TABLE IF NOT EXISTS pis_v2.quality_document (
    id UUID PRIMARY KEY,
    title VARCHAR(512) NOT NULL,
    document_no VARCHAR(128) NOT NULL,
    category_code VARCHAR(64) NOT NULL,
    version_label VARCHAR(64) NOT NULL,
    effective_at TIMESTAMPTZ,
    owner_reference VARCHAR(256) NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    content_reference VARCHAR(1024) NOT NULL,
    previous_document_id UUID REFERENCES pis_v2.quality_document(id),
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    reviewed_at TIMESTAMPTZ,
    reviewed_by_ref VARCHAR(128),
    archived_at TIMESTAMPTZ,
    CONSTRAINT uq_v2_quality_document_version UNIQUE (organization_reference, document_no, version_label),
    CONSTRAINT ck_v2_quality_document_status CHECK (status_code IN ('DRAFT', 'REVIEW', 'PUBLISHED', 'ARCHIVED'))
);

CREATE TABLE IF NOT EXISTS pis_v2.equipment (
    id UUID PRIMARY KEY,
    equipment_code VARCHAR(128) NOT NULL,
    name VARCHAR(256) NOT NULL,
    category_code VARCHAR(128) NOT NULL,
    manufacturer VARCHAR(256),
    model VARCHAR(256),
    serial_no VARCHAR(256),
    location_reference VARCHAR(256),
    custodian_reference VARCHAR(256),
    purchase_date DATE,
    warranty_until DATE,
    calibration_due_at DATE,
    status_code VARCHAR(32) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_equipment_code UNIQUE (organization_reference, equipment_code)
);
CREATE TABLE IF NOT EXISTS pis_v2.equipment_event (
    id UUID PRIMARY KEY,
    equipment_id UUID NOT NULL REFERENCES pis_v2.equipment(id),
    event_code VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    operator_reference VARCHAR(256) NOT NULL,
    description VARCHAR(4000),
    amount NUMERIC(18,2),
    organization_reference VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis_v2.consumable_catalog (
    id UUID PRIMARY KEY,
    material_code VARCHAR(128) NOT NULL,
    name VARCHAR(256) NOT NULL,
    category_code VARCHAR(128) NOT NULL,
    specification VARCHAR(512),
    unit_code VARCHAR(64) NOT NULL,
    manufacturer VARCHAR(256),
    supplier VARCHAR(256),
    hazardous BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_consumable_code UNIQUE (organization_reference, material_code)
);
CREATE TABLE IF NOT EXISTS pis_v2.consumable_batch (
    id UUID PRIMARY KEY,
    catalog_id UUID NOT NULL REFERENCES pis_v2.consumable_catalog(id),
    batch_no VARCHAR(128) NOT NULL,
    expiry_date DATE,
    storage_location VARCHAR(256),
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_v2_consumable_batch UNIQUE (catalog_id, batch_no)
);
CREATE TABLE IF NOT EXISTS pis_v2.consumable_transaction (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES pis_v2.consumable_batch(id),
    direction_code VARCHAR(32) NOT NULL,
    quantity NUMERIC(18,3) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    source_reference VARCHAR(256),
    operator_reference VARCHAR(256) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_consumable_transaction_direction CHECK (direction_code IN ('INBOUND', 'OUTBOUND', 'ADJUSTMENT')),
    CONSTRAINT ck_v2_consumable_transaction_quantity CHECK (quantity > 0)
);
CREATE TABLE IF NOT EXISTS pis_v2.consumable_requisition (
    id UUID PRIMARY KEY,
    request_no VARCHAR(128) NOT NULL,
    requester_reference VARCHAR(256) NOT NULL,
    department_reference VARCHAR(256) NOT NULL,
    purpose VARCHAR(2000) NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    decided_by_ref VARCHAR(128),
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_consumable_requisition_no UNIQUE (organization_reference, request_no),
    CONSTRAINT ck_v2_consumable_requisition_status CHECK (status_code IN ('REQUESTED', 'APPROVED', 'REJECTED', 'FULFILLED'))
);
CREATE TABLE IF NOT EXISTS pis_v2.consumable_requisition_item (
    id UUID PRIMARY KEY,
    requisition_id UUID NOT NULL REFERENCES pis_v2.consumable_requisition(id),
    catalog_id UUID NOT NULL REFERENCES pis_v2.consumable_catalog(id),
    quantity NUMERIC(18,3) NOT NULL,
    CONSTRAINT ck_v2_consumable_requisition_quantity CHECK (quantity > 0)
);
CREATE TABLE IF NOT EXISTS pis_v2.consumable_quality_evaluation (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES pis_v2.consumable_batch(id),
    result_code VARCHAR(32) NOT NULL,
    note VARCHAR(2000),
    evaluated_at TIMESTAMPTZ NOT NULL,
    evaluated_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis_v2.procurement_request (
    id UUID PRIMARY KEY,
    request_no VARCHAR(128) NOT NULL,
    requester_reference VARCHAR(256) NOT NULL,
    department_reference VARCHAR(256) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_procurement_request_no UNIQUE (organization_reference, request_no),
    CONSTRAINT ck_v2_procurement_status CHECK (status_code IN ('REQUESTED', 'APPROVED', 'REJECTED', 'ORDERED', 'RECEIVED', 'CLOSED'))
);
CREATE TABLE IF NOT EXISTS pis_v2.procurement_item (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES pis_v2.procurement_request(id),
    material_reference VARCHAR(256) NOT NULL,
    quantity NUMERIC(18,3) NOT NULL,
    estimated_amount NUMERIC(18,2) NOT NULL,
    supplier VARCHAR(256),
    CONSTRAINT ck_v2_procurement_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_v2_procurement_item_amount CHECK (estimated_amount >= 0)
);
CREATE TABLE IF NOT EXISTS pis_v2.procurement_approval (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES pis_v2.procurement_request(id),
    approval_sequence INTEGER NOT NULL,
    approver_reference VARCHAR(256) NOT NULL,
    decision_code VARCHAR(32) NOT NULL,
    comment VARCHAR(2000),
    decided_at TIMESTAMPTZ NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_procurement_decision CHECK (decision_code IN ('APPROVED', 'REJECTED'))
);
CREATE TABLE IF NOT EXISTS pis_v2.procurement_attachment (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES pis_v2.procurement_request(id),
    attachment_kind_code VARCHAR(64) NOT NULL,
    storage_reference VARCHAR(1024) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis_v2.department_space (
    id UUID PRIMARY KEY,
    parent_id UUID REFERENCES pis_v2.department_space(id),
    space_code VARCHAR(128) NOT NULL,
    name VARCHAR(256) NOT NULL,
    zone_code VARCHAR(64) NOT NULL,
    area_value NUMERIC(18,3),
    administrator_reference VARCHAR(256),
    description VARCHAR(2000),
    view_reference VARCHAR(1024),
    active BOOLEAN NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_department_space_code UNIQUE (organization_reference, space_code)
);
CREATE TABLE IF NOT EXISTS pis_v2.space_environment_record (
    id UUID PRIMARY KEY,
    space_id UUID NOT NULL REFERENCES pis_v2.department_space(id),
    metric_code VARCHAR(64) NOT NULL,
    measure_value NUMERIC(18,6) NOT NULL,
    unit_code VARCHAR(32) NOT NULL,
    measured_at TIMESTAMPTZ NOT NULL,
    source_reference VARCHAR(256),
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.space_safety_check (
    id UUID PRIMARY KEY,
    space_id UUID NOT NULL REFERENCES pis_v2.department_space(id),
    check_code VARCHAR(64) NOT NULL,
    result_code VARCHAR(32) NOT NULL,
    note VARCHAR(2000),
    checked_at TIMESTAMPTZ NOT NULL,
    checked_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis_v2.critical_value (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    value_type_code VARCHAR(128) NOT NULL,
    grade_code VARCHAR(32) NOT NULL,
    trigger_reference VARCHAR(512),
    status_code VARCHAR(32) NOT NULL,
    due_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_critical_value_status CHECK (status_code IN ('OPEN', 'ACKNOWLEDGED', 'COMPLETED', 'CANCELLED'))
);
CREATE TABLE IF NOT EXISTS pis_v2.critical_value_notification (
    id UUID PRIMARY KEY,
    critical_value_id UUID NOT NULL REFERENCES pis_v2.critical_value(id),
    department_reference VARCHAR(256) NOT NULL,
    recipient_reference VARCHAR(256) NOT NULL,
    method_code VARCHAR(64) NOT NULL,
    notified_at TIMESTAMPTZ NOT NULL,
    notified_by_ref VARCHAR(128) NOT NULL,
    acknowledgement_at TIMESTAMPTZ,
    acknowledged_by_ref VARCHAR(128),
    organization_reference VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.critical_value_feedback (
    id UUID PRIMARY KEY,
    critical_value_id UUID NOT NULL REFERENCES pis_v2.critical_value(id),
    content VARCHAR(4000) NOT NULL,
    feedback_at TIMESTAMPTZ NOT NULL,
    feedback_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis_v2.report_distribution (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES pis_v2.report(id),
    target_code VARCHAR(64) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    status_code VARCHAR(32) NOT NULL,
    retry_count INTEGER NOT NULL,
    last_error VARCHAR(2000),
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_report_distribution_status CHECK (status_code IN ('REQUESTED', 'SENT', 'RETRY_PENDING', 'FAILED')),
    CONSTRAINT ck_v2_report_distribution_retry CHECK (retry_count >= 0),
    CONSTRAINT ck_v2_report_distribution_result CHECK (
        (status_code = 'SENT' AND sent_at IS NOT NULL AND last_error IS NULL)
        OR (status_code <> 'SENT' AND sent_at IS NULL)
    )
);
CREATE TABLE IF NOT EXISTS pis_v2.report_print_record (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES pis_v2.report(id),
    identity_reference VARCHAR(256) NOT NULL,
    terminal_reference VARCHAR(256),
    printer_reference VARCHAR(256),
    printed_at TIMESTAMPTZ NOT NULL,
    result_code VARCHAR(32) NOT NULL,
    copy_count INTEGER NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_report_print_copy_count CHECK (copy_count > 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.common_address (
    id UUID PRIMARY KEY,
    address_name VARCHAR(256) NOT NULL,
    recipient_name VARCHAR(256) NOT NULL,
    phone VARCHAR(128),
    address_text VARCHAR(2000) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS pis_v2.logistics_package (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    consultation_id UUID,
    courier_company VARCHAR(256) NOT NULL,
    tracking_no VARCHAR(256),
    sender_reference VARCHAR(256) NOT NULL,
    recipient_reference VARCHAR(256) NOT NULL,
    address_text VARCHAR(2000) NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    sent_at TIMESTAMPTZ,
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_logistics_package_status CHECK (status_code IN ('DRAFT', 'SENT', 'IN_TRANSIT', 'DELIVERED', 'DELAYED', 'LOST', 'RETURNED', 'DAMAGED'))
);
CREATE TABLE IF NOT EXISTS pis_v2.logistics_package_item (
    id UUID PRIMARY KEY,
    package_id UUID NOT NULL REFERENCES pis_v2.logistics_package(id),
    block_id UUID REFERENCES pis_v2.block(id),
    slide_id UUID REFERENCES pis_v2.slide(id),
    document_reference VARCHAR(1024),
    CONSTRAINT ck_v2_logistics_item_one_kind CHECK (
        (block_id IS NOT NULL)::integer + (slide_id IS NOT NULL)::integer
            + (document_reference IS NOT NULL)::integer = 1
    )
);
CREATE TABLE IF NOT EXISTS pis_v2.logistics_event (
    id UUID PRIMARY KEY,
    package_id UUID NOT NULL REFERENCES pis_v2.logistics_package(id),
    status_code VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    note VARCHAR(2000),
    recorded_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_logistics_event_status CHECK (
        status_code IN ('SENT', 'IN_TRANSIT', 'DELIVERED', 'DELAYED', 'LOST', 'RETURNED', 'DAMAGED')
    )
);

CREATE TABLE IF NOT EXISTS pis_v2.molecular_project (
    id UUID PRIMARY KEY,
    project_code VARCHAR(128) NOT NULL,
    project_name VARCHAR(256) NOT NULL,
    project_type_code VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_molecular_project UNIQUE (organization_reference, project_code)
);
CREATE TABLE IF NOT EXISTS pis_v2.molecular_instrument (
    id UUID PRIMARY KEY,
    instrument_code VARCHAR(128) NOT NULL,
    name VARCHAR(256) NOT NULL,
    adapter_code VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_molecular_instrument UNIQUE (organization_reference, instrument_code)
);
CREATE TABLE IF NOT EXISTS pis_v2.molecular_reagent_kit (
    id UUID PRIMARY KEY,
    kit_code VARCHAR(128) NOT NULL,
    manufacturer VARCHAR(256),
    batch_no VARCHAR(128) NOT NULL,
    expiry_date DATE,
    enabled BOOLEAN NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_molecular_reagent UNIQUE (organization_reference, kit_code, batch_no)
);
CREATE TABLE IF NOT EXISTS pis_v2.molecular_test (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    specimen_id UUID REFERENCES pis_v2.specimen(id),
    project_id UUID NOT NULL REFERENCES pis_v2.molecular_project(id),
    detection_no VARCHAR(128) NOT NULL,
    instrument_id UUID REFERENCES pis_v2.molecular_instrument(id),
    reagent_kit_id UUID REFERENCES pis_v2.molecular_reagent_kit(id),
    raw_data_reference VARCHAR(1024),
    structured_result TEXT,
    analysis_result TEXT,
    status_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    created_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_molecular_detection_no UNIQUE (organization_reference, detection_no),
    CONSTRAINT ck_v2_molecular_test_status CHECK (status_code IN ('REQUESTED', 'RUNNING', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS pis_v2.digital_slide_archive (
    id UUID PRIMARY KEY,
    digital_slide_id UUID NOT NULL REFERENCES pis_v2.digital_slide(id),
    storage_path VARCHAR(2048) NOT NULL,
    storage_tier VARCHAR(64) NOT NULL,
    filename VARCHAR(512) NOT NULL,
    format_code VARCHAR(64) NOT NULL,
    pathology_no VARCHAR(128),
    slide_no VARCHAR(128),
    patient_reference VARCHAR(256),
    organ_reference VARCHAR(256),
    integrity_digest VARCHAR(256),
    status_code VARCHAR(32) NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL,
    restored_at TIMESTAMPTZ,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_digital_slide_archive UNIQUE (digital_slide_id),
    CONSTRAINT ck_v2_digital_slide_archive_status CHECK (status_code IN ('INDEXED', 'ARCHIVED', 'RESTORED', 'INTEGRITY_ERROR'))
);

CREATE TABLE IF NOT EXISTS pis_v2.regional_share (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    receiving_organization VARCHAR(512) NOT NULL,
    receiving_doctor VARCHAR(256),
    expires_at TIMESTAMPTZ,
    patient_authorized BOOLEAN NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    requested_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_regional_share_status CHECK (status_code IN ('REQUESTED', 'ACTIVE', 'EXPIRED', 'CANCELLED'))
);
CREATE TABLE IF NOT EXISTS pis_v2.regional_share_item (
    id UUID PRIMARY KEY,
    share_id UUID NOT NULL REFERENCES pis_v2.regional_share(id),
    report_id UUID REFERENCES pis_v2.report(id),
    digital_slide_id UUID REFERENCES pis_v2.digital_slide(id),
    attachment_reference VARCHAR(1024),
    CONSTRAINT ck_v2_regional_share_item_kind CHECK (
        (report_id IS NOT NULL)::integer + (digital_slide_id IS NOT NULL)::integer
            + (attachment_reference IS NOT NULL)::integer = 1
    )
);
CREATE TABLE IF NOT EXISTS pis_v2.regional_share_access (
    id UUID PRIMARY KEY,
    share_id UUID NOT NULL REFERENCES pis_v2.regional_share(id),
    accessor_reference VARCHAR(256) NOT NULL,
    accessed_at TIMESTAMPTZ NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis_v2.income_fact (
    id UUID PRIMARY KEY,
    case_id UUID REFERENCES pis_v2.pathology_case(id),
    project_code VARCHAR(128) NOT NULL,
    amount NUMERIC(18,2) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    source_reference VARCHAR(256) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_income_amount CHECK (amount >= 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.migration_job (
    id UUID PRIMARY KEY,
    source_code VARCHAR(128) NOT NULL,
    mode_code VARCHAR(32) NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_migration_job_mode CHECK (mode_code IN ('IMPORT', 'READ_ONLY')),
    CONSTRAINT ck_v2_migration_job_status CHECK (status_code IN ('CREATED', 'RUNNING', 'COMPLETED', 'FAILED', 'READ_ONLY')),
    CONSTRAINT ck_v2_migration_job_timing CHECK (completed_at IS NULL OR started_at IS NOT NULL)
);
CREATE TABLE IF NOT EXISTS pis_v2.migration_record (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES pis_v2.migration_job(id),
    legacy_type VARCHAR(128) NOT NULL,
    legacy_key VARCHAR(256) NOT NULL,
    local_type VARCHAR(128),
    local_id UUID,
    record_status VARCHAR(32) NOT NULL,
    raw_reference VARCHAR(1024),
    mapped_at TIMESTAMPTZ,
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_migration_record_legacy UNIQUE (job_id, legacy_type, legacy_key),
    CONSTRAINT ck_v2_migration_record_status CHECK (record_status IN ('PENDING', 'MAPPED', 'SKIPPED', 'FAILED'))
);
CREATE TABLE IF NOT EXISTS pis_v2.migration_error (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES pis_v2.migration_job(id),
    record_id UUID REFERENCES pis_v2.migration_record(id),
    error_code VARCHAR(128) NOT NULL,
    error_message VARCHAR(4000) NOT NULL,
    retry_count INTEGER NOT NULL,
    resolved_at TIMESTAMPTZ,
    resolved_by_ref VARCHAR(128),
    organization_reference VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_migration_error_retry CHECK (retry_count >= 0),
    CONSTRAINT ck_v2_migration_error_resolution CHECK (
        (resolved_at IS NULL AND resolved_by_ref IS NULL)
        OR (resolved_at IS NOT NULL AND resolved_by_ref IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS ix_v2_critical_value_case ON pis_v2.critical_value (case_id, created_at);
CREATE INDEX IF NOT EXISTS ix_v2_critical_value_notification ON pis_v2.critical_value_notification (critical_value_id, notified_at);
CREATE INDEX IF NOT EXISTS ix_v2_report_distribution_report ON pis_v2.report_distribution (report_id, requested_at);
CREATE INDEX IF NOT EXISTS ix_v2_logistics_package_case ON pis_v2.logistics_package (case_id, created_at);
CREATE INDEX IF NOT EXISTS ix_v2_logistics_event_package ON pis_v2.logistics_event (package_id, occurred_at);
CREATE INDEX IF NOT EXISTS ix_v2_equipment_status ON pis_v2.equipment (organization_reference, status_code);
CREATE INDEX IF NOT EXISTS ix_v2_equipment_event_equipment ON pis_v2.equipment_event (equipment_id, occurred_at);
CREATE INDEX IF NOT EXISTS ix_v2_consumable_transaction_batch ON pis_v2.consumable_transaction (batch_id, occurred_at);
CREATE INDEX IF NOT EXISTS ix_v2_procurement_approval_request ON pis_v2.procurement_approval (request_id, approval_sequence);
CREATE INDEX IF NOT EXISTS ix_v2_molecular_test_queue ON pis_v2.molecular_test (organization_reference, status_code, created_at);
CREATE INDEX IF NOT EXISTS ix_v2_regional_share_case ON pis_v2.regional_share (case_id, requested_at);
CREATE INDEX IF NOT EXISTS ix_v2_migration_record_job ON pis_v2.migration_record (job_id, record_status);
CREATE INDEX IF NOT EXISTS ix_v2_migration_error_job ON pis_v2.migration_error (job_id, resolved_at);

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b234', 'PIS_V2', 'BUSINESS-OPERATIONS-FACTS', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE SET version_code = 'BUSINESS-OPERATIONS-FACTS', recorded_at = CURRENT_TIMESTAMP;
