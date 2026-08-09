# PIS V2 最终实施报告

## 1. 结论

本报告记录 Final Readiness Closure 的收口结果。实施基线仍为 `31c4793 + P00–P09`，V2-I01–I05 及收官阶段的领域决定保持不变；本轮只补齐运行时验证、认证身份映射、浏览器闭环和文档。

## 2. Final Gate

| Gate | 结论 | 证据摘要 |
|---|---|---|
| Gate A Frozen | PASS | 单轮、双轮；签发前新增材料留在当前轮；签发后新增材料创建下一轮；每轮独立 Diagnosis/Report；Frozen End 创建唯一 Routine Case 且重复执行幂等。 |
| Gate B Business Types | PASS | Routine、Frozen、Cytology、独立 Molecular、TechnicalOrder Molecular、Consultation/External Block→Local Slide、Supplemental Report 均完成浏览器闭环。 |
| Gate C Digital / Archive / Search / QC | PASS | DigitalSlide 绑定/改绑、归档、借阅归还、全局搜索、QC 事实评估和基础统计均完成浏览器验证。 |
| Gate D V2 Integration | PASS | V2 为主要入口；真实登录用户映射 DoctorIdentity；DOC-A/DOC-B/DOC-C 责任链正确；登记员/技术员不能签发。 |
| Gate E Full E2E | PASS | 15 个浏览器回归场景完成，业务结果、PDF、责任链、材料关系和去向均有断言。 |
| Gate F Legacy Retirement | PASS | Legacy 业务生产路由和 V2 生产依赖为 0；历史 Flyway 和历史数据来源保留，不做破坏性删除。 |

## 3. 核心业务闭环

### 3.1 已实现 BusinessType

- `ROUTINE`
- `FROZEN`
- `CYTOLOGY`
- `MOLECULAR`
- `REFERRAL`/Consultation 和 Send-out 基础能力作为配置和材料来源适配，不建立平行病例模型。

### 3.2 核心领域对象

Case、BusinessType、Application、PathologyNo、Specimen、Grossing、Block、Slide、SlideRule、FrozenRound、FrozenRoundSpecimen、Frozen End 关系、Diagnosis、DiagnosisTemplate、Responsibility、Assignment、TechnicalProject、TechnicalOrder、TechnicalOrderItem、TechnicalOrderTarget、TechnicalOrderOutput、TechnicalOrderItemResult、ReportTemplate、ReportTemplateVersion、Report、Report PDF、MolecularResult、外部材料、SendOut、DigitalSlide、ArchiveLocation、ArchiveHistory、Loan、LoanItem、Destruction、QCRule、QCEvaluation、Statistics，以及 `auth_user`、`auth_user_permission`、`doctor_identity`。

V2 生产业务的 Source of Truth 为 `pis_v2`。Case 生命周期仍只有 `ACTIVE/CANCELLED`；病例展示状态由下级业务事实投影得出。

### 3.3 命令、查询和工作台

命令边界覆盖：登记建案/标本、Grossing/Block/Slide、制片完成、FrozenRound/Frozen End、Diagnosis 认领/分配/完成、TechnicalOrder 创建/执行/结果录入、Report Preview/Sign-out/Withdraw/Re-sign/Supplemental、Molecular Result、Consultation External Block/Local Slide、DigitalSlide bind/rebind、Archive/Loan/Return/Destruction、QC 评估和认证登录。

查询边界覆盖：Case/Specimen/Material Tree、Diagnosis Workspace、Technical Workbench、Report History/PDF、Frozen Workspace、DigitalSlide、Custody、Global Search、QC/Statistics 和认证当前身份。

前端工作台覆盖：工作台、登记、取材/制片、诊断、冰冻、技术医嘱、报告、归档借阅、数字切片、查询、质控统计，以及配置/系统管理入口。

## 4. 运行时治理

`Authenticated User → DoctorIdentityResolver → DoctorIdentity` 已成为统一边界。真实合成账号 `doctor-a`、`doctor-b`、`doctor-c` 映射为 `DOC-A`、`DOC-B`、`DOC-C`；责任节点、审核、签发和审计均使用该医疗人员引用。认证账号只通过环境变量注入合成密码，Flyway 不保存密码明文。

QC 只评价事实并产生 `NORMAL/WARNING/OVERDUE/ABNORMAL` 结果；浏览器已验证 QC 提醒默认不阻断签发。

## 5. 业务闭环统计

- BusinessType：4 个核心类型，另有 Consultation/Send-out 基础适配。
- QC 规则：Routine TAT、Frozen TAT、Report Withdraw Rate、Slide Reprint Rate，共 4 类。
- 内置统计：registration、specimen、grossing、block、slide、Diagnosis INITIAL/REVIEW/AUDIT、Report sign-out、Frozen、TechnicalOrder 等责任事实指标。
- 浏览器 E2E：15 个场景。
- Frontend unit：6 个测试文件、8 个测试。
- Backend：32 个测试。
- Legacy 业务生产路由：0。
- V2 → Legacy 业务依赖：0。

## 6. Domain Deviations

None。没有重新引入 `CaseStatus`、`BusinessRecord`、Legacy Task Workflow、`ReportVersion`、Frozen 平行材料实体、`TechnicalSlide` 或 `DiagnosisTask`。

## 7. 剩余问题分级

### P0 Core Runtime

0。

### P1 Core Runtime

0。

### P1 Site Integration

以下项目属于下一阶段 Site Integration / Production Readiness，不阻断 Core Completion：真实 HIS/LIS/EMR 接口、真实打印机、真实扫描仪/WSI 平台、CA/电子签章、生产部署、真实组织目录/LDAP/OIDC、历史医疗数据正式迁移和医院 Pilot/Cutover。

### P2

不扩展本轮核心范围的个性化报表、绩效、科研、AI、高级 WSI 阅片器和医院特殊接口。
<!-- End of report -->
