-- V2-I02 creates the unified Grossing -> Block -> Slide material chain.

CREATE TABLE IF NOT EXISTS pis_v2.grossing_sequence (
    organization_reference VARCHAR(128) NOT NULL,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    next_serial BIGINT NOT NULL,
    CONSTRAINT pk_v2_grossing_sequence PRIMARY KEY (organization_reference, case_id),
    CONSTRAINT ck_v2_grossing_sequence_serial CHECK (next_serial > 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.grossing (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    grossing_no VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_reference_id UUID,
    gross_description TEXT NOT NULL,
    grossing_instruction TEXT,
    grossing_doctor_id VARCHAR(128) NOT NULL,
    recorder_id VARCHAR(128) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    completed_by_ref VARCHAR(128),
    deleted_at TIMESTAMPTZ,
    deleted_by_ref VARCHAR(128),
    deletion_reason VARCHAR(2000),
    concurrency_version BIGINT NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_grossing_no UNIQUE (organization_reference, case_id, grossing_no),
    CONSTRAINT ck_v2_grossing_source CHECK (source_type IN ('INITIAL', 'TECHNICAL_ORDER', 'FROZEN_CONTEXT', 'OTHER')),
    CONSTRAINT ck_v2_grossing_version CHECK (concurrency_version >= 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.grossing_specimen (
    grossing_id UUID NOT NULL REFERENCES pis_v2.grossing(id),
    specimen_id UUID NOT NULL REFERENCES pis_v2.specimen(id),
    sequence_no INTEGER NOT NULL,
    material_description TEXT,
    concurrency_version BIGINT NOT NULL,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT pk_v2_grossing_specimen PRIMARY KEY (grossing_id, specimen_id),
    CONSTRAINT uq_v2_grossing_specimen_sequence UNIQUE (grossing_id, sequence_no),
    CONSTRAINT ck_v2_grossing_specimen_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_v2_grossing_specimen_version CHECK (concurrency_version >= 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.block (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    grossing_id UUID NOT NULL REFERENCES pis_v2.grossing(id),
    specimen_id UUID NOT NULL REFERENCES pis_v2.specimen(id),
    block_code VARCHAR(64) NOT NULL,
    block_type VARCHAR(64) NOT NULL,
    external_source_flag BOOLEAN NOT NULL,
    external_source_reference VARCHAR(256),
    deleted_at TIMESTAMPTZ,
    deleted_by_ref VARCHAR(128),
    deletion_reason VARCHAR(2000),
    concurrency_version BIGINT NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_block_external_source CHECK (
        external_source_flag = FALSE OR external_source_reference IS NOT NULL
    ),
    CONSTRAINT ck_v2_block_version CHECK (concurrency_version >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_block_code_active
    ON pis_v2.block (case_id, block_code) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS pis_v2.slide_rule (
    id UUID PRIMARY KEY,
    organization_reference VARCHAR(128) NOT NULL,
    business_type_id UUID NOT NULL REFERENCES pis_v2.business_type(id),
    rule_code VARCHAR(64) NOT NULL,
    source_context_type VARCHAR(32) NOT NULL,
    trigger_code VARCHAR(64) NOT NULL,
    slide_type VARCHAR(64) NOT NULL,
    stain_code VARCHAR(64) NOT NULL,
    copies INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_slide_rule UNIQUE (organization_reference, business_type_id, rule_code),
    CONSTRAINT ck_v2_slide_rule_source CHECK (source_context_type IN
        ('INITIAL', 'TECHNICAL_ORDER', 'FROZEN_ROUND', 'CYTOLOGY', 'EXTERNAL')),
    CONSTRAINT ck_v2_slide_rule_copies CHECK (copies > 0),
    CONSTRAINT ck_v2_slide_rule_version CHECK (configuration_version > 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.slide (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    block_id UUID REFERENCES pis_v2.block(id),
    specimen_id UUID REFERENCES pis_v2.specimen(id),
    slide_code VARCHAR(128) NOT NULL,
    slide_type VARCHAR(64) NOT NULL,
    source_context_type VARCHAR(32) NOT NULL,
    source_context_id UUID,
    rule_code VARCHAR(64) NOT NULL,
    occurrence_no INTEGER NOT NULL,
    required BOOLEAN NOT NULL,
    completed_at TIMESTAMPTZ,
    completed_by_ref VARCHAR(128),
    deleted_at TIMESTAMPTZ,
    deleted_by_ref VARCHAR(128),
    deletion_reason VARCHAR(2000),
    concurrency_version BIGINT NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_slide_source CHECK (block_id IS NOT NULL OR specimen_id IS NOT NULL),
    CONSTRAINT ck_v2_slide_context CHECK (source_context_type IN
        ('INITIAL', 'TECHNICAL_ORDER', 'FROZEN_ROUND', 'CYTOLOGY', 'EXTERNAL')),
    CONSTRAINT ck_v2_slide_occurrence CHECK (occurrence_no > 0),
    CONSTRAINT ck_v2_slide_version CHECK (concurrency_version >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_slide_code_active
    ON pis_v2.slide (case_id, slide_code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_slide_rule_output_active
    ON pis_v2.slide (block_id, source_context_type, rule_code, occurrence_no) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS pis_v2.print_rule (
    id UUID PRIMARY KEY,
    organization_reference VARCHAR(128) NOT NULL,
    business_type_id UUID REFERENCES pis_v2.business_type(id),
    entity_kind_code VARCHAR(32) NOT NULL,
    trigger_code VARCHAR(64) NOT NULL,
    printer_profile_code VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_print_rule_entity CHECK (entity_kind_code IN ('BLOCK', 'SLIDE')),
    CONSTRAINT ck_v2_print_rule_trigger CHECK (trigger_code IN
        ('ON_CREATE', 'ON_GROSSING_COMPLETE', 'MANUAL')),
    CONSTRAINT ck_v2_print_rule_version CHECK (configuration_version > 0)
);

CREATE TABLE IF NOT EXISTS pis_v2.print_log (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    entity_kind_code VARCHAR(32) NOT NULL,
    entity_id UUID NOT NULL,
    business_code VARCHAR(128) NOT NULL,
    printer_profile_code VARCHAR(128) NOT NULL,
    operator_ref VARCHAR(128) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    result_code VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(2000),
    CONSTRAINT ck_v2_print_log_entity CHECK (entity_kind_code IN ('BLOCK', 'SLIDE')),
    CONSTRAINT ck_v2_print_log_result CHECK (result_code IN ('SUCCESS', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS pis_v2.material_command_idempotency (
    id UUID PRIMARY KEY,
    operation_code VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL,
    result_kind_code VARCHAR(64) NOT NULL,
    result_entity_id UUID,
    result_count INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_material_command_idempotency UNIQUE (operation_code, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_v2_grossing_case ON pis_v2.grossing (case_id, created_at);
CREATE INDEX IF NOT EXISTS idx_v2_block_grossing ON pis_v2.block (grossing_id, created_at);
CREATE INDEX IF NOT EXISTS idx_v2_block_specimen ON pis_v2.block (specimen_id, created_at);
CREATE INDEX IF NOT EXISTS idx_v2_slide_block ON pis_v2.slide (block_id, created_at);
CREATE INDEX IF NOT EXISTS idx_v2_slide_case_completion ON pis_v2.slide
    (case_id, source_context_type, completed_at, deleted_at);

INSERT INTO pis_v2.slide_rule
    (id, organization_reference, business_type_id, rule_code, source_context_type, trigger_code,
     slide_type, stain_code, copies, active, configuration_version, created_at, updated_at, created_by_ref)
SELECT md5('PIS-V2-I02-SLIDE-RULE:' || bt.business_type_code)::uuid,
       'LOCAL_HOSPITAL', bt.id, 'INITIAL-HE', 'INITIAL', 'ON_GROSSING_COMPLETE',
       'HE', 'HE', 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'V2-I02-SEED'
FROM pis_v2.business_type bt
ON CONFLICT (organization_reference, business_type_id, rule_code) DO NOTHING;

INSERT INTO pis_v2.print_rule
    (id, organization_reference, business_type_id, entity_kind_code, trigger_code, printer_profile_code,
     active, configuration_version, created_at, updated_at, created_by_ref)
VALUES ('00000000-0000-0000-0000-00000000d201', 'LOCAL_HOSPITAL', NULL, 'SLIDE', 'ON_GROSSING_COMPLETE',
        'SYNTH-PRINTER', TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'V2-I02-SEED')
ON CONFLICT DO NOTHING;

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b201', 'PIS_V2', 'V2-I02', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE SET version_code = 'V2-I02', recorded_at = CURRENT_TIMESTAMP;
