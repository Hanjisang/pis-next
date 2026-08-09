# S02 Integration Architecture

文档状态：FOUNDATION COMPLETE

真实 HIS/LIS/EMR/平台联调：NOT VERIFIED

## 1. 强制调用边界

S02-INT-001：外部系统不得直接写 PIS V2 核心表。固定调用方向为：

```text
External System
  → Integration DTO
  → IntegrationMessageMapper
  → IntegrationEnvelope
  → Integration Adapter
  → Application Service
  → V2 Domain Command
```

S02-INT-002：外部 Patient/Order/Result DTO 只存在于适配器边界，不得进入 V2 Domain 方法签名或持久化模型。

S02-INT-003：入站适配器完成协议校验、鉴权、字段映射和幂等校验后，才可调用公开应用命令；出站适配器只读取已提交业务事实。

## 2. 能力目录

统一 `IntegrationCapability` 已预留：

| 系统 | 能力 |
|---|---|
| HIS | Patient Query、Encounter Query、Order Receive、Fee Status Sync、Report Delivery |
| EMR | Report View、Clinical Information Query |
| LIS | Specimen、Result |
| 外部平台 | Province Platform、Regional Platform |

实际协议、厂商字段、认证方式和传输通道属于 Hospital Implementation Project，不在本阶段实现。

## 3. 消息可靠性

`IntegrationMessageLog` 记录医院、方向、源/目标系统、消息 ID、能力、业务键、请求引用和摘要、状态、错误及重试信息。请求正文不得直接写入普通日志；原始报文应保存到受控存储，并只在消息日志中记录引用和摘要。

状态为：

```text
PENDING → SUCCEEDED
       ↘ RETRY_PENDING → DEAD_LETTER
```

S02-INT-004：幂等键为 Hospital + Source System + Target System + Message ID。同一键和同一摘要返回既有结果；同一键但不同摘要必须拒绝并告警。

S02-INT-005：每次调用写 `integration_attempt`。自动重试耗尽后写 `integration_dead_letter`；人工重放必须先写 `integration_replay_request`，记录申请人和原因。

S02-INT-006：`external_identifier_mapping` 保存外部标识与 V2 内部标识的版本化映射，不得把外部 ID 当作 V2 主键。

S02-INT-007：每日对账写 `integration_reconciliation`；数量或状态不一致时保留差异和证据引用，不得自动猜测修复医疗事实。

## 4. 事务与失败语义

核心业务提交和外部投递是两个事务边界。例如：

```text
Report SIGNED（已提交）
  → HIS Delivery FAILED（可重试/死信）
```

S02-INT-008：HIS 投递失败不得回滚已签发 Report。系统应告警、重试、死信和对账，但报告事实保持有效。

S02-INT-009：外部 Order 重复、乱序或暂时不可用时，适配器必须依据消息时间、外部标识映射和业务不变量决定接受、等待或进入人工处理；不得直接覆盖核心状态。

## 5. 已验证范围

- `MockHisAdapter`：订单能力、DTO 映射和幂等；
- `MockReportDeliveryAdapter`：报告投递失败、三次尝试和死信；
- `MockLisAdapter`：标本/结果能力边界；
- 数据库：消息、尝试、死信、重放、对账和外部标识映射六类表；
- 架构守卫：V2 Domain 不依赖 Integration DTO/Adapter，Integration 不依赖 V2 Domain 实现。

以上均为合成 Mock 验证。真实接口的网络、证书、字段、消息乱序、性能和医院对账未验证。

## 6. 实施项目输入

每个医院接口必须另行提供：接口责任方、版本、协议、鉴权、超时、重试上限、幂等键、乱序规则、原始报文存储、脱敏规则、死信处置、对账文件和联调环境。缺失项标记为“待业务确认”，不得以默认猜测上线。
