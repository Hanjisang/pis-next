# P11 全病理诊断、报告与文件数据字典

文档状态：已完成
数据库平台：待确认；列采用逻辑类型
覆盖表：P11-TBL-009–012、040、056–063、076、082–084，共17张

列编号按表号加两位序号分配。诊断记录、医学判读、报告版本、报告组成引用、PDF文件和数字材料版本均为独立对象；签发后的正文和组成引用不可直接更新。

## 1. 诊断和报告

| 表/列范围 | 表中文名 | 完整正式列 | 关键约束与追溯 |
|---|---|---|---|
| P11-TBL-009 / P11-COL-0901–0914 | 诊断责任 | id、case_id、diagnosis_task_no、responsibility_kind_code、assigned_person_ref、actual_operator_ref、authorization_delegation_id、responsibility_state_code、assigned_at、accepted_at、returned_at、record_version_no、concurrency_version、created_at | assigned_person_ref和responsibility_kind_code非空；代理不替代实际操作者；责任转移只追加；P05-INV-019–024/041–043 |
| P11-TBL-010 / P11-COL-1001–1014 | 诊断记录 | id、diagnosis_task_id、diagnosis_record_no、diagnosis_kind_code、diagnosis_state_code、current_version_id、basis_count、responsible_person_ref、submitted_at、confirmed_at、disagreement_code、record_version_no、concurrency_version、created_at | 任务与记录分离；current_version_id只能指向已确认版本；确认后不得无痕更新；P05-INV-024–027 |
| P11-TBL-011 / P11-COL-1101–1115 | 报告生命周期 | id、case_id、report_lifecycle_no、report_kind_code、report_lifecycle_state_code、current_report_version_id、current_effective_version_id、report_display_no、issued_at、withdrawn_at、superseded_by_lifecycle_id、record_version_no、concurrency_version、created_at、created_by_ref | report_display_no在医院作用域唯一；current_effective_version_id受签发、补充、更正、撤回守卫；P05-INV-028/030–033 |
| P11-TBL-012 / P11-COL-1201–1213 | 出站业务事件边界 | id、source_aggregate_id、source_aggregate_kind_code、event_family_code、current_event_version_no、event_lifecycle_state_code、target_count、last_delivery_at、reconciliation_state_code、record_version_no、created_at、created_by_ref、sensitivity_code | 本地事实先形成；投递状态不得写回覆盖报告或诊断事实；P05-INV-032/037–040 |
| P11-TBL-040 / P11-COL-4001–4013 | 报告模板版本 | id、template_code、template_version_no、template_state_code、template_kind_code、schema_document_ref、published_at、retired_at、organization_reference、change_reason_text、created_at、created_by_ref、sensitivity_code | template_code+version唯一；模板停用不影响历史报告；不产生API Schema；P05-INV-028/032 |
| P11-TBL-056 / P11-COL-5601–5614 | 诊断记录版本 | id、diagnosis_record_id、diagnosis_version_no、version_kind_code、diagnosis_conclusion_text、diagnosis_basis_summary_text、responsible_person_ref、submitted_at、confirmed_at、supersedes_version_id、record_digest、created_at、created_by_ref、sensitivity_code | 版本号在诊断记录内唯一；正文和依据摘要不可更新；取代关系追加；D2；P05-INV-024/025/027 |
| P11-TBL-057 / P11-COL-5701–5716 | 报告版本 | id、report_lifecycle_id、report_version_no、version_kind_code、version_state_code、report_title_text、report_body_text、diagnosis_version_id、template_version_id、predecessor_version_id、superseded_version_id、issued_by_ref、issued_at、withdrawal_reason_code、content_digest、created_at | report_lifecycle_id+report_version_no唯一；签发后report_body_text不可更新；撤回不删除；D2；P05-INV-028/030–033 |
| P11-TBL-058 / P11-COL-5801–5812 | 报告组成引用 | id、report_version_id、component_kind_code、component_object_id、component_version_id、component_source_module_code、reference_role_code、fixed_at、reference_digest、superseded_by_reference_id、created_at、created_by_ref | 同一报告版本+组件+角色唯一；只引用固定版本；不覆盖组成报告；P05-INV-031/032/069 |
| P11-TBL-059 / P11-COL-5901–5914 | 有效分子结果版本 | id、molecular_task_id、molecular_run_id、qc_result_id、raw_result_id、valid_result_version_no、validity_state_code、result_kind_code、result_value_text、validity_reason_text、confirmed_by_ref、confirmed_at、superseded_version_id、created_at | 只有质控通过且原始结果完整时可确认；复测生成新版本；D2；P05-INV-066/067/068 |
| P11-TBL-060 / P11-COL-6001–6013 | 分子医学判读版本 | id、valid_result_version_id、interpretation_version_no、interpretation_state_code、interpretation_text、evidence_summary_text、interpreter_ref、interpreted_at、clinical_significance_code、supersedes_version_id、record_digest、created_at、created_by_ref | 判读只能引用有效结果；不得把原始设备结果当医学判读；D2；P05-INV-024/066/069 |
| P11-TBL-061 / P11-COL-6101–6115 | 外部结果版本 | id、referral_id、external_result_version_no、external_accession_id、external_result_kind_code、external_result_state_code、external_result_text、external_report_file_id、received_at、verified_at、verified_by_ref、verification_record_id、supersedes_version_id、external_digest、created_at | 来源机构+外部号+版本唯一；verified_at前不可进入有效本地依据；外部事实标记不可删除；D2；P05-INV-069/070 |
| P11-TBL-062 / P11-COL-6201–6214 | 数字材料版本 | id、digital_material_id、digital_version_no、digital_version_state_code、source_actual_slide_id、source_external_material_id、file_business_reference_id、image_quality_code、diagnostic_availability_code、scan_attempt_id、created_at、created_by_ref、record_digest、sensitivity_code | source_actual_slide_id或合法外部材料必须存在；被引用版本不可静默替换；P05-INV-005/013/025 |
| P11-TBL-063 / P11-COL-6301–6313 | PDF文件版本 | id、report_version_id、file_id、pdf_version_no、generation_kind_code、generation_state_code、template_version_id、generated_at、generated_by_ref、content_digest、supersedes_pdf_version_id、created_at、sensitivity_code | 重新生成形成新文件版本；历史报告引用不变；P05-INV-028/032 |

## 2. 诊断依据和文件引用

| 表/列范围 | 表中文名 | 完整正式列 | 关键约束与追溯 |
|---|---|---|---|
| P11-TBL-076 / P11-COL-7601–7613 | 诊断依据引用 | id、diagnosis_record_version_id、evidence_kind_code、evidence_object_id、evidence_version_id、evidence_source_module_code、evidence_role_code、is_external_fact、fixed_at、reference_digest、created_at、created_by_ref、sensitivity_code | 依据必须能定位到具体对象和版本；外部事实is_external_fact为真实二值；不得只存文本；P05-INV-024/027/069 |
| P11-TBL-082 / P11-COL-8201–8217 | 业务文件元数据 | id、file_no、file_kind_code、file_version_no、storage_provider_reference、storage_object_reference、original_file_name、format_code、file_size_value、digest_algorithm_code、digest_value、created_source_code、created_at、availability_state_code、integrity_state_code、archive_state_code、sensitivity_code | 文件元数据与二进制分离；storage_object_reference为逻辑引用；digest_value和大小在文件存在时必填；D2/D3；P05-INV-025/032 |
| P11-TBL-083 / P11-COL-8301–8313 | 文件完整性校验 | id、file_id、file_version_no、check_kind_code、check_state_code、expected_digest_value、observed_digest_value、checked_at、checked_by_ref、openability_state_code、security_scan_state_code、failure_reason_text、created_at | 每次校验追加；损坏不修改历史报告或结果；P05-INV-025/047/048 |
| P11-TBL-084 / P11-COL-8401–8412 | 文件业务引用 | id、file_id、file_version_no、business_object_id、business_object_kind_code、business_version_id、reference_role_code、reference_state_code、fixed_at、created_at、created_by_ref、sensitivity_code | 文件可被报告、数字材料、外部结果和恢复校验引用；删除文件不删除业务事实；P05-INV-025/032/047 |

## 3. 版本和医学事实属性

1. `report_body_text`、`diagnosis_conclusion_text`、`interpretation_text`和外部原文只追加；更正、补充、撤回和重新签发通过新版本或事件表达。
2. 报告组成引用保存`component_version_id`，综合报告不会把组织、细胞、分子组成版本合并成一个可覆盖对象。
3. PDF和数字切片只保存业务元数据、逻辑存储引用和完整性证据；数据库平台确认前不规定对象存储字段或二进制类型。
4. `external_result_state_code`和`verified_at`明确区分外部接收、核验和本地可引用状态；外部结果不得伪装成本地运行事实。
