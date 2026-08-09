# PIS V2 UX01 当前前端审计

## 1. 审计目的

本审计用于确定 UX01 的重建边界。审计只评价当前前端是否支持病理科用户高效完成任务，不重新评价已经通过验证的 V2 Core Domain。

审计基线：`9c2a74b9214572252cabbca8b4f76a91348ca511`。

审计方法：

1. 静态检查 `frontend/src` 的页面、组件、路由、API 客户端和测试；
2. 在隔离的 PostgreSQL 18.4、Backend、Frontend 环境中使用合成账号和合成病例进行浏览器检查；
3. 检查 1920×1080 与 1366×768 两种桌面分辨率；
4. 按页面目的、首屏信息、跳转次数、弹窗依赖、技术术语、快捷操作、重复编辑和可访问名称分类；
5. 将页面归类为 `KEEP`、`REBUILD`、`MERGE` 或 `DELETE`。

## 2. 总体结论

当前 V2 前端已能调用正式 V2 API 并覆盖核心业务能力，但整体仍是面向实现者的“能力验证台”，不是面向病理科岗位的生产工作区。

主要问题：

1. 一级入口由查询参数和页面按钮拼接，缺少稳定的任务路由和角色导航；
2. 首页同时展示登记、技术、医生三类岗位，未回答当前登录用户“今天要做什么”；
3. 登记、取材、制片、冰冻均要求用户输入内部 Case ID、Specimen ID 或 Doctor ID；
4. 多处直接暴露 `ACTIVE`、`HISTOLOGY`、`INITIAL`、`TechnicalOrder`、`Material Tree`、`Responsibility Chain`、JSON Schema 等内部术语；
5. 取材和制片被合并到一个长页面，却没有形成病例级取材工作区或切片级生产队列；
6. Diagnosis 已具备聚合查询和核心命令，但视觉中心、固定上下文、责任链、技术结果、预览和底部动作仍未形成医生工作环境；
7. 冰冻页面以内部 ID 和命令表单组织，不能一眼看出当前轮次、材料、进度和下一步；
8. 技术医嘱配置与执行队列混在同一页面，且首屏出现重复项目和大量配置字段；
9. 数字切片、归档借阅、搜索、质控统计被塞入一个通用 Operations 页面，页面目的不明确；
10. 1366×768 下巨型品牌区占据首屏，真实待办需要滚动后才能看到；
11. 当前错误消息主要沿用 API 错误码和英文消息，缺少“发生了什么、为什么、下一步怎么办”；
12. 当前没有正式 Playwright 工程，浏览器回归证据无法由仓库直接复现。

结论：保留已经正确的 API 边界和少量登录能力，重建信息架构及主要工作区；被替代页面不得长期并存。

## 3. 页面分类

| 当前资产 | 分类 | 审计结论 | UX01 处理 |
| --- | --- | --- | --- |
| `App.vue` 应用外壳 | REBUILD | 巨型 Hero、查询参数路由、所有角色共用导航、重复页脚返回入口 | 建立桌面应用壳、任务路由、角色导航、当前身份与全局搜索 |
| `V2Login.vue` | KEEP | 登录目的和认证链清楚，但存在英文眉题与测试账号说明 | 保留认证行为，统一视觉与错误反馈；测试账号提示仅限测试运行时 |
| `V2Home.vue` | REBUILD | 是功能入口和角色卡片集合，不是真实岗位待办；管理员同时看到所有岗位 | 按当前角色显示待办数量、异常和快捷任务 |
| `V2RegistrationWorkbench.vue` | REBUILD | 暴露来源系统、外部标识、技术标签、Case ID、生命周期和软删除；病例与标本分成演示步骤 | 重建为单页登记工作区，患者/就诊、申请、业务类型、多个标本和确认登记连续完成 |
| `V2MaterialProductionWorkbench.vue` | DELETE | 只负责把取材和制片两个独立任务堆在同一页面 | 由独立 `/v2/grossing/:caseId` 与 `/v2/production` 路由替代 |
| `V2GrossingWorkbench.vue` | REBUILD | 依赖 Case ID、Specimen ID、医生 ID；一次创建一块；内部英文和事实转换占据首屏 | 重建病例级取材工作区，左侧标本、中心描述与快速蜡块、固定完成动作 |
| `V2SlideProductionWorkbench.vue` | REBUILD | 依赖 Case ID，围绕 Material Tree 和 `INITIAL`，不是待制片队列 | 重建切片级队列、扫码完成、批量完成、打印与补打反馈 |
| `V2DiagnosisWorkspace.vue` | REBUILD | 已有单一 Workspace Query 和正式业务命令，但两栏布局、内部术语、内嵌小预览和责任 ID 仍以模型为中心 | 保留 API 和命令，重建固定 Context Bar、上下文侧栏、诊断编辑中心、责任/医嘱侧栏、固定 Action Bar 和大面积预览 |
| `V2TechnicalWorkbench.vue` | REBUILD / MERGE | TechnicalProject 配置与执行队列混排，暴露 JSON Schema、Target、BLOCKING 和英文状态 | 执行工作台按待处理/处理中/待录结果/已完成组织；项目配置移入配置中心 |
| `V2OperationsWorkbench.vue` | DELETE | 冰冻、数字切片、归档借阅、搜索、质控统计共享一个通用技术页面 | 拆分为各自任务工作区，复用现有 API，不保留通用 Operations 主页面 |
| `v2Api.ts` | KEEP | 正式认证、病例和标本 API 可继续使用 | 增加业务语言映射和统一可操作错误，不把聚合逻辑放进全局 Store |
| `v2MaterialApi.ts` | KEEP / MERGE | 材料命令完整，但类型和错误直接暴露内部状态 | 保留命令，补充适合工作区的查询适配和中文显示模型 |
| `v2DiagnosisApi.ts` | KEEP | 已提供单次 Workspace Query、availableActions、责任、技术医嘱、报告历史 | 作为 Diagnosis 重建基础；仅在确有瀑布请求时调整 Query DTO |
| `v2BusinessApi.ts` | KEEP / MERGE | 覆盖冰冻、数字、归档、搜索和质控，但调用入口分散 | 按工作区拆分前端显示模型，保留服务端事实边界 |
| `styles.css` | REBUILD | 页面级样式混合、Hero 过大、表格/状态/间距缺少统一令牌 | 建立 Design Tokens、密度、语义色、焦点、加载与桌面断点 |
| 现有 Vitest 测试 | REWRITE / EXPAND | 仅覆盖组件片段，并断言部分旧技术语言 | 改为断言业务上下文、主要动作、角色导航、错误提示和材料树结构 |
| 浏览器业务回归 | REBUILD | 仓库内没有可执行 Playwright 项目，旧报告不能替代可复现测试 | 新增 UX Smoke 与核心交互 Playwright 场景，保留真实浏览器人工检查证据 |

## 4. 主要页面审计

### 4.1 应用外壳与工作台

当前首屏先显示约 280px 高的品牌 Hero，再显示 13 个一级按钮。1366×768 下，真实岗位待办卡片只露出标题，用户必须先滚动。

管理员登录后同时看到“登记员”“取材/技术”“诊断医生”三组卡片；普通岗位也沿用同一静态结构。页面没有待办数量、超时、退回或技术结果返回等事实。

处理决定：`REBUILD`。

### 4.2 登记

浏览器首屏出现：

- 来源系统；
- 外部申请标识；
- 患者上下文引用；
- 就诊上下文引用；
- 来源引用；
- 技术标签；
- `ACTIVE`；
- Case ID；
- “独立 V2 API”；
- “不维护病例/标本流程状态机”。

这些字段适合接口或调试，不适合登记员。登记病例后还需在下一张卡片逐个登记标本，无法一次维护多个标本、复制上一标本、排序或明确确认。

处理决定：`REBUILD`。

### 4.3 取材

当前页面要求手工输入 Case ID、多个 Specimen ID、Grossing Doctor ID 和 Recorder ID；蜡块需要按“来源 Specimen ID、Block code、Block type”逐块建立。完成和重开以“Fact transition”展示。

用户无法在同一屏内看清全部标本、当前标本描述和已有蜡块，也没有 Enter 新增、复制、编号自动生成和补打。

处理决定：`REBUILD`。

### 4.4 制片

当前制片区位于取材页面下方，先输入 Case ID，再读取 Material Tree。页面声明“每个 Slide 保留内部 ID、版本号和打印/重打审计记录”，并突出 `INITIAL`。

这不能回答“今天有哪些片、哪些未完成、有没有漏、如何补打”。

处理决定：`REBUILD`。

### 4.5 Diagnosis Workspace

当前优点：

1. 使用一次 Workspace Query 获取病例、材料、诊断、责任、技术医嘱和报告；
2. 核心诊断命令和 availableActions 已存在；
3. 诊断、技术医嘱、报告没有依赖 Legacy。

当前问题：

1. 空页面要求粘贴 Case 内部 ID；
2. 顶部仍显示 `V2 · DIAGNOSIS RESPONSIBILITY` 和 `Diagnosis Workspace`；
3. 材料区显示 `HISTOLOGY · ACTIVE`、`Material Tree` 和“0/0 初始切片完成”；
4. 技术医嘱区显示 Project、Target type、Target ID、Parameters JSON；
5. 责任区显示 `Responsibility Chain` 和角色枚举；
6. 报告阻断原因直接显示 `DIAGNOSIS_NOT_CREATED` 等代码；
7. 报告预览被压缩在小区域；
8. 保存、提交、预览和签发没有形成稳定的固定底部操作条。

处理决定：`REBUILD`，保留 API 与业务命令。

### 4.6 冰冻

当前页面要求输入 Frozen Case 内部 ID，再依次点击“开始新轮次”“登记标本”“建立快速诊断”“进入快速诊断与签发”“打开本轮材料生产”“结束冰冻并转常规”。

页面没有病例 Context Bar、Round 时间线、当前轮次材料、当前操作或新送检边界提示。用户必须理解内部命令顺序。

处理决定：`REBUILD`。

### 4.7 技术医嘱

当前首屏是 TechnicalProject Configuration，包含 Business type ID、Allowed targets、Parameters schema、Result schema、Slide/Block/Result、Blocking default 等字段；执行队列排在配置之后。浏览器中同一内置项目还出现重复列表项。

技术人员进入页面后看不到按待处理、处理中、待录结果、已完成组织的任务。

处理决定：执行页面 `REBUILD`，配置能力 `MERGE` 到配置中心。

### 4.8 数字切片、归档借阅、查询、质控统计

四类任务当前由同一个 Operations 组件按模式切换，均以内部 ID 和通用命令表单为主。全局查询只在首页存在，不是任何页面可用的 Ctrl+K Drawer。

处理决定：通用页面 `DELETE`，按任务拆分并保留现有 Core API。

## 5. 路由审计

当前路由本质是 `?workspace=...&caseId=...`，没有稳定的任务 URL。目标路由：

| 任务 | 目标路由 |
| --- | --- |
| 工作台 | `/v2/workbench` |
| 登记 | `/v2/registration` |
| 取材 | `/v2/grossing/:caseId` |
| 制片 | `/v2/production` |
| 诊断 | `/v2/diagnosis/:caseId` |
| 冰冻 | `/v2/frozen/:caseId?` |
| 技术医嘱 | `/v2/technical-orders` |
| 报告 | `/v2/reports` |
| 归档借阅 | `/v2/material-custody` |
| 查询 | `/v2/search`，同时提供全局 Ctrl+K |
| 质控统计 | `/v2/quality` |
| 配置 | `/v2/configuration` |
| 系统管理 | `/v2/system` |

为避免一次重构引入新的路由依赖，可先使用 History API 的轻量任务路由器；路由语义必须达到以上形式，旧查询参数入口在内部引用清零后删除。

## 6. API 与状态管理审计

1. Diagnosis 已有合格的 Workspace Query，不需要前端调用 15 个 API 拼接领域事实；
2. 登记、取材和制片当前偏命令式，工作台队列查询不足。UX01 只允许增加 Query DTO、Projection、Workspace Query、AvailableActions 和 Batch Commands；
3. 前端状态应分为服务端事实缓存和当前工作区草稿，不建立全局 Case Store；
4. API 错误必须统一映射为业务语言，并保留可追踪错误码用于支持人员排查；
5. 当前身份应从认证链获取角色、权限和 DoctorIdentity，不允许页面录入医生内部 ID 作为常规路径。

## 7. 可访问性与反馈审计

保留项：现有多数输入和按钮已经具备可访问名称，登录页可使用键盘完成。

必须修正：

1. 业务页面的大量文本框使用技术字段名作为 label；
2. 同名按钮在同一页面出现时缺少上下文名称；
3. 焦点样式和键盘顺序未统一；
4. 状态主要依赖卡片颜色和英文状态文本；
5. 长操作缺少稳定的进度区域；
6. Toast 不能作为扫码成功或失败的唯一反馈；
7. 空状态应描述当前任务，而不是仅显示无数据。

## 8. UX01 重建边界

本轮重建前端信息架构和交互支持层，不修改以下 Core Domain 决定：

- Case 生命周期仅 `ACTIVE/CANCELLED`；
- Specimen、Block、Slide、Diagnosis、TechnicalOrder、Report 的独立事实和生命周期；
- FrozenRound 多轮模型；
- Responsibility 责任事实；
- Report 签发、撤回、重签和补充语义；
- DigitalSlide、Archive、Loan、QC 的既有领域边界；
- V2 与 Legacy Business 的隔离。

若交互需要更多数据，优先增加只读 Query DTO 或工作区查询，不在 Pinia 或页面组件中重新实现领域聚合。

## 9. Gate 前置判定

| UX Gate | 当前状态 | 主要阻断 |
| --- | --- | --- |
| A Diagnosis | FAIL | 内部 ID 入口、技术术语、非固定 Context/Action、预览过小 |
| B Registration | FAIL | 技术表单、病例与标本割裂、无多标本单页维护 |
| C Grossing | FAIL | 按 ID 和单块命令操作，不是 Case 多标本工作区 |
| D Production | FAIL | 以材料树和内部状态为中心，没有切片队列与扫码反馈 |
| E Frozen | FAIL | 轮次、材料和下一步不清晰 |
| F New User Comprehension | FAIL | 多个任务必须理解内部对象、ID 和命令顺序 |

UX01 实施完成后必须重新运行本表的任务场景，不能用“页面能打开”替代业务结果断言。
