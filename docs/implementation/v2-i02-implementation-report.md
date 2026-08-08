# V2-I02 实施报告

文档状态：已实施，待提交收口

实施基线：`c1b417d`（V2-I01A 收口完成）

## 1. V2-I02 Gate

| Gate | 结果 | 证据 |
|---|---|---|
| Domain | PASS | Grossing、GrossingSpecimen、统一 Block、统一 Slide、SlideRule、PrintRule 领域对象；3 个领域测试覆盖 OPEN/COMPLETED/reopen、事实完成、软删除和编号规则 |
| Database | PASS | `V13__v2_i02_material_production_chain.sql`；全部新增表位于 `pis_v2`；没有 Legacy FK；PostgreSQL 迁移和 I02 seed 验证通过 |
| Backend | PASS | 3 个 I02 Web 测试覆盖完成幂等、重开增量生成、Block 改号联动、软删除、Slide 完成和打印失败保留材料；后端全量 45/45 |
| Frontend | PASS | 独立 V2-I02 Grossing/Block 与 Slide/Material Tree 工作台；前端 7/7、typecheck、format、build 通过 |
| Architecture | PASS | V2 Architecture Drift Test 2/2；Module Boundary Test 通过；V2 禁止类型扫描包含 ProcessingTask、EmbeddingTask、PlannedBlock、ActualBlock、PlannedSlide、ActualSlide、PhysicalSlideInstance、SlideRework、BusinessRecord |
| Legacy Isolation | PASS | V2 I02 代码只依赖 V2 Registration、共享授权/审计/outbox 基础设施；不依赖 Legacy Task、Planned/Actual Block/Slide 或 Legacy 表 |

## 2. 已实施内容

1. Grossing 按 Case 建模，可关联多个 Specimen；完成和重开复用同一记录，不创建 GrossingVersion。
2. Block 使用统一实体，当前来源为 Case + Specimen + Grossing；Case 内 active `blockCode` 唯一，更新和软删除保留原记录。
3. Slide 使用统一实体；当前由 Block 按有效 SlideRule 生成，模型保留 Specimen/External 来源字段；Slide 在打印前已存在。
4. Grossing 完成命令在同一事务内补齐缺失 INITIAL Slide，写入 Audit 和 Outbox；重复命令或重复规则输出不会重复创建。
5. Block 改号会按规则同步 INITIAL Slide 编号；Block 软删除会使来源 Slide 失效，不覆盖历史完成事实。
6. PrintRule、PrintLog、PrintService 已分层；打印和重打使用同一材料实体，Fake printer 失败只写 FAILED PrintLog，不删除材料。
7. Material Tree 直接查询 Specimen、Block、Slide 关系，不创建 `material_tree` 真源表；返回 INITIAL required/completed projection 和 Slide 并发版本。
8. 提供 I02 命令 API、查询 API 和独立前端工作台；Legacy 页面默认不变，V2 工作台通过 `?workspace=v2` 展示。

## 3. API Gate

已实现：

- `POST /api/v2/cases/{caseId}/grossings`
- `PUT /api/v2/grossings/{grossingId}`
- `POST /api/v2/grossings/{grossingId}/specimens`
- `POST /api/v2/grossings/{grossingId}/blocks`
- `PUT /api/v2/blocks/{blockId}`
- `POST /api/v2/blocks/{blockId}/soft-delete`
- `POST /api/v2/grossings/{grossingId}/complete`
- `POST /api/v2/grossings/{grossingId}/reopen`
- `POST /api/v2/slides/{slideId}/complete`
- `POST /api/v2/slides/complete-batch`
- `POST /api/v2/blocks/{blockId}/print`
- `POST /api/v2/slides/{slideId}/print`
- `GET /api/v2/cases/{caseId}/materials`

所有写命令均通过应用服务授权；带有幂等键，变更命令使用 `expectedVersion` 或数据库唯一约束保护并发。

## 4. Test Evidence

| Check | Result |
|---|---:|
| Backend clean full suite | 45/45 passed |
| V2 material domain tests | 3/3 passed |
| V2 material Web tests | 3/3 passed |
| PostgreSQL Testcontainers integration tests | 3/3 passed |
| Frontend unit tests | 7/7 passed |
| Frontend typecheck | passed |
| Frontend format check | passed |
| Frontend production build | passed |
| Frontend lint | passed with 0 errors; 12 Vue template warnings |

测试数据全部为合成数据。PostgreSQL 测试使用 PostgreSQL 18.4；Flyway 对该版本输出“尚未测试支持、最新验证版本为 17”的工具警告，但迁移执行和断言均通过。

## 5. Domain Deviations

无已知偏离 P0–P09 和 V2-I01A 基线的领域模型实现。

以下是明确的实施边界，不视为已实现能力：

- `TECHNICAL_ORDER`、`FROZEN_ROUND`、`CYTOLOGY`、`EXTERNAL` 仅保留来源上下文扩展点，本增量不创建对应完整业务模块。
- 当前 PrintService 使用 Fake printer；真实 GK888/TSC/Zebra 等设备适配、异步设备队列和生产设备联调不在 I02 范围内。
- 当前前端工作台通过 query workspace 独立展示，未引入新的路由框架。

## 6. Legacy Debt

- Legacy P16/P17 仍保留原有 Task、计划/实际双模型和旧工作台，作为历史实现与后续迁移输入，不由 I02 修改。
- V2 尚未实现外部 Block/Slide 导入命令和外部来源适配器，仅完成模型字段预留。
- 真实打印设备、数字切片、技术医嘱、冰冻轮次和返工业务需要后续独立增量，经业务评审后实现。

## 7. P0/P1/P2

- P0：0
- P1：0
- P2：3 个边界待办，分别为真实打印设备适配、外部材料来源命令、独立前端路由化。

## 8. Git 收口

- 保持 stash `codex-preserve-pre-existing-work-before-v2-i01a-sync` 原样隔离，未恢复、未修改。
- 提交前执行 `git diff --check`、敏感信息扫描和工作树审查。
- 目标提交：`feat: implement V2-I02 material production chain`
