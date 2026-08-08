# PIS-Next V2 P07 前端设计基线

状态：已完成（设计封版）
版本：V2-0.2
日期：2026-08-08
技术基线：Vue 3、TypeScript、Vite；以现有前端实现为过渡资产，不把现有 P15–P19 页面当作 V2 最终信息架构

## 1. 体验目标

V2 前端面向病理登记人员、取材/技术人员、诊断医生、审核人员、归档人员、质控人员和系统配置人员。核心体验不是“浏览数据库表”，而是围绕角色待办、病例上下文、材料来源、责任链和可审计命令完成工作。

必须做到：

1. 任何关键操作都显示前置条件、当前责任、版本冲突和审计结果。
2. 用户从一个病例上下文即可查看和处理相关标本、蜡块、切片、数字切片、技术医嘱、诊断和报告，不在多个孤立模块之间跳转。
3. 角色看到自己的工作队列和权限范围，不默认展示全院病例列表。
4. 医疗事实和报告版本以只读快照展示；可执行命令由后端 capabilities 返回，前端不自行推断权限。
5. 密集信息可扫描、可键盘操作、可筛选和可追溯；警告不能只用颜色表达。

## 2. 顶层信息架构

顶层导航按工作场景组织，权限动态裁剪：

Workbench、Registration、Grossing、Processing、Diagnosis、Frozen、TechnicalOrder、Report、Archive/Loan、Search、QC/Stats、Config、System。

Workbench 是按角色聚合的入口，不是全量病例表。各角色默认入口如下：

| 角色 | 默认工作台 | 主要队列 |
|---|---|---|
| 登记人员 | Registration Workbench | 待映射申请、病理号冲突、待接收标本 |
| 取材人员 | Grossing Workbench | 待取材、补取材、冰冻取材、待完成材料 |
| 技术人员 | Processing / TechnicalOrder Workbench | 待认领订单、批次、待完成 Block/Slide、异常 |
| 诊断医生 | Diagnosis Workbench | 我的初诊、审核、数字切片待阅、技术结果待确认 |
| 审核人员 | Review Workbench | 待审核责任链、报告签发前检查、QC 警告 |
| 归档/借用人员 | Archive/Loan Workbench | 待归档、借出、逾期、归还和位置冲突 |
| 质控人员 | QC/Stats Workbench | 断链、超时、异常、撤回和对账指标 |
| 配置管理员 | Config Workbench | 业务类型、编号、规则、模板、分派和质控配置 |

路由必须支持深链接到 Case Context、Diagnosis Workspace 和受控命令结果页；返回操作回到来源队列，并保留筛选条件。

## 3. Diagnosis Workspace 详细线框

诊断工作区是 V2 的核心组合视图，不能要求医生在 Case、材料、技术医嘱、报告四个菜单间往返。

页面结构：

1. Header：Case ID、PathologyNo、BusinessType、患者/就诊最小快照、当前责任人、病例风险、数据刷新时间和锁定/版本提示。
2. 左侧 Case Context：
   - Application：申请项目、来源系统、外部标识；
   - Specimen：标本名称、部位、接收和取材摘要；
   - Grossing：初始/补充/冰冻轮次、取材描述；
   - Block：蜡块编号、来源、外部标记和状态；
   - Slide：切片编号、用途、完成时间、来源上下文；
   - Digital：平台、图像 ID、查看器入口、扫描状态；
   - History：责任、状态、审计和异常时间线。
3. 右侧 Diagnosis：
   - 模板版本和诊断能力提示；
   - 结构化诊断字段；
   - microscopic findings；
   - final diagnosis；
   - comment 和动态模板字段；
   - 草稿版本、保存状态、冲突提示和提交审核按钮。
4. 底部固定操作区：
   - TechnicalOrder：创建、查看、认领、结果摘要；
   - Review：责任链、审核意见、退回原因；
   - Audit：关键操作和前后值摘要；
   - Preview：报告模板和渲染快照；
   - Sign：签发前置条件、签字确认和结果；
   - Report：有效、撤回、补充、更正和重新签发关系。

推荐桌面布局为 Header 56–72px、左侧上下文 280–360px、右侧主编辑区自适应、底部命令条 64–80px。小于 1024px 时左侧上下文改为可展开抽屉，底部操作区保留可键盘访问的固定区域，不能产生页面级横向滚动。

诊断编辑必须展示“正在编辑的 Diagnosis 版本”和“已签发 Report 只读”边界。后端返回的 capabilities 决定按钮是否显示；不可执行命令也要在查看权限内说明缺失前置条件，而不是静默消失。

## 4. Material Tree

Material Tree 是所有工作台共享的可复用投影组件：

Specimen → Block → Slide → DigitalSlide
Specimen → Slide（直接切片）
External Block → Local Slide
External Slide（无本地 Block）

节点必须显示材料编号、类型、来源、外部标志、完成状态、当前责任和最近审计时间。点击节点进入同一 Case Context 的局部定位，不打开脱离上下文的孤立详情页。树节点的展开、选中、禁用和加载状态必须使用动态 aria-expanded、aria-selected 和 aria-busy。

来源异常（缺 Block、缺 Specimen、外部证明缺失、重复编号）同时显示图标、文字和可访问的状态标签；颜色不能成为唯一含义。

## 5. 全局搜索和上下文进入

Ctrl+K 打开 Global Search Drawer。支持病理号、标本号、蜡块号、切片号、外部标识和权限允许的患者索引。

搜索交互：

1. 输入 2 个字符后才开始防抖查询，显示查询范围和当前院区；
2. 结果按 Case、Specimen、Block、Slide、TechnicalOrder、Report 分组；
3. 每个结果显示编号、类型、业务类型、最近时间和权限提示；
4. 选中后进入 Case Context，并在左侧 Material Tree 自动定位；
5. 病理号冲突、重复 Case 或权限不足显示明确数据质量/权限反馈；
6. 搜索历史只保存非敏感的编号和最近上下文，不保存完整患者信息。

## 6. 表单、命令和反馈

登记、取材、技术医嘱、诊断和报告使用命令表单，不使用通用表编辑器。

- 所有字段有可见标签、单位/格式提示、必填和业务原因；
- 校验错误贴近字段显示，同时在表单顶部提供可聚焦错误摘要；
- 提交中按钮显示处理中并防止重复点击，重复命令显示幂等结果；
- 版本冲突显示当前版本、用户正在编辑的版本和重新加载/保留草稿选择；
- 高风险命令使用确认对话框、原因字段和二次权限校验；
- 成功反馈包含操作编号和下一步入口，失败反馈包含是否可重试；
- 不通过前端修改 lifecycle、completed、signed 或 withdrawn 字段。

当前技术栈采用 Vue 3/TypeScript；组件必须动态绑定 ARIA 状态，复杂表单使用统一验证方案，避免各页面复制手写校验。是否引入额外表单库属于实现阶段 ADR，本轮不增加依赖。

## 7. 视觉和可访问性基线

采用面向临床工作站的高对比、信息密集、低动效视觉基线：

- 主色使用专业蓝，成功/可继续使用服务绿，破坏性和阻断使用红色；颜色必须映射到语义 token，不在组件中散落原始色值；
- 正文基础字号不小于 16px，行高约 1.5，表格密度通过间距和列组织控制，不用过小文字；
- 普通文本对比度至少 4.5:1，焦点环清晰且不能被组件覆盖；
- 图标采用一致的 SVG 图标集，不使用 emoji 代替医疗状态或操作；
- 可点击区域和图标按钮至少 44×44px，键盘焦点顺序与视觉顺序一致；
- 动效只用于状态变化和空间连续性，持续约 150–300ms；尊重 prefers-reduced-motion；
- 断点至少验证 375px、768px、1024px、1440px；桌面密集工作台优先，但不得横向溢出；
- 表格、树、时间线和状态标签同时提供文本、图标和 aria-label；
- 不使用 hover 作为唯一反馈或唯一操作入口。

## 8. 配置和模板界面

Config Workbench 按配置实体分组维护：

BusinessType、ApplicationItemMapping、PathologyNumberRule、SlideRule、PrintRule、TechnicalProject、DiagnosisTemplate、ReportTemplate、AssignmentRule、QCRule、TechnicalNodeConfig。

每个配置页显示 scope、enabled、priority、version、生效时间和历史版本。已发布模板和规则进入只读状态，编辑生成新版本并显示差异。DiagnosisTemplate Editor 与 ReportTemplate Editor 分离，分别提供 schema/字段预览和报告渲染/打印预览，不让管理员直接编辑核心业务表。

## 9. 前端数据和组件边界

1. api client 只负责 HTTP、错误合同、幂等键和版本头；领域命令组合放在 feature service。
2. Case Context、Material Tree、Responsibility Chain、CommandBar、Audit Timeline、QC Warning 使用共享组件，但字段由模块 DTO 明确提供。
3. Query cache 允许失效和重取；本地草稿必须带 diagnosisId、baseVersion、savedAt 和敏感数据清理策略。
4. 权限和 capabilities 来自后端；前端路由守卫只做导航级保护，服务端仍是最终授权者。
5. Loading、empty、error、stale、conflict、forbidden、withdrawn、external 状态分别设计，不能都显示成空白。
6. 前端测试覆盖组件状态、键盘导航、命令重复提交、版本冲突、脱敏和角色投影。

## 10. P07 封版结论

P07 已定义角色工作台、Diagnosis Workspace 线框、Material Tree、Global Search、命令反馈、可访问性、配置界面和前端组件边界。当前 P15–P19 workbench 仅作为实现过渡资产；新 V2 页面必须先通过 P08 的工作台、权限、并发和端到端场景验收。
