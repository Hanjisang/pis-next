# PIS V2 PX02 Build vs Reuse

基线：`f7468d46ae62f86a31022272ffb272b614bd38dd`

本表记录 PX02 的实现边界。所有新增内容均停留在 Query、Application、Frontend 或 Adapter 边界；未修改 Core Domain。

| 能力 | 决策 | 实现边界 |
| --- | --- | --- |
| Personal Workbench | REBUILD | 新增 `/api/v2/my-workbench` 投影，区分 MY_WORK 与 PUBLIC_POOL，复用认证、责任链和权限事实。 |
| Permission UX | REFACTOR | 导航和工作台按权限集合生成；角色只作为显示/配置模板，不作为前端业务门控。 |
| Case 360 | REFACTOR | 保留 Case Workspace、材料树和 Timeline，增强首屏上下文、最近历史和 Block/Slide 深链高亮。 |
| Case Timeline | REUSE + REFACTOR | 继续组合既有审计、材料、诊断、技术医嘱和报告事实，不新建逐实体历史表。 |
| Global Search | REFACTOR | 保留搜索 API 和 Ctrl+K；加入 debounce、上下键、Enter/Escape 和按结果类型深链。 |
| Registration | REFACTOR | 登记只建立 Case、病理号和 Specimen；业务类型来自真实 ApplicationItemMapping，合成项目仅保留在 seed/fixture。 |
| Grossing | REFACTOR | 复用既有取材/蜡块命令，保留单病例多标本、快速建块、作废、打印和历史。 |
| Histology | REBUILD | 以 Slide 为工作对象，使用 `material_process_fact` 五阶段时间事实和派生队列；不增加状态机。 |
| Diagnosis Workspace | REFACTOR + SPLIT | 保留既有 Diagnosis/Responsibility/Report 业务边界，拆出 Shell、证据、编辑器、Viewer、技术医嘱、责任和报告 UI 边界。 |
| DigitalSlide Viewer | REBUILD | 新增 `ImageViewerAdapter`、OpenSeadragon tiled adapter、普通图像 fallback 和外部平台 contract；Domain 不依赖 Viewer 库。 |
| Frozen | REFACTOR | 复用 FrozenRound、Case、Specimen、Grossing、Block、Slide 和 Report；不建立 Frozen 平行材料实体。 |
| TechnicalOrder | REFACTOR | 复用既有医嘱/结果链，在工作台和诊断上下文中提供返回结果关注入口。 |
| Report Center | BUILD AS PROJECTION | 新增报告队列查询；编辑、预览、签发、撤回和补充仍回到既有 Diagnosis/Case Workspace。 |
| Configuration | REBUILD | 以真实配置快照和更新命令替换静态占位页，覆盖业务类型、申请映射、编号规则、技术项目、诊断模板和报告模板。 |
| System Administration | REBUILD | 提供用户、Doctor Identity、组织范围以及 BUSINESS/DATA/ACTION 三层权限的真实读取和保存入口。 |
| Design System | REFACTOR | 复用既有 tokens，统一紧凑工具栏、病例 Header、表格密度、工作区面板、状态和反馈样式。 |
| 旧 PX01 页面/兼容层 | DELETE / NOT ADDED | 没有新增兼容页面或 Legacy Business 依赖；旧验收测试改写为当前 V2 语义和显式合成夹具。 |

## 明确不构建

PX02 不实现真实 HIS/LIS/EMR 联调、厂商打印机/扫描仪、医院 WSI 平台、CA、Pilot、生产部署、正式历史迁移、AI 或高级 WSI 能力。
