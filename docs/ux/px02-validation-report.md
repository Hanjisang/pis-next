# PIS V2 PX02 验收报告

## 1. 验收范围

PX02 只验证本地产品体验：个人工作台、权限导航、病例上下文、业务历史、登记、取材、轻量 Histology、诊断工作区、数字切片本地 Viewer、配置、系统管理、报告队列和桌面布局。

真实医院接口、厂商设备、CA、生产部署、正式数据迁移和 Pilot 不属于本次验收，统一标记为 `SITE INTEGRATION NOT VERIFIED`。

## 2. Final Gate

| Gate | 状态 | 证据 |
| --- | --- | --- |
| A Personal Workbench | PASS | 真实认证账号加载 `/api/v2/my-workbench`；个人责任、技术结果关注和公共病例池分离。 |
| B Permission-driven Navigation | PASS | Registrar/Doctor/Technician/Admin 浏览器回归验证按权限显示入口；权限维度页面可真实保存。 |
| C Histology Workbench | PASS | PX01A-GH 真实执行五个技术事实阶段、异常记录、取材和制片；1920/1366 均通过。 |
| D Diagnosis Componentization | PASS | Diagnosis Shell、Evidence、Editor、Viewer、Technical/Responsibility/Report UI 边界已建立；诊断链回归通过。 |
| E Tiled Viewer | PASS | 本地 DZI/tiles fixture 真实验证打开、缩放、平移容器、导航器、全屏进出和两张数字切片切换。 |
| F Real Configuration Pages | PASS | Admin 浏览器读取真实配置快照并保存申请项目映射。 |
| G Real System Administration | PASS | Admin 浏览器读取并保存用户、Doctor Identity、组织范围和 BUSINESS/DATA/ACTION 权限。 |
| H Registration Cleanup | PASS | 浏览器从登记开始创建独立病例和多个标本；正式页面读取 ApplicationItemMapping，不写入 SYNTH 常量。 |
| I Case 360 Refinement | PASS | Case 360 首屏上下文、材料、责任、报告和最近业务历史可见；搜索和业务入口可进入。 |
| J Deep-link Search | PASS | Ctrl+K debounce、上下键、Enter/Escape，以及病例/材料/报告类型落点已验证。 |
| K Report Queue | PASS | Report Center 读取真实队列，点击已签发报告进入带 reportId 的病例诊断上下文。 |
| L Visual Density | PASS | 1920×1080 与 1366×768 全套浏览器回归通过，无页面横向溢出。 |
| M Architecture | PASS | Core Domain 变更文件 0；无 PX02 新增平行 Domain 或复杂 Histology 状态机。 |
| N Full Browser E2E | PASS | 15/15 场景在 1920×1080 通过，15/15 场景在 1366×768 通过。 |

## 3. Test Evidence

- Backend Maven：`49/49`，Failures `0`，Errors `0`，Skipped `0`。
- Frontend format：PASS。
- Frontend lint：PASS，0 errors / 0 warnings。
- Frontend typecheck：PASS。
- Frontend unit：`9` files / `15` tests，全部 PASS。
- Frontend build：PASS；产物包含独立 OpenSeadragon chunk。
- Browser E2E：`15/15` at `1920×1080`；`15/15` at `1366×768`。
- PostgreSQL/Flyway：Testcontainers PostgreSQL `18.4` clean bootstrap，schema latest `v26`；Flyway 仅报告 PostgreSQL 18.4 尚未被当前 Flyway 版本正式支持的兼容性 warning。
- Local runtime：PX02 frontend/backend health UP；`/api/v2/my-workbench` 和 `/api/v2/report-center` authenticated request 返回 200。
- Database write evidence：浏览器测试真实写入 Case、Specimen、Grossing、Block、Slide、Histology fact、DigitalSlide、Diagnosis、TechnicalOrder、Responsibility、Report 和 Timeline 事实。

## 4. Architecture and Scope Checks

- Core Domain modified files：`0`。
- Legacy Business Dependency：`0`。
- Complex Histology State Machine：`0`。
- Production UI synthetic registration constants：`0`；synthetic application mappings remain only as migration/demo seed data。
- Role-name business gating where permissions suffice：`0` in PX02 navigation/workbench。
- `<img>`-only WSI implementation：`0`；tiled source uses `ImageViewerAdapter`。
- Static configuration/admin placeholder pages：`0`。
- Sensitive scan：未发现新密码、Token、私钥或生产连接信息；E2E 密码仅由运行环境变量注入。

## 5. Domain Deviations

None. PX02 未修改 Core Domain 文件、数据库核心生命周期或既有业务不变量。

## 6. Remaining Issues

### P0/P1 Core Product

`P0 = 0`，`P1 = 0`。

### Site Integration

以下内容明确不属于 PX02，本报告不将其伪装为 PASS：

- 真实 HIS/LIS/EMR 接口联调；
- 厂商打印机、扫描仪和医院 WSI 平台；
- CA/电子签章；
- 生产容量、部署和监控；
- 正式历史数据迁移；
- Hospital Pilot / Cutover。
