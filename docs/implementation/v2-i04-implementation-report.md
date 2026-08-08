# V2-I04 实施报告

## 1. 结论

V2-I04 已完成 TechnicalProject、TechnicalOrder、TechnicalOrderItem、TechnicalOrderTarget、正式输出关联和 Diagnosis↔Technical loop。本阶段没有进入 V2-I05。订单状态、签出阻断和工作台状态均由真实输出/结果事实投影，不新增 CaseStatus。

## 2. 已实现范围

### 数据库与领域

1. Flyway V15 新增 `technical_project`、`technical_order`、`technical_order_item`、`technical_order_target`、`technical_order_item_result`、`technical_order_output`、序列和幂等表。
2. TechnicalProject 保存允许目标类型、Slide/Block/StructuredResult 输出声明、参数/结果 schema、费用/显示配置和 `required_before_sign_out_default`。
3. TechnicalOrder 归属 Diagnosis 和 Case；Item 保存项目配置快照；Target 支持 CASE、SPECIMEN、BLOCK、SLIDE 多目标。
4. 服务端验证 Diagnosis、当前责任医生、启用项目、项目业务类型、目标类型、目标存在和跨病例一致性。
5. TechnicalOrder 状态采用 PENDING/EXECUTING/COMPLETED/CANCELLED，取消只写事实和版本，不删除输出。

### 材料与结果主链

1. IHC 等切片项目直接调用 I02 正式 `Slide`，使用 `TECHNICAL_ORDER` 来源上下文；INITIAL Slide 的完成事实和数量保持不变。
2. 补充取材项目调用 I02 正式 `Grossing`、`Block`、`Slide`，新 Grossing 标记 `TECHNICAL_ORDER` 来源。
3. StructuredResult 使用 `technical_order_item_result`，结果 belongs to Item，支持版本化更新和幂等。
4. 项目完成条件由配置输出类型和真实事实共同决定；Slide 完成后订单查询立即投影为 COMPLETED。

### 工作区与前端

1. I03 Diagnosis Workspace 增加 TechnicalOrder 列表、Item/Target/Output/Result 摘要、阻断数量和创建入口。
2. 新增 `workspace=v2-technical` 独立 Technical Workbench，包含项目配置基础表单、活跃订单队列、执行、取消和结构化结果入口。
3. 前端不提交病例状态、订单派生状态或材料完成状态；命令仅提交业务输入、版本和幂等键。

## 3. 测试证据

| 验证 | 结果 |
|---|---|
| `mvn -B -ntp -Dtest=V2TechnicalOrderWebTest test` | 5/5 通过，覆盖多项目/多目标、跨病例拒绝、正式 Slide、补充 Grossing/Block/Slide、结构化结果、取消和阻断 |
| `mvn -B -ntp -Dtest=V2DiagnosisWebTest test` | 5/5 通过，I03 回归通过且 TechnicalOrder 占位更新为已实现投影 |
| `npm run typecheck` | 通过 |
| `npm run test:unit -- --run` | 9 个测试文件、10 个测试通过 |
| `V2ArchitectureDriftTest` | 保持通过；禁止的 Legacy/伪技术类型未进入 V2 |
| `mvn -B -ntp -Dtest=V2RegistrationPostgresIntegrationTest test` | 1/1 通过，实际执行 PostgreSQL 18.4/Testcontainers 的 Flyway V15 迁移、24 个 TechnicalProject 种子和显式 FK/索引断言；Flyway 对 PostgreSQL 18.4 有版本支持提示 |

## 4. 权限、审计和风险

I04 使用当前 P14 共享权限能力：创建/取消/查询/执行分别映射到已存在的 P14 权限编号，并在命令中产生 Audit/Outbox 事实。当前 identity-access 仍是适配边界，不伪造 User/Doctor 主数据。

当前仓库未实现真实医院接口、真实打印机、数字切片平台联调、生产部署或用户验收；这些不是 I04 的已验证结果。项目配置编辑/停用命令也留待后续配置治理任务，订单项目始终保存配置版本快照。
