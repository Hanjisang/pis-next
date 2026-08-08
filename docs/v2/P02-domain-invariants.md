# PIS-Next V2 P02 领域不变量

文档状态：已完成
文档版本：V2-0.1
完成日期：2026-08-08

## 1. 使用方式

以下不变量是 V2 领域基线，后续数据库、API、前端和测试必须服从它们。医院可以配置编号、模板、路由、提醒、阈值和展示，但不能关闭身份稳定、来源闭合、责任可识别、报告不可变和审计保护。

## 2. 身份和登记

| 编号 | 不变量 |
|---|---|
| INV-V2-001 | `caseId` 永久稳定且不可业务修改；业务编号与内部 ID 分离。 |
| INV-V2-002 | 每个 Case 只有一个 BusinessType 和一套独立 pathology number；Case 生命周期只有 ACTIVE/CANCELLED。 |
| INV-V2-003 | ApplicationItemMapping 按来源系统、外部项目和可选院区配置；映射失败必须可人工选择并留痕，禁止医院项目号硬编码。 |
| INV-V2-004 | 一个申请可以形成多个 Case；一个 Case 可以有多个 Specimen；不得用申请或容器替代 Case/Specimen。 |
| INV-V2-005 | Specimen 在 Case 内编号唯一；新增、修改和软删除不覆盖来源、原值和责任历史。 |
| INV-V2-006 | Frozen Case 与 Routine Case 是两个 Case；冰剩转常规必须保留 frozenSourceCaseId 和来源链。 |
| INV-V2-007 | 病理号修改、释放、取消和重分配必须有授权和 Audit；已形成历史不得无痕改写。 |

## 3. 取材和材料

| 编号 | 不变量 |
|---|---|
| INV-V2-008 | Grossing 属于 Case，一次 Grossing 可处理多个 Specimen，同一 Case 可多次 Grossing。 |
| INV-V2-009 | 原取材录错 reopen 原 Grossing；诊断后补取材必须创建新 Grossing 并关联 TechnicalOrder。 |
| INV-V2-010 | V2 只保留 Block；不得创建 PlannedBlock/ActualBlock 或把组织盒设为 Block 的替代父层。 |
| INV-V2-011 | Block 必须保留 sourceGrossing、正常来源 Specimen 或明确的 External 来源；Case 内业务编号唯一。 |
| INV-V2-012 | V2 只保留 Slide；不得创建 PlannedSlide/ActualSlide；打印前 Slide 已存在。 |
| INV-V2-013 | Slide 来源必须明确为 Block、Specimen 或 External；Block 修改不得造成 Slide 来源漂移。 |
| INV-V2-014 | 返工、补块、重切、重染和重扫追加新事实或新业务对象，不得覆盖原记录。 |
| INV-V2-015 | Slide 软删除不物理删除，且不得让历史诊断、打印、归档和数字切片引用失效。 |

## 4. 生产和打印

| 编号 | 不变量 |
|---|---|
| INV-V2-016 | INITIAL_PRODUCTION、TECHNICAL_ORDER、FROZEN_ROUND 只表达轻量来源/上下文，不是 Case 生命周期。 |
| INV-V2-017 | Grossing 完成后根据最终 Block 集合和 SlideRule 生成默认 Slide；Slide 完成由事实和规则判断。 |
| INV-V2-018 | PrintRule、PrintService 和 PrinterAdapter 分层；核心领域不得绑定具体打印 SDK。 |
| INV-V2-019 | 补打是同一 Block/Slide 的新 PrintLog；不得因为打印再次创建业务对象。 |
| INV-V2-020 | 标签打印不等于实物形成；标签失效和补打必须保留版本与责任。 |

## 5. TechnicalOrder 和 TechnicalRecord

| 编号 | 不变量 |
|---|---|
| INV-V2-021 | TechnicalOrder 属于诊断循环，支持多个 Item 和多个 Target，不能作为诊断前固定主流程。 |
| INV-V2-022 | TechnicalOrder 的 Target 必须指向 Case、Specimen、Block 或 Slide 的具体身份。 |
| INV-V2-023 | TechnicalOrder 只有 PENDING、EXECUTING、COMPLETED、CANCELLED 等简单状态；完成尽量由实际输出推导。 |
| INV-V2-024 | 新 Block/Slide/分子结果必须进入正式材料或结果域；禁止把全部输出塞进 Generic TechnicalResult。 |
| INV-V2-025 | TechnicalRecord 记录物理节点和责任，不默认成为 Diagnosis 硬门槛，也不把脱水/包埋/切片/染色/封片建成主 Task。 |
| INV-V2-026 | 订单取消不删除已发生执行事实；只标记订单/项目和不再需要的派生 Slide 为取消或软删除。 |

## 6. 数字切片和诊断

| 编号 | 不变量 |
|---|---|
| INV-V2-027 | DigitalSlide 必须有 caseId，Block/Slide 绑定可选；一张 Slide 可有多个 DigitalSlide。 |
| INV-V2-028 | 数字切片支持自动绑定、手工绑定和重新绑定；扫描未完成只提示，不阻止 Diagnosis。 |
| INV-V2-029 | 普通 Case 原则上一份 main Diagnosis；FrozenRound 可有独立 Diagnosis；模板版本必须固定。 |
| INV-V2-030 | Diagnosis 结构化字段、自由文本、条件、依赖、计算和生成文本由模板配置，不能把医院差异硬编码为类型分支。 |
| INV-V2-031 | 诊断、复诊和审核都可以在责任授权范围内修改 Diagnosis；修改须保留变更和责任历史。 |
| INV-V2-032 | Assignment 只决定责任工作上下文；工作台 Projection 不得写入 CaseStatus 或冒充领域事实。 |

## 7. 责任、报告和签发

| 编号 | 不变量 |
|---|---|
| INV-V2-033 | ResponsibilityChain 累积保存 INITIAL、REVIEW、AUDIT 等责任节点，不用 owner-transfer 覆盖历史。 |
| INV-V2-034 | 同一账号承担多个角色时，每个角色分别留痕；最终 Audit 承担签发责任。 |
| INV-V2-035 | ReportTemplate 与 DiagnosisTemplate 分离；报告取值必须固定到当次上下文和责任。 |
| INV-V2-036 | 一次 Sign-out 创建一份不可变 Report；禁止 Report → ReportVersion 嵌套。 |
| INV-V2-037 | 撤回保留原 Report 的快照、内容、签发责任和撤回理由；重新签发创建新的 Report。 |
| INV-V2-038 | 补充报告可与原有效 Report 并存；任何签发后的医学变化走受控报告事件。 |
| INV-V2-039 | PDF 和打印输出是 Report 的持久化呈现快照，不因模板修改自动变化；文件失败不回滚本地 Report。 |
| INV-V2-040 | Diagnosis 存在必须等待的 TechnicalOrder 时，按配置不得直接签发；数字切片扫描本身不得成为默认阻断条件。 |

## 8. 特殊业务和外部事实

| 编号 | 不变量 |
|---|---|
| INV-V2-041 | FrozenRound 未签发时可继续加入标本；已签发后新增标本进入新 Round。 |
| INV-V2-042 | Cytology 可无 Block；Cell Block 使用统一 Block，直接细胞学来源可直接形成 Slide。 |
| INV-V2-043 | 独立 Molecular 业务建立新 Case；原 Case 追加分子检测必须通过 TechnicalOrder 和同 Case 结果链。 |
| INV-V2-044 | 外院会诊建立本地 Case 和本地 pathology number；外院材料必须标记 External。 |
| INV-V2-045 | Send-out 沿用现有 Case，不因外送创建新的本院 Case；外部结果必须分层并核验。 |
| INV-V2-046 | External Block/Slide 可缺少完整本地来源，但不能伪装成本院来源；本院由 External Block 派生的 Slide 属于本院 Slide。 |
| INV-V2-047 | Block、Slide 分别维护 archiveLocation、currentDestination 和历史；Loan 不覆盖归档位置，销毁不删除医疗事实。 |

## 9. 权限、审计、接口和质控

| 编号 | 不变量 |
|---|---|
| INV-V2-048 | 权限至少分为功能权限、数据范围和敏感操作权限；数据范围可按账号、角色、院区、科室、诊断组和 BusinessType 判断。 |
| INV-V2-049 | 敏感修改必须记录 who、when、what、old value、new value，原因是否必填由配置决定。 |
| INV-V2-050 | 外部消息必须支持唯一标识、幂等、乱序、重试、死信、人工重放、对账和原始报文追溯。 |
| INV-V2-051 | External Integration 失败不回滚已形成内部业务；Internal Domain Call 必须在同一一致性边界内处理。 |
| INV-V2-052 | 外部收费状态只如实同步，不阻塞 PIS 主业务；报告回传键由接口配置，不写死 ReportId。 |
| INV-V2-053 | QC 默认提醒而非阻断，必须能 drill down 到具体 Case 和业务事实；明确的患者安全门槛除外。 |
| INV-V2-054 | 医疗事实不得物理删除；作废、撤回、失效、受控销毁和审计必须追加事实。 |

## 10. 配置边界和待确认项

可配置：BusinessType 各项规则、编号、模板、路由、医院字段、复核触发、打印格式、QC 阈值、归档期限、通知方式和外部接口映射。

待业务确认：具体病理号格式、医院级责任签发组合、技术医嘱阻断条件、各业务类型默认 SlideRule、归档保留期限、外部报告核验清单和各院区数据范围。这些确认不得改变上述身份、来源、责任和历史保护底线。
