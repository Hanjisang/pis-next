# SRS V1.4 → PIS V2 实施日志

## 2026-08-14：报告时效、延迟登记与超期统计闭环

关闭 `RPT-022`、`RPT-035`、`STAT-007`、`STAT-008`、`CFG-008`。V49 新增医院/业务类型级 `report_tat_policy` 与报告延迟事实；时效起点明确为 `CASE_REGISTERED`，报告中心计算登记至当前或签发的分钟数，区分未配置、正常、临期、超期、按时签发和超期签发。临期/超期待签发报告可登记受控原因、说明和预计签发时间；同一诊断只允许一个活动登记，幂等重放返回原事实，人工关闭保留关闭说明，正式签发在同一事务中自动关闭活动登记。

假设与待确认：SRS 未给出各医院正式报告时效数值，且质量属性参数将报告时限标记为“未确认”。为避免把推测写成临床规则，迁移不种任何阈值；管理员必须按业务类型输入提醒/目标分钟并显式启用。当前策略用于在途报告和管理统计，延迟登记会固化当时策略版本及目标时间；医院验收时应确认业务类型阈值和是否需要工作日历，后者如确认将以策略扩展实现，不改变现有报告/诊断生命周期。

质控统计页从当前医院病例和不可变签发报告计算平均签发分钟、按时率、临期/超期/延迟数量，并提供真实超期明细，不使用 fixture 数字。权限边界为管理员 `P14-PERM-001` 配置、报告查询 `P14-PERM-055` 读取、报告签发 `P14-PERM-036` 登记/关闭；配置、登记和关闭均写审计。验证证据：`V2ReportWebTest` 覆盖策略配置、并发版本冲突、超期投影、统计、延迟幂等、人工关闭、再次登记和签发自动关闭；15 个 PostgreSQL/Testcontainers 测试类共 20 tests 通过，其中 `V49ReportTatExistingDatabaseUpgradeTest` 在 PostgreSQL 18.4 通过 V48→V49 顺序升级且确认无默认阈值；前端 format、typecheck、lint、17 个测试文件共 37 tests 和 production build 全部通过，3 个相关测试文件共 6 tests，`report-tat-delay.spec.ts` 在 1920/1366 两档共 2 个浏览器用例通过。覆盖矩阵复算为 `TOTAL=742 / COMPLETE=338 / PARTIAL=120 / MISSING=204 / EXTERNAL_DEPENDENCY=80 / CONFLICT_RESOLVED_BY_V2=0`。

## 2026-08-14：报告模板设计器与常用肿瘤结构闭环

关闭 `RPT-003`、`RPT-009`。配置中心的报告模板不再只允许改名称：管理员可创建医院级模板，在结构化设计器中配置报告标题、通用/肿瘤类别、肿瘤部位代码、A4页码、版块顺序、版块数据来源及字段编码。保存总是追加新的 DRAFT；发布经过服务端 schemaVersion、标题、类别、肿瘤部位、页面、版块唯一性、来源和字段校验，已发布版本仍由既有数据库触发器保持不可变。诊断工作区列出与病例业务类型匹配的已发布报告版本，预览切换后显示模板标题/版块名称，签发命令显式携带并固化所选版本定义。

V48 新增版本化 `report_template_preset` 和医院模板的 `source_preset_code` 追溯，内置肺、乳腺、结直肠三类结构。预置只包含基本信息、材料、镜下、诊断、辅助检查和签发等通用结构，不写入医学结论、分期规则或治疗建议；复制后仍是当前医院草稿，必须由本地业务审核并显式发布。`V48ReportTemplateDesignerExistingDatabaseUpgradeTest` 覆盖 V47→V48 顺序、三项种子和来源列。

验证证据：`V2ReportWebTest` 通过，覆盖预置查询/复制、非法定义拒绝、设计新版本、发布、目录追溯、诊断预览选择和签发快照；前端全量 16 个测试文件、35 tests 通过，format、lint、typecheck 和 production build 均通过；`report-template-designer.spec.ts` 在 1920/1366 两档共 2 个浏览器用例通过。Docker 恢复后，14 个 Testcontainers 测试类共 19 tests 全部通过，覆盖 PostgreSQL 18.4 新库迁移、V34/V35/V36/V39/V45/V46/V47/V48 顺序升级与并发约束；Flyway 当前仅声明已测试至 PostgreSQL 17，因此保留版本兼容告警但不存在测试失败。覆盖矩阵复算为 `TOTAL=742 / COMPLETE=333 / PARTIAL=124 / MISSING=205 / EXTERNAL_DEPENDENCY=80 / CONFLICT_RESOLVED_BY_V2=0`。

## 2026-08-14：报告分页与 PDF 加密闭环

关闭 `RPT-011`、`RPT-013`。原手工 PDF 适配器固定单页并截断 1500 字符，无法作为正式医疗输出。本次改为 Apache PDFBox 3.0.8：按 Unicode 码点换行、完整正文自动分页，页眉固化报告号、内容 SHA-256 和页码，文档元数据记录完整字符数用于回归核验。签发报告 PDF 默认使用 AES-256 权限保护，禁止修改、批注、表单填充、组装和普通内容提取，保留打印与辅助访问能力。

诊断工作区的每份生效报告新增“加密下载”入口。操作者必须输入 8–64 字符访问密码和下载用途；后端基于不可变签发 PDF 生成一次性 AES-256 口令副本，仅返回下载，不创建新的 Report、不覆盖原 PDF、不保存或记录密码。撤回报告不能再生成新的对外副本；高风险下载记录报告、操作者与用途审计。运行环境缺少中文字体字形时，渲染器以 Unicode 转义显示缺失字符，保证内容不静默丢失；生产镜像中文字体包仍需作为部署基线验证。

验证证据：`V2ReportPdfRendererTest` 验证长中文正文跨页、加密状态、完整字符计数和内容摘要；`V2ReportWebTest` 验证默认权限加密、短密码拒绝、错误密码拒绝、正确密码打开、撤回报告拒绝以及审计事实。前端 3 个相关测试文件共 11 tests 通过，`report-pdf-security.spec.ts`、报告输出和 Viewer 回归在 1920/1366 两档共 6 个浏览器用例通过；format、lint、typecheck 和 production build 均通过。覆盖矩阵复算为 `TOTAL=742 / COMPLETE=331 / PARTIAL=125 / MISSING=206 / EXTERNAL_DEPENDENCY=80 / CONFLICT_RESOLVED_BY_V2=0`。

## 2026-08-14：报告重签、自助打印与输出历史闭环

关闭 `RPT-017`、`RPT-024`、`RPT-033`、`RPT-036`、`RPT-038`；`RPT-037` 在完成产品内端口、Simulator、状态查询和错误语义后转为 `EXTERNAL_DEPENDENCY`。撤回后的同一 Diagnosis 继续编辑和重新审核，再签发追加 R002，R001 及 PDF 保持不可变，不引入 ReportVersion。

报告打印和发放复用 V34 的 `report_print_record`、`report_distribution`，不建立第二套输出事实。V47 增加请求人、设备/通道回执、错误证据和按医院唯一的输出幂等记录。自助打印只接受生效报告，身份引用必须匹配病例最新快照；服务端调用 `ReportOutputPort` 决定 SUCCESS/FAILED，客户端不再提交结果码。发放同样由端口执行，未配置真实通道明确记录 FAILED，且不改变 Report。业务管理入口提供身份、终端、打印机、份数、打印机状态以及逐报告打印/发放历史。

验证证据：`V2BusinessOperationsSecurityTest` 与 `V2ReportWebTest` 共 5 tests 通过；覆盖身份拒绝、Simulator 成功、外部通道失败、幂等、历史、数据隔离及撤回后重签。`V2ClinicalOperations.test.ts` 通过；`report-output.spec.ts` 在 1920/1366 两种浏览器视口均通过，并发现、修复后台刷新时卸载业务组件而丢失当前页和输出历史的问题。PostgreSQL V46→V47 升级测试已编译，Docker 不可用时保持 `POSTGRES_REVALIDATION_REQUIRED=YES`。覆盖矩阵复算为 `TOTAL=742 / COMPLETE=329 / PARTIAL=126 / MISSING=207 / EXTERNAL_DEPENDENCY=80 / CONFLICT_RESOLVED_BY_V2=0`。

待业务确认：单次自助打印暂设 1–10 份技术安全边界，目的是防止终端误操作造成无界打印；超过 10 份需拆分为新的、有独立幂等键的打印命令。该边界未来应由 P09 参数确认并配置化，不影响每次打印事实、身份核验和历史追踪语义。

## 2026-08-14：Viewer 标注、测量与截图证据闭环

关闭 `WSI-015`、`WSI-016`、`WSI-017`。原实现虽已有 API/表/按钮，但标注和测量写死中心坐标，截图仅保存 `browser://` 临时引用，因此没有提前提升状态。本次由 Viewer 真实鼠标点选生成归一化标注坐标和两点测量；普通图像按实际图像边界定位，分层 WSI 保存视口归一化坐标及当时视口状态，未取得扫描仪物理标定时只记录坐标系内比例，不伪造毫米/微米。Regular Image 与 OpenSeadragon Adapter 导出当前视野 PNG，后端验证 PNG 签名和大小，保存二进制内容、SHA-256、视口和创建人，并通过受控 API 读取。

三类写操作均增加按医院、操作和幂等键唯一的命令记录；同一 DigitalSlide 行锁串行化重复点击，摘要冲突明确拒绝。阅片历史在诊断工作区可见，截图不再是不可访问的伪引用。`V2GateCWebTest` 验证内容往返、列表、幂等和跨医院拒绝；`V2ImageViewer.test.ts` 验证真实坐标交互；`viewer-review.spec.ts` 在 1920 与 1366 两种浏览器视口中验证真实标注、两点测量和 PNG 截图。浏览器测试同时发现并修复新增工具区把阅片视口压缩成细条、无历史记录时误显“暂无数字切片”的布局回归。覆盖矩阵复算为 `TOTAL=742 / COMPLETE=324 / PARTIAL=129 / MISSING=210 / EXTERNAL_DEPENDENCY=79 / CONFLICT_RESOLVED_BY_V2=0`。

## 2026-08-14：Diagnosis 自动分诊、亚专科与日容量闭环

关闭 `DX-009`、`DX-012`、`DX-013`。自动分诊从 Case 的业务类型、当前医院校区、申请科室和首个有效标本取材部位形成路由事实，匹配启用规则后按维度精确度、规则优先级、当前未完成责任数、当日初诊接诊数选择医生。命中规则创建真实 INITIAL `ResponsibilityUnit`（`AssignmentSource.AUTO`），并把亚专科、匹配维度、分派前计数和容量上限固化为不可变事实；幂等重放不重复创建责任或占用容量。

规则维护由诊断模块 API 承担，配置中心提供新增、启停、优先级、亚专科与每日上限入口。每日上限 0 表示不限量；容量仅约束自动分诊，不替代有权限的手工指派。规则查询和写入均强制当前医院范围，写操作使用模板管理权限、乐观锁、幂等摘要和审计。

验证证据：`V2DiagnosisWebTest` 新增规则管理、两名医生各上限 1、幂等重放、容量耗尽和不可变分派事实测试；`V2DiagnosisWorkspace.test.ts` 与 `V2ConfigurationHub.test.ts` 覆盖工作区动作和配置入口。覆盖矩阵复算为 `TOTAL=742 / COMPLETE=321 / PARTIAL=129 / MISSING=213 / EXTERNAL_DEPENDENCY=79 / CONFLICT_RESOLVED_BY_V2=0`。

## 2026-08-14：原子总数与状态表头校准审计

`CONFLICT_RESOLUTION_AUDIT` 结论：不存在可映射到 ATOMIC_ID 的“缺失 46 条”，因此不得伪造 46 条 `CONFLICT_RESOLVED_BY_V2`。校准前提交 `9f2b61feaf4ccc4f69f7801e5d6fd4debb90f28f` 与校准后提交 `b4cc8fa376795916a6ba895fe49f9d916f90e550` 均包含 742 个 `### <ATOMIC_ID>`、742 个唯一 `- ID:`，ID 集合差异为 0、重复为 0。

旧表头 `TOTAL=788 / COMPLETE=355 / PARTIAL=136 / MISSING=218 / EXTERNAL_DEPENDENCY=79` 与同一文件逐条 `Status` 不一致。`9f2b61f` 逐条可复算结果实际为 `TOTAL=742 / COMPLETE=311 / PARTIAL=135 / MISSING=217 / EXTERNAL_DEPENDENCY=79`。`b4cc8fa` 真实 ATOMIC_ID 状态变化仅有：

| ATOMIC_ID | Old status | New status | Reason | Evidence |
|---|---|---|---|---|
| IHC-001 | PARTIAL | COMPLETE | IHC 项目配置、目标校验、产出和设备尝试闭环 | `V2TechnicalOrderWebTest`、`V44__technical_order_capabilities_and_support_facts.sql` |
| IHC-010 | PARTIAL | COMPLETE | 技术产出质量评价成为独立追加事实 | `V2TechnicalOrderWebTest.technicalSupportFactsKeepDeviceQualityFeeConsumptionAndLabelHistorySeparate` |
| IHC-017 | PARTIAL | COMPLETE | 费用侧通道状态可记录且不阻断技术产出 | 同上 |

因此 `COMPLETE 355 → 314` 不是 41 个 ATOMIC_ID 降级，而是把错误表头校准为“上一提交实际 311 + 本提交新增 3 = 314”；`TOTAL 788 → 742` 同理是表头纠错，没有需求记录从文件消失。复算口径固定为逐个原子小节的唯一 `ID` 与单一 `Status`，实施说明小节不计数。

## 2026-08-14：Diagnosis 病例支持与撤回待处理闭环

关闭 `DX-007`、`DX-027`、`DX-028`、`DX-029`：报告撤回重开审核责任并投影工作台待处理队列；诊断工作区新增病例收藏、科内会诊和随访计划/结果入口。会诊和随访写命令增加幂等摘要冲突校验，所有按病例访问强制组织数据范围，关键动作写审计。

验证证据：`V2DiagnosisWebTest` 6 tests、`V2GateCWebTest` 1 test、`V2ReportWebTest` 1 test 均通过；`V2DiagnosisWorkspace.test.ts` 5 tests、前端 typecheck、lint 和 production build 均通过。

## 2026-08-14：I04 技术支持事实闭环

本次只关闭已有证据足够的原子项，不将真实医院设备、计费或库存联调写成产品内完成。

已实现：

- `technical_project` 增加 capability、output、result、device 和 consumable 配置；既有 IHC、补充取材、分子项目完成回填，并为重切、深切、特殊染色、白片和其他技术项目提供可配置种子。
- 技术医嘱执行调用可替换的 `IhcDevicePort`，产品内使用合成适配器；每次设备尝试单独写入事实并审计，失败不覆盖技术医嘱或物料记录。
- 新增质量评价、费用状态、耗材消耗和标签打印事实。费用失败保持 side-channel，不阻断核心病理产出；标签重打按产物递增打印版本。
- 技术工作台新增产物级“质控通过”、新标签打印和费用登记入口；技术结果仍通过既有玻片/蜡块/结构化结果链返回诊断工作区。

验证证据：

- 后端：`V2TechnicalOrderWebTest`，7 tests passed；覆盖多项目/多目标、跨病例拒绝、正式技术玻片、补充取材、结构化结果、取消和四类支持事实。
- 前端：`npm run typecheck` passed；`V2TechnicalWorkbench.test.ts` 与 `V2DiagnosisWorkspace.test.ts` 共 6 tests passed。
- 覆盖矩阵：IHC-001、IHC-010、IHC-017 更新为 COMPLETE；IHC-002、IHC-016、TO-002/003/005/006/007/008/016 等仍按证据不足保持 PARTIAL；真实依赖保持 EXTERNAL_DEPENDENCY 或待后续处理。

已知限制：

- 当前设备、打印机和计费调用仍使用产品内 Simulator/Mock 适配器；真实厂商协议、真实硬件和医院计费回执未验证。
- 工作台目前不直接选择耗材批次；批次消耗 API 已有后端权限和数据范围校验，库存选择入口需在后续库存工作区补齐。
- 统计、批量设备下发、扫码和完整质控表不属于本次已关闭范围。
