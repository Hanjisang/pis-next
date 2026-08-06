# PIS Next

Next-generation Pathology Information System.

当前阶段：P13 工程基础初始化已完成。

工程基础说明见 [`docs/engineering/p13-engineering-foundation.md`](docs/engineering/p13-engineering-foundation.md)，关闭审查见 [`docs/reviews/p13-consistency-review.md`](docs/reviews/p13-consistency-review.md)。

Windows 本地验证：

```powershell
$env:PIS_DB_PASSWORD = 'change-me-local-only'
.\scripts\build.ps1
```
