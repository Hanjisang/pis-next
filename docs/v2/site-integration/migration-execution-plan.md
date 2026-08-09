# S05 Historical Data Migration Execution Plan

文档状态：FRAMEWORK COMPLETE

正式医院历史数据迁移：NOT STARTED / NOT VERIFIED

## 1. 原则

S05-MIG-001：迁移不是 Legacy 表复制。执行链固定为：

```text
Legacy Fact Snapshot
  → Source Adapter
  → Quarantine / Staging
  → Mapping Decision
  → Validation + Exception List
  → Approved V2 Import Command
  → Reconciliation
```

S05-MIG-002：本阶段框架只写 `migration_*` 隔离表，不直接写 Case、Specimen、Block、Slide、Diagnosis 或 Report。正式写入命令必须在 Hospital Implementation Project 中经数据样本评审后实现。

S05-MIG-003：迁移适配器和 Legacy DTO 只能位于 `integration/migration/legacy` 边界，生产 V2 不得依赖 Legacy 业务实现。

S05-MIG-004：只使用批准的脱敏样本进行开发测试；真实医疗数据不得进入仓库、日志或测试报告。

## 2. 首批对象

| Legacy Fact | 目标决定 | 关系校验 |
|---|---|---|
| Patient | 保留外部患者引用 | 标识映射唯一 |
| Case | 映射 V2 Case | 病理号、患者引用、BusinessType |
| Specimen | 映射 V2 Specimen | Case–Specimen |
| Block | 映射 V2 Block | Specimen–Block |
| Slide | 映射 V2 Slide | Block–Slide；外部材料例外必须显式建模 |
| Diagnosis | 映射 V2 Diagnosis 事实 | Case–Diagnosis、责任和时间 |
| Report Metadata | 保留历史报告元数据和文件引用 | Case–Report、签发状态和摘要 |

S05-MIG-005：历史 PDF 原文件引用必须保留，不重新生成，不把新模板渲染结果冒充历史报告。

## 3. 运行与证据

每次执行创建 `migration_run` 和不可混用的 `migration_source_manifest`，记录来源适配器、数据集版本、Schema Hash、映射规则版本、记录数和时间。

每条暂存记录保存：来源类型/ID、父对象、计划目标类型/ID、映射决定、业务引用、文件引用、Payload Digest 和不含医疗正文的证据快照。相同 Run + Source Type + Source ID 幂等更新；同一 runId 若来源快照或映射规则不同则拒绝恢复。

检查点写入 `migration_checkpoint`。当前基础检查点为 `M5-VALIDATION`；正式迁移应依次设置：

1. M1 来源冻结与清单；
2. M2 映射预检；
3. M3 隔离暂存；
4. M4 人工异常处理；
5. M5 计数/关系校验；
6. M6 批准写入；
7. M7 V2 对账和只读验收。

## 4. Validation Report

`migration_validation_report` 至少比较：

- Case、Specimen、Block、Slide、Diagnosis、Report source/staged count；
- Case–Specimen、Specimen–Block、Block–Slide、Case–Report 关系数；
- 异常数量和最终 `VALIDATED/BLOCKED/FAILED` 状态。

任何 P0/P1 异常、数量差异或关系差异均为 `BLOCKED`。不得以总数相同替代关系校验。

## 5. Migration Exception List

无法可靠转换的记录写 `migration_exception`，至少包含 exception code、严重度、来源对象、原因、人工动作、状态和证据引用。当前识别包括：

- `DUPLICATE_SOURCE_FACT`；
- `MISSING_PAYLOAD_DIGEST`；
- `INVALID_PARENT_TYPE`；
- `ORPHAN_SOURCE`；
- `PARENT_NOT_STAGED`；
- `INCOMPLETE_REPORT_REFERENCE`；
- `MANIFEST_COUNT_MISMATCH`。

S05-MIG-006：异常不得 silent ignore。父对象异常时，下级对象也不能先行进入 V2。

S05-MIG-007：`WAIVED` 只允许在医疗、数据和系统责任人批准并记录理由后使用；不能用来掩盖缺失病例/报告。

## 6. 合成验证

自动化已验证：一组 Patient→Case→Specimen→Block→Slide，加 Diagnosis 和 Report Metadata 的 7 条合成事实全部暂存，计数和四类关系差异为 0，历史 PDF 引用保持不变。

另一组合成数据验证：重复 Case、孤立 Slide、缺少 PDF 引用及父对象级联异常全部使运行进入 `BLOCKED`，且问题记录不被静默暂存。

## 7. 正式执行前置条件

- 真实 Legacy 数据字典、数量和质量画像：待业务确认；
- 标识、状态、责任人和时间字段映射：待业务确认；
- 报告文件存储可访问性和摘要：待业务确认；
- 脱敏样本和迁移演练环境：待提供；
- 导入批次、停机窗口、签字责任人和异常 SLA：待业务确认；
- 至少两轮全量演练和一次增量/回滚演练：NOT VERIFIED。

在以上条件满足前，不得执行正式生产迁移或退役历史数据源。
