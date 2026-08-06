CREATE SCHEMA IF NOT EXISTS pis;

CREATE TABLE IF NOT EXISTS pis.specimen (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL,
    specimen_no VARCHAR(64) NOT NULL UNIQUE,
    specimen_kind_code VARCHAR(32) NOT NULL,
    specimen_source_code VARCHAR(32) NOT NULL,
    collection_site_text VARCHAR(500) NOT NULL,
    collection_method_code VARCHAR(32) NOT NULL,
    specimen_lifecycle_state_code VARCHAR(32) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE,
    received_by_ref VARCHAR(128),
    specimen_difference_code VARCHAR(64),
    record_version_no INTEGER NOT NULL,
    concurrency_version BIGINT NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.specimen_container (
    id UUID PRIMARY KEY,
    specimen_id UUID NOT NULL,
    container_barcode VARCHAR(128) NOT NULL UNIQUE,
    expected_quantity INTEGER NOT NULL,
    actual_quantity INTEGER,
    container_state_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.clinical_state_current (
    object_id UUID NOT NULL,
    object_kind_code VARCHAR(32) NOT NULL,
    state_machine_code VARCHAR(32) NOT NULL,
    state_code VARCHAR(32) NOT NULL,
    concurrency_version BIGINT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    PRIMARY KEY (object_id, state_machine_code)
);

CREATE TABLE IF NOT EXISTS pis.state_transition_history (
    id UUID PRIMARY KEY,
    object_id UUID NOT NULL,
    object_kind_code VARCHAR(32) NOT NULL,
    state_machine_code VARCHAR(32) NOT NULL,
    source_state_code VARCHAR(32) NOT NULL,
    target_state_code VARCHAR(32) NOT NULL,
    transition_event_code VARCHAR(64) NOT NULL,
    expected_version BIGINT,
    resulting_version BIGINT,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_by_ref VARCHAR(128) NOT NULL,
    reason VARCHAR(500) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.operation_responsibility (
    id UUID PRIMARY KEY,
    object_id UUID NOT NULL,
    object_kind_code VARCHAR(32) NOT NULL,
    responsibility_type_code VARCHAR(32) NOT NULL,
    responsible_actor_ref VARCHAR(128) NOT NULL,
    actual_actor_ref VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis.handoff_record (
    id UUID PRIMARY KEY,
    object_id UUID NOT NULL,
    object_kind_code VARCHAR(32) NOT NULL,
    from_actor_ref VARCHAR(128) NOT NULL,
    to_actor_ref VARCHAR(128) NOT NULL,
    handoff_state_code VARCHAR(32) NOT NULL,
    handoff_digest VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_by_ref VARCHAR(128) NOT NULL,
    UNIQUE (object_id, handoff_digest)
);

CREATE TABLE IF NOT EXISTS pis.business_exception (
    id UUID PRIMARY KEY,
    object_id UUID NOT NULL,
    object_kind_code VARCHAR(32) NOT NULL,
    error_code VARCHAR(32) NOT NULL,
    exception_state_code VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL
);
