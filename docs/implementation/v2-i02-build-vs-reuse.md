# V2-I02 Build vs Reuse 决策

文档状态：V2-I02 实施前决策

实施基线：`c1b417d`（V2-I01A 收口完成；实际实现提交已同步到其后提交）

## 1. 决策总览

| Component | Decision | Reason |
|---|---|---|
| Grossing | NEW BUILD | Legacy GrossingBatch 依赖旧 Task/批次状态，且按单一工作台组织；V2 Grossing 必须是 Case 级、可处理多个 Specimen 的业务活动 |
| GrossingSpecimen | NEW BUILD | V2 需要明确 Grossing↔Specimen N:N 关系和关系事实；不复用 Legacy batch-specimen 计划关系 |
| Block | NEW BUILD | Legacy 同时存在 TissueBlock、计划 Block 和 ActualBlockFormation 语义；V2 只保留一个真实 Block |
| Slide | NEW BUILD | Legacy 没有可用统一 Slide 来源模型；V2 必须允许 Block/Specimen/External 来源，且打印不能创建 Slide |
| SlideRule | NEW BUILD | Legacy 没有 V2 配置规则边界；规则负责计算 Expected Slide Set，不承担 BPM |
| PrintRule | NEW BUILD | Legacy 标签打印请求不能表达 Slide 打印触发和材料创建/打印分离 |
| PrintService | NEW BUILD + SHARED INFRASTRUCTURE REUSE | 建立稳定领域抽象和 Fake Printer；不把 GK888/TSC/Zebra SDK 引入核心模块 |
| PrintLog | NEW BUILD | 补打必须是同一 Block/Slide 的追加打印事实，不创建 Reprint 实体 |
| Material Tree | NEW BUILD | 从 V2 Case/Specimen/Grossing/Block/Slide 真实关系查询，不建立 material_tree Source of Truth |
| Database | NEW BUILD | 只增加 `pis_v2` 的 I02 Flyway 迁移，不 ALTER Legacy 表或 FK 到 Legacy Task |
| Backend API | NEW BUILD | 使用 `/api/v2` 命令式 API；不在 P16/P17 Controller 添加 V2 行为 |
| Frontend Grossing | NEW BUILD | 以多 Specimen 取材为中心，不按 Legacy Task 拆页 |
| Frontend Slide Production | NEW BUILD | 以待完成 Slide、扫码/批量完成、补打和进度为中心，不显示脱水/包埋/染色状态机 |
| Authorization/Audit/Outbox/Idempotency | REUSE | 复用 I01 已验证的共享服务和事务基础设施；I02 只新增材料命令所需的轻量幂等记录 |

## 2. Legacy 明确不复用项

以下对象仅作为历史功能和迁移输入，不进入 V2 I02 领域依赖：

- `ProcessingTask`
- `EmbeddingTask`
- `ActualBlockFormation`
- `PlannedBlock` / `ActualBlock`
- `PlannedSlide` / `ActualSlide`
- Legacy 技术流程状态机

Legacy 旧页面可继续独立存在，但 V2 不双写 Legacy，不把 V2 Block/Slide 转换成旧 Task 以驱动旧工作台。

## 3. 复用边界

1. V2 Material 应用服务复用 ActorContext、P15AuthorizationService、JdbcAuditEventRepository、OutboxPort 和 JDBC/Flyway 运行时。
2. V2 PrintService 只依赖抽象；当前实现使用 Fake Printer，真实打印机通过未来 Adapter 接入。
3. `TechnicalOrder`、`FrozenRound` 只作为 `source_type`/`source_reference_id` 的稳定扩展点，不在 I02 创建对应业务模块。
4. Material Tree 是实时领域查询 DTO；不创建复制表、通用多态外键或 EAV。

## 4. 关键判断

只要旧实现仍然把材料实体绑定到 ProcessingTask、EmbeddingTask、计划/实际双模型或 CaseStatus，继续兼容就会把错误语义带入 V2。因此 I02 采用新表、新领域对象、新 API 和独立前端工作台；共享设施复用不改变 V2 材料模型的所有权。
