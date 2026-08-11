-- V2 business facts for specimen description, receiving, and split lineage.
-- These facts extend Specimen without introducing a workflow state machine.

ALTER TABLE pis_v2.specimen
    ADD COLUMN IF NOT EXISTS laterality_code VARCHAR(32),
    ADD COLUMN IF NOT EXISTS quantity_value NUMERIC(12, 3),
    ADD COLUMN IF NOT EXISTS quantity_unit_code VARCHAR(32),
    ADD COLUMN IF NOT EXISTS description VARCHAR(4000),
    ADD COLUMN IF NOT EXISTS removed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS fixed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS received_at TIMESTAMPTZ;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_v2_specimen_quantity'
          AND conrelid = 'pis_v2.specimen'::regclass
    ) THEN
        ALTER TABLE pis_v2.specimen
            ADD CONSTRAINT ck_v2_specimen_quantity
            CHECK (quantity_value IS NULL OR quantity_value > 0);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS pis_v2.specimen_receiving_fact (
    id UUID PRIMARY KEY,
    specimen_id UUID NOT NULL REFERENCES pis_v2.specimen(id),
    verification_code VARCHAR(64) NOT NULL,
    actual_description VARCHAR(4000),
    reason VARCHAR(2000),
    received_at TIMESTAMPTZ NOT NULL,
    received_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis_v2.specimen_split (
    id UUID PRIMARY KEY,
    source_specimen_id UUID NOT NULL REFERENCES pis_v2.specimen(id),
    child_specimen_id UUID NOT NULL REFERENCES pis_v2.specimen(id),
    quantity_value NUMERIC(12, 3),
    reason VARCHAR(2000) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT uq_v2_specimen_split_child UNIQUE (child_specimen_id),
    CONSTRAINT ck_v2_specimen_split_quantity CHECK (quantity_value IS NULL OR quantity_value > 0)
);

CREATE INDEX IF NOT EXISTS idx_v2_specimen_receipt_case
    ON pis_v2.specimen_receiving_fact (organization_reference, specimen_id, received_at DESC);
CREATE INDEX IF NOT EXISTS idx_v2_specimen_split_source
    ON pis_v2.specimen_split (organization_reference, source_specimen_id, created_at);
