# PIS V2 外部依赖登记表

本表只登记当前代码中已经建立替换边界、但不能由合成测试宣称生产联调完成的依赖。

| 依赖 | 代码边界 | 当前替代 | 必须保留的业务事实 | 生产验收条件 | 状态 |
|---|---|---|---|---|---|
| IHC/特殊染色设备 | `IhcDevicePort` | `SimulatorIhcDeviceAdapter` | 请求引用、适配器、状态、重试次数、异常、请求/完成时间、操作者 | 厂商协议联调、失败/超时/重放和设备回执对账 | EXTERNAL_DEPENDENCY |
| 标签打印机 | `LabelPrintService` | `MockPrinterAdapter` | 产物、标签内容、打印版本、结果、失败原因、打印人和时间 | 真实打印机连接、耗材不足、离线重试和旧标签失效验证 | EXTERNAL_DEPENDENCY |
| 计费系统 | `technical_order_fee_status` side-channel | 合成费用状态请求 | 状态、外部引用、失败原因、更新时间和操作者 | 外部回执幂等、对账、退费和失败补偿 | EXTERNAL_DEPENDENCY |
| 耗材库存 | `technical_order_consumption` + `consumable_transaction` | 测试批次 | 技术项目、批次、数量、单位、原因、操作者和时间 | 库存扣减幂等、批次/效期校验和库存对账 | EXTERNAL_DEPENDENCY |
| 分子检测设备 | `MolecularInstrumentPort` | `SimulatorMolecularInstrumentAdapter` | 检测号、项目、设备/试剂绑定、请求与响应引用、尝试次数、成功/失败、错误码、时间和操作者 | 厂商协议适配、设备回执、超时/断线重试、结果对账和现场异常恢复 | EXTERNAL_DEPENDENCY |

外部依赖失败不得静默覆盖核心病例、标本、蜡块、切片或报告事实；失败应写入可追溯的异常或支持事实，并由明确的重试/补偿流程处理。
