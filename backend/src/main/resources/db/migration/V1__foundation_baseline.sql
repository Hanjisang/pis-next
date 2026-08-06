CREATE SCHEMA IF NOT EXISTS pis;

CREATE TABLE IF NOT EXISTS pis.foundation_schema_metadata (
    metadata_id UUID PRIMARY KEY,
    schema_code VARCHAR(64) NOT NULL,
    foundation_version VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_foundation_schema_metadata_code UNIQUE (schema_code)
);

INSERT INTO pis.foundation_schema_metadata (metadata_id, schema_code, foundation_version)
VALUES ('00000000-0000-0000-0000-000000000013', 'PIS_NEXT', 'P13')
ON CONFLICT (schema_code) DO NOTHING;
