# V2-I01A 实施收口验收报告

文档状态：已完成收口审查

审查基线：`31c4793`（P00–P09 领域设计与工程设计）

审查范围：V2-I01 已实现内容、模型漂移、数据库迁移、事务边界、Legacy 隔离、V2 前端隔离和测试证据。

明确不包含：V2-I02 及任何后续阶段业务功能。

## 1. 最终结论

V2-I01A 收口通过。原 I01 实现中存在的 Case/Specimen 复杂流程状态漂移已通过 `V12__v2_i01a_model_drift_correction.sql` 收敛：

1. V2 Case 生命周期只有 `ACTIVE` 和 `CANCELLED`。
2. V2 Specimen 不再维护流程生命周期状态，只保存可修改业务事实和软删除事实。
3. V2 不建立 Label 领域实体；`label_code` 只是标本上的技术标识字段。
4. Case、Specimen、业务编号和内部 UUID 分离；取消不回收、不自动复用已分配病理号。
5. V2 写入只进入 `pis_v2`，没有 V2/Legacy 双写。
6. `V2ArchitectureDriftTest` 防止核心禁用类型和 Case 生命周期再次漂移。

当前结论为：`V2-I01 COMPLETE`。`V2-I02` 未启动。

## 2. 状态机与历史记录审查

| 对象/记录 | 当前表示 | 来源 | 结论 |
|---|---|---|---|
| Case | `ACTIVE`、`CANCELLED` | `v2.registration.domain.Case`、`pis_v2.pathology_case.lifecycle_state_code` | 通过；只有两种生命周期值 |
| Case 取消 | 取消原因、时间、执行人及 `number_binding_active=false` | `Case.cancel(...)`、Case 取消字段 | 通过；不覆盖原 Case 事实，不回收号码 |
| Specimen | 可修改事实：`specimenCode`、来源、部位、方法、技术标签 | `v2.registration.domain.Specimen`、`pis_v2.specimen` | 通过；不是流程状态机 |
| Specimen 删除 | `deleted_at`、删除原因、执行人 | `Specimen.softDelete(...)`、`pis_v2.specimen` | 通过；这是软删除事实，不是工作流状态 |
| Case 状态历史 | 不再存在 | V12 删除 `case_state_history` | 通过；I01A 不保留虚假的状态流转历史 |
| Specimen 状态/接收/异常历史 | 不再存在 | V12 删除 `specimen_state_history`、`specimen_receipt_fact`、`specimen_exception` | 通过；接收和异常不被冒充为 I01A 核心流程 |
| 幂等记录 | 技术幂等记录，含结果类型和结果 ID | `pis_v2.idempotency_record` | 技术记录，不属于医疗业务状态机 |
| Outbox | 共享基础设施的发布技术记录 | shared outbox | 技术记录，不属于 Case/Specimen 生命周期 |
| 配置 active 字段 | 业务类型、映射、编号规则的配置启用标记 | `business_type`、`application_item_mapping`、`pathology_number_rule` | 配置状态，不属于医疗对象生命周期 |

V2 源码和 V2 schema 中不再使用 P08 的 `P08-SM-002-*` Case 流程状态或 `P08-SM-003-*` Specimen 流程状态。P08 文档仍作为历史设计基线保留，但不被 I01A 实现复制成新的 V2 核心状态机。

## 3. Label 绑定审查

1. V2 没有 `Label` 或 `LabelBinding` 领域实体、表或独立生命周期。
2. `label_code` 是 Specimen 上的技术标签标识，用于技术识别和访问校验，不拥有标本，也不承担业务流程状态。
3. 活跃技术标签通过 `uq_v2_specimen_label_active` 约束；软删除后标签绑定事实保留，活跃唯一性释放。
4. `specimenCode` 是标本业务事实，使用 `uq_v2_specimen_code_active(case_id, specimen_code)` 保证同一 Case 内活跃唯一；不同 Case 可以使用相同代码。
5. V2 前端只提供登记、事实修改和软删除，不提供 Label 状态机或标签工作流按钮。

## 4. Domain Gate

| Gate | 审查结果 | 证据 |
|---|---|---|
| Case 生命周期 | PASS | `Case` 仅暴露 `ACTIVE/CANCELLED`；领域测试验证非法状态拒绝和取消关闭号码绑定 |
| BusinessType | PASS | 数据库配置实体；路由从 `business_type` 读取，不使用 Java enum 驱动 |
| ApplicationItemMapping | PASS | 数据库唯一约束、active 配置和路由查询；无前端状态猜测 |
| PathologyNumberRule | PASS | `FOR UPDATE` 锁定规则行，条件递增 `next_serial`，唯一约束保护规则；事务失败回滚由注册应用事务边界保证 |
| 取消与号码 | PASS | 取消只使当前绑定失效，历史病理号保留；I01A 不自动回收或复用号码 |
| Specimen 归属 | PASS | `specimen.case_id` 外键指向 V2 Case |
| Specimen 编码 | PASS | 活跃同 Case `specimenCode` 唯一；跨 Case 可复用；软删除后可重新登记 |
| Specimen 修改/软删除 | PASS | 领域对象保存新事实，原记录不物理删除；数据库使用 `deleted_at` 和乐观版本 |
| Registration 事务 | PASS | 建案/登记在一个应用事务中完成路由、幂等占位、编号分配、核心写入、审计和 outbox |
| Legacy 隔离 | PASS | V2 使用独立 `com.hanjisang.pis.v2` 和 `pis_v2`；未修改 Legacy 核心表，也没有双写命令 |
| 数据库约束 | PASS | UUID 与业务编号分离、外键、唯一约束、检查约束、活跃唯一索引和 Flyway V1–V12 |
| API 隔离 | PASS | `/api/v2/registration` 下仅保留 I01A 建案、标本登记、修改和软删除命令 |
| 前端隔离 | PASS | `V2RegistrationWorkbench` 使用独立 `v2Api.ts`；不提供 Legacy 状态按钮或 UUID 手工录入 |

## 5. 漂移项及处理结果

| 漂移项 | 原风险 | I01A 处理 |
|---|---|---|
| Case 复制 P08 多状态机 | 将历史流程状态误当成 V2 当前模型 | Case 收敛为 `ACTIVE/CANCELLED`，V12 清理历史状态表 |
| Specimen 复制接收/隔离状态 | 将业务事实和后续流程混入 I01 | Specimen 改为可修改事实 + 软删除，不再保存流程状态 |
| Label 被误建成业务实体 | 引入错误所有权和生命周期 | 保留技术 `label_code` 字段及活跃唯一约束 |
| 号码并发/取消语义不清 | 可能重复分配或错误回收病理号 | 数据库行锁 + 条件递增；取消不回收、不自动复用 |
| 架构禁用类型可能回流 | 后续实现再次引入 `Block/Slide/Report` 核心模型 | 增加 `V2ArchitectureDriftTest`，禁止六类核心类型名 |

本表中的漂移项均已处理，当前 P0 漂移项为 0。

## 6. 验证证据

### 6.1 后端与数据库

1. 全量 Maven 测试：`mvn -B -ntp test`，45 项通过，0 失败，0 错误。
2. 定向 V2/边界测试：`mvn -B -ntp -Dtest=... test`，10 项通过，0 失败，0 错误。
3. PostgreSQL Testcontainers：`postgres:18.4-alpine`，Flyway V1–V12 全部执行成功；验证 `PIS_V2/V2-I01A`、8 个业务类型、4 个申请项目映射、16 条编号规则、活跃唯一索引和 I01A 删除的状态表。
4. V2 Web 测试覆盖：幂等重放、幂等键 payload 冲突、Case/业务编号分离、同 Case 编码唯一、跨 Case 编码复用、事实修改、乐观版本冲突、软删除和软删除后的编码复用。
5. 架构漂移测试：2 项通过，覆盖禁用核心类型和 Case 生命周期值集合。

Flyway 对 PostgreSQL 18.4 的“数据库版本高于当前支持版本”提示是工具兼容性警告，不是迁移失败；本次已实际执行并通过迁移验证。

### 6.2 前端

前端验证结果：`npm.cmd run typecheck` 通过；`npm.cmd run test:unit -- --run` 为 8 项通过；`npm.cmd run format:check` 通过；`npm.cmd run build` 通过；`npm.cmd run lint` 为 0 错误、75 条既有 Legacy 工作台格式 warning。

## 7. 假设、待确认和剩余风险

### 7.1 待业务确认

1. 正式医院的业务类型、申请项目映射、病理号前缀、年度范围和跨组织编号范围仍以医院配置与业务评审为准；当前 `SYNTH-*`、`LOCAL_HOSPITAL`、`H-*`/`HS-*` 仅为合成开发配置。
2. Case 取消的正式应用命令、权限和医院操作界面不在 I01A 范围内；本次已完成领域不变量和号码语义守卫，未提前实现 V2-I02 功能。

### 7.2 剩余风险

1. P1：尚未进行真实医院接口联调、真实数据验证或生产部署验证。
2. P2：尚未进行多实例高并发压力测试；当前已验证数据库锁、条件更新和 Web 乐观版本冲突。
3. P2：V2 前端工作台仍是 I01A 独立入口，综合工作台切换需等待后续读写边界评审，避免形成双写。

P0：0。以上风险没有隐藏，也不阻断 I01A 收口。

## 8. 收口签字项

- [x] 未启动 V2-I02。
- [x] 已完成 I01 全量状态/历史/实体漂移审查。
- [x] 已完成数据库迁移和 `pis_v2` 约束检查。
- [x] 已完成 V2/Legacy 隔离检查。
- [x] 已增加 Architecture Drift Test。
- [x] 已同步实现范围、构建复用决策和测试证据文档。
- [x] P0=0。
