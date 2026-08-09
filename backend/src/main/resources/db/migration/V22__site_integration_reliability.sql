-- S02: external integration reliability facts. Core business facts are deliberately
-- not referenced by foreign keys so an external failure cannot roll them back.

CREATE TABLE pis_v2.integration_message_log (
    id UUID PRIMARY KEY,
    hospital_profile_code VARCHAR(128) NOT NULL,
    direction_code VARCHAR(16) NOT NULL,
    source_system_code VARCHAR(128) NOT NULL,
    target_system_code VARCHAR(128) NOT NULL,
    message_id VARCHAR(256) NOT NULL,
    capability_code VARCHAR(64) NOT NULL,
    business_key VARCHAR(256) NOT NULL,
    request_reference VARCHAR(1024) NOT NULL,
    request_digest VARCHAR(128) NOT NULL,
    response_summary VARCHAR(2000),
    status_code VARCHAR(32) NOT NULL,
    error_code VARCHAR(128),
    error_message VARCHAR(2000),
    retry_count INTEGER NOT NULL,
    max_retries INTEGER NOT NULL,
    next_retry_at TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_v2_integration_message UNIQUE
        (hospital_profile_code, source_system_code, target_system_code, message_id),
    CONSTRAINT ck_v2_integration_direction CHECK (direction_code IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT ck_v2_integration_status CHECK (status_code IN
        ('PENDING', 'SUCCEEDED', 'RETRY_PENDING', 'DEAD_LETTER')),
    CONSTRAINT ck_v2_integration_retry CHECK
        (retry_count >= 0 AND max_retries BETWEEN 0 AND 100)
);

CREATE TABLE pis_v2.integration_attempt (
    id UUID PRIMARY KEY,
    message_log_id UUID NOT NULL REFERENCES pis_v2.integration_message_log(id),
    attempt_no INTEGER NOT NULL,
    adapter_code VARCHAR(128) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    result_code VARCHAR(32) NOT NULL,
    response_summary VARCHAR(2000),
    error_code VARCHAR(128),
    error_message VARCHAR(2000),
    retryable BOOLEAN NOT NULL,
    CONSTRAINT uq_v2_integration_attempt UNIQUE (message_log_id, attempt_no),
    CONSTRAINT ck_v2_integration_attempt_result CHECK (result_code IN ('SUCCEEDED', 'FAILED'))
);

CREATE TABLE pis_v2.integration_dead_letter (
    id UUID PRIMARY KEY,
    message_log_id UUID NOT NULL UNIQUE REFERENCES pis_v2.integration_message_log(id),
    reason VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    resolved_by_ref VARCHAR(128),
    resolution VARCHAR(2000)
);

CREATE TABLE pis_v2.integration_replay_request (
    id UUID PRIMARY KEY,
    message_log_id UUID NOT NULL REFERENCES pis_v2.integration_message_log(id),
    requested_by_ref VARCHAR(128) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    replay_status_code VARCHAR(32) NOT NULL,
    new_attempt_no INTEGER,
    CONSTRAINT ck_v2_integration_replay_status CHECK (replay_status_code IN
        ('REQUESTED', 'APPROVED', 'COMPLETED', 'REJECTED'))
);

CREATE TABLE pis_v2.integration_reconciliation (
    id UUID PRIMARY KEY,
    hospital_profile_code VARCHAR(128) NOT NULL,
    target_system_code VARCHAR(128) NOT NULL,
    reconciliation_date DATE NOT NULL,
    local_message_count BIGINT NOT NULL,
    external_confirmed_count BIGINT NOT NULL,
    difference_count BIGINT NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    evidence_reference VARCHAR(1024) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_integration_reconciliation UNIQUE
        (hospital_profile_code, target_system_code, reconciliation_date),
    CONSTRAINT ck_v2_integration_reconciliation_status CHECK (status_code IN
        ('MATCHED', 'DIFFERENCE', 'BLOCKED')),
    CONSTRAINT ck_v2_integration_reconciliation_counts CHECK
        (local_message_count >= 0 AND external_confirmed_count >= 0 AND difference_count >= 0)
);

CREATE TABLE pis_v2.external_identifier_mapping (
    id UUID PRIMARY KEY,
    hospital_profile_code VARCHAR(128) NOT NULL,
    external_system_code VARCHAR(128) NOT NULL,
    external_object_type VARCHAR(64) NOT NULL,
    external_identifier VARCHAR(256) NOT NULL,
    local_object_type VARCHAR(64) NOT NULL,
    local_identifier UUID NOT NULL,
    mapping_version INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_external_identifier UNIQUE
        (hospital_profile_code, external_system_code, external_object_type, external_identifier),
    CONSTRAINT ck_v2_external_identifier_version CHECK (mapping_version > 0)
);

CREATE INDEX ix_v2_integration_retry_due
    ON pis_v2.integration_message_log (status_code, next_retry_at)
    WHERE status_code = 'RETRY_PENDING';
CREATE INDEX ix_v2_integration_business_key
    ON pis_v2.integration_message_log (hospital_profile_code, business_key, created_at);
CREATE INDEX ix_v2_integration_attempt_log
    ON pis_v2.integration_attempt (message_log_id, attempt_no);

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b201', 'PIS_V2', 'S02-INTEGRATION-ARCHITECTURE', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE
SET version_code = 'S02-INTEGRATION-ARCHITECTURE', recorded_at = CURRENT_TIMESTAMP;
