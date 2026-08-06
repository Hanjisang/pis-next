# P11 集成、治理、安全、归档与恢复数据字典

文档状态：已完成
数据库平台：待确认；列采用逻辑类型
覆盖表：P11-TBL-043–055、064–070、078–079、085–089，共25张

列按表号加两位序号分配。治理事实、状态历史、事件可靠性和对账均独立建模；审计、异常、质量、纠错、销毁和恢复不合并成万能日志。

## 1. 责任、治理、异常、质量和恢复

| 表/列范围 | 表中文名 | 完整正式列 | 关键约束与追溯 |
|---|---|---|---|
| P11-TBL-043 / P11-COL-4301–4312 | 操作责任事实 | id、business_object_id、business_object_kind_code、operation_kind_code、operator_person_ref、operator_service_ref、responsibility_kind_code、occurred_at、result_code、evidence_file_id、created_at、sensitivity_code | operator_person_ref和operator_service_ref至少一个且不得混淆；事实追加；D3；P05-INV-019–023/041–044 |
| P11-TBL-044 / P11-COL-4401–4413 | 交接事实 | id、source_object_id、source_object_kind_code、receiver_object_id、receiver_object_kind_code、handoff_kind_code、handoff_state_code、handed_over_by_ref、received_by_ref、handed_over_at、received_at、evidence_file_id、created_at | 交出和接收责任分别保存；签收前不得宣布责任已接收；P05-INV-009/019/023 |
| P11-TBL-047 / P11-COL-4701–4714 | 业务异常事实 | id、exception_no、exception_kind_code、severity_code、affected_object_id、affected_object_kind_code、triggered_at、blocking_scope_code、disposition_code、resolved_at、source_process_code、created_at、created_by_ref、sensitivity_code | 关联P07-EXC事实；局部异常只阻断声明范围；不作为所有对象统一状态；P05-INV-040 |
| P11-TBL-048 / P11-COL-4801–4814 | 质量事件 | id、quality_event_no、quality_kind_code、affected_object_id、affected_object_kind_code、quality_state_code、detected_at、investigator_ref、impact_assessment_text、corrective_action_text、closed_at、evidence_file_id、created_at、created_by_ref | 质量调查、影响和关闭独立保存；异常与质量事件分离；D2/D3；P05-INV-035/040/043/046 |
| P11-TBL-049 / P11-COL-4901–4915 | 受控纠错事实 | id、correction_no、target_object_id、target_object_kind_code、original_value_digest、proposed_value_digest、correction_reason_text、requested_by_ref、approved_by_ref、executed_by_ref、reviewed_by_ref、correction_state_code、executed_at、created_at、sensitivity_code | 原值和新值摘要、审批、执行、复核均不可变；不得覆盖历史；D2/D3；P05-INV-007/014/026/028/030 |
| P11-TBL-050 / P11-COL-5001–5013 | 授权代理事实 | id、authorization_no、principal_ref、delegate_ref、authorization_kind_code、scope_code、strong_authentication_ref、granted_at、effective_from、effective_until、revoked_at、authorization_state_code、created_at | delegate_ref不得成为actual_operator；effective_until不得早于effective_from；高风险操作需要强认证和第二人复核；P05-INV-041–043 |
| P11-TBL-051 / P11-COL-5101–5115 | 审计事件 | id、audit_sequence_no、actor_person_ref、actor_service_ref、action_kind_code、target_object_id、target_object_kind_code、request_correlation_ref、before_value_digest、after_value_digest、outcome_code、occurred_at、source_module_code、sensitivity_code、created_at | 只追加；不复制患者和诊断明文；actor身份和服务身份分离；D3；P05-INV-041–044 |
| P11-TBL-052 / P11-COL-5201–5214 | 档案销毁事实 | id、destruction_task_no、target_object_id、target_object_kind_code、freeze_check_state_code、retention_basis_code、approved_by_ref、executed_by_ref、witness_ref、destruction_state_code、executed_at、storage_delete_result_code、created_at、sensitivity_code | 冻结检查、审批、执行、见证和实际存储结果分离；不得级联删除事实；P05-INV-045–047 |
| P11-TBL-053 / P11-COL-5301–5314 | 恢复任务 | id、recovery_task_no、backup_reference、target_scope_code、recovery_kind_code、recovery_state_code、requested_by_ref、approved_by_ref、isolated_at、validated_at、opened_at、business_validation_state_code、created_at、sensitivity_code | 恢复任务不等于业务重新开放；恢复前必须隔离；P05-INV-047/048 |
| P11-TBL-054 / P11-COL-5401–5414 | 恢复校验事实 | id、recovery_task_id、validation_batch_no、validation_kind_code、object_identity_check_code、file_integrity_check_code、version_consistency_check_code、audit_consistency_check_code、difference_count、validation_state_code、validated_at、validated_by_ref、created_at、sensitivity_code | 业务、文件、版本、审计分别校验；差异隔离，不直接宣布生产有效；P05-INV-047/048 |
| P11-TBL-055 / P11-COL-5501–5514 | 外部材料核验 | id、external_material_reference_id、referral_id、external_result_version_id、verification_version_no、identity_check_code、source_check_code、completeness_check_code、integrity_check_code、verification_state_code、verified_at、verified_by_ref、failure_reason_text、created_at | 患者、材料、机构、方法和文件完整性分别核验；核验前不可作为本地医学依据；P05-INV-056/063/069 |

## 2. 状态和历史

| 表/列范围 | 表中文名 | 完整正式列 | 关键约束与追溯 |
|---|---|---|---|
| P11-TBL-064 / P11-COL-6401–6410 | 临床当前状态 | id、object_id、object_kind_code、state_machine_code、lifecycle_state_code、quality_state_code、availability_state_code、changed_at、concurrency_version、updated_by_ref | state_machine_code+object_id唯一；只保存有边界临床对象；历史同步写入P11-TBL-070；P08-SM-001/002/003/006/010/017/029 |
| P11-TBL-065 / P11-COL-6501–6510 | 材料当前状态 | id、object_id、object_kind_code、state_machine_code、material_state_code、quality_state_code、availability_state_code、changed_at、concurrency_version、updated_by_ref | 材料状态与质量/可用性正交；来源链变化不覆盖历史；P08-SM-004/005/009/012/013/014/024/026/027/031 |
| P11-TBL-066 / P11-COL-6601–6610 | 任务当前状态 | id、object_id、object_kind_code、state_machine_code、task_state_code、assignment_state_code、blocked_state_code、changed_at、concurrency_version、updated_by_ref | 任务、责任和医学事实分离；P08-SM-007/008/011/015/023/025 |
| P11-TBL-067 / P11-COL-6701–6710 | 报告当前状态 | id、object_id、object_kind_code、state_machine_code、report_lifecycle_state_code、report_effective_state_code、withdrawal_state_code、changed_at、concurrency_version、updated_by_ref | 报告生命周期和版本分离；签发后只能受控生成新版本；P08-SM-017/028 |
| P11-TBL-068 / P11-COL-6801–6810 | 集成当前状态 | id、object_id、object_kind_code、state_machine_code、delivery_state_code、reconciliation_state_code、blocked_state_code、changed_at、concurrency_version、updated_by_ref | 投递状态不覆盖业务状态；P08-SM-018 |
| P11-TBL-069 / P11-COL-6901–6910 | 治理当前状态 | id、object_id、object_kind_code、state_machine_code、governance_state_code、isolation_state_code、approval_state_code、changed_at、concurrency_version、updated_by_ref | 异常、质量、纠错、销毁和恢复各有对象边界；P08-SM-019–022/030 |
| P11-TBL-070 / P11-COL-7001–7015 | 状态转换历史 | id、transition_sequence_no、object_id、object_kind_code、state_machine_code、source_state_code、target_state_code、transition_event_code、operator_ref、responsibility_ref、occurred_at、reason_code、exception_id、authorization_id、correction_id | 每次转换追加；source和target必须属于对应P08状态机；禁止转换由事务守卫拒绝并留审计；P08全部31状态机、93转换、31禁止组 |

## 3. 关系、集成、发件箱、收件箱和对账

| 表/列范围 | 表中文名 | 完整正式列 | 关键约束与追溯 |
|---|---|---|---|
| P11-TBL-078 / P11-COL-7801–7812 | 材料来源关系 | id、target_material_id、target_material_kind_code、source_material_id、source_material_kind_code、relation_kind_code、derivation_id、source_path_digest、effective_at、ended_at、created_at、created_by_ref | target不得等于source；来源链必须可回溯；跨模块使用逻辑引用或受控外键；P05-INV-004/063 |
| P11-TBL-079 / P11-COL-7901–7912 | 授权范围关系 | id、authorization_id、scope_kind_code、scope_object_id、operation_kind_code、risk_level_code、second_reviewer_required_code、effective_from、effective_until、revoked_at、created_at、created_by_ref | 授权范围、对象和操作均结构化；到期或撤销后不得继续使用；P05-INV-041–043 |
| P11-TBL-085 / P11-COL-8501–8516 | 入站原始消息 | id、source_system_code、source_message_id、message_kind_code、received_at、external_occurred_at、payload_reference、payload_digest、parse_state_code、idempotency_result_code、business_processing_state_code、failure_reason_text、linked_object_id、linked_object_kind_code、sensitivity_code、created_at | source_system_code+source_message_id唯一；原始载荷或引用不可变；解析失败不删除原文；P10-EVT-001、P09-REQ-0001–0004 |
| P11-TBL-086 / P11-COL-8601–8615 | 事件发件箱 | id、event_id、event_version_no、source_aggregate_id、source_aggregate_kind_code、event_kind_code、event_payload_reference、event_payload_digest、published_state_code、available_at、published_at、blocked_at、retry_count、created_at、sensitivity_code | 业务事务内写入；event_id+version唯一；发布状态不改写业务事实；P10-EVT-001–021 |
| P11-TBL-087 / P11-COL-8701–8714 | 事件收件箱去重 | id、event_id、event_version_no、consumer_code、first_received_at、last_received_at、consumption_state_code、duplicate_received_count、processed_at、failure_reason_text、retry_allowed_code、closed_at、created_at、sensitivity_code | event_id+version+consumer_code唯一；重复接收只增加技术事实；P10-RULE/事务可靠性 |
| P11-TBL-088 / P11-COL-8801–8815 | 单次投递尝试 | id、outbox_event_id、delivery_attempt_no、target_system_code、delivery_state_code、request_message_reference、request_digest、sent_at、technical_response_reference、technical_response_code、business_ack_state_code、retry_reason_code、failed_at、created_at、sensitivity_code | 同一尝试不可更新为下一次；每次重试新建行；技术回执不等于业务确认；P05-INV-037–039 |
| P11-TBL-089 / P11-COL-8901–8916 | 对账差异 | id、reconciliation_batch_no、local_object_id、local_object_kind_code、external_system_code、external_fact_reference、difference_kind_code、local_fact_digest、external_fact_digest、difference_state_code、discovered_at、assigned_to_ref、compensation_action_text、closed_at、reopened_at、created_at | 本地和外部事实分别保存；差异可关闭和重新打开；不得用外部状态覆盖本地事实；P05-INV-037–040/048 |

## 4. 数据字典的共同属性

- `id`、业务对象身份、状态机代码、发生时间、记录时间、来源和责任列均有明确含义；不使用裸`status`、`type`、`code`、`data`、`result`或`flag`。
- 所有敏感列具有D1–D4逻辑分类；普通日志只记录引用、摘要和关联ID，不记录患者、诊断或原始载荷明文。
- 原始消息、事件载荷、设备载荷和外部报告使用`payload_reference`、`storage_object_reference`或摘要值；不把结构化文档当核心关系。
- 18项P09参数只影响平台映射、容量、保留、重试和恢复能力；没有任何虚构长度、数值、周期、RPO或RTO。
