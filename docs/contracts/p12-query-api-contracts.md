# P12 查询API契约

文档状态：P12已基线化
正式查询：P12-API-043至060，共18项。查询只读业务信息，不触发状态变化、任务接管、自动纠错或外部投递。

## 1. 查询共同规则

每个查询必须声明允许调用方、对象范围、数据范围、过滤条件、排序、分页、返回Schema、数据时效、强一致/最终一致、敏感字段、脱敏、权限、最大范围参数、审计和索引依据。分页和范围上限只引用P09-PARAM-001、002、015、016，不填正式数值。查询不得提供正式修改能力，也不得以读取方式绕过状态守卫。

## 2. 查询目录

| 查询编号 | 业务目的/调用方 | 查询对象和过滤 | 排序/分页 | 返回Schema | 一致性/敏感字段 | 权限/审计/索引 |
|---|---|---|---|---|---|---|
| P12-API-043 | 对象责任模块按内部身份定位 | object_identity、object_kind_code | 固定身份排序；单对象 | SCH-002/015 | 强一致；只返回授权字段 | 对象范围审计；P11主键 |
| P12-API-044 | 业务人员按申请/病例/标本/材料编号查询 | business_number、object_kind_code | 编号唯一；无任意排序 | SCH-002/005/007/008 | 强一致；患者和诊断最小化 | 业务范围审计；唯一索引 |
| P12-API-045 | 集成责任按外部标识查找 | source_system_code、external_identifier、object_kind_code | 外部版本降序；游标 | SCH-002/020 | 强一致；外部身份高敏 | 集成权限；幂等索引 |
| P12-API-046 | 定位患者/就诊业务上下文 | source_system_code、patient/visit reference、case identity | 快照时间；游标 | SCH-003/005 | 强一致；不返回完整患者主数据 | 身份范围审计；快照索引 |
| P12-API-047 | 查看病例全链路摘要 | case_identity、modality、lifecycle state | 业务发生时间；游标 | SCH-005/006/007/012 | 投影可最终一致；正文按权限 | 病例范围审计；关系索引 |
| P12-API-048 | 追溯标本、蜡块、玻片、制备物和提取物来源 | material_identity、source_identity、derivation kind | 来源形成时间；游标 | SCH-007/008/009 | 强一致或明确投影时效；材料高敏 | 材料责任审计；来源索引 |
| P12-API-049 | 标本接收待办 | responsible_party、specimen_state、isolation_state | received_at、priority；游标 | SCH-007/009/016 | 最终一致允许；不返回诊断正文 | 工作队列权限；状态索引 |
| P12-API-050 | 取材/制片/技术工作队列 | task kind、material state、responsible party | due context、created_at；游标 | SCH-008/015/016 | 最终一致；只返回技术必要字段 | 任务权限；状态/队列索引 |
| P12-API-051 | 冰冻时效和轮次队列 | frozen_business、round_state、feedback_state | occurred_at、提醒上下文；游标 | SCH-030/016 | 最终一致；不返回无权医学正文 | 冰冻责任审计；队列索引 |
| P12-API-052 | 细胞筛查/复核队列 | preparation、adequacy、screening qualification、responsible party | task_created_at；游标 | SCH-010/029/016 | 最终一致；诊断正文分离 | 细胞责任审计；队列索引 |
| P12-API-053 | 分子任务/运行/质控队列 | task_state、run_state、quality_state、method | created_at、priority；游标 | SCH-028/016 | 原始载荷按设备权限；结果分层 | 分子责任审计；状态索引 |
| P12-API-054 | 外送任务和外部结果队列 | external organization、task state、verification state | handoff_at、received_at；游标 | SCH-027/016 | 外部正文最小化；未核验隔离 | 外送权限；外部状态索引 |
| P12-API-055 | 诊断、审核和签发队列 | diagnostic responsibility、report state、modality | assigned_at、version；游标 | SCH-010/012/013/016 | 签发前强一致；正文高敏 | 医学责任审计；报告索引 |
| P12-API-056 | 投递失败和对账队列 | target_system_code、delivery_state_code、reconciliation_difference_code | attempted_at、retry_state_code；游标 | SCH-031/032/016 | 最终一致；载荷引用不展开 | 集成权限；投递索引 |
| P12-API-057 | 报告生命周期和版本历史 | report_lifecycle_identity、event kind、version | version_number降序；游标 | SCH-012/013/016 | 强一致；正文需报告权限 | 报告审计；版本索引 |
| P12-API-058 | 诊断依据和综合组成引用 | report/diagnosis version、evidence kind | reference order；游标 | SCH-011/012/049 | 强一致；高敏医学依据 | 诊断范围审计；引用关系 |
| P12-API-059 | 材料消耗、派生和剩余 | material identity、operation kind、responsible party | occurred_at；游标 | SCH-008/009/016 | 强一致；数量和来源高敏 | 材料权限；消耗索引 |
| P12-API-060 | 数字切片和文件完整性 | slide/file version、integrity、quality、availability | version/checked_at；游标 | SCH-014/032/016 | 最终一致标注检查时间；文件权限 | 数字材料审计；文件索引 |

## 3. 查询输出限制

查询响应必须返回结果形成时间、版本、来源和数据时效；最终一致投影必须声明投影时间和未追平提示。任意字段过滤、任意排序、无限制全量、任意导出、诊断正文越权、患者身份批量拼接和通过查询改变状态均被拒绝并写入审计。
