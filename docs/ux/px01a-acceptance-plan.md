# PX01A 产品体验验收计划

## 1. 范围

PX01A 只验收 PIS V2 本地产品体验的真实业务操作，不新增业务模块，不进入 Site Integration、Production Readiness、正式迁移或 Pilot。

本轮验收范围包括：

- 登记、取材和蜡块生命周期操作；
- Histology 五个轻量技术过程事实及异常记录；
- Diagnosis Workspace 内的材料查看、诊断、技术医嘱、责任链和报告操作；
- 本地数字切片 Viewer fixture；
- 冰冻 Round 1 / Round 2 和 Frozen End；
- 报告撤回/重签及补充报告；
- Case 360、Timeline、Global Search、个人工作台和权限边界。

以下内容明确不在本轮验收范围：真实 HIS/LIS/EMR、厂商扫描仪或 WSI、真实打印机、CA/电子签章、生产容量、正式历史数据迁移、Pilot 和 Cutover。

## 2. 验收方法

每个浏览器场景通过页面完成写操作，再从页面查询结果和数据库事实核对结果。测试数据使用合成数据；每次运行使用带时间和 Playwright project 标识的独立业务编号，避免依赖共享业务 fixture。

测试认证身份按业务责任隔离：`registrar`、`technician`、`doctor-a`、`doctor-b`、`doctor-c` 和 `admin`。场景中的 Case、病理号、标本、蜡块、玻片和责任链均由本次运行创建。

验收在两种桌面分辨率执行：

- 1920×1080；
- 1366×768。

Viewer 使用仓库内的本地多分辨率 SVG fixture。它验证 Viewer 交互和上下文绑定，不代表真实医院扫描仪或厂商 WSI 接口已验证。

## 3. 浏览器场景矩阵

| 场景 | 页面写入链 | 关键结果 |
|---|---|---|
| PX01A-GH | 登记 → 多标本 → 取材 → A1/A2/A3/A4 → 修改/作废/打印/补打 → 完成取材 → 脱水/包埋/切片/染色/封片 | Case 360 材料树、技术事实、异常事实和 Timeline 正确 |
| PX01A-IJLMO | Diagnosis → DigitalSlide → TechnicalOrder → 技术结果 → A/B/C 责任链 → 签发 → 撤回/重签 → 补充报告 | 诊断、结果、责任、报告链和历史均可回溯 |
| PX01A-K | 冰冻 Round 1 → 签发 → 新送检 → Round 2 → 签发 → Frozen End | 两轮材料/报告分离，且只创建一个常规病例 |
| PX01A-EF | 个人工作台 → 搜索 → Case 360 → 历史/Timeline → 权限边界 | 工作项、业务落点和禁止操作符合角色权限 |

上述 4 个组合场景分别在 2 种分辨率运行，共 8 次浏览器验收运行；组合场景覆盖最终 14 项业务验收场景。

## 4. 验收判定

- 页面可见性不能替代写入证据；每个关键动作必须有成功反馈并在后续查询中可见。
- 报告撤回/重签与 Supplemental 使用不同语义：撤回链保留 withdrawn 记录，补充链保留原有效报告并建立 supplemental 关系。
- Histology 只记录 `startedAt`、`completedAt`、`operator` 及可选设备/批次/异常事实，不引入新的技术状态机。
- Viewer 适配边界停留在前端/基础设施，不进入 Core Domain。
- 任何真实医院外部系统能力均不得因本地 fixture 通过而标记为已验证。
