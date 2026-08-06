# PIS Next 文档中心

## 1. 文档用途

本目录用于保存 PIS Next 项目的业务需求、领域模型、工作流程、状态机、系统架构、数据库设计、接口设计、测试资料、部署运维资料和发布验收资料。

项目设计和开发必须以本目录中已经确认的文档为依据。

PIS Next 采用净室设计，不以任何旧 PIS 系统作为新系统设计输入。

---

## 2. 文档目录

| 目录 | 用途 |
|---|---|
| `project/` | 项目目标、范围、总体计划、进度记录和业务决策台账 |
| `requirements/` | 功能需求、非功能需求、需求优先级和追踪矩阵 |
| `domain/` | 病理业务术语、领域模型、聚合、不变量和对象关系 |
| `workflows/` | 常规病理、冰冻、技术医嘱、报告等业务流程 |
| `state-machines/` | 病例、标本、蜡块、切片、报告等对象状态机 |
| `architecture/` | 系统架构、模块边界、事务和安全架构 |
| `database/` | 数据库模型、数据字典、约束和索引设计 |
| `contracts/` | 内部应用API、医院接口、事件、文件和契约追溯 |
| `api/` | 内部 API、医院接口和接口契约 |
| `testing/` | 测试策略、黄金场景、测试用例和测试报告 |
| `deployment/` | 安装、环境配置和生产参考架构 |
| `operations/` | 备份恢复、监控、故障排查和发布回滚 |
| `decisions/` | 架构决策、业务假设和待确认事项 |
| `reviews/` | 业务、架构、数据库、安全和代码审查结果，包括 P04 收尾审查 |
| `release/` | 发布说明、验收报告和已知限制 |

---

## 3. 文档状态

每份正式文档应在开头标明状态：

- `草稿`
- `待业务确认`
- `已确认`
- `已废弃`

建议使用以下文档头：

```text
文档状态：
文档版本：
创建日期：
最后更新：
负责人：
相关需求：
```

未经过业务确认的规则不得标记为“已确认”。

---

## 4. 规则优先级

项目规则发生冲突时，按以下优先级处理：

1. 患者安全和医疗数据完整性；
2. 根目录 `AGENTS.md`；
3. 已确认的业务不变量；
4. 已确认的业务流程和状态机；
5. 已批准的架构决策；
6. 功能需求；
7. 代码实现。

当代码与已确认文档不一致时，不得默认以代码为准，必须分析并修正冲突。

---

## 5. 文档编号原则

正式业务规则和需求应使用稳定编号，例如：

```text
REQ-PIS-001
NFR-PIS-001
INV-PIS-001
WF-PIS-001
SM-PIS-001
ADR-PIS-001
TEST-PIS-001
```

编号一旦被正式引用，不应重新分配给其他内容。

---

## 净室设计原则

PIS Next 不以任何旧 PIS 系统作为设计基线。

项目文档只记录：

- 当前确认的业务目标；
- 从零建立的病理领域模型；
- 已确认的业务规则；
- 状态机；
- 异常场景；
- 架构决策；
- 测试和验收依据。

旧系统材料不得存放在本仓库中。

如未来开展数据迁移或差异核对，应建立独立、隔离的迁移工作区，不得直接污染本项目领域设计。

---

## 7. 当前阶段

当前项目已完成 P04 核心业务场景、P05 病理类型覆盖受控修正、P06 全病理业务流程设计、P07 全病理异常场景矩阵、P08 全病理对象状态机、P09 全病理产品需求基线、P10 全病理系统架构设计、P11 全病理数据库设计、P12 全病理API与接口契约设计和 P13 工程基础初始化。原有效 P0 40 项继续作为历史基线，新增12项范围扩展决策已正式归档。

历史任务：`P11-ALL-MODALITY-DATABASE-DESIGN-FINAL` 已完成：89个逻辑实体和正式表、96个正式关系、1,164个逻辑列、878项逻辑数据库约束、160个索引，以及全量对象、聚合、不变量、需求、状态机、事件和Q追溯均已通过关闭审查。

历史任务：`P12-ALL-MODALITY-API-INTERFACE-CONTRACT-FINAL` 已完成：53个规范化Schema、70项内部应用能力、12项医院及外部业务接口、21个事件契约、6项文件能力、54项幂等写操作、27项预期版本操作和82个稳定错误代码，协议未确认时未生成OpenAPI/AsyncAPI。

当前任务：`P13-ENGINEERING-FOUNDATION-FINAL` 已完成：Java 21、Maven Wrapper 3.9.16、Spring Boot 4.1.0、Spring Modulith 2.1.0、Vue 3.5.40、Vite 8.1.0、PostgreSQL 18.4参考运行时、Docker、Compose、CI 和 Dependabot 均已建立并通过关闭审查。

当前状态：P13 已完成，下一阶段为 P14 用户权限与审计。P13 未提前实现后续业务功能。

P0 统计：原始标记 46；当前有效 40；已确认有效 40；待确认有效 0；非 P0 原则/配置项 1；合并并由既有决策覆盖 5；DB-P0-01 当前有效 P0 7、已确认 7、待确认 0，DB-P0-02 当前有效 P0 7、已确认 7、待确认 0，DB-P0-03 当前有效 P0 6、已确认 6、待确认 0，DB-P0-04 当前有效 P0 6、已确认 6、待确认 0，DB-P0-05 当前有效 P0 8、已确认 8、待确认 0，DB-P0-06 当前有效 P0 4、已确认 4、待确认 0，DB-P0-07 当前有效 P0 2、已确认 2、待确认 0。

P03 术语文档入口：

- `domain/glossary.md`：病理业务术语表；
- `domain/terminology-rules.md`：术语使用规则。

P05 核心领域模型入口：

- `domain/p05-design-rules.md`：P05 统一设计规则与完成门禁；
- `domain/core-object-catalog.md`：43 项历史基线及 OBJ-044 至 OBJ-063 病理类型覆盖修正对象；
- `domain/domain-relationships.md`：核心领域关系和来源链；
- `domain/aggregate-boundaries.md`：18 个聚合及跨聚合一致性边界；
- `domain/domain-invariants.md`：70 条正式业务不变量；
- `domain/p05-traceability.md`：场景、决策、Q 编号与模型追溯矩阵；
- `reviews/p05-consistency-review.md`：P05 核心领域模型一致性与关闭审查。

病理类型覆盖场景入口：

- `workflows/cytology-scenarios.md`：8 个细胞病理场景；
- `workflows/molecular-and-referral-scenarios.md`：6 个分子病理与外送检测场景；
- `workflows/core-scenario-catalog.md`：59 个历史场景与 14 个追加场景的统一目录。

P06 全病理业务流程入口：

- `workflows/p06-process-design-rules.md`：P06 流程设计规则和阶段边界；
- `workflows/p06-process-catalog.md`：36项正式流程目录；
- `workflows/p06-common-processes.md`：共性流程 P06-PROC-001～005；
- `workflows/p06-histology-frozen-processes.md`：组织与术中冰冻流程 P06-PROC-006～014；
- `workflows/p06-cytology-processes.md`：细胞病理流程 P06-PROC-015～022；
- `workflows/p06-molecular-processes.md`：分子病理和外送流程 P06-PROC-023～030；
- `workflows/p06-cross-modality-processes.md`：多模态及支撑流程 P06-PROC-031～036；
- `workflows/p06-process-handoffs.md`：跨流程衔接和业务流图；
- `workflows/p06-process-traceability.md`：场景、决策、对象、聚合、不变量和Q追溯；
- `reviews/p06-consistency-review.md`：P06一致性与关闭审查。

P07 全病理异常场景矩阵入口：

- `workflows/p07-exception-design-rules.md`：P07异常设计规则和阶段边界；
- `workflows/p07-exception-catalog.md`：108项正式异常目录；
- `workflows/p07-exception-matrix.md`：异常触发、影响、阻断、处置、恢复和后续输入矩阵；
- `workflows/p07-critical-safety-exceptions.md`：26项患者安全关键异常；
- `workflows/p07-exception-handling-rules.md`：异常处置、补偿和恢复规则；
- `workflows/p07-exception-traceability.md`：P06入口、流程、场景、决策、对象、聚合、不变量和Q追溯；
- `reviews/p07-consistency-review.md`：P07一致性与关闭审查。

P08 全病理对象状态机入口：

- `state-machines/p08-state-machine-design-rules.md`：P08对象状态、任务、事实、版本和维度设计规则；
- `state-machines/p08-object-state-classification.md`：63个正式对象逐项分类；
- `state-machines/p08-state-machine-catalog.md`：31个正式状态机目录；
- `state-machines/p08-clinical-case-state-machines.md`：申请、病例、标本、责任、冰冻和细胞责任状态机；
- `state-machines/p08-material-technical-state-machines.md`：蜡块、玻片、数字材料、技术、细胞制备物和分子状态机；
- `state-machines/p08-diagnosis-report-state-machines.md`：诊断记录、报告生命周期和报告版本状态机；
- `state-machines/p08-integration-governance-state-machines.md`：出站、质量、纠错和恢复校验状态机；
- `state-machines/p08-cross-object-transition-rules.md`：跨对象转换守卫、聚合衔接和20个Mermaid状态图；
- `state-machines/p08-state-machine-traceability.md`：对象、聚合、流程、异常、SEV-1、场景、决策、不变量和Q追溯；
- `reviews/p08-consistency-review.md`：P08一致性与关闭审查。

P09 全病理产品需求基线入口：

- `requirements/p09-requirement-baseline-rules.md`：P09需求基线设计规则；
- `requirements/p09-requirement-catalog.md`：107项正式需求目录；
- `requirements/p09-common-functional-requirements.md`：共性功能需求；
- `requirements/p09-modality-functional-requirements.md`：各病理类型功能需求；
- `requirements/p09-cross-cutting-functional-requirements.md`：跨流程、报告、接口和治理需求；
- `requirements/p09-data-security-governance-requirements.md`：数据、安全、审计、归档和恢复需求；
- `requirements/p09-quality-attribute-requirements.md`：质量属性需求和参数登记；
- `requirements/p09-acceptance-baseline.md`：需求验收基线；
- `requirements/p09-requirement-traceability.md`：需求双向追溯矩阵；
- `reviews/p09-consistency-review.md`：P09全病理需求基线一致性与关闭审查。

P10 全病理系统架构入口：

- `architecture/p10-architecture-design-rules.md`：P10架构设计规则；
- `architecture/p10-architecture-drivers.md`：架构驱动因素与约束；
- `architecture/p10-system-context.md`：系统上下文与责任边界；
- `architecture/p10-module-boundaries.md`：模块、聚合和对象归属；
- `architecture/p10-component-dependency-rules.md`：组件依赖和调用规则；
- `architecture/p10-transaction-consistency.md`：事务与一致性策略；
- `architecture/p10-event-integration-architecture.md`：事件与集成架构；
- `architecture/p10-security-governance-architecture.md`：安全与治理架构；
- `architecture/p10-file-report-imaging-architecture.md`：文件、报告与数字材料架构；
- `architecture/p10-observability-continuity.md`：可观测性、连续性与恢复；
- `architecture/p10-architecture-traceability.md`：架构双向追溯矩阵；
- `architecture/adr/ADR-001.md`：总体架构风格；
- `architecture/adr/ADR-002.md`：模块与聚合归属；
- `architecture/adr/ADR-003.md`：事务和一致性策略；
- `architecture/adr/ADR-004.md`：领域事件及可靠投递；
- `architecture/adr/ADR-005.md`：身份、编号、版本和历史；
- `architecture/adr/ADR-006.md`：领域状态机与任务编排；
- `architecture/adr/ADR-007.md`：外部系统防腐层；
- `architecture/adr/ADR-008.md`：安全、授权、代理和强认证；
- `architecture/adr/ADR-009.md`：审计、质量事件和受控纠错；
- `architecture/adr/ADR-010.md`：报告、文件和数字切片；
- `architecture/adr/ADR-011.md`：可观测性、连续性和恢复；
- `reviews/p10-consistency-review.md`：P10全病理系统架构一致性与关闭审查。

P11 全病理数据库设计入口：

- `data/p11-database-design-rules.md`：数据库设计规则、逻辑类型和平台适配边界；
- `data/p11-persistence-classification.md`：63个领域对象持久化分类；
- `data/p11-logical-er-model.md`：89个逻辑实体和96个关系的ER模型；
- `data/p11-relational-schema.md`：89张产品中立关系表和数据归属；
- `data/p11-data-dictionary-core.md`：核心业务数据字典；
- `data/p11-data-dictionary-material-technical.md`：材料与技术数据字典；
- `data/p11-data-dictionary-diagnosis-report.md`：诊断、报告和文件数据字典；
- `data/p11-data-dictionary-integration-governance.md`：集成、治理、安全、归档和恢复数据字典；
- `data/p11-state-history-versioning.md`：31个状态机、状态历史、版本和不可变事实设计；
- `data/p11-constraints-invariants.md`：70条不变量和逻辑数据库约束矩阵；
- `data/p11-index-query-strategy.md`：索引和查询支撑策略；
- `data/p11-data-security-retention.md`：数据安全、敏感性、归档与保留设计；
- `data/p11-database-traceability.md`：数据库双向追溯矩阵和19项数据风险；
- `reviews/p11-consistency-review.md`：P11全病理数据库设计一致性与关闭审查。

P12 全病理API与接口契约入口：

- `contracts/p12-contract-design-rules.md`：P12契约设计规则和机器规范边界；
- `contracts/p12-contract-decisions.md`：接口风格、协议、版本、幂等和机器规范决策；
- `contracts/p12-canonical-contract-models.md`：53个规范化公共和事件Schema；
- `contracts/p12-internal-api-catalog.md`：15个模块、70项内部应用能力目录；
- `contracts/p12-command-api-contracts.md`：42项命令API契约；
- `contracts/p12-query-api-contracts.md`：18项查询API契约；
- `contracts/p12-hospital-interface-catalog.md`：14个外部适配器和12项正式业务接口；
- `contracts/p12-hospital-inbound-contracts.md`：3项医院入站接口契约；
- `contracts/p12-hospital-outbound-contracts.md`：4项医院出站接口契约；
- `contracts/p12-event-contracts.md`：21个P10架构事件的P12契约；
- `contracts/p12-file-imaging-contracts.md`：6项文件、报告和数字切片能力；
- `contracts/p12-idempotency-version-error-contracts.md`：幂等、并发、版本、兼容和82个错误代码；
- `contracts/p12-security-audit-contracts.md`：安全、责任、代理、服务身份和审计上下文；
- `contracts/p12-contract-traceability.md`：全量双向追溯和24项接口风险；
- `reviews/p12-consistency-review.md`：P12全病理API与接口契约一致性和关闭审查。

P13 工程基础入口：

- `engineering/p13-engineering-foundation.md`：工程边界、版本、15个模块、数据库、容器、脚本、追溯和假设；
- `reviews/p13-consistency-review.md`：P13环境、工程、验证、安全和范围关闭审查。

P14 全病理身份与授权入口：

- `security/p14-authorization-design-rules.md`：22条P14授权安全设计规则和阶段边界；
- `security/p14-subject-identity-model.md`：人工、服务、任务、设备和外部系统主体模型；
- `security/p14-capability-permission-catalog.md`：70个内部API和12个外部接口的82项能力/权限目录；
- `security/p14-role-model.md`：21个业务角色来源、能力族和系统管理员边界；
- `security/p14-resource-action-scope-model.md`：资源、动作、对象状态和10个范围维度；
- `security/p14-organization-data-scope.md`：医院、院区、科室、工作组、队列、病例和用途范围；
- `security/p14-task-responsibility-authorization.md`：分配、接管、交接、代理、批准和复核责任；
- `security/p14-delegation-temporary-emergency.md`：角色/任务代理、临时授权和紧急授权；
- `security/p14-high-risk-controls.md`：16个高风险动作、增强认证和第二人复核；
- `security/p14-segregation-of-duties.md`：12条职责分离规则和受控例外；
- `security/p14-service-device-identities.md`：服务、后台任务、设备和外部系统身份边界；
- `security/p14-authorization-decision-model.md`：18类决策输入和7类授权结果；
- `security/p14-authorization-audit-evidence.md`：13类授权和审计证据；
- `security/p14-authorization-traceability.md`：模块、API、接口、事件、状态机、异常、需求和决策追溯；
- `reviews/p14-consistency-review.md`：P14全病理身份与授权一致性与关闭审查。

本次正式决策归档：

- `decisions/p05-modality-coverage-correction-final.md`：BD-P04-080 至 BD-P04-091 共 12 项范围扩展决策。

P04 决策收敛文档入口：

- `project/p0-decision-batch-plan.md`：P0 业务决策分批计划；
- `decisions/p0-decision-pack-01.md`：首批核心业务对象身份与来源链决策包；
- `decisions/lightweight-technical-workflow-principles.md`：轻量技术流程原则；
- `decisions/technical-workflow-model-correction.md`：技术流程模型修订记录；
- `project/controlled-business-correction-principles.md`：业务信息灵活修改与受控纠错原则。

P04 收尾文档入口：

- `reviews/p04-consistency-review.md`：全场景一致性审查报告；
- `reviews/p04-question-traceability.md`：原始待确认问题与决策项追溯；
- `project/p04-business-decision-backlog.md`：业务决策待确认台账。

P05-001-FINAL 的原范围关闭审查、`P05-MODALITY-COVERAGE-CORRECTION-FINAL` 的范围扩展复审、P06-ALL-MODALITY-FINAL、P07-ALL-MODALITY-FINAL、P08-ALL-MODALITY-FINAL、P09-ALL-MODALITY-REQUIREMENT-BASELINE-FINAL、P10-ALL-MODALITY-SYSTEM-ARCHITECTURE-FINAL、P11-ALL-MODALITY-DATABASE-DESIGN-FINAL、P12-ALL-MODALITY-API-INTERFACE-CONTRACT-FINAL、P13-ENGINEERING-FOUNDATION-FINAL 和 P14-ALL-MODALITY-AUTHORIZATION-SECURITY-FINAL 的关闭审查均已通过。P14 仅完成身份与授权安全设计；未进入 P15 业务实现。
