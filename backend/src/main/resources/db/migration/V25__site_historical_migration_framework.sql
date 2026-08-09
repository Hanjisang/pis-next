-- S05: historical migration quarantine, evidence, validation, and exception list.
-- No statement writes migrated data into V2 Core Domain tables.

CREATE TABLE pis_v2.migration_run (
    id UUID PRIMARY KEY,
    source_adapter_code VARCHAR(128) NOT NULL,
    source_dataset_version VARCHAR(128) NOT NULL,
    source_schema_hash VARCHAR(128) NOT NULL,
    mapping_rule_version VARCHAR(128) NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    started_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_migration_run_status CHECK (status_code IN
        ('RUNNING', 'VALIDATED', 'BLOCKED', 'FAILED'))
);

CREATE TABLE pis_v2.migration_source_manifest (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL UNIQUE REFERENCES pis_v2.migration_run(id),
    source_reference VARCHAR(1024) NOT NULL,
    source_dataset_version VARCHAR(128) NOT NULL,
    source_schema_hash VARCHAR(128) NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    record_count BIGINT NOT NULL,
    CONSTRAINT ck_v2_migration_manifest_count CHECK (record_count >= 0)
);

CREATE TABLE pis_v2.migration_staging_record (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES pis_v2.migration_run(id),
    source_object_type VARCHAR(64) NOT NULL,
    source_object_id VARCHAR(256) NOT NULL,
    source_parent_type VARCHAR(64),
    source_parent_id VARCHAR(256),
    target_object_type VARCHAR(64) NOT NULL,
    target_object_id UUID NOT NULL,
    mapping_decision_code VARCHAR(32) NOT NULL,
    business_reference VARCHAR(256),
    payload_reference VARCHAR(1024),
    payload_digest VARCHAR(128) NOT NULL,
    evidence_snapshot VARCHAR(4000) NOT NULL,
    staged_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_v2_migration_staging_source UNIQUE
        (run_id, source_object_type, source_object_id),
    CONSTRAINT ck_v2_migration_mapping_decision CHECK (mapping_decision_code IN
        ('MAP', 'MERGE', 'KEEP_REFERENCE', 'MANUAL_REVIEW'))
);

CREATE TABLE pis_v2.migration_exception (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES pis_v2.migration_run(id),
    exception_code VARCHAR(128) NOT NULL,
    severity_code VARCHAR(8) NOT NULL,
    source_object_type VARCHAR(64) NOT NULL,
    source_object_id VARCHAR(256) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    manual_action VARCHAR(2000) NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    evidence_reference VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    resolved_by_ref VARCHAR(128),
    resolution VARCHAR(2000),
    CONSTRAINT ck_v2_migration_exception_severity CHECK (severity_code IN ('P0', 'P1', 'P2')),
    CONSTRAINT ck_v2_migration_exception_status CHECK (status_code IN
        ('OPEN', 'IN_REVIEW', 'RESOLVED', 'WAIVED', 'BLOCKED'))
);

CREATE TABLE pis_v2.migration_checkpoint (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES pis_v2.migration_run(id),
    checkpoint_code VARCHAR(128) NOT NULL,
    last_source_object_type VARCHAR(64),
    last_source_object_id VARCHAR(256),
    staged_count BIGINT NOT NULL,
    exception_count BIGINT NOT NULL,
    saved_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_v2_migration_checkpoint UNIQUE (run_id, checkpoint_code),
    CONSTRAINT ck_v2_migration_checkpoint_counts CHECK (staged_count >= 0 AND exception_count >= 0)
);

CREATE TABLE pis_v2.migration_validation_report (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL UNIQUE REFERENCES pis_v2.migration_run(id),
    case_source_count BIGINT NOT NULL,
    case_staged_count BIGINT NOT NULL,
    specimen_source_count BIGINT NOT NULL,
    specimen_staged_count BIGINT NOT NULL,
    block_source_count BIGINT NOT NULL,
    block_staged_count BIGINT NOT NULL,
    slide_source_count BIGINT NOT NULL,
    slide_staged_count BIGINT NOT NULL,
    diagnosis_source_count BIGINT NOT NULL,
    diagnosis_staged_count BIGINT NOT NULL,
    report_source_count BIGINT NOT NULL,
    report_staged_count BIGINT NOT NULL,
    case_specimen_difference BIGINT NOT NULL,
    specimen_block_difference BIGINT NOT NULL,
    block_slide_difference BIGINT NOT NULL,
    case_report_difference BIGINT NOT NULL,
    exception_count BIGINT NOT NULL,
    validation_status_code VARCHAR(32) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_v2_migration_validation_status CHECK (validation_status_code IN
        ('VALIDATED', 'BLOCKED', 'FAILED'))
);

CREATE INDEX ix_v2_migration_exception_open
    ON pis_v2.migration_exception (run_id, severity_code, status_code)
    WHERE status_code IN ('OPEN', 'IN_REVIEW', 'BLOCKED');
CREATE INDEX ix_v2_migration_staging_target
    ON pis_v2.migration_staging_record (run_id, target_object_type, target_object_id);

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b201', 'PIS_V2', 'S05-MIGRATION-FRAMEWORK', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE
SET version_code = 'S05-MIGRATION-FRAMEWORK', recorded_at = CURRENT_TIMESTAMP;
