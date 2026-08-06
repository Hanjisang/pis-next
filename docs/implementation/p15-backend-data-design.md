# P15 后端与数据实现设计

## 1. 事务与领域边界

所有命令先通过服务端形成的 `ActorContext` 和 P14 权限决策，再进入领域命令。领域类型不依赖 Spring、HTTP、JDBC、JSON 或 Vue。控制器只做输入映射和响应映射，状态转换由聚合方法表达，禁止 `setStatus`、`updateStatus` 或直接写入任意状态值。

申请/病例事务和标本/接收事务分别由主责任模块拥有；跨模块只传稳定内部身份、业务编号或端口结果。P15 的演示工作流在同一个本地事务中写入本地事实、状态历史、审计和 outbox；外部消息原文及幂等摘要先追加保存，业务处理结果与原文不互相覆盖。

## 2. 采用的 P11 逻辑表

实现使用 P11 正式逻辑表名的 P15 最小列集，不重命名核心对象：`pathology_request`、`pathology_case`、`patient_context_reference`、`visit_context_reference`、`patient_visit_snapshot`、`external_request_reference`、`specimen`、`specimen_container`、`clinical_state_current`、`state_transition_history`、`operation_responsibility`、`handoff_record`、`business_exception`、`audit_event`、`inbound_raw_message`、`inbox_consumption` 和 `outbox_event`。内部 UUID 与申请号、病理号、标本号、容器条码分列保存。

关键约束：

- 来源命名空间 + 外部申请标识、来源消息身份 + 摘要、病例号/申请号/标本号/容器条码分别唯一；重复同载荷返回既有事实，不生成第二条申请、病例或接收事实。
- 申请、病例、标本拥有 `concurrency_version`；状态当前值和状态转换历史同事务写入。接收带 `expected_version`，并发冲突返回 P12-ERR-010 或 P12-ERR-024 方向，不静默覆盖。
- `specimen` 只保存当前摘要和生命周期状态；每次接收、拒收、隔离、交接保存追加事实。患者/就诊使用外部引用和业务快照，不建立患者主数据。
- 关键写操作至少写入 `audit_event`；形成 P12-EVC-001/002/003 对应的 outbox 记录，outbox 与业务事实位于同一事务。
- 本阶段不创建蜡块、玻片、技术、冰冻、细胞、分子、外送、诊断、报告或文件业务表。

## 3. 编号策略

P11/P06 只确认“按已配置规则分配”，没有确认机构生产编号格式。因此实现通过 `BusinessNumberAllocator` 端口生成本地/测试的 `DEV-REQ-*`、`DEV-CASE-*`、`DEV-SP-*` 和 `DEV-CNT-*` 展示编号，并在非 local/test 运行环境拒绝未配置编号策略。该前缀是实现环境标记，不宣称正式医院业务编号格式，不使用 `SELECT MAX+1`。

## 4. 错误和事实保护

入口使用 P12 稳定错误语义：`P12-ERR-001/002/003/004/005/006/007/010/011/021/022/023/024/025/026/027/028` 的适用子集。API 响应包含操作身份、关联身份、业务事实引用、主体身份、业务版本、事件引用、审计引用、处理状态和安全重试提示；失败包含稳定错误、当前/预期版本、处理方向和审计引用。日志只输出内部引用、操作码和错误码，不输出患者标识正文或原始报文。
## 5. 数据范围实现

V3 迁移为病例和标本增加 `organization_reference`，local/test ActorContext 使用 `LOCAL_HOSPITAL` 合成范围。条码读取、隔离、交接、接收队列和病例追溯均由后端把 ActorContext 的医院范围作为 SQL 参数过滤；前端不能自行扩大范围。生产身份提供器、跨院授权和正式机构字典不在 P15 交付范围内。
