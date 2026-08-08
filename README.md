# PIS Next

Next-generation Pathology Information System.

当前阶段：PIS V2 已成为主要业务入口，常规病理、技术医嘱循环、报告签发、数字切片材料绑定、归档借阅、全局查询、基础质控和统计已完成本地合成数据验证。

本轮收官结果和未完成项见 [`docs/implementation/v2-final-implementation-report.md`](docs/implementation/v2-final-implementation-report.md)、[`docs/implementation/v2-final-e2e-report.md`](docs/implementation/v2-final-e2e-report.md)、[`docs/implementation/legacy-retirement-report.md`](docs/implementation/legacy-retirement-report.md) 与 [`docs/implementation/v2-runtime-readiness-report.md`](docs/implementation/v2-runtime-readiness-report.md)。Legacy 业务入口和已被 V2 替代的生产实现已退役；V1–V9 Flyway 迁移及可能承载历史医疗数据的表保留为历史迁移来源，未被物理删除。

当前仍不可宣称核心业务完成或临床生产就绪：Frozen 多轮浏览器 E2E、Cytology/Molecular/Consultation/Supplemental 的完整浏览器 E2E，以及真实认证用户到 Doctor Identity 的映射仍属于 P1。真实医院接口、设备、电子签章、生产部署和医院验收均未验证。

Windows 本地验证：

```powershell
$env:PIS_DB_PASSWORD = 'change-me-local-only'
.\scripts\verify.ps1
```

项目文档入口：[`docs/index.md`](docs/index.md)。
