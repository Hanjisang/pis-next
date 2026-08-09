# PIS Next

Next-generation Pathology Information System.

当前阶段：`PIS V2 CORE IMPLEMENTATION COMPLETE`。项目已进入 Site Integration & Production Readiness；医院配置、接口适配、设备适配、身份组织、历史迁移隔离和生产/切换计划的基础框架已建立。

本轮收官结果和未完成项见 [`docs/implementation/v2-final-implementation-report.md`](docs/implementation/v2-final-implementation-report.md)、[`docs/implementation/v2-final-e2e-report.md`](docs/implementation/v2-final-e2e-report.md)、[`docs/implementation/legacy-retirement-report.md`](docs/implementation/legacy-retirement-report.md) 与 [`docs/implementation/v2-runtime-readiness-report.md`](docs/implementation/v2-runtime-readiness-report.md)。Legacy 业务入口和已被 V2 替代的生产实现已退役；V1–V9 Flyway 迁移及可能承载历史医疗数据的表保留为历史迁移来源，未被物理删除。

Core 已完成不等于医院生产就绪。真实 HIS/LIS/EMR、LDAP/AD/SSO、打印机、扫描仪、电子签章、容量/灾备、历史数据正式迁移、Pilot、Cutover 和医院验收仍未验证。Site Foundation 文档入口为 [`docs/v2/site-integration/`](docs/v2/site-integration/)。

Windows 本地验证：

```powershell
$env:PIS_DB_PASSWORD = 'change-me-local-only'
.\scripts\verify.ps1
```

项目文档入口：[`docs/index.md`](docs/index.md)。
