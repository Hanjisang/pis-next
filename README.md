# PIS Next

Next-generation Pathology Information System.

当前阶段：P18 技术医嘱已完成，覆盖深切、重切、白片、IHC、特殊染色等技术项目的开立、目标、计划产物、审核/受理/责任交接、结果引用和追溯管理。详见 [`docs/implementation/p18-implementation-scope.md`](docs/implementation/p18-implementation-scope.md) 与 [`docs/reviews/p18-consistency-review.md`](docs/reviews/p18-consistency-review.md)。下一阶段 P19 尚未启动。

P15 登记与标本接收、P16 取材与蜡块、P17 组织处理与包埋已完成。P18 不实现实际切片、实际染色/封片、设备运行、技术质控、数字切片、诊断或报告；当前系统不可用于临床生产。

工程基础说明见 [`docs/engineering/p13-engineering-foundation.md`](docs/engineering/p13-engineering-foundation.md)，项目导航见 [`docs/index.md`](docs/index.md)。

Windows 本地验证：

```powershell
$env:PIS_DB_PASSWORD = 'change-me-local-only'
.\scripts\verify.ps1
```
