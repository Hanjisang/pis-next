# S01 Hospital Profile & Configuration 设计

文档状态：FOUNDATION COMPLETE

适用范围：医院级配置，不修改 PIS V2 Core Domain

医院现场验证：NOT VERIFIED

## 1. 目标与边界

S01-HSP-001：同一份应用代码必须支持多个医院配置，不允许出现 `HospitalAController`、`HospitalBService` 等医院专用业务实现。

S01-HSP-002：Hospital Profile 只决定启用项、编号、流程策略、报告外观、设备和接口选择；不得改变 Case、Specimen、Diagnosis、Report、BusinessType、TechnicalOrder 的核心语义。

S01-HSP-003：配置记录必须带 `configuration_version`。上线项目应通过受控配置发布和审计提升版本，不得直接以生产 SQL 临时修值。

## 2. 配置模型

| 配置对象 | 数据表 | 用途 |
|---|---|---|
| Hospital | `hospital_profile` | 医院代码、名称、时区、语言和启用状态 |
| Campus | `hospital_campus` | 一个医院下的院区 |
| Department | `hospital_department` | 医院/院区下的科室范围 |
| Business Configuration | `hospital_business_type_configuration` | ROUTINE、FROZEN、CYTOLOGY、MOLECULAR、CONSULTATION 的启停及核心类型映射 |
| Workflow Configuration | `hospital_workflow_configuration` | 复诊、审核、直接制片等稳定策略 |
| Pathology Number Rule | `pathology_number_rule` | 按组织和 BusinessType 选择已有 V2 编号规则 |
| Label Configuration | `label_template`、`printer_mapping`、`print_strategy` | 标签内容、逻辑打印机、触发点、份数和重试 |
| Report Configuration | `hospital_report_configuration` | 默认模板、签名显示、Logo 引用和页脚 |
| Device Configuration | `device_configuration` | 设备逻辑码、适配器码、端点引用和非核心设置 |
| Integration Configuration | `integration_configuration` | 外部系统、适配器和端点配置 |

S01-HSP-004：`settings` JSON 只承载适配器的非核心参数，不得用来存储患者、病例、诊断或报告核心字段。

S01-HSP-005：Logo、打印机和接口端点只存受控引用；密码、Token、私钥和证书必须由部署环境或密钥管理系统注入。

## 3. BusinessType 与编号

医院侧标准名称映射如下：

| Profile 名称 | V2 核心配置 |
|---|---|
| ROUTINE | HISTOLOGY |
| FROZEN | FROZEN |
| CYTOLOGY | CYTOLOGY_NON_GYN |
| MOLECULAR | MOLECULAR |
| CONSULTATION | REFERRAL |

S01-HSP-006：禁用 BusinessType 只关闭该医院的新业务入口，不删除既有 Case，也不改变历史记录的可读性。

S01-HSP-007：编号仍由 V2 `PathologyNumberRule` 生成。Hospital Profile 只选择医院范围内的规则，不建立第二套病理号模型。

## 4. 配置解析

`HospitalProfileApplicationService` 通过 `HospitalProfileRepository` 读取完整快照，并对登记入口提供 Hospital + BusinessType 的配置解析。Profile 不存在、已禁用或 BusinessType 未配置时必须明确失败，不得回落到另一医院。

配置优先级固定为：

1. 当前认证用户的 Hospital/Organization；
2. 当前 Campus/Department 的更具体映射；
3. Hospital 默认配置；
4. 无有效配置时拒绝操作，不使用代码硬编码兜底。

## 5. 双 Profile 验证证据

仓库包含完全合成的 `HOSPITAL_A` 和 `HOSPITAL_B`：

- Routine 病理号前缀分别为 `A-P-` 和 `B-P-`；
- Hospital A 启用 Molecular，Hospital B 禁用 Molecular；
- 默认标签份数分别为 1 和 2；
- 差异由 V21 配置和应用查询产生，没有医院专用 Java 类。

PostgreSQL 集成测试从空库执行 Flyway 后验证以上差异。上述 Profile 仅为自动化样例，不代表真实医院配置已确认。

## 6. 医院项目待确认

- 真实院区、科室和组织编码；
- 各 BusinessType 的启用范围和审核策略；
- 病理号格式、年度切换、并发容量和补号规则；
- 标签尺寸、条码规范、份数和逻辑打印机；
- 报告模板、Logo、页脚和签名显示合规要求。

以上均标记为：待业务确认。
