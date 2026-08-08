# PIS-Next V2 P05 数据迁移设计基线

状态：已完成（设计封版）
版本：V2-0.2
日期：2026-08-08
输入：P00 当前系统审计、P01–P04；当前仓库 Flyway V1–V9 和 P15–P19 实现

## 1. 迁移范围与原则

本文件只设计历史数据识别、映射、校验、人工复核和回滚边界，不创建迁移脚本，不执行生产数据迁移。迁移对象是当前仓库已经存在的 P15–P19 数据结构和可证明的业务事实，不读取或参考仓库之外的旧 PIS。

迁移必须遵守：

1. 只迁移能够从原始记录、明确外键和不可变业务编号中唯一恢复的事实。
2. 规划记录不等于实际材料；没有可证明的实际 Block/Slide 不能猜测补齐。
3. 冲突、孤儿、重复和来源不完整必须进入 MigrationWarning 或 ManualReview，禁止静默修复。
4. 原始数据库只读保留；每轮迁移有 checkpoint、输入快照校验和输出计数。
5. V2 报告必须从可验证的诊断/签发事实形成不可变 Report 快照；旧版本字段不能直接等同于 V2 Report。
6. 迁移前后均按 Case、pathologyNo、Specimen、Block、Slide、Diagnosis、Report 做数量、关系和来源对账。
7. 未通过人工复核的记录不得进入有效医疗事实；可进入隔离区或迁移失败区等待处理。

## 2. 当前来源资产

P00 已审计的来源包括 pathology_case、specimen、specimen_container、grossing_batch、grossing_batch_specimen、grossing_record、tissue_block、tissue_sample、tissue_block_sample、p17_processing_task、p17_embedding_task、p17_actual_block_formation、p18_technical_order、p18_technical_order_project、p18_order_target、p18_planned_output、p19_diagnosis_task、p19_diagnosis_work_draft、p19_diagnosis_opinion、p19_diagnosis_opinion_version、p19_report、p19_report_content_version、p19_report_section_version、p19_signing_fact、p19_report_withdrawal_fact，以及当前审计、出站和幂等表。

源数据的“当前状态”列不能直接迁移成 V2 的巨型 Case 状态。必须重新计算 Case 展示投影，并分别建立材料、诊断和报告生命周期。

## 3. Migration Matrix

Decision 仅允许：KEEP、MAP、MERGE、DROP、REBUILD、MANUAL_REVIEW。Confidence 使用 H（可由明确来源唯一证明）、M（存在有限业务歧义）、L（无法证明，需要人工裁决）。

| Legacy Object | V2 Target | Decision | Confidence | Migration Rule | Manual Review |
|---|---|---|---|---|---|
| pathology_case | Case | MAP | H/M | 以原内部主键建立不可变 caseId；映射业务类型、来源系统、申请号和患者/就诊快照；重新计算 lifecycle | 缺少业务类型、重复主键或患者/就诊快照冲突 |
| clinical_state_current | Case 展示投影与各领域状态 | REBUILD | M | 不迁移为 CaseStatus；依据下级事实和状态历史重建投影 | 当前状态与下级事实矛盾 |
| state_transition_history | AuditEvent / 领域历史 | MAP | H | 保留原时间、操作者、来源和原状态文本；为无法对应 V2 命令的历史加 legacy 标识 | 时间顺序或操作人无法解析 |
| pathology_request | Application/Case 登记上下文 | MAP | H | 保留申请身份和原始业务项目；通过 ApplicationItemMapping 解析 businessTypeId | externalItemCode 未配置或同码多业务类型 |
| specimen | Specimen | MAP | H | 以 caseId + specimenCode 建立；保留来源、部位、名称和接收事实 | 标本无 Case、编码重复、外部标识冲突 |
| specimen_container | Specimen 来源/容器关系 | MERGE | M | 只有能够唯一指向 Specimen 的容器才合并为来源元数据；容器本身不提升为新 Specimen | 一个容器指向多个标本或缺少来源 |
| grossing_batch、grossing_record | Grossing、GrossingSpecimen | MAP | H/M | 批次和记录按实际取材记录映射；关联标本通过明确外键保留 | 只有计划无执行记录、跨病例关系不清 |
| tissue_block | Block | MAP | M | 仅当 blockCode、Case、来源标本/取材或外部来源均能唯一证明时迁移 | unknown actual Block、来源缺失、编号冲突 |
| tissue_sample、tissue_block_sample | Block 与 Specimen 关联 | MERGE | M | 只保留可验证的材料来源关系；不把 sample 自动当作新 Block | 多来源、循环关系或无法区分蜡块/组织片 |
| p17_processing_task | TechnicalRecord/历史技术事实 | MANUAL_REVIEW | L/M | Task 不是 V2 事实；有明确 performed/result/material 证据时映射为 TechnicalRecord，否则保留隔离历史 | 没有实际输入输出、仅计划状态或结果缺失 |
| p17_processing_batch、run、run_step | TechnicalRecord、批次成员与审计 | MAP | M | 有实际执行时间、设备/操作者和材料成员时映射；程序版本作为配置快照 | 批次成员不完整、重跑关系不明 |
| p17_embedding_task | Block 生产来源或 TechnicalRecord | MANUAL_REVIEW | L/M | 只有实际包埋形成和唯一 Block 关系才进入 Block 证据；单独 Task 不迁移为核心事实 | 仅有待执行/完成状态但无 Block 结果 |
| p17_actual_block_formation | Block 来源事实 / TechnicalRecord | MAP | M | 保留 actual block code、source task、材料输入和形成时间；冲突不得覆盖 | 一个实际编号对应多个来源或缺少输入 |
| p17_actual_block_replacement | Block 关系历史 / AuditEvent | MAP | M | 作为替换历史追加记录；新 Block 需单独建立，原 Block 保留失效关系 | 替换前后编号和原因不完整 |
| p18_technical_order | TechnicalOrder | MAP | H | 保留 orderNo、Case、申请人、原因、状态历史和版本信息 | 订单无 Case 或状态与项目矛盾 |
| p18_technical_order_project | TechnicalOrderItem / TechnicalProject | MERGE | M | 项目身份映射到配置；每个项目转换为 Item，保留原序号和参数快照 | 项目编码未配置或项目语义重复 |
| p18_order_target | TechnicalOrderTarget | MAP | M | 仅将可唯一解析的 Case/Specimen/Block/Slide 映射到固定目标列，拒绝通用 targetId | 目标不存在、跨病例或目标类型冲突 |
| p18_planned_output | TechnicalOrderItem 计划信息 | DROP | H | 作为规划/计划历史留在迁移报告或隔离区，不生成 Block/Slide | 如果同时有明确实际输出，人工确认实际记录来源后另行 MAP |
| p18_project_result_reference | TechnicalRecord / 外部结果引用 | MANUAL_REVIEW | M | 有实际结果、材料和来源时映射；仅引用不产生 GenericTechnicalResult | 引用目标、结果提供方或内容不完整 |
| p19_diagnosis_task | Diagnosis 工作上下文 | MANUAL_REVIEW | M/L | 任务身份和责任流可进入历史；只有明确诊断事实时才创建 Diagnosis | Diagnosis 无法与 Case、医生或签发事实唯一关联 |
| p19_diagnosis_work_draft | Diagnosis 草稿 | MAP | M | 未签发草稿可进入隔离/历史草稿；不能冒充有效诊断 | 草稿与已签发内容冲突或作者不明 |
| p19_diagnosis_opinion、opinion_version | Diagnosis 结构化/文本快照 | MAP | M | 按意见身份、作者、时间和版本建立 Diagnosis 编辑历史；不覆盖历史内容 | 多意见合并规则不清或关键字段缺失 |
| p19_report | Report | MAP | M | 每个已签发事实建立一个不可变 Report；撤回状态保留撤回元数据 | Case/Diagnosis 断链、多个签发事实冲突 |
| p19_report_content_version、section_version | Report template/data/rendered snapshot | MERGE | M | 仅把最终可验证签发版本合并到 Report 快照；其他版本保留历史证据 | current_version_id 指向失效版本或快照不完整 |
| p19_signing_fact | Report signer/signedAt/status | MAP | H/M | 签发人、时间、签发命令和结果校验迁移；失败签发不生成有效 Report | 签发人缺失、时间倒序或签发结果不一致 |
| p19_report_withdrawal_fact、correction、supplement | Report 撤回/关系历史 | MAP | H/M | 原 Report 保留；撤回、更正、补充和重新签发建立追加关系与审计 | 原因、责任人、目标报告或时间不完整 |
| p19_report_result_reference | Report file/reference | MANUAL_REVIEW | M | 只迁移能校验完整性和归属的文件引用；同时保存快照校验信息 | 文件缺失、哈希缺失或 Case 归属不明 |
| p19_state_history、p19_command_idempotency | AuditEvent、IdempotencyRecord | KEEP | H | 作为操作证据和幂等证据保留，按新实体映射 entityType/entityId | 旧记录不可解析或重复键冲突 |

## 4. 迁移警告和人工复核

定义 MigrationWarning 逻辑实体，至少包含 id、runId、warningCode、severity、legacyObject、legacyId、caseId、pathologyNo、targetType、targetId、evidenceSnapshot、message、status、assignedTo、resolvedAt、resolution、createdAt。severity 取 P0、P1、P2；status 至少取 OPEN、IN_REVIEW、RESOLVED、WAIVED、BLOCKED。

必须产生以下标准 warningCode：

- UNKNOWN_ACTUAL_BLOCK：无法证明来源的实际 Block；
- MISSING_SLIDE_SOURCE：Slide 缺少 Block、Specimen 或外部来源证明；
- PATHOLOGY_NO_CONFLICT：有效病理号冲突或同号多 Case；
- DUPLICATE_CASE：可能对应同一业务事实的重复 Case；
- UNLINKED_DIAGNOSIS：Diagnosis 无法唯一关联 Case；
- INCOMPLETE_REPORT_SNAPSHOT：Report 的签发快照、模板、文件引用或签字证据不完整；
- ORPHAN_SOURCE：存在孤儿的源记录；
- AMBIGUOUS_MAPPING：多个 V2 目标均可能匹配；
- FAILED_MIGRATION：迁移写入或校验失败。

处理规则：

1. P0/P1 warning 未解决时，不得把相关记录标为有效 V2 医疗事实。
2. P2 可由授权业务人员豁免，但必须填写原因和证据，不能删除 warning。
3. 所有自动映射要保存 evidenceSnapshot 和 mappingRuleVersion，支持复盘。
4. 不允许以“取第一条”“取最新一条”“按最大号补齐”等方式猜测。

## 5. 分阶段执行设计

| 阶段 | 输入与动作 | 输出和门禁 |
|---|---|---|
| M0 只读盘点 | 固化源库版本、Flyway 版本、表计数、约束、索引和哈希；停止写入或使用一致性快照 | SourceManifest、基线计数、SchemaHash |
| M1 身份映射 | 建立 Case、申请、外部标识和病理号候选映射；不生成下游事实 | CaseMapping、PathologyNumberConflict、DuplicateCase warning |
| M2 材料映射 | 按明确关系处理 Specimen、Grossing、Block、Slide、DigitalSlide | MaterialMapping、来源完整性报告、人工复核清单 |
| M3 技术和诊断映射 | 处理 TechnicalOrder、实际 TechnicalRecord、Diagnosis、Responsibility 和 Report | Diagnosis/Report mapping、快照校验、断链报告 |
| M4 隔离导入 | 先写入迁移隔离区和 warning，再由批准的批次写入 V2 领域表 | 可重放批次、checkpoint、失败记录 |
| M5 对账与验收 | 执行数量、关系、唯一性、报告不可变和权限审计校验 | ReconciliationReport、签字记录、是否允许切换的裁决 |

每个阶段都可从 checkpoint 重新开始；失败批次不能覆盖已成功批次，修复必须新增 mapping rule/version。

## 6. 对账和验收指标

每次迁移运行必须输出以下至少一行汇总及逐条明细：

| 指标 | 口径 |
|---|---|
| Case count | 源 Case、成功映射、隔离、失败、未处理 |
| pathologyNo count | 源有效病理号、V2 有效病理号、冲突、缺失 |
| Specimen count | 源、成功、孤儿、重复、人工复核 |
| Block count | 可证明实际 Block、未知实际 Block、编号冲突、未迁移计划记录 |
| Slide count | 可证明来源 Slide、缺来源 Slide、外部 Slide、重复 |
| Diagnosis count | 有效诊断、草稿、未关联、重复、人工复核 |
| Report count | 可重建签发 Report、撤回、快照不完整、文件引用失败 |
| Warning count | P0/P1/P2、OPEN、RESOLVED、WAIVED、BLOCKED |
| Failed count | 写入失败、校验失败、外部依赖失败和可重试数量 |

关系对账必须验证 Case→Specimen、Specimen→Block、Block/Specimen→Slide、Slide→DigitalSlide、Case→Diagnosis、Diagnosis→Report 的外键完整性和反向计数。任一核心关系不平衡时，批次状态为 BLOCKED。

## 7. 回滚与数据安全

迁移只能写入隔离区和 V2 新表；原始表只读。每批保存 runId、checkpoint、输入快照哈希、输出数量和事务日志。回滚优先通过废弃未验收批次、恢复读取入口和保留原始源数据实现，不删除原始数据。已经验收的 V2 事实不得用物理删除回滚，必须产生撤销/失效和审计。

## 8. P05 封版结论

P05 已把当前 P15–P19 主要对象逐项映射到 V2 目标，明确了 KEEP、MAP、MERGE、DROP、REBUILD、MANUAL_REVIEW 的处置规则、证据要求、warning、人工复核、对账和回滚边界。迁移仍未开始；下一步只有在 P09 切换门禁和业务批准后，才可按 M0–M5 编写迁移脚本和演练。
