# P18 技术医嘱领域与数据设计

## 1. 聚合与身份

`p18_technical_order` 是医嘱聚合根，使用不可变内部 UUID 和独立医嘱业务编号。`p18_technical_order_project` 是医嘱内独立项目，拥有项目编号、项目类型、项目版本、配置快照、优先级、用途和并发版本。医嘱总状态由项目状态聚合得出，不覆盖审核、受理、执行交接和结果维度。

项目目标位于 `p18_order_target`，目标历史位于 `p18_order_target_history`。计划产物位于 `p18_planned_output`，与实际玻片严格分离。P17 的 `p17_actual_block_formation` 是当前目标的正式来源；P18 不复制 `tissue_block` 或实际蜡块主记录。

## 2. 表边界

Flyway V7 创建 13 张 P18 表：

1. `p18_technical_project_configuration`：项目配置及版本；
2. `p18_technical_order`：医嘱聚合根；
3. `p18_technical_order_project`：独立技术项目；
4. `p18_order_target`：当前目标关系；
5. `p18_order_target_history`：目标绑定/更正历史；
6. `p18_planned_output`：计划玻片/计划产物；
7. `p18_project_review`：审核事实；
8. `p18_project_responsibility_history`：责任分配、接管和交接；
9. `p18_project_change`：追加和变更历史；
10. `p18_project_cancellation`：取消历史；
11. `p18_project_result_reference`：下游规范化结果引用；
12. `p18_order_state_history`：医嘱状态历史；
13. `p18_project_state_history`：项目状态历史。

所有医疗业务记录只追加，不物理删除。重要表具备唯一约束、外键、检查约束、索引、`record_version_no` 和 `concurrency_version`。核心目标不使用通用多态外键、EAV、逗号分隔 ID 或无约束 JSON。

## 3. 关键不变量

- 项目必须有病例、项目类型、用途、理由和目标；
- 目标必须属于同一病例，存在于当前有效的 P17 实际蜡块形成事实，且 P17 蜡块生命周期为 `P08-SM-004-ST-03`；
- 计划数量和标签数量为显式整数，不能用计划记录冒充实际玻片；
- 完成项目必须存在规范化结果引用；
- 取消、目标更正和责任变更只能追加事实并留下审计；
- 版本不匹配拒绝写入，重复幂等键同载荷返回权威结果，不同载荷拒绝。
