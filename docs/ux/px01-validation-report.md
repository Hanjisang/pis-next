# PIS V2 PX01 产品体验重构验证报告

状态：PX01 实现与本地验证完成；产品 Gate 以证据范围标记

验证环境：Windows、Java 21、Node 24、PostgreSQL 18.4、Playwright Desktop Chromium；本地合成账号和合成病例数据。

## 1. 实现范围

| 能力 | 结果 | 主要证据 |
| --- | --- | --- |
| UX Audit | PASS | `px01-current-ux-audit.md` |
| Personal Workbench | PASS（页面级） | 按技术员身份登录后显示个人工作台、工作项和权限过滤导航 |
| Case 360 | PASS | `GET /api/v2/case-workspaces/{caseId}`、统一 Header、材料树、责任链和报告页 |
| Case Timeline | PASS（查询级） | 组合审计/业务事实并映射为登记、取材、制片、诊断、医嘱、报告等业务语言；支持对象过滤 |
| Global Search | PASS | Ctrl+K 搜索结果统一进入病例中心 |
| Histology Facts | PASS（后端） | V26 `material_process_fact`，五阶段开始/完成/异常，状态由时间事实推导 |
| Diagnosis Viewer | PASS（合成引用） | 诊断工作区内打开 `V2ImageViewer`，支持缩放、还原、全屏入口、平移和缩略导航区 |
| Obsolete overview cleanup | PASS | 删除 `V2SectionOverview.vue`；配置和系统管理改为独立业务语言页面 |

## 2. PX01 Gate

| Gate | 状态 | 证据与边界 |
| --- | --- | --- |
| Gate A Personal Workbench | PASS | 真实认证下验证个人工作台和岗位工作项入口 |
| Gate B Permission UX | PASS（现有权限边界） | 登记员无诊断导航；核心动作仍由后端 `availableActions`/权限决定；未做医院组织目录联调 |
| Gate C Case 360 | PASS | 病例 Header、材料关系、责任链、报告和病例操作入口已在浏览器验证 |
| Gate D Timeline | PASS（查询级） | 病例历史和对象历史入口已验证；真实业务数据完整度取决于生产事实来源 |
| Gate E Global Search | PASS | 18 条浏览器场景均从 Ctrl+K/查询进入病例中心或对应工作区 |
| Gate F Registration | PASS（现有闭环） | 单页登记、多标本、病理号和搜索回到病例中心浏览器验证 |
| Gate G Grossing | CONDITIONAL | 病例级取材工作区、标本切换、蜡块区和历史入口已验证；本轮未在共享合成病例上再次写入/完成取材，避免污染回归夹具 |
| Gate H Histology | CONDITIONAL | 制片工作台与五阶段事实区已验证，后端开始/完成/异常为 PASS；浏览器本轮未修改共享玻片事实 |
| Gate I Diagnosis | CONDITIONAL | 诊断主工作区、固定上下文、责任/医嘱区、Viewer 和报告入口已验证；完整编辑→复诊→审核→签发变更链仍依赖既有 Core E2E/API 证据 |
| Gate J DigitalSlide Viewer | PASS（容器级） | 合成 `viewer://` 引用下验证诊断内阅片器、缩放和还原；真实 WSI/Tile Server 未验证 |
| Gate K Frozen | PASS（查看级） | F-000003 的第 1/2/3 轮、冰剩常规入口在 1920/1366 浏览器验证；本轮不重新写入冰冻轮次 |
| Gate L TechnicalOrder | PASS（入口级） | 技术医嘱工作台分栏和病例入口浏览器验证；技术结果写入链由既有 Core 测试覆盖 |
| Gate M Report History | PASS（查看级） | 原始报告 R001/R002/R003、签发人和历史列表在病例中心验证；当前夹具没有补充报告记录，补充语义由既有 Core 测试覆盖 |
| Gate N Visual System | PASS（桌面级） | 1920×1080、1366×768 运行并通过无横向溢出/可访问按钮检查；未进行真实用户可用性访谈 |
| Gate O Full E2E | CONDITIONAL | PX01 浏览器回归 18/18 PASS（9 个场景×2 分辨率）；这组是产品体验回归，不等同于医院全量业务写入验收 |
| Gate P Architecture | PASS | 后端 Architecture Drift/Module Boundary PASS；V2 生产代码未发现禁止平行模型依赖；Core Domain 文件未改 |

## 3. Test Evidence

### Backend

- `mvn -B -ntp clean verify`：PASS；48/48 tests，0 failures，0 errors，0 skipped。
- `V2MaterialProductionWebTest` 定向：6/6 PASS。
- PostgreSQL Testcontainers：PostgreSQL 18.4，Flyway 从空库 V1→V26 PASS。
- Flyway 仍输出“PG18.4 高于当前已测试支持版本 PG17”的兼容性 warning；未发现迁移失败。

### Frontend

- Prettier format check：PASS。
- ESLint：PASS，0 errors，0 warnings。
- TypeScript：PASS。
- Vitest：9 files，15/15 PASS。
- Vite production build：PASS。
- Playwright：18/18 PASS；desktop-1920 与 desktop-1366 各 9 个场景。

### Architecture / Safety

- `git diff --check`：PASS。
- 既有后端架构漂移测试：PASS。
- V2 生产代码禁止类型扫描：未发现 `BusinessRecord`、`PlannedBlock`、`ActualBlock`、`PlannedSlide`、`ActualSlide`、`ProcessingTask`、`EmbeddingTask`、`DiagnosisTask`、`ReportVersion`、`GenericTechnicalResult`、`TechnicalSlide`、`FrozenBlock`、`FrozenSlide`、`FrozenDiagnosis`、`FrozenReport` 或复杂 Histology 状态常量的新增依赖。
- 敏感模式扫描：未发现新增密钥、Token、私钥或本地密码；测试密码只通过运行环境注入。

## 4. Core Domain Changes

Core Domain modified files：`0`。

V26 只新增轻量 `material_process_fact` 支撑技术过程事实；它不改变 Case/Specimen/Block/Slide 生命周期，不创建复杂状态机，也不引入平行材料模型。

## 5. 未验证边界

以下不属于 PX01 本地产品体验验证，不能写成 PASS：真实 WSI/Tile Server、扫描仪、打印机、HIS/LIS/EMR、CA、生产部署、医院用户验收和正式历史数据迁移。

因此 PX01 产品代码已完成本地构建和体验回归，但“完整医院业务写入 E2E”仍标记 CONDITIONAL，待后续受控 Pilot/接口环境验证。

## 6. P0 / P1

- P0 UX：0 个已知阻断。
- P1 UX：0 个已知阻断。
- Site Integration：保留在后续阶段，不计入 PX01 Core UX 缺陷。

## 7. Domain Deviations

None。
