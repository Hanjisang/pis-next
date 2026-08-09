-- S01: hospital-scoped configuration. These tables configure the V2 application
-- without changing any Core Domain aggregate or creating hospital-specific code.

CREATE TABLE pis_v2.hospital_profile (
    id UUID PRIMARY KEY,
    profile_code VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(256) NOT NULL,
    legal_name VARCHAR(512),
    timezone_id VARCHAR(128) NOT NULL,
    locale_code VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_v2_hospital_profile_version CHECK (configuration_version > 0)
);

CREATE TABLE pis_v2.hospital_campus (
    id UUID PRIMARY KEY,
    hospital_profile_id UUID NOT NULL REFERENCES pis_v2.hospital_profile(id),
    campus_code VARCHAR(128) NOT NULL,
    campus_name VARCHAR(256) NOT NULL,
    enabled BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_v2_hospital_campus UNIQUE (hospital_profile_id, campus_code),
    CONSTRAINT ck_v2_hospital_campus_version CHECK (configuration_version > 0)
);

CREATE TABLE pis_v2.hospital_department (
    id UUID PRIMARY KEY,
    hospital_profile_id UUID NOT NULL REFERENCES pis_v2.hospital_profile(id),
    campus_id UUID REFERENCES pis_v2.hospital_campus(id),
    department_code VARCHAR(128) NOT NULL,
    department_name VARCHAR(256) NOT NULL,
    department_type_code VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_v2_hospital_department UNIQUE (hospital_profile_id, department_code),
    CONSTRAINT ck_v2_hospital_department_version CHECK (configuration_version > 0)
);

CREATE TABLE pis_v2.hospital_business_type_configuration (
    id UUID PRIMARY KEY,
    hospital_profile_id UUID NOT NULL REFERENCES pis_v2.hospital_profile(id),
    canonical_business_type_code VARCHAR(64) NOT NULL,
    core_business_type_code VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_v2_hospital_business_type UNIQUE (hospital_profile_id, canonical_business_type_code),
    CONSTRAINT ck_v2_hospital_business_type_code CHECK (canonical_business_type_code IN
        ('ROUTINE', 'FROZEN', 'CYTOLOGY', 'MOLECULAR', 'CONSULTATION')),
    CONSTRAINT ck_v2_hospital_business_type_version CHECK (configuration_version > 0)
);

CREATE TABLE pis_v2.hospital_workflow_configuration (
    id UUID PRIMARY KEY,
    hospital_profile_id UUID NOT NULL REFERENCES pis_v2.hospital_profile(id),
    canonical_business_type_code VARCHAR(64) NOT NULL,
    require_review BOOLEAN NOT NULL,
    require_audit BOOLEAN NOT NULL,
    allow_direct_slide BOOLEAN NOT NULL,
    enabled BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_v2_hospital_workflow UNIQUE (hospital_profile_id, canonical_business_type_code),
    CONSTRAINT ck_v2_hospital_workflow_version CHECK (configuration_version > 0)
);

CREATE TABLE pis_v2.label_template (
    id UUID PRIMARY KEY,
    hospital_profile_id UUID NOT NULL REFERENCES pis_v2.hospital_profile(id),
    template_code VARCHAR(128) NOT NULL,
    template_name VARCHAR(256) NOT NULL,
    entity_kind_code VARCHAR(64) NOT NULL,
    renderer_code VARCHAR(64) NOT NULL,
    content_template TEXT NOT NULL,
    enabled BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_v2_label_template UNIQUE (hospital_profile_id, template_code),
    CONSTRAINT ck_v2_label_template_version CHECK (configuration_version > 0)
);

CREATE TABLE pis_v2.printer_mapping (
    id UUID PRIMARY KEY,
    hospital_profile_id UUID NOT NULL REFERENCES pis_v2.hospital_profile(id),
    campus_id UUID REFERENCES pis_v2.hospital_campus(id),
    department_id UUID REFERENCES pis_v2.hospital_department(id),
    logical_printer_code VARCHAR(128) NOT NULL,
    adapter_code VARCHAR(64) NOT NULL,
    endpoint_reference VARCHAR(512),
    enabled BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_v2_printer_mapping UNIQUE (hospital_profile_id, logical_printer_code),
    CONSTRAINT ck_v2_printer_mapping_version CHECK (configuration_version > 0)
);

CREATE TABLE pis_v2.print_strategy (
    id UUID PRIMARY KEY,
    hospital_profile_id UUID NOT NULL REFERENCES pis_v2.hospital_profile(id),
    entity_kind_code VARCHAR(64) NOT NULL,
    trigger_code VARCHAR(64) NOT NULL,
    label_template_code VARCHAR(128) NOT NULL,
    logical_printer_code VARCHAR(128) NOT NULL,
    copies INTEGER NOT NULL,
    retry_limit INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_v2_print_strategy UNIQUE (hospital_profile_id, entity_kind_code, trigger_code),
    CONSTRAINT ck_v2_print_strategy_copies CHECK (copies BETWEEN 1 AND 20),
    CONSTRAINT ck_v2_print_strategy_retry CHECK (retry_limit BETWEEN 0 AND 10),
    CONSTRAINT ck_v2_print_strategy_version CHECK (configuration_version > 0)
);

CREATE TABLE pis_v2.hospital_report_configuration (
    id UUID PRIMARY KEY,
    hospital_profile_id UUID NOT NULL REFERENCES pis_v2.hospital_profile(id),
    canonical_business_type_code VARCHAR(64) NOT NULL,
    default_report_template_code VARCHAR(128) NOT NULL,
    signature_display_mode VARCHAR(64) NOT NULL,
    hospital_logo_reference VARCHAR(512),
    footer_text VARCHAR(2000),
    enabled BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_v2_hospital_report_config UNIQUE (hospital_profile_id, canonical_business_type_code),
    CONSTRAINT ck_v2_hospital_report_config_version CHECK (configuration_version > 0)
);

CREATE TABLE pis_v2.device_configuration (
    id UUID PRIMARY KEY,
    hospital_profile_id UUID NOT NULL REFERENCES pis_v2.hospital_profile(id),
    device_code VARCHAR(128) NOT NULL,
    device_type_code VARCHAR(64) NOT NULL,
    adapter_code VARCHAR(64) NOT NULL,
    endpoint_reference VARCHAR(512),
    settings JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_v2_device_config UNIQUE (hospital_profile_id, device_code),
    CONSTRAINT ck_v2_device_settings_object CHECK (jsonb_typeof(settings) = 'object'),
    CONSTRAINT ck_v2_device_config_version CHECK (configuration_version > 0)
);

CREATE TABLE pis_v2.integration_configuration (
    id UUID PRIMARY KEY,
    hospital_profile_id UUID NOT NULL REFERENCES pis_v2.hospital_profile(id),
    system_code VARCHAR(128) NOT NULL,
    system_type_code VARCHAR(64) NOT NULL,
    adapter_code VARCHAR(64) NOT NULL,
    endpoint_reference VARCHAR(512),
    settings JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled BOOLEAN NOT NULL,
    configuration_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_v2_integration_config UNIQUE (hospital_profile_id, system_code),
    CONSTRAINT ck_v2_integration_settings_object CHECK (jsonb_typeof(settings) = 'object'),
    CONSTRAINT ck_v2_integration_config_version CHECK (configuration_version > 0)
);

INSERT INTO pis_v2.hospital_profile
    (id, profile_code, display_name, legal_name, timezone_id, locale_code, enabled,
     configuration_version, created_at, updated_at)
SELECT md5('PIS-SITE-HOSPITAL:' || seed.profile_code)::uuid,
       seed.profile_code, seed.display_name, seed.legal_name, 'Asia/Shanghai', 'zh-CN', TRUE, 1,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('LOCAL_HOSPITAL', '本地演示医院', '本地演示医院（合成）'),
    ('HOSPITAL_A', '合成医院 A', '合成医院 A'),
    ('HOSPITAL_B', '合成医院 B', '合成医院 B')
) AS seed(profile_code, display_name, legal_name)
ON CONFLICT (profile_code) DO NOTHING;

INSERT INTO pis_v2.hospital_campus
    (id, hospital_profile_id, campus_code, campus_name, enabled, configuration_version, created_at, updated_at)
SELECT md5('PIS-SITE-CAMPUS:' || hp.profile_code || ':MAIN')::uuid,
       hp.id, 'MAIN', hp.display_name || '主院区', TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM pis_v2.hospital_profile hp
ON CONFLICT (hospital_profile_id, campus_code) DO NOTHING;

INSERT INTO pis_v2.hospital_department
    (id, hospital_profile_id, campus_id, department_code, department_name, department_type_code,
     enabled, configuration_version, created_at, updated_at)
SELECT md5('PIS-SITE-DEPARTMENT:' || hp.profile_code || ':PATHOLOGY')::uuid,
       hp.id, hc.id, 'PATHOLOGY', '病理科', 'PATHOLOGY', TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM pis_v2.hospital_profile hp
JOIN pis_v2.hospital_campus hc ON hc.hospital_profile_id = hp.id AND hc.campus_code = 'MAIN'
ON CONFLICT (hospital_profile_id, department_code) DO NOTHING;

INSERT INTO pis_v2.hospital_business_type_configuration
    (id, hospital_profile_id, canonical_business_type_code, core_business_type_code, enabled,
     configuration_version, created_at, updated_at)
SELECT md5('PIS-SITE-BUSINESS:' || hp.profile_code || ':' || seed.canonical_code)::uuid,
       hp.id, seed.canonical_code, seed.core_code,
       CASE WHEN hp.profile_code = 'HOSPITAL_B' AND seed.canonical_code IN ('FROZEN', 'MOLECULAR')
            THEN FALSE ELSE TRUE END,
       1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM pis_v2.hospital_profile hp
CROSS JOIN (VALUES
    ('ROUTINE', 'HISTOLOGY'),
    ('FROZEN', 'FROZEN'),
    ('CYTOLOGY', 'CYTOLOGY_NON_GYN'),
    ('MOLECULAR', 'MOLECULAR'),
    ('CONSULTATION', 'REFERRAL')
) AS seed(canonical_code, core_code)
ON CONFLICT (hospital_profile_id, canonical_business_type_code) DO NOTHING;

INSERT INTO pis_v2.hospital_workflow_configuration
    (id, hospital_profile_id, canonical_business_type_code, require_review, require_audit,
     allow_direct_slide, enabled, configuration_version, created_at, updated_at)
SELECT md5('PIS-SITE-WORKFLOW:' || hp.profile_code || ':' || btc.canonical_business_type_code)::uuid,
       hp.id, btc.canonical_business_type_code,
       CASE WHEN hp.profile_code = 'HOSPITAL_B' THEN FALSE ELSE TRUE END,
       TRUE,
       btc.canonical_business_type_code IN ('CYTOLOGY', 'CONSULTATION'),
       btc.enabled, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM pis_v2.hospital_profile hp
JOIN pis_v2.hospital_business_type_configuration btc ON btc.hospital_profile_id = hp.id
ON CONFLICT (hospital_profile_id, canonical_business_type_code) DO NOTHING;

INSERT INTO pis_v2.label_template
    (id, hospital_profile_id, template_code, template_name, entity_kind_code, renderer_code,
     content_template, enabled, configuration_version, created_at, updated_at)
SELECT md5('PIS-SITE-LABEL:' || hp.profile_code || ':MATERIAL')::uuid,
       hp.id, 'MATERIAL-LABEL', '材料标签', 'MATERIAL', 'ZPL_COMPATIBLE',
       '{{businessCode}}|{{pathologyNo}}|{{materialCode}}', TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM pis_v2.hospital_profile hp
ON CONFLICT (hospital_profile_id, template_code) DO NOTHING;

INSERT INTO pis_v2.printer_mapping
    (id, hospital_profile_id, campus_id, department_id, logical_printer_code, adapter_code,
     endpoint_reference, enabled, configuration_version, created_at, updated_at)
SELECT md5('PIS-SITE-PRINTER:' || hp.profile_code || ':MATERIAL')::uuid,
       hp.id, hc.id, hd.id, 'MATERIAL-PRINTER', 'MOCK', 'mock://material-printer',
       TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM pis_v2.hospital_profile hp
JOIN pis_v2.hospital_campus hc ON hc.hospital_profile_id = hp.id AND hc.campus_code = 'MAIN'
JOIN pis_v2.hospital_department hd ON hd.hospital_profile_id = hp.id AND hd.department_code = 'PATHOLOGY'
ON CONFLICT (hospital_profile_id, logical_printer_code) DO NOTHING;

INSERT INTO pis_v2.print_strategy
    (id, hospital_profile_id, entity_kind_code, trigger_code, label_template_code,
     logical_printer_code, copies, retry_limit, enabled, configuration_version, created_at, updated_at)
SELECT md5('PIS-SITE-PRINT-STRATEGY:' || hp.profile_code || ':SLIDE')::uuid,
       hp.id, 'SLIDE', 'ON_MATERIAL_CREATED', 'MATERIAL-LABEL', 'MATERIAL-PRINTER',
       CASE WHEN hp.profile_code = 'HOSPITAL_B' THEN 2 ELSE 1 END,
       2, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM pis_v2.hospital_profile hp
ON CONFLICT (hospital_profile_id, entity_kind_code, trigger_code) DO NOTHING;

INSERT INTO pis_v2.hospital_report_configuration
    (id, hospital_profile_id, canonical_business_type_code, default_report_template_code,
     signature_display_mode, hospital_logo_reference, footer_text, enabled,
     configuration_version, created_at, updated_at)
SELECT md5('PIS-SITE-REPORT:' || hp.profile_code || ':ROUTINE')::uuid,
       hp.id, 'ROUTINE', 'DEFAULT-REPORT-HISTOLOGY',
       CASE WHEN hp.profile_code = 'HOSPITAL_B' THEN 'NAME_AND_TITLE' ELSE 'NAME_CODE_AND_TITLE' END,
       'config://hospital-logo/' || lower(hp.profile_code),
       CASE WHEN hp.profile_code = 'HOSPITAL_B' THEN '合成医院 B 报告页脚' ELSE '病理报告仅供临床使用' END,
       TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM pis_v2.hospital_profile hp
ON CONFLICT (hospital_profile_id, canonical_business_type_code) DO NOTHING;

INSERT INTO pis_v2.device_configuration
    (id, hospital_profile_id, device_code, device_type_code, adapter_code, endpoint_reference,
     settings, enabled, configuration_version, created_at, updated_at)
SELECT md5('PIS-SITE-DEVICE:' || hp.profile_code || ':SCANNER')::uuid,
       hp.id, 'SYNTH-SCANNER', 'DIGITAL_SCANNER', 'MOCK_SCANNER', 'mock://scanner',
       '{"callbackMode":"SYNTHETIC"}'::jsonb, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM pis_v2.hospital_profile hp
ON CONFLICT (hospital_profile_id, device_code) DO NOTHING;

INSERT INTO pis_v2.integration_configuration
    (id, hospital_profile_id, system_code, system_type_code, adapter_code, endpoint_reference,
     settings, enabled, configuration_version, created_at, updated_at)
SELECT md5('PIS-SITE-INTEGRATION:' || hp.profile_code || ':HIS')::uuid,
       hp.id, 'SYNTH-HIS', 'HIS', 'MOCK_HIS', 'mock://his',
       '{"contractVersion":"P12"}'::jsonb, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM pis_v2.hospital_profile hp
ON CONFLICT (hospital_profile_id, system_code) DO NOTHING;

INSERT INTO pis_v2.pathology_number_rule
    (id, business_type_id, organization_reference, number_kind_code, prefix, scope_code,
     padding_width, next_serial, active, configuration_version, created_at, updated_at, created_by_ref)
SELECT md5('PIS-SITE-NUMBER:' || hp.profile_code || ':' || btc.core_business_type_code || ':' || kind.code)::uuid,
       bt.id, hp.profile_code, kind.code,
       CASE hp.profile_code WHEN 'HOSPITAL_A' THEN 'A-' ELSE 'B-' END ||
       CASE btc.canonical_business_type_code
           WHEN 'ROUTINE' THEN 'P'
           WHEN 'FROZEN' THEN 'F'
           WHEN 'CYTOLOGY' THEN 'C'
           WHEN 'MOLECULAR' THEN 'M'
           ELSE 'R'
       END || CASE kind.code WHEN 'SPECIMEN' THEN 'S-' ELSE '-' END,
       'ORGANIZATION', CASE kind.code WHEN 'CASE' THEN 6 ELSE 7 END, 1,
       btc.enabled, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'S01-SEED'
FROM pis_v2.hospital_profile hp
JOIN pis_v2.hospital_business_type_configuration btc ON btc.hospital_profile_id = hp.id
JOIN pis_v2.business_type bt ON bt.business_type_code = btc.core_business_type_code
CROSS JOIN (VALUES ('CASE'), ('SPECIMEN')) AS kind(code)
WHERE hp.profile_code IN ('HOSPITAL_A', 'HOSPITAL_B')
ON CONFLICT (organization_reference, business_type_id, number_kind_code) DO NOTHING;

CREATE INDEX ix_v2_hospital_business_enabled
    ON pis_v2.hospital_business_type_configuration (hospital_profile_id, enabled, canonical_business_type_code);
CREATE INDEX ix_v2_device_configuration_enabled
    ON pis_v2.device_configuration (hospital_profile_id, device_type_code, enabled);
CREATE INDEX ix_v2_integration_configuration_enabled
    ON pis_v2.integration_configuration (hospital_profile_id, system_type_code, enabled);

INSERT INTO pis_v2.schema_metadata (id, schema_code, version_code, recorded_at)
VALUES ('00000000-0000-0000-0000-00000000b201', 'PIS_V2', 'S01-HOSPITAL-PROFILE', CURRENT_TIMESTAMP)
ON CONFLICT (schema_code) DO UPDATE
SET version_code = 'S01-HOSPITAL-PROFILE', recorded_at = CURRENT_TIMESTAMP;
