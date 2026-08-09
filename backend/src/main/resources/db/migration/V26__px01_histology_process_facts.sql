-- PX01 records lightweight histology facts without introducing a process state machine.
CREATE TABLE IF NOT EXISTS pis_v2.material_process_fact (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    slide_id UUID NOT NULL REFERENCES pis_v2.slide(id),
    phase_code VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    operator_ref VARCHAR(128),
    device_reference VARCHAR(256),
    batch_reference VARCHAR(256),
    exception_code VARCHAR(64),
    exception_note VARCHAR(2000),
    concurrency_version BIGINT NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_v2_material_process_fact UNIQUE (slide_id, phase_code),
    CONSTRAINT ck_v2_material_process_phase CHECK (phase_code IN
        ('DEHYDRATION', 'EMBEDDING', 'SECTIONING', 'STAINING', 'MOUNTING')),
    CONSTRAINT ck_v2_material_process_time CHECK (completed_at IS NULL OR started_at IS NOT NULL),
    CONSTRAINT ck_v2_material_process_operator CHECK (
        (started_at IS NULL AND operator_ref IS NULL)
        OR (started_at IS NOT NULL AND operator_ref IS NOT NULL)
    ),
    CONSTRAINT ck_v2_material_process_version CHECK (concurrency_version >= 0)
);

CREATE INDEX IF NOT EXISTS ix_v2_material_process_case
    ON pis_v2.material_process_fact (case_id, phase_code, started_at, completed_at);
CREATE INDEX IF NOT EXISTS ix_v2_material_process_slide
    ON pis_v2.material_process_fact (slide_id, phase_code);

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b260', 'PIS_V2', 'PX01-HISTOLOGY-FACTS', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE SET version_code = 'PX01-HISTOLOGY-FACTS', recorded_at = CURRENT_TIMESTAMP;
