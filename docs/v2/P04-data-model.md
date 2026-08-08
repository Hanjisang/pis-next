# PIS-Next V2 P04 数据模型设计基线

状态：已完成（设计封版）
版本：V2-0.2
日期：2026-08-08
前置：P00 当前系统审计、P01 领域模型、P02 领域不变量、P03 模块边界

## 1. 设计目标与禁止事项

本文件把 P01 的领域对象和 P02 的不变量落到逻辑数据模型，粒度达到研发可以开始 Flyway 表设计、约束设计和索引设计，但本轮不创建迁移脚本、不修改现有数据库、不实现 V2 业务代码。

数据模型必须满足：

1. 内部 ID 与病理号、标本号、蜡块号、切片号等业务编号分离；内部 ID 不可变。
2. Case、Specimen、Grossing、Block、Slide、DigitalSlide、TechnicalOrder、Diagnosis、Report 各自拥有生命周期和责任边界。
3. 业务撤销、作废、更正和失效使用状态或历史记录，不物理删除医疗业务记录。
4. 核心关系使用明确外键；不得用无法保证完整性的通用多态外键承载核心关系。
5. JSONB 只承载模板驱动的动态内容快照；高频检索字段、关系字段和关键医疗字段必须结构化。
6. 关键写操作具有事务边界、乐观锁或等效并发控制，并产生审计。
7. 本模型不采用巨型 CaseStatus、PlannedBlock、ActualBlock、PlannedSlide、ActualSlide、ReportVersion、BusinessRecord 或把所有业务塞入 Task 的替代模型。

逻辑表名使用英文；本文中的“表”是逻辑模型，不代表本轮已经创建数据库表。

## 2. 五层数据分区

| 数据层 | 所有权 | 典型内容 | 访问规则 |
|---|---|---|---|
| Core Domain Data | 对应领域模块 | Case、Specimen、Grossing、Block、Slide、DigitalSlide、TechnicalOrder、Diagnosis、Report、FrozenRound | 只由所属模块应用服务写入，其他模块通过公开接口或领域事件访问 |
| Configuration Data | Config 模块 | BusinessType、编号规则、切片规则、打印规则、技术项目、模板、分派规则、质控规则、技术节点 | 按租户/院区隔离，版本化和审计，生效配置不可静默覆盖 |
| Projection / Query Data | Query/Projection 模块 | 病例上下文、物料树、诊断工作台、角色队列、报表统计 | 可重建、只读，不作为领域事实来源 |
| Integration Data | Integration 模块 | 原始报文、外部标识映射、投递、尝试、重试、死信、重放 | 外部系统不能直接写 Core Domain Data |
| Audit Data | Audit/Security 模块 | 操作、责任、前后值、原因、授权、报告撤回和关键医疗字段变更 | 追加式写入，敏感字段脱敏，按实体和病理号可追溯 |

跨层写入必须通过明确的应用命令。Projection、Integration、Audit 的失败不得回滚已经成功提交的医疗事实，但必须留下可重试或人工处置记录。

## 3. Core Domain Data

### 3.1 Case、BusinessType、登记映射和病理号

Case 是病例身份聚合，不承载标本、蜡块、切片或报告的全部状态。

| 逻辑实体 | 必备字段 | 约束与业务规则 |
|---|---|---|
| Case | id、businessTypeId、pathologyNo、lifecycle、frozenSourceCaseId、sourceType、sourceSystem、applicationNo、patientSnapshot、encounterSnapshot、registeredAt、registeredBy、cancelledAt、cancelledBy、cancelReason、createdAt、updatedAt、version | id 不可变；frozenSourceCaseId 可为空但必须指向允许的常规来源病例；患者和就诊信息是登记时快照，不覆盖外部主数据；取消必须有原因和责任人；pathologyNo 由规则服务分配，可在受控命令下修改 |
| BusinessType | id、code、name、enabled、capabilities、scope、priority、version、effectiveFrom、effectiveTo | capabilities 至少包含 requiresGrossing、supportsBlock、supportsDirectSlide、supportsFrozenRound、supportsMolecular、supportsConsultation；它是业务能力配置，不是 BPM 流程图 |
| ApplicationItemMapping | id、tenantId/hospitalId、sourceSystem、campus、externalItemCode、businessTypeId、enabled、priority、version | sourceSystem、campus、externalItemCode 在有效范围内唯一；未知项目进入异常队列，不默认为常规活检 |
| PathologyNumberRule | id、businessTypeId、prefix、datePart、sequencePart、campusPart、resetPolicy、effectiveFrom、effectiveTo、version | 分配、并发锁、修改、取消释放、冲突和审计由编号域服务负责；禁止读取当前最大值再加一；已使用号码原则上不回收 |

patientSnapshot 和 encounterSnapshot 只能存批准的最小快照字段，敏感字段脱敏展示；实际患者主索引和外部就诊映射由对应模块负责。Case 的病例身份不因病理号修改而改变。

### 3.2 Specimen 与取材

| 逻辑实体 | 必备字段 | 约束与业务规则 |
|---|---|---|
| Specimen | id、caseId、specimenCode、name、description、site、sourceType、sourceSystem、externalSpecimenId、receivedAt、receivedBy、deletedAt、deletedBy、version | Case 1 对 0..N Specimen；specimenCode 在 Case 内唯一；外部来源和外部标识必须成对记录；删除字段只表示业务失效，原记录保留 |
| Grossing | id、caseId、grossingType、contextType、technicalOrderId、frozenRoundId、startedAt、completedAt、completedBy、cancelledAt、cancelReason、version | Case 1 对 0..N；支持初始、补充取材、冰冻上下文和技术医嘱来源；不得按场景复制多个取材实体 |
| GrossingSpecimen | grossingId、specimenId、sequence、materialDescription、version | Grossing 与 Specimen 为明确关联；同一取材内 sequence 唯一；跨病例关联禁止 |

补取材必须新建 Grossing 记录；冰冻剩余组织转入常规流程时保留 frozenSourceCaseId、frozenRoundId 或来源标识关系，不能复制成无来源的新标本。

### 3.3 Block、Slide 和 DigitalSlide

| 逻辑实体 | 必备字段 | 约束与业务规则 |
|---|---|---|
| Block | id、caseId、specimenId、grossingId、blockCode、blockType、sourceTechnicalOrderItemId、externalSourceFlag、externalSourceReference、createdAt、createdBy、deletedAt、deletedBy、version | caseId 必填；合法外部蜡块允许 specimenId、grossingId 为空但必须 externalSourceFlag 和来源证明；blockCode 按医院规则在有效范围内唯一；不保存 embedding、processing、sectioning 状态 |
| Slide | id、caseId、blockId、specimenId、slideCode、slideType、sourceContextType、sourceContextId、completed、completedAt、completedBy、externalSourceFlag、externalSourceReference、deletedAt、deletedBy、version | caseId 必填；blockId 可以为空以支持直接来自 Specimen 的切片；blockId 和 specimenId 至少有一个来源或有明确外部来源；sourceContextType 取 INITIAL、TECHNICAL_ORDER、FROZEN_ROUND、CYTOLOGY、EXTERNAL；技术医嘱切片是新记录，不回滚初始切片完成事实 |
| DigitalSlide | id、caseId、blockId、slideId、externalPlatform、externalImageId、viewerReference、scanMetadata、bindingMetadata、receivedAt、status、version | caseId 必填；一个 Slide 可有多个 DigitalSlide；外部平台和外部图像 ID 在平台范围内幂等；绑定和解绑有审计，不把外部回调直接写入 Slide |

Material 来源关系必须能沿 Case → Specimen → Block → Slide → DigitalSlide 追溯，也必须支持 Case → Specimen → Slide、外部 Block → 本地 Slide 和外部 Slide 三类合法路径。来源删除采用失效，不级联物理删除下游医疗记录。

### 3.4 TechnicalOrder、TechnicalOrderItem、Target 和 TechnicalRecord

| 逻辑实体 | 必备字段 | 约束与业务规则 |
|---|---|---|
| TechnicalOrder | id、caseId、orderNo、requester、reason、status、createdAt、cancelledAt、cancelReason、version | 一个病例可有多个订单；取消是命令和历史，不覆盖已产生的输出 |
| TechnicalOrderItem | id、technicalOrderId、technicalProjectId、sequence、parameters、status、sourceContextType、version | 一个订单包含多个 Item；每个 Item 至少有一个 Target；参数按 TechnicalProject 能力校验 |
| TechnicalOrderTarget | id、itemId、targetType、caseId、specimenId、blockId、slideId、sequence、version | targetType 只能为 CASE、SPECIMEN、BLOCK、SLIDE；实际外键按类型使用明确关联列并通过约束/应用校验保证只有一个目标；禁止用通用 targetId |
| TechnicalProject | id、code、name、capability、enabled、scope、version | 配置实体，不把项目结果定义成 GenericTechnicalResult |
| TechnicalRecord | id、caseId、technicalOrderItemId、nodeConfigId、recordType、status、performedAt、performedBy、structuredResult、version | 记录实际技术事实；支持跨病例批次，但每个材料仍明确归属病例；不得用 Task 代替事实记录 |
| TechnicalRecordMaterial | technicalRecordId、materialType、blockId、slideId、sequence、inputOutputRole | materialType 取 BLOCK、SLIDE；输入和输出关系明确；Block/Slide 的 sourceTechnicalOrderItemId 记录来源订单 Item |

Structured result 必须按照项目 capability 定义模式校验。跨病例批次由 TechnicalRecordBatch 及其成员关联表达，不改变每个材料的病例归属。

### 3.5 Diagnosis、Responsibility 和 Report

| 逻辑实体 | 必备字段 | 约束与业务规则 |
|---|---|---|
| DiagnosisTemplate | id、code、name、businessTypeId、scope、enabled、version、effectiveFrom、effectiveTo | 模板身份与模板版本分离；启用范围可按租户/院区配置 |
| DiagnosisTemplateVersion | id、templateId、versionNo、schemaDefinition、publishedAt、publishedBy、status | 发布后不可修改；schemaDefinition 只描述动态内容结构，不取代核心关系 |
| Diagnosis | id、caseId、frozenRoundId、templateVersionId、structuredData、microscopicFindings、finalDiagnosis、comment、status、createdAt、updatedAt、version | caseId 必填，frozenRoundId 可为空；结构化诊断和关键字段独立存储；structuredData JSONB 只承载模板动态项；编辑、提交、审核、完成均为命令 |
| ResponsibilityUnit | id、diagnosisId、role、doctorId、sequence、acceptedAt、completedAt、currentEditable、status、version | Diagnosis 关联 1..N；role 取 INITIAL、REVIEW、AUDIT；同一 Diagnosis 的 role、doctorId、sequence 组合按业务规则唯一；当前可编辑责任人由链条和状态派生，不由前端直接写 |
| ReportTemplate | id、code、name、scope、enabled、version、effectiveFrom、effectiveTo | 报告模板独立配置，模板版本发布后不可改 |
| Report | id、caseId、diagnosisId、frozenRoundId、templateId、templateVersionId、templateSnapshot、signerId、signedAt、status、renderedSnapshot、pdfFileReference、printableSnapshot、withdrawnAt、withdrawnBy、withdrawReason、version | 一次签发产生一个不可变 Report；status 至少支持 EFFECTIVE、WITHDRAWN 及设计需要的草稿/待签状态；撤回必须有原因和责任人；不存在 report_version，也不直接覆盖已签发报告 |

Report 的模板、数据和渲染结果均保存快照；PDF 或文件服务只保存引用和校验信息，关键可打印内容同时保存 printableSnapshot。更正、补充、撤回和重新签发通过新的 Diagnosis/Report 关系或专门历史事实表达，不修改原 Report。

### 3.6 FrozenRound、归档和借用

| 逻辑实体 | 必备字段 | 约束与业务规则 |
|---|---|---|
| FrozenRound | id、caseId、roundNo、arrivalAt、grossingStartedAt、slideCompletedAt、diagnosisSignedAt、status、version | Frozen Case 1 对 1..N；roundNo 在 Case 内唯一；标本通过 FrozenRoundSpecimen 形成 N 对 N；诊断和报告可按 frozenRoundId 定位 |
| FrozenRoundSpecimen | frozenRoundId、specimenId、sequence、role | 同一轮内 specimen 唯一；保留剩余组织转常规流程的来源关系 |
| ArchiveLocation | id、code、name、locationType、enabled、scope、version | 位置配置版本化；Block 和 Slide 可有当前归档位置/目的地引用 |
| ArchiveHistory | id、entityType、blockId、slideId、fromLocationId、toLocationId、operation、operatorId、operatedAt、reason | 迁移、归档、取出均追加记录；不得覆盖历史位置 |
| Loan | id、caseId、borrower、purpose、requestedAt、approvedAt、dueAt、returnedAt、status、version | 借用是生命周期记录，不通过改写归档位置表示 |
| LoanItem | loanId、blockId、slideId、quantity、outAt、returnedAt、status | 同一借用可含多个 Block/Slide；归还和部分归还保留事实 |
| DestructionRecord | id、caseId、blockId、slideId、approvedBy、destroyedAt、reason、evidenceReference | 物理销毁前需审批和证据；医疗记录不因销毁材料而物理删除 |

## 4. Configuration Data

以下实体统一具备 scope（tenant/hospital/campus）、enabled、priority、version、effectiveFrom、effectiveTo 和审计字段：

BusinessType、ApplicationItemMapping、PathologyNumberRule、SlideRule、PrintRule、TechnicalProject、DiagnosisTemplate、DiagnosisTemplateVersion、ReportTemplate、AssignmentRule、QCRule、TechnicalNodeConfig。

配置生效按优先级和时间窗口解析；配置修改不得直接改变历史业务记录的解释。Template Editor 与一般字典维护分离，发布模板和规则必须有审批/审计。

## 5. Projection / Query Data

至少规划以下可重建投影：

1. CaseContextProjection：Case、患者/就诊快照、申请、Specimen、Grossing、Block、Slide、DigitalSlide、TechnicalOrder、Diagnosis、Responsibility、Report 的只读汇总。
2. MaterialTreeProjection：Specimen → Block → Slide → DigitalSlide，以及 Specimen → Slide、外部来源路径。
3. DiagnosisWorkspaceProjection：诊断工作区需要的当前责任、草稿、技术结果、历史、QC 警告和报告摘要。
4. RoleQueueProjection：技术人员、取材人员、诊断医生、审核人员各自的待办队列；不提供无权限的全院病例列表。
5. StatisticsProjection：按业务类型、节点、时效和质量指标聚合；不反向写入领域事实。

投影记录必须包含 sourceVersion、projectedAt 和 rebuildStatus；投影延迟或失败不得改变 Core Domain Data 的事实。

## 6. Integration Data

IntegrationDelivery、IntegrationAttempt、ExternalIdentifierMapping、InboundRawMessage、InboxConsumption、DeadLetterRecord 和 ReplayRequest 组成集成事实：

1. 每条消息有 messageId、sourceSystem、businessKey、receivedAt 和幂等结果。
2. 每次投递有 attemptNo、状态、错误摘要、重试时间和脱敏响应。
3. 业务成功但 HIS 回执失败时，Core Domain Data 保留成功事实，IntegrationDelivery 标记待重试/失败。
4. 原始报文只进入受控存储，日志只输出脱敏摘要。
5. 人工重放进入应用命令，不能直接重放数据库写操作。

当前仓库已有 Outbox 及相关集成基础设施；V2 设计应复用其可靠投递能力，但按上述业务投递状态补足重试、死信和对账语义。

## 7. Audit Data

AuditEvent 至少包含 id、entityType、entityId、caseId、pathologyNo、operation、beforeSnapshot、afterSnapshot、operatorId、operatorRole、occurredAt、reason、correlationId、ip/device 摘要和 authorizationDecision。

必须审计：

- 病理号分配、修改、冲突、取消和释放决策；
- Specimen、Block、Slide 来源关系和标签补打/失效；
- Diagnosis 关键医疗字段编辑、责任链变更和审核；
- Report 签发、撤回、更正、补充、重新签发；
- 高风险归档、借用、归还、销毁和特殊授权；
- 外部报文处理、重试、死信和人工重放。

before/after 需要保护敏感字段；审计记录追加式保存并受权限控制。

## 8. 约束、索引和事务设计入口

唯一性和查询索引至少覆盖：

| 约束对象 | 设计要求 |
|---|---|
| Case.id | 主键唯一且不可变 |
| Case.pathologyNo | 有效业务范围内冲突禁止；病理号修改走编号域服务，不能只靠普通唯一索引 |
| Specimen | caseId + specimenCode 唯一，caseId、externalSpecimenId 建索引 |
| Block | caseId + blockCode 按医院规则唯一；caseId、specimenId、sourceTechnicalOrderItemId 建索引 |
| Slide | caseId + slideCode 按 SlideRule 唯一；blockId、specimenId、sourceContextId 建索引 |
| FrozenRound | caseId + roundNo 唯一 |
| ResponsibilityUnit | diagnosisId + role + sequence 及当前可编辑责任约束 |
| TechnicalOrder | caseId + orderNo 唯一；Item sequence、Target sequence 唯一 |
| DigitalSlide | externalPlatform + externalImageId 唯一 |
| Report | 一次签发事实唯一；有效/撤回约束由 Report 服务和状态约束共同保证 |

优先使用部分唯一索引处理“有效记录唯一、失效记录可保留”；如果数据库约束不能表达业务规则，则由领域服务在同一事务和并发锁内检查。登记、取材完成、材料完成、诊断签发、报告签发、撤回、号码分配和外部回执分别定义事务边界。任何写命令必须携带 expectedVersion 或等效并发令牌，冲突返回明确错误，不静默覆盖。

## 9. P04 封版结论

P04 已定义五层数据边界、核心逻辑实体、关键字段、关系、来源链、配置作用域、投影、集成、审计、索引和并发入口。研发下一步可以基于本文件创建 Flyway 详细设计，但必须在代码前完成字段级评审、数据库命名评审和 P08 契约测试设计。

明确不在本轮创建迁移脚本；所有当前 P15–P19 表到 V2 表的处置进入 P05。医院编号格式、模板具体字段、院区编码和归档设备类型中仍属部署差异的内容标记为“待业务确认”，不阻塞核心领域模型封版。
