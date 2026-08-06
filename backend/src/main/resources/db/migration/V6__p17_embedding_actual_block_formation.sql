CREATE TABLE IF NOT EXISTS pis.p17_embedding_task (
    id UUID PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL,
    tissue_block_id UUID NOT NULL REFERENCES pis.tissue_block(id),
    processing_result_id UUID NOT NULL REFERENCES pis.p17_processing_result(id),
    task_attempt_no INTEGER NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    task_state_code VARCHAR(64) NOT NULL,
    assigned_actor_ref VARCHAR(128),
    actual_actor_ref VARCHAR(128),
    embedding_requirement_snapshot VARCHAR(2000),
    orientation_reference VARCHAR(256),
    assigned_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    record_version_no INTEGER NOT NULL,
    concurrency_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (organization_reference, task_no),
    UNIQUE (tissue_block_id, processing_result_id, task_attempt_no)
);

CREATE TABLE IF NOT EXISTS pis.p17_embedding_task_assignment (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES pis.p17_embedding_task(id),
    from_actor_ref VARCHAR(128),
    to_actor_ref VARCHAR(128) NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    reason VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p17_embedding_fact (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL UNIQUE REFERENCES pis.p17_embedding_task(id),
    tissue_block_id UUID NOT NULL REFERENCES pis.tissue_block(id),
    processing_result_id UUID NOT NULL REFERENCES pis.p17_processing_result(id),
    embedding_state_code VARCHAR(64) NOT NULL,
    requirement_snapshot VARCHAR(2000) NOT NULL,
    orientation_reference VARCHAR(256),
    actual_actor_ref VARCHAR(128) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    record_version_no INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.p17_actual_block_formation (
    id UUID PRIMARY KEY,
    tissue_block_id UUID NOT NULL REFERENCES pis.tissue_block(id),
    embedding_fact_id UUID NOT NULL REFERENCES pis.p17_embedding_fact(id),
    processing_result_id UUID NOT NULL REFERENCES pis.p17_processing_result(id),
    formation_version_no INTEGER NOT NULL,
    inherited_block_no VARCHAR(64) NOT NULL,
    current_valid BOOLEAN NOT NULL,
    formation_state_code VARCHAR(64) NOT NULL,
    formed_at TIMESTAMPTZ NOT NULL,
    formed_by_ref VARCHAR(128) NOT NULL,
    supersedes_formation_id UUID REFERENCES pis.p17_actual_block_formation(id),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (tissue_block_id, formation_version_no),
    UNIQUE (embedding_fact_id)
);

CREATE TABLE IF NOT EXISTS pis.p17_actual_block_replacement (
    id UUID PRIMARY KEY,
    original_formation_id UUID NOT NULL REFERENCES pis.p17_actual_block_formation(id),
    replacement_formation_id UUID NOT NULL REFERENCES pis.p17_actual_block_formation(id),
    reason VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (original_formation_id, replacement_formation_id)
);

ALTER TABLE pis.p17_embedding_task
    ADD COLUMN IF NOT EXISTS rework_of_formation_id UUID REFERENCES pis.p17_actual_block_formation(id);

CREATE INDEX IF NOT EXISTS idx_p17_embedding_queue ON pis.p17_embedding_task(organization_reference, task_state_code, created_at);
CREATE INDEX IF NOT EXISTS idx_p17_actual_block_current ON pis.p17_actual_block_formation(tissue_block_id, current_valid);

INSERT INTO pis.foundation_schema_metadata (metadata_id, schema_code, foundation_version)
VALUES ('00000000-0000-0000-0000-000000000020', 'PIS_NEXT', 'P17')
ON CONFLICT (schema_code) DO UPDATE SET foundation_version = 'P17';
