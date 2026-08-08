# V2-I04 构建与复用决策

文档状态：V2-I04 实施完成

实施基线：V2-I03 完成后的 `main`；本阶段继续以 P00-P09 和已确认的 V2-I02/I03 设计为基线。

## 1. 决策总览

| 对象 | 决策 | 边界与原因 |
|---|---|---|
| TechnicalProject | NEW BUILD | 保存医院范围内的项目配置、目标类型、输出类型、参数/结果 schema、费用和签出阻断配置；配置版本进入订单项目快照 |
| TechnicalOrder | NEW BUILD | 聚合 Diagnosis、Case、订单编号、阻断标记、取消事实和并发版本；状态由真实 output/result 事实投影 |
| TechnicalOrderItem | NEW BUILD | 一个订单可有多个项目；每个项目独立保存数量、参数、配置快照和目标集合 |
| TechnicalOrderTarget | NEW BUILD | 一个项目可有多个 Case/Specimen/Block/Slide 目标；服务端校验目标存在、目标类型支持和病例一致性 |
| StructuredResult | NEW BUILD | 使用 `technical_order_item_result`，结果属于 Item，保留 schema 快照、结果版本和录入事实 |
| TechnicalOrderOutput | NEW BUILD | 只记录 Item 到正式 Grossing/Block/Slide/Result 的实际输出关联，不承载通用业务状态 |
| V2 Grossing/Block/Slide | REUSE | 复用 I02 正式领域对象和仓储；补充取材先创建 `Grossing.TECHNICAL_ORDER`，再创建正式 Block/Slide |
| V2 Material Tree | REUSE | 复用 I02 `findMaterialTree` 只读投影；INITIAL 输出不被 TechnicalOrder 输出替换 |
| V2 Diagnosis/Responsibility | REUSE | 通过 I03 Diagnosis、当前责任医生和 Case 读取/校验，订单从 Diagnosis 发起 |
| Authorization/Audit/Outbox | REUSE | 复用已验证共享基础设施；I04 仅增加技术订单命令和结果的审计/事件 |
| Legacy ProcessingTask 等 | 不复用 | 不建立 Legacy 外键、不双写、不从旧状态机推导 V2 TechnicalOrder 状态 |

## 2. 明确禁止的漂移

I04 不创建 `TechnicalSlide`、`TechnicalBlock`、`GenericTechnicalResult`、`IhcWorkflow`、`DeepSectionWorkflow`、`SpecialStainWorkflow` 或任何把不同输出类型塞入通用技术结果的替代模型。正式材料仍归 I02 Material 所有；TechnicalOrder 只保存订单上下文和输出关联。

I04 也不复用 P18 的 planned/actual technical order 表。P18 旧表只作为历史实现边界，不是 V2 的领域来源。

## 3. 状态和输出规则

1. `PENDING` 表示订单项目尚无实际输出事实。
2. `EXECUTING` 表示至少产生了输出或结构化结果，但项目尚未满足配置要求。
3. `COMPLETED` 由每个项目的实际 Slide 完成、Block 输出存在和 StructuredResult 存在共同投影。
4. `CANCELLED` 只来自取消命令；取消不物理删除已生成的 Grossing、Block、Slide 或 Result。
5. `requiredBeforeSignOut` 只影响阻断投影；取消订单不再阻断，未完成的已配置订单继续阻断。
6. Technical Slide 使用 I02 `Slide`，其 `source_context_type=TECHNICAL_ORDER`、`source_context_id=technical_order_item.id`，规则编码使用项目编码。
7. 补充取材不回写 INITIAL Grossing；新 Grossing 的 `source_type=TECHNICAL_ORDER` 且 `source_reference_id=technical_order_item.id`。

## 4. API 边界

- `GET /api/v2/technical-projects`：读取启用项目配置。
- `POST /api/v2/technical-projects`：创建配置版本基础记录。
- `POST /api/v2/technical-orders`：从 Diagnosis 创建多项目、多目标订单。
- `POST /api/v2/technical-orders/{id}/execute`：生成正式输出事实。
- `POST /api/v2/technical-order-items/{id}/result`：创建或更新版本化结构化结果。
- `POST /api/v2/technical-orders/{id}/cancel`：取消订单并保留事实。
- `GET /api/v2/diagnoses/{diagnosisId}/technical-orders`、`GET /api/v2/technical-workbench`：提供诊断整合和独立工作台投影。

所有写命令都要求权限、幂等键和事务边界；病例一致性只在服务端判断，前端不能提交状态或绕过目标校验。

## 5. 待业务确认与限制

- 当前仓库没有 User/Doctor 主数据。I04 沿用 I03 的 `identity-access` 适配边界：只校验当前 actor 与 Diagnosis Responsibility 的一致性，主体资格和组织范围接入时补强，标记为“待业务确认”。
- 项目配置当前提供创建和读取；配置编辑/停用的专用版本命令可在后续配置治理任务中补齐，不改变 I04 订单快照规则。
- 当前输出生成使用合成领域参数和 Fake 共享设施，不代表医院真实设备联调或生产打印结果。
