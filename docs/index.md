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

当前项目已完成 P04 核心业务场景、P05 病理类型覆盖受控修正和 P06 全病理业务流程设计。原有效 P0 40 项继续作为历史基线，新增12项范围扩展决策已正式归档。

当前任务：`P07-ALL-MODALITY-FINAL` 已完成：108项正式异常、26项患者安全关键异常、36个流程、73个场景和P06入口追溯均已通过关闭审查。

当前状态：P07 已完成，等待启动 P08。不得在本批次提前创建 P08 对象状态设计、数据库、接口、页面或代码。

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

P05-001-FINAL 的原范围关闭审查、`P05-MODALITY-COVERAGE-CORRECTION-FINAL` 的范围扩展复审、P06-ALL-MODALITY-FINAL 和 P07-ALL-MODALITY-FINAL 的关闭审查均已通过。P07 已完成；本任务未进入 P08 对象状态、数据库、接口、页面、代码或部署设计。
