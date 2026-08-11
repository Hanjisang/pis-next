# PIS V2 SRS V1.4 Business Completion Scope

本文件定义本轮“全业务功能一次性闭环实施”的范围。计数来自
`SRS-V14-V2-COVERAGE-MATRIX.md` 的逐条 `Scope` 和 `Current Status`，不是根据
Prompt 重新估算。

## 1. Scope definition

`BUSINESS` 指病理科人员、科室管理人员或医院业务人员在正常工作中直接操作、
查看、维护、产生的数据或功能，包括：

- 电子申请、登记接收、Case、标本、取材、蜡块、切片、技术生产、技术医嘱；
- 细胞、冰冻、诊断、阅片、报告、模板、档案、借阅、销毁、外送和危急值；
- QC、质量体系文档、人员、权限、排班、设备、试剂耗材、采购和科室空间；
- 统计、收入事实汇总、配置、区域会诊、患者报告发放、全局检索和审计；
- 数字切片归档的业务索引、绑定、查询和远程查看能力。

本轮不把以下内容计入强制业务闭环：

| Scope | 本轮处理方式 |
|---|---|
| EXTERNAL_PLATFORM | 只要求内部边界、适配器、模拟器、配置、可观察失败和审计；没有真实医院平台时不得声明生产联调。 |
| DATA_PLATFORM | 数据采集前置服务不属于本轮 BUSINESS 完成条件。 |
| AI | 只保留 Provider Port、适配器/模拟器和结果契约；不伪造临床模型验证。 |
| SECURITY_INFRA | CA/HSM/国密生产部署、等保和最终安全基础设施闭环不计入本轮。人员、权限和业务审计仍属于 BUSINESS。 |
| NFR | 性能压测、灾备演练和可用性最终验收不计入本轮 BUSINESS 完成条件。 |

AM（数据迁移）保留在 BUSINESS_SCOPE，因为它是 V2 上线所需的业务数据迁移、
校验、映射和历史查询能力；旧系统只作为迁移数据源，不成为 V2 主域模型。

## 2. Group mapping

| Requirement groups | Scope | Reason |
|---|---|---|
| A–AC | BUSINESS | 病理核心、生产、诊断、报告、管理和配置能力。 |
| AD | EXTERNAL_PLATFORM | HIS/LIS/PACS/EMR/院内系统接口边界。 |
| AE | SECURITY_INFRA | CA/无纸化签名的真实基础设施边界；业务侧签发能力仍需可用模拟器。 |
| AF | EXTERNAL_PLATFORM | 省、市平台真实连接边界。 |
| AG | DATA_PLATFORM | 数据采集前置服务。 |
| AH | BUSINESS | 数字切片归档、索引、绑定和业务查看；厂商扫描器/格式仍可标记外部依赖。 |
| AI | AI | AI provider/model 能力。 |
| AJ–AK | BUSINESS | 区域会诊、患者报告查询和院内/患者发放业务。 |
| AL | SECURITY_INFRA | 安全基础设施、密钥管理、备份恢复和安全事件最终闭环。 |
| AM | BUSINESS | 业务历史数据迁移和可追溯历史查询。 |
| NFR | NFR | 非功能目标。 |

## 3. Baseline before implementation

```text
BUSINESS_TOTAL=592
BUSINESS_COMPLETE_BEFORE=256
BUSINESS_PARTIAL_BEFORE=161
BUSINESS_MISSING_BEFORE=158
BUSINESS_EXTERNAL_BEFORE=17
```

基线严格覆盖率（仅 COMPLETE）为 `256 / 592 = 43.2%`。
将已有外部依赖行计入“已有覆盖但未生产验证”的宽口径为
`(256 + 17) / 592 = 46.1%`；这不等同于生产环境验证。

基线中属于 BUSINESS 的外部依赖行是：

`I07, I08, K12, M21, N14, N25, AH01, AH03, AH04, AH05, AH06, AH07,
AK01, AK02, AK05, AK06, AK09`。

## 4. Completion rules

1. 只有同时具备业务规则、持久化、应用/API、用户入口、权限、审计和相关测试，才可标记 `COMPLETE`。
2. 只有后端或只有 UI/fixture 的实现标记为 `PARTIAL`，不能因为页面可显示而完成。
3. 真实外部系统不存在时，内部适配器链必须达到 `ADAPTER READY` 和 `SIMULATOR VERIFIED`，并保留 `EXTERNAL_DEPENDENCY`。
4. 不能为覆盖 SRS 引入 `GenericWorkflow`、`GenericTask`、持久化 `WorkItem`、巨型 Case 状态机、`ReportVersion` wrapper 或平行材料实体。
5. `BUSINESS_MISSING=0` 且 `BUSINESS_PARTIAL=0` 才允许发布业务完成声明；外部依赖必须逐条列出，不得伪造生产验证。

## 5. Recalculation contract

最终矩阵必须重新扫描全部 BUSINESS 行，并输出：

- `BUSINESS_COMPLETE`
- `BUSINESS_PARTIAL`
- `BUSINESS_MISSING`
- `BUSINESS_EXTERNAL_DEPENDENCY`
- 严格覆盖率和依赖就绪覆盖率

本文件和最终 closure 文档中的数字必须来自同一份最终矩阵。
