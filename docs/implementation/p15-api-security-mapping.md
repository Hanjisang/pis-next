# P15 API 与授权安全映射

## 1. 内部 API

| P12 API | P14 权限 | P15 入口 | 说明 |
|---|---|---|---|
| P12-API-001 | P14-PERM-001 | `POST /api/p15/registrations/external` | 接收外部原始申请，保留原文摘要和来源身份 |
| P12-API-002 | P14-PERM-002 | `POST /api/p15/registrations/manual` | 授权登记人员手工建立申请 |
| P12-API-003 | P14-PERM-003 | `POST /api/p15/registrations/{id}/accept` | 申请进入可建案状态 |
| P12-API-004 | P14-PERM-004 | `POST /api/p15/cases` | 根据申请建立病例和上下文快照 |
| P12-API-008 | P14-PERM-008 | `POST /api/p15/cases/{id}/expected-specimens` | 登记预计标本和容器 |
| P12-API-009 | P14-PERM-009 | `POST /api/p15/specimens/receive` | 扫码、核对并接收单个标本 |
| P12-API-010 | P14-PERM-010 | `POST /api/p15/specimens/{specimenId}/isolation` | 记录隔离或退回事实 |
| P12-API-011 | P14-PERM-011 | `POST /api/p15/specimens/{specimenId}/handoffs` | 记录责任交接和签收 |
| P12-API-048/049 | P14-PERM-048/049 | `GET /api/p15/receiving-queue`、`GET /api/p15/cases/{id}/trace` | 只读、带范围和最小敏感字段 |

取消、身份纠错、质量审批和解除隔离不在本切片提供绕过审批的快捷写接口。

## 2. P14 决策输入

每个命令都重新计算主体类型、服务来源、P14 权限、医院/院区/科室/工作组、P14-SCOPE-005 队列/任务范围、P14-SCOPE-006 病理类型、P14-SCOPE-007 对象范围、P14-SCOPE-008 来源机构、对象当前状态、并发版本、任务责任和审计条件。P15 的 local/test 身份由服务端配置 `PIS_ACTOR_ID` 和 `PIS_ACTOR_PERMISSIONS` 形成；客户端不能自报实际操作者、授权结果或接收时间。非 local/test 运行环境没有真实身份提供器时默认拒绝。

登记使用 `P14-TASK-002` 接管/登记责任；接收使用 `P14-TASK-003` 交接责任；隔离方向需要材料/质量责任并追加 P14 高风险审计证据。系统身份只能处理外部原始事实接收，不能自动取得病例纠错、医学或报告责任。

## 3. 审计和事件

每个成功或拒绝命令写入 P11-TBL-051 `audit_event`。申请、病例和接收事实分别追加 `P12-EVC-001`、`P12-EVC-002`、`P12-EVC-003` outbox 事件；事件载荷只包含稳定身份、版本、业务类型、来源摘要和追溯引用，不包含患者全文或原始载荷。重复请求返回首次业务事实引用并记录一次重复处理审计，不重复发布业务事件。
