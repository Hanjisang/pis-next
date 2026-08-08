# PIS-Next V2 P07 前端计划

文档状态：计划
文档版本：V2-0.1

## 1. 核心体验

Diagnosis Workspace 是最重要的工作界面：病例上下文、Specimen/Block/Slide 来源、诊断模板、技术医嘱、责任链和报告预览应在同一工作上下文中完成，避免医生为完成诊断跳转多个孤立模块。

## 2. 页面边界

- 通用 Shell：导航、权限、通知、用户上下文和审计提示；
- Registration：申请映射、人工登记、多 Case/多 Specimen；
- Material Workbench：Grossing、Block、Slide、打印和完成事实；
- Diagnosis Workspace：结构化模板、自由文本、依据引用、责任链和 TechnicalOrder；
- Report：Preview、Sign、Withdraw、Resign、Supplement 和固定快照；
- Frozen：Round、追加标本、冰冻报告和冰剩转常规；
- Archive/Loan：材料级位置、借用和历史；
- Global Search Drawer：`Ctrl + K` 搜索并进入 Case context；
- Operations：接口异常、QC、审计和投影查询。

## 3. UI 约束

前端不创建或提交 CaseStatus，不把打印结果显示成材料形成，不把扫描完成显示成诊断完成，不允许隐藏已签发报告历史。敏感操作必须显示权限、原因、责任和审计结果。

具体组件、交互稿、可访问性和端到端场景待 P08 测试计划与业务流程确定后实施。
