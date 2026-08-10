# PX02B Build vs Reuse

## 1. 选择原则

PX02B 只增加 Query、Projection、前端工作区状态和适配器边界，不复制 Case、Specimen、Block、Slide、Diagnosis、Report 等核心领域对象。所有写操作仍通过已有 V2 Application API。

## 2. 决策矩阵

| 能力 | 决策 | 实施说明 |
| --- | --- | --- |
| Histology Workbench | REFACTOR | 复用 V2 Histology phase facts；将队列、批量动作、扫码、异常、打印组合为单一工作台。 |
| Unified History | BUILD（Query/UI） | 新增 `V2HistoryDrawer` 和既有 Case Timeline 的统一展示层；不新增 History Domain。 |
| Case 360 | REFACTOR | 复用 Case Workspace Query、Material Tree、Timeline；补充首屏派生字段和对象历史入口。 |
| Patient Pathology History | BUILD（Query） | 新增患者引用查询服务，返回既往病例、诊断摘要和报告状态；不改变患者核心模型。 |
| Diagnosis Workstation | REFACTOR | 复用 Diagnosis、Responsibility、TechnicalOrder、Report API；拆分展示壳、证据、编辑、阅片等 UI 组件。 |
| DigitalSlide Viewer | REUSE / REFACTOR | 复用 OpenSeadragon 和 `ImageViewerAdapter`；PX02B 不增加 AI、标注或 WSI 服务端。 |
| Registration Queue | BUILD（Projection） | 新增登记队列查询；空的外部申请队列如实显示为空，手工登记仍调用正式 Application→Case 链。 |
| Grossing Efficiency | REFACTOR | 复用 Block/Grossing commands；增加批量快捷动作和 History Drawer。 |
| Global Search | REFACTOR | 复用 Search API；增加防抖、患者结果和 Slide/Patient 深链参数。 |
| Technical Result Attention | REFACTOR | 复用 TechnicalOrder item 与责任链；增加“当前责任 + 未确认”查询和 acknowledge command。 |
| Archive Location | REFACTOR / BUILD（Query） | 操作页只读已配置库位；复用 custody domain，不在归档操作页创建库位。 |
| Configuration / Administration | REFACTOR | 复用既有配置/权限页面和 API，补足业务语言、库位配置和保存反馈。 |
| Core Domain | REUSE | 本轮目标为 0 个核心领域文件修改；新增内容位于 application/query/web/frontend。 |

## 3. 明确不采用

- 不引入 `CaseStatus`、Histology 状态机、`TechnicalSlide`、`FrozenXXX` 平行实体。
- 不把 `printCount`、扫码或异常事实解释为阶段状态。
- 不在前端用角色名称代替后端权限。
- 不建立 `material_tree` 作为新的事实源。
- 不把医院接口、设备 SDK 或真实数据迁移带入 PX02B。
