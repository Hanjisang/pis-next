# PIS-Next V2 Phase 1 Change Summary

文档状态：已完成
完成日期：2026-08-08
范围：P00-P03，含后续阶段设计入口

## 新增

- `docs/v2/README.md`：V2 文档入口、状态和阶段边界；
- `P00-current-system-audit.md`：当前仓库、工程资产、现有文档语义和 KEEP/REFACTOR/DELETE 分类；
- `P01-domain-model.md`：Case、Specimen、Grossing、Block、Slide、Diagnosis、Report、TechnicalOrder 等 V2 模型；
- `P02-domain-invariants.md`：54 条 V2 身份、来源、责任、报告、接口和治理不变量；
- `P03-module-boundaries.md`：模块所有权、依赖、聚合和模块化单体边界；
- `P04` 至 `P09`：第一阶段建立的数据、迁移、API、前端、测试和切换设计入口；本轮已由设计封版阶段完成为正式基线；
- `apps/backend-v2`、`apps/frontend-v2`、`tests/v2`：仅为空目录占位的 V2 隔离边界；现有运行代码仍在 `backend/` 和 `frontend/`。

## 重构定义

- 将现有 P05 文档视为设计资产和历史依据，不直接冻结为 V2 数据库或代码；
- 将“蜡块业务记录”重建为 `Block`；
- 将“计划玻片/实际玻片”重建为单一 `Slide`；
- 将报告生命周期和报告版本嵌套重建为“一次签发一个不可变 Report”；
- 将诊断任务/责任对象拆为 `Assignment`、`ResponsibilityChain` 和持续编辑的 `Diagnosis`；
- 将脱水、包埋、切片、染色、封片限定为 `TechnicalRecord`，默认不作为 Diagnosis 硬门槛；
- 将工作台状态限定为 Query/Projection，不建立 CaseStatus；
- 将迁移与领域设计隔离，当前不读取旧 PIS 数据。

## 删除或淘汰

本阶段没有删除历史文档或旧目录，以保持决策追溯；已明确下列概念不得进入 V2 核心实现：PlannedBlock、ActualBlock、PlannedSlide、ActualSlide、ReportVersion 嵌套、CaseStatus、Generic TechnicalResult 和实验室动作 Task 主流程。

## 验证

- 所需 `docs/v2/P00-P09` 文档和 README 均存在且非空；
- V2 禁用概念扫描通过：仅存在于禁止、审计、迁移和测试门禁语境；
- `git diff --check` 通过；
- 未发现 `sk-` 密钥、密码赋值或患者姓名；
- 当前没有修改现有 P15-P19 业务代码、数据库迁移、API、前端或生产配置；现有工程实现已在 P00 中完成审计。
- 后端非 Docker 测试 `28/28` 通过；完整 `verify` 的另外 2 个 PostgreSQL/Testcontainers 测试因当前 Docker Server 不可用而失败；
- 前端 `format:check`、`lint`、`typecheck`、`test:unit`（5/5）和 `build` 全部通过；
- Docker CLI 客户端可用，但 `dockerDesktopLinuxEngine` Server 管道不可用，未宣称容器集成验证通过。

## 剩余风险

- V2 仍处于设计阶段；现有 P15-P19 代码和测试可运行性不能证明 V2 领域正确；
- 第一阶段结束时 P04-P09 仅为计划入口；本轮封版后仍不是已完成的数据库、迁移、API、前端、测试或切换实现，正式设计基线见 P04-P09 和 design-freeze-report.md；
- 具体编号、模板、签发组合、技术医嘱阻断条件、保留期限和医院数据范围仍为“待业务确认”；
- 旧 PIS 数据映射必须在获得明确授权后，于隔离迁移工作区完成。
