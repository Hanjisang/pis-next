# S03 Device Adapter Layer 设计

文档状态：FOUNDATION COMPLETE

真实打印机/扫描仪验证：NOT VERIFIED

## 1. 边界

S03-DEV-001：业务代码只调用逻辑设备服务，不识别设备 SDK、驱动、IP、串口或厂商协议。

```text
V2 Application
  → LabelPrintService / ScannerAdapter
  → Device Adapter Registry
  → Site Adapter
  → Physical Device
```

S03-DEV-002：设备失败生成设备/打印事实和可重试错误，不得覆盖材料、诊断或报告事实。

S03-DEV-003：设备密钥、网络地址和协议参数来自 Hospital Profile/部署密钥，不得硬编码进 Domain。

## 2. 标签打印

公开接口为 `LabelPrinter` 和 `LabelPrintService`。打印命令包含逻辑打印机、端点引用、材料类型、业务编号、已渲染标签和操作人引用；业务层不传厂商对象。

现有适配器：

| Adapter | 状态 | 说明 |
|---|---|---|
| `MockPrinterAdapter` | 可用于自动化 | 产生合成设备 Job，引导成功/失败测试 |
| `GK888Adapter` | Adapter shell | 未集成 SDK/驱动，返回未配置 |
| `ZebraAdapter` | Adapter shell | 未集成 SDK/驱动，返回未配置 |

S03-DEV-004：`GK888Adapter`、`ZebraAdapter` 的存在只证明扩展边界，不代表任何型号兼容或现场打印通过。

标签模板、逻辑打印机映射和打印策略来自 Hospital Profile。补打仍必须遵守 V2 已有打印版本和审计规则。

## 3. 数字扫描

`ScannerAdapter` 接收 Case（必需）、Block/Slide（可选）和关联 ID，返回扫描任务引用。完成回调统一映射为 `ScanCompletedEvent`，包含：

- scannerCode / scannerJobReference；
- caseId / blockId / slideId；
- sourcePlatform / externalImageId；
- viewerReference / contentDigest / completedAt。

S03-DEV-005：回调进入应用服务后才可建立 DigitalSlide 绑定；适配器不能直接更新 DigitalSlide 表。

S03-DEV-006：PIS 只管理元数据、绑定和 viewerReference，不实现 WSI Tile Server、金字塔生成或扫描仪协议引擎。

`MockScannerAdapter` 已验证合成扫描完成事件。真实厂商回调签名、重放、文件校验、网络和 WSI 平台均未验证。

## 4. 现场适配器验收

每个设备适配器必须独立验证：

1. 设备型号、固件、驱动和协议版本；
2. 中文、条码、DPI、标签尺寸和边距；
3. 断网、缺纸、卡纸、重试和补打；
4. Job ID 与 PIS 审计记录关联；
5. 扫描回调鉴权、重复、乱序和摘要；
6. 设备日志不包含患者敏感正文；
7. 现场可观测性和人工降级流程。

上述项目当前均为待业务确认/NOT VERIFIED。
