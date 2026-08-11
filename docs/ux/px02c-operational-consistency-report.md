# PIS V2 PX02C Operational Consistency Closure Report

日期：2026-08-11

基线：`9b7603d13ef9202d5b1dcd566f2f29766a6413ea`
范围：PX02C 本地产品一致性收口；不包含真实 HIS/LIS/EMR、设备、CA、迁移、Pilot 或生产部署。

## Final Gate

| Gate | 状态 | 验收依据 |
| --- | --- | --- |
| A History Business Identity | PASS | Timeline Projection 提供 `targetDisplayCode/targetDisplayName`；History Drawer 不渲染 `targetId`；无业务编号时只显示对象类型。 |
| B Structured Changes | PASS | 诊断、报告撤回、标本和蜡块修改写入结构化 `changes`，UI 直接显示字段、修改前和修改后。 |
| C History Classification | PASS | 新审计事件写入 `categoryCode`；前端优先使用投影分类，旧数据才使用兼容 fallback。 |
| D Histology Single Projection | PASS | 正式查询为 `GET /api/v2/histology-workbench`；生产页只以该投影为主数据源，未再调用旧生产列表并在前端 merge。 |
| E Histology UX | PASS | 页面一级队列收敛为待脱水、待包埋、待切片、待染色、待封片、异常、已完成；`PENDING/IN_PROGRESS` 不再作为一级用户语义；打印和扫码是操作。 |
| F Inbound Application Boundary | PASS | 建立 `InboundApplicationSource` 与 `InboundApplicationInbox`；正式生产无 source 时显示“当前尚未连接申请来源”；local/test 使用独立适配器。 |
| G Registration Queue | PASS | 浏览器真实选择入站申请并登记；登记后从待登记队列消失；取消申请显示不可登记并被禁用；手工登记保持独立入口。 |
| H Loan Due/Overdue | PASS | 借阅记录保存借阅人、科室、用途、预计归还；今日到期、逾期、已归还由时间事实推导，归还记录实际时间。 |
| I Patient History | PASS | 既往病例查询排除当前病例，按签发时间优先排序；诊断工作区显示报告入口和可用的旧数字切片入口。 |
| J Visual Cleanup | PASS | 生产页移除本轮范围内的工程/架构说明文案，保留业务提示、异常和操作反馈。 |
| K Browser E2E | PASS（PX02C 范围） | PX02C 4 场景 × 2 分辨率 = 8/8；PX02B 回归 3 场景 × 2 分辨率 = 6/6。均包含真实 UI 写入和查询回显。 |
| L Architecture | PASS | 后端架构漂移测试通过；核心 Domain 未修改；旧 Histology 查询路径和旧生产投影已从生产代码移除；历史 UUID 仅保留 API 内部定位。 |

## Build vs Reuse

- History：REFACTOR，继续复用 Audit、Domain Facts 和 Timeline Projection。
- Histology：REFACTOR，统一为一个工作台 Query Model，不创建新的领域状态机。
- Inbound Application：BUILD boundary，local/test adapter 仅用于本地申请来源，不进入 Case Domain。
- Loan：REFACTOR，补齐预计归还、科室和借阅查询投影，继续从时间事实推导逾期。
- Patient History：REFACTOR，复用现有查询并补充当前病例排除、报告和旧数字切片落点。
- UI：REFACTOR，继续复用现有 Workspace、History Drawer、Case 360 和材料模型。

## Test Evidence

### Backend

```text
.\mvnw.cmd -B clean verify -DskipTests=false
Tests run: 49, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### PostgreSQL / Flyway

- Testcontainers PostgreSQL 18.4：通过。
- Flyway clean bootstrap：V1 → V27 通过。
- 本地运行容器已应用 V27，启动日志显示 schema version 27。
- Flyway 对 PostgreSQL 18.4 的“最新支持版本为 17”提示仍存在；这是兼容性提示，不是迁移失败。

### Frontend

- Prettier format check：PASS。
- ESLint：PASS，0 errors / 0 warnings observed。
- Typecheck：PASS。
- Unit：9 test files，15 tests，全部 PASS。
- Production build：PASS；OpenSeadragon bundle 正常构建。

### Browser E2E

PX02C 新增场景：

1. Local inbound application → registration → registered projection。
2. Histology 单一投影 → 脱水至封片 → 玻片完成。
3. History Drawer → 业务编号、结构化 before/after、无 UUID。
4. Loan → 预计归还 → 今日到期 → 逾期 → 归还。

结果：

- 1920×1080：4/4 PASS。
- 1366×768：4/4 PASS。
- PX02B 回归：1920×1080 3/3 PASS；1366×768 3/3 PASS。
- 以上场景均使用 Playwright 通过浏览器执行写操作，并验证 API/Query 结果与 UI 回显。

全仓 `npm run test:e2e` 的 44 条历史测试中，当前运行结果为 24 PASS、20 FAIL。失败项不属于 PX02C 新增验收场景：其中 14 条依赖未提供的 `PIS_E2E_*_PATHOLOGY_NO` 外部演示病例环境变量，另有 PX01A 旧测试仍断言已被 PX02C 收敛的旧工作台标签。PX02C Gate K 以本报告列明的独立写入场景和 PX02B 回归为准；该历史测试现状不隐藏，后续应在单独的测试夹具/旧测试清理任务中处理。

## Architecture Evidence

- `V2ArchitectureDriftTest`：2/2 PASS。
- `ModuleBoundariesTest`：1/1 PASS。
- `SiteIntegrationArchitectureTest`：2/2 PASS。
- 正式 Histology 页面仅请求 `/api/v2/histology-workbench`；旧 `/api/v2/histology/workbench` 和 `/api/v2/slides/production-workbench` 仅作为 E2E 负向断言字符串保留。
- `Case/Specimen/Block/Slide` 等核心 Domain 文件未修改；本轮变更集中在 Application、Query/Projection、Infrastructure、Web、UI、测试 schema 和 V27 migration。
- `targetId` 仍作为 API 内部过滤定位字段存在，但正式 History UI 不展示 UUID。
- `SYNTH-*` 仅保留在历史 migration 与 `local/test` fixture；正式注册 UI 不显示这些内部 fixture code。
- 不引入 Loan 状态机；逾期由 `now > expectedReturnAt && actualReturnAt == null` 推导。

## Core Domain Changes

目标：0。
结果：0 个不必要 Core Domain 文件修改。

本轮没有新增平行 Frozen/Histology/History/Loan 领域实体，也没有恢复 Legacy Business 依赖。

## Domain Deviations

None。

## P0 / P1

- P0：0。
- P1：0（PX02C 本地产品范围）。

## Known Site Integration Gaps

以下明确不属于 PX02C，仍未验证：

- 真实 HIS / EMR / LIS 接口。
- 真实打印机、扫描仪和厂商 WSI 平台。
- CA / 电子签章。
- 正式历史数据迁移、生产容量、部署、Pilot 和 Cutover。

## Stop Point

PX02C 完成后停止继续产品重构，不开始 PX03，也不自动进入 Site Integration。下一步交由人工在本地浏览器进行产品体验验收。
