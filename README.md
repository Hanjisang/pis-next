# PIS Next

Next-generation Pathology Information System.

当前阶段：P17 组织处理与包埋已实现，包含处理任务/批次、程序版本快照、原始执行事实、人工确认、异常恢复、包埋和实际蜡块形成。详见 [`docs/implementation/p17-implementation-scope.md`](docs/implementation/p17-implementation-scope.md) 与 [`docs/reviews/p17-consistency-review.md`](docs/reviews/p17-consistency-review.md)。

当前阶段：P16 取材与蜡块已完成，等待启动下一正式阶段。

工程基础说明见 [`docs/engineering/p13-engineering-foundation.md`](docs/engineering/p13-engineering-foundation.md)，关闭审查见 [`docs/reviews/p13-consistency-review.md`](docs/reviews/p13-consistency-review.md)。

Windows 本地验证：

```powershell
$env:PIS_DB_PASSWORD = 'change-me-local-only'
.\scripts\build.ps1
```
# P15 登记与标本接收

P15 已实现登记、病例建立、预计标本登记、扫码接收、审计、发件箱与追溯验证。详见 [`docs/implementation/p15-implementation-scope.md`](docs/implementation/p15-implementation-scope.md) 和 [`docs/reviews/p15-consistency-review.md`](docs/reviews/p15-consistency-review.md)。

# P16 取材与蜡块

已实现登记、标本接收和 P16 取材纵向切片，包括取材批次、取材记录、组织取样、计划蜡块/包埋盒和标签打印管理。当前仍不可用于临床生产；脱水、组织处理、包埋执行、制片、诊断和报告尚未实现。打印机生产协议、正式数据库平台和生产 IAM 仍待后续决策。
