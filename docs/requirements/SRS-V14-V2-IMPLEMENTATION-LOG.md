# SRS V1.4 → PIS V2 实施日志

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
