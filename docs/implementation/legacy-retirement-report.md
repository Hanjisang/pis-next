# Legacy Retirement Matrix

## 1. 分类规则

本矩阵不使用 `UNKNOWN`。Legacy 业务实现、历史迁移来源、共享基础设施和历史文档必须分别归类。历史医疗数据不得因为业务实现退役而删除。

## 2. 资产矩阵

| Legacy 资产 | 分类 | 当前结论 |
|---|---|---|
| Legacy accession/specimen/technical/diagnosis Java 业务包和 Controller | DELETE | 已删除；V2 Registration、Material、Technical、Diagnosis/Report 已成为生产入口。 |
| Legacy P15–P19 前端页面、store、API client、workflow component | DELETE | 已删除；V2 首页和一级导航为正式入口。 |
| Legacy-only tests 和 p17/p18/p19 smoke scripts | DELETE | 已删除；V2 业务不变量由 V2 测试覆盖。 |
| ProcessingTask、EmbeddingTask、ActualBlockFormation、Planned/Actual Block/Slide 等失败业务模型 | DELETE | 不存在于活动 V2 生产模型；V2 使用 Block/Slide 独立事实和来源上下文。 |
| 旧报告 Content/Version、旧 Diagnosis workflow、旧 CaseStatus workflow | DELETE | 已被 V2 Report/Diagnosis/Projection 替代。 |
| V1–V9 Flyway migration 和历史 schema/table 名称 | RETAIN_AS_HISTORICAL_DATA | 保留 migration history 和潜在历史数据来源；不重写 checksum，不作为 V2 新写入入口。 |
| Outbox、Audit、权限、数据库、测试容器等共享基础设施 | RETAIN_AS_INFRASTRUCTURE | 保留并由 V2 使用，不属于 Legacy 业务依赖。 |
| 旧设计文档和历史实现报告 | RETAIN_AS_HISTORICAL_DATA | 仅用于历史决策、审计和迁移参考，不作为 V2 运行时依据。 |
| `codex-preserve-pre-existing-work-before-v2-i01a-sync` stash | DELETE | 当前 `git stash list` 为空；未执行 `stash pop`，未把其中内容重新引入 V2 main。 |

## 3. 最终指标

- Legacy Business Dependency：0 active production reference。
- Legacy Production Route：0。
- Obsolete Domain Type：0 active V2 domain type。
- Temporary Compatibility Code：0 active V2→Legacy bridge/fallback。
- Legacy-only Business Test：0；保留的旧名字符串只出现在 V2 的负向架构守卫断言中。
- Legacy Frontend Warning：0；frontend lint 0 errors / 0 warnings。
- Legacy tables physically dropped：0；这是有意的历史数据保护策略，不代表存在 Legacy 新写入。

## 4. 数据迁移边界

当前仓库没有可供本轮正式 reconciliation 的真实 Legacy medical-data fixture。本轮不声称历史迁移已完成；后续迁移必须逐项核对 Case、PathologyNo、Specimen、Block、Slide、Diagnosis、Report，无法可靠转换的记录必须标记 `MANUAL_REVIEW`，不得猜测或静默丢弃。

## 5. 复核结果

Gate F 仍为 **PASS**。V2 生产源代码、前端和路由均未恢复 Legacy 业务依赖；历史 migration 和历史数据保护边界保持不变。
<!-- End of report -->
