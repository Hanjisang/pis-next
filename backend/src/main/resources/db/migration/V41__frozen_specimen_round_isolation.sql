-- SRS-FC03C: one unified Specimen belongs to at most one FrozenRound.
-- The service validates Case ownership; this index closes the remaining
-- concurrent cross-round assignment race at the database boundary.
CREATE UNIQUE INDEX IF NOT EXISTS uq_v2_frozen_round_specimen_global
    ON pis_v2.frozen_round_specimen (specimen_id);

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b241', 'PIS_V2', 'FC03C-FROZEN-SPECIMEN-ROUND-ISOLATION', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE
SET version_code = 'FC03C-FROZEN-SPECIMEN-ROUND-ISOLATION', recorded_at = CURRENT_TIMESTAMP;
