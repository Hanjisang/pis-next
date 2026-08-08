# PIS-Next V2 P08 测试设计基线

状态：已完成（设计封版）
版本：V2-0.2
日期：2026-08-08
目标：证明领域不变量、来源追溯、报告不可变、权限审计和切换质量，而不是只证明页面能显示

## 1. 测试策略和证据等级

测试从 P02 的 INV-V2-001 至 INV-V2-054 建立可追溯矩阵。每个不变量至少有一个自动化测试；高风险不变量同时覆盖领域单元、数据库集成、API/权限和端到端场景。测试报告保存 commit、数据库版本、环境、数据集哈希、执行命令和失败证据。

| 层级 | 责任 | 重点 |
|---|---|---|
| Domain unit | 领域模块 | 状态转换、来源规则、责任链、编号和报告不可变 |
| Application/API | 应用服务与 Controller | 命令前置条件、权限、幂等、错误合同、审计 |
| Database integration | Flyway + PostgreSQL | 外键、唯一约束、部分索引、事务回滚、乐观锁 |
| Architecture guard | ArchUnit/架构测试 | 包依赖、禁用模型、模块所有权、控制器轻量 |
| Frontend component | Vue/Vitest | 工作台状态、树、表单校验、键盘和无障碍反馈 |
| End-to-end | 浏览器/真实 API | 登记到报告、角色队列、报告撤回、异常和恢复 |
| Migration rehearsal | 合成历史数据 | 映射、warning、对账、断链、快照和回滚 |

只在 CI/开发测试库使用虚构或合成数据。不得把真实患者、真实报告或生产连接信息放入测试夹具。

## 2. P02 不变量到测试追溯矩阵

下表按 P02 编号范围给出最小证据集；研发实现时必须把每个具体 INV 编号绑定到测试类/测试方法，不得只绑定到模块名称。

| P02 不变量 | 测试证据 | 失败时的门禁 |
|---|---|---|
| INV-V2-001–004 病例身份、业务类型、病理号 | CaseDomainTest、PathologyNumberConcurrencyTest、RegistrationApiTest、数据库唯一性测试 | P0；不得登记或切换 |
| INV-V2-005–008 申请映射、外部标识、取消 | ApplicationMappingTest、InboundIdempotencyTest、CancelCaseCommandTest | P1；未知项目不得静默进入病例 |
| INV-V2-009–014 Specimen 关系和来源 | SpecimenRelationshipTest、SpecimenDatabaseTest、MaterialTreeE2ETest | P0；来源断链阻塞 |
| INV-V2-015–021 Grossing 初始/补充/冰冻 | GrossingLifecycleTest、GrossingReopenPermissionTest、FrozenSourceTest | P0；不得覆盖原取材 |
| INV-V2-022–028 Block 来源、外部材料、编号 | BlockProvenanceTest、BlockUniquenessTest、UnknownMaterialMigrationTest | P0；未知实际材料隔离 |
| INV-V2-029–034 Slide 来源、完成、重切重染 | SlideProvenanceTest、SlideBatchCompletionConcurrencyTest、ReworkCreatesNewRecordTest | P0；不得回滚初始完成事实 |
| INV-V2-035–038 DigitalSlide 绑定和回调 | DigitalSlideBindingTest、DigitalCallbackIdempotencyTest、PermissionTest | P1；外部回调失败可重试 |
| INV-V2-039–043 TechnicalOrder 多 Item/Target | TechnicalOrderAggregateTest、TargetForeignKeyTest、OrderCommandApiTest | P0；跨病例目标拒绝 |
| INV-V2-044–046 TechnicalRecord 与材料 | TechnicalRecordFactTest、CrossCaseBatchTest、ResultSchemaTest | P1；计划不能当事实 |
| INV-V2-047–050 Diagnosis、模板和动态内容 | DiagnosisVersionTest、TemplateSchemaTest、DiagnosisConflictTest | P0；关键医疗字段不得静默覆盖 |
| INV-V2-051–054 Responsibility、Report、审计 | ResponsibilityChainTest、ReportImmutabilityTest、WithdrawalAuditTest | P0；签发和撤回证据不完整不得切换 |

矩阵不替代具体测试；测试名称可以因实现调整，但 P02 编号和证据链必须保留。

## 3. 架构偏离扫描

加入 ArchUnit 或等效架构测试，扫描 V2 包和数据库设计资产：

1. 禁止 PlannedBlock、ActualBlock、PlannedSlide、ActualSlide 作为强制核心实体；实际 Block/Slide 必须由来源和事实记录表达。
2. 禁止 EmbeddingTask 作为 V2 核心领域必备实体；包埋过程只能通过技术事实和 Block 结果表达。
3. 禁止 ProcessingTask 作为所有技术业务的必备父模型；TechnicalOrder、TechnicalRecord 和项目能力各自独立。
4. 禁止 ReportVersion；一个签发事实对应一个不可变 Report，历史关系追加保存。
5. 禁止 BusinessRecord、GenericTechnicalResult 或通用多态外键承载核心关系。
6. 禁止巨型 CaseStatus；Case 展示状态必须来自下级领域投影。
7. 禁止 Controller 直接写数据库或直接修改关键状态字段。
8. 禁止跨模块直接修改其他模块的核心表；只能调用公开应用接口或发布领域事件。
9. 禁止核心关系使用无约束 JSONB 或 EAV；JSONB 仅可用于模板动态内容快照。
10. 禁止生产代码物理删除 Case、Specimen、Block、Slide、Diagnosis、Report 和 Audit 事实。

架构测试失败即停止 V2 设计实现合入，不通过降低断言、删除失败测试或排除整个模块来“修复”。

## 4. 必测业务场景

| 场景 | 正常路径 | 异常/安全路径 | 关键证据 |
|---|---|---|---|
| Registration | 申请映射 → 分配病理号 → Case/Specimen | 重复消息、未知项目、病理号冲突、取消 | 幂等、编号唯一、Audit |
| Grossing | 初始取材 → 多标本 → 多 Block/Slide | 补充取材、重开权限、来源缺失、并发完成 | 原记录不覆盖、来源链 |
| Material | Specimen → Block → Slide → DigitalSlide | 外部 Block/Slide、直接切片、重复编号、回调乱序 | Material Tree、外部来源 |
| Diagnosis | 模板加载 → 草稿 → 审核 → 责任链完成 | 版本冲突、越权编辑、关键字段缺失、断链 | baseVersion、责任、审计 |
| TechnicalOrder | 多 Item、多 Target、认领、执行 | 跨病例目标、重复命令、取消后输出、结果失败 | Item/Target、实际事实 |
| Report | 预览 → 签发 → 有效报告查询 | 重复签发、签发冲突、撤回、补充/更正、快照缺失 | 不可变、版本链、文件校验 |
| Frozen | 多轮冻结、材料关联、完成 | 未完成切片、来源转常规、重复结束、回调失败 | FrozenRound、来源保留 |
| Cytology | 独立业务类型、细胞材料和诊断路径 | 不能强行套用蜡块流程、模板能力缺失 | BusinessType capability |
| Consultation | 会诊申请、责任链、意见和报告关系 | 外部意见未确认、撤回和权限边界 | Consultation 事实、审计 |
| Molecular | 技术项目、样本/结果引用、报告关系 | 结果缺失、外部失败、重复回调 | 项目能力、集成投递 |

每个场景至少覆盖合成数据下的成功、验证失败、权限拒绝、并发冲突、幂等重放和审计查询。

## 5. 迁移测试

迁移测试使用固定合成历史数据集，至少包含：

1. 正常 Case、重复 Case、病理号冲突和未知业务项目；
2. 有来源 Block/Slide、unknown actual Block、缺 Slide 来源和外部材料；
3. 规划输出与实际输出不一致；
4. 未关联 Diagnosis、多个诊断意见、报告快照不完整、撤回报告；
5. 断链、孤儿、重复编码、跨病例目标和写入失败；
6. 可重放批次、checkpoint 中断、部分成功、回滚和人工豁免。

自动校验：

- 源/目标 Case、pathologyNo、Specimen、Block、Slide、Diagnosis、Report 计数；
- Case→Specimen→Block/Slide、Slide→DigitalSlide、Case→Diagnosis→Report 关系；
- 有效病理号和材料编号冲突；
- MigrationWarning 的 code、级别、证据和状态；
- report template/data/rendered/printable snapshot 完整性；
- orphan、duplicate、warning、failed 五类计数；
- 重跑同一 runId 不产生重复事实；
- 未解决 P0/P1 warning 阻止有效导入。

## 6. 当前基线回归和环境状态

2026-08-08 在 commit 5467766 上已执行现有实现回归，结果用于证明当前工程基线，不代表 V2 已实现：

| 验证 | 结果 |
|---|---|
| 后端非 Docker 测试：./mvnw.cmd -B -Dtest='!ProcessingPostgresIntegrationTest,!TechnicalOrderPostgresIntegrationTest' test | 28/28 通过 |
| 后端完整 verify：./mvnw.cmd -B verify | 30/30 通过，其中 2 个 Testcontainers PostgreSQL 集成测试已实际运行 |
| 前端格式检查、lint、typecheck | 通过 |
| 前端单元测试：npm run test:unit -- --run | 5 个测试文件、5 个测试通过 |
| 前端构建：npm run build | 通过 |
| Docker | Client/Server 29.6.2 可用；docker compose config --quiet 通过（使用合成校验密码，不写入文件） |

此前 Docker Server 曾不可连接，已经在本轮完整 verify 前恢复。完整集成测试现已通过；仍需在后续 V2 实现阶段执行 compose build/启动、V2 数据库迁移和端到端链路。

## 7. 前端和可访问性测试

Diagnosis Workspace、Material Tree、Global Search Drawer、CommandBar 和 Config Editor 必须测试：

- Tab/Shift+Tab 顺序、Enter/Space 激活、Esc 关闭抽屉/对话框；
- 动态 aria-expanded、aria-selected、aria-busy、aria-invalid 和错误摘要焦点；
- 加载、空数据、过期投影、权限拒绝、版本冲突、撤回和外部失败状态；
- 44×44px 交互目标、焦点环、对比度和 reduced-motion；
- 病理号、标本号、蜡块号、切片号搜索后保持 Case Context；
- 重复点击签发/完成只产生一次命令；
- 角色投影不可看到或操作无权限病例。

## 8. P08 封版结论

P08 已建立 P02 不变量追溯、架构偏离扫描、九类以上核心场景、迁移数据质量测试、权限/并发/幂等/审计测试和现有回归基线。V2 测试代码尚未创建；进入 V2-I01 后必须先将矩阵转成测试骨架，再允许实现领域代码。
