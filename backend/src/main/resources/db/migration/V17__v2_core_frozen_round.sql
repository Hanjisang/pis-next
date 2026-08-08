-- V2 core closure Gate A: FrozenRound uses Case, Specimen, material, Diagnosis and Report.

ALTER TABLE pis_v2.pathology_case
    ADD COLUMN IF NOT EXISTS frozen_source_case_id UUID REFERENCES pis_v2.pathology_case(id);

CREATE INDEX IF NOT EXISTS idx_v2_case_frozen_source
    ON pis_v2.pathology_case (frozen_source_case_id);

CREATE TABLE IF NOT EXISTS pis_v2.frozen_round (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    round_no INTEGER NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    arrival_time TIMESTAMPTZ NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL,
    grossing_start_time TIMESTAMPTZ,
    slide_completed_time TIMESTAMPTZ,
    diagnosis_signed_time TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    ended_by_ref VARCHAR(128),
    concurrency_version BIGINT NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_frozen_round_no UNIQUE (case_id, round_no),
    CONSTRAINT ck_v2_frozen_round_status CHECK (status_code IN ('OPEN', 'PRODUCTION_COMPLETE', 'SIGNED', 'ENDED')),
    CONSTRAINT ck_v2_frozen_round_no CHECK (round_no > 0),
    CONSTRAINT ck_v2_frozen_round_version CHECK (concurrency_version >= 0),
    CONSTRAINT ck_v2_frozen_round_end_fields CHECK (
        (status_code <> 'ENDED' AND ended_at IS NULL AND ended_by_ref IS NULL)
        OR (status_code = 'ENDED' AND ended_at IS NOT NULL AND ended_by_ref IS NOT NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_frozen_round_open
    ON pis_v2.frozen_round (case_id) WHERE status_code IN ('OPEN', 'PRODUCTION_COMPLETE');

CREATE TABLE IF NOT EXISTS pis_v2.frozen_round_specimen (
    frozen_round_id UUID NOT NULL REFERENCES pis_v2.frozen_round(id),
    specimen_id UUID NOT NULL REFERENCES pis_v2.specimen(id),
    sequence_no INTEGER NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL,
    linked_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT pk_v2_frozen_round_specimen PRIMARY KEY (frozen_round_id, specimen_id),
    CONSTRAINT uq_v2_frozen_round_specimen_sequence UNIQUE (frozen_round_id, sequence_no),
    CONSTRAINT ck_v2_frozen_round_specimen_sequence CHECK (sequence_no > 0)
);

CREATE INDEX IF NOT EXISTS idx_v2_frozen_round_specimen_specimen
    ON pis_v2.frozen_round_specimen (specimen_id);

CREATE TABLE IF NOT EXISTS pis_v2.frozen_end (
    id UUID PRIMARY KEY,
    frozen_case_id UUID NOT NULL UNIQUE REFERENCES pis_v2.pathology_case(id),
    routine_case_id UUID NOT NULL UNIQUE REFERENCES pis_v2.pathology_case(id),
    idempotency_key VARCHAR(256) NOT NULL UNIQUE,
    ended_at TIMESTAMPTZ NOT NULL,
    ended_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_frozen_end_distinct_cases CHECK (frozen_case_id <> routine_case_id)
);

INSERT INTO pis_v2.slide_rule
    (id, organization_reference, business_type_id, rule_code, source_context_type, trigger_code,
     slide_type, stain_code, copies, active, configuration_version, created_at, updated_at, created_by_ref)
SELECT md5('PIS-V2-I06-FROZEN-SLIDE-RULE:' || bt.business_type_code)::uuid,
       'LOCAL_HOSPITAL', bt.id, 'FROZEN-HE', 'FROZEN_ROUND', 'ON_GROSSING_COMPLETE',
       'FROZEN-HE', 'HE', 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'V2-I06-SEED'
FROM pis_v2.business_type bt
WHERE bt.business_type_code = 'FROZEN'
ON CONFLICT (organization_reference, business_type_id, rule_code) DO NOTHING;

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b201', 'PIS_V2', 'V2-I06-A', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE SET version_code = 'V2-I06-A', recorded_at = CURRENT_TIMESTAMP;
