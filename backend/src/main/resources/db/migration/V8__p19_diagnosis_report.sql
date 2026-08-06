CREATE TABLE IF NOT EXISTS pis.p19_diagnosis_task (
    id UUID PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL,
    case_id UUID NOT NULL,
    pathology_modality_code VARCHAR(32) NOT NULL,
    task_category_code VARCHAR(32) NOT NULL,
    priority_code VARCHAR(32) NOT NULL,
    task_state_code VARCHAR(40) NOT NULL,
    assigned_actor_ref VARCHAR(128),
    responsible_actor_ref VARCHAR(128),
    represented_actor_ref VARCHAR(128),
    organization_reference VARCHAR(128) NOT NULL,
    data_scope_code VARCHAR(64) NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    concurrency_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (organization_reference, task_no),
    CHECK (concurrency_version >= 0),
    CHECK (task_state_code IN ('P19-DIAGNOSIS-TASK-PLANNED','P19-DIAGNOSIS-TASK-ASSIGNED','P19-DIAGNOSIS-TASK-IN-PROGRESS','P19-DIAGNOSIS-TASK-AWAITING-REVIEW','P19-DIAGNOSIS-TASK-CLOSED','P19-DIAGNOSIS-TASK-HANDED-OFF'))
);

CREATE TABLE IF NOT EXISTS pis.p19_diagnosis_responsibility_history (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES pis.p19_diagnosis_task(id),
    from_actor_ref VARCHAR(128),
    to_actor_ref VARCHAR(128),
    action_code VARCHAR(40) NOT NULL,
    reason_text VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p19_diagnosis_work_draft (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL UNIQUE REFERENCES pis.p19_diagnosis_task(id),
    owner_actor_ref VARCHAR(128) NOT NULL,
    gross_description_reference VARCHAR(128),
    microscopic_description TEXT,
    diagnosis_conclusion TEXT,
    supplementary_note TEXT,
    structured_items TEXT,
    terminology_reference VARCHAR(128),
    concurrency_version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p19_diagnosis_opinion (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES pis.p19_diagnosis_task(id),
    case_id UUID NOT NULL,
    diagnosis_type_code VARCHAR(32) NOT NULL,
    opinion_state_code VARCHAR(32) NOT NULL,
    current_version_no INTEGER NOT NULL,
    current_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (task_id, diagnosis_type_code),
    CHECK (current_version_no > 0),
    CHECK (opinion_state_code IN ('DRAFT','SUBMITTED','WITHDRAWN'))
);

CREATE TABLE IF NOT EXISTS pis.p19_diagnosis_opinion_version (
    id UUID PRIMARY KEY,
    opinion_id UUID NOT NULL REFERENCES pis.p19_diagnosis_opinion(id),
    version_no INTEGER NOT NULL,
    version_state_code VARCHAR(32) NOT NULL,
    gross_description_reference VARCHAR(128),
    microscopic_description TEXT NOT NULL,
    diagnosis_conclusion TEXT NOT NULL,
    supplementary_note TEXT,
    structured_items TEXT,
    terminology_reference VARCHAR(128),
    evidence_version_summary TEXT NOT NULL,
    submitted_by_ref VARCHAR(128) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    prior_version_id UUID,
    UNIQUE (opinion_id, version_no),
    CHECK (version_state_code IN ('DRAFT','SUBMITTED','WITHDRAWN'))
);

CREATE TABLE IF NOT EXISTS pis.p19_diagnosis_follow_up (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES pis.p19_diagnosis_task(id),
    target_opinion_version_id UUID NOT NULL REFERENCES pis.p19_diagnosis_opinion_version(id),
    follow_up_actor_ref VARCHAR(128) NOT NULL,
    follow_up_opinion TEXT NOT NULL,
    consistency_code VARCHAR(32) NOT NULL,
    return_reason TEXT,
    adoption_recommendation TEXT,
    follow_up_state_code VARCHAR(32) NOT NULL,
    version_no INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CHECK (follow_up_state_code IN ('DRAFT','SUBMITTED','ACCEPTED','RETURNED'))
);

CREATE TABLE IF NOT EXISTS pis.p19_diagnosis_review (
    id UUID PRIMARY KEY,
    target_opinion_version_id UUID REFERENCES pis.p19_diagnosis_opinion_version(id),
    target_report_content_version_id UUID,
    review_kind_code VARCHAR(32) NOT NULL,
    decision_code VARCHAR(32) NOT NULL,
    reviewer_actor_ref VARCHAR(128) NOT NULL,
    review_reason VARCHAR(2000),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (target_opinion_version_id, review_kind_code),
    UNIQUE (target_report_content_version_id, review_kind_code),
    CHECK (decision_code IN ('PENDING','APPROVED','REJECTED')),
    CHECK ((target_opinion_version_id IS NOT NULL) OR (target_report_content_version_id IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS pis.p19_report (
    id UUID PRIMARY KEY,
    report_no VARCHAR(64) NOT NULL,
    case_id UUID NOT NULL,
    report_type_code VARCHAR(32) NOT NULL,
    report_state_code VARCHAR(32) NOT NULL,
    current_effective_version_id UUID,
    next_version_no INTEGER NOT NULL DEFAULT 1,
    concurrency_version BIGINT NOT NULL DEFAULT 0,
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (organization_reference, report_no),
    CHECK (next_version_no > 0),
    CHECK (report_state_code IN ('DRAFT','IN-REVIEW','SIGNED','WITHDRAWN'))
);

CREATE TABLE IF NOT EXISTS pis.p19_report_content_version (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES pis.p19_report(id),
    version_no INTEGER NOT NULL,
    content_state_code VARCHAR(32) NOT NULL,
    patient_snapshot TEXT NOT NULL,
    encounter_snapshot TEXT NOT NULL,
    case_no_snapshot VARCHAR(128) NOT NULL,
    specimen_material_summary TEXT NOT NULL,
    clinical_information TEXT,
    specimen_information TEXT,
    gross_description TEXT,
    microscopic_description TEXT,
    diagnosis_conclusion TEXT NOT NULL,
    supplementary_note TEXT,
    technical_result_reference_summary TEXT,
    diagnosis_version_id UUID NOT NULL REFERENCES pis.p19_diagnosis_opinion_version(id),
    template_logic_version VARCHAR(64) NOT NULL,
    formed_by_ref VARCHAR(128) NOT NULL,
    formed_at TIMESTAMPTZ NOT NULL,
    prior_version_id UUID,
    UNIQUE (report_id, version_no),
    CHECK (content_state_code IN ('DRAFT','SUBMITTED','SIGNED','RETIRED','WITHDRAWN'))
);

CREATE TABLE IF NOT EXISTS pis.p19_report_section_version (
    id UUID PRIMARY KEY,
    report_content_version_id UUID NOT NULL REFERENCES pis.p19_report_content_version(id),
    section_code VARCHAR(48) NOT NULL,
    body_text TEXT NOT NULL,
    source_code VARCHAR(32) NOT NULL,
    adopted_state_code VARCHAR(32) NOT NULL,
    formed_by_ref VARCHAR(128) NOT NULL,
    formed_at TIMESTAMPTZ NOT NULL,
    UNIQUE (report_content_version_id, section_code)
);

CREATE TABLE IF NOT EXISTS pis.p19_signing_fact (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES pis.p19_report(id),
    report_content_version_id UUID NOT NULL UNIQUE REFERENCES pis.p19_report_content_version(id),
    signing_actor_ref VARCHAR(128) NOT NULL,
    actual_operator_ref VARCHAR(128) NOT NULL,
    responsible_actor_ref VARCHAR(128) NOT NULL,
    signed_at TIMESTAMPTZ NOT NULL,
    enhanced_authentication_reference VARCHAR(128) NOT NULL,
    second_review_reference UUID NOT NULL REFERENCES pis.p19_diagnosis_review(id),
    task_responsibility_snapshot VARCHAR(128) NOT NULL,
    signed_object_version BIGINT NOT NULL,
    signing_result_code VARCHAR(32) NOT NULL,
    UNIQUE (report_id, report_content_version_id),
    CHECK (signing_result_code = 'SIGNED')
);

CREATE TABLE IF NOT EXISTS pis.p19_report_revision_relation (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES pis.p19_report(id),
    original_version_id UUID NOT NULL REFERENCES pis.p19_report_content_version(id),
    replacement_version_id UUID REFERENCES pis.p19_report_content_version(id),
    relation_type_code VARCHAR(32) NOT NULL,
    reason_text VARCHAR(2000) NOT NULL,
    current_effective_flag BOOLEAN NOT NULL DEFAULT FALSE,
    formed_by_ref VARCHAR(128) NOT NULL,
    formed_at TIMESTAMPTZ NOT NULL,
    CHECK (relation_type_code IN ('SUPPLEMENT','CORRECTION','WITHDRAWAL','RESIGN'))
);

CREATE TABLE IF NOT EXISTS pis.p19_report_supplement (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES pis.p19_report(id),
    original_version_id UUID NOT NULL REFERENCES pis.p19_report_content_version(id),
    supplement_version_id UUID REFERENCES pis.p19_report_content_version(id),
    reason_text VARCHAR(2000) NOT NULL,
    request_state_code VARCHAR(32) NOT NULL,
    requested_by_ref VARCHAR(128) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    CHECK (request_state_code IN ('REQUESTED','SUBMITTED','APPROVED','SIGNED','REJECTED'))
);

CREATE TABLE IF NOT EXISTS pis.p19_report_correction (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES pis.p19_report(id),
    original_version_id UUID NOT NULL REFERENCES pis.p19_report_content_version(id),
    correction_version_id UUID REFERENCES pis.p19_report_content_version(id),
    error_type_code VARCHAR(64) NOT NULL,
    reason_text VARCHAR(2000) NOT NULL,
    request_state_code VARCHAR(32) NOT NULL,
    requested_by_ref VARCHAR(128) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    CHECK (request_state_code IN ('REQUESTED','APPROVED','SIGNED','REJECTED'))
);

CREATE TABLE IF NOT EXISTS pis.p19_report_withdrawal_request (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES pis.p19_report(id),
    original_version_id UUID NOT NULL REFERENCES pis.p19_report_content_version(id),
    reason_text VARCHAR(2000) NOT NULL,
    request_state_code VARCHAR(32) NOT NULL,
    requested_by_ref VARCHAR(128) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    CHECK (request_state_code IN ('REQUESTED','APPROVED','EXECUTED','REJECTED'))
);

CREATE TABLE IF NOT EXISTS pis.p19_report_withdrawal_fact (
    id UUID PRIMARY KEY,
    withdrawal_request_id UUID NOT NULL UNIQUE REFERENCES pis.p19_report_withdrawal_request(id),
    report_id UUID NOT NULL REFERENCES pis.p19_report(id),
    original_version_id UUID NOT NULL REFERENCES pis.p19_report_content_version(id),
    approved_by_ref VARCHAR(128) NOT NULL,
    executed_by_ref VARCHAR(128) NOT NULL,
    reason_text VARCHAR(2000) NOT NULL,
    executed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p19_report_resign_relation (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES pis.p19_report(id),
    prior_signing_fact_id UUID NOT NULL REFERENCES pis.p19_signing_fact(id),
    new_signing_fact_id UUID NOT NULL REFERENCES pis.p19_signing_fact(id),
    formed_by_ref VARCHAR(128) NOT NULL,
    formed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p19_report_result_reference (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES pis.p19_report(id),
    report_content_version_id UUID NOT NULL REFERENCES pis.p19_report_content_version(id),
    technical_project_id UUID,
    result_identity VARCHAR(128) NOT NULL,
    result_version_reference VARCHAR(128) NOT NULL,
    result_digest VARCHAR(128) NOT NULL,
    adoption_state_code VARCHAR(32) NOT NULL,
    recorded_by_ref VARCHAR(128) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    UNIQUE (report_content_version_id, result_identity)
);

CREATE TABLE IF NOT EXISTS pis.p19_state_history (
    id UUID PRIMARY KEY,
    target_object_id UUID NOT NULL,
    target_object_kind_code VARCHAR(48) NOT NULL,
    source_state_code VARCHAR(48),
    target_state_code VARCHAR(48) NOT NULL,
    transition_event_code VARCHAR(64) NOT NULL,
    expected_version BIGINT,
    resulting_version BIGINT NOT NULL,
    reason_text VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p19_command_idempotency (
    id UUID PRIMARY KEY,
    operation_code VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL,
    result_object_id UUID NOT NULL,
    actor_ref VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (operation_code, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_p19_task_queue ON pis.p19_diagnosis_task (organization_reference, task_state_code, created_at);
CREATE INDEX IF NOT EXISTS idx_p19_report_queue ON pis.p19_report (organization_reference, report_state_code, created_at);
CREATE INDEX IF NOT EXISTS idx_p19_state_history ON pis.p19_state_history (target_object_id, occurred_at);
