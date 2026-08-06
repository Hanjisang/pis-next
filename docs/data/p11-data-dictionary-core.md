# P11 全病理核心数据字典

文档状态：已完成
数据库平台：待确认；类型列使用逻辑类型
覆盖表：P11-TBL-001–003、041、071–075、077、080–081，共12张

## 1. 字段编号和字段组

每张表的列按“表号两位+列序号两位”分配，例如P11-TBL-001的第1列为P11-COL-0101。表行中的列按该顺序展开，范围不表示省略列。`InternalId`、`BusinessNumber`、`ExternalIdentifier`、`ControlledCode`、`UtcInstant`、`VersionNumber`和`ConcurrencyVersion`均按P11规则映射，具体产品类型、长度和精度待平台确认。

公共字段含义：`id`为稳定内部主键；`created_at/created_by_ref`为记录创建事实；`source_system_code`为来源命名空间；`record_version_no`为业务版本；`concurrency_version`为乐观并发版本；`sensitivity_code`为P11逻辑敏感等级；`archive_state_code`为归档状态。不可变事实不包含可变更新字段。

## 2. 核心业务表

| 表/列范围 | 表中文名 | 完整正式列（按列编号顺序） | 关键类型、可空、默认与约束 |
|---|---|---|---|
| P11-TBL-001 / P11-COL-0101–0114 | 病理申请 | id、source_system_code、application_no、application_lifecycle_state_code、patient_reference_id、visit_reference_id、request_received_at、request_channel_code、request_content_text、record_version_no、concurrency_version、created_at、created_by_ref、sensitivity_code | id、application_no、patient_reference_id、request_received_at非空；application_no在机构作用域唯一；状态为受控代码；申请正文可空但原始消息必须关联；P05-INV-001/037/038 |
| P11-TBL-002 / P11-COL-0201–0215 | 病理病例 | id、case_no、case_lifecycle_state_code、request_id、patient_visit_snapshot_id、case_source_code、case_established_at、case_effective_at、case_termination_reason_code、record_version_no、concurrency_version、created_at、created_by_ref、sensitivity_code、archive_state_code | id、case_no、request_id、case_established_at非空；case_no在医院和业务周期作用域唯一；patient_visit_snapshot_id签发前必填；不可级联删除；P05-INV-002/006/007/010 |
| P11-TBL-003 / P11-COL-0301–0315 | 标本 | id、case_id、specimen_no、specimen_kind_code、specimen_source_code、collection_site_text、collection_method_code、specimen_lifecycle_state_code、received_at、received_by_ref、specimen_difference_code、record_version_no、concurrency_version、created_at、created_by_ref、sensitivity_code | id、case_id、specimen_no、specimen_kind_code、specimen_lifecycle_state_code非空；specimen_no在病例作用域唯一；接收事实和差异独立追加；P05-INV-009/010/014/019 |
| P11-TBL-041 / P11-COL-4101–4110 | 病例模态参与项 | id、case_id、modality_code、modality_role_code、linked_business_object_id、linked_business_object_kind_code、participation_state_code、participation_started_at、participation_ended_at、created_by_ref | id、case_id、modality_code、modality_role_code非空；case_id+modality_code+modality_role_code唯一；linked_business_object_id为受控逻辑引用；结束时间不得早于开始时间；P05-INV-062/069 |
| P11-TBL-071 / P11-COL-7101–7112 | 患者上下文引用 | id、source_system_code、external_patient_id、patient_namespace_code、reference_state_code、first_seen_at、last_verified_at、mapping_conflict_code、source_payload_ref、created_at、created_by_ref、sensitivity_code | id、source_system_code、external_patient_id、patient_namespace_code非空；来源命名空间+外部患者号唯一；不得存本地患者主数据；D1；P05-INV-001/006 |
| P11-TBL-072 / P11-COL-7201–7212 | 就诊上下文引用 | id、source_system_code、external_visit_id、visit_namespace_code、patient_reference_id、visit_reference_state_code、visit_started_at、visit_ended_at、last_verified_at、source_payload_ref、created_at、created_by_ref | id、source_system_code、external_visit_id、visit_namespace_code、patient_reference_id非空；来源+外部就诊号唯一；时间顺序由应用守卫确认；D1；P05-INV-001/006 |
| P11-TBL-073 / P11-COL-7301–7313 | 患者就诊快照 | id、patient_reference_id、visit_reference_id、snapshot_version_no、snapshot_purpose_code、patient_display_text、visit_display_text、organization_reference、snapshot_taken_at、snapshot_hash_value、created_at、created_by_ref、sensitivity_code | id、snapshot_version_no、snapshot_purpose_code、snapshot_taken_at、snapshot_hash_value非空；同一业务事实只固定一个有效快照；正文不可更新；D1；P05-INV-006/008 |
| P11-TBL-074 / P11-COL-7401–7412 | 外部申请引用 | id、request_id、source_system_code、external_request_id、external_request_kind_code、first_received_at、last_received_at、idempotency_digest、raw_message_id、reference_state_code、created_at、created_by_ref | id、request_id、source_system_code、external_request_id、idempotency_digest非空；来源+外部申请号唯一；原始值不可修改；P05-INV-037/038 |
| P11-TBL-075 / P11-COL-7501–7513 | 外部材料引用 | id、source_organization_ref、external_material_id、external_material_kind_code、linked_specimen_id、linked_referral_id、linked_digital_material_id、reference_state_code、first_received_at、last_verified_at、source_payload_ref、created_at、created_by_ref | id、source_organization_ref、external_material_id、external_material_kind_code非空；来源机构+外部材料号唯一；本地核验前不得作为诊断依据；D2；P05-INV-014/069 |
| P11-TBL-077 / P11-COL-7701–7711 | 病例模态关系 | id、case_id、modality_code、related_case_id、related_task_id、relation_kind_code、relation_version_no、relation_state_code、effective_at、created_at、created_by_ref | id、case_id、modality_code、relation_kind_code、effective_at非空；同一关系版本唯一；不合并组成对象身份；P05-INV-062/069 |
| P11-TBL-080 / P11-COL-8001–8012 | 外部系统配置 | id、external_system_code、external_system_kind_code、organization_reference、configuration_version_no、configuration_state_code、capability_code_set_ref、route_policy_code、credential_reference、published_at、created_at、created_by_ref | id、external_system_code、configuration_version_no、configuration_state_code非空；系统代码+版本唯一；凭证只保存逻辑引用；D4；不得改变核心身份和历史规则 |
| P11-TBL-081 / P11-COL-8101–8111 | 受控代码集定义 | id、code_set_name、code_set_version_no、code_value、code_display_text、code_state_code、parent_code_value、effective_at、retired_at、source_decision_ref、created_at | id、code_set_name、code_set_version_no、code_value、code_display_text、effective_at非空；代码集+版本+代码值唯一；代码停用不删除历史引用；D4 |

## 3. 核心列属性规则

1. `id`、内部引用、业务生效时间和不可变事实序列必须非空；条件性对象的引用列由业务状态检查约束控制。
2. 受控代码列必须引用P11-TBL-081或后续平台等价代码集；不得把显示文本作为关系键。
3. 患者、就诊、诊断、报告和外部原始消息使用D1或D2敏感等级；普通日志不得复制这些列的明文。
4. P11-TBL-001至003的临床事实不得级联删除；申请取消和病例终止只形成状态和历史事实。
5. 业务编号格式、最大长度、排序规则、年度作用域和保留期限均待确认，不在P11写入具体数值。

## 4. 追溯

核心表覆盖P09-REQ-0001至P09-REQ-0020、P09-REQ-0055至P09-REQ-0060、P09-REQ-0088至P09-REQ-0099，P05-INV-001至P05-INV-016、P05-INV-028至P05-INV-040和P05-INV-062至P05-INV-070；支撑P10-TXN-001、002、003、005、008、009、012、013、014、015及P10-EVT-001至021的相关来源和引用。
