# P11 全病理关系模式

文档状态：已完成
数据库平台：待确认；以下为产品中立关系模式，不是可执行DDL
正式表：89
正式逻辑实体：89
正式关系：96
正式列：1,164
主责任模块：15/15

## 1. 关系模式原则

每张表只有一个P10主责任模块。聚合根表负责本聚合的写入事实；跨模块表间关系按P10事务策略使用数据库外键、受控逻辑引用、不可变快照或事件投影。所有临床事实默认禁止级联删除。表的逻辑类型、列名和约束能力待数据库平台确认后再做产品映射。

每张表均具有内部稳定主键；业务编号、外部标识和版本号均为独立列。可变聚合根具有ConcurrencyVersion；不可变事实和版本以追加方式形成，不通过更新旧行表达新事实。

## 2. 表分类统计

| 表类别 | 数量 | 表编号范围 | 说明 |
|---|---:|---|---|
| 聚合根表 | 18 | P11-TBL-001–018 | 对应18个正式聚合的根事实 |
| 聚合内部子实体表 | 24 | P11-TBL-019–042 | 有独立局部身份或执行责任的子实体 |
| 不可变事实表 | 13 | P11-TBL-043–055 | 责任、交接、异常、治理和核验事实 |
| 不可变版本表 | 8 | P11-TBL-056–063 | 诊断、报告、结果、数字材料和文件版本 |
| 状态或历史表 | 7 | P11-TBL-064–070 | 六类有边界当前状态加一张转换历史 |
| 快照和外部引用表 | 5 | P11-TBL-071–075 | 患者/就诊上下文、外部标识和外部材料 |
| 关系表 | 4 | P11-TBL-076–079 | 诊断依据、模态、来源和授权范围 |
| 配置表 | 2 | P11-TBL-080–081 | 外部目标和受控代码集 |
| 文件元数据表 | 3 | P11-TBL-082–084 | 文件、完整性和业务引用 |
| 集成、事件和对账表 | 5 | P11-TBL-085–089 | 入站原文、发件箱、收件箱、投递、对账 |
| **合计** | **89** | **P11-TBL-001–089** | **不使用万能表** |

逻辑约束登记：PRIMARY KEY 89、FOREIGN KEY 154、UNIQUE 71、CHECK 126、NOT NULL 438，共878项；索引登记160项，其中89个主键索引、17个唯一二级索引、28个状态/工作队列索引和14个外部幂等索引。所有数量均为逻辑设计项，不代表已选择具体数据库产品。

## 3. 正式表目录和数据归属

| 表 | 逻辑表名 | 中文名称 | 责任模块 | 聚合/边界 | 类别 | 根/追加 | 当前状态 | 并发版本 |
|---|---|---|---|---|---|---|---|---|
| P11-TBL-001 | pathology_request | 病理申请 | P10-MOD-001 | P05-AGG-001 | 聚合根 | 根/可变 | clinical_state_current | 是 |
| P11-TBL-002 | pathology_case | 病理病例 | P10-MOD-001 | P05-AGG-002 | 聚合根 | 根/可变 | clinical_state_current | 是 |
| P11-TBL-003 | specimen | 标本 | P10-MOD-002 | P05-AGG-003 | 聚合根 | 根/可变 | clinical_state_current | 是 |
| P11-TBL-004 | tissue_block | 蜡块业务记录 | P10-MOD-003 | P05-AGG-004 | 聚合根 | 根/可变 | material_state_current | 是 |
| P11-TBL-005 | actual_slide | 实际玻片 | P10-MOD-003 | P05-AGG-005 | 聚合根 | 根/可变 | material_state_current | 是 |
| P11-TBL-006 | digital_material | 数字材料聚合 | P10-MOD-010 | P05-AGG-006 | 聚合根 | 根/可变 | material_state_current | 是 |
| P11-TBL-007 | technical_order | 技术医嘱 | P10-MOD-003 | P05-AGG-007 | 聚合根 | 根/可变 | task_state_current | 是 |
| P11-TBL-008 | frozen_business | 术中冰冻业务 | P10-MOD-004 | P05-AGG-008 | 聚合根 | 根/可变 | clinical_state_current | 是 |
| P11-TBL-009 | diagnosis_responsibility | 诊断责任 | P10-MOD-008 | P05-AGG-009 | 聚合根 | 根/可变 | task_state_current | 是 |
| P11-TBL-010 | diagnosis_record | 诊断记录 | P10-MOD-008 | P05-AGG-010 | 聚合根 | 根/可变 | clinical_state_current | 是 |
| P11-TBL-011 | report_lifecycle | 报告生命周期 | P10-MOD-008 | P05-AGG-011 | 聚合根 | 根/可变 | report_state_current | 是 |
| P11-TBL-012 | outbound_business_event | 出站业务事件边界 | P10-MOD-011 | P05-AGG-012 | 聚合根 | 根/追加 | integration_state_current | 否 |
| P11-TBL-013 | cytology_material | 细胞学制备与材料 | P10-MOD-005 | P05-AGG-013 | 聚合根 | 根/可变 | material_state_current | 是 |
| P11-TBL-014 | cytology_review_responsibility | 细胞筛查复核责任 | P10-MOD-005 | P05-AGG-014 | 聚合根 | 根/可变 | task_state_current | 是 |
| P11-TBL-015 | molecular_task_run | 分子检测任务与运行 | P10-MOD-006 | P05-AGG-015 | 聚合根 | 根/可变 | task_state_current | 是 |
| P11-TBL-016 | molecular_material_analyte | 分子材料与分析物 | P10-MOD-006 | P05-AGG-016 | 聚合根 | 根/可变 | material_state_current | 是 |
| P11-TBL-017 | referral_external_result | 外送检测与外部结果 | P10-MOD-007 | P05-AGG-017 | 聚合根 | 根/可变 | integration_state_current | 是 |
| P11-TBL-018 | multimodal_diagnosis_relation | 多模态诊断关联 | P10-MOD-009 | P05-AGG-018 | 聚合根 | 根/可变 | report_state_current | 是 |
| P11-TBL-019 | specimen_container | 标本容器 | P10-MOD-002 | P05-AGG-003 | 子实体 | 局部/可变 | material_state_current | 是 |
| P11-TBL-020 | tissue_box_identity | 组织盒身份 | P10-MOD-003 | P05-AGG-004 | 子实体 | 局部/可变 | material_state_current | 是 |
| P11-TBL-021 | block_processing_batch | 组织处理批次 | P10-MOD-003 | P05-AGG-004/007 | 子实体 | 局部/可变 | task_state_current | 是 |
| P11-TBL-022 | technical_execution | 技术执行记录 | P10-MOD-003 | P05-AGG-007 | 子实体 | 局部/追加 | task_state_current | 否 |
| P11-TBL-023 | planned_slide | 计划玻片 | P10-MOD-003 | P05-AGG-005/007 | 子实体 | 局部/可变 | task_state_current | 是 |
| P11-TBL-024 | scan_task | 扫描任务 | P10-MOD-010 | P05-AGG-006 | 子实体 | 局部/可变 | task_state_current | 是 |
| P11-TBL-025 | frozen_round | 冰冻轮次 | P10-MOD-004 | P05-AGG-008 | 子实体 | 局部/可变 | clinical_state_current | 是 |
| P11-TBL-026 | frozen_round_material | 冰冻轮次材料 | P10-MOD-004 | P05-AGG-008 | 子实体 | 局部/可变 | material_state_current | 是 |
| P11-TBL-027 | cytology_preparation_record | 细胞制备记录 | P10-MOD-005 | P05-AGG-013 | 子实体 | 局部/追加 | task_state_current | 否 |
| P11-TBL-028 | cytology_preparation_material | 细胞学制备物 | P10-MOD-005 | P05-AGG-013/016 | 子实体 | 条件/可变 | material_state_current | 是 |
| P11-TBL-029 | adequacy_evaluation | 标本充分性评价 | P10-MOD-005 | P05-AGG-013/014 | 子实体 | 评价/追加 | clinical_state_current | 否 |
| P11-TBL-030 | screening_task | 细胞学筛查任务 | P10-MOD-005 | P05-AGG-014 | 子实体 | 任务/可变 | task_state_current | 是 |
| P11-TBL-031 | screening_record | 细胞学筛查记录 | P10-MOD-005 | P05-AGG-014 | 子实体 | 记录/追加 | clinical_state_current | 否 |
| P11-TBL-032 | review_task | 细胞学复核任务 | P10-MOD-005 | P05-AGG-014 | 子实体 | 任务/可变 | task_state_current | 是 |
| P11-TBL-033 | review_record | 细胞学复核记录 | P10-MOD-005 | P05-AGG-014 | 子实体 | 记录/追加 | clinical_state_current | 否 |
| P11-TBL-034 | device_task | 设备任务 | P10-MOD-003 | P05-AGG-007/006 | 子实体 | 任务/可变 | task_state_current | 是 |
| P11-TBL-035 | device_run_batch | 设备运行批次 | P10-MOD-003/006 | P05-AGG-007/015 | 子实体 | 批次/可变 | task_state_current | 是 |
| P11-TBL-036 | molecular_raw_result | 分子设备原始结果 | P10-MOD-006 | P05-AGG-015 | 子实体 | 原始/追加 | task_state_current | 否 |
| P11-TBL-037 | molecular_qc_result | 分子质控结果 | P10-MOD-006 | P05-AGG-015 | 子实体 | 质控/追加 | task_state_current | 否 |
| P11-TBL-038 | molecular_material_selection | 检测材料选择 | P10-MOD-006 | P05-AGG-016 | 子实体 | 选择/追加 | task_state_current | 否 |
| P11-TBL-039 | extract_or_analyte | 提取物或分析物 | P10-MOD-006 | P05-AGG-016 | 子实体 | 材料/可变 | material_state_current | 是 |
| P11-TBL-040 | report_template | 报告模板版本 | P10-MOD-015 | P05-AGG-011关联 | 配置/版本 | 版本/追加 | report_state_current | 否 |
| P11-TBL-041 | case_modality_participation | 病例模态参与项 | P10-MOD-001/009 | P05-AGG-002/018 | 关系子实体 | 关系/追加 | clinical_state_current | 否 |
| P11-TBL-042 | technical_target | 技术目标 | P10-MOD-003 | P05-AGG-007 | 子实体 | 目标/可变 | task_state_current | 是 |
| P11-TBL-043 | operation_responsibility | 操作责任事实 | P10-MOD-013 | 治理边界 | 不可变事实 | 追加 | 无 | 否 |
| P11-TBL-044 | handoff_record | 交接事实 | P10-MOD-013 | 关联聚合 | 不可变事实 | 追加 | 无 | 否 |
| P11-TBL-045 | material_derivation | 材料派生事实 | P10-MOD-006 | P05-AGG-016 | 不可变事实 | 追加 | 无 | 否 |
| P11-TBL-046 | material_consumption | 材料消耗事实 | P10-MOD-006 | P05-AGG-016 | 不可变事实 | 追加 | 无 | 否 |
| P11-TBL-047 | business_exception | 业务异常事实 | P10-MOD-012 | 治理边界 | 不可变事实 | 追加 | governance_state_current | 否 |
| P11-TBL-048 | quality_event | 质量事件 | P10-MOD-012 | 治理边界 | 不可变事实 | 追加 | governance_state_current | 否 |
| P11-TBL-049 | controlled_correction | 受控纠错事实 | P10-MOD-012 | 治理边界 | 不可变事实 | 追加 | governance_state_current | 否 |
| P11-TBL-050 | authorization_delegation | 授权代理事实 | P10-MOD-013 | 治理边界 | 不可变事实 | 追加 | governance_state_current | 否 |
| P11-TBL-051 | audit_event | 审计事件 | P10-MOD-013 | 治理边界 | 不可变事实 | 追加 | 无 | 否 |
| P11-TBL-052 | archive_destruction_record | 档案销毁事实 | P10-MOD-014 | 治理边界 | 不可变事实 | 追加 | governance_state_current | 否 |
| P11-TBL-053 | recovery_task | 恢复任务 | P10-MOD-014 | 治理边界 | 不可变事实 | 追加 | governance_state_current | 否 |
| P11-TBL-054 | recovery_validation | 恢复校验事实 | P10-MOD-014 | 治理边界 | 不可变事实 | 追加 | governance_state_current | 否 |
| P11-TBL-055 | external_material_verification | 外部材料核验 | P10-MOD-007 | P05-AGG-017 | 不可变事实 | 追加 | governance_state_current | 否 |
| P11-TBL-056 | diagnosis_record_version | 诊断记录版本 | P10-MOD-008 | P05-AGG-010 | 不可变版本 | 追加 | 无 | 否 |
| P11-TBL-057 | report_version | 报告版本 | P10-MOD-008 | P05-AGG-011 | 不可变版本 | 追加 | report_state_current | 否 |
| P11-TBL-058 | report_component_reference | 报告组成引用 | P10-MOD-008/009 | P05-AGG-011/018 | 不可变版本 | 追加 | 无 | 否 |
| P11-TBL-059 | valid_molecular_result_version | 有效分子结果版本 | P10-MOD-006 | P05-AGG-015 | 不可变版本 | 追加 | clinical_state_current | 否 |
| P11-TBL-060 | molecular_interpretation_version | 分子医学判读版本 | P10-MOD-006 | P05-AGG-015 | 不可变版本 | 追加 | clinical_state_current | 否 |
| P11-TBL-061 | external_result_version | 外部结果版本 | P10-MOD-007 | P05-AGG-017 | 不可变版本 | 追加 | integration_state_current | 否 |
| P11-TBL-062 | digital_material_version | 数字材料版本 | P10-MOD-010 | P05-AGG-006 | 不可变版本 | 追加 | material_state_current | 否 |
| P11-TBL-063 | pdf_file_version | PDF文件版本 | P10-MOD-015 | P05-AGG-011 | 不可变版本 | 追加 | 无 | 否 |
| P11-TBL-064 | clinical_state_current | 临床当前状态 | 各拥有模块 | 31个状态机分类 | 状态表 | 可变 | 是 | 是 |
| P11-TBL-065 | material_state_current | 材料当前状态 | P10-MOD-002/003/005/006/007 | 材料状态机 | 状态表 | 可变 | 是 | 是 |
| P11-TBL-066 | task_state_current | 任务当前状态 | 各拥有模块 | 任务状态机 | 状态表 | 可变 | 是 | 是 |
| P11-TBL-067 | report_state_current | 报告当前状态 | P10-MOD-008 | 报告状态机 | 状态表 | 可变 | 是 | 是 |
| P11-TBL-068 | integration_state_current | 集成当前状态 | P10-MOD-011 | 出站状态机 | 状态表 | 可变 | 是 | 是 |
| P11-TBL-069 | governance_state_current | 治理当前状态 | P10-MOD-012/013/014 | 质量/纠错/恢复状态机 | 状态表 | 可变 | 是 | 是 |
| P11-TBL-070 | state_transition_history | 状态转换历史 | 各拥有模块 | 31个状态机 | 历史表 | 追加 | 无 | 否 |
| P11-TBL-071 | patient_context_reference | 患者上下文引用 | P10-MOD-001 | 上下文边界 | 外部引用 | 追加/纠错 | 无 | 是 |
| P11-TBL-072 | visit_context_reference | 就诊上下文引用 | P10-MOD-001 | 上下文边界 | 外部引用 | 追加/纠错 | 无 | 是 |
| P11-TBL-073 | patient_visit_snapshot | 患者就诊快照 | P10-MOD-001/008 | P05-AGG-002/011 | 快照表 | 追加 | 无 | 否 |
| P11-TBL-074 | external_request_reference | 外部申请引用 | P10-MOD-001 | P05-AGG-001 | 外部引用 | 追加 | 无 | 是 |
| P11-TBL-075 | external_material_reference | 外部材料引用 | P10-MOD-002/007/010 | 关联聚合 | 外部引用 | 追加 | 无 | 是 |
| P11-TBL-076 | diagnosis_evidence_reference | 诊断依据引用 | P10-MOD-008 | P05-AGG-010 | 关系表 | 追加 | 无 | 是 |
| P11-TBL-077 | case_modality_relation | 病例模态关系 | P10-MOD-001/009 | P05-AGG-002/018 | 关系表 | 追加 | 无 | 是 |
| P11-TBL-078 | material_source_relation | 材料来源关系 | 各材料模块 | 来源链 | 关系表 | 追加 | 无 | 是 |
| P11-TBL-079 | authorization_scope_relation | 授权范围关系 | P10-MOD-013 | 治理边界 | 关系表 | 追加 | 无 | 是 |
| P11-TBL-080 | external_system_configuration | 外部系统配置 | P10-MOD-015/011 | 配置边界 | 配置表 | 版本化 | 无 | 是 |
| P11-TBL-081 | code_set_definition | 受控代码集定义 | P10-MOD-015 | 配置边界 | 配置表 | 版本化 | 无 | 是 |
| P11-TBL-082 | business_file | 业务文件元数据 | 文件能力 | 文件边界 | 文件表 | 追加/版本 | 无 | 是 |
| P11-TBL-083 | file_integrity_check | 文件完整性校验 | 文件能力 | 文件边界 | 文件表 | 追加 | 无 | 否 |
| P11-TBL-084 | file_business_reference | 文件业务引用 | 文件能力 | 关联聚合 | 文件关系表 | 追加 | 无 | 是 |
| P11-TBL-085 | inbound_raw_message | 入站原始消息 | P10-MOD-003/011 | P05-AGG-001/012 | 集成表 | 追加 | integration_state_current | 否 |
| P11-TBL-086 | outbox_event | 事件发件箱 | P10-MOD-011 | P05-AGG-012 | 集成表 | 追加 | integration_state_current | 否 |
| P11-TBL-087 | inbox_consumption | 事件收件箱去重 | P10-MOD-011 | P05-AGG-012 | 集成表 | 追加 | integration_state_current | 否 |
| P11-TBL-088 | delivery_attempt | 单次投递尝试 | P10-MOD-011 | P05-AGG-012 | 集成表 | 追加 | integration_state_current | 否 |
| P11-TBL-089 | reconciliation_difference | 对账差异 | P10-MOD-011 | P05-AGG-012 | 集成表 | 追加 | governance_state_current | 否 |

## 4. 关系模式共性列组

以下列组是逻辑字段组，不隐藏实际列；数据字典按表展开每张表的完整列清单。

| 字段组 | 实际列 | 使用范围 |
|---|---|---|
| G-ID | id、source_system_code、created_at、created_by_ref | 所有需要稳定身份的表 |
| G-CHANGE | record_version_no、concurrency_version、updated_at、updated_by_ref | 可变聚合根和任务表 |
| G-IMMUTABLE | fact_sequence_no、occurred_at、recorded_at、recorded_by_ref | 不可变事实、历史和事件表 |
| G-SOURCE | source_organization_ref、external_identifier、source_payload_ref | 外部引用、原始消息和外部结果 |
| G-SENSITIVITY | sensitivity_code、access_scope_code、export_control_code | 含业务或医学敏感数据的表 |
| G-ARCHIVE | archive_state_code、freeze_state_code、destroy_state_code、retention_basis_code | 受归档、冻结或销毁控制的表 |

字段组在数据字典中被明确展开或声明实际包含关系；不使用“其他字段”“审计字段等”或未定义扩展列。

## 5. P12及后续输入

P12获得：表级责任模块、外部引用、入站原始消息、发件箱、收件箱、投递尝试、对账差异、事件版本、幂等身份和平台适配待确认项。P14获得敏感等级和授权引用；P23/P27获得归档、文件、恢复和容量候选；P25获得并发、唯一性、状态守卫和事件幂等证据。
