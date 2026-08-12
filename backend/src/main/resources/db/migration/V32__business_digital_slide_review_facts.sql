-- Persistent review facts are attached to a DigitalSlide and never alter the
-- source slide or the viewer engine state.
CREATE TABLE IF NOT EXISTS pis_v2.digital_slide_annotation (
    id UUID PRIMARY KEY,
    digital_slide_id UUID NOT NULL REFERENCES pis_v2.digital_slide(id),
    annotation_type_code VARCHAR(32) NOT NULL,
    geometry_json VARCHAR(20000) NOT NULL,
    label VARCHAR(256),
    note VARCHAR(4000),
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_ref VARCHAR(128) NOT NULL,
    deleted_at TIMESTAMPTZ,
    organization_reference VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis_v2.digital_slide_measurement (
    id UUID PRIMARY KEY,
    digital_slide_id UUID NOT NULL REFERENCES pis_v2.digital_slide(id),
    geometry_json VARCHAR(20000) NOT NULL,
    measurement_value NUMERIC(18,6) NOT NULL,
    unit_code VARCHAR(32) NOT NULL,
    measurement_mode_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS pis_v2.digital_slide_screenshot (
    id UUID PRIMARY KEY,
    digital_slide_id UUID NOT NULL REFERENCES pis_v2.digital_slide(id),
    viewport_json VARCHAR(20000) NOT NULL,
    storage_reference VARCHAR(1024) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    organization_reference VARCHAR(128) NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_v2_digital_annotation_slide
    ON pis_v2.digital_slide_annotation (digital_slide_id, created_at);
CREATE INDEX IF NOT EXISTS ix_v2_digital_measurement_slide
    ON pis_v2.digital_slide_measurement (digital_slide_id, created_at);
