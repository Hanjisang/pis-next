# P16 领域与数据设计

## 1. 聚合和不变量

`GrossingBatch`（P16取材责任和任务边界）与 `TissueBlock`（P05-AGG-004蜡块业务记录计划）是两个写入聚合。`GrossingRecord`、`TissueSample`、标签和打印尝试是不可变事实或受控子实体。P15 `specimen` 仍由 specimen 模块所有。

核心不变量：

1. 只有 `RECEIVED`、未隔离、未拒收、未终止且身份已核对的标本允许开始取材。
2. 每条取材记录必须带批次、标本、责任主体、时间、版本和大体事实。
3. 已完成取材记录不可原地修改；更正是新版本并带理由、操作者和复核/审批引用。
4. 每条组织取样必须追溯病例、标本、批次、记录、操作者、部位、数量和计划蜡块。
5. 计划蜡块的 `id`、业务编号、标签身份、打印请求和打印尝试互不复用。
6. 计划蜡块不能在 P16 写入物理形成事实；标签生成/打印不改变物理状态。
7. 取样分配和完成使用乐观锁；有效编号由服务端生成并受数据库唯一约束保护。
8. 业务事实、状态历史、审计和 outbox 在同一事务内写入。

## 2. P16迁移表

V4 建立 11 张范围内表：`grossing_batch`、`grossing_batch_specimen`、`grossing_record`、`tissue_sample`、`tissue_block`、`tissue_block_sample`、`tissue_box_identity`、`label_identity`、`label_print_request`、`label_print_attempt`、`p16_idempotency_key`。`tissue_block` 是 P11-TBL-004 的产品实现；`tissue_box_identity` 作为 P11-TBL-020 的产品实现并由 V4 一并建立。

每张表均有 UUID 内部主键、组织范围字段、创建主体/时间和必要的并发版本。病例、标本、接收事实、审计、状态历史、outbox 复用 P15 已有表，不新建同义主表。核心引用使用明确外键；不使用多态外键承载核心来源；不提供物理删除。

## 3. 关键字段和约束

`grossing_record` 保存身份核对摘要、外观、数量、单位、大体描述和 `record_version_no`。`tissue_sample` 保存来源标本、取材记录、部位、描述、数量和分配状态。`tissue_block` 保存 `block_no`、来源标本/病例、来源类型、批次、生命周期、记录版本和并发版本。`tissue_block_sample` 防止同一取样重复分配。

`label_identity` 保存目标身份、目标版本、标签版本、模板逻辑版本、显示字段快照、条码载荷、生成主体和生成时间。`label_print_request` 与 `label_print_attempt` 分离；每次重打追加新的请求/尝试并记录理由，旧标签可正式作废但历史不删除。

编号默认使用 `DEV-BLOCK-<随机稳定片段>`，只在 local/test 可用；正式环境没有正式 `BlockNumberingPolicy` 时拒绝创建，绝不把 DEV 规则当作医院正式编号。
