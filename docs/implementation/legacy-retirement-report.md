# Legacy Retirement Matrix

## 1. 分类规则

本矩阵不使用 `UNKNOWN`。医疗历史数据不因代码清理而删除；删除只针对已经被 V2 替代的失败业务实现和业务入口。

## 2. 资产矩阵

| Legacy 资产 | 分类 | 本轮动作 | 说明 |
|---|---|---|---|
| `backend/.../accession` | DELETE | 已删除生产 Java 包和旧 `/api/p15` Controller | V2 registration 已承担新写入。 |
| `backend/.../specimen` | DELETE | 已删除生产 Java 包和旧 `/api/p15` Controller | V2 specimen 已承担新写入。 |
| `backend/.../technical` | DELETE | 已删除旧 Grossing/Processing/TechnicalOrder Controller、Service、Domain、Repository | V2 material/technical loop 已替代，包含 ProcessingTask、EmbeddingTask、ActualBlockFormation 等对象。 |
| `backend/.../diagnosis` | DELETE | 已删除旧 `/api/p19` Controller、Service、Repository | V2 Diagnosis/Report 已替代旧报告流程。 |
| `frontend/src/api.ts` | DELETE | 已删除 P15–P19 API client | App 已无 active reference。 |
| `frontend/src/components/P15–P19*` | DELETE | 已删除旧工作台和 Legacy-only tests | V2 已成为默认正式入口。 |
| `scripts/p17-smoke.ps1`、`p18-smoke.ps1`、`p19-smoke.ps1` | DELETE | 已删除旧 smoke scripts，并从 verify 移除引用 | 旧路由删除后不再作为回归入口。 |
| Legacy-only Java tests | DELETE | 已删除 accession/specimen/technical/diagnosis 测试 | 同一业务不变量由 V2 测试覆盖。 |
| `backend/src/main/resources/db/migration/V1–V9` | RETAIN_AS_HISTORICAL_DATA | 不修改、不 DROP | 已发布 Flyway history 和潜在历史医疗数据来源必须保留；应用无新 Legacy route 写入。 |
| Legacy V1–V9 schema/table names | RETAIN_AS_HISTORICAL_DATA | 不做破坏性清理 | 后续若迁移真实历史数据，无法可靠转换的记录必须进入 MANUAL_REVIEW。 |
| `backend/.../integration` Outbox 与审计基础设施 | RETAIN_AS_INFRASTRUCTURE | 保留并由 V2 使用 | 这是共享可靠性基础设施，不是 Legacy 业务模型。 |
| `backend/.../security` 授权、审计和合成认证适配器 | RETAIN_AS_INFRASTRUCTURE | 保留并由 V2 使用 | Doctor Identity 的正式外部映射仍是 P1。 |
| 旧 V2 前的 stash `codex-preserve-pre-existing-work-before-v2-i01a-sync` | DELETE | 已审查后丢弃 | 内容为旧 Legacy/BCR 业务变更，不属于 V2 活动实现。 |
| `docs/implementation/p15–p19*` 设计文档 | RETAIN_AS_HISTORICAL_DATA | 保留 | 用于历史决策、迁移和审计，不作为 V2 运行依赖。 |

## 3. 最终指标

- Legacy Business Dependency：0 active production reference。
- Legacy Production Route：0（`/api/p15`–`/api/p19` Controller 已删除）。
- Obsolete Domain Type：0 active V2 usage；历史 migration SQL 中的名称按历史数据分类保留。
- Temporary Compatibility Code：0 active V2 bridge/legacy fallback。
- Legacy-only Business Test：0。
- Legacy Frontend Warning：0；前端 lint 无 error/warning。
- Legacy tables physically dropped：0，符合历史医疗数据不可破坏性清理规则。

## 4. 迁移警告

当前没有真实 Legacy 数据迁移 fixture 可供 reconciliation。不能把“没有 fixture”写成“迁移已通过”；真实历史数据接入时必须逐对象核对 Case、PathologyNo、Specimen、Block、Slide、Diagnosis、Report 数量及关系，无法可靠转换的记录标记 `MANUAL_REVIEW`。
