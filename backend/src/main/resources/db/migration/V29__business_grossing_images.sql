-- Phase A: grossing images, annotations, and measurements are append-only
-- business facts. They do not replace the Grossing record.

CREATE TABLE IF NOT EXISTS pis_v2.grossing_image (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES pis_v2.pathology_case(id),
    grossing_id UUID NOT NULL REFERENCES pis_v2.grossing(id),
    specimen_id UUID REFERENCES pis_v2.specimen(id),
    image_name VARCHAR(256) NOT NULL,
    media_type VARCHAR(128) NOT NULL,
    storage_reference VARCHAR(1024) NOT NULL,
    metadata_json TEXT,
    captured_at TIMESTAMPTZ NOT NULL,
    captured_by_ref VARCHAR(128) NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by_ref VARCHAR(128),
    deletion_reason VARCHAR(2000),
    organization_reference VARCHAR(128) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_v2_grossing_image_context
    ON pis_v2.grossing_image (grossing_id, specimen_id, captured_at, deleted_at);

CREATE TABLE IF NOT EXISTS pis_v2.grossing_image_annotation (
    id UUID PRIMARY KEY,
    image_id UUID NOT NULL REFERENCES pis_v2.grossing_image(id),
    annotation_type_code VARCHAR(32) NOT NULL,
    geometry_json TEXT NOT NULL,
    label VARCHAR(256),
    note VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    deleted_at TIMESTAMPTZ,
    deleted_by_ref VARCHAR(128),
    CONSTRAINT ck_v2_grossing_annotation_type CHECK (annotation_type_code IN ('POINT', 'RECTANGLE', 'POLYGON', 'FREEHAND'))
);

CREATE INDEX IF NOT EXISTS idx_v2_grossing_annotation_image
    ON pis_v2.grossing_image_annotation (image_id, created_at, deleted_at);

CREATE TABLE IF NOT EXISTS pis_v2.grossing_image_measurement (
    id UUID PRIMARY KEY,
    image_id UUID NOT NULL REFERENCES pis_v2.grossing_image(id),
    geometry_json TEXT NOT NULL,
    "value" NUMERIC(18, 6) NOT NULL,
    unit_code VARCHAR(32) NOT NULL,
    measurement_mode_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_grossing_measurement_mode CHECK (measurement_mode_code IN ('CALIBRATED', 'IMAGE_COORDINATE'))
);

CREATE INDEX IF NOT EXISTS idx_v2_grossing_measurement_image
    ON pis_v2.grossing_image_measurement (image_id, created_at);
