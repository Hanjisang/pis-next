# PIS-Next V2 P06 API 设计基线

状态：已完成（设计封版）
版本：V2-0.2
日期：2026-08-08
原则：API 表达领域命令和查询，不把数据库表暴露为 CRUD 资源

## 1. API 总体约束

1. Query 与 Command 分离：查询只读，命令改变一个明确领域事实并返回结果事件/版本。
2. 控制器只做鉴权、输入校验、事务入口和响应映射；状态转换由应用服务和领域规则完成。
3. 禁止提供 PUT /case/{id}/status 或任何让前端直接写聚合状态的通用接口。
4. 业务编号、内部 ID、外部系统标识分别命名；病理号修改是独立受控命令。
5. 所有关键命令返回 operationId、entityId、newVersion、auditId 或可追踪的 correlationId。
6. 成功提交领域事实后，投影和外部回执通过 Outbox/IntegrationDelivery 异步处理；失败必须可重试、死信和人工重放。
7. API 版本通过 URL 或媒体类型管理；V2 设计期间不改变当前 P15–P19 已有契约，适配器负责过渡。

## 2. 资源和查询 API

| 方法与路径 | 返回内容 | 规则 |
|---|---|---|
| GET /api/v2/cases/{caseId} | Case 基本信息、业务类型、生命周期投影、申请摘要、责任摘要、风险提示 | 只读；按权限脱敏；不返回可直接写状态的字段 |
| GET /api/v2/cases/by-pathology-no/{pathologyNo} | 病例身份和上下文入口 | 病理号必须按租户/院区范围解析；冲突返回数据质量错误，不任意取一条 |
| GET /api/v2/cases/{caseId}/context | Case、患者/就诊快照、Application、Specimen、Grossing、Block、Slide、DigitalSlide、TechnicalOrder、Diagnosis、Responsibility、Report、History、QC Warning | 诊断工作台和病例上下文的聚合查询；来源字段和历史摘要齐全 |
| GET /api/v2/cases/{caseId}/materials | MaterialTreeProjection | 返回 Specimen → Block → Slide → DigitalSlide、Specimen → Slide、外部来源路径 |
| GET /api/v2/cases/{caseId}/work-queue-context | 当前角色可处理的队列项、责任和锁定信息 | 不能用全院病例表替代角色投影 |
| GET /api/v2/specimens/{specimenId} | Specimen、取材、来源和下级材料摘要 | 只读；失效记录保留历史标识 |
| GET /api/v2/technical-orders/{orderId} | Order、Item、Target、状态历史、项目结果引用 | 结果按项目能力呈现，不返回 GenericTechnicalResult |
| GET /api/v2/diagnosis-workspaces/{caseId} | 诊断工作台聚合 | 必须包含 Case、Patient、Application、Specimen、Grossing、Block、Slide、DigitalSlide、TechnicalOrder、Diagnosis、Responsibility、Reports、History summary、QC warning |
| GET /api/v2/diagnoses/{diagnosisId} | 诊断内容、模板版本、责任链、编辑历史 | structuredData 受模板 schema 校验；关键字段结构化返回 |
| GET /api/v2/reports/{reportId} | Report 状态、签发/撤回元数据、模板/渲染/打印快照引用 | 已签发快照只读；权限不足不得返回文件引用 |
| GET /api/v2/search | 按病理号、标本号、蜡块号、切片号、外部标识和受限患者索引查询 | 进入 Case Context；按权限和脱敏规则限制结果 |
| GET /api/v2/audit-events | 按实体、Case、病理号、操作时间和 correlationId 查询 | 只读、分页、权限严格；不返回未授权敏感 before/after |

查询响应必须包含 asOfVersion 或 projectedAt，并明确是否来自实时领域读取还是可重建投影。不存在的资源与权限不足使用可区分但不泄露敏感信息的错误码。

## 3. 核心命令 API

| 方法与路径 | 领域行为 | 幂等与并发 |
|---|---|---|
| POST /api/v2/cases/registrations | 解析 ApplicationItemMapping，分配病理号，创建 Case 和申请上下文 | Idempotency-Key 必填；同一 sourceSystem + externalApplicationId 不重复登记；编号服务并发锁 |
| POST /api/v2/cases/{caseId}/cancel | 取消病例并记录原因、责任和审计 | 需要 expectedVersion；已有不可逆下游事实时按规则拒绝或转特殊裁决 |
| POST /api/v2/cases/{caseId}/pathology-number/change | 受控修改病理号，保留旧值、原因和审批 | 幂等 commandId；编号冲突、并发修改和已签发报告均需明确裁决 |
| POST /api/v2/specimens/{specimenId}/receive | 接收标本并建立接收事实 | 同一外部接收消息幂等；版本冲突拒绝覆盖 |
| POST /api/v2/cases/{caseId}/grossings | 创建初始/补充/冰冻上下文取材 | commandId 幂等；并发取材编号和来源关系由服务校验 |
| POST /api/v2/grossings/{grossingId}/complete | 完成取材并提交实际 Block/Slide 来源事实 | expectedVersion；不能用计划输出代替实际材料 |
| POST /api/v2/grossings/{grossingId}/reopen | 在授权和原因满足时重新打开取材 | 需要高风险权限、理由和审计；不删除原完成事实 |
| POST /api/v2/slides/{slideId}/complete | 完成单张切片或一批明确切片 | batch command 有幂等键和版本集合；已完成切片不回滚 |
| POST /api/v2/technical-orders | 创建含多个 Item 和 Target 的技术医嘱 | Idempotency-Key；Item/Target 原子校验 |
| POST /api/v2/technical-orders/{orderId}/cancel | 取消订单或项目 | expectedVersion；已产生的实际输出保留，不回滚 |
| POST /api/v2/technical-orders/{orderId}/claim | 认领技术医嘱 | 幂等认领；责任竞争使用乐观锁 |
| POST /api/v2/diagnoses/{diagnosisId}/assign | 分派/变更诊断责任链 | role、doctor、sequence 唯一校验；审计责任变更 |
| POST /api/v2/diagnoses/{diagnosisId}/review | 提交审核意见并推进责任链 | 需要当前责任人和 expectedVersion；禁止越权推进 |
| POST /api/v2/diagnoses/{diagnosisId}/sign | 生成签发事实和不可变 Report | Idempotency-Key 必填；同一诊断不可重复有效签发；签发前锁定诊断版本 |
| POST /api/v2/reports/{reportId}/withdraw | 撤回报告并记录原因、责任和关系 | 幂等；只允许有效报告；不能物理删除原报告 |
| POST /api/v2/frozen-rounds/{roundId}/finish | 完成冰冻轮次并固定时间/责任事实 | Idempotency-Key；需核验标本、切片和诊断条件 |
| POST /api/v2/archive/movements | 归档/调位，追加 ArchiveHistory | expectedVersion；位置竞争和审计 |
| POST /api/v2/loans | 创建借用及 LoanItem | 幂等；借出、部分归还和逾期不覆盖历史 |
| POST /api/v2/loans/{loanId}/return | 归还全部或部分材料 | expectedVersion；逐项记录归还 |
| POST /api/v2/destructions | 提交审批后的材料销毁记录 | 高风险权限、审批证据和审计必填 |

“完成”“审核”“签发”“撤回”“重开”均是具有前置条件和历史的命令，不提供通用状态更新接口。

## 4. 诊断工作台契约

GET /api/v2/diagnosis-workspaces/{caseId} 的响应逻辑结构固定为：

1. header：Case、Patient/Encounter snapshot、PathologyNo、BusinessType、当前责任；
2. caseContext：Application、Specimen、Grossing、Block、Slide、DigitalSlide、History；
3. technicalContext：TechnicalOrder、Item、Target、TechnicalRecord 摘要和 QC warning；
4. diagnosis：模板版本、结构化诊断、镜下所见、最终诊断、comment、草稿版本；
5. responsibility：INITIAL、REVIEW、AUDIT 的医生、顺序、可编辑权和完成时间；
6. reports：草稿、有效、撤回、补充、更正和重新签发关系摘要；
7. capabilities：当前角色可执行的命令和缺失前置条件。

诊断编辑命令使用专门路径，例如 PATCH /api/v2/diagnoses/{diagnosisId}/content 和 POST /api/v2/diagnoses/{diagnosisId}/submit-review。PATCH 只修改指定诊断草稿版本，不修改已签发 Report；请求必须携带 baseVersion，冲突返回 409 DIAGNOSIS_VERSION_CONFLICT。

## 5. 幂等、并发和错误合同

以下操作必须有稳定幂等键：登记、报告签发、冰冻结束、外部回调、人工重放、批量完成。系统复用当前已有的幂等存储方向（P16 幂等键、P19 command idempotency），V2 统一为 Integration/Command Idempotency 约定。重复请求返回第一次结果或明确的同义状态，不重复产生病例、报告或审计事实。

以下操作必须使用乐观锁或等效机制：诊断编辑、取材重开、批量切片完成、病理号分配、报告签发、责任分派。冲突返回 409，包含 entityId、expectedVersion、actualVersion、reloadHint；不得采取最后写入覆盖医疗数据。

错误响应至少包含 code、message、correlationId、operationId、fieldErrors、retryable 和 currentVersion（适用时）。核心错误码包括 INVALID_BUSINESS_TYPE、PATHOLOGY_NUMBER_CONFLICT、SOURCE_NOT_FOUND、PRECONDITION_FAILED、VERSION_CONFLICT、ALREADY_COMPLETED、REPORT_IMMUTABLE、FORBIDDEN_HIGH_RISK_OPERATION、IDEMPOTENCY_REPLAY、INTEGRATION_PENDING。

## 6. 外部系统 API

外部接口由医院适配器隔离，不能直接更新 Core Domain Data：

| 方向 | 接口能力 | V2 处理 |
|---|---|---|
| 入站 | 申请登记、患者/就诊映射、取消 | 原始报文入库，解析为应用命令，幂等和未知项目异常 |
| 双向 | 费用/项目同步 | 记录业务成功与外部失败的独立状态，支持重试和对账 |
| 出站 | 状态反馈、报告交付、报告撤回 | Outbox → IntegrationDelivery，保存外部回执和文件校验 |
| 入站 | 数字切片扫描/绑定回调 | 映射 ExternalIdentifier，不能直接写 Slide 或 DigitalSlide |
| 运维 | 失败补偿、死信重放 | 人工重放 API 产生新 commandId 和审计，不重复执行已成功命令 |

每个适配器都必须提供 sourceSystem、版本、字段映射、脱敏策略和契约测试；医院差异通过配置和适配器实现，不复制核心领域 API。

## 7. 权限、审计和版本化

API 采用角色、资源范围和特殊授权三层校验。高风险命令（病理号修改、取材重开、报告撤回、销毁、人工重放）必须记录 authorizationDecision、原因和责任链。控制器不得依赖前端传入的可编辑状态作为授权依据。

所有命令成功或失败都写入 correlationId；成功命令写 AuditEvent，外部交付和失败补偿写 IntegrationAttempt。响应中不包含真实患者敏感字段的非必要复制；日志和错误信息脱敏。

## 8. P06 封版结论

P06 已完成 Query/Command 边界、核心资源路径、诊断工作台聚合、幂等、并发、错误合同、外部适配和权限审计设计。尚未创建 Controller、OpenAPI 文件或接口实现；研发下一步应先把本文件转为契约测试和 ADR，再按 P09 阶段启用。
