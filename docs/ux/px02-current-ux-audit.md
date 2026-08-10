# PX02 当前前端与工作台审计

基线：`f7468d46ae62f86a31022272ffb272b614bd38dd`

本审计先于 PX02 实施完成，范围只覆盖当前 PIS V2 产品体验、查询接口和权限入口，不扩展 Site Integration。

## 1. 总体结论

当前系统的核心业务事实和 PX01 页面已可运行，但首页仍由多个页面分别请求数据，无法表达“我的责任”和“公共池”的区别；制片页面将玻片队列、打印、细胞病理、扫描和技术事实揉在一起；诊断页面为单体组件；配置和系统管理仍是静态说明页；Viewer 仍是普通图片/引用的实现。

PX02 的主要策略是：新增工作台查询投影和管理查询/命令边界，重构页面组合；不复制 Core Domain，不增加复杂 Histology 状态机。

## 2. 页面分类

| 页面/能力 | 结论 | 原因 |
|---|---|---|
| Personal Workbench / `V2Home` | REBUILD | 当前并行请求公共池、技术医嘱和玻片，计数为空或把所有完成医嘱当作结果提醒；没有 MY_WORK/PUBLIC_POOL 投影。 |
| Navigation / AppShell | REFACTOR | 入口仍按角色数组和权限双重过滤；权限应成为业务入口的唯一依据。 |
| Case 360 / `V2CaseContext` | REFACTOR | 已有统一上下文和 Timeline，但首屏患者/申请信息和深链高亮不足。 |
| Global Search | REFACTOR | 查询可用，但无 debounce、键盘上下移动和按结果类型进入深层上下文。 |
| Registration / `V2RegistrationWorkbench` | REFACTOR | 页面仍包含 SYNTH-* 正式映射和分子/会诊下游写入；登记边界应停在 Case、病理号和初始标本。 |
| Grossing / `V2GrossingWorkbench` | KEEP + REFACTOR | 取材写入链已通过 PX01A；保留现有业务操作，继续收紧工作区信息密度。 |
| Slide Production / `V2SlideProductionWorkbench` | REBUILD | 693 行单体组件混合玻片队列、打印、细胞病理、扫描、阶段事实和异常。 |
| Histology | REBUILD | 需要围绕五个事实阶段和七个工作队列重新组织，不引入状态机。 |
| Diagnosis / `V2DiagnosisWorkspace` | REBUILD + SPLIT | 1491 行单体组件同时负责证据、编辑、医嘱、责任、报告、历史和路由。 |
| Image Viewer / `V2ImageViewer` | REBUILD | 当前是 `<img>` 与 CSS transform，不是 tiled WSI viewer。 |
| Technical Workbench | REFACTOR | 业务操作可用，需从诊断工作区/我的工作台投影接入结果关注。 |
| Frozen Workspace | KEEP + REFACTOR | PX01A 已有真实轮次闭环；本轮只修正上下文和入口，不新增 Frozen 平行模型。 |
| Configuration Hub | REBUILD | 当前只有分类卡片和说明，无真实列表/编辑/启停。 |
| System Admin Hub | REBUILD | 当前只有静态管理范围说明，无用户、身份、权限或组织范围操作。 |
| Report Center | BUILD AS PROJECTION | 不复制报告编辑器；只提供队列并回到现有 Diagnosis/Case Workspace。 |

## 3. 后端审计

- 已有 Case Workspace、Diagnosis Workspace、Histology、TechnicalOrder、Search 和 Report API，可作为查询和写命令复用边界。
- 尚无 `GET /api/v2/my-workbench`，当前首页在前端分别请求公共诊断池、技术工作台和制片列表。
- `AuthenticatedUser` 已提供账号、权限、医院/科室/任务范围和 DoctorIdentity；PX02 可在查询投影中使用，不需要重做 User 系统。
- Registration 数据库已有 `business_type`、`application_item_mapping` 和 `pathology_number_rule`，但缺少统一配置查询/编辑入口。
- DiagnosisTemplate、ReportTemplate、TechnicalProject 已有表和部分写命令，可增补配置查询边界。
- Histology 已通过 `material_process_fact` 保存五段时间事实，适合改造为队列投影。

## 4. 约束与验收重点

1. Core Domain modified files 目标为 0。
2. 权限判断以后端授权和 `availableActions` 为准，前端不再用角色名作为核心业务门控。
3. Histology 只推导“未开始/处理中/已完成”，打印、扫描和异常是附加动作。
4. 正式登记 UI 不再写死 SYNTH-HISTOLOGY、SYNTH-FROZEN、SYNTH-CYTOLOGY、SYNTH-MOLECULAR、SYNTH-CONSULTATION；测试/演示 seed 可以保留。
5. Viewer 的库依赖必须停留在 Adapter/基础设施边界。
6. 本轮不验证真实医院接口、设备、CA、Pilot 或生产部署。
