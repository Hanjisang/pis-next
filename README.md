# PIS Next

Next-generation Pathology Information System.

当前阶段：P13 工程基础初始化已完成。

工程基础说明见 [`docs/engineering/p13-engineering-foundation.md`](docs/engineering/p13-engineering-foundation.md)，关闭审查见 [`docs/reviews/p13-consistency-review.md`](docs/reviews/p13-consistency-review.md)。

Windows 本地验证：

```powershell
$env:PIS_DB_PASSWORD = 'change-me-local-only'
.\scripts\build.ps1
```
# P15 登记与标本接收

P15 已实现登记、病例建立、预计标本登记、扫码接收、审计、发件箱与追溯验证。详见 [`docs/implementation/p15-implementation-scope.md`](docs/implementation/p15-implementation-scope.md) 和 [`docs/reviews/p15-consistency-review.md`](docs/reviews/p15-consistency-review.md)。
