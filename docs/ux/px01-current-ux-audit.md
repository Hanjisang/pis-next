# PIS V2 PX01 当前产品体验审计

## 1. 审计范围

本审计基于当前 `main` 分支的真实实现，覆盖前端路由、应用壳、主要工作区、V2 Query/API、权限边界、全局搜索、数字切片、诊断、报告和现有测试。审计目标是确定 PX01 的产品重构边界，不改变已经确认的 Core Domain 规则。

## 2. 总体结论

当前系统已经能够完成主要正向业务闭环，但产品体验仍然以“能力验证工作台”为中心：病例上下文分散在多个页面，历史只能从报告或审计事实间接查看，制片页面聚焦于完成玻片而没有轻量技术事实，数字切片只有绑定/元数据入口，没有稳定的诊断阅片容器。

PX01 应优先建立统一的 `Case Workspace` 查询层和 `CaseTimeline` 展示层，再让工作台、诊断、数字切片、报告和材料页面共享病例上下文。Core Domain 保持不动；新增查询 DTO、Projection 和 Viewer Adapter 边界。

## 3. 页面与模块决策

| 当前资产 | 决策 | 原因与 PX01 去向 |
|---|---|---|
| `App.vue` / `navigation.ts` | REFACTOR | 保留认证、权限过滤和任务路由，改为统一病例上下文入口、清晰的岗位工作台导航。 |
| `V2Home.vue` | REBUILD | 当前按角色显示少量计数，但缺少真正工作项、优先级和直接进入病例的队列。改为个人工作台。 |
| `V2CaseContext.vue` | REBUILD | 当前只展示 Case + 材料树；升级为 Case 360，统一 Header、Material Tree、Timeline、责任、报告和快速动作。 |
| `V2GlobalSearch.vue` | REFACTOR | 保留 Ctrl+K 和搜索 API；结果全部落到 Case Workspace，并增加清晰的结果分组。 |
| `V2RegistrationWorkbench.vue` | REFACTOR | 保留正式登记 API 和多标本维护；减少实现字段，强化申请到病例的连续路径。 |
| `V2GrossingWorkbench.vue` | REFACTOR | 保留病例级取材和 Block 命令；补强多标本切换、历史入口、快速建块和病例 Header。 |
| `V2SlideProductionWorkbench.vue` | REBUILD | 当前围绕玻片完成队列，未表达脱水/包埋/切片/染色/封片的轻量事实。增加事实记录 Query/Command 边界，并保持 Slide 为业务主人。 |
| `V2DiagnosisWorkspace.vue` | REBUILD | 领域命令和 Workspace Query 可复用，但需要病例上下文、材料证据、时间线、责任链、技术结果和大面积 Viewer。 |
| `V2DigitalSlideWorkbench.vue` | REBUILD | 当前是数字切片绑定管理页；改为诊断上下文中的 Viewer 容器，同时保留独立管理入口。 |
| `V2FrozenWorkspace.vue` | REFACTOR | 现有轮次命令可复用；增加统一 Case Header、每轮材料/诊断/报告/时间线和临床反馈展示。 |
| `V2TechnicalWorkbench.vue` | REFACTOR | 保留 TechnicalOrder 领域和结果录入；按待处理、处理中、待录结果、已完成组织，隐藏 JSON/内部状态。 |
| `V2MaterialCustodyWorkbench.vue` | REFACTOR | 保留归档/借阅/归还事实；在材料上下文中显示病例、当前位置和历史。 |
| `V2QualityWorkbench.vue` | KEEP / REFACTOR | 基础 QC 已可用；仅统一视觉和从个人首页移除管理统计。 |
| `V2SectionOverview.vue` | DELETE | 通用模块说明页不能代替用户工作区；被任务队列或配置中心实际页面替代。 |
| `V2Login.vue` | KEEP / REFACTOR | 认证行为正确；统一中文提示、错误反馈和测试环境说明边界。 |
| `v2Api.ts` / `v2DiagnosisApi.ts` / `v2MaterialApi.ts` | REUSE / EXTEND | 继续复用正式命令和既有 Query；增加 Case Workspace、Timeline、轻量技术事实的客户端类型。 |
| `v2OperationsApi.ts` | REFACTOR | 将数字切片、材料保管和搜索的通用调用拆成产品上下文 API。 |
| `styles.css` | REBUILD / REFACTOR | 现有 Token 已有基础，但页面级样式重复且层级偏后台；建立 PX01 Workspace、Header、Timeline、Viewer、Table 组件样式。 |

## 4. 关键体验问题

1. 任意页面点击病理号、标本、蜡块、玻片或报告，不能稳定进入同一个病例中心。
2. `pis.audit_event` 只是操作审计事实，当前没有把多个 V2 业务事实组合成病理科可读的业务时间线。
3. 数字切片只有 `viewerReference`，缺少可替换的 Image Viewer Adapter 和可验证的缩放、平移、缩略导航容器。
4. 技术工作台的核心视角仍是医嘱/结果，而不是技术人员今天要完成的轻量环节事实。
5. 个人工作台的队列来源不统一，登记、取材、制片和诊断工作项不能都直接打开具体病例。
6. 页面之间存在重复的病例摘要、材料树和操作按钮，历史入口不统一。

## 5. 构建与复用边界

### REUSE

- V2 Registration、Grossing、Block、Slide、Diagnosis、Responsibility、TechnicalOrder、Report、Frozen、DigitalSlide、Archive/Loan 的领域命令。
- 认证、`availableActions`、权限检查和现有 V2 组织范围。
- 现有正式 Material Tree、Diagnosis Workspace Query、Report History 和 Search API。

### REFACTOR

- 路由、个人工作台、登记、取材、制片、冰冻、技术工作台、报告历史和全局搜索的产品呈现。
- Query DTO、病例上下文查询、Timeline Projection、错误反馈和设计 Token。

### REBUILD

- Case 360 / Case Workspace。
- Diagnosis 中的 Evidence/Viewer 区域。
- Histology 技术工作台的轻量环节事实视角。

### DELETE

- 不能承载真实任务的通用 Section Overview 入口。
- 被统一 Case Workspace 完全替代的重复 Case/材料摘要页面。

## 6. 约束与风险

- Core Domain modified files 目标为 `0`。若技术环节事实无法由现有模型表达，必须单独记录 `DOMAIN CHANGE REQUIRED`，不得静默修改。
- 本轮不接入真实 HIS/LIS/EMR、扫描仪、生产设备、CA、医院 Pilot 或正式历史数据迁移。
- WSI Viewer 只实现前端可替换容器和合成/普通图片 Adapter；不实现 Tile Server、金字塔生成或厂商协议。
