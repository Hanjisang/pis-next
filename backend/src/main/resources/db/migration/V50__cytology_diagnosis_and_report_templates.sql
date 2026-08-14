-- CYTO-010/CYTO-011: versioned cytology diagnosis schemas and report definitions.
-- TBS fields follow the IARC/WHO-published 2014 Bethesda reporting structure; no patient conclusion is seeded.

INSERT INTO pis_v2.diagnosis_template_version
    (id, template_id, version_no, schema_definition, status_code, published_at, published_by_ref,
     created_at, created_by_ref, concurrency_version)
SELECT md5('PIS-V2-CYTOLOGY-DIAGNOSIS-V2:' || bt.business_type_code)::uuid,
       dt.id, 2,
       CASE bt.business_type_code
           WHEN 'CYTOLOGY_GYN' THEN
               '{"version":2,"standardCode":"TBS-2014","components":[{"type":"SINGLE_SELECT","code":"specimenType","label":"标本类型","required":true,"options":[{"value":"CONVENTIONAL_SMEAR","label":"传统涂片"},{"value":"LIQUID_BASED","label":"液基制片"},{"value":"OTHER","label":"其他"}]},{"type":"SINGLE_SELECT","code":"specimenAdequacy","label":"标本满意度","required":true,"options":[{"value":"SATISFACTORY","label":"满意"},{"value":"UNSATISFACTORY","label":"不满意"}]},{"type":"BOOLEAN","code":"transformationZoneComponent","label":"见转化区成分","required":true},{"type":"SINGLE_SELECT","code":"generalCategory","label":"总体分类","required":true,"options":[{"value":"NILM","label":"未见上皮内病变或恶性病变"},{"value":"OTHER","label":"其他"},{"value":"EPITHELIAL_CELL_ABNORMALITY","label":"上皮细胞异常"}]},{"type":"TEXTAREA","code":"interpretationResult","label":"解释/结果","required":true},{"type":"TEXTAREA","code":"microscopicDescription","label":"镜下所见","required":true},{"type":"TEXTAREA","code":"diagnosisText","label":"细胞学诊断","required":true},{"type":"TEXTAREA","code":"comment","label":"备注与建议"}]}'::jsonb
           WHEN 'CYTOLOGY_FNA' THEN
               '{"version":2,"standardCode":"CYTOLOGY-FNA-STRUCTURED","components":[{"type":"TEXT","code":"specimenSite","label":"穿刺部位","required":true},{"type":"SINGLE_SELECT","code":"specimenAdequacy","label":"标本满意度","required":true,"options":[{"value":"DIAGNOSTIC","label":"可诊断"},{"value":"NON_DIAGNOSTIC","label":"不可诊断"}]},{"type":"TEXT","code":"diagnosticCategory","label":"诊断分类","required":true},{"type":"TEXTAREA","code":"interpretationResult","label":"解释/结果","required":true},{"type":"TEXTAREA","code":"microscopicDescription","label":"镜下所见","required":true},{"type":"TEXTAREA","code":"diagnosisText","label":"细胞学诊断","required":true},{"type":"TEXTAREA","code":"comment","label":"备注与建议"}]}'::jsonb
           ELSE
               '{"version":2,"standardCode":"CYTOLOGY-NON-GYN-STRUCTURED","components":[{"type":"TEXT","code":"specimenType","label":"标本类型","required":true},{"type":"SINGLE_SELECT","code":"specimenAdequacy","label":"标本满意度","required":true,"options":[{"value":"SATISFACTORY","label":"满意"},{"value":"UNSATISFACTORY","label":"不满意"}]},{"type":"TEXT","code":"diagnosticCategory","label":"诊断分类","required":true},{"type":"TEXTAREA","code":"interpretationResult","label":"解释/结果","required":true},{"type":"TEXTAREA","code":"microscopicDescription","label":"镜下所见","required":true},{"type":"TEXTAREA","code":"diagnosisText","label":"细胞学诊断","required":true},{"type":"TEXTAREA","code":"comment","label":"备注与建议"}]}'::jsonb
       END,
       'PUBLISHED', CURRENT_TIMESTAMP, 'V2-CYTOLOGY-SEED', CURRENT_TIMESTAMP, 'V2-CYTOLOGY-SEED', 0
FROM pis_v2.business_type bt
JOIN pis_v2.diagnosis_template dt
  ON dt.business_type_id = bt.id
 AND dt.organization_reference = 'LOCAL_HOSPITAL'
 AND dt.template_code = 'DEFAULT-' || bt.business_type_code
WHERE bt.business_type_code IN ('CYTOLOGY_GYN', 'CYTOLOGY_NON_GYN', 'CYTOLOGY_FNA')
ON CONFLICT (template_id, version_no) DO NOTHING;

INSERT INTO pis_v2.report_template_version
    (id, template_id, version_no, definition, status_code, published_at, published_by_ref,
     created_at, created_by_ref, concurrency_version)
SELECT md5('PIS-V2-CYTOLOGY-REPORT-V2:' || bt.business_type_code)::uuid,
       rt.id, 2,
       jsonb_build_object(
           'schemaVersion', 1,
           'title', bt.display_name || '报告',
           'category', 'GENERAL',
           'page', jsonb_build_object('size', 'A4', 'showPageNumber', true),
           'sections', jsonb_build_array(
               jsonb_build_object('code', 'BASIC', 'label', '基本信息', 'source', 'CASE',
                   'fields', jsonb_build_array('pathologyNo', 'patientReference', 'visitReference')),
               jsonb_build_object('code', 'MATERIAL', 'label', '细胞学标本与玻片', 'source', 'MATERIAL',
                   'fields', jsonb_build_array('specimenNo', 'slideCode', 'slideType')),
               jsonb_build_object('code', 'CYTOLOGY', 'label', '细胞学结构化结果', 'source', 'DIAGNOSIS',
                   'fields', jsonb_build_array('structuredData', 'microscopicDescription')),
               jsonb_build_object('code', 'DIAGNOSIS', 'label', '细胞学诊断', 'source', 'DIAGNOSIS',
                   'fields', jsonb_build_array('diagnosisText', 'comment')),
               jsonb_build_object('code', 'SIGNATURE', 'label', '签发信息', 'source', 'SIGNATURE',
                   'fields', jsonb_build_array('signedBy', 'signedAt')))),
       'PUBLISHED', CURRENT_TIMESTAMP, 'V2-CYTOLOGY-SEED', CURRENT_TIMESTAMP, 'V2-CYTOLOGY-SEED', 0
FROM pis_v2.business_type bt
JOIN pis_v2.report_template rt
  ON rt.business_type_id = bt.id
 AND rt.organization_reference = 'LOCAL_HOSPITAL'
 AND rt.template_code = 'DEFAULT-REPORT-' || bt.business_type_code
WHERE bt.business_type_code IN ('CYTOLOGY_GYN', 'CYTOLOGY_NON_GYN', 'CYTOLOGY_FNA')
ON CONFLICT (template_id, version_no) DO NOTHING;

UPDATE pis_v2.schema_metadata
SET version_code = 'CYTOLOGY-DIAGNOSIS-REPORT-CLOSURE', recorded_at = CURRENT_TIMESTAMP
WHERE schema_code = 'PIS_V2';
