# P09 全病理产品需求目录

文档状态：P09已基线化
需求总数：107（编号连续、无重复）
验收准则：182，其中正向107、负向75
参数登记：18，其中已确认0、未确认18，全部不阻塞P09

本目录是需求基线索引；每条需求的完整正文、验收、权限、审计、配置边界和上下游输入以对应文件为准。

## 1. 目录字段

### 共性功能需求（P09-REQ-0001–P09-REQ-0020）

| 需求编号 | 需求名称 | 需求类型 | 适用病理类型 | 优先级 | 安全等级 | 主要对象 | 所属聚合 | 上游追溯 | 正文文件 | 当前状态 |
|---|---|---|---|---|---|---|---|---|---|---|
| P09-REQ-0001 | 外部申请原始事实接收 | 业务功能 | 全病理共性 | MUST | S1 | OBJ-001 | AGG-001 | OBJ-001,AGG-001,SCN-PIS-001,P06-PROC-001,P05-INV-001,P07-EXC-001,BD-P04-001 | [p09-common-functional-requirements.md](./p09-common-functional-requirements.md) | P09已基线化 |
| P09-REQ-0002 | 手工申请建立 | 业务功能 | 全病理共性 | MUST | S1 | OBJ-002 | AGG-002 | OBJ-002,AGG-002,SCN-PIS-002,P06-PROC-002,P05-INV-002,P07-EXC-002,BD-P04-003 | [p09-common-functional-requirements.md](./p09-common-functional-requirements.md) | P09已基线化 |
| P09-REQ-0003 | 外部申请标识绑定 | 数据与追溯 | 全病理共性 | MUST | S1 | OBJ-003 | AGG-003 | OBJ-003,AGG-003,SCN-PIS-003,P06-PROC-003,P05-INV-003,P07-EXC-003,BD-P04-004 | [p09-common-functional-requirements.md](./p09-common-functional-requirements.md) | P09已基线化 |
| P09-REQ-0004 | 重复消息幂等判定 | 业务规则落实 | 全病理共性 | MUST | S1 | OBJ-004 | AGG-004 | OBJ-004,AGG-004,SCN-PIS-004,P06-PROC-004,P05-INV-004,P07-EXC-004,BD-P04-007 | [p09-common-functional-requirements.md](./p09-common-functional-requirements.md) | P09已基线化 |
| P09-REQ-0005 | 患者和就诊上下文引用 | 数据与追溯 | 全病理共性 | MUST | S1 | OBJ-005 | AGG-005 | OBJ-005,AGG-005,SCN-PIS-005,P06-PROC-005,P05-INV-005,P07-EXC-005,BD-P04-008 | [p09-common-functional-requirements.md](./p09-common-functional-requirements.md) | P09已基线化 |
| P09-REQ-0006 | 申请与病例身份分离 | 业务规则落实 | 全病理共性 | MUST | S1 | OBJ-006 | AGG-006 | OBJ-006,AGG-006,SCN-PIS-010,P06-PROC-006,P05-INV-006,P07-EXC-006,BD-P04-013 | [p09-common-functional-requirements.md](./p09-common-functional-requirements.md) | P09已基线化 |
| P09-REQ-0007 | 病理类型识别 | 业务功能 | 全病理共性 | MUST | S1 | OBJ-007 | AGG-007 | OBJ-007,AGG-007,SCN-PIS-012,P06-PROC-007,P05-INV-007,P07-EXC-007,BD-P04-015 | [p09-common-functional-requirements.md](./p09-common-functional-requirements.md) | P09已基线化 |
| P09-REQ-0008 | 独立病例与附属检测模式 | 业务规则落实 | 全病理共性 | MUST | S1 | OBJ-008 | AGG-008 | OBJ-008,AGG-008,SCN-PIS-013,P06-PROC-008,P05-INV-008,P07-EXC-008,BD-P04-016 | [p09-common-functional-requirements.md](./p09-common-functional-requirements.md) | P09已基线化 |
| P09-REQ-0009 | 内部身份和业务编号生效 | 数据与追溯 | 全病理共性 | MUST | S1 | OBJ-009 | AGG-009 | OBJ-009,AGG-009,SCN-PIS-020,P06-PROC-009,P05-INV-009,P07-EXC-009,BD-P04-017 | [p09-common-functional-requirements.md](./p09-common-functional-requirements.md) | P09已基线化 |
| P09-REQ-0010 | 病例拆分、合并和终止 | 业务规则落实 | 全病理共性 | MUST | S1 | OBJ-010 | AGG-010 | OBJ-010,AGG-010,SCN-PIS-060,P06-PROC-010,P05-INV-010,P07-EXC-010,BD-P04-018 | [p09-common-functional-requirements.md](./p09-common-functional-requirements.md) | P09已基线化 |
| P09-REQ-0011 | 预计标本与实际到达登记 | 业务功能 | 全病理共性 | MUST | S1 | OBJ-011 | AGG-011 | OBJ-011,AGG-011,SCN-PIS-090,P06-PROC-011,P05-INV-011,P07-EXC-011,BD-P04-023 | [p09-common-functional-requirements.md](./p09-common-functional-requirements.md) | P09已基线化 |
| P09-REQ-0012 | 逐标本身份和来源核对 | 数据与追溯 | 全病理共性 | MUST | S1 | OBJ-012 | AGG-012 | OBJ-012,AGG-012,SCN-PIS-100,P06-PROC-012,P05-INV-012,P07-EXC-012,BD-P04-025 | [p09-common-functional-requirements.md](./p09-common-functional-requirements.md) | P09已基线化 |
| P09-REQ-0013 | 责任交接与签收 | 审计与合规 | 全病理共性 | MUST | S1 | OBJ-013 | AGG-013 | OBJ-013,AGG-013,SCN-PIS-103,P06-PROC-013,P05-INV-013,P07-EXC-013,BD-P04-026 | [p09-common-functional-requirements.md](./p09-common-functional-requirements.md) | P09已基线化 |
| P09-REQ-0014 | 部分标本独立推进 | 业务规则落实 | 全病理共性 | MUST | S1 | OBJ-014 | AGG-014 | OBJ-014,AGG-014,SCN-PIS-001,P06-PROC-014,P05-INV-014,P07-EXC-014,BD-P04-027 | [p09-common-functional-requirements.md](./p09-common-functional-requirements.md) | P09已基线化 |
| P09-REQ-0015 | 申请取消与下游事实保护 | 业务规则落实 | 全病理共性 | MUST | S1 | OBJ-015 | AGG-015 | OBJ-015,AGG-015,SCN-PIS-002,P06-PROC-015,P05-INV-015,P07-EXC-015,BD-P04-032 | [p09-common-functional-requirements.md](./p09-common-functional-requirements.md) | P09已基线化 |
| P09-REQ-0016 | 受控纠错申请与影响评估 | 数据与追溯 | 全病理共性 | MUST | S1 | OBJ-016 | AGG-016 | OBJ-016,AGG-016,SCN-PIS-003,P06-PROC-016,P05-INV-016,P07-EXC-016,BD-P04-033 | [p09-common-functional-requirements.md](./p09-common-functional-requirements.md) | P09已基线化 |
| P09-REQ-0017 | 共性任务分配和接管 | 业务功能 | 全病理共性 | MUST | S1 | OBJ-017 | AGG-017 | OBJ-017,AGG-017,SCN-PIS-004,P06-PROC-017,P05-INV-017,P07-EXC-017,BD-P04-034 | [p09-common-functional-requirements.md](./p09-common-functional-requirements.md) | P09已基线化 |
| P09-REQ-0018 | 代理与临时授权使用 | 安全与权限 | 全病理共性 | MUST | S1 | OBJ-018 | AGG-018 | OBJ-018,AGG-018,SCN-PIS-005,P06-PROC-018,P05-INV-018,P07-EXC-018,BD-P04-040 | [p09-common-functional-requirements.md](./p09-common-functional-requirements.md) | P09已基线化 |
| P09-REQ-0019 | 身份和来源纠错执行 | 数据与追溯 | 全病理共性 | MUST | S1 | OBJ-019 | AGG-001 | OBJ-019,AGG-001,SCN-PIS-010,P06-PROC-019,P05-INV-019,P07-EXC-019,BD-P04-041 | [p09-common-functional-requirements.md](./p09-common-functional-requirements.md) | P09已基线化 |
| P09-REQ-0020 | 关键业务审计留痕 | 审计与合规 | 全病理共性 | MUST | S1 | OBJ-020 | AGG-002 | OBJ-020,AGG-002,SCN-PIS-012,P06-PROC-020,P05-INV-020,P07-EXC-020,BD-P04-043 | [p09-common-functional-requirements.md](./p09-common-functional-requirements.md) | P09已基线化 |

### 病理类型功能需求（P09-REQ-0021–P09-REQ-0054）

| 需求编号 | 需求名称 | 需求类型 | 适用病理类型 | 优先级 | 安全等级 | 主要对象 | 所属聚合 | 上游追溯 | 正文文件 | 当前状态 |
|---|---|---|---|---|---|---|---|---|---|---|
| P09-REQ-0021 | 组织取材记录 | 业务功能 | 常规组织病理 | MUST | S1 | OBJ-021 | AGG-003 | OBJ-021,AGG-003,SCN-PIS-030,P06-PROC-021,P05-INV-021,P07-EXC-021,BD-P04-044 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0022 | 蜡块业务记录和编号 | 数据与追溯 | 常规组织病理 | MUST | S1 | OBJ-022 | AGG-004 | OBJ-022,AGG-004,SCN-PIS-032,P06-PROC-022,P05-INV-022,P07-EXC-022,BD-P04-047 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0023 | 组织处理与包埋完成 | 业务功能 | 常规组织病理 | MUST | S1 | OBJ-023 | AGG-005 | OBJ-023,AGG-005,SCN-PIS-040,P06-PROC-023,P05-INV-023,P07-EXC-023,BD-P04-048 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0024 | 技术医嘱和计划玻片 | 业务规则落实 | 常规组织病理 | MUST | S1 | OBJ-024 | AGG-006 | OBJ-024,AGG-006,SCN-PIS-044,P06-PROC-024,P05-INV-024,P07-EXC-024,BD-P04-049 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0025 | 实际玻片多来源形成 | 业务规则落实 | 常规组织病理 | MUST | S1 | OBJ-025 | AGG-007 | OBJ-025,AGG-007,SCN-PIS-051,P06-PROC-025,P05-INV-025,P07-EXC-025,BD-P04-050 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0026 | 切片染色与特殊染色 | 业务功能 | 常规组织病理 | MUST | S1 | OBJ-026 | AGG-008 | OBJ-026,AGG-008,SCN-PIS-060,P06-PROC-026,P05-INV-026,P07-EXC-026,BD-P04-052 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0027 | 扫描可选与数字切片版本 | 业务功能 | 常规组织病理 | MUST | S1 | OBJ-027 | AGG-009 | OBJ-027,AGG-009,SCN-PIS-063,P06-PROC-027,P05-INV-027,P07-EXC-027,BD-P04-053 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0028 | 组织诊断和复诊审核 | 业务功能 | 常规组织病理 | MUST | S1 | OBJ-028 | AGG-010 | OBJ-028,AGG-010,SCN-PIS-070,P06-PROC-028,P05-INV-028,P07-EXC-028,BD-P04-054 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0029 | 组织报告签发 | 业务功能 | 常规组织病理 | MUST | S1 | OBJ-029 | AGG-011 | OBJ-029,AGG-011,SCN-PIS-074,P06-PROC-029,P05-INV-029,P07-EXC-029,BD-P04-055 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0030 | 冰冻业务和轮次建立 | 业务功能 | 术中冰冻 | MUST | S1 | OBJ-030 | AGG-012 | OBJ-030,AGG-012,SCN-PIS-110,P06-PROC-030,P05-INV-030,P07-EXC-030,BD-P04-060 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0031 | 冰冻初步反馈与正式报告 | 业务规则落实 | 术中冰冻 | MUST | S2 | OBJ-031 | AGG-013 | OBJ-031,AGG-013,SCN-PIS-111,P06-PROC-031,P05-INV-031,P07-EXC-031,BD-P04-062 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0032 | 冰剩转常规 | 业务功能 | 术中冰冻 | MUST | S2 | OBJ-032 | AGG-014 | OBJ-032,AGG-014,SCN-PIS-112,P06-PROC-032,P05-INV-032,P07-EXC-032,BD-P04-064 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0033 | 细胞病理类型和标本分类 | 业务功能 | 妇科细胞病理 | MUST | S2 | OBJ-033 | AGG-015 | OBJ-033,AGG-015,SCN-PIS-115,P06-PROC-033,P05-INV-033,P07-EXC-033,BD-P04-065 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0034 | 细胞直接涂片与液基制备 | 业务功能 | 非妇科细胞病理 | MUST | S2 | OBJ-034 | AGG-016 | OBJ-034,AGG-016,SCN-PIS-118,P06-PROC-034,P05-INV-034,P07-EXC-034,BD-P04-066 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0035 | 细胞制备记录与保存边界 | 数据与追溯 | 细胞蜡块 | MUST | S2 | OBJ-035 | AGG-017 | OBJ-035,AGG-017,SCN-PIS-119,P06-PROC-035,P05-INV-035,P07-EXC-035,BD-P04-068 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0036 | 细胞蜡块业务记录 | 业务功能 | 细胞蜡块 | MUST | S2 | OBJ-036 | AGG-018 | OBJ-036,AGG-018,SCN-PIS-120,P06-PROC-036,P05-INV-036,P07-EXC-036,BD-P04-069 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0037 | 细胞充分性评价 | 业务规则落实 | 妇科细胞病理 | MUST | S2 | OBJ-037 | AGG-001 | OBJ-037,AGG-001,SCN-PIS-121,P06-PROC-001,P05-INV-037,P07-EXC-037,BD-P04-071 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0038 | 细胞筛查资格与任务 | 安全与权限 | 非妇科细胞病理 | MUST | S2 | OBJ-038 | AGG-002 | OBJ-038,AGG-002,SCN-PIS-122,P06-PROC-002,P05-INV-038,P07-EXC-038,BD-P04-075 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0039 | 细胞筛查复核与质量抽查 | 业务功能 | 穿刺细胞病理 | MUST | S2 | OBJ-039 | AGG-003 | OBJ-039,AGG-003,SCN-PIS-123,P06-PROC-003,P05-INV-039,P07-EXC-039,BD-P04-078 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0040 | 细胞最终诊断与报告 | 业务功能 | 妇科细胞病理 | MUST | S2 | OBJ-040 | AGG-004 | OBJ-040,AGG-004,SCN-PIS-030,P06-PROC-004,P05-INV-040,P07-EXC-040,BD-P04-079 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0041 | HPV等关联检测展示 | 集成与互操作 | 妇科细胞病理 | MUST | S2 | OBJ-041 | AGG-005 | OBJ-041,AGG-005,SCN-PIS-032,P06-PROC-005,P05-INV-041,P07-EXC-041,BD-P04-080 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0042 | 独立或附属分子检测建立 | 业务规则落实 | 分子病理 | MUST | S2 | OBJ-042 | AGG-006 | OBJ-042,AGG-006,SCN-PIS-040,P06-PROC-006,P05-INV-042,P07-EXC-042,BD-P04-081 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0043 | 分子材料选择与派生材料 | 数据与追溯 | 分子病理 | MUST | S2 | OBJ-043 | AGG-007 | OBJ-043,AGG-007,SCN-PIS-044,P06-PROC-007,P05-INV-043,P07-EXC-043,BD-P04-082 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0044 | 核酸或分析物提取质量 | 业务功能 | 分子病理 | MUST | S2 | OBJ-044 | AGG-008 | OBJ-044,AGG-008,SCN-PIS-051,P06-PROC-008,P05-INV-044,P07-EXC-044,BD-P04-083 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0045 | 分子任务运行质控和原始结果 | 业务功能 | 分子病理 | MUST | S2 | OBJ-045 | AGG-009 | OBJ-045,AGG-009,SCN-PIS-060,P06-PROC-009,P05-INV-045,P07-EXC-045,BD-P04-084 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0046 | 有效分子结果与医学判读 | 业务功能 | 分子病理 | MUST | S2 | OBJ-046 | AGG-010 | OBJ-046,AGG-010,SCN-PIS-063,P06-PROC-010,P05-INV-046,P07-EXC-046,BD-P04-085 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0047 | 无效失败复测补测 | 业务规则落实 | 分子病理 | MUST | S2 | OBJ-047 | AGG-011 | OBJ-047,AGG-011,SCN-PIS-070,P06-PROC-011,P05-INV-047,P07-EXC-047,BD-P04-086 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0048 | 外送机构交接和外部结果核验 | 业务功能 | 外送检测 | MUST | S2 | OBJ-048 | AGG-012 | OBJ-048,AGG-012,SCN-PIS-074,P06-PROC-012,P05-INV-048,P07-EXC-048,BD-P04-087 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0049 | 外送结果与报告引用 | 集成与互操作 | 外送检测 | MUST | S2 | OBJ-049 | AGG-013 | OBJ-049,AGG-013,SCN-PIS-110,P06-PROC-013,P05-INV-049,P07-EXC-049,BD-P04-088 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0050 | 多模态综合诊断和综合报告 | 业务功能 | 多模态综合诊断 | MUST | S2 | OBJ-050 | AGG-014 | OBJ-050,AGG-014,SCN-PIS-111,P06-PROC-014,P05-INV-050,P07-EXC-050,BD-P04-089 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0051 | 综合报告版本与组成报告保护 | 业务规则落实 | 多模态综合诊断 | MUST | S2 | OBJ-051 | AGG-015 | OBJ-051,AGG-015,SCN-PIS-112,P06-PROC-015,P05-INV-051,P07-EXC-051,BD-P04-090 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0052 | 组织细胞分子结果关联 | 集成与互操作 | 多模态综合诊断 | MUST | S2 | OBJ-052 | AGG-016 | OBJ-052,AGG-016,SCN-PIS-115,P06-PROC-016,P05-INV-052,P07-EXC-052,BD-P04-091 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0053 | 外部医学依据纳入 | 集成与互操作 | 多模态综合诊断 | MUST | S2 | OBJ-053 | AGG-017 | OBJ-053,AGG-017,SCN-PIS-118,P06-PROC-017,P05-INV-053,P07-EXC-053,BD-P04-001 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |
| P09-REQ-0054 | 跨模态冲突与局部继续 | 业务规则落实 | 多模态综合诊断 | MUST | S2 | OBJ-054 | AGG-018 | OBJ-054,AGG-018,SCN-PIS-119,P06-PROC-018,P05-INV-054,P07-EXC-054,BD-P04-003 | [p09-modality-functional-requirements.md](./p09-modality-functional-requirements.md) | P09已基线化 |

### 跨流程、报告、接口和治理需求（P09-REQ-0055–P09-REQ-0069）

| 需求编号 | 需求名称 | 需求类型 | 适用病理类型 | 优先级 | 安全等级 | 主要对象 | 所属聚合 | 上游追溯 | 正文文件 | 当前状态 |
|---|---|---|---|---|---|---|---|---|---|---|
| P09-REQ-0055 | 诊断任务分配和接管 | 业务功能 | 全病理共性 | MUST | S2 | OBJ-055 | AGG-001 | OBJ-055,AGG-001,SCN-PIS-060,P06-PROC-019,P05-INV-055,P07-EXC-055,BD-P04-004 | [p09-cross-cutting-functional-requirements.md](./p09-cross-cutting-functional-requirements.md) | P09已基线化 |
| P09-REQ-0056 | 诊断记录提交和固定 | 业务功能 | 全病理共性 | MUST | S2 | OBJ-056 | AGG-002 | OBJ-056,AGG-002,SCN-PIS-061,P06-PROC-020,P05-INV-056,P07-EXC-056,BD-P04-007 | [p09-cross-cutting-functional-requirements.md](./p09-cross-cutting-functional-requirements.md) | P09已基线化 |
| P09-REQ-0057 | 多模态关联版本固定 | 业务规则落实 | 全病理共性 | MUST | S2 | OBJ-057 | AGG-003 | OBJ-057,AGG-003,SCN-PIS-063,P06-PROC-021,P05-INV-057,P07-EXC-057,BD-P04-008 | [p09-cross-cutting-functional-requirements.md](./p09-cross-cutting-functional-requirements.md) | P09已基线化 |
| P09-REQ-0058 | 综合诊断依据组合 | 业务功能 | 全病理共性 | MUST | S2 | OBJ-058 | AGG-004 | OBJ-058,AGG-004,SCN-PIS-091,P06-PROC-022,P05-INV-058,P07-EXC-058,BD-P04-013 | [p09-cross-cutting-functional-requirements.md](./p09-cross-cutting-functional-requirements.md) | P09已基线化 |
| P09-REQ-0059 | 报告生命周期建立 | 业务功能 | 全病理共性 | MUST | S2 | OBJ-059 | AGG-005 | OBJ-059,AGG-005,SCN-PIS-093,P06-PROC-023,P05-INV-059,P07-EXC-059,BD-P04-015 | [p09-cross-cutting-functional-requirements.md](./p09-cross-cutting-functional-requirements.md) | P09已基线化 |
| P09-REQ-0060 | 报告版本签发和引用 | 数据与追溯 | 全病理共性 | MUST | S2 | OBJ-060 | AGG-006 | OBJ-060,AGG-006,SCN-PIS-094,P06-PROC-024,P05-INV-060,P07-EXC-060,BD-P04-016 | [p09-cross-cutting-functional-requirements.md](./p09-cross-cutting-functional-requirements.md) | P09已基线化 |
| P09-REQ-0061 | 补充更正撤回重新签发 | 业务规则落实 | 全病理共性 | MUST | S2 | OBJ-061 | AGG-007 | OBJ-061,AGG-007,SCN-PIS-095,P06-PROC-025,P05-INV-061,P07-EXC-061,BD-P04-017 | [p09-cross-cutting-functional-requirements.md](./p09-cross-cutting-functional-requirements.md) | P09已基线化 |
| P09-REQ-0062 | 外部医学依据和报告引用 | 集成与互操作 | 全病理共性 | MUST | S2 | OBJ-062 | AGG-008 | OBJ-062,AGG-008,SCN-PIS-100,P06-PROC-026,P05-INV-062,P07-EXC-062,BD-P04-018 | [p09-cross-cutting-functional-requirements.md](./p09-cross-cutting-functional-requirements.md) | P09已基线化 |
| P09-REQ-0063 | 本地事实生成出站事件 | 集成与互操作 | 全病理共性 | MUST | S2 | OBJ-063 | AGG-009 | OBJ-063,AGG-009,SCN-PIS-103,P06-PROC-027,P05-INV-063,P07-EXC-063,BD-P04-023 | [p09-cross-cutting-functional-requirements.md](./p09-cross-cutting-functional-requirements.md) | P09已基线化 |
| P09-REQ-0064 | 投递尝试独立记录 | 集成与互操作 | 全病理共性 | MUST | S2 | OBJ-001 | AGG-010 | OBJ-001,AGG-010,SCN-PIS-104,P06-PROC-028,P05-INV-064,P07-EXC-064,BD-P04-025 | [p09-cross-cutting-functional-requirements.md](./p09-cross-cutting-functional-requirements.md) | P09已基线化 |
| P09-REQ-0065 | 外部业务状态与对账差异 | 集成与互操作 | 全病理共性 | MUST | S2 | OBJ-002 | AGG-011 | OBJ-002,AGG-011,SCN-PIS-118,P06-PROC-029,P05-INV-065,P07-EXC-065,BD-P04-026 | [p09-cross-cutting-functional-requirements.md](./p09-cross-cutting-functional-requirements.md) | P09已基线化 |
| P09-REQ-0066 | 高风险操作授权和强认证 | 安全与权限 | 全病理共性 | MUST | S2 | OBJ-003 | AGG-012 | OBJ-003,AGG-012,SCN-PIS-121,P06-PROC-030,P05-INV-066,P07-EXC-066,BD-P04-027 | [p09-cross-cutting-functional-requirements.md](./p09-cross-cutting-functional-requirements.md) | P09已基线化 |
| P09-REQ-0067 | 质量事件调查和隔离 | 业务规则落实 | 全病理共性 | MUST | S2 | OBJ-004 | AGG-013 | OBJ-004,AGG-013,SCN-PIS-123,P06-PROC-031,P05-INV-067,P07-EXC-067,BD-P04-032 | [p09-cross-cutting-functional-requirements.md](./p09-cross-cutting-functional-requirements.md) | P09已基线化 |
| P09-REQ-0068 | 档案冻结与销毁申请 | 归档、保留与销毁 | 全病理共性 | MUST | S2 | OBJ-005 | AGG-014 | OBJ-005,AGG-014,SCN-PIS-060,P06-PROC-032,P05-INV-068,P07-EXC-068,BD-P04-033 | [p09-cross-cutting-functional-requirements.md](./p09-cross-cutting-functional-requirements.md) | P09已基线化 |
| P09-REQ-0069 | 恢复任务和重新开放 | 恢复与灾备 | 全病理共性 | MUST | S2 | OBJ-006 | AGG-015 | OBJ-006,AGG-015,SCN-PIS-061,P06-PROC-033,P05-INV-069,P07-EXC-069,BD-P04-034 | [p09-cross-cutting-functional-requirements.md](./p09-cross-cutting-functional-requirements.md) | P09已基线化 |

### 数据、安全、审计、归档和恢复需求（P09-REQ-0070–P09-REQ-0087）

| 需求编号 | 需求名称 | 需求类型 | 适用病理类型 | 优先级 | 安全等级 | 主要对象 | 所属聚合 | 上游追溯 | 正文文件 | 当前状态 |
|---|---|---|---|---|---|---|---|---|---|---|
| P09-REQ-0070 | 内部稳定身份与外部标识分离 | 数据与追溯 | 全病理共性 | MUST | S2 | OBJ-007 | AGG-016 | OBJ-007,AGG-016,SCN-PIS-001,P06-PROC-034,P05-INV-070,P07-EXC-070,BD-P04-040 | [p09-data-security-governance-requirements.md](./p09-data-security-governance-requirements.md) | P09已基线化 |
| P09-REQ-0071 | 材料来源连续性 | 业务规则落实 | 全病理共性 | MUST | S2 | OBJ-008 | AGG-017 | OBJ-008,AGG-017,SCN-PIS-003,P06-PROC-035,P05-INV-001,P07-EXC-071,BD-P04-041 | [p09-data-security-governance-requirements.md](./p09-data-security-governance-requirements.md) | P09已基线化 |
| P09-REQ-0072 | 对象级独立推进 | 数据与追溯 | 全病理共性 | MUST | S2 | OBJ-009 | AGG-018 | OBJ-009,AGG-018,SCN-PIS-005,P06-PROC-036,P05-INV-002,P07-EXC-072,BD-P04-043 | [p09-data-security-governance-requirements.md](./p09-data-security-governance-requirements.md) | P09已基线化 |
| P09-REQ-0073 | 不可变原始事实 | 审计与合规 | 全病理共性 | MUST | S2 | OBJ-010 | AGG-001 | OBJ-010,AGG-001,SCN-PIS-013,P06-PROC-001,P05-INV-003,P07-EXC-073,BD-P04-044 | [p09-data-security-governance-requirements.md](./p09-data-security-governance-requirements.md) | P09已基线化 |
| P09-REQ-0074 | 不可变版本和快照 | 安全与权限 | 全病理共性 | MUST | S2 | OBJ-011 | AGG-002 | OBJ-011,AGG-002,SCN-PIS-020,P06-PROC-002,P05-INV-004,P07-EXC-074,BD-P04-047 | [p09-data-security-governance-requirements.md](./p09-data-security-governance-requirements.md) | P09已基线化 |
| P09-REQ-0075 | 设备原始结果与有效结果分离 | 集成与互操作 | 全病理共性 | MUST | S2 | OBJ-012 | AGG-003 | OBJ-012,AGG-003,SCN-PIS-027,P06-PROC-003,P05-INV-005,P07-EXC-075,BD-P04-048 | [p09-data-security-governance-requirements.md](./p09-data-security-governance-requirements.md) | P09已基线化 |
| P09-REQ-0076 | 患者就诊快照固定 | 归档、保留与销毁 | 全病理共性 | SHOULD | S3 | OBJ-013 | AGG-004 | OBJ-013,AGG-004,SCN-PIS-031,P06-PROC-004,P05-INV-006,P07-EXC-076,BD-P04-049 | [p09-data-security-governance-requirements.md](./p09-data-security-governance-requirements.md) | P09已基线化 |
| P09-REQ-0077 | 最小授权边界 | 恢复与灾备 | 全病理共性 | SHOULD | S3 | OBJ-014 | AGG-005 | OBJ-014,AGG-005,SCN-PIS-033,P06-PROC-005,P05-INV-007,P07-EXC-077,BD-P04-050 | [p09-data-security-governance-requirements.md](./p09-data-security-governance-requirements.md) | P09已基线化 |
| P09-REQ-0078 | 代理与临时授权边界 | 数据与追溯 | 全病理共性 | SHOULD | S3 | OBJ-015 | AGG-006 | OBJ-015,AGG-006,SCN-PIS-034,P06-PROC-006,P05-INV-008,P07-EXC-078,BD-P04-052 | [p09-data-security-governance-requirements.md](./p09-data-security-governance-requirements.md) | P09已基线化 |
| P09-REQ-0079 | 高风险第二人复核 | 业务规则落实 | 全病理共性 | SHOULD | S3 | OBJ-016 | AGG-007 | OBJ-016,AGG-007,SCN-PIS-035,P06-PROC-007,P05-INV-009,P07-EXC-079,BD-P04-053 | [p09-data-security-governance-requirements.md](./p09-data-security-governance-requirements.md) | P09已基线化 |
| P09-REQ-0080 | 审计事件完整性 | 数据与追溯 | 全病理共性 | SHOULD | S3 | OBJ-017 | AGG-008 | OBJ-017,AGG-008,SCN-PIS-036,P06-PROC-008,P05-INV-010,P07-EXC-080,BD-P04-054 | [p09-data-security-governance-requirements.md](./p09-data-security-governance-requirements.md) | P09已基线化 |
| P09-REQ-0081 | 受控纠错证据链 | 审计与合规 | 全病理共性 | SHOULD | S3 | OBJ-018 | AGG-009 | OBJ-018,AGG-009,SCN-PIS-091,P06-PROC-009,P05-INV-011,P07-EXC-081,BD-P04-055 | [p09-data-security-governance-requirements.md](./p09-data-security-governance-requirements.md) | P09已基线化 |
| P09-REQ-0082 | 外部事件幂等和版本保护 | 安全与权限 | 全病理共性 | SHOULD | S3 | OBJ-019 | AGG-010 | OBJ-019,AGG-010,SCN-PIS-093,P06-PROC-010,P05-INV-012,P07-EXC-082,BD-P04-060 | [p09-data-security-governance-requirements.md](./p09-data-security-governance-requirements.md) | P09已基线化 |
| P09-REQ-0083 | 失败重试和补偿事实 | 集成与互操作 | 全病理共性 | SHOULD | S3 | OBJ-020 | AGG-011 | OBJ-020,AGG-011,SCN-PIS-095,P06-PROC-011,P05-INV-013,P07-EXC-083,BD-P04-062 | [p09-data-security-governance-requirements.md](./p09-data-security-governance-requirements.md) | P09已基线化 |
| P09-REQ-0084 | 报告引用完整性 | 归档、保留与销毁 | 全病理共性 | SHOULD | S3 | OBJ-021 | AGG-012 | OBJ-021,AGG-012,SCN-PIS-100,P06-PROC-012,P05-INV-014,P07-EXC-084,BD-P04-064 | [p09-data-security-governance-requirements.md](./p09-data-security-governance-requirements.md) | P09已基线化 |
| P09-REQ-0085 | 档案保留和销毁保护 | 恢复与灾备 | 全病理共性 | SHOULD | S3 | OBJ-022 | AGG-013 | OBJ-022,AGG-013,SCN-PIS-103,P06-PROC-013,P05-INV-015,P07-EXC-085,BD-P04-065 | [p09-data-security-governance-requirements.md](./p09-data-security-governance-requirements.md) | P09已基线化 |
| P09-REQ-0086 | 恢复后身份和来源校验 | 数据与追溯 | 全病理共性 | SHOULD | S3 | OBJ-023 | AGG-014 | OBJ-023,AGG-014,SCN-PIS-104,P06-PROC-014,P05-INV-016,P07-EXC-086,BD-P04-066 | [p09-data-security-governance-requirements.md](./p09-data-security-governance-requirements.md) | P09已基线化 |
| P09-REQ-0087 | 恢复后报告和文件校验 | 业务规则落实 | 全病理共性 | SHOULD | S3 | OBJ-024 | AGG-015 | OBJ-024,AGG-015,SCN-PIS-120,P06-PROC-015,P05-INV-017,P07-EXC-087,BD-P04-068 | [p09-data-security-governance-requirements.md](./p09-data-security-governance-requirements.md) | P09已基线化 |

### 质量属性需求（P09-REQ-0088–P09-REQ-0107）

| 需求编号 | 需求名称 | 需求类型 | 适用病理类型 | 优先级 | 安全等级 | 主要对象 | 所属聚合 | 上游追溯 | 正文文件 | 当前状态 |
|---|---|---|---|---|---|---|---|---|---|---|
| P09-REQ-0088 | 患者安全关键守卫可观察 | 安全与权限 | 全病理共性 | SHOULD | S3 | OBJ-025 | AGG-016 | OBJ-025,AGG-016,SCN-PIS-001,P06-PROC-016,P05-INV-018,P07-EXC-088,BD-P04-069 | [p09-quality-attribute-requirements.md](./p09-quality-attribute-requirements.md) | P09已基线化 |
| P09-REQ-0089 | 交互响应指标登记 | 性能与容量 | 全病理共性 | SHOULD | S3 | OBJ-026 | AGG-017 | OBJ-026,AGG-017,SCN-PIS-003,P06-PROC-017,P05-INV-019,P07-EXC-089,BD-P04-071 | [p09-quality-attribute-requirements.md](./p09-quality-attribute-requirements.md) | P09已基线化 |
| P09-REQ-0090 | 批量业务时限指标登记 | 性能与容量 | 全病理共性 | SHOULD | S3 | OBJ-027 | AGG-018 | OBJ-027,AGG-018,SCN-PIS-009,P06-PROC-018,P05-INV-020,P07-EXC-090,BD-P04-075 | [p09-quality-attribute-requirements.md](./p09-quality-attribute-requirements.md) | P09已基线化 |
| P09-REQ-0091 | 报告生成时限指标登记 | 性能与容量 | 全病理共性 | SHOULD | S3 | OBJ-028 | AGG-001 | OBJ-028,AGG-001,SCN-PIS-026,P06-PROC-019,P05-INV-021,P07-EXC-091,BD-P04-078 | [p09-quality-attribute-requirements.md](./p09-quality-attribute-requirements.md) | P09已基线化 |
| P09-REQ-0092 | 图像和大文件处理指标登记 | 性能与容量 | 全病理共性 | SHOULD | S3 | OBJ-029 | AGG-002 | OBJ-029,AGG-002,SCN-PIS-032,P06-PROC-020,P05-INV-022,P07-EXC-092,BD-P04-079 | [p09-quality-attribute-requirements.md](./p09-quality-attribute-requirements.md) | P09已基线化 |
| P09-REQ-0093 | 查询与检索指标登记 | 性能与容量 | 全病理共性 | SHOULD | S3 | OBJ-030 | AGG-003 | OBJ-030,AGG-003,SCN-PIS-033,P06-PROC-021,P05-INV-023,P07-EXC-093,BD-P04-080 | [p09-quality-attribute-requirements.md](./p09-quality-attribute-requirements.md) | P09已基线化 |
| P09-REQ-0094 | 投递和对账时限指标登记 | 性能与容量 | 全病理共性 | SHOULD | S3 | OBJ-031 | AGG-004 | OBJ-031,AGG-004,SCN-PIS-034,P06-PROC-022,P05-INV-024,P07-EXC-094,BD-P04-081 | [p09-quality-attribute-requirements.md](./p09-quality-attribute-requirements.md) | P09已基线化 |
| P09-REQ-0095 | 容量测量维度登记 | 性能与容量 | 全病理共性 | SHOULD | S3 | OBJ-032 | AGG-005 | OBJ-032,AGG-005,SCN-PIS-036,P06-PROC-023,P05-INV-025,P07-EXC-095,BD-P04-082 | [p09-quality-attribute-requirements.md](./p09-quality-attribute-requirements.md) | P09已基线化 |
| P09-REQ-0096 | 局部故障隔离 | 可用性与连续性 | 全病理共性 | SHOULD | S3 | OBJ-033 | AGG-006 | OBJ-033,AGG-006,SCN-PIS-040,P06-PROC-024,P05-INV-026,P07-EXC-096,BD-P04-083 | [p09-quality-attribute-requirements.md](./p09-quality-attribute-requirements.md) | P09已基线化 |
| P09-REQ-0097 | 外部不可用时本地事实保护 | 可用性与连续性 | 全病理共性 | SHOULD | S3 | OBJ-034 | AGG-007 | OBJ-034,AGG-007,SCN-PIS-051,P06-PROC-025,P05-INV-027,P07-EXC-097,BD-P04-084 | [p09-quality-attribute-requirements.md](./p09-quality-attribute-requirements.md) | P09已基线化 |
| P09-REQ-0098 | 设备离线连续性 | 可用性与连续性 | 全病理共性 | SHOULD | S3 | OBJ-035 | AGG-008 | OBJ-035,AGG-008,SCN-PIS-093,P06-PROC-026,P05-INV-028,P07-EXC-098,BD-P04-085 | [p09-quality-attribute-requirements.md](./p09-quality-attribute-requirements.md) | P09已基线化 |
| P09-REQ-0099 | 恢复后对账连续性 | 可用性与连续性 | 全病理共性 | SHOULD | S3 | OBJ-036 | AGG-009 | OBJ-036,AGG-009,SCN-PIS-103,P06-PROC-027,P05-INV-029,P07-EXC-099,BD-P04-086 | [p09-quality-attribute-requirements.md](./p09-quality-attribute-requirements.md) | P09已基线化 |
| P09-REQ-0100 | 备份完整性 | 恢复与灾备 | 全病理共性 | SHOULD | S3 | OBJ-037 | AGG-010 | OBJ-037,AGG-010,SCN-PIS-104,P06-PROC-028,P05-INV-030,P07-EXC-100,BD-P04-087 | [p09-quality-attribute-requirements.md](./p09-quality-attribute-requirements.md) | P09已基线化 |
| P09-REQ-0101 | 恢复范围与隔离恢复 | 恢复与灾备 | 全病理共性 | MAY | S4 | OBJ-038 | AGG-011 | OBJ-038,AGG-011,SCN-PIS-001,P06-PROC-029,P05-INV-031,P07-EXC-101,BD-P04-088 | [p09-quality-attribute-requirements.md](./p09-quality-attribute-requirements.md) | P09已基线化 |
| P09-REQ-0102 | RPO参数登记 | 恢复与灾备 | 全病理共性 | MAY | S4 | OBJ-039 | AGG-012 | OBJ-039,AGG-012,SCN-PIS-003,P06-PROC-030,P05-INV-032,P07-EXC-102,BD-P04-089 | [p09-quality-attribute-requirements.md](./p09-quality-attribute-requirements.md) | P09已基线化 |
| P09-REQ-0103 | RTO参数登记 | 恢复与灾备 | 全病理共性 | MAY | S4 | OBJ-040 | AGG-013 | OBJ-040,AGG-013,SCN-PIS-009,P06-PROC-031,P05-INV-033,P07-EXC-103,BD-P04-090 | [p09-quality-attribute-requirements.md](./p09-quality-attribute-requirements.md) | P09已基线化 |
| P09-REQ-0104 | 关键流程可观测性 | 可维护性与可观测性 | 全病理共性 | MAY | S4 | OBJ-041 | AGG-014 | OBJ-041,AGG-014,SCN-PIS-026,P06-PROC-032,P05-INV-034,P07-EXC-104,BD-P04-091 | [p09-quality-attribute-requirements.md](./p09-quality-attribute-requirements.md) | P09已基线化 |
| P09-REQ-0105 | 配置和规则变更可追溯 | 可维护性与可观测性 | 全病理共性 | MAY | S4 | OBJ-042 | AGG-015 | OBJ-042,AGG-015,SCN-PIS-032,P06-PROC-033,P05-INV-035,P07-EXC-105,BD-P04-001 | [p09-quality-attribute-requirements.md](./p09-quality-attribute-requirements.md) | P09已基线化 |
| P09-REQ-0106 | 升级不破坏历史 | 可维护性与可观测性 | 全病理共性 | MAY | S4 | OBJ-043 | AGG-016 | OBJ-043,AGG-016,SCN-PIS-033,P06-PROC-034,P05-INV-036,P07-EXC-106,BD-P04-003 | [p09-quality-attribute-requirements.md](./p09-quality-attribute-requirements.md) | P09已基线化 |
| P09-REQ-0107 | 错误防范和操作可理解 | 易用性与错误防范 | 全病理共性 | MAY | S4 | OBJ-044 | AGG-017 | OBJ-044,AGG-017,SCN-PIS-034,P06-PROC-035,P05-INV-037,P07-EXC-107,BD-P04-004 | [p09-quality-attribute-requirements.md](./p09-quality-attribute-requirements.md) | P09已基线化 |

## 2. 汇总

| 维度 | 结果 |
|---|---|
| 编号连续性 | P09-REQ-0001 至 P09-REQ-0107，无重复 |
| 优先级 | MUST 75；SHOULD 25；MAY 7 |
| 安全等级 | S1 30；S2 45；S3 25；S4 7 |
| 正式病理类型覆盖 | 常规组织病理、术中冰冻、妇科细胞病理、非妇科细胞病理、穿刺细胞病理、细胞蜡块、分子病理、外送检测、多模态综合诊断 |
| 上游覆盖 | 73场景、52项已确认决策、63对象、18聚合、70不变量、36流程、108决策点、108异常入口、1,220个Q均在需求或追溯矩阵中可定位 |
| 下游输入 | P10、P11、P12、P14、P24、P25，必要时P27 |
| 当前状态 | P09已基线化 |
