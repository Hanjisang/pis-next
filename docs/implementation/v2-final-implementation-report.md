# PIS V2 最终实施报告

## 1. 报告范围

本报告记录 `013bbc1 feat: implement V2-I05 report sign-out loop` 之后的 V2 收官实施。业务基线仍为 `31c4793 + P00–P09`，本轮不把 Legacy 领域模型重新引入 V2。

## 2. Gate 结论

| Gate | 结论 | 证据摘要 |
|---|---|---|
| Gate A Frozen | CONDITIONAL | FrozenRound、Frozen 材料上下文、Round 制片完成、快速诊断、独立 Report 签发和 Frozen End → 唯一 Routine Case 的后端测试通过；浏览器已完成一轮从材料、诊断、预览、签发到 Frozen End 的闭环；多轮浏览器场景仍待补齐。 |
| Gate B Other Business Types | CONDITIONAL | ROUTINE、CYTOLOGY、FROZEN、MOLECULAR、Consultation、Send-out 的 V2 API/领域测试通过；四类业务的统一模型适配已落地，但 Cytology/Molecular/Consultation 的完整浏览器签发链尚未完成。 |
| Gate C Digital / Archive / Search / QC | PASS | 数字切片绑定/改绑、全局搜索、归档位置、批量归档、借阅/归还、QC 事实评估和基础统计均有 V2 API、前端工作区和合成数据浏览器验证。 |
| Gate D V2 Integration | CONDITIONAL | V2 已成为默认首页和主要业务入口，常规病例及技术医嘱循环已通过真实浏览器；登录和按真实角色的身份系统尚未接入。 |
| Gate E Full E2E | CONDITIONAL | 常规、技术循环、撤回/重签、单轮 Frozen 签发/转常规、数字切片、归档/借阅已通过浏览器；Frozen Round 2+、Cytology、Molecular、Consultation 和 Supplemental 的浏览器场景仍待补齐。 |
| Gate F Legacy Retirement | PASS（历史迁移保留） | Legacy 生产 Controller、Service、Domain、DTO/API、前端旧工作台、Legacy-only 测试和旧 smoke 脚本已删除；V1–V9 迁移历史及可能承载历史数据的表未物理删除，应用层已无其生产写入口。 |

## 3. 已实现的核心能力

### 3.1 领域对象

当前活动业务 Source of Truth 为 `pis_v2`，核心对象包括：

1. Case、BusinessType、ApplicationItemMapping、PathologyNo。
2. Specimen、Grossing、Block、Slide、SlideRule。
3. FrozenRound、FrozenRoundSpecimen、Frozen End 关系。
4. Diagnosis、DiagnosisTemplate、Responsibility、Assignment。
5. TechnicalProject、TechnicalOrder、TechnicalOrderItem、TechnicalResult。
6. ReportTemplate、ReportTemplateVersion、Report、Report PDF、撤回/补充/重签关系。
7. MolecularResult、Consultation 外部材料、SendOut。
8. DigitalSlide、ArchiveLocation、ArchiveHistory、Loan、LoanItem、Destruction。
9. QCRule、QCEvaluation、统计 Projection、报表扩展注册点。

### 3.2 V2 API 能力

V2 已提供登记、标本、取材、蜡块、切片、冻结、诊断责任、技术医嘱、报告签发、分子结果、会诊材料、外送结果、数字切片、材料去向、搜索、QC、统计和扩展点的命令/查询边界。关键写操作沿用幂等键、审计、Outbox 和并发版本控制。

### 3.3 前端工作区

正式入口为 V2 首页，一级导航已接入：工作台、登记、取材/制片、诊断、冰冻、技术医嘱、报告、归档借阅、数字切片、查询、质控统计、配置和系统管理。

用户界面使用待诊病例、初诊医生、审核医生、技术医嘱、切片、蜡块和冰冻轮次等业务语言，不要求用户理解 Projection、Aggregate 或 Outbox。

## 4. 统计

- BusinessType：ROUTINE、CYTOLOGY、FROZEN、MOLECULAR，另有 Consultation/Send-out 基础适配。
- 主要 V2 工作区：登记、材料生产、诊断、技术医嘱、冻结、数字切片、归档借阅、QC/统计、首页搜索。
- QC 规则：Routine TAT、Frozen TAT、Report Withdraw Rate、Slide Reprint Rate。
- 内置统计：登记、业务类型、Grossing、Block、Slide、INITIAL/REVIEW/AUDIT 责任事实、签发、Frozen、TechnicalOrder、TAT。
- Legacy 生产业务对象删除：已删除 Legacy accession/specimen/technical/diagnosis Java 生产包及旧 P15–P19 前端工作台。
- Legacy 路由删除：`/api/p15`、`/api/p16`、`/api/p17`、`/api/p18`、`/api/p19` Controller 已删除。
- Legacy 表物理退役：0；原因是 V1–V9 属于已发布迁移历史，不能在未知历史数据状态下 DROP。

## 5. Domain Deviations

1. 原设计要求每一类核心业务均完成浏览器级完整签发链；当前实现已完成 Routine/Technical/Report 主链和 Gate C 浏览器链，其他业务类型仍以 API/领域测试为主。原因是本轮尚未完成各业务类型的专用浏览器操作面板。影响：Gate B/E 为 CONDITIONAL。
2. Frozen 专用诊断和 Report 已通过 FrozenRound context workspace 进入统一诊断/报告工作区，单轮浏览器签发和 Frozen End 已验证；尚未在浏览器中完成多轮材料在签发前/后的自动分流场景。影响：Gate A/E 仍为 CONDITIONAL。
3. Current Auth User → Doctor Identity 的正式认证边界尚未接入，合成环境仍使用配置 actor `p15-local-registration-actor`。影响：权限和责任事实已验证，真实登录/组织身份尚未验证。
4. V1–V9 Legacy Flyway migration 保留为历史迁移链，不作为活动 V2 业务依赖。影响：历史 schema 名称仍可在迁移文件和数据库历史中出现，但无 Legacy 生产写入口。

## 6. 剩余问题

### P1

1. 在浏览器中补齐 Frozen Round 2+ 场景，验证签发前材料留在当前轮、签发后材料创建新轮，以及多轮独立签发。
2. 为 Cytology、Molecular 独立 Case、Molecular TechnicalOrder、Consultation/External Material、Send-out 和 Supplemental Report 增加完整浏览器 E2E。
3. 接入真实认证用户到 Doctor Identity 的映射，并在浏览器中验证角色菜单与责任授权。

### P2

1. 将配置、系统管理和外挂报表扩展点补成专用前端页面。
2. 增加搜索结果的更细粒度落点和 QC 统计筛选；不改变 QC 默认不阻断签发规则。
3. 评估 Flyway 对 PostgreSQL 18.4 的正式兼容版本；当前仅有工具版本提示。

P0 = 0。由于仍存在 P1，当前不得宣告 `PIS V2 CORE IMPLEMENTATION COMPLETE`。
