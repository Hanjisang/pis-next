# P12 全病理规范化公共契约模型

文档状态：P12已基线化
Schema总数：53（公共Schema 32；21个P10架构事件载荷Schema）。
说明：字段名是契约语义名，不是P11列名；每行记录字段集合、逻辑类型/基数、必填和条件必填、来源、敏感等级、允许契约、版本和P11映射。

## 1. 公共Schema目录（32）

| Schema编号 | 名称 | 核心字段 | 类型/基数 | 必填与条件必填 | 来源/敏感等级 | 允许契约 | 版本/兼容 | P11映射 |
|---|---|---|---|---|---|---|---|---|
| P12-SCH-001 | 请求上下文 | request_identity、correlation_identity、causation_identity、source_system_code、source_message_identity、contract_version、organization_context、authenticated_subject、service_subject、request_occurred_at、business_occurred_at | ExternalIdentifier 1；ControlledCode 1；UtcInstant 0..2 | request_identity、contract_version、request_occurred_at必填；外部消息时source_message_identity必填 | 来源系统/低至中 | 全部写入、事件、文件 | 新增可选；旧字段不改义 | TBL-071、085、086 |
| P12-SCH-002 | 对象身份 | object_identity、object_kind_code、business_number、external_identifier、source_system_code、historical_number | InternalId 0..1；BusinessNumber 0..1；ExternalIdentifier 0..N | object_kind_code和至少一种身份必填；外部对象必须有来源 | 身份/中 | 命令、查询、事件 | 业务编号可新增历史，不替换内部身份 | TBL-001–089 |
| P12-SCH-003 | 患者就诊上下文 | patient_external_reference、patient_snapshot、visit_external_reference、visit_snapshot、source_system_code、snapshot_occurred_at、conflict_notice | ExternalIdentifier 0..1；StructuredDocumentReference 0..2；UtcInstant 1 | 患者或就诊引用至少一项；快照随来源消息形成 | 患者身份/高 | 申请、查询、事件 | 快照追加，不覆盖本地事实 | TBL-071–073 |
| P12-SCH-004 | 病理申请 | application_identity、application_number、request_channel_code、pathology_modality_code、request_content_text、received_at、application_version | InternalId 1；BusinessNumber 1；ControlledCode 1；LongText 0..1；VersionNumber 1 | 申请号、类型、接收时间必填；手工申请需记录责任主体 | 医疗业务/高 | 申请接收、查询、入站 | 版本追加；正文不静默覆盖 | TBL-001、074、085 |
| P12-SCH-005 | 病例上下文 | case_identity、case_number、application_identity、case_modality_code、case_version、patient_visit_context、lifecycle_state_code | InternalId 1；BusinessNumber 1；VersionNumber 1；ControlledCode 1 | 申请关联、病理类型、版本必填 | 医疗业务/高 | 建案、查询、事件 | 状态通过业务命令变更 | TBL-002、041、073 |
| P12-SCH-006 | 模态关系 | relationship_identity、case_identity、related_case_identity、relationship_kind_code、effective_version、relation_reason | InternalId 1；ControlledCode 1；VersionNumber 1；LongText 0..1 | 两端身份和关系类型必填 | 医疗关系/高 | 关联、多模态、查询 | 关系事实追加 | TBL-077 |
| P12-SCH-007 | 标本 | specimen_identity、specimen_number、case_identity、material_kind_code、source_reference、received_at、specimen_state_code | InternalId 1；BusinessNumber 1；ControlledCode 1；UtcInstant 0..1 | 来源、材料类型、病例必填；接收时received_at必填 | 材料身份/高 | 标本命令、查询、事件 | 来源不可替换 | TBL-003、074、075 |
| P12-SCH-008 | 材料来源链 | material_identity、parent_material_identity、source_kind_code、derivation_kind_code、quantity_measurement、remaining_measurement、consumption_identity | InternalId 1；ControlledCode 1；DecimalQuantity 0..N | 派生材料必须有父来源；消耗需有责任和用途 | 材料/高 | 材料命令、查询、事件 | 追加消耗事实 | TBL-020、021、028、038、045、078 |
| P12-SCH-009 | 责任交接 | handoff_identity、subject_identity、from_responsible_party、to_responsible_party、handed_over_at、accepted_at、acceptance_code、handoff_reason | InternalId 1；PersonReference 1..2；UtcInstant 1..2；ControlledCode 1 | 交出方、接收方、交出时间必填；签收完成需accepted_at | 责任/高 | 交接命令、事件、查询 | 交接不可覆盖 | TBL-026、044、078 |
| P12-SCH-010 | 诊断工作 | diagnostic_work_identity、case_identity、assigned_responsible_party、work_kind_code、expected_version、work_state_code、due_context | InternalId 1；PersonReference 1；VersionNumber 0..1；ControlledCode 1 | 责任和任务类型必填；修改时expected_version必填 | 诊断责任/高 | 诊断命令、查询 | 责任变更追加 | TBL-009、043 |
| P12-SCH-011 | 诊断记录 | diagnosis_record_identity、diagnostic_work_identity、evidence_references、diagnostic_conclusion_text、author_subject、record_version | InternalId 1；ExternalIdentifier 0..N；LongText 1；PersonReference 1；VersionNumber 1 | 正文、作者、版本必填；确认需依据引用 | 医学正文/高 | 诊断、事件、查询 | 版本不可覆盖 | TBL-010、056、076 |
| P12-SCH-012 | 报告引用 | report_lifecycle_identity、report_version_identity、report_number、report_kind_code、clinical_validity_code、composition_references | InternalId 1..2；BusinessNumber 0..1；ControlledCode 1；ExternalIdentifier 0..N | 版本引用和报告类型必填 | 医学报告/高 | 报告、综合、出站、查询 | 固定具体版本 | TBL-011、057、076、077 |
| P12-SCH-013 | 报告版本 | report_version_identity、report_lifecycle_identity、version_number、report_body_text、signed_at、signed_by_subject、supersedes_version_identity、report_state_code | InternalId 1..2；VersionNumber 1；LongText 0..1；UtcInstant 0..1；PersonReference 0..1；ControlledCode 1 | 草稿可未签发；签发时signed_at、signed_by_subject必填 | 医学报告/高 | 报告签发、回传、查询 | 只追加补充/更正/撤回/重签 | TBL-056–063 |
| P12-SCH-014 | 文件引用 | file_identity、file_version_identity、file_kind_code、format_code、size_measurement、digest、storage_reference、integrity_state_code、business_reference | InternalId 1..2；ControlledCode 1；IntegerCount 0..1；BinaryDigest 1；FileReference 1；ExternalIdentifier 1 | 摘要、存储引用、文件类型必填 | 文件/中至高 | 文件、报告、数字材料、查询 | 文件版本追加 | TBL-082–084 |
| P12-SCH-015 | 状态和版本 | subject_identity、state_machine_code、current_state_code、business_version、transition_identity、transition_occurred_at、history_reference | InternalId 1；ControlledCode 2；VersionNumber 1；ExternalIdentifier 0..1；UtcInstant 1 | 状态机、目标状态、版本必填 | 生命周期/高 | 命令响应、事件、查询 | 只通过业务转换升级 | TBL-064–070 |
| P12-SCH-016 | 游标分页 | cursor_identity、page_size_parameter_reference、sort_specification、filter_scope、returned_count、next_cursor | Cursor 0..1；IntegerCount 0..1；ShortText 0..N | page_size仅引用P09参数；next_cursor有后续页才返回 | 查询/中 | 查询 | 参数值待确认 | 查询投影 |
| P12-SCH-017 | 稳定错误 | error_code、error_category_code、human_message、subject_identity、conflict_field_name、current_state_code、expected_version、current_version、retryability_code、patient_safety_code、correlation_identity | ControlledCode 1；LongText 1；InternalId 0..1；VersionNumber 0..2；ExternalIdentifier 0..1 | error_code、category、message必填；版本冲突需双方版本 | 错误/中至高 | 所有命令、查询、接口 | 新错误码只新增；旧码不改义 | TBL-027、034、089 |
| P12-SCH-018 | 幂等回执 | idempotency_key、scope_code、payload_digest、first_outcome_code、replay_outcome_code、existing_fact_reference、processed_at、retention_parameter_reference | ExternalIdentifier 1；ControlledCode 1；BinaryDigest 1；UtcInstant 1；InternalId 0..1 | 键、作用域、摘要必填 | 集成/中 | 所有可重试写入 | 载荷不同必须冲突 | TBL-085、086、088 |
| P12-SCH-019 | 预期版本 | subject_identity、expected_business_version、current_business_version、conflict_policy_code、reload_required | InternalId 1；VersionNumber 0..2；ControlledCode 1；Boolean 1 | 可变聚合命令必填；创建命令不要求 | 并发/高 | 高风险命令 | 冲突不得静默覆盖 | TBL-001–018、064–070 |
| P12-SCH-020 | 外部消息 | source_system_code、source_message_identity、source_message_version、external_business_identity、payload_digest、received_at、raw_message_reference | ControlledCode 1；ExternalIdentifier 2；VersionNumber 1；BinaryDigest 1；UtcInstant 1；FileReference 1 | 五项来源幂等信息和原文引用必填 | 外部消息/高 | HIF入站、事件桥接 | 原始消息不可覆盖 | TBL-071、074、085 |
| P12-SCH-021 | 事件Envelope | event_identity、event_type_code、event_version、occurred_at、recorded_at、producer_module_code、subject_identity、aggregate_identity、aggregate_version、correlation_identity、causation_identity、payload_schema | InternalId 1；ControlledCode 2；VersionNumber 2；UtcInstant 2；ExternalIdentifier 0..N | 事件身份、类型、版本、生产模块、主体、发生时间必填 | 事件/中至高 | 21个事件 | Envelope不可变 | TBL-086、087 |
| P12-SCH-022 | 事件关联 | correlation_identity、causation_identity、parent_event_identity、business_scope、replay_scope_code、sensitivity_code | ExternalIdentifier 2..3；ControlledCode 2 | correlation_identity必填；重放时replay_scope必填 | 观测/中 | 事件、审计、对账 | 只追加关联 | TBL-086、088、089 |
| P12-SCH-023 | 审计上下文 | authenticated_subject、service_subject、proxy_identity、source_system_code、received_at、authorization_outcome、target_subject、operation_code、processing_outcome、correlation_identity | PersonReference 0..1；ExternalIdentifier 2..3；UtcInstant 1；ControlledCode 2 | 可信身份、目标、操作和结果必填 | 安全/高 | 高风险命令、事件、文件 | 客户端字段不能替代可信字段 | TBL-009、031、086、089 |
| P12-SCH-024 | 授权上下文 | authorization_scope、organization_context、task_responsibility、permission_kind_code、proxy_context、strong_authentication_state、second_review_state | ControlledCode 2；ExternalIdentifier 0..2；Boolean 0..2 | 权限类型、组织和对象范围必填；高风险需强认证/复核条件 | 安全/高 | 高风险命令 | P14扩展矩阵，不在P12完成矩阵 | TBL-025、026 |
| P12-SCH-025 | 代理上下文 | actual_operator、delegated_responsible_party、delegation_identity、delegation_reason、effective_from、effective_to、revocation_state_code | PersonReference 2；ExternalIdentifier 1；LongText 1；UtcInstant 2；ControlledCode 1 | 实际操作者、被代理责任、依据和有效期必填 | 责任/高 | 代理命令、审计 | 代理失效拒绝高风险操作 | TBL-030、086 |
| P12-SCH-026 | 异常质量上下文 | exception_identity、quality_event_identity、severity_code、affected_subjects、isolation_scope、handling_direction、closure_evidence | InternalId 0..2；ControlledCode 1；ExternalIdentifier 1..N；LongText 0..1 | 严重度、影响对象和处理方向必填 | 患者安全/高 | 错误、治理命令、事件 | 不能覆盖原业务事实 | TBL-027–029、087、089 |
| P12-SCH-027 | 外部结果 | external_organization_identity、external_task_identity、external_report_version、external_result_reference、source_verification_state、local_verification_state、received_at | ExternalIdentifier 3；ControlledCode 2；FileReference 0..1；UtcInstant 1 | 机构、外部任务、版本和来源核验必填 | 外部医学/高 | 入站结果、外送、查询 | 不表示本地执行 | TBL-060、061、075 |
| P12-SCH-028 | 分子结果 | task_identity、run_identity、quality_control_references、raw_result_reference、valid_result_reference、interpretation_reference、result_version | InternalId 2；ExternalIdentifier 3；VersionNumber 1 | 任务、运行和结果版本必填；有效结果需质控确认 | 分子医学/高 | 分子命令、事件、报告 | 原始/有效/判读分层 | TBL-035–037、059–060 |
| P12-SCH-029 | 细胞制备 | preparation_task_identity、preparation_record_identity、preparation_kind_code、actual_slide_identity、stable_material_identity、consumption_reference、preparation_version | InternalId 0..2；ControlledCode 1；ExternalIdentifier 1..2；VersionNumber 1 | 制备记录和类型必填；稳定制备物仅保存/复用时必填 | 细胞材料/高 | 细胞命令、查询、事件 | 不凭空创建稳定对象 | TBL-027、028、042、062 |
| P12-SCH-030 | 冰冻轮次 | frozen_business_identity、round_identity、material_identity、feedback_version、clinical_feedback_reference、frozen_report_reference、leftover_transfer_reference | InternalId 2；ExternalIdentifier 1..4；VersionNumber 0..1 | 业务、轮次、材料必填；报告/反馈按形成条件必填 | 冰冻/高 | 冰冻命令、事件、查询 | 轮次不替代标本身份 | TBL-008、025、026、045 |
| P12-SCH-031 | 对账 | reconciliation_identity、target_system_code、event_identity、delivery_attempt_identity、technical_acknowledgement、business_confirmation、external_version、difference_code、closure_evidence | InternalId 1；ControlledCode 2；ExternalIdentifier 2..3；LongText 0..1 | 目标、事件、投递、技术应答和业务确认分层 | 集成/中至高 | 出站、对账、查询 | 差异追加，不能改本地事实 | TBL-088、089 |
| P12-SCH-032 | 投递状态 | outbound_event_identity、target_system_code、delivery_attempt_identity、delivery_state_code、attempted_at、technical_acknowledgement、business_confirmation、retry_parameter_reference、replay_authorization | ExternalIdentifier 2；InternalId 1；ControlledCode 2；UtcInstant 1；Boolean 0..1 | 事件、目标、尝试和状态必填 | 集成/中 | 出站、文件、事件 | 技术状态不替代业务状态 | TBL-086、087、088 |

## 2. 事件载荷Schema目录（21）

事件载荷均复用`P12-SCH-021` Envelope、`P12-SCH-022`关联和`P12-SCH-023`审计上下文；以下Schema只声明事件事实字段，来源为对应P10事件和P11对象/表，兼容规则为新增可选字段、禁止重命名和禁止改变既有字段语义。

| Schema编号 | 对应P10事件 | 事件载荷字段 | 必填/敏感等级 | P12事件契约 |
|---|---|---|---|---|
| P12-SCH-033 | P10-EVT-001 申请原始事实已接收 | application_identity、external_application_identity、source_message_identity、request_channel_code、raw_message_reference | 全部必填/高 | P12-EVC-001 |
| P12-SCH-034 | P10-EVT-002 病例正式建立 | case_identity、application_identity、case_number、case_modality_code、case_version | 全部必填/高 | P12-EVC-002 |
| P12-SCH-035 | P10-EVT-003 标本接收与责任交出 | specimen_identity、handoff_identity、received_at、from_responsible_party、to_responsible_party | 全部必填/高 | P12-EVC-003 |
| P12-SCH-036 | P10-EVT-004 外部材料核验完成 | external_material_identity、verification_version、source_organization_identity、verification_state_code | 全部必填/高 | P12-EVC-004 |
| P12-SCH-037 | P10-EVT-005 实际材料形成 | material_identity、material_kind_code、source_reference、formed_at、formed_by_subject | 全部必填/高 | P12-EVC-005 |
| P12-SCH-038 | P10-EVT-006 冰冻反馈形成 | frozen_business_identity、round_identity、feedback_version、feedback_kind_code、clinical_feedback_reference | 前四项必填/高 | P12-EVC-006 |
| P12-SCH-039 | P10-EVT-007 细胞充分性确认 | specimen_identity、preparation_reference、adequacy_version、adequacy_state_code、assessor_subject | 全部必填/高 | P12-EVC-007 |
| P12-SCH-040 | P10-EVT-008 细胞筛查或复核完成 | screening_task_identity、review_identity、record_version、responsible_subject、completion_state_code | 全部必填/高 | P12-EVC-008 |
| P12-SCH-041 | P10-EVT-009 分子检测任务建立 | molecular_task_identity、case_or_parent_task_reference、material_reference、task_version、task_kind_code | 全部必填/高 | P12-EVC-009 |
| P12-SCH-042 | P10-EVT-010 分子运行和质控事实形成 | run_identity、batch_identity、quality_control_references、raw_result_reference、run_version | 全部必填/高 | P12-EVC-010 |
| P12-SCH-043 | P10-EVT-011 有效分子结果确认 | valid_result_identity、task_identity、quality_release_reference、result_version、validity_state_code | 全部必填/高 | P12-EVC-011 |
| P12-SCH-044 | P10-EVT-012 外送任务交出 | outbound_task_identity、material_handoff_identity、external_organization_identity、external_task_identity | 全部必填/高 | P12-EVC-012 |
| P12-SCH-045 | P10-EVT-013 外部结果已接收 | external_result_identity、external_report_version、external_file_reference、source_verification_state_code、received_at | 全部必填/高 | P12-EVC-013 |
| P12-SCH-046 | P10-EVT-014 诊断依据已确认 | diagnosis_record_identity、evidence_references、author_subject、record_version、confirmation_state_code | 全部必填/高 | P12-EVC-014 |
| P12-SCH-047 | P10-EVT-015 报告版本已签发 | report_version_identity、report_lifecycle_identity、report_version_number、signed_by_subject、signed_at | 全部必填/高 | P12-EVC-015 |
| P12-SCH-048 | P10-EVT-016 报告受控版本事件形成 | prior_report_version、new_report_version、report_event_kind_code、reason_text、responsible_subject | 全部必填/高 | P12-EVC-016 |
| P12-SCH-049 | P10-EVT-017 多模态诊断上下文固定 | context_identity、component_report_versions、molecular_evidence_reference、external_evidence_reference、context_version | 全部必填/高 | P12-EVC-017 |
| P12-SCH-050 | P10-EVT-018 数字材料可用性确认 | scan_task_identity、digital_slide_version、file_version_identity、quality_state_code、diagnostic_availability_code | 全部必填/高 | P12-EVC-018 |
| P12-SCH-051 | P10-EVT-019 出站投递请求已形成 | outbound_event_identity、target_system_code、event_version、delivery_attempt_identity、payload_reference | 全部必填/中高 | P12-EVC-019 |
| P12-SCH-052 | P10-EVT-020 外部业务对账完成 | reconciliation_identity、target_system_code、external_business_confirmation、external_version、difference_code | 全部必填/中高 | P12-EVC-020 |
| P12-SCH-053 | P10-EVT-021 治理事实已形成 | governance_identity、operation_kind_code、affected_subjects、authorization_reference、closure_state_code | 前四项必填/高 | P12-EVC-021 |

## 3. 字段通用兼容规则

公共Schema和事件载荷Schema的契约版本独立于业务对象版本、报告版本、文件版本和医院适配器版本。新增可选字段可以向前兼容；新增代码值要求消费者未知值安全保留或进入适配器隔离；必填字段、字段语义、身份含义和时间语义变化必须创建新Schema版本并登记`P12-COMP-XXX`。
