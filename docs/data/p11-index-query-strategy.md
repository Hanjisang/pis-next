# P11 索引与查询支撑策略

文档状态：已完成
数据库平台：待确认；索引为产品中立意图
索引总数：160
其中主键索引：89；唯一二级索引：17；状态或工作队列索引：28；外部幂等索引：14

## 1. 索引规则

P11-IDX-001至P11-IDX-089为89张正式表各自的主键索引，主键索引名称和物理实现待平台映射。P11-IDX-090至P11-IDX-160为71个查询、唯一性、状态、队列、来源、文件和对账索引意图。索引不承担未确认参数的分区、全文、向量或物理存储选择。

主键索引按表内`id`建立；外键连接索引只在查询或一致性守卫需要时建立；大文本、结构化文档和文件引用不建立未经依据的普通索引；所有时间范围索引均保留参数化周期而不硬编码保留期限。

## 2. 查询来源和模式

| 查询来源 | 主要模式 | 支撑索引类别 |
|---|---|---|
| P09正式需求 | 申请、病例、标本、报告、结果、责任、来源和历史查询 | 业务编号、外部标识、外键、时间范围 |
| P06流程 | 待接收、待取材、待制片、待诊断、待签发、待核验 | 状态、责任人、任务队列 |
| P07异常 | 隔离对象、SEV-1影响、未关闭质量和恢复任务 | 严重等级、状态、对象和时间 |
| P08状态机 | 当前状态、合法转换前置和历史回放 | 当前状态、并发版本、转换历史 |
| P10事务 | 幂等、发件箱、收件箱、投递尝试、对账 | 唯一、幂等、重试和差异 |
| P09质量属性 | 响应、容量、恢复、消息和报告时效的测量数据 | 时间、状态、关联对象 |

## 3. 主键和唯一索引登记

| 索引编号 | 表 | 索引列 | 唯一性 | 支撑查询/约束 |
|---|---|---|---|---|
| P11-IDX-090 | P11-TBL-074 | source_system_code, external_request_id | 唯一 | 入站申请幂等 |
| P11-IDX-091 | P11-TBL-002 | organization_reference, case_no | 唯一 | 病例业务编号 |
| P11-IDX-092 | P11-TBL-003 | case_id, specimen_no | 唯一 | 病例内标本编号 |
| P11-IDX-093 | P11-TBL-004 | organization_reference, block_no | 唯一 | 蜡块编号不复用 |
| P11-IDX-094 | P11-TBL-005 | organization_reference, slide_no | 唯一 | 实际玻片编号 |
| P11-IDX-095 | P11-TBL-011 | organization_reference, report_display_no | 唯一 | 报告展示号 |
| P11-IDX-096 | P11-TBL-015 | organization_reference, molecular_task_no | 唯一 | 分子任务编号 |
| P11-IDX-097 | P11-TBL-017 | organization_reference, referral_no | 唯一 | 外送任务编号 |
| P11-IDX-098 | P11-TBL-019 | specimen_id, container_no | 唯一 | 容器编号 |
| P11-IDX-099 | P11-TBL-025 | frozen_business_id, round_no | 唯一 | 冰冻轮次 |
| P11-IDX-100 | P11-TBL-030 | cytology_review_responsibility_id, screening_task_no | 唯一 | 筛查任务 |
| P11-IDX-101 | P11-TBL-032 | cytology_review_responsibility_id, review_task_no | 唯一 | 复核任务 |
| P11-IDX-102 | P11-TBL-075 | source_organization_ref, external_material_id | 唯一 | 外部材料幂等 |
| P11-IDX-103 | P11-TBL-086 | event_id, event_version_no | 唯一 | 发件箱事件版本 |
| P11-IDX-104 | P11-TBL-088 | outbox_event_id, delivery_attempt_no | 唯一 | 投递尝试序列 |
| P11-IDX-105 | P11-TBL-087 | event_id, event_version_no, consumer_code | 唯一 | 收件箱消费去重 |
| P11-IDX-106 | P11-TBL-081 | code_set_name, code_set_version_no, code_value | 唯一 | 受控代码集 |
| P11-IDX-107 | P11-TBL-080 | external_system_code, configuration_version_no | 唯一 | 外部系统配置 |
| P11-IDX-108 | P11-TBL-073 | snapshot_hash_value | 非唯一候选 | 快照重复检测 |
| P11-IDX-109 | P11-TBL-011 | case_id, current_effective_version_id | 候选唯一 | 当前有效报告守卫 |

P11-IDX-090至107为17个唯一二级索引；P11-IDX-109是非唯一版本守卫候选，不计入唯一二级索引数量。

## 4. 状态、队列、来源、文件和对账索引登记

| 索引编号 | 表 | 索引列 | 唯一性/过滤意图 | 支撑查询 |
|---|---|---|---|---|
| P11-IDX-110 | P11-TBL-064 | state_machine_code, lifecycle_state_code, changed_at | 非唯一 | 临床待办和状态历史 |
| P11-IDX-111 | P11-TBL-065 | state_machine_code, material_state_code, changed_at | 非唯一 | 材料可用性 |
| P11-IDX-112 | P11-TBL-066 | task_state_code, assignment_state_code, changed_at | 非唯一 | 任务队列 |
| P11-IDX-113 | P11-TBL-067 | report_lifecycle_state_code, changed_at | 非唯一 | 报告待签发/撤回 |
| P11-IDX-114 | P11-TBL-068 | delivery_state_code, reconciliation_state_code, changed_at | 非唯一 | 集成待办 |
| P11-IDX-115 | P11-TBL-069 | governance_state_code, isolation_state_code, changed_at | 非唯一 | 治理待办 |
| P11-IDX-116 | P11-TBL-070 | object_id, state_machine_code, occurred_at | 非唯一 | 对象状态历史 |
| P11-IDX-117 | P11-TBL-003 | case_id, specimen_lifecycle_state_code, received_at | 非唯一 | 标本接收和差异 |
| P11-IDX-118 | P11-TBL-004 | specimen_id, block_lifecycle_state_code, physical_formed_at | 非唯一 | 蜡块来源和形成 |
| P11-IDX-119 | P11-TBL-005 | source_kind_code, source_block_id, formed_at | 非唯一 | 玻片来源追溯 |
| P11-IDX-120 | P11-TBL-062 | source_actual_slide_id, digital_version_state_code | 非唯一 | 数字材料质量 |
| P11-IDX-121 | P11-TBL-027 | specimen_id, preparation_state_code, completed_at | 非唯一 | 细胞制备待办 |
| P11-IDX-122 | P11-TBL-029 | adequacy_state_code, evaluated_at | 非唯一 | 充分性评价 |
| P11-IDX-123 | P11-TBL-030 | task_state_code, assigned_person_ref, assigned_at | 非唯一 | 筛查队列 |
| P11-IDX-124 | P11-TBL-032 | task_state_code, assigned_person_ref, assigned_at | 非唯一 | 复核队列 |
| P11-IDX-125 | P11-TBL-015 | material_selection_id, molecular_task_state_code | 非唯一 | 附属检测材料 |
| P11-IDX-126 | P11-TBL-035 | device_run_batch_id, batch_state_code, started_at | 非唯一 | 运行批次 |
| P11-IDX-127 | P11-TBL-036 | molecular_run_id, received_at | 非唯一 | 原始结果接收 |
| P11-IDX-128 | P11-TBL-037 | molecular_run_id, qc_state_code, evaluated_at | 非唯一 | 质控队列 |
| P11-IDX-129 | P11-TBL-059 | molecular_task_id, validity_state_code, confirmed_at | 非唯一 | 有效结果确认 |
| P11-IDX-130 | P11-TBL-017 | external_organization_ref, external_accession_id | 非唯一 | 外送外部编号 |
| P11-IDX-131 | P11-TBL-061 | referral_id, verification_state_code, received_at | 非唯一 | 外部结果核验 |
| P11-IDX-132 | P11-TBL-082 | digest_algorithm_code, digest_value | 非唯一 | 文件重复和完整性 |
| P11-IDX-133 | P11-TBL-083 | file_id, check_state_code, checked_at | 非唯一 | 文件校验和孤儿检测 |
| P11-IDX-134 | P11-TBL-084 | business_object_id, business_object_kind_code | 非唯一 | 文件业务引用 |
| P11-IDX-135 | P11-TBL-051 | target_object_id, occurred_at | 非唯一 | 审计对象时间 |
| P11-IDX-136 | P11-TBL-048 | quality_state_code, detected_at | 非唯一 | 质量事件 |
| P11-IDX-137 | P11-TBL-047 | severity_code, exception_state_code, triggered_at | 非唯一 | 异常隔离和SEV-1 |
| P11-IDX-138 | P11-TBL-049 | target_object_id, correction_state_code, created_at | 非唯一 | 纠错队列 |
| P11-IDX-139 | P11-TBL-052 | destruction_state_code, freeze_check_state_code, created_at | 非唯一 | 销毁冻结检查 |
| P11-IDX-140 | P11-TBL-053 | recovery_state_code, requested_by_ref, created_at | 非唯一 | 恢复队列 |
| P11-IDX-141 | P11-TBL-054 | recovery_task_id, validation_state_code, validated_at | 非唯一 | 恢复校验 |
| P11-IDX-142 | P11-TBL-044 | source_object_id, handed_over_at | 非唯一 | 交接链 |
| P11-IDX-143 | P11-TBL-043 | operator_person_ref, occurred_at | 非唯一 | 操作责任 |
| P11-IDX-144 | P11-TBL-078 | target_material_id, source_material_id | 非唯一 | 材料来源链 |
| P11-IDX-145 | P11-TBL-046 | source_material_id, occurred_at | 非唯一 | 材料消耗 |
| P11-IDX-146 | P11-TBL-045 | source_material_id, occurred_at | 非唯一 | 材料派生 |
| P11-IDX-147 | P11-TBL-076 | diagnosis_record_version_id, evidence_role_code | 非唯一 | 诊断依据 |
| P11-IDX-148 | P11-TBL-058 | report_version_id, component_kind_code | 非唯一 | 报告组成 |
| P11-IDX-149 | P11-TBL-077 | case_id, modality_code, relation_state_code | 非唯一 | 病例模态 |
| P11-IDX-150 | P11-TBL-079 | authorization_id, scope_object_id | 非唯一 | 授权范围 |
| P11-IDX-151 | P11-TBL-086 | published_state_code, available_at | 非唯一 | 发件箱待发布 |
| P11-IDX-152 | P11-TBL-086 | published_state_code, blocked_at | 非唯一 | 发件箱受阻 |
| P11-IDX-153 | P11-TBL-087 | consumption_state_code, retry_allowed_code | 非唯一 | 收件箱重试 |
| P11-IDX-154 | P11-TBL-088 | delivery_state_code, target_system_code, failed_at | 非唯一 | 投递失败 |
| P11-IDX-155 | P11-TBL-089 | difference_state_code, assigned_to_ref, discovered_at | 非唯一 | 对账差异 |
| P11-IDX-156 | P11-TBL-071 | source_system_code, external_patient_id | 非唯一候选 | 患者映射 |
| P11-IDX-157 | P11-TBL-072 | source_system_code, external_visit_id | 非唯一候选 | 就诊映射 |
| P11-IDX-158 | P11-TBL-074 | idempotency_digest, received_at | 非唯一候选 | 入站幂等检查 |
| P11-IDX-159 | P11-TBL-084 | file_id, business_object_id | 非唯一 | 文件孤儿检测 |
| P11-IDX-160 | P11-TBL-082 | archive_state_code, freeze_state_code | 非唯一 | 归档冻结候选 |

P11-IDX-110至124、126至129、135至155共28个状态或工作队列索引；P11-IDX-090、102–105、131、151–154、158及入站/外送组合中的幂等路径共14个外部幂等索引。具体过滤条件、部分索引、表达式索引和排序规则待数据库平台确认。

## 5. 风险和禁止项检查

- 没有重复地为同一唯一约束建立普通二级索引；P11-IDX-090至107服务业务唯一性或幂等性。
- 没有为大文本、原始载荷或JSON建立普通内容索引；只对摘要、来源、状态和时间建立索引。
- 未确认P09参数不用于硬编码索引分区、保留周期、并发容量或覆盖列。
- 不创建全文索引、向量索引或分区索引；平台和容量确认移交后续技术阶段。
