# PIS-Next V2 前期设计封版报告

状态：设计封版完成，运行时切换未授权
版本：V2-0.2
日期：2026-08-08
当前基线：commit 5467766

## 1. 结论

P04–P09 已在同一仓库内完成工程设计封版。P04 定义了五层数据模型和核心来源链，P05 定义了基于当前 P15–P19 实现的迁移矩阵、证据、warning、人工复核和对账，P06 定义了 Query/Command API 和并发/幂等合同，P07 定义了角色工作台和 Diagnosis Workspace，P08 定义了不变量、架构守卫、业务场景和迁移测试，P09 定义了渐进切换、回滚、16 项门禁和 Legacy 处置。

本封版不包含 V2 业务代码、Flyway 迁移脚本、API 实现、页面实现、生产数据迁移或切换。下一任务固定为：

V2-I01：Case / BusinessType / ApplicationItemMapping / PathologyNumberRule / Specimen 基础领域实现

## 2. 交付物

| 文件 | 封版结果 |
|---|---|
| P04-data-model.md | 已完成：核心/配置/投影/集成/审计五层、字段、关系、约束、索引和并发入口 |
| P05-migration-plan.md | 已完成：当前 P15–P19 对象逐项 Migration Matrix、MigrationWarning、M0–M5、对账和回滚 |
| P06-api-plan.md | 已完成：查询、命令、诊断工作台、幂等、并发、错误和外部适配 |
| P07-frontend-plan.md | 已完成：角色工作台、Diagnosis Workspace、Material Tree、Global Search、可访问性 |
| P08-test-plan.md | 已完成：P02 不变量矩阵、架构偏离、场景、迁移、回归和环境记录 |
| P09-cutover-plan.md | 已完成：A–F 阶段、feature flag、回滚字段、16 项门禁、阻断项和 Legacy 影响 |
| design-freeze-report.md | 本报告 |

## 3. 设计门禁结果

| 门禁 | 结果 | 说明 |
|---|---|---|
| P00 当前实现审计 | PASS | 已审计当前 backend/frontend、Flyway V1–V9、P15–P19 实现和测试资产 |
| P01–P03 领域基线 | PASS | 对象、54 项不变量、模块所有权和隔离边界已存在 |
| P04–P09 文档完整性 | PASS | 六份文档已完成并互相引用 |
| V2 业务代码/迁移脚本未提前启动 | PASS | 本轮只改设计文档和索引/报告 |
| 当前后端非 Docker 回归 | PASS | 28/28 通过 |
| 当前前端回归 | PASS | 格式、lint、typecheck、5/5 单元测试、build 通过 |
| Docker/完整 PostgreSQL 集成 | PASS | Docker Client/Server 29.6.2 可用；完整 verify 的 2 个 Testcontainers 测试已执行并通过，compose config 通过 |
| 生产切换准入 | NOT READY | 还未完成 V2-I01、V2 迁移演练、V2 备份恢复、V2 端到端链路和用户验收 |

Design Gate：PASS（P04–P09 设计封版）；Runtime/Cutover Gate：NOT READY（V2 后续实现、迁移演练、备份恢复和用户验收未完成）。

## 4. 影响盘点

Legacy 资产按 P00 的设计盘点为：

- KEEP：6 个资产组。技术栈、模块化单体、CI/Docker/测试基础、权限/审计/Outbox/幂等/乐观锁、已有基础设施、前端 Shell/组件和现有文档测试作为输入；
- REFACTOR：6 个资产组。accession/specimen、technical、diagnosis、P17/P18/P19 数据和 repository、前端工作台、integration/security/audit/file/projection 边界；
- DELETE / RETIRE FROM V2：8 个概念族。ProcessingTask、EmbeddingTask、ActualBlockFormation 主语义、Planned/Actual Block/Slide 双模型、Report content version/current_version_id 嵌套、CaseStatus、Generic TechnicalResult、Container/收费/数字扫描硬阻断语义。

上述数量是设计分类组数量，不是已删除文件数量。当前没有物理删除历史代码或迁移文件。

## 5. 阻断和未决事项

1. P0 未决项：0。
2. P1：Docker Server 曾不可连接，已恢复；完整 Testcontainers 已通过，但 V2 compose/迁移/运行时验证仍属于后续门禁。
3. P1：医院具体病理号格式、模板字段、院区和设备配置待部署确认；不改变核心模型，但阻断具体部署配置发布。
4. P2：现有 P15–P19 测试尚未转为 V2 不变量测试；V2-I01 必须先建立 P08 测试骨架。

“待业务确认”内容已经与核心领域规则分离；未决内容不能被当作已完成的医院实施事实。

## 6. 验证记录

已完成：

- 文档 UTF-8、替换字符、代码围栏、尾随空格和 git diff --check 检查；
- 后端非 Docker 测试 28/28；
- 前端 format check、lint、typecheck、unit 5/5、build；
- 敏感信息扫描未发现密钥模式；
- Docker Client/Server 29.6.2 状态检查、完整 verify 和 compose config 已通过，未把客户端存在误判为集成通过。

待后续完成：

- V2 实现阶段的完整 Maven verify、Testcontainers、compose build/启动；
- P04 字段级 Flyway 设计和迁移脚本；
- P06 契约测试及 V2 领域测试；
- P05 合成历史数据迁移演练；
- 备份恢复、权限审计和一线用户验收。

## 7. 封版批准边界

本报告批准的是“前期设计可以进入 V2-I01”的边界，不批准生产切换、不批准历史数据写入、不批准长期双写，也不批准删除 Legacy 资产。任何新增病理类型、对象、不变量或追溯规则必须稳定追加编号并更新 P01–P09，不得覆盖历史基线。
