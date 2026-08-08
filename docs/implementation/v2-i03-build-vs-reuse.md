# V2-I03 构建与复用决策

## 1. 范围与基线

- 实施基线：`249e89f`（V2-I02 材料生产主链完成）。
- 本阶段只实现 V2 Diagnosis / DiagnosisTemplate / Responsibility / Assignment / Diagnosis Workspace。
- 不进入 V2-I04 TechnicalOrder/TechnicalProject，也不进入 V2-I05 ReportTemplate/Report/sign-out。
- Legacy Diagnosis、Report、DiagnosisTask、Sign-out 和 CaseStatus 不属于本阶段实现边界。

## 2. 复用项

| 复用对象 | 复用方式 | 边界 |
|---|---|---|
| V2 Case/Specimen | 通过 `JdbcV2RegistrationRepository` 读取 | Diagnosis 模块不写登记表，不引入 Legacy 关系 |
| V2 Material Tree | 复用 `JdbcV2MaterialRepository.findMaterialTree` 只读查询 | 诊断认领只读取初始切片完成事实，不修改材料事实 |
| P15 Authorization | 通过 `P15AuthorizationService` 检查共享权限 | 角色、责任人和并发条件仍由 Diagnosis 应用服务判断 |
| Audit/Outbox | 复用 `JdbcAuditEventRepository`、`OutboxPort` | 医疗文本不直接写入日志；前后值使用不可逆摘要保留变更证据 |
| Vue/Vite/TypeScript | 沿用现有前端工程 | Diagnosis Workspace 使用独立 V2 组件，不接 Legacy 诊断路由 |

## 3. 新建项

1. `pis_v2.diagnosis`、`diagnosis_template`、`diagnosis_template_version`、`responsibility_unit` 和 `assignment_rule` 及必要索引。
2. Diagnosis、DiagnosisTemplate、DiagnosisTemplateVersion、ResponsibilityUnit 和 AssignmentRule 领域对象。
3. Diagnosis 应用服务：公开池认领、手工分配、自主认领、重分配、内容保存、初诊/审核/审阅完成以及工作区查询。
4. DiagnosisTemplate 版本创建和发布命令。已发布版本由应用规则和数据库触发器共同保护为不可变。
5. `GET /api/v2/diagnosis-workspaces/{caseId}` 聚合查询。它是 Projection，不创建 `diagnosis_workspace` 源表。
6. 独立 V2 Diagnosis Workspace 前端；技术医嘱、报告和签发仅显示为后续占位，不提前创建后续领域实体。

## 4. 关键设计决定

### 4.1 Diagnosis 与生命周期

Diagnosis 不创建 `WAIT_INITIAL`、`INITIALING`、`WAIT_REVIEW`、`REVIEWING`、`WAIT_AUDIT`、`AUDITING`、`SIGNED` 或 `WITHDRAWN` 状态机。Diagnosis 的存在、责任链当前节点和未来 Report 事实共同形成工作区投影。

### 4.2 责任链与分配

ResponsibilityUnit 按 `sequence_no` 累积保存，当前节点由 `completed_at IS NULL AND ended_at IS NULL` 派生。重分配关闭旧节点并新增节点，保留来源、原因和顺序；不设置 `current=true`，不覆盖旧责任。

Assignment 不是任务实体。公开池、手工分配、自主认领和重分配都在事务内创建 INITIAL ResponsibilityUnit；不创建 AssignmentTask。

### 4.3 并发

认领、分配和重分配先对 V2 Case 行加锁，再检查初始切片完成事实和当前 INITIAL 责任。Diagnosis 的内容保存使用 `version` 条件更新；模板版本发布只允许草稿版本，发布后不可更新。这样同一病例的并发认领只有一个成功，旧编辑不会静默覆盖新编辑。

### 4.4 动态内容

Diagnosis 保留 `structured_data`、`microscopic_description`、`diagnosis_text` 和 `comment` 四类内容。模板 schema 只描述动态组件和校验信息，核心关系、责任和并发字段仍使用结构化列。生成文本若由前端或模板产生，只作为可编辑建议，不会锁定 `diagnosis_text`。

### 4.5 权限映射

当前 P14 权限目录没有为 I03 单独拆分五个新编号，因此本实现使用以下共享能力映射：

| I03 语义权限 | P14 权限 | 用途 |
|---|---|---|
| DIAGNOSIS_VIEW | P14-PERM-055 | 诊断/审核队列与工作区查询 |
| DIAGNOSIS_INITIAL / REVIEW / AUDIT | P14-PERM-034 | 分配、接管、提交和确认诊断；具体角色仍由责任链和当前医生校验 |
| DIAGNOSIS_ASSIGN / REASSIGN | P14-PERM-034 | 建立或变更诊断责任 |
| TEMPLATE_MANAGE | P14-PERM-042 | 发布医院配置版本；模板编辑只写 DiagnosisTemplate 所有表 |

这是共享权限基础设施的能力映射，不是把 Legacy DiagnosisTask 当作 V2 责任来源。后续 P14 若拆分更细权限，只需调整映射，不改变 V2 领域模型。

## 5. 待确认与风险

- 当前仓库没有可供 V2 直接引用的 User/Doctor 主数据表。I03 对 `doctor_id` 执行非空和责任一致性校验；真正的主体存在性和资格范围由共享 identity-access 适配器接入时补强，标记为“待业务确认”。
- Report、TechnicalOrder、DigitalSlide 尚未进入 V2 源模型，因此工作区返回占位信息，不伪造这些领域事实。
- 本阶段保留显式保存，不实现自动保存；前端使用 Diagnosis `version` 提交，冲突时要求重新加载。
