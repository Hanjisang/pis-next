# PIS V2 PX01 Build vs Reuse

## 1. 决策表

| 能力 | 决策 | 复用/新建边界 |
| --- | --- | --- |
| Personal Workbench | REBUILD | 重建首页工作项布局；复用认证、权限和既有队列 API |
| Permission UX | REFACTOR | 使用现有 `V2AuthUser.permissions` 与诊断 `actions`；不复制角色权限判断 |
| Case 360 | REBUILD | 新增 Workspace Query、统一 Header、Material Tree、责任和报告视图 |
| Case Timeline | REBUILD | 新增 Query Projection；组合审计与既有业务事实，不建立逐实体 History 表 |
| Global Search | REFACTOR | 保留搜索 API 和 Ctrl+K；所有结果统一进入病例中心 |
| Registration | REFACTOR | 复用 Registration Commands；保留单页多标本和业务类型差异 |
| Grossing | REFACTOR | 复用 Grossing/Block API；统一病例 Header，强化快速建块和历史入口 |
| Histology Production | REBUILD | 以 Slide 队列为主，新增轻量 `material_process_fact` Query/Command |
| Diagnosis Workspace | REBUILD | 复用 Diagnosis Workspace Query、Diagnosis、Responsibility、Report Commands |
| DigitalSlide 管理 | REFACTOR | 复用绑定/改绑/解除绑定 API，加入统一病例上下文 |
| DigitalSlide Viewer | BUILD | 新增可替换前端 `V2ImageViewer` 容器；不实现 WSI 基础设施 |
| Frozen | REFACTOR | 复用 FrozenRound API；统一 CaseHeader 和轮次上下文 |
| TechnicalOrder | REFACTOR | 复用 TechnicalOrder/Result；增加病例中心和诊断工作区落点 |
| Report History | REFACTOR | 复用 I05 Report Query；增强责任、撤回和补充关系展示 |
| Archive/Loan | REFACTOR | 保留既有材料保管事实；后续继续以材料上下文进入 |
| QC/Statistics | KEEP / REFACTOR | 保留核心查询，管理统计不占据个人工作台首屏 |
| Design System | REFACTOR | 复用既有 tokens，统一 CaseHeader、Timeline、Viewer、Table 和反馈样式 |
| 旧 V2 兼容页面 | DELETE | 仅删除完全被新工作区替代且无外部引用的页面；本轮不保留兼容路由 |

## 2. 明确不构建

本轮不构建真实 HIS/LIS/EMR、真实打印机/扫描仪、CA、生产部署、历史数据正式迁移、AI、高级 WSI 功能和医院 Pilot。

## 3. Core Domain 约束

PX01 不修改既有 Core Domain 文件。V26 只增加 Histology 轻量生产事实表及其 Query/Command 边界，不创建新的材料生命周期或技术状态机。
