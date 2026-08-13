# SRS V1.4 → PIS V2 实施日志

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
