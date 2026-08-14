-- RPT-003/RPT-009: versioned designer presets are reference data; hospital templates remain scoped copies.

CREATE TABLE pis_v2.report_template_preset (
    id UUID PRIMARY KEY,
    preset_code VARCHAR(64) NOT NULL UNIQUE,
    preset_name VARCHAR(200) NOT NULL,
    tumor_site_code VARCHAR(64) NOT NULL,
    definition JSONB NOT NULL,
    enabled BOOLEAN NOT NULL,
    preset_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_ref VARCHAR(128) NOT NULL,
    CONSTRAINT ck_v2_report_template_preset_version CHECK (preset_version > 0)
);

ALTER TABLE pis_v2.report_template
    ADD COLUMN source_preset_code VARCHAR(64);

ALTER TABLE pis_v2.report_template
    ADD CONSTRAINT fk_v2_report_template_preset
    FOREIGN KEY (source_preset_code) REFERENCES pis_v2.report_template_preset(preset_code);

INSERT INTO pis_v2.report_template_preset
    (id, preset_code, preset_name, tumor_site_code, definition, enabled, preset_version,
     created_at, created_by_ref)
VALUES
    (md5('PIS-V2-RPT-PRESET:LUNG')::uuid, 'TUMOR-LUNG', '肺肿瘤报告结构', 'LUNG',
     '{"schemaVersion":1,"title":"肺肿瘤病理报告","category":"TUMOR","tumorSiteCode":"LUNG","page":{"size":"A4","showPageNumber":true},"sections":[{"code":"BASIC","label":"基本信息","source":"CASE","fields":["pathologyNo","patientReference","visitReference"]},{"code":"MATERIAL","label":"标本与材料","source":"MATERIAL","fields":["specimenNo","blockCode","slideCode"]},{"code":"MICROSCOPY","label":"镜下所见","source":"DIAGNOSIS","fields":["microscopicDescription"]},{"code":"DIAGNOSIS","label":"病理诊断","source":"DIAGNOSIS","fields":["diagnosisText","structuredData","comment"]},{"code":"TECHNICAL","label":"辅助检查","source":"TECHNICAL","fields":["projectCode","result"]},{"code":"SIGNATURE","label":"签发信息","source":"SIGNATURE","fields":["signedBy","signedAt"]}]}'::jsonb,
     TRUE, 1, CURRENT_TIMESTAMP, 'V2-RPT-PRESET-SEED'),
    (md5('PIS-V2-RPT-PRESET:BREAST')::uuid, 'TUMOR-BREAST', '乳腺肿瘤报告结构', 'BREAST',
     '{"schemaVersion":1,"title":"乳腺肿瘤病理报告","category":"TUMOR","tumorSiteCode":"BREAST","page":{"size":"A4","showPageNumber":true},"sections":[{"code":"BASIC","label":"基本信息","source":"CASE","fields":["pathologyNo","patientReference","visitReference"]},{"code":"MATERIAL","label":"标本与材料","source":"MATERIAL","fields":["specimenNo","blockCode","slideCode"]},{"code":"MICROSCOPY","label":"镜下所见","source":"DIAGNOSIS","fields":["microscopicDescription"]},{"code":"DIAGNOSIS","label":"病理诊断","source":"DIAGNOSIS","fields":["diagnosisText","structuredData","comment"]},{"code":"TECHNICAL","label":"辅助检查","source":"TECHNICAL","fields":["projectCode","result"]},{"code":"SIGNATURE","label":"签发信息","source":"SIGNATURE","fields":["signedBy","signedAt"]}]}'::jsonb,
     TRUE, 1, CURRENT_TIMESTAMP, 'V2-RPT-PRESET-SEED'),
    (md5('PIS-V2-RPT-PRESET:COLORECTAL')::uuid, 'TUMOR-COLORECTAL', '结直肠肿瘤报告结构', 'COLORECTAL',
     '{"schemaVersion":1,"title":"结直肠肿瘤病理报告","category":"TUMOR","tumorSiteCode":"COLORECTAL","page":{"size":"A4","showPageNumber":true},"sections":[{"code":"BASIC","label":"基本信息","source":"CASE","fields":["pathologyNo","patientReference","visitReference"]},{"code":"MATERIAL","label":"标本与材料","source":"MATERIAL","fields":["specimenNo","blockCode","slideCode"]},{"code":"MICROSCOPY","label":"镜下所见","source":"DIAGNOSIS","fields":["microscopicDescription"]},{"code":"DIAGNOSIS","label":"病理诊断","source":"DIAGNOSIS","fields":["diagnosisText","structuredData","comment"]},{"code":"TECHNICAL","label":"辅助检查","source":"TECHNICAL","fields":["projectCode","result"]},{"code":"SIGNATURE","label":"签发信息","source":"SIGNATURE","fields":["signedBy","signedAt"]}]}'::jsonb,
     TRUE, 1, CURRENT_TIMESTAMP, 'V2-RPT-PRESET-SEED');

UPDATE pis_v2.schema_metadata
SET version_code = 'RPT-TEMPLATE-DESIGNER-PRESETS', recorded_at = CURRENT_TIMESTAMP
WHERE schema_code = 'PIS_V2';
