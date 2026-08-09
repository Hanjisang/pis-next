-- S03: normalize the existing logical printer profile to the adapter://logical-printer format.
-- No vendor SDK, address, credential, or hospital-specific driver is installed by this migration.

UPDATE pis_v2.print_rule
   SET printer_profile_code = 'MOCK://SYNTH-PRINTER',
       configuration_version = configuration_version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE printer_profile_code = 'SYNTH-PRINTER';

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b201', 'PIS_V2', 'S03-DEVICE-ADAPTER', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE
SET version_code = 'S03-DEVICE-ADAPTER', recorded_at = CURRENT_TIMESTAMP;
