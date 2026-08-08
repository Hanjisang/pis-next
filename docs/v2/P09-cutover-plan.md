# PIS-Next V2 P09 切换与封版设计基线

状态：已完成（设计封版）
版本：V2-0.2
日期：2026-08-08
切换原则：同一仓库、同一应用、渐进替换；不新建第二套 PIS 项目

## 1. 切换总原则

1. V2 在当前 pis-next 仓库和模块化单体内逐步替换 Legacy Domain；不复制一套长期并行系统。
2. V2 Core Domain 是唯一医疗事实来源。短期允许 shadow query 或结果比对，不允许长期双写两个领域模型。
3. Feature flag 只控制入口、读取投影和命令启用，不把 flag 变成长期双领域状态。
4. 切换前、切换中和切换后都保留原始数据、审计、迁移 checkpoint、回滚入口和验证报告。
5. 任何切换阶段不得牺牲来源链、报告不可变、责任链、权限、审计和备份恢复能力。

## 2. 阶段路线

| 阶段 | 范围与动作 | 单一事实来源 | 退出条件 |
|---|---|---|---|
| A Legacy + V2 隔离 | 在同一应用内建立 V2 包/模块和隔离 schema；P00–P04 设计不进入生产命令 | Legacy 仍为当前运行事实，V2 仅设计/测试 | P04–P08 评审通过、架构守卫可执行、没有跨模块越权写 |
| B 最小完整链路 | 实现 Registration → Case → Specimen → Grossing → Block → Slide → Diagnosis → Report | 新链路逐步成为指定业务类型的唯一写入源 | 核心链路单元/API/数据库/E2E 通过，报告签发和撤回审计通过 |
| C 扩展领域 | 接入 TechnicalOrder、Frozen、Cytology、Molecular、Consultation、DigitalSlide | 已启用业务类型由 V2 Core Domain 负责 | 各业务能力测试、权限、外部回调和来源链通过 |
| D 历史迁移 | 按 P05 M0–M5 只读盘点、映射、隔离、导入、对账和人工复核 | 迁移批次验收后由 V2 读取 | P0/P1 warning 关闭或批准，数量/关系/快照对账通过 |
| E 查询和入口切换 | 角色工作台、Search、Case Context、Diagnosis Workspace 切到 V2 Projection/Query；短期 shadow query 做差异比对 | V2 Projection/Query；事实仍为 V2 Core Domain | 业务用户验收、性能/权限/审计和回滚演练通过 |
| F 清理 Legacy | 稳定观察期后删除或退休旧领域对象、obsolete tables、兼容代码和过渡 flag | V2 唯一 | P09/P10 后续门禁通过；保留历史只读审计和迁移证据 |

阶段 F 不是本轮任务；在没有完成 D/E、备份恢复和业务验收前，不得物理删除当前代码或表。

## 3. Feature Flag 和兼容边界

规划 flag：

- v2.registration.enabled
- v2.grossing.enabled
- v2.material.enabled
- v2.technical-order.enabled
- v2.diagnosis.enabled
- v2.report.enabled
- v2.frozen.enabled
- v2.cytology.enabled
- v2.molecular.enabled
- v2.consultation.enabled
- v2.digital-slide.enabled
- v2.query.case-context.enabled
- v2.query.diagnosis-workspace.enabled

每个 flag 必须有 scope、owner、默认值、启用时间、回滚动作和审计。flag 只能选择 V2 入口或投影，不允许在同一个业务命令内随机决定写 Legacy 还是 V2。切换期间如需兼容现有 P15–P19 API，使用适配器把请求转换为 V2 命令，不能让适配器直接修改 V2 核心表。

## 4. 回滚字段和动作

| 对象 | 必备字段 | 回滚前提 | 回滚动作 |
|---|---|---|---|
| Feature flag | flag、scope、oldValue、newValue、changedBy、changedAt、reason | 新链路错误率、权限或数据质量超过阈值；尚无不可逆外部交付 | 停止新入口，恢复上一版本读取入口，保留 V2 事实和审计 |
| Migration run | runId、checkpoint、sourceHash、mappingRuleVersion、targetCounts、warningCounts、status | 批次未验收且未成为唯一有效来源 | 标记批次失效/隔离，从 checkpoint 重跑；不删除源数据 |
| Query cutover | oldQueryVersion、newQueryVersion、shadowDiffCount、enabledAt | 读模型延迟、差异或权限泄露 | 恢复 Legacy 查询入口，保留差异报告，修复后再启用 |
| Report cutover | reportRuleVersion、lastSignedReportId、snapshotHash | 报告渲染、签发、撤回或打印校验异常 | 停止新签发入口，保留已签发 V2 Report；按批准流程处理未签发草稿 |
| External integration | deliveryCursor、lastAckId、retryCount、deadLetterId | 外部回执失败但核心事实已提交 | 暂停出站或切换适配器，依靠 Outbox/Delivery 重试，不重写业务事实 |

每个阶段开始前必须验证备份可恢复、checkpoint 可读取、flag 可回退、监控和责任人在线。报告签发、材料销毁和外部正式交付属于不可逆风险，不能用简单数据库回滚替代业务撤回/补偿。

## 5. 切换门禁

以下 16 项是进入下一阶段或宣布设计封版的门禁：

1. P01 领域对象、全病理业务类型和边界已评审；
2. P02 不变量编号完整，关键规则无未记录矛盾；
3. P03 模块所有权、事务边界和依赖方向通过架构评审；
4. P04 数据模型覆盖核心、配置、投影、集成和审计层；
5. P05 迁移矩阵、warning、人工复核和对账规则已批准；
6. P06 命令/查询、错误合同、幂等、并发和外部适配已批准；
7. P07 角色工作台、Diagnosis Workspace、Material Tree 和无障碍基线已批准；
8. P08 不变量、架构守卫、业务场景和迁移测试可追溯；
9. V2 核心链路 Registration → Report 的自动化测试全部通过；
10. 迁移演练的数量、关系、编号、快照、孤儿、重复和 warning 对账通过；
11. 运行时就绪：数据库、Flyway、容器、日志、监控、队列和重试能力通过验证；
12. 备份、恢复、checkpoint 和回滚演练成功；
13. 权限、特殊授权、脱敏和审计抽查通过；
14. 报告签发、撤回、打印快照和文件引用不可变性通过；
15. 业务用户完成登记、取材、技术、诊断、审核、归档和质控验收；
16. P0 阻断项为 0，P1 项有批准的关闭证据或明确的切换前关闭时间。

任一核心领域、来源追溯、报告不可变、备份恢复或关键测试出现未解决 P0，必须阻断切换。Docker Server 当前不可用时，第 11 项和涉及 Testcontainers 的完整验证不能标记通过。

## 6. 指标和运行报告

每阶段报告至少记录：

- migration mapping count；
- success count；
- warning count，按 P0/P1/P2 分布；
- manual review count、resolved count、unresolved count；
- failed count、retry count、dead-letter count；
- shadow query diff count；
- Case/Specimen/Block/Slide/Diagnosis/Report 的关系对账差异；
- 报告签发、撤回、外部交付和权限拒绝的数量/错误率；
- 未解决设计问题数量及 owner。

指标必须带统计口径、时间窗口、数据集版本和脱敏后的 correlationId；不能只报告“完成率”。

## 7. 当前阻断项分级

| 编号 | 级别 | 问题 | 影响 | 处理条件 |
|---|---|---|---|---|
| CUT-P0-001 | P0 | 当前无已识别的核心领域/来源链/报告不可变 P0 | 不阻断本文档封版 | 若出现立即停止进入 V2-I01 |
| CUT-P1-001 | P1 | Docker Server/dockerDesktopLinuxEngine 曾不可连接 | 已解除；不再阻断当前设计封版和现有集成回归 | 仍需在 V2 实现阶段持续验证 compose、迁移和端到端运行 |
| CUT-P1-002 | P1 | 医院具体病理号格式、模板字段、院区和设备配置尚待部署确认 | 不改变核心模型；阻断具体部署配置发布 | 在 P09 阶段 D 前完成配置确认和审批 |
| CUT-P2-001 | P2 | 现有 P15–P19 测试仍以旧实现为主，尚未转为 V2 不变量测试 | 不阻断设计封版；阻断 V2 代码合入 | V2-I01 先建立 P08 测试骨架 |

“P1-001”是已解除的历史环境验证阻断，不可改写为产品测试通过；“P2-001”是明确的后续实现工作，不得在本轮偷渡修改。

## 8. Legacy 资产影响汇总

以下是 P00 资产分类的设计盘点，不是本轮物理删除数量：

| 处置 | 数量 | 最重要对象 |
|---|---:|---|
| KEEP | 6 个资产组 | Java 21/Spring Modulith/JDBC/Flyway/PostgreSQL/Vue/Vite；CI/Docker/测试基础；权限/审计/Outbox/幂等/乐观锁；登记/接收/取材/标签/技术医嘱/诊断/报告基础设施；前端 Shell/API/表单/工作台组件；现有场景/文档/测试作为审计和迁移输入 |
| REFACTOR | 6 个资产组 | accession/specimen；technical；diagnosis；P17/P18/P19 数据库和 repository；P15–P19 前端工作台；integration/security/audit/file/projection 边界 |
| DELETE / RETIRE FROM V2 | 8 个概念族 | ProcessingTask、EmbeddingTask；ActualBlockFormation 主语义；Planned/Actual Block/Slide 双模型；Report content version/current_version_id 嵌套；统一 CaseStatus；Generic TechnicalResult；Container/组织盒核心父层；收费/数字扫描硬阻断 |

历史代码和迁移文件在阶段 F 前不物理删除。Legacy 对象被“退休”表示不再作为 V2 核心语义，不表示已经从当前仓库移除。

## 9. 下一任务

下一任务必须明确为：

V2-I01：Case / BusinessType / ApplicationItemMapping / PathologyNumberRule / Specimen 基础领域实现

I01 开始前必须把 P04 转成字段级 ADR/Flyway 设计、把 P06 转成契约测试、把 P08 的 Registration/Specimen/编号不变量转成测试骨架，并确认 P09 第 1–8 项门禁。I01 不在本轮启动。

## 10. P09 封版结论

P09 已定义同仓库渐进替换、A–F 阶段、单一事实来源、短期 shadow query、feature flag、回滚字段、16 项切换门禁、指标、阻断分级和 Legacy 影响。设计封版不等于 V2 已开发、已迁移或已具备生产切换条件；运行时 Docker 验证和业务用户验收仍是后续门禁。
