-- Rework is an explicit material fact. It does not replace the original
-- slide/block and it does not introduce a physical-process workflow.
CREATE TABLE IF NOT EXISTS pis_v2.material_rework (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    original_slide_id UUID NOT NULL REFERENCES pis_v2.slide(id),
    rework_type_code VARCHAR(64) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    status_code VARCHAR(32) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    requested_by_ref VARCHAR(128) NOT NULL,
    replacement_slide_id UUID REFERENCES pis_v2.slide(id),
    completed_at TIMESTAMPTZ,
    completed_by_ref VARCHAR(128),
    organization_reference VARCHAR(128) NOT NULL,
    concurrency_version BIGINT NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    CONSTRAINT uq_v2_material_rework_idempotency UNIQUE (organization_reference, idempotency_key),
    CONSTRAINT ck_v2_material_rework_status CHECK (status_code IN ('REQUESTED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_v2_material_rework_completion CHECK (
        (status_code = 'COMPLETED' AND replacement_slide_id IS NOT NULL AND completed_at IS NOT NULL
            AND completed_by_ref IS NOT NULL)
        OR (status_code <> 'COMPLETED' AND replacement_slide_id IS NULL AND completed_at IS NULL
            AND completed_by_ref IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS ix_v2_material_rework_case
    ON pis_v2.material_rework (organization_reference, case_id, requested_at);
CREATE INDEX IF NOT EXISTS ix_v2_material_rework_slide
    ON pis_v2.material_rework (organization_reference, original_slide_id, status_code);
