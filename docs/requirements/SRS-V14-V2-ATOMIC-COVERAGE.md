# SRS V1.4 → PIS V2 Atomic Coverage Baseline

原子基线：`7efacb4c4d575e37481f42d16e49b457f7f0f845`。生成日期：2026-08-12。FC02A 闭环基线：`bcdf9b83c6d6feeb5e6cdb87602fd9948662aa57`。FC02B 实际起始基线：`5efd315935c16a86044673dfa3b4fc7bcbbd0f79`。FC03A 实际起始基线：`09f763241cc029ccde781024695d305b3692dc1c`。

## 1. 口径

本文件取代按大模块完成度统计的方式。每条记录表示用户可独立执行或观察的能力；状态仅使用 COMPLETE、PARTIAL、MISSING、EXTERNAL_DEPENDENCY、CONFLICT_RESOLVED_BY_V2。O01–O16 旧工作台大项不参与统计，改由第 3 节 WB 原子项替代；A14–A18 按扫码、匹配、拒绝、事实保存、查询和导出拆为 13 条。非 WB 缺口在 FC01A 中只记录，不扩展实现。

| Status | Count |
|---|---:|
| COMPLETE | 338 |
| PARTIAL | 120 |
| MISSING | 204 |
| EXTERNAL_DEPENDENCY | 80 |
| CONFLICT_RESOLVED_BY_V2 | 0 |
| **TOTAL** | **742** |

2026-08-14 校准：以上统计按本文件当前 `###` 原子记录的 `Status` 字段重新计算；FC03C/FC03C1 的实施说明小节不计入原子总数。

FC02A 仅更新 Application、Registration、Case cancellation 与 APP-SEND 连续链：33 条由 PARTIAL 转为 COMPLETE，TOTAL 不变。产品内 Patient/Print Port 与 Simulator 已形成可替换、可测试闭环；真实医院 HIS 与真实打印硬件仍由独立 EXTERNAL_DEPENDENCY 原子项承担，本文不宣称生产联调完成。

FC02B 仅更新 Specimen → Grossing → Block 连续链：`SPEC-011` 由 PARTIAL 转为 COMPLETE；`GROSS-006`、`GROSS-008`、`GROSS-009`、`GROSS-010` 由 MISSING 转为 COMPLETE；`GROSS-007` 在产品内 Port、Simulator、失败语义与审计闭环后转为 EXTERNAL_DEPENDENCY，明确保留真实拍摄台硬件联调缺口。TOTAL 不变。
FC03A 仅更新常规组织 Block → Slide 主链：`PROD-014`、`SLIDE-013`、`SLIDE-015` 由 PARTIAL 转为 COMPLETE。统一 Slide、可选技术记录、物理返工、编号历史、软失效、打印、权限和数据隔离已形成闭环；自动脱水机、染色机、封片机等真实设备联调仍由既有 DEVICE/INT 外部依赖项承载。`QC-004` 仍为 PARTIAL，当前异常与返工事实不冒充完整玻片质控闭环。TOTAL 不变。
FC03B 仅更新直接细胞制片链：`CYTO-002`、`CYTO-003` 由 PARTIAL 转为 COMPLETE。统一 Case/Specimen/Slide 支持零玻片进入队列、多标本规则投影、直接 Specimen → Slide、制片方式审计、打印/重打和 PostgreSQL 并发唯一性；液基制片仪、细胞染色机、封片机及扫码硬件真实联调仍保持 EXTERNAL_DEPENDENCY。TBS、细胞诊断模板和细胞报告不在本轮关闭范围。TOTAL 不变。
FC03C1 更新 Frozen Closure：`FROZEN-011`、`FROZEN-017` 由 PARTIAL 转为 COMPLETE；`FROZEN-012` 保持 EXTERNAL_DEPENDENCY。通知失败/重试仅验证 Simulator、不可变 attempt history、权限和报告身份边界，不宣称真实 OR/HIS 联调完成；Frozen/常规对照仅并列展示事实，不自动判定医学一致性。TOTAL 不变。

## 2. Atomic requirements

### APP-001 — 门诊病理电子申请

- ID: APP-001
- Source: SRS V1.4 A01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 门诊病理电子申请
- Behavior: 系统执行或展示[门诊病理电子申请]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A01 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`V2ApplicationController`；APP-003 另由 `PatientInfoProviderPort` 与 `SimulatorPatientInfoProvider` 提供可替换集成边界。
- DB Evidence: `V28__application_case_mapping.sql` 与 `V35__application_registration_closure.sql` 保存申请、项目、患者/就诊快照、取消、送检及打印事实。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供门诊/住院申请、HIS 查询与人工补录、多项目编辑/取消、校验和打印入口。
- Test Evidence: `V2ApplicationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 覆盖创建、更新、取消、映射、校验、打印及失败语义。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实外部系统或硬件验证按独立 INT/DEVICE 原子项统计。
- V2 Decision: FC02A 沿用 Application 与 ApplicationItem 既有模型完成闭环；Application 仍不等同于 Case，已登记 Item 的 Case 不被申请侧修改或取消逆向覆盖。

### APP-002 — 住院病理电子申请

- ID: APP-002
- Source: SRS V1.4 A02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 住院病理电子申请
- Behavior: 系统执行或展示[住院病理电子申请]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A02 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`V2ApplicationController`；APP-003 另由 `PatientInfoProviderPort` 与 `SimulatorPatientInfoProvider` 提供可替换集成边界。
- DB Evidence: `V28__application_case_mapping.sql` 与 `V35__application_registration_closure.sql` 保存申请、项目、患者/就诊快照、取消、送检及打印事实。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供门诊/住院申请、HIS 查询与人工补录、多项目编辑/取消、校验和打印入口。
- Test Evidence: `V2ApplicationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 覆盖创建、更新、取消、映射、校验、打印及失败语义。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实外部系统或硬件验证按独立 INT/DEVICE 原子项统计。
- V2 Decision: FC02A 沿用 Application 与 ApplicationItem 既有模型完成闭环；Application 仍不等同于 Case，已登记 Item 的 Case 不被申请侧修改或取消逆向覆盖。

### APP-003 — HIS患者基本信息自动获取

- ID: APP-003
- Source: SRS V1.4 A03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: HIS患者基本信息自动获取
- Behavior: 系统执行或展示[HIS患者基本信息自动获取]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A03 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`V2ApplicationController`；APP-003 另由 `PatientInfoProviderPort` 与 `SimulatorPatientInfoProvider` 提供可替换集成边界。
- DB Evidence: `V28__application_case_mapping.sql` 与 `V35__application_registration_closure.sql` 保存申请、项目、患者/就诊快照、取消、送检及打印事实。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供门诊/住院申请、HIS 查询与人工补录、多项目编辑/取消、校验和打印入口。
- Test Evidence: `V2ApplicationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 覆盖创建、更新、取消、映射、校验、打印及失败语义。
- Status: COMPLETE
- Gap: 产品内 Port、Simulator、映射、未找到/故障处理已闭环；真实医院 HIS 联调仍归 `INT-HIS-*` 的 EXTERNAL_DEPENDENCY，不冒充生产验证。
- V2 Decision: FC02A 沿用 Application 与 ApplicationItem 既有模型完成闭环；Application 仍不等同于 Case，已登记 Item 的 Case 不被申请侧修改或取消逆向覆盖。

### APP-004 — 人工患者信息补录

- ID: APP-004
- Source: SRS V1.4 A04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 人工患者信息补录
- Behavior: 系统执行或展示[人工患者信息补录]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A04 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`V2ApplicationController`；APP-003 另由 `PatientInfoProviderPort` 与 `SimulatorPatientInfoProvider` 提供可替换集成边界。
- DB Evidence: `V28__application_case_mapping.sql` 与 `V35__application_registration_closure.sql` 保存申请、项目、患者/就诊快照、取消、送检及打印事实。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供门诊/住院申请、HIS 查询与人工补录、多项目编辑/取消、校验和打印入口。
- Test Evidence: `V2ApplicationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 覆盖创建、更新、取消、映射、校验、打印及失败语义。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实外部系统或硬件验证按独立 INT/DEVICE 原子项统计。
- V2 Decision: FC02A 沿用 Application 与 ApplicationItem 既有模型完成闭环；Application 仍不等同于 Case，已登记 Item 的 Case 不被申请侧修改或取消逆向覆盖。

### APP-005 — 多种病理申请类型

- ID: APP-005
- Source: SRS V1.4 A05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 多种病理申请类型
- Behavior: 系统执行或展示[多种病理申请类型]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A05 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`V2ApplicationController`；APP-003 另由 `PatientInfoProviderPort` 与 `SimulatorPatientInfoProvider` 提供可替换集成边界。
- DB Evidence: `V28__application_case_mapping.sql` 与 `V35__application_registration_closure.sql` 保存申请、项目、患者/就诊快照、取消、送检及打印事实。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供门诊/住院申请、HIS 查询与人工补录、多项目编辑/取消、校验和打印入口。
- Test Evidence: `V2ApplicationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 覆盖创建、更新、取消、映射、校验、打印及失败语义。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实外部系统或硬件验证按独立 INT/DEVICE 原子项统计。
- V2 Decision: FC02A 沿用 Application 与 ApplicationItem 既有模型完成闭环；Application 仍不等同于 Case，已登记 Item 的 Case 不被申请侧修改或取消逆向覆盖。

### APP-006 — 申请新增

- ID: APP-006
- Source: SRS V1.4 A06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 申请新增
- Behavior: 系统执行或展示[申请新增]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A06 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`V2ApplicationController`；APP-003 另由 `PatientInfoProviderPort` 与 `SimulatorPatientInfoProvider` 提供可替换集成边界。
- DB Evidence: `V28__application_case_mapping.sql` 与 `V35__application_registration_closure.sql` 保存申请、项目、患者/就诊快照、取消、送检及打印事实。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供门诊/住院申请、HIS 查询与人工补录、多项目编辑/取消、校验和打印入口。
- Test Evidence: `V2ApplicationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 覆盖创建、更新、取消、映射、校验、打印及失败语义。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实外部系统或硬件验证按独立 INT/DEVICE 原子项统计。
- V2 Decision: FC02A 沿用 Application 与 ApplicationItem 既有模型完成闭环；Application 仍不等同于 Case，已登记 Item 的 Case 不被申请侧修改或取消逆向覆盖。

### APP-007 — 申请修改

- ID: APP-007
- Source: SRS V1.4 A07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 申请修改
- Behavior: 系统执行或展示[申请修改]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A07 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`V2ApplicationController`；APP-003 另由 `PatientInfoProviderPort` 与 `SimulatorPatientInfoProvider` 提供可替换集成边界。
- DB Evidence: `V28__application_case_mapping.sql` 与 `V35__application_registration_closure.sql` 保存申请、项目、患者/就诊快照、取消、送检及打印事实。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供门诊/住院申请、HIS 查询与人工补录、多项目编辑/取消、校验和打印入口。
- Test Evidence: `V2ApplicationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 覆盖创建、更新、取消、映射、校验、打印及失败语义。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实外部系统或硬件验证按独立 INT/DEVICE 原子项统计。
- V2 Decision: FC02A 沿用 Application 与 ApplicationItem 既有模型完成闭环；Application 仍不等同于 Case，已登记 Item 的 Case 不被申请侧修改或取消逆向覆盖。

### APP-008 — 申请取消

- ID: APP-008
- Source: SRS V1.4 A08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 申请取消
- Behavior: 系统执行或展示[申请取消]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A08 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`V2ApplicationController`；APP-003 另由 `PatientInfoProviderPort` 与 `SimulatorPatientInfoProvider` 提供可替换集成边界。
- DB Evidence: `V28__application_case_mapping.sql` 与 `V35__application_registration_closure.sql` 保存申请、项目、患者/就诊快照、取消、送检及打印事实。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供门诊/住院申请、HIS 查询与人工补录、多项目编辑/取消、校验和打印入口。
- Test Evidence: `V2ApplicationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 覆盖创建、更新、取消、映射、校验、打印及失败语义。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实外部系统或硬件验证按独立 INT/DEVICE 原子项统计。
- V2 Decision: FC02A 沿用 Application 与 ApplicationItem 既有模型完成闭环；Application 仍不等同于 Case，已登记 Item 的 Case 不被申请侧修改或取消逆向覆盖。

### APP-009 — 申请项目到 BusinessType 映射

- ID: APP-009
- Source: SRS V1.4 A09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 申请项目到 BusinessType 映射
- Behavior: 系统执行或展示[申请项目到 BusinessType 映射]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A09 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`V2ApplicationController`；APP-003 另由 `PatientInfoProviderPort` 与 `SimulatorPatientInfoProvider` 提供可替换集成边界。
- DB Evidence: `V28__application_case_mapping.sql` 与 `V35__application_registration_closure.sql` 保存申请、项目、患者/就诊快照、取消、送检及打印事实。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供门诊/住院申请、HIS 查询与人工补录、多项目编辑/取消、校验和打印入口。
- Test Evidence: `V2ApplicationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 覆盖创建、更新、取消、映射、校验、打印及失败语义。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实外部系统或硬件验证按独立 INT/DEVICE 原子项统计。
- V2 Decision: FC02A 沿用 Application 与 ApplicationItem 既有模型完成闭环；Application 仍不等同于 Case，已登记 Item 的 Case 不被申请侧修改或取消逆向覆盖。

### APP-010 — 申请单完整性校验

- ID: APP-010
- Source: SRS V1.4 A10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 申请单完整性校验
- Behavior: 系统执行或展示[申请单完整性校验]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A10 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`V2ApplicationController`；APP-003 另由 `PatientInfoProviderPort` 与 `SimulatorPatientInfoProvider` 提供可替换集成边界。
- DB Evidence: `V28__application_case_mapping.sql` 与 `V35__application_registration_closure.sql` 保存申请、项目、患者/就诊快照、取消、送检及打印事实。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供门诊/住院申请、HIS 查询与人工补录、多项目编辑/取消、校验和打印入口。
- Test Evidence: `V2ApplicationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 覆盖创建、更新、取消、映射、校验、打印及失败语义。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实外部系统或硬件验证按独立 INT/DEVICE 原子项统计。
- V2 Decision: FC02A 沿用 Application 与 ApplicationItem 既有模型完成闭环；Application 仍不等同于 Case，已登记 Item 的 Case 不被申请侧修改或取消逆向覆盖。

### APP-011 — 标本条码打印

- ID: APP-011
- Source: SRS V1.4 A11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 标本条码打印
- Behavior: 系统执行或展示[标本条码打印]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A11 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`V2ApplicationController`；APP-003 另由 `PatientInfoProviderPort` 与 `SimulatorPatientInfoProvider` 提供可替换集成边界。
- DB Evidence: `V28__application_case_mapping.sql` 与 `V35__application_registration_closure.sql` 保存申请、项目、患者/就诊快照、取消、送检及打印事实。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供门诊/住院申请、HIS 查询与人工补录、多项目编辑/取消、校验和打印入口。
- Test Evidence: `V2ApplicationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 覆盖创建、更新、取消、映射、校验、打印及失败语义。
- Status: COMPLETE
- Gap: 打印业务、稳定顺序、重打与成功/失败日志已闭环；真实打印机硬件接入仍为独立 EXTERNAL_DEPENDENCY。
- V2 Decision: FC02A 沿用 Application 与 ApplicationItem 既有模型完成闭环；Application 仍不等同于 Case，已登记 Item 的 Case 不被申请侧修改或取消逆向覆盖。

### APP-012 — 批量条码打印

- ID: APP-012
- Source: SRS V1.4 A12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 批量条码打印
- Behavior: 系统执行或展示[批量条码打印]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A12 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`V2ApplicationController`；APP-003 另由 `PatientInfoProviderPort` 与 `SimulatorPatientInfoProvider` 提供可替换集成边界。
- DB Evidence: `V28__application_case_mapping.sql` 与 `V35__application_registration_closure.sql` 保存申请、项目、患者/就诊快照、取消、送检及打印事实。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供门诊/住院申请、HIS 查询与人工补录、多项目编辑/取消、校验和打印入口。
- Test Evidence: `V2ApplicationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 覆盖创建、更新、取消、映射、校验、打印及失败语义。
- Status: COMPLETE
- Gap: 打印业务、稳定顺序、重打与成功/失败日志已闭环；真实打印机硬件接入仍为独立 EXTERNAL_DEPENDENCY。
- V2 Decision: FC02A 沿用 Application 与 ApplicationItem 既有模型完成闭环；Application 仍不等同于 Case，已登记 Item 的 Case 不被申请侧修改或取消逆向覆盖。

### APP-013 — 打印日志

- ID: APP-013
- Source: SRS V1.4 A13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 打印日志
- Behavior: 系统执行或展示[打印日志]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A13 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`V2ApplicationController`；APP-003 另由 `PatientInfoProviderPort` 与 `SimulatorPatientInfoProvider` 提供可替换集成边界。
- DB Evidence: `V28__application_case_mapping.sql` 与 `V35__application_registration_closure.sql` 保存申请、项目、患者/就诊快照、取消、送检及打印事实。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供门诊/住院申请、HIS 查询与人工补录、多项目编辑/取消、校验和打印入口。
- Test Evidence: `V2ApplicationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 覆盖创建、更新、取消、映射、校验、打印及失败语义。
- Status: COMPLETE
- Gap: 打印业务、稳定顺序、重打与成功/失败日志已闭环；真实打印机硬件接入仍为独立 EXTERNAL_DEPENDENCY。
- V2 Decision: FC02A 沿用 Application 与 ApplicationItem 既有模型完成闭环；Application 仍不等同于 Case，已登记 Item 的 Case 不被申请侧修改或取消逆向覆盖。

### REG-001 — HIS申请登记

- ID: REG-001
- Source: SRS V1.4 B01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: HIS申请登记
- Behavior: 系统执行或展示[HIS申请登记]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 B01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B01 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### REG-002 — 手工登记

- ID: REG-002
- Source: SRS V1.4 B02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 手工登记
- Behavior: 系统执行或展示[手工登记]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 B02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B02 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### REG-003 — 无申请直接登记

- ID: REG-003
- Source: SRS V1.4 B03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 无申请直接登记
- Behavior: 系统执行或展示[无申请直接登记]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 B03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B03 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### REG-004 — 多 BusinessType 登记

- ID: REG-004
- Source: SRS V1.4 B04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 多 BusinessType 登记
- Behavior: 系统执行或展示[多 BusinessType 登记]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 B04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B04 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### REG-005 — 病理号自动生成

- ID: REG-005
- Source: SRS V1.4 B05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 病理号自动生成
- Behavior: 系统执行或展示[病理号自动生成]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 B05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B05 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### REG-006 — BusinessType 独立编号规则

- ID: REG-006
- Source: SRS V1.4 B06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: BusinessType 独立编号规则
- Behavior: 系统执行或展示[BusinessType 独立编号规则]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 B06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B06 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### REG-007 — 病理号重复校验

- ID: REG-007
- Source: SRS V1.4 B07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 病理号重复校验
- Behavior: 系统执行或展示[病理号重复校验]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 B07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B07 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### REG-008 — 授权病理号纠正

- ID: REG-008
- Source: SRS V1.4 B08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 授权病理号纠正
- Behavior: 系统执行或展示[授权病理号纠正]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 B08 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`/`V2ApplicationController` 完成核对与拒收；`V2RegistrationApplicationService`/`V2RegistrationController` 完成病理号纠正、正式标签和门诊回执。
- DB Evidence: `V35__application_registration_closure.sql` 保存逐 Item 核对/拒收、正式标签打印日志、病理号变更历史与 Case 快照。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 在同一 Focused Workspace 展示申请、核对、拒收、登记、标签与回执；`V2CaseContext.vue` 提供授权病理号更正。
- Test Evidence: `V2ApplicationWebTest`、`V2RegistrationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 覆盖原因必填、拒收不建 Case、历史、打印及 403。
- Status: COMPLETE
- Gap: 无当前已知闭环缺口。
- V2 Decision: 拒收是 ApplicationItem/来样事实且不创建 Case；病理号纠正保持 CaseId 与下游材料不变并保留完整历史。

### REG-009 — 标本接收

- ID: REG-009
- Source: SRS V1.4 B09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 标本接收
- Behavior: 系统执行或展示[标本接收]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 B09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B09 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### REG-010 — 标本核对

- ID: REG-010
- Source: SRS V1.4 B10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 标本核对
- Behavior: 系统执行或展示[标本核对]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 B10 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`/`V2ApplicationController` 完成核对与拒收；`V2RegistrationApplicationService`/`V2RegistrationController` 完成病理号纠正、正式标签和门诊回执。
- DB Evidence: `V35__application_registration_closure.sql` 保存逐 Item 核对/拒收、正式标签打印日志、病理号变更历史与 Case 快照。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 在同一 Focused Workspace 展示申请、核对、拒收、登记、标签与回执；`V2CaseContext.vue` 提供授权病理号更正。
- Test Evidence: `V2ApplicationWebTest`、`V2RegistrationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 覆盖原因必填、拒收不建 Case、历史、打印及 403。
- Status: COMPLETE
- Gap: 无当前已知闭环缺口。
- V2 Decision: 拒收是 ApplicationItem/来样事实且不创建 Case；病理号纠正保持 CaseId 与下游材料不变并保留完整历史。

### REG-011 — 不合格标本拒收

- ID: REG-011
- Source: SRS V1.4 B11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 不合格标本拒收
- Behavior: 系统执行或展示[不合格标本拒收]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 B11 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`/`V2ApplicationController` 完成核对与拒收；`V2RegistrationApplicationService`/`V2RegistrationController` 完成病理号纠正、正式标签和门诊回执。
- DB Evidence: `V35__application_registration_closure.sql` 保存逐 Item 核对/拒收、正式标签打印日志、病理号变更历史与 Case 快照。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 在同一 Focused Workspace 展示申请、核对、拒收、登记、标签与回执；`V2CaseContext.vue` 提供授权病理号更正。
- Test Evidence: `V2ApplicationWebTest`、`V2RegistrationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 覆盖原因必填、拒收不建 Case、历史、打印及 403。
- Status: COMPLETE
- Gap: 无当前已知闭环缺口。
- V2 Decision: 拒收是 ApplicationItem/来样事实且不创建 Case；病理号纠正保持 CaseId 与下游材料不变并保留完整历史。

### REG-012 — 拒收原因

- ID: REG-012
- Source: SRS V1.4 B12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 拒收原因
- Behavior: 系统执行或展示[拒收原因]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 B12 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`/`V2ApplicationController` 完成核对与拒收；`V2RegistrationApplicationService`/`V2RegistrationController` 完成病理号纠正、正式标签和门诊回执。
- DB Evidence: `V35__application_registration_closure.sql` 保存逐 Item 核对/拒收、正式标签打印日志、病理号变更历史与 Case 快照。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 在同一 Focused Workspace 展示申请、核对、拒收、登记、标签与回执；`V2CaseContext.vue` 提供授权病理号更正。
- Test Evidence: `V2ApplicationWebTest`、`V2RegistrationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 覆盖原因必填、拒收不建 Case、历史、打印及 403。
- Status: COMPLETE
- Gap: 无当前已知闭环缺口。
- V2 Decision: 拒收是 ApplicationItem/来样事实且不创建 Case；病理号纠正保持 CaseId 与下游材料不变并保留完整历史。

### REG-013 — 标本补充信息

- ID: REG-013
- Source: SRS V1.4 B13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 标本补充信息
- Behavior: 系统执行或展示[标本补充信息]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 B13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B13 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### REG-014 — 登记标签打印

- ID: REG-014
- Source: SRS V1.4 B14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 登记标签打印
- Behavior: 系统执行或展示[登记标签打印]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 B14 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`/`V2ApplicationController` 完成核对与拒收；`V2RegistrationApplicationService`/`V2RegistrationController` 完成病理号纠正、正式标签和门诊回执。
- DB Evidence: `V35__application_registration_closure.sql` 保存逐 Item 核对/拒收、正式标签打印日志、病理号变更历史与 Case 快照。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 在同一 Focused Workspace 展示申请、核对、拒收、登记、标签与回执；`V2CaseContext.vue` 提供授权病理号更正。
- Test Evidence: `V2ApplicationWebTest`、`V2RegistrationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 覆盖原因必填、拒收不建 Case、历史、打印及 403。
- Status: COMPLETE
- Gap: 产品内打印与日志已闭环；真实标签机/回执打印机仍按 DEVICE-PRINT-* 作为 EXTERNAL_DEPENDENCY。
- V2 Decision: 拒收是 ApplicationItem/来样事实且不创建 Case；病理号纠正保持 CaseId 与下游材料不变并保留完整历史。

### REG-015 — 门诊回执打印

- ID: REG-015
- Source: SRS V1.4 B15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 门诊回执打印
- Behavior: 系统执行或展示[门诊回执打印]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 B15 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`/`V2ApplicationController` 完成核对与拒收；`V2RegistrationApplicationService`/`V2RegistrationController` 完成病理号纠正、正式标签和门诊回执。
- DB Evidence: `V35__application_registration_closure.sql` 保存逐 Item 核对/拒收、正式标签打印日志、病理号变更历史与 Case 快照。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 在同一 Focused Workspace 展示申请、核对、拒收、登记、标签与回执；`V2CaseContext.vue` 提供授权病理号更正。
- Test Evidence: `V2ApplicationWebTest`、`V2RegistrationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 覆盖原因必填、拒收不建 Case、历史、打印及 403。
- Status: COMPLETE
- Gap: 产品内打印与日志已闭环；真实标签机/回执打印机仍按 DEVICE-PRINT-* 作为 EXTERNAL_DEPENDENCY。
- V2 Decision: 拒收是 ApplicationItem/来样事实且不创建 Case；病理号纠正保持 CaseId 与下游材料不变并保留完整历史。

### REG-016 — 电子登记本

- ID: REG-016
- Source: SRS V1.4 B16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 电子登记本
- Behavior: 系统执行或展示[电子登记本]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 B16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B16 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### REG-017 — 登记操作日志

- ID: REG-017
- Source: SRS V1.4 B17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 登记操作日志
- Behavior: 系统执行或展示[登记操作日志]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 B17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B17 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### REG-018 — 登记后 Case Progress Projection

- ID: REG-018
- Source: SRS V1.4 B18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 登记后 Case Progress Projection
- Behavior: 系统执行或展示[登记后 Case Progress Projection]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 B18 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B18 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B18 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B18 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md B18 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-001 — CaseId

- ID: CASE-001
- Source: SRS V1.4 C01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: CaseId
- Behavior: 系统执行或展示[CaseId]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 C01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C01 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-002 — BusinessType

- ID: CASE-002
- Source: SRS V1.4 C02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: BusinessType
- Behavior: 系统执行或展示[BusinessType]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 C02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C02 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-003 — PathologyNo

- ID: CASE-003
- Source: SRS V1.4 C03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: PathologyNo
- Behavior: 系统执行或展示[PathologyNo]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 C03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C03 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-004 — ACTIVE / CANCELLED

- ID: CASE-004
- Source: SRS V1.4 C04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: ACTIVE / CANCELLED
- Behavior: 系统执行或展示[ACTIVE / CANCELLED]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 C04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C04 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-005 — Case cancellation

- ID: CASE-005
- Source: SRS V1.4 C05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Case cancellation
- Behavior: 系统执行或展示[Case cancellation]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 C05 对应产品入口
- Backend Evidence: `V2RegistrationApplicationService`、`JdbcV2RegistrationRepository` 与 `V2RegistrationController` 实现授权软取消、活跃编号绑定释放和历史查询；`JdbcV2SearchRepository` 支持新旧号检索。
- DB Evidence: `V35__application_registration_closure.sql` 保存取消原因/人/时间、病理号历史，并以 active-only 唯一索引约束有效号码绑定。
- Frontend Evidence: `V2CaseContext.vue` 的“更多”菜单提供取消病例并清晰展示已取消状态、原因和时间线；全局搜索仍可进入病例中心。
- Test Evidence: `V2RegistrationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 验证不硬删除、材料保留、正常队列排除、新旧号可搜索和无权限 403。
- Status: COMPLETE
- Gap: 无当前已知闭环缺口；编号生成器不会主动复用已取消号码。
- V2 Decision: Case 取消独立于 Application 取消；只释放 active number binding，保留 Case、材料、诊断、报告、审计及号码历史。

### CASE-006 — pathology number release

- ID: CASE-006
- Source: SRS V1.4 C06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: pathology number release
- Behavior: 系统执行或展示[pathology number release]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 C06 对应产品入口
- Backend Evidence: `V2RegistrationApplicationService`、`JdbcV2RegistrationRepository` 与 `V2RegistrationController` 实现授权软取消、活跃编号绑定释放和历史查询；`JdbcV2SearchRepository` 支持新旧号检索。
- DB Evidence: `V35__application_registration_closure.sql` 保存取消原因/人/时间、病理号历史，并以 active-only 唯一索引约束有效号码绑定。
- Frontend Evidence: `V2CaseContext.vue` 的“更多”菜单提供取消病例并清晰展示已取消状态、原因和时间线；全局搜索仍可进入病例中心。
- Test Evidence: `V2RegistrationWebTest`、`RegistrationPermissionAndDataScopeTest` 与 `fc02a-registration.spec.ts` 验证不硬删除、材料保留、正常队列排除、新旧号可搜索和无权限 403。
- Status: COMPLETE
- Gap: 无当前已知闭环缺口；编号生成器不会主动复用已取消号码。
- V2 Decision: Case 取消独立于 Application 取消；只释放 active number binding，保留 Case、材料、诊断、报告、审计及号码历史。

### CASE-007 — Case progress projection

- ID: CASE-007
- Source: SRS V1.4 C07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Case progress projection
- Behavior: 系统执行或展示[Case progress projection]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 C07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C07 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-008 — current handler projection

- ID: CASE-008
- Source: SRS V1.4 C08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: current handler projection
- Behavior: 系统执行或展示[current handler projection]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 C08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C08 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-009 — report status projection

- ID: CASE-009
- Source: SRS V1.4 C09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: report status projection
- Behavior: 系统执行或展示[report status projection]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 C09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C09 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-010 — patient context

- ID: CASE-010
- Source: SRS V1.4 C10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: patient context
- Behavior: 系统执行或展示[patient context]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 C10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C10 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-011 — clinical context

- ID: CASE-011
- Source: SRS V1.4 C11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: clinical context
- Behavior: 系统执行或展示[clinical context]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 C11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C11 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CASE-011 独立立项、实现和验收。

### CASE-012 — Case Center

- ID: CASE-012
- Source: SRS V1.4 C12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Case Center
- Behavior: 系统执行或展示[Case Center]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 C12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C12 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-013 — complete history

- ID: CASE-013
- Source: SRS V1.4 C13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: complete history
- Behavior: 系统执行或展示[complete history]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 C13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C13 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-014 — audit history

- ID: CASE-014
- Source: SRS V1.4 C14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: audit history
- Behavior: 系统执行或展示[audit history]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 C14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md C14 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SPEC-001 — 一个 Case 多 Specimen

- ID: SPEC-001
- Source: SRS V1.4 D01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 一个 Case 多 Specimen
- Behavior: 系统执行或展示[一个 Case 多 Specimen]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 D01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D01 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SPEC-002 — 标本编号

- ID: SPEC-002
- Source: SRS V1.4 D02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 标本编号
- Behavior: 系统执行或展示[标本编号]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 D02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D02 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SPEC-003 — 标本类型

- ID: SPEC-003
- Source: SRS V1.4 D03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 标本类型
- Behavior: 系统执行或展示[标本类型]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 D03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D03 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SPEC-004 — 标本部位

- ID: SPEC-004
- Source: SRS V1.4 D04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 标本部位
- Behavior: 系统执行或展示[标本部位]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 D04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D04 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SPEC-005 — 离体时间

- ID: SPEC-005
- Source: SRS V1.4 D05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 离体时间
- Behavior: 系统执行或展示[离体时间]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 D05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D05 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SPEC-005 独立立项、实现和验收。

### SPEC-006 — 固定时间

- ID: SPEC-006
- Source: SRS V1.4 D06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 固定时间
- Behavior: 系统执行或展示[固定时间]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 D06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D06 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SPEC-006 独立立项、实现和验收。

### SPEC-007 — 接收时间

- ID: SPEC-007
- Source: SRS V1.4 D07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 接收时间
- Behavior: 系统执行或展示[接收时间]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 D07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D07 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SPEC-008 — 标本信息修改

- ID: SPEC-008
- Source: SRS V1.4 D08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 标本信息修改
- Behavior: 系统执行或展示[标本信息修改]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 D08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D08 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SPEC-009 — 标本新增

- ID: SPEC-009
- Source: SRS V1.4 D09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 标本新增
- Behavior: 系统执行或展示[标本新增]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 D09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D09 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SPEC-010 — 标本删除

- ID: SPEC-010
- Source: SRS V1.4 D10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 标本删除
- Behavior: 系统执行或展示[标本删除]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 D10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D10 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SPEC-011 — 标本拆分

- ID: SPEC-011
- Source: SRS V1.4 D11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 标本拆分
- Behavior: 系统执行或展示[标本拆分]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: `V2GrossingWorkbench.vue` 的标本拆分操作
- Backend Evidence: `V2RegistrationApplicationService.splitSpecimen`、`Specimen`、`JdbcV2RegistrationRepository.insertSpecimenSplit`
- DB Evidence: `V30__business_specimen_lifecycle_facts.sql` 的 `specimen_split` 与 `V36__specimen_grossing_block_closure.sql` 的创建来源约束
- Frontend Evidence: `V2GrossingWorkbench.vue`、`v2Api.ts` 提供子标本名称、部位、数量与原因输入，并展示来源血缘
- Test Evidence: `SpecimenSplitTest`、`V2MaterialDomainTest`、`fc02b-grossing.spec.ts` 验证新 SpecimenId、原标本保留及拆分血缘
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口。
- V2 Decision: 拆分创建新的 Specimen 并保存来源关系；不修改原 SpecimenId，不把拆分仅实现为数量字段变化。

### SPEC-012 — 标本异常

- ID: SPEC-012
- Source: SRS V1.4 D12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 标本异常
- Behavior: 系统执行或展示[标本异常]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 D12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D12 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SPEC-013 — 标本追踪

- ID: SPEC-013
- Source: SRS V1.4 D13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 标本追踪
- Behavior: 系统执行或展示[标本追踪]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 D13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D13 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SPEC-014 — Specimen → Slide 支持

- ID: SPEC-014
- Source: SRS V1.4 D14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Specimen → Slide 支持
- Behavior: 系统执行或展示[Specimen → Slide 支持]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 D14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md D14 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### GROSS-001 — 待取材工作队列

- ID: GROSS-001
- Source: SRS V1.4 E01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 待取材工作队列
- Behavior: 系统执行或展示[待取材工作队列]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 E01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E01 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### GROSS-002 — 扫码进入取材

- ID: GROSS-002
- Source: SRS V1.4 E02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 扫码进入取材
- Behavior: 系统执行或展示[扫码进入取材]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 E02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E02 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 GROSS-002 独立立项、实现和验收。

### GROSS-003 — 患者/标本信息

- ID: GROSS-003
- Source: SRS V1.4 E03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 患者/标本信息
- Behavior: 系统执行或展示[患者/标本信息]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 E03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E03 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### GROSS-004 — 大体描述

- ID: GROSS-004
- Source: SRS V1.4 E04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 大体描述
- Behavior: 系统执行或展示[大体描述]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 E04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E04 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### GROSS-005 — 标本尺寸

- ID: GROSS-005
- Source: SRS V1.4 E05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 标本尺寸
- Behavior: 系统执行或展示[标本尺寸]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 E05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E05 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 GROSS-005 独立立项、实现和验收。

### GROSS-006 — 大体图像

- ID: GROSS-006
- Source: SRS V1.4 E06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 大体图像
- Behavior: 系统执行或展示[大体图像]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: `V2GrossingWorkbench.vue` 的大体图像区
- Backend Evidence: `V2GrossingImageApplicationService`、`V2GrossingImageController`、`JdbcV2GrossingImageRepository`
- DB Evidence: `V29__business_grossing_images.sql` 的 `grossing_image` 及 Case/Grossing/Specimen 关联
- Frontend Evidence: `V2GrossingWorkbench.vue`、`v2MaterialApi.ts` 提供拍摄、预览、刷新和作废
- Test Evidence: `GrossImageTest`、`GrossImageAnnotationTest`、`fc02b-grossing.spec.ts` 验证捕获、持久化、刷新与软删除
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实拍摄硬件验证由 GROSS-007 单独统计。
- V2 Decision: 图像是可审计业务事实并绑定取材上下文，不以内存预览或临时文件冒充持久化。

### GROSS-007 — 拍摄台接口

- ID: GROSS-007
- Source: SRS V1.4 E07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 拍摄台接口
- Behavior: 系统执行或展示[拍摄台接口]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: `V2GrossingWorkbench.vue` 的拍摄操作
- Backend Evidence: `GrossImagingDevicePort`、`SimulatorGrossImagingDeviceAdapter`、`V2GrossingImageApplicationService`
- DB Evidence: `V29__business_grossing_images.sql` 保存成功/失败拍摄事实、设备引用与审计时间
- Frontend Evidence: `V2GrossingWorkbench.vue`、`v2MaterialApi.ts` 展示设备结果并提供明确失败反馈和重试入口
- Test Evidence: `GrossImageTest` 覆盖 Simulator 成功及设备失败不生成成功图像；`fc02b-grossing.spec.ts` 验证产品交互
- Status: EXTERNAL_DEPENDENCY
- Gap: 产品内 Port、Simulator、映射、错误语义与审计已闭环；真实医院拍摄台协议、驱动和现场联调未验证。
- V2 Decision: 不把 Simulator 冒充真实硬件验证；生产拍摄台联调作为独立外部依赖保留。

### GROSS-008 — 图像与 Case 绑定

- ID: GROSS-008
- Source: SRS V1.4 E08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 图像与 Case 绑定
- Behavior: 系统执行或展示[图像与 Case 绑定]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: `V2GrossingWorkbench.vue` 当前病例的取材图像区
- Backend Evidence: `V2GrossingImageApplicationService.capture` 校验 Grossing、Case、Specimen 同范围绑定；`JdbcV2GrossingImageRepository`
- DB Evidence: `V29__business_grossing_images.sql` 对 `case_id`、`grossing_id`、`specimen_id` 建立外键和上下文索引
- Frontend Evidence: `V2GrossingWorkbench.vue` 仅在当前 Focused Workspace 展示对应图像及标本摘要
- Test Evidence: `GrossImageTest`、`GrossingDataScopeTest`、`fc02b-grossing.spec.ts` 覆盖绑定、刷新和跨院隔离
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口。
- V2 Decision: 图像必须同时具备 Case 与 Grossing 归属；选择标本时再保存 Specimen 归属，禁止跨病例绑定。

### GROSS-009 — 图像标注

- ID: GROSS-009
- Source: SRS V1.4 E09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 图像标注
- Behavior: 系统执行或展示[图像标注]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: `V2GrossingWorkbench.vue` 图像的标注区
- Backend Evidence: `V2GrossingImageApplicationService` 的新增、修改、删除标注能力及几何数据校验
- DB Evidence: `V29__business_grossing_images.sql` 的 `grossing_image_annotation`、图像外键和软删除字段
- Frontend Evidence: `V2GrossingWorkbench.vue`、`v2MaterialApi.ts` 提供标注文本、几何信息、保存与删除
- Test Evidence: `GrossImageAnnotationTest`、`fc02b-grossing.spec.ts` 验证保存后刷新仍存在以及删除语义
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口。
- V2 Decision: 标注作为图像子事实独立保存，不覆盖原图，不解析自由文本生成测量事实。

### GROSS-010 — 尺寸测量

- ID: GROSS-010
- Source: SRS V1.4 E10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 尺寸测量
- Behavior: 系统执行或展示[尺寸测量]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: `V2GrossingWorkbench.vue` 图像的测量区
- Backend Evidence: `V2GrossingImageApplicationService.addMeasurement` 校验数值、单位和几何信息；`JdbcV2GrossingImageRepository`
- DB Evidence: `V29__business_grossing_images.sql` 的 `grossing_image_measurement`、图像外键、数值与单位字段
- Frontend Evidence: `V2GrossingWorkbench.vue`、`v2MaterialApi.ts` 提供测量值、单位、几何信息及结果列表
- Test Evidence: `GrossImageAnnotationTest`、`fc02b-grossing.spec.ts` 验证数值/单位/几何持久化与刷新
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实拍摄设备标定由 GROSS-007 外部依赖承担。
- V2 Decision: 测量保存明确值、单位和几何来源；不把没有单位的自由文本当作结构化测量。

### GROSS-011 — 多 Specimen 一次 Grossing

- ID: GROSS-011
- Source: SRS V1.4 E11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 多 Specimen 一次 Grossing
- Behavior: 系统执行或展示[多 Specimen 一次 Grossing]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 E11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E11 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### GROSS-012 — 多次 Grossing

- ID: GROSS-012
- Source: SRS V1.4 E12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 多次 Grossing
- Behavior: 系统执行或展示[多次 Grossing]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 E12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E12 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### GROSS-013 — Block 创建

- ID: GROSS-013
- Source: SRS V1.4 E13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Block 创建
- Behavior: 系统执行或展示[Block 创建]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 E13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E13 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### GROSS-014 — Block 编号

- ID: GROSS-014
- Source: SRS V1.4 E14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Block 编号
- Behavior: 系统执行或展示[Block 编号]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 E14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E14 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### GROSS-015 — Block 标签

- ID: GROSS-015
- Source: SRS V1.4 E15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Block 标签
- Behavior: 系统执行或展示[Block 标签]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 E15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E15 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### GROSS-016 — Block 打印

- ID: GROSS-016
- Source: SRS V1.4 E16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Block 打印
- Behavior: 系统执行或展示[Block 打印]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 E16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E16 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### GROSS-017 — Block 信息纠正

- ID: GROSS-017
- Source: SRS V1.4 E17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Block 信息纠正
- Behavior: 系统执行或展示[Block 信息纠正]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 E17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E17 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### GROSS-018 — 材块核对

- ID: GROSS-018
- Source: SRS V1.4 E18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 材块核对
- Behavior: 系统执行或展示[材块核对]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 E18 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E18 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E18 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E18 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E18 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### GROSS-019 — 操作人/时间

- ID: GROSS-019
- Source: SRS V1.4 E19
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 操作人/时间
- Behavior: 系统执行或展示[操作人/时间]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 E19 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E19 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E19 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E19 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E19 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### GROSS-020 — 取材完成事实

- ID: GROSS-020
- Source: SRS V1.4 E20
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 取材完成事实
- Behavior: 系统执行或展示[取材完成事实]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 E20 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E20 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E20 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E20 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md E20 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-001 — 常规制片

- ID: PROD-001
- Source: SRS V1.4 F01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 常规制片
- Behavior: 系统执行或展示[常规制片]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F01 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-002 — 细胞制片

- ID: PROD-002
- Source: SRS V1.4 F02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 细胞制片
- Behavior: 系统执行或展示[细胞制片]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F02 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-003 — 冰冻制片

- ID: PROD-003
- Source: SRS V1.4 F03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 冰冻制片
- Behavior: 系统执行或展示[冰冻制片]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F03 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-004 — TechnicalOrder 制片

- ID: PROD-004
- Source: SRS V1.4 F04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: TechnicalOrder 制片
- Behavior: 系统执行或展示[TechnicalOrder 制片]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F04 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-005 — Block → Slide

- ID: PROD-005
- Source: SRS V1.4 F05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Block → Slide
- Behavior: 系统执行或展示[Block → Slide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F05 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-006 — Specimen → Slide

- ID: PROD-006
- Source: SRS V1.4 F06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Specimen → Slide
- Behavior: 系统执行或展示[Specimen → Slide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F06 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-007 — External Slide

- ID: PROD-007
- Source: SRS V1.4 F07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: External Slide
- Behavior: 系统执行或展示[External Slide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F07 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-008 — Slide 编号

- ID: PROD-008
- Source: SRS V1.4 F08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Slide 编号
- Behavior: 系统执行或展示[Slide 编号]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F08 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-009 — Slide 标签

- ID: PROD-009
- Source: SRS V1.4 F09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Slide 标签
- Behavior: 系统执行或展示[Slide 标签]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F09 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-010 — Slide 批量打印

- ID: PROD-010
- Source: SRS V1.4 F10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Slide 批量打印
- Behavior: 系统执行或展示[Slide 批量打印]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F10 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-011 — Slide 扫码

- ID: PROD-011
- Source: SRS V1.4 F11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Slide 扫码
- Behavior: 系统执行或展示[Slide 扫码]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F11 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-012 — Slide completion

- ID: PROD-012
- Source: SRS V1.4 F12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Slide completion
- Behavior: 系统执行或展示[Slide completion]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F12 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-013 — 异常

- ID: PROD-013
- Source: SRS V1.4 F13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 异常
- Behavior: 系统执行或展示[异常]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F13 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-014 — 返工

- ID: PROD-014
- Source: SRS V1.4 F14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 返工
- Behavior: 系统执行或展示[返工]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F14 对应产品入口
- Backend Evidence: `V2MaterialReworkApplicationService`、`V2MaterialReworkController` 实现 RECUT/RESTAIN/RESCAN 的物理语义，原玻片始终保留，RECUT 自动创建统一 Slide 替代物。
- DB Evidence: `material_rework` 保存原玻片、替代玻片、原因、操作者与完成事实；`V37__routine_histology_production_closure.sql` 允许不产生新物理玻片的 RESTAIN/RESCAN 正确完成。
- Frontend Evidence: `V2RoutineProductionWorkspace.vue` 的次级“异常与物理返工”区域提供异常登记及重切、重染、重扫入口。
- Test Evidence: `TechnicalTraceAndReworkTest`、`V2MaterialProductionWebTest` 与 FC03A Playwright 覆盖原件保留、重切血缘以及重染/重扫不新增物理玻片。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；数字切片扫描设备联调仍按独立 WSI/DEVICE 外部依赖统计。
- V2 Decision: 返工是材料事实，不建立 ExceptionWorkflow；RECUT 产生新 Slide，RESTAIN/RESCAN 不默认产生新物理 Slide。

### PROD-015 — 技术人员

- ID: PROD-015
- Source: SRS V1.4 F15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 技术人员
- Behavior: 系统执行或展示[技术人员]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F15 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-016 — 时间记录

- ID: PROD-016
- Source: SRS V1.4 F16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 时间记录
- Behavior: 系统执行或展示[时间记录]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F16 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-017 — 脱水记录

- ID: PROD-017
- Source: SRS V1.4 F17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 脱水记录
- Behavior: 系统执行或展示[脱水记录]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F17 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-018 — 包埋记录

- ID: PROD-018
- Source: SRS V1.4 F18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 包埋记录
- Behavior: 系统执行或展示[包埋记录]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F18 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F18 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F18 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F18 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F18 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-019 — 切片记录

- ID: PROD-019
- Source: SRS V1.4 F19
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 切片记录
- Behavior: 系统执行或展示[切片记录]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F19 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F19 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F19 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F19 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F19 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-020 — 染色记录

- ID: PROD-020
- Source: SRS V1.4 F20
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 染色记录
- Behavior: 系统执行或展示[染色记录]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F20 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F20 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F20 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F20 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F20 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-021 — 封片记录

- ID: PROD-021
- Source: SRS V1.4 F21
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 封片记录
- Behavior: 系统执行或展示[封片记录]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F21 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F21 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F21 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F21 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F21 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-022 — 设备信息

- ID: PROD-022
- Source: SRS V1.4 F22
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 设备信息
- Behavior: 系统执行或展示[设备信息]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F22 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F22 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F22 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F22 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F22 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-023 — 开始时间

- ID: PROD-023
- Source: SRS V1.4 F23
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 开始时间
- Behavior: 系统执行或展示[开始时间]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F23 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F23 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F23 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F23 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F23 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-024 — 完成时间

- ID: PROD-024
- Source: SRS V1.4 F24
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 完成时间
- Behavior: 系统执行或展示[完成时间]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F24 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F24 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F24 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F24 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F24 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-025 — 操作人员

- ID: PROD-025
- Source: SRS V1.4 F25
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 操作人员
- Behavior: 系统执行或展示[操作人员]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F25 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F25 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F25 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F25 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F25 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-026 — 异常备注

- ID: PROD-026
- Source: SRS V1.4 F26
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 异常备注
- Behavior: 系统执行或展示[异常备注]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F26 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F26 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F26 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F26 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F26 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PROD-027 — 批量操作

- ID: PROD-027
- Source: SRS V1.4 F27
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 批量操作
- Behavior: 系统执行或展示[批量操作]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 F27 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F27 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F27 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F27 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md F27 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SLIDE-001 — Unified Block

- ID: SLIDE-001
- Source: SRS V1.4 G01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Unified Block
- Behavior: 系统执行或展示[Unified Block]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 G01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G01 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SLIDE-002 — Local Block

- ID: SLIDE-002
- Source: SRS V1.4 G02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Local Block
- Behavior: 系统执行或展示[Local Block]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 G02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G02 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SLIDE-003 — External Block

- ID: SLIDE-003
- Source: SRS V1.4 G03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: External Block
- Behavior: 系统执行或展示[External Block]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 G03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G03 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SLIDE-004 — Block → Specimen

- ID: SLIDE-004
- Source: SRS V1.4 G04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Block → Specimen
- Behavior: 系统执行或展示[Block → Specimen]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 G04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G04 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SLIDE-005 — Block → Grossing

- ID: SLIDE-005
- Source: SRS V1.4 G05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Block → Grossing
- Behavior: 系统执行或展示[Block → Grossing]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 G05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G05 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SLIDE-006 — Block correction

- ID: SLIDE-006
- Source: SRS V1.4 G06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Block correction
- Behavior: 系统执行或展示[Block correction]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 G06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G06 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SLIDE-007 — Block archive

- ID: SLIDE-007
- Source: SRS V1.4 G07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Block archive
- Behavior: 系统执行或展示[Block archive]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 G07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G07 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SLIDE-008 — Block loan

- ID: SLIDE-008
- Source: SRS V1.4 G08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Block loan
- Behavior: 系统执行或展示[Block loan]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 G08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G08 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SLIDE-009 — Unified Slide

- ID: SLIDE-009
- Source: SRS V1.4 G09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Unified Slide
- Behavior: 系统执行或展示[Unified Slide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 G09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G09 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SLIDE-010 — Block → Slide

- ID: SLIDE-010
- Source: SRS V1.4 G10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Block → Slide
- Behavior: 系统执行或展示[Block → Slide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 G10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G10 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SLIDE-011 — Specimen → Slide

- ID: SLIDE-011
- Source: SRS V1.4 G11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Specimen → Slide
- Behavior: 系统执行或展示[Specimen → Slide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 G11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G11 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SLIDE-012 — External Slide

- ID: SLIDE-012
- Source: SRS V1.4 G12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: External Slide
- Behavior: 系统执行或展示[External Slide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 G12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G12 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SLIDE-013 — Slide soft delete

- ID: SLIDE-013
- Source: SRS V1.4 G13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Slide soft delete
- Behavior: 系统执行或展示[Slide soft delete]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 G13 对应产品入口
- Backend Evidence: `V2MaterialProductionApplicationService.cancelSlide` 仅允许授权软失效，并在 DigitalSlide、技术医嘱、归档或借阅依赖存在时拒绝操作。
- DB Evidence: 统一 `slide.deleted_at/deleted_by_ref/deletion_reason` 保存失效事实；既有外键与 `V37` 历史表保留完整材料身份。
- Frontend Evidence: `V2RoutineProductionWorkspace.vue` 提供原因必填的“设为失效”次级危险操作，不提供物理删除。
- Test Evidence: `SlideLifecycleTest` 覆盖无下游依赖软失效与 DigitalSlide 依赖保护；权限与数据范围由 `RoutineProductionPermissionAndDataScopeTest` 覆盖。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口。
- V2 Decision: 误生成玻片采用审计化软失效；任何已有下游医学证据的玻片都不可物理删除。

### SLIDE-014 — Slide completion

- ID: SLIDE-014
- Source: SRS V1.4 G14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Slide completion
- Behavior: 系统执行或展示[Slide completion]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 G14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G14 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SLIDE-015 — Slide correction

- ID: SLIDE-015
- Source: SRS V1.4 G15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Slide correction
- Behavior: 系统执行或展示[Slide correction]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 G15 对应产品入口
- Backend Evidence: `correctSlideCode` 与 `correctSlideCompletion` 在保持 SlideId、CaseId 和 Block 血缘不变的前提下执行授权修正并审计。
- DB Evidence: `V37__routine_histology_production_closure.sql` 新增 `slide_code_history` 与 `slide_completion_correction`，当前有效编号仍受 Case 内活跃唯一约束保护。
- Frontend Evidence: `V2RoutineProductionWorkspace.vue` 提供编号更正与完成事实修正，均要求原因并使用后端 availableActions。
- Test Evidence: `SlideLifecycleTest` 覆盖身份不变、编号历史、完成修正历史与重复编号保护；PostgreSQL 集成测试验证唯一约束。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口。
- V2 Decision: 修正更新统一 Slide 的当前有效事实并追加历史，不通过新建平行 Slide 或覆盖历史实现。

### SLIDE-016 — Slide archive

- ID: SLIDE-016
- Source: SRS V1.4 G16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Slide archive
- Behavior: 系统执行或展示[Slide archive]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 G16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G16 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SLIDE-017 — Slide loan

- ID: SLIDE-017
- Source: SRS V1.4 G17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Slide loan
- Behavior: 系统执行或展示[Slide loan]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 G17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md G17 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### TO-001 — 医生下达技术医嘱

- ID: TO-001
- Source: SRS V1.4 H01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 医生下达技术医嘱
- Behavior: 系统执行或展示[医生下达技术医嘱]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H01 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### TO-002 — 重切

- ID: TO-002
- Source: SRS V1.4 H02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 重切
- Behavior: 系统执行或展示[重切]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H02 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 TO-002 独立立项、实现和验收。

### TO-003 — 深切

- ID: TO-003
- Source: SRS V1.4 H03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 深切
- Behavior: 系统执行或展示[深切]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H03 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 TO-003 独立立项、实现和验收。

### TO-004 — 补取

- ID: TO-004
- Source: SRS V1.4 H04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 补取
- Behavior: 系统执行或展示[补取]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H04 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 TO-004 独立立项、实现和验收。

### TO-005 — 重包埋

- ID: TO-005
- Source: SRS V1.4 H05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 重包埋
- Behavior: 系统执行或展示[重包埋]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H05 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 TO-005 独立立项、实现和验收。

### TO-006 — IHC

- ID: TO-006
- Source: SRS V1.4 H06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: IHC
- Behavior: 系统执行或展示[IHC]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H06 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 TO-006 独立立项、实现和验收。

### TO-007 — 特殊染色

- ID: TO-007
- Source: SRS V1.4 H07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 特殊染色
- Behavior: 系统执行或展示[特殊染色]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H07 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 TO-007 独立立项、实现和验收。

### TO-008 — 其他技术项目

- ID: TO-008
- Source: SRS V1.4 H08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 其他技术项目
- Behavior: 系统执行或展示[其他技术项目]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H08 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 TO-008 独立立项、实现和验收。

### TO-009 — target Case

- ID: TO-009
- Source: SRS V1.4 H09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: target Case
- Behavior: 系统执行或展示[target Case]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H09 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### TO-010 — target Specimen

- ID: TO-010
- Source: SRS V1.4 H10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: target Specimen
- Behavior: 系统执行或展示[target Specimen]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H10 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### TO-011 — target Block

- ID: TO-011
- Source: SRS V1.4 H11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: target Block
- Behavior: 系统执行或展示[target Block]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H11 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### TO-012 — target Slide

- ID: TO-012
- Source: SRS V1.4 H12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: target Slide
- Behavior: 系统执行或展示[target Slide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H12 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### TO-013 — 一个 Order 多 Item

- ID: TO-013
- Source: SRS V1.4 H13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 一个 Order 多 Item
- Behavior: 系统执行或展示[一个 Order 多 Item]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H13 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### TO-014 — 一个 Item 多 Target

- ID: TO-014
- Source: SRS V1.4 H14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 一个 Item 多 Target
- Behavior: 系统执行或展示[一个 Item 多 Target]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H14 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### TO-015 — 条码生成

- ID: TO-015
- Source: SRS V1.4 H15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 条码生成
- Behavior: 系统执行或展示[条码生成]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H15 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 TO-015 独立立项、实现和验收。

### TO-016 — 标签打印

- ID: TO-016
- Source: SRS V1.4 H16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 标签打印
- Behavior: 系统执行或展示[标签打印]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H16 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 TO-016 独立立项、实现和验收。

### TO-017 — 执行

- ID: TO-017
- Source: SRS V1.4 H17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 执行
- Behavior: 系统执行或展示[执行]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H17 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### TO-018 — 取消

- ID: TO-018
- Source: SRS V1.4 H18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 取消
- Behavior: 系统执行或展示[取消]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H18 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H18 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H18 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H18 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H18 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### TO-019 — 进度

- ID: TO-019
- Source: SRS V1.4 H19
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 进度
- Behavior: 系统执行或展示[进度]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H19 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H19 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H19 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H19 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H19 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### TO-020 — 执行人员

- ID: TO-020
- Source: SRS V1.4 H20
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 执行人员
- Behavior: 系统执行或展示[执行人员]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H20 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H20 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H20 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H20 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H20 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### TO-021 — 执行时间

- ID: TO-021
- Source: SRS V1.4 H21
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 执行时间
- Behavior: 系统执行或展示[执行时间]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H21 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H21 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H21 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H21 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H21 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### TO-022 — 结构化结果

- ID: TO-022
- Source: SRS V1.4 H22
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 结构化结果
- Behavior: 系统执行或展示[结构化结果]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H22 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H22 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H22 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H22 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H22 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### TO-023 — 染色质量评价

- ID: TO-023
- Source: SRS V1.4 H23
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 染色质量评价
- Behavior: 系统执行或展示[染色质量评价]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H23 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H23 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H23 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H23 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H23 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 TO-023 独立立项、实现和验收。

### TO-024 — DigitalSlide 关联

- ID: TO-024
- Source: SRS V1.4 H24
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: DigitalSlide 关联
- Behavior: 系统执行或展示[DigitalSlide 关联]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H24 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H24 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H24 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H24 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H24 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 TO-024 独立立项、实现和验收。

### TO-025 — 医生获得新技术结果提醒

- ID: TO-025
- Source: SRS V1.4 H25
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 医生获得新技术结果提醒
- Behavior: 系统执行或展示[医生获得新技术结果提醒]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 H25 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H25 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H25 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H25 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md H25 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### IHC-001 — IHC 项目

- ID: IHC-001
- Source: SRS V1.4 I01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: IHC 项目
- Behavior: 系统执行或展示[IHC 项目]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 I01 对应产品入口
- Backend Evidence: `V2TechnicalOrderApplicationService`、`V2TechnicalOrderController` 与 `JdbcV2TechnicalOrderRepository` 提供 IHC 项目配置、目标校验、技术产出和执行状态。
- DB Evidence: `V44__technical_order_capabilities_and_support_facts.sql` 保存 capability/output/device/consumable 配置；技术产出仍通过既有 Block/Slide 外键链保存。
- Frontend Evidence: `V2TechnicalWorkbench.vue` 与 `v2DiagnosisApi.ts` 提供 IHC 技术医嘱执行、玻片完成、质控和标签操作入口。
- Test Evidence: `V2TechnicalOrderWebTest` 覆盖 IHC 项目配置、跨病例目标拒绝、正式技术玻片和支持事实；前端工作台单测通过。
- Status: COMPLETE
- Gap: 产品内 IHC 技术项目闭环已验证；真实染色设备联调仍由 IHC-007 的外部依赖状态承担。
- V2 Decision: IHC 项目通过 capability/output 配置驱动，不新增通用 Task/Workflow 实体；设备调用只写不可变尝试事实。

### IHC-002 — Special Stain 项目

- ID: IHC-002
- Source: SRS V1.4 I02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Special Stain 项目
- Behavior: 系统执行或展示[Special Stain 项目]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 I02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I02 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 IHC-002 独立立项、实现和验收。

### IHC-003 — 批量技术医嘱

- ID: IHC-003
- Source: SRS V1.4 I03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 批量技术医嘱
- Behavior: 系统执行或展示[批量技术医嘱]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 I03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I03 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### IHC-004 — 包埋盒扫码

- ID: IHC-004
- Source: SRS V1.4 I04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 包埋盒扫码
- Behavior: 系统执行或展示[包埋盒扫码]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 I04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I04 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 IHC-004 独立立项、实现和验收。

### IHC-005 — Slide 生成

- ID: IHC-005
- Source: SRS V1.4 I05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Slide 生成
- Behavior: 系统执行或展示[Slide 生成]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 I05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I05 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### IHC-006 — Slide 标签

- ID: IHC-006
- Source: SRS V1.4 I06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Slide 标签
- Behavior: 系统执行或展示[Slide 标签]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 I06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I06 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### IHC-007 — IHC设备接口

- ID: IHC-007
- Source: SRS V1.4 I07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: IHC设备接口
- Behavior: 系统执行或展示[IHC设备接口]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 I07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I07 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 IHC-007 独立立项、实现和验收。

### IHC-008 — 批量下发设备

- ID: IHC-008
- Source: SRS V1.4 I08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 批量下发设备
- Behavior: 系统执行或展示[批量下发设备]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 I08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I08 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 IHC-008 独立立项、实现和验收。

### IHC-009 — 执行结果

- ID: IHC-009
- Source: SRS V1.4 I09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 执行结果
- Behavior: 系统执行或展示[执行结果]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 I09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I09 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### IHC-010 — 染色质量评价

- ID: IHC-010
- Source: SRS V1.4 I10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 染色质量评价
- Behavior: 系统执行或展示[染色质量评价]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 I10 对应产品入口
- Backend Evidence: `POST /api/v2/technical-order-items/{itemId}/quality` 经 `V2TechnicalOrderApplicationService.evaluateQuality` 写入独立质量评价事实并审计。
- DB Evidence: `technical_order_quality_evaluation` 保存 item/output、结果、评分、说明和评价人，不覆盖原始技术产出。
- Frontend Evidence: `V2TechnicalWorkbench.vue` 在每个蜡块/玻片产物旁提供“质控通过”入口。
- Test Evidence: `V2TechnicalOrderWebTest.technicalSupportFactsKeepDeviceQualityFeeConsumptionAndLabelHistorySeparate` 覆盖 PASS 评价和落库。
- Status: COMPLETE
- Gap: 产品内质量评价闭环已验证；完整质控表字段和统计口径属于 IHC-011/IHC-013，未在本原子项中冒充完成。
- V2 Decision: 质量评价是独立追加事实，不能修改或删除原始技术产出。

### IHC-011 — 质控表

- ID: IHC-011
- Source: SRS V1.4 I11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 质控表
- Behavior: 系统执行或展示[质控表]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 I11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I11 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 IHC-011 独立立项、实现和验收。

### IHC-012 — 项目明细

- ID: IHC-012
- Source: SRS V1.4 I12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 项目明细
- Behavior: 系统执行或展示[项目明细]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 I12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I12 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### IHC-013 — 工作量统计

- ID: IHC-013
- Source: SRS V1.4 I13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 工作量统计
- Behavior: 系统执行或展示[工作量统计]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 I13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I13 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 IHC-013 独立立项、实现和验收。

### IHC-014 — 项目分类统计

- ID: IHC-014
- Source: SRS V1.4 I14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 项目分类统计
- Behavior: 系统执行或展示[项目分类统计]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 I14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I14 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 IHC-014 独立立项、实现和验收。

### IHC-015 — DigitalSlide

- ID: IHC-015
- Source: SRS V1.4 I15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: DigitalSlide
- Behavior: 系统执行或展示[DigitalSlide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 I15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I15 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### IHC-016 — 试剂消耗关联

- ID: IHC-016
- Source: SRS V1.4 I16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 试剂消耗关联
- Behavior: 系统执行或展示[试剂消耗关联]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 I16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md I16 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 IHC-016 独立立项、实现和验收。

### IHC-017 — 费用状态记录

- ID: IHC-017
- Source: SRS V1.4 I17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 费用状态记录
- Behavior: 系统执行或展示[费用状态记录]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 I17 对应产品入口
- Backend Evidence: `POST /api/v2/technical-order-items/{itemId}/fee-status` 经 `V2TechnicalOrderApplicationService.updateFeeStatus` 记录费用侧通道状态。
- DB Evidence: `technical_order_fee_status` 对每个技术项目保存当前状态、外部引用和失败原因；费用状态不参与核心产出完成判定。
- Frontend Evidence: `V2TechnicalWorkbench.vue` 提供“记录费用已登记”入口，并明确费用失败不阻断技术产出。
- Test Evidence: `V2TechnicalOrderWebTest.technicalSupportFactsKeepDeviceQualityFeeConsumptionAndLabelHistorySeparate` 覆盖 FAILED 状态落库且技术医嘱仍可继续。
- Status: COMPLETE
- Gap: 产品内费用状态侧通道已闭环；真实计费系统对账与外部回执仍属于接口外部依赖。
- V2 Decision: 费用是 side-channel，不得反向阻塞核心病理产物或替代技术医嘱状态。

### CYTO-001 — 独立 BusinessType

- ID: CYTO-001
- Source: SRS V1.4 J01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 独立 BusinessType
- Behavior: 系统执行或展示[独立 BusinessType]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 J01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J01 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CYTO-002 — 细胞标本类型

- ID: CYTO-002
- Source: SRS V1.4 J02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 细胞标本类型
- Behavior: 系统执行或展示[细胞标本类型]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 J02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J02 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 真实细胞采集设备与院内字典同步仍属于外部集成/配置运营验证；本轮不把硬件联调冒充为产品完成。
- V2 Decision: 复用统一 Specimen 的 `specimen_kind_code`、采集信息和现有 ApplicationItemMapping，不创建 CytologySpecimen 或固定枚举；细胞生产工作区按该事实显示标本类型。

### CYTO-003 — 标本处理方法

- ID: CYTO-003
- Source: SRS V1.4 J03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 标本处理方法
- Behavior: 系统执行或展示[标本处理方法]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 J03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J03 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 真实液基/离心/染色设备适配仍属于独立外部依赖；产品内制片方式记录和更正已闭环。
- V2 Decision: V38 增加可空 `specimen.preparation_method_code`，通过后端版本校验 API 和细胞制片工作区保存；不创建新的 Specimen，也不把制片方式变成流程状态机。

### CYTO-004 — 登记

- ID: CYTO-004
- Source: SRS V1.4 J04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 登记
- Behavior: 系统执行或展示[登记]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 J04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J04 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CYTO-005 — Specimen → Slide

- ID: CYTO-005
- Source: SRS V1.4 J05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Specimen → Slide
- Behavior: 系统执行或展示[Specimen → Slide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 J05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J05 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CYTO-006 — 细胞制片

- ID: CYTO-006
- Source: SRS V1.4 J06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 细胞制片
- Behavior: 系统执行或展示[细胞制片]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 J06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J06 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CYTO-007 — 染色封片记录

- ID: CYTO-007
- Source: SRS V1.4 J07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 染色封片记录
- Behavior: 系统执行或展示[染色封片记录]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 J07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J07 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CYTO-008 — DigitalSlide

- ID: CYTO-008
- Source: SRS V1.4 J08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: DigitalSlide
- Behavior: 系统执行或展示[DigitalSlide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 J08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J08 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CYTO-009 — 细胞诊断

- ID: CYTO-009
- Source: SRS V1.4 J09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 细胞诊断
- Behavior: 系统执行或展示[细胞诊断]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 J09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J09 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CYTO-010 — TBS 等结构化模板

- ID: CYTO-010
- Source: SRS V1.4 J10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: TBS 等结构化模板
- Behavior: 系统执行或展示[TBS 等结构化模板]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 J10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J10 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CYTO-010 独立立项、实现和验收。

### CYTO-011 — 细胞报告

- ID: CYTO-011
- Source: SRS V1.4 J11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 细胞报告
- Behavior: 系统执行或展示[细胞报告]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 J11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md J11 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CYTO-011 独立立项、实现和验收。

### FROZEN-001 — Frozen Case

- ID: FROZEN-001
- Source: SRS V1.4 K01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Frozen Case
- Behavior: 系统执行或展示[Frozen Case]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 K01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K01 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### FROZEN-002 — 独立 Frozen PathologyNo

- ID: FROZEN-002
- Source: SRS V1.4 K02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 独立 Frozen PathologyNo
- Behavior: 系统执行或展示[独立 Frozen PathologyNo]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 K02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K02 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### FROZEN-003 — FrozenRound

- ID: FROZEN-003
- Source: SRS V1.4 K03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: FrozenRound
- Behavior: 系统执行或展示[FrozenRound]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 K03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K03 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### FROZEN-004 — 一个 Frozen Case 多 Round

- ID: FROZEN-004
- Source: SRS V1.4 K04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 一个 Frozen Case 多 Round
- Behavior: 系统执行或展示[一个 Frozen Case 多 Round]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 K04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K04 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### FROZEN-005 — 一个 Round 多 Specimen

- ID: FROZEN-005
- Source: SRS V1.4 K05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 一个 Round 多 Specimen
- Behavior: 系统执行或展示[一个 Round 多 Specimen]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 K05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K05 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### FROZEN-006 — Frozen Slide

- ID: FROZEN-006
- Source: SRS V1.4 K06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Frozen Slide
- Behavior: 系统执行或展示[Frozen Slide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 K06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K06 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### FROZEN-007 — Frozen DigitalSlide

- ID: FROZEN-007
- Source: SRS V1.4 K07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Frozen DigitalSlide
- Behavior: 系统执行或展示[Frozen DigitalSlide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 K07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K07 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### FROZEN-008 — Frozen Diagnosis

- ID: FROZEN-008
- Source: SRS V1.4 K08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Frozen Diagnosis
- Behavior: 系统执行或展示[Frozen Diagnosis]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 K08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K08 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### FROZEN-009 — Frozen Report

- ID: FROZEN-009
- Source: SRS V1.4 K09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Frozen Report
- Behavior: 系统执行或展示[Frozen Report]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 K09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K09 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### FROZEN-010 — Frozen TAT

- ID: FROZEN-010
- Source: SRS V1.4 K10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Frozen TAT
- Behavior: 系统执行或展示[Frozen TAT]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 K10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K10 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### FROZEN-011 — 超时提醒

- ID: FROZEN-011
- Source: SRS V1.4 K11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 超时提醒
- Behavior: 系统执行或展示[超时提醒]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 K11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K11 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据覆盖提醒阈值投影、工作区可见状态、确认已知悉操作、刷新保持和不可变处置事实；真实运营平台联动不属于本原子能力。
- V2 Decision: FC03C1 使用 FrozenRound TAT policy 和独立 `frozen_tat_alert_action` 事实完成超时提醒与确认闭环，不以通知中心或通用 WorkItem 替代业务事实。

### FROZEN-012 — 术中报告发送

- ID: FROZEN-012
- Source: SRS V1.4 K12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 术中报告发送
- Behavior: 系统执行或展示[术中报告发送]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 K12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K12 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 FROZEN-012 独立立项、实现和验收。

### FROZEN-013 — Frozen End

- ID: FROZEN-013
- Source: SRS V1.4 K13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Frozen End
- Behavior: 系统执行或展示[Frozen End]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 K13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K13 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### FROZEN-014 — 自动创建 Routine Case

- ID: FROZEN-014
- Source: SRS V1.4 K14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 自动创建 Routine Case
- Behavior: 系统执行或展示[自动创建 Routine Case]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 K14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K14 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### FROZEN-015 — Routine Case 新病理号

- ID: FROZEN-015
- Source: SRS V1.4 K15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Routine Case 新病理号
- Behavior: 系统执行或展示[Routine Case 新病理号]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 K15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K15 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### FROZEN-016 — routine_case.frozen_source_case_id

- ID: FROZEN-016
- Source: SRS V1.4 K16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: routine_case.frozen_source_case_id
- Behavior: 系统执行或展示[routine_case.frozen_source_case_id]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 K16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K16 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### FROZEN-017 — 冰冻/石蜡诊断对照

- ID: FROZEN-017
- Source: SRS V1.4 K17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 冰冻/石蜡诊断对照
- Behavior: 系统执行或展示[冰冻/石蜡诊断对照]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 K17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md K17 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据覆盖 Frozen 各轮正式诊断、Routine 最终有效报告、Routine 未诊断和撤回语义、双向入口、权限/数据范围及人工查看；本能力不包含自动医学一致性判定或人工评价枚举。
- V2 Decision: FC03C1 通过 `frozen_source_case_id` 查询关系并由单一 comparison query 返回事实；不创建 Generic CaseRelation，不使用字符串比较或模型自动判定医学一致性。

### DX-001 — 医生工作台

- ID: DX-001
- Source: SRS V1.4 L01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 医生工作台
- Behavior: 系统执行或展示[医生工作台]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L01 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-002 — 待接诊

- ID: DX-002
- Source: SRS V1.4 L02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 待接诊
- Behavior: 系统执行或展示[待接诊]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L02 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-003 — 待初诊

- ID: DX-003
- Source: SRS V1.4 L03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 待初诊
- Behavior: 系统执行或展示[待初诊]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L03 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-004 — 待复诊

- ID: DX-004
- Source: SRS V1.4 L04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 待复诊
- Behavior: 系统执行或展示[待复诊]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L04 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-005 — 待审核

- ID: DX-005
- Source: SRS V1.4 L05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 待审核
- Behavior: 系统执行或展示[待审核]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L05 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-006 — 新技术结果

- ID: DX-006
- Source: SRS V1.4 L06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 新技术结果
- Behavior: 系统执行或展示[新技术结果]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L06 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-007 — 撤回待处理

- ID: DX-007
- Source: SRS V1.4 L07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 撤回待处理
- Behavior: 系统执行或展示[撤回待处理]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L07 对应产品入口
- Backend Evidence: `V2ReportApplicationService.withdraw` 撤回不可变报告并重开最后一个审核责任；`V2WorkbenchApplicationService` 与 `JdbcV2WorkbenchRepository.findWithdrawnReports` 投影独立撤回待处理队列。
- DB Evidence: `report` 保存撤回人、时间、原因和原 PDF 哈希；`responsibility_unit` 追加重开的审核责任事实，原签审记录不覆盖。
- Frontend Evidence: `V2DiagnosisWorkspace.vue` 在历史报告面板提供撤回入口并展示撤回状态；工作台能力队列提供“撤回待处理”入口。
- Test Evidence: `V2ReportWebTest.previewDoesNotPersistAndSignOutWithdrawResignAndSupplementPreserveHistory` 覆盖撤回、审核责任重开、工作台待处理投影、重新签发和完整版本历史。
- Status: COMPLETE
- Gap: 无当前已知产品内闭环缺口；撤回不删除或覆盖任何已签发报告。
- V2 Decision: “撤回待处理”是 Report 与 ResponsibilityUnit 事实投影，不创建 Generic Task/Workflow。

### DX-008 — 主动接诊

- ID: DX-008
- Source: SRS V1.4 L08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 主动接诊
- Behavior: 系统执行或展示[主动接诊]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L08 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-009 — 自动分诊

- ID: DX-009
- Source: SRS V1.4 L09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 自动分诊
- Behavior: 系统执行或展示[自动分诊]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: `V2DiagnosisWorkspace.vue` 公共病例池和病例工作区提供自动分诊动作；`V2ConfigurationHub.vue` 提供规则维护入口。
- Backend Evidence: `V2DiagnosisApplicationService.autoAssignDiagnosis` 以 Case、路由事实和启用规则创建真实 INITIAL `ResponsibilityUnit`；`V2DiagnosisController` 提供自动分诊及规则 API。
- DB Evidence: `assignment_rule` 与 `V45__diagnosis_auto_assignment_capacity.sql`；命中规则、路由维度和容量快照写入不可变 `diagnosis_auto_assignment_fact`。
- Frontend Evidence: `V2DiagnosisWorkspace.vue`、`V2ConfigurationHub.vue`、`v2DiagnosisApi.ts`。
- Test Evidence: `V2DiagnosisWebTest.autoAssignmentUsesSubspecialtyRulesAndEnforcesDailyDoctorCapacity`、`V2DiagnosisWorkspace.test.ts`、`V2ConfigurationHub.test.ts`。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；规则未匹配和容量耗尽均返回明确错误，不宣称医院人员主数据质量已验证。
- V2 Decision: 自动分诊只创建诊断责任事实，不引入 Generic Task/Workflow；选择过程按维度精确度、规则优先级、当前负载和当日量确定。

### DX-010 — 手工指派

- ID: DX-010
- Source: SRS V1.4 L10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 手工指派
- Behavior: 系统执行或展示[手工指派]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L10 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-011 — 转交

- ID: DX-011
- Source: SRS V1.4 L11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 转交
- Behavior: 系统执行或展示[转交]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L11 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-012 — 亚专科

- ID: DX-012
- Source: SRS V1.4 L12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 亚专科
- Behavior: 系统执行或展示[亚专科]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: `V2ConfigurationHub.vue` 的“自动分诊规则”可配置并维护亚专科组、医生和病例匹配维度。
- Backend Evidence: `AssignmentRule.diagnosisGroup` 参与候选规则和责任医生选择，命中组随自动分诊结果返回。
- DB Evidence: `assignment_rule.diagnosis_group_code`；`diagnosis_auto_assignment_fact.diagnosis_group_code` 固化当次亚专科归属。
- Frontend Evidence: 配置中心显示亚专科组，诊断工作区反馈命中的亚专科和责任医生。
- Test Evidence: `V2DiagnosisWebTest.autoAssignmentUsesSubspecialtyRulesAndEnforcesDailyDoctorCapacity` 断言 GI 组路由与事实保存；配置组件测试覆盖创建入口。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；具体亚专科字典与医生归属由医院配置负责。
- V2 Decision: 亚专科是诊断分派维度和不可变分派快照，不复制 Diagnosis 或 Case 实体。

### DX-013 — 每日最大接诊量

- ID: DX-013
- Source: SRS V1.4 L13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 每日最大接诊量
- Behavior: 系统执行或展示[每日最大接诊量]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: `V2ConfigurationHub.vue` 支持为每条医生分诊规则设置每日上限，0 明确表示不限量。
- Backend Evidence: 自动分诊在锁定当前医院启用规则后统计医生当日全部 INITIAL 接诊病例；达到上限的候选被排除，全部耗尽返回 `V2-DIAGNOSIS-DAILY-LIMIT-REACHED`。
- DB Evidence: `assignment_rule.daily_case_limit` 非负约束；自动分诊事实保存分派前计数和上限快照。
- Frontend Evidence: 配置中心新增/编辑表单提供非负每日上限；自动分诊结果返回本次分派后的当日数量和上限。
- Test Evidence: `V2DiagnosisWebTest.autoAssignmentUsesSubspecialtyRulesAndEnforcesDailyDoctorCapacity` 验证两位医生各上限 1、顺序分派、幂等重放不重复计数和第三例容量拒绝。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；自然日按数据库当前日期计算，跨时区医院策略尚未独立配置。
- V2 Decision: 容量只约束自动分诊，不阻断有权限人员的显式手工指派；规则行锁用于并发容量判定。

### DX-014 — 镜下所见

- ID: DX-014
- Source: SRS V1.4 L14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 镜下所见
- Behavior: 系统执行或展示[镜下所见]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L14 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-015 — 病理诊断

- ID: DX-015
- Source: SRS V1.4 L15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 病理诊断
- Behavior: 系统执行或展示[病理诊断]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L15 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-016 — 备注

- ID: DX-016
- Source: SRS V1.4 L16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 备注
- Behavior: 系统执行或展示[备注]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L16 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-017 — Structured Diagnosis

- ID: DX-017
- Source: SRS V1.4 L17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Structured Diagnosis
- Behavior: 系统执行或展示[Structured Diagnosis]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L17 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-018 — Free Text

- ID: DX-018
- Source: SRS V1.4 L18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Free Text
- Behavior: 系统执行或展示[Free Text]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L18 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L18 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L18 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L18 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L18 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-019 — DiagnosisTemplate

- ID: DX-019
- Source: SRS V1.4 L19
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: DiagnosisTemplate
- Behavior: 系统执行或展示[DiagnosisTemplate]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L19 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L19 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L19 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L19 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L19 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-020 — 医嘱下达

- ID: DX-020
- Source: SRS V1.4 L20
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 医嘱下达
- Behavior: 系统执行或展示[医嘱下达]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L20 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L20 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L20 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L20 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L20 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-021 — 诊断时限

- ID: DX-021
- Source: SRS V1.4 L21
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 诊断时限
- Behavior: 系统执行或展示[诊断时限]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L21 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L21 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L21 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L21 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L21 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-022 — 保存

- ID: DX-022
- Source: SRS V1.4 L22
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 保存
- Behavior: 系统执行或展示[保存]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L22 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L22 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L22 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L22 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L22 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-023 — 初诊

- ID: DX-023
- Source: SRS V1.4 L23
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 初诊
- Behavior: 系统执行或展示[初诊]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L23 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L23 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L23 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L23 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L23 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-024 — 复诊

- ID: DX-024
- Source: SRS V1.4 L24
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 复诊
- Behavior: 系统执行或展示[复诊]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L24 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L24 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L24 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L24 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L24 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-025 — 审核

- ID: DX-025
- Source: SRS V1.4 L25
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 审核
- Behavior: 系统执行或展示[审核]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L25 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L25 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L25 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L25 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L25 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-026 — 签审记录

- ID: DX-026
- Source: SRS V1.4 L26
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 签审记录
- Behavior: 系统执行或展示[签审记录]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L26 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L26 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L26 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L26 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md L26 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### DX-027 — 科内会诊记录

- ID: DX-027
- Source: SRS V1.4 L27
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 科内会诊记录
- Behavior: 系统执行或展示[科内会诊记录]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L27 对应产品入口
- Backend Evidence: `V2CaseSupportApplicationService` 与 `V2CaseSupportController` 保存和查询科内会诊；创建命令具备权限、病例数据范围、幂等摘要冲突和审计校验。
- DB Evidence: `V33__business_case_support_facts.sql` 的 `case_consultation` 保存发起人、参与人、原因、讨论、结论、备注、附件引用和记录人；幂等结果复用 `diagnosis_command_idempotency`。
- Frontend Evidence: `V2DiagnosisWorkspace.vue` 的“会诊与随访”面板提供参与医生、原因、讨论、结论和备注录入及历史展示。
- Test Evidence: `V2DiagnosisWebTest.caseSupportCommandsAreScopedIdempotentAndKeepCompletionHistory` 与 `V2DiagnosisWorkspace.test.ts` 覆盖会诊创建、重放、范围拒绝和工作区入口。
- Status: COMPLETE
- Gap: 无当前已知产品内闭环缺口；附件只保存受控引用，文件存储和访问继续遵循统一附件权限。
- V2 Decision: 科内会诊是病例下追加业务事实，不替代初诊/复诊/审核责任链。

### DX-028 — 病例收藏

- ID: DX-028
- Source: SRS V1.4 L28
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 病例收藏
- Behavior: 系统执行或展示[病例收藏]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L28 对应产品入口
- Backend Evidence: `V2CaseSupportApplicationService.favorite/unfavorite/favoriteState/favorites` 提供用户级收藏，强制权限、病例范围和审计。
- DB Evidence: `case_favorite` 以病例、用户和组织范围组成唯一键，重复收藏自然幂等且不会改变病例医疗状态。
- Frontend Evidence: `V2DiagnosisWorkspace.vue` 顶部主操作区提供“收藏病例/取消收藏”并即时反馈状态。
- Test Evidence: `V2DiagnosisWebTest.caseSupportCommandsAreScopedIdempotentAndKeepCompletionHistory`、`V2GateCWebTest` 和 `V2DiagnosisWorkspace.test.ts` 覆盖收藏、状态读取、范围拒绝和 UI 动作。
- Status: COMPLETE
- Gap: 无当前已知产品内闭环缺口。
- V2 Decision: 收藏是用户偏好事实，不进入病例生命周期和责任状态机。

### DX-029 — 随访

- ID: DX-029
- Source: SRS V1.4 L29
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 随访
- Behavior: 系统执行或展示[随访]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 L29 对应产品入口
- Backend Evidence: `V2CaseSupportApplicationService` 与 `V2CaseSupportController` 提供随访计划创建、查询和一次性完成；写命令具备权限、病例范围、幂等摘要冲突及审计。
- DB Evidence: `case_follow_up` 分离计划日期、计划、随访内容、结果、操作人和完成时间，完成后不允许覆盖；幂等结果复用 `diagnosis_command_idempotency`。
- Frontend Evidence: `V2DiagnosisWorkspace.vue` 的“会诊与随访”面板支持新增计划、查看待随访项、填写内容/结果并完成。
- Test Evidence: `V2DiagnosisWebTest.caseSupportCommandsAreScopedIdempotentAndKeepCompletionHistory` 与 `V2DiagnosisWorkspace.test.ts` 覆盖创建/完成重放、不可重复完成、范围拒绝和 UI 请求。
- Status: COMPLETE
- Gap: 无当前已知产品内闭环缺口；跨系统自动随访消息不在本原子项中冒充完成。
- V2 Decision: 随访使用独立病例事实，不修改报告版本或诊断责任链。

### WSI-001 — DigitalSlide

- ID: WSI-001
- Source: SRS V1.4 M01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: DigitalSlide
- Behavior: 系统执行或展示[DigitalSlide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 M01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M01 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### WSI-002 — DigitalSlide → Case

- ID: WSI-002
- Source: SRS V1.4 M02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: DigitalSlide → Case
- Behavior: 系统执行或展示[DigitalSlide → Case]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 M02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M02 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### WSI-003 — optional Block

- ID: WSI-003
- Source: SRS V1.4 M03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: optional Block
- Behavior: 系统执行或展示[optional Block]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 M03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M03 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### WSI-004 — optional Slide

- ID: WSI-004
- Source: SRS V1.4 M04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: optional Slide
- Behavior: 系统执行或展示[optional Slide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 M04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M04 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### WSI-005 — 一个 Slide 多 DigitalSlide

- ID: WSI-005
- Source: SRS V1.4 M05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 一个 Slide 多 DigitalSlide
- Behavior: 系统执行或展示[一个 Slide 多 DigitalSlide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 M05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M05 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### WSI-006 — manual bind

- ID: WSI-006
- Source: SRS V1.4 M06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: manual bind
- Behavior: 系统执行或展示[manual bind]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 M06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M06 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### WSI-007 — rebind

- ID: WSI-007
- Source: SRS V1.4 M07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: rebind
- Behavior: 系统执行或展示[rebind]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 M07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M07 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### WSI-008 — bind audit

- ID: WSI-008
- Source: SRS V1.4 M08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: bind audit
- Behavior: 系统执行或展示[bind audit]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 M08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M08 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### WSI-009 — WSI open

- ID: WSI-009
- Source: SRS V1.4 M09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: WSI open
- Behavior: 系统执行或展示[WSI open]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 M09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M09 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### WSI-010 — zoom

- ID: WSI-010
- Source: SRS V1.4 M10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: zoom
- Behavior: 系统执行或展示[zoom]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 M10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M10 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### WSI-011 — pan

- ID: WSI-011
- Source: SRS V1.4 M11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: pan
- Behavior: 系统执行或展示[pan]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 M11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M11 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### WSI-012 — fullscreen

- ID: WSI-012
- Source: SRS V1.4 M12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: fullscreen
- Behavior: 系统执行或展示[fullscreen]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 M12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M12 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### WSI-013 — minimap

- ID: WSI-013
- Source: SRS V1.4 M13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: minimap
- Behavior: 系统执行或展示[minimap]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 M13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M13 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### WSI-014 — multiple slide switch

- ID: WSI-014
- Source: SRS V1.4 M14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: multiple slide switch
- Behavior: 系统执行或展示[multiple slide switch]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 M14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M14 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### WSI-015 — annotation

- ID: WSI-015
- Source: SRS V1.4 M15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: annotation
- Behavior: 系统执行或展示[annotation]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: 诊断工作区阅片主区域的“在图像上标注”；先填说明，再在真实 Viewer 视口点选。
- Backend Evidence: `V2DigitalSlideApplicationService.annotate` 校验权限、组织范围、几何数据和幂等摘要，保存后写审计。
- DB Evidence: `digital_slide_annotation` 保存坐标系、归一化坐标、视口、说明与创建人；`V46__digital_slide_review_closure.sql` 提供并发幂等记录。
- Frontend Evidence: `V2ImageViewer.vue` 从实际鼠标位置计算坐标；普通图像按渲染图像边界归一化并拒绝留白点击，分层 WSI 保存归一化视口坐标及当时视口状态；`V2DiagnosisWorkspace.vue` 保存并回显标注历史。
- Test Evidence: `V2GateCWebTest` 验证保存、幂等重放、查询和跨医院拒绝；`V2ImageViewer.test.ts` 验证实际点选坐标。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；坐标系随记录显式保存，不冒充扫描仪物理标定值。
- V2 Decision: 标注是 DigitalSlide 的独立审阅事实，不修改源 WSI 或 Slide。

### WSI-016 — measurement

- ID: WSI-016
- Source: SRS V1.4 M16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: measurement
- Behavior: 系统执行或展示[measurement]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: 诊断工作区阅片主区域的“在图像上测量”，依次选择起点和终点。
- Backend Evidence: `V2DigitalSlideApplicationService.measure` 校验非负值、权限、范围和幂等，写独立测量事实与审计。
- DB Evidence: `digital_slide_measurement` 保存两点归一化几何、比例值、单位和测量模式。
- Frontend Evidence: `V2ImageViewer.vue` 依据两次真实点选计算同一坐标系内的比例距离；历史区以百分比业务语言回显。
- Test Evidence: `V2GateCWebTest` 验证持久化查询；`V2ImageViewer.test.ts` 断言两点坐标和 0.7 比例距离。
- Status: COMPLETE
- Gap: 无扫描仪物理标定时只显示 `IMAGE_RATIO` 或 `VIEWPORT_RATIO`，不伪造毫米或微米结果；真实物理单位由具备校准元数据的 Viewer Adapter 提供。
- V2 Decision: 测量结果记录坐标系和单位，未校准图像不得冒充物理尺寸。

### WSI-017 — screenshot

- ID: WSI-017
- Source: SRS V1.4 M17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: screenshot
- Behavior: 系统执行或展示[screenshot]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: 诊断工作区阅片主区域“保存当前截图”，历史区提供受控截图查看链接。
- Backend Evidence: Viewer Adapter 导出当前 PNG；`V2DigitalSlideApplicationService.screenshot` 校验 PNG 签名、10MB 上限、幂等和范围，内容读取再次授权。
- DB Evidence: `V46__digital_slide_review_closure.sql` 为截图增加媒体类型、SHA-256 和二进制证据，并保持 V32 旧引用行兼容。
- Frontend Evidence: Regular Image 与 OpenSeadragon Adapter 均实现当前视野捕获；工作区保存实际图像和视口参数，不再生成 `browser://` 伪引用。
- Test Evidence: `V2GateCWebTest` 验证 PNG 内容往返、hash 元数据、幂等重放、列表和跨医院读取拒绝；前端类型与组件测试覆盖 Adapter 契约。
- Status: COMPLETE
- Gap: 外部厂商 Viewer 若不开放截图 API 会明确返回不可导出，不伪造截图成功。
- V2 Decision: 截图是受控 DigitalSlide 审阅证据；保存真实内容、摘要和视口，不仅保存客户端临时 URL。

### WSI-018 — slide metadata

- ID: WSI-018
- Source: SRS V1.4 M18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: slide metadata
- Behavior: 系统执行或展示[slide metadata]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 M18 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M18 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M18 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M18 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M18 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### WSI-019 — viewer link

- ID: WSI-019
- Source: SRS V1.4 M19
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: viewer link
- Behavior: 系统执行或展示[viewer link]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 M19 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M19 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M19 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M19 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M19 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### WSI-020 — no-WSI stable state

- ID: WSI-020
- Source: SRS V1.4 M20
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: no-WSI stable state
- Behavior: 系统执行或展示[no-WSI stable state]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 M20 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M20 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M20 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M20 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M20 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### WSI-021 — remote viewing

- ID: WSI-021
- Source: SRS V1.4 M21
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: remote viewing
- Behavior: 系统执行或展示[remote viewing]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 M21 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M21 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M21 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M21 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md M21 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 WSI-021 独立立项、实现和验收。

### RPT-001 — DiagnosisTemplate

- ID: RPT-001
- Source: SRS V1.4 N01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: DiagnosisTemplate
- Behavior: 系统执行或展示[DiagnosisTemplate]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N01 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### RPT-002 — ReportTemplate

- ID: RPT-002
- Source: SRS V1.4 N02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: ReportTemplate
- Behavior: 系统执行或展示[ReportTemplate]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N02 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### RPT-003 — Template Designer

- ID: RPT-003
- Source: SRS V1.4 N03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Template Designer
- Behavior: 系统执行或展示[Template Designer]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N03 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 无。配置中心提供结构化报告标题、类别、A4页码、版块顺序、数据来源和字段设计，不要求管理员直接编辑JSON。
- V2 Decision: 保存总是追加DRAFT版本，发布后版本不可修改；服务端校验schemaVersion、版块唯一性、来源及字段，诊断预览可选择已发布版本并把定义固化进报告快照。

### RPT-004 — Structured elements

- ID: RPT-004
- Source: SRS V1.4 N04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Structured elements
- Behavior: 系统执行或展示[Structured elements]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N04 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### RPT-005 — single-select

- ID: RPT-005
- Source: SRS V1.4 N05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: single-select
- Behavior: 系统执行或展示[single-select]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N05 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### RPT-006 — multi-select

- ID: RPT-006
- Source: SRS V1.4 N06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: multi-select
- Behavior: 系统执行或展示[multi-select]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N06 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### RPT-007 — input

- ID: RPT-007
- Source: SRS V1.4 N07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: input
- Behavior: 系统执行或展示[input]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N07 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### RPT-008 — free text

- ID: RPT-008
- Source: SRS V1.4 N08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: free text
- Behavior: 系统执行或展示[free text]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N08 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### RPT-009 — 常用肿瘤模板

- ID: RPT-009
- Source: SRS V1.4 N09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 常用肿瘤模板
- Behavior: 系统执行或展示[常用肿瘤模板]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N09 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 无。内置肺、乳腺和结直肠三类版本化肿瘤报告结构，可复制为当前医院、指定业务类型的草稿后再设计和发布。
- V2 Decision: 内置内容只定义通用信息、材料、镜下、诊断、辅助检查和签发版块，不预设医学结论或诊断规则；医院草稿必须经本地业务审核后发布，来源预置代码保留追溯。

### RPT-010 — report preview

- ID: RPT-010
- Source: SRS V1.4 N10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: report preview
- Behavior: 系统执行或展示[report preview]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N10 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### RPT-011 — pagination

- ID: RPT-011
- Source: SRS V1.4 N11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: pagination
- Behavior: 系统执行或展示[pagination]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N11 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 无。正式 PDF 按完整 Unicode 正文自动换行和分页，页眉显示报告号、内容摘要和页码，不再截断长报告。
- V2 Decision: 采用 PDFBox 分页渲染并由 `V2ReportPdfRendererTest` 验证长正文跨页及完整字符计数；签发、预览和补充报告统一使用该输出边界。

### RPT-012 — PDF

- ID: RPT-012
- Source: SRS V1.4 N12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: PDF
- Behavior: 系统执行或展示[PDF]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N12 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### RPT-013 — PDF encryption

- ID: RPT-013
- Source: SRS V1.4 N13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: PDF encryption
- Behavior: 系统执行或展示[PDF encryption]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N13 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 无。正式 PDF 默认执行 AES-256 权限保护；生效报告可在诊断工作区按用途生成一次性口令加密副本。
- V2 Decision: 口令只用于本次生成，不落库、不写日志和审计；审计仅记录报告、操作者和下载用途。撤回报告禁止生成新的对外加密副本，原签发 PDF 仍保持不可变。

### RPT-014 — CA signature

- ID: RPT-014
- Source: SRS V1.4 N14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: CA signature
- Behavior: 系统执行或展示[CA signature]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N14 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 RPT-014 独立立项、实现和验收。

### RPT-015 — sign-out

- ID: RPT-015
- Source: SRS V1.4 N15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: sign-out
- Behavior: 系统执行或展示[sign-out]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N15 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### RPT-016 — withdrawal

- ID: RPT-016
- Source: SRS V1.4 N16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: withdrawal
- Behavior: 系统执行或展示[withdrawal]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N16 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### RPT-017 — re-sign

- ID: RPT-017
- Source: SRS V1.4 N17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: re-sign
- Behavior: 系统执行或展示[re-sign]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: 诊断工作区报告区撤回生效报告；原 Diagnosis 与重新打开的审核责任仍在同一工作区编辑、完成并再次签发。
- Backend Evidence: `V2ReportApplicationService.withdraw` 重开最后审核责任；再次 `signOut` 仅在无生效 ORIGINAL 时创建新的 Report。
- DB Evidence: 撤回的 R001 保持 `WITHDRAWN`，重新签发追加 R002 及独立 PDF；禁止更新旧签发快照或引入 ReportVersion。
- Frontend Evidence: `V2DiagnosisWorkspace.vue` 依据工作区动作重新开放编辑、审核和签发入口，并显示完整报告历史。
- Test Evidence: `V2ReportWebTest.previewDoesNotPersistAndSignOutWithdrawResignAndSupplementPreserveHistory` 断言 R001 撤回后同 Diagnosis 产生 R002，历史均保留。
- Status: COMPLETE
- Gap: 当前证据闭合；任何后续更正继续追加新 Report，不覆盖已签发报告。
- V2 Decision: re-sign 是同一 Diagnosis 的新 Report 事实，不是 ReportVersion。

### RPT-018 — supplemental report

- ID: RPT-018
- Source: SRS V1.4 N18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: supplemental report
- Behavior: 系统执行或展示[supplemental report]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N18 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N18 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N18 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N18 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N18 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### RPT-019 — historical report

- ID: RPT-019
- Source: SRS V1.4 N19
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: historical report
- Behavior: 系统执行或展示[historical report]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N19 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N19 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N19 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N19 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N19 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### RPT-020 — immutable signed artifact

- ID: RPT-020
- Source: SRS V1.4 N20
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: immutable signed artifact
- Behavior: 系统执行或展示[immutable signed artifact]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N20 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N20 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N20 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N20 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N20 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### RPT-021 — report status

- ID: RPT-021
- Source: SRS V1.4 N21
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: report status
- Behavior: 系统执行或展示[report status]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N21 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N21 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N21 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N21 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N21 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### RPT-022 — report TAT

- ID: RPT-022
- Source: SRS V1.4 N22
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: report TAT
- Behavior: 系统执行或展示[report TAT]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N22 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N22 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N22 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N22 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N22 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 无。报告中心按医院、业务类型的启用策略计算病例登记至当前或签发的分钟数，展示正常、临期、超期、按时签发和超期签发状态。
- V2 Decision: TAT是报告域只读投影，不改变病例或诊断状态；策略未配置时明确显示UNCONFIGURED，不以猜测阈值参与临床提醒。

### RPT-023 — frozen report

- ID: RPT-023
- Source: SRS V1.4 N23
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: frozen report
- Behavior: 系统执行或展示[frozen report]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 N23 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N23 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N23 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N23 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md N23 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### RPT-024 — self-service printing

- ID: RPT-024
- Source: SRS V1.4 N24
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: self-service printing
- Behavior: 系统执行或展示[self-service printing]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: 业务管理 → 报告发放 → 报告自助打印；输入报告、身份核验引用、终端、打印机和份数。
- Backend Evidence: `printReport` 仅接受生效报告，身份引用必须匹配病例最新快照；份数、范围和幂等校验后调用 `ReportOutputPort`，客户端不能伪造打印结果。
- DB Evidence: 复用 `report_print_record`，V47 增加请求人、设备任务、错误码和失败原因；独立幂等表防止终端重复提交。
- Frontend Evidence: `V2ClinicalOperations.vue` 提供自助终端打印表单、打印机检查与逐报告历史。
- Test Evidence: `V2BusinessOperationsSecurityTest` 覆盖身份拒绝、Simulator 成功、幂等和跨医院拒绝；`V2ClinicalOperations.test.ts` 与 `report-output.spec.ts` 覆盖 UI。
- Status: COMPLETE
- Gap: 产品内自助打印闭环完成；真实打印硬件状态与联调由 RPT-037 的外部依赖承担。
- V2 Decision: 身份核验失败不得调用打印端口，也不得生成成功记录。

### RPT-025 — report distribution

- ID: RPT-025
- Source: SRS V1.4 N25
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: report distribution
- Behavior: 系统执行或展示[report distribution]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: 业务管理报告发放入口可选择产品内 Simulator 或已配置外部目标，并查看每次结果。
- Backend Evidence: `ReportOutputPort` 隔离真实通道；仅生效报告可发放，Simulator、未配置失败、幂等和审计语义已实现。
- DB Evidence: `report_distribution` 保存每次请求、状态、通道引用和错误；V47 增加输出幂等证据。
- Frontend Evidence: `V2ClinicalOperations.vue` 执行发放并回显 SENT/FAILED 历史，不把“已建立请求”冒充已送达。
- Test Evidence: `V2BusinessOperationsSecurityTest` 覆盖 Simulator 成功、未配置通道失败且不改变 Report；浏览器测试覆盖结果回显。
- Status: EXTERNAL_DEPENDENCY
- Gap: 产品内 Port/Simulator/Attempt/History 已闭环；真实 HIS、EMR、患者平台地址、凭据和回执仍未联调。
- V2 Decision: 外部发放失败是独立尝试事实，不回滚或改写生效报告。

### CASE-015 — Case Header

- ID: CASE-015
- Source: SRS V1.4 P01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Case Header
- Behavior: 系统执行或展示[Case Header]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 P01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P01 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-016 — patient

- ID: CASE-016
- Source: SRS V1.4 P02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: patient
- Behavior: 系统执行或展示[patient]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 P02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P02 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-017 — clinical info

- ID: CASE-017
- Source: SRS V1.4 P03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: clinical info
- Behavior: 系统执行或展示[clinical info]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 P03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P03 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-018 — Application

- ID: CASE-018
- Source: SRS V1.4 P04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Application
- Behavior: 系统执行或展示[Application]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 P04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P04 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-019 — Specimen

- ID: CASE-019
- Source: SRS V1.4 P05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Specimen
- Behavior: 系统执行或展示[Specimen]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 P05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P05 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-020 — Grossing

- ID: CASE-020
- Source: SRS V1.4 P06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Grossing
- Behavior: 系统执行或展示[Grossing]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 P06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P06 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-021 — Block

- ID: CASE-021
- Source: SRS V1.4 P07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Block
- Behavior: 系统执行或展示[Block]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 P07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P07 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-022 — Slide

- ID: CASE-022
- Source: SRS V1.4 P08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Slide
- Behavior: 系统执行或展示[Slide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 P08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P08 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-023 — DigitalSlide

- ID: CASE-023
- Source: SRS V1.4 P09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: DigitalSlide
- Behavior: 系统执行或展示[DigitalSlide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 P09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P09 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-024 — Frozen

- ID: CASE-024
- Source: SRS V1.4 P10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Frozen
- Behavior: 系统执行或展示[Frozen]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 P10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P10 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-025 — TechnicalOrder

- ID: CASE-025
- Source: SRS V1.4 P11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: TechnicalOrder
- Behavior: 系统执行或展示[TechnicalOrder]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 P11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P11 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-026 — Diagnosis

- ID: CASE-026
- Source: SRS V1.4 P12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Diagnosis
- Behavior: 系统执行或展示[Diagnosis]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 P12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P12 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-027 — Report

- ID: CASE-027
- Source: SRS V1.4 P13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Report
- Behavior: 系统执行或展示[Report]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 P13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P13 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-028 — Archive

- ID: CASE-028
- Source: SRS V1.4 P14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Archive
- Behavior: 系统执行或展示[Archive]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 P14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P14 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CASE-028 独立立项、实现和验收。

### CASE-029 — Loan

- ID: CASE-029
- Source: SRS V1.4 P15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Loan
- Behavior: 系统执行或展示[Loan]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 P15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P15 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CASE-029 独立立项、实现和验收。

### CASE-030 — complete timeline

- ID: CASE-030
- Source: SRS V1.4 P16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: complete timeline
- Behavior: 系统执行或展示[complete timeline]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 P16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P16 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CASE-031 — audit history

- ID: CASE-031
- Source: SRS V1.4 P17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: audit history
- Behavior: 系统执行或展示[audit history]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 P17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md P17 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### MOL-001 — Molecular BusinessType

- ID: MOL-001
- Source: SRS V1.4 Q01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Molecular BusinessType
- Behavior: 系统执行或展示[Molecular BusinessType]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Q01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q01 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MOL-001 独立立项、实现和验收。

### MOL-002 — 独立检测号

- ID: MOL-002
- Source: SRS V1.4 Q02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 独立检测号
- Behavior: 系统执行或展示[独立检测号]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Q02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q02 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MOL-002 独立立项、实现和验收。

### MOL-003 — 分子申请

- ID: MOL-003
- Source: SRS V1.4 Q03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 分子申请
- Behavior: 系统执行或展示[分子申请]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Q03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q03 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MOL-003 独立立项、实现和验收。

### MOL-004 — 检测项目

- ID: MOL-004
- Source: SRS V1.4 Q04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 检测项目
- Behavior: 系统执行或展示[检测项目]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Q04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q04 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MOL-004 独立立项、实现和验收。

### MOL-005 — 标本

- ID: MOL-005
- Source: SRS V1.4 Q05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 标本
- Behavior: 系统执行或展示[标本]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Q05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q05 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MOL-005 独立立项、实现和验收。

### MOL-006 — raw data

- ID: MOL-006
- Source: SRS V1.4 Q06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: raw data
- Behavior: 系统执行或展示[raw data]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Q06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q06 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MOL-006 独立立项、实现和验收。

### MOL-007 — structured result

- ID: MOL-007
- Source: SRS V1.4 Q07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: structured result
- Behavior: 系统执行或展示[structured result]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Q07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q07 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MOL-007 独立立项、实现和验收。

### MOL-008 — analysis result

- ID: MOL-008
- Source: SRS V1.4 Q08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: analysis result
- Behavior: 系统执行或展示[analysis result]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Q08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q08 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MOL-008 独立立项、实现和验收。

### MOL-009 — instrument

- ID: MOL-009
- Source: SRS V1.4 Q09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: instrument
- Behavior: 系统执行或展示[instrument]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Q09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q09 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MOL-009 独立立项、实现和验收。

### MOL-010 — reagent kit

- ID: MOL-010
- Source: SRS V1.4 Q10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: reagent kit
- Behavior: 系统执行或展示[reagent kit]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Q10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q10 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MOL-010 独立立项、实现和验收。

### MOL-011 — equipment binding

- ID: MOL-011
- Source: SRS V1.4 Q11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: equipment binding
- Behavior: 系统执行或展示[equipment binding]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Q11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q11 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MOL-011 独立立项、实现和验收。

### MOL-012 — reagent kit binding

- ID: MOL-012
- Source: SRS V1.4 Q12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: reagent kit binding
- Behavior: 系统执行或展示[reagent kit binding]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Q12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q12 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MOL-012 独立立项、实现和验收。

### MOL-013 — molecular diagnosis

- ID: MOL-013
- Source: SRS V1.4 Q13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: molecular diagnosis
- Behavior: 系统执行或展示[molecular diagnosis]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Q13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q13 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MOL-013 独立立项、实现和验收。

### MOL-014 — molecular report template

- ID: MOL-014
- Source: SRS V1.4 Q14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: molecular report template
- Behavior: 系统执行或展示[molecular report template]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Q14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q14 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MOL-014 独立立项、实现和验收。

### MOL-015 — molecular report

- ID: MOL-015
- Source: SRS V1.4 Q15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: molecular report
- Behavior: 系统执行或展示[molecular report]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Q15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q15 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MOL-015 独立立项、实现和验收。

### MOL-016 — routine report linkage

- ID: MOL-016
- Source: SRS V1.4 Q16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: routine report linkage
- Behavior: 系统执行或展示[routine report linkage]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Q16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q16 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MOL-016 独立立项、实现和验收。

### MOL-017 — DigitalSlide/附件支持

- ID: MOL-017
- Source: SRS V1.4 Q17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: DigitalSlide/附件支持
- Behavior: 系统执行或展示[DigitalSlide/附件支持]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Q17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q17 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MOL-017 独立立项、实现和验收。

### MOL-018 — workbench queue

- ID: MOL-018
- Source: SRS V1.4 Q18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: workbench queue
- Behavior: 系统执行或展示[workbench queue]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Q18 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q18 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q18 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q18 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Q18 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MOL-018 独立立项、实现和验收。

### PERM-001 — Account

- ID: PERM-001
- Source: SRS V1.4 R01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Account
- Behavior: 系统执行或展示[Account]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 R01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R01 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PERM-002 — login

- ID: PERM-002
- Source: SRS V1.4 R02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: login
- Behavior: 系统执行或展示[login]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 R02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R02 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PERM-003 — logout

- ID: PERM-003
- Source: SRS V1.4 R03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: logout
- Behavior: 系统执行或展示[logout]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 R03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R03 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PERM-004 — activation

- ID: PERM-004
- Source: SRS V1.4 R04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: activation
- Behavior: 系统执行或展示[activation]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 R04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R04 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PERM-005 — disable

- ID: PERM-005
- Source: SRS V1.4 R05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: disable
- Behavior: 系统执行或展示[disable]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 R05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R05 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PERM-006 — password management

- ID: PERM-006
- Source: SRS V1.4 R06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: password management
- Behavior: 系统执行或展示[password management]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 R06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R06 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 PERM-006 独立立项、实现和验收。

### PERM-007 — User

- ID: PERM-007
- Source: SRS V1.4 R07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: User
- Behavior: 系统执行或展示[User]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 R07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R07 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PERM-008 — Staff Profile

- ID: PERM-008
- Source: SRS V1.4 R08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Staff Profile
- Behavior: 系统执行或展示[Staff Profile]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 R08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R08 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 PERM-008 独立立项、实现和验收。

### PERM-009 — Role

- ID: PERM-009
- Source: SRS V1.4 R09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Role
- Behavior: 系统执行或展示[Role]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 R09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R09 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PERM-010 — multiple roles

- ID: PERM-010
- Source: SRS V1.4 R10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: multiple roles
- Behavior: 系统执行或展示[multiple roles]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 R10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R10 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PERM-011 — Business Permission

- ID: PERM-011
- Source: SRS V1.4 R11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Business Permission
- Behavior: 系统执行或展示[Business Permission]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 R11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R11 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PERM-012 — Data Permission

- ID: PERM-012
- Source: SRS V1.4 R12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Data Permission
- Behavior: 系统执行或展示[Data Permission]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 R12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R12 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PERM-013 — Action Permission

- ID: PERM-013
- Source: SRS V1.4 R13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Action Permission
- Behavior: 系统执行或展示[Action Permission]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 R13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R13 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PERM-014 — individual override

- ID: PERM-014
- Source: SRS V1.4 R14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: individual override
- Behavior: 系统执行或展示[individual override]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 R14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R14 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PERM-015 — minimum privilege

- ID: PERM-015
- Source: SRS V1.4 R15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: minimum privilege
- Behavior: 系统执行或展示[minimum privilege]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 R15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R15 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PERM-016 — scheduling

- ID: PERM-016
- Source: SRS V1.4 R16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: scheduling
- Behavior: 系统执行或展示[scheduling]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 R16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R16 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 PERM-016 独立立项、实现和验收。

### PERM-017 — operation log

- ID: PERM-017
- Source: SRS V1.4 R17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: operation log
- Behavior: 系统执行或展示[operation log]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 R17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R17 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### PERM-018 — permission audit

- ID: PERM-018
- Source: SRS V1.4 R18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: permission audit
- Behavior: 系统执行或展示[permission audit]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 R18 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R18 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R18 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R18 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md R18 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### ARCH-001 — Block archive

- ID: ARCH-001
- Source: SRS V1.4 S01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Block archive
- Behavior: 系统执行或展示[Block archive]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 S01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S01 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### ARCH-002 — Slide archive

- ID: ARCH-002
- Source: SRS V1.4 S02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Slide archive
- Behavior: 系统执行或展示[Slide archive]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 S02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S02 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### ARCH-003 — archive location

- ID: ARCH-003
- Source: SRS V1.4 S03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: archive location
- Behavior: 系统执行或展示[archive location]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 S03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S03 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### ARCH-004 — batch archive

- ID: ARCH-004
- Source: SRS V1.4 S04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: batch archive
- Behavior: 系统执行或展示[batch archive]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 S04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S04 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### ARCH-005 — archive query

- ID: ARCH-005
- Source: SRS V1.4 S05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: archive query
- Behavior: 系统执行或展示[archive query]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 S05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S05 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### ARCH-006 — physical inventory

- ID: ARCH-006
- Source: SRS V1.4 S06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: physical inventory
- Behavior: 系统执行或展示[physical inventory]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 S06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S06 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-006 独立立项、实现和验收。

### ARCH-007 — archive inspection

- ID: ARCH-007
- Source: SRS V1.4 S07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: archive inspection
- Behavior: 系统执行或展示[archive inspection]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 S07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S07 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-007 独立立项、实现和验收。

### ARCH-008 — loan

- ID: ARCH-008
- Source: SRS V1.4 S08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: loan
- Behavior: 系统执行或展示[loan]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 S08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S08 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### ARCH-009 — multiple materials loan

- ID: ARCH-009
- Source: SRS V1.4 S09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: multiple materials loan
- Behavior: 系统执行或展示[multiple materials loan]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 S09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S09 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### ARCH-010 — expected return

- ID: ARCH-010
- Source: SRS V1.4 S10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: expected return
- Behavior: 系统执行或展示[expected return]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 S10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S10 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### ARCH-011 — return

- ID: ARCH-011
- Source: SRS V1.4 S11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: return
- Behavior: 系统执行或展示[return]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 S11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S11 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### ARCH-012 — overdue

- ID: ARCH-012
- Source: SRS V1.4 S12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: overdue
- Behavior: 系统执行或展示[overdue]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 S12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S12 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### ARCH-013 — destruction

- ID: ARCH-013
- Source: SRS V1.4 S13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: destruction
- Behavior: 系统执行或展示[destruction]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 S13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S13 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### ARCH-014 — destruction approval

- ID: ARCH-014
- Source: SRS V1.4 S14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: destruction approval
- Behavior: 系统执行或展示[destruction approval]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 S14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S14 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-014 独立立项、实现和验收。

### ARCH-015 — history

- ID: ARCH-015
- Source: SRS V1.4 S15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: history
- Behavior: 系统执行或展示[history]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 S15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md S15 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### LOGI-001 — external consultation package

- ID: LOGI-001
- Source: SRS V1.4 T01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: external consultation package
- Behavior: 系统执行或展示[external consultation package]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 T01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T01 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 LOGI-001 独立立项、实现和验收。

### LOGI-002 — package contents

- ID: LOGI-002
- Source: SRS V1.4 T02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: package contents
- Behavior: 系统执行或展示[package contents]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 T02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T02 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 LOGI-002 独立立项、实现和验收。

### LOGI-003 — Block

- ID: LOGI-003
- Source: SRS V1.4 T03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Block
- Behavior: 系统执行或展示[Block]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 T03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T03 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 LOGI-003 独立立项、实现和验收。

### LOGI-004 — Slide

- ID: LOGI-004
- Source: SRS V1.4 T04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Slide
- Behavior: 系统执行或展示[Slide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 T04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T04 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 LOGI-004 独立立项、实现和验收。

### LOGI-005 — documents

- ID: LOGI-005
- Source: SRS V1.4 T05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: documents
- Behavior: 系统执行或展示[documents]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 T05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T05 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 LOGI-005 独立立项、实现和验收。

### LOGI-006 — courier company

- ID: LOGI-006
- Source: SRS V1.4 T06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: courier company
- Behavior: 系统执行或展示[courier company]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 T06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T06 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 LOGI-006 独立立项、实现和验收。

### LOGI-007 — tracking number

- ID: LOGI-007
- Source: SRS V1.4 T07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: tracking number
- Behavior: 系统执行或展示[tracking number]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 T07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T07 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 LOGI-007 独立立项、实现和验收。

### LOGI-008 — recipient

- ID: LOGI-008
- Source: SRS V1.4 T08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: recipient
- Behavior: 系统执行或展示[recipient]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 T08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T08 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 LOGI-008 独立立项、实现和验收。

### LOGI-009 — sender

- ID: LOGI-009
- Source: SRS V1.4 T09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: sender
- Behavior: 系统执行或展示[sender]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 T09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T09 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 LOGI-009 独立立项、实现和验收。

### LOGI-010 — logistics tracking

- ID: LOGI-010
- Source: SRS V1.4 T10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: logistics tracking
- Behavior: 系统执行或展示[logistics tracking]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 T10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T10 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 LOGI-010 独立立项、实现和验收。

### LOGI-011 — logistics abnormal

- ID: LOGI-011
- Source: SRS V1.4 T11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: logistics abnormal
- Behavior: 系统执行或展示[logistics abnormal]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 T11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T11 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 LOGI-011 独立立项、实现和验收。

### LOGI-012 — common addresses

- ID: LOGI-012
- Source: SRS V1.4 T12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: common addresses
- Behavior: 系统执行或展示[common addresses]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 T12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T12 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 LOGI-012 独立立项、实现和验收。

### LOGI-013 — loan relationship

- ID: LOGI-013
- Source: SRS V1.4 T13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: loan relationship
- Behavior: 系统执行或展示[loan relationship]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 T13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T13 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 LOGI-013 独立立项、实现和验收。

### LOGI-014 — return relationship

- ID: LOGI-014
- Source: SRS V1.4 T14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: return relationship
- Behavior: 系统执行或展示[return relationship]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 T14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md T14 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 LOGI-014 独立立项、实现和验收。

### CV-001 — Critical Value

- ID: CV-001
- Source: SRS V1.4 U01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Critical Value
- Behavior: 系统执行或展示[Critical Value]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 U01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U01 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CV-001 独立立项、实现和验收。

### CV-002 — grading

- ID: CV-002
- Source: SRS V1.4 U02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: grading
- Behavior: 系统执行或展示[grading]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 U02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U02 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CV-002 独立立项、实现和验收。

### CV-003 — clinical notification

- ID: CV-003
- Source: SRS V1.4 U03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: clinical notification
- Behavior: 系统执行或展示[clinical notification]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 U03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U03 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CV-003 独立立项、实现和验收。

### CV-004 — notification method

- ID: CV-004
- Source: SRS V1.4 U04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: notification method
- Behavior: 系统执行或展示[notification method]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 U04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U04 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CV-004 独立立项、实现和验收。

### CV-005 — notification time

- ID: CV-005
- Source: SRS V1.4 U05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: notification time
- Behavior: 系统执行或展示[notification time]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 U05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U05 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CV-005 独立立项、实现和验收。

### CV-006 — recipient

- ID: CV-006
- Source: SRS V1.4 U06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: recipient
- Behavior: 系统执行或展示[recipient]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 U06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U06 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CV-006 独立立项、实现和验收。

### CV-007 — acknowledgement

- ID: CV-007
- Source: SRS V1.4 U07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: acknowledgement
- Behavior: 系统执行或展示[acknowledgement]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 U07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U07 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CV-007 独立立项、实现和验收。

### CV-008 — feedback

- ID: CV-008
- Source: SRS V1.4 U08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: feedback
- Behavior: 系统执行或展示[feedback]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 U08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U08 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CV-008 独立立项、实现和验收。

### CV-009 — report

- ID: CV-009
- Source: SRS V1.4 U09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: report
- Behavior: 系统执行或展示[report]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 U09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U09 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CV-009 独立立项、实现和验收。

### CV-010 — communication history

- ID: CV-010
- Source: SRS V1.4 U10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: communication history
- Behavior: 系统执行或展示[communication history]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 U10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U10 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CV-010 独立立项、实现和验收。

### CV-011 — multi-condition search

- ID: CV-011
- Source: SRS V1.4 U11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: multi-condition search
- Behavior: 系统执行或展示[multi-condition search]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 U11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U11 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CV-011 独立立项、实现和验收。

### CV-012 — export

- ID: CV-012
- Source: SRS V1.4 U12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: export
- Behavior: 系统执行或展示[export]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 U12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U12 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CV-012 独立立项、实现和验收。

### CV-013 — TAT

- ID: CV-013
- Source: SRS V1.4 U13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: TAT
- Behavior: 系统执行或展示[TAT]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 U13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md U13 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CV-013 独立立项、实现和验收。

### QC-001 — specimen QC

- ID: QC-001
- Source: SRS V1.4 V01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: specimen QC
- Behavior: 系统执行或展示[specimen QC]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 V01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V01 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-001 独立立项、实现和验收。

### QC-002 — grossing QC

- ID: QC-002
- Source: SRS V1.4 V02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: grossing QC
- Behavior: 系统执行或展示[grossing QC]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 V02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V02 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-002 独立立项、实现和验收。

### QC-003 — block QC

- ID: QC-003
- Source: SRS V1.4 V03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: block QC
- Behavior: 系统执行或展示[block QC]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 V03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V03 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-003 独立立项、实现和验收。

### QC-004 — slide QC

- ID: QC-004
- Source: SRS V1.4 V04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: slide QC
- Behavior: 系统执行或展示[slide QC]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 V04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V04 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-004 独立立项、实现和验收。

### QC-005 — IHC QC

- ID: QC-005
- Source: SRS V1.4 V05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: IHC QC
- Behavior: 系统执行或展示[IHC QC]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 V05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V05 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-005 独立立项、实现和验收。

### QC-006 — frozen QC

- ID: QC-006
- Source: SRS V1.4 V06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: frozen QC
- Behavior: 系统执行或展示[frozen QC]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 V06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V06 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-006 独立立项、实现和验收。

### QC-007 — diagnosis QC

- ID: QC-007
- Source: SRS V1.4 V07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: diagnosis QC
- Behavior: 系统执行或展示[diagnosis QC]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 V07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V07 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-007 独立立项、实现和验收。

### QC-008 — report QC

- ID: QC-008
- Source: SRS V1.4 V08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: report QC
- Behavior: 系统执行或展示[report QC]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 V08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V08 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-008 独立立项、实现和验收。

### QC-009 — TAT QC

- ID: QC-009
- Source: SRS V1.4 V09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: TAT QC
- Behavior: 系统执行或展示[TAT QC]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 V09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V09 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-009 独立立项、实现和验收。

### QC-010 — custom QC indicators

- ID: QC-010
- Source: SRS V1.4 V10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: custom QC indicators
- Behavior: 系统执行或展示[custom QC indicators]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 V10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V10 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-010 独立立项、实现和验收。

### QC-011 — daily

- ID: QC-011
- Source: SRS V1.4 V11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: daily
- Behavior: 系统执行或展示[daily]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 V11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V11 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-011 独立立项、实现和验收。

### QC-012 — weekly

- ID: QC-012
- Source: SRS V1.4 V12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: weekly
- Behavior: 系统执行或展示[weekly]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 V12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V12 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-012 独立立项、实现和验收。

### QC-013 — monthly

- ID: QC-013
- Source: SRS V1.4 V13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: monthly
- Behavior: 系统执行或展示[monthly]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 V13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V13 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-013 独立立项、实现和验收。

### QC-014 — quarterly

- ID: QC-014
- Source: SRS V1.4 V14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: quarterly
- Behavior: 系统执行或展示[quarterly]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 V14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V14 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-014 独立立项、实现和验收。

### QC-015 — annual

- ID: QC-015
- Source: SRS V1.4 V15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: annual
- Behavior: 系统执行或展示[annual]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 V15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V15 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-015 独立立项、实现和验收。

### QC-016 — statistics

- ID: QC-016
- Source: SRS V1.4 V16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: statistics
- Behavior: 系统执行或展示[statistics]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 V16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V16 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-016 独立立项、实现和验收。

### QC-017 — charts

- ID: QC-017
- Source: SRS V1.4 V17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: charts
- Behavior: 系统执行或展示[charts]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 V17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V17 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-017 独立立项、实现和验收。

### QC-018 — export

- ID: QC-018
- Source: SRS V1.4 V18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: export
- Behavior: 系统执行或展示[export]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 V18 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V18 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V18 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V18 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V18 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-018 独立立项、实现和验收。

### QC-019 — abnormal warning

- ID: QC-019
- Source: SRS V1.4 V19
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: abnormal warning
- Behavior: 系统执行或展示[abnormal warning]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 V19 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V19 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V19 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V19 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md V19 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-019 独立立项、实现和验收。

### QC-020 — quality manual

- ID: QC-020
- Source: SRS V1.4 W01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: quality manual
- Behavior: 系统执行或展示[quality manual]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 W01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W01 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-020 独立立项、实现和验收。

### QC-021 — procedure documents

- ID: QC-021
- Source: SRS V1.4 W02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: procedure documents
- Behavior: 系统执行或展示[procedure documents]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 W02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W02 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-021 独立立项、实现和验收。

### QC-022 — work instructions

- ID: QC-022
- Source: SRS V1.4 W03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: work instructions
- Behavior: 系统执行或展示[work instructions]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 W03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W03 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-022 独立立项、实现和验收。

### QC-023 — forms

- ID: QC-023
- Source: SRS V1.4 W04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: forms
- Behavior: 系统执行或展示[forms]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 W04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W04 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-023 独立立项、实现和验收。

### QC-024 — upload

- ID: QC-024
- Source: SRS V1.4 W05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: upload
- Behavior: 系统执行或展示[upload]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 W05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W05 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-024 独立立项、实现和验收。

### QC-025 — online view

- ID: QC-025
- Source: SRS V1.4 W06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: online view
- Behavior: 系统执行或展示[online view]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 W06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W06 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-025 独立立项、实现和验收。

### QC-026 — revision

- ID: QC-026
- Source: SRS V1.4 W07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: revision
- Behavior: 系统执行或展示[revision]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 W07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W07 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-026 独立立项、实现和验收。

### QC-027 — revision history

- ID: QC-027
- Source: SRS V1.4 W08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: revision history
- Behavior: 系统执行或展示[revision history]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 W08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W08 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-027 独立立项、实现和验收。

### QC-028 — review

- ID: QC-028
- Source: SRS V1.4 W09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: review
- Behavior: 系统执行或展示[review]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 W09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W09 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-028 独立立项、实现和验收。

### QC-029 — archive

- ID: QC-029
- Source: SRS V1.4 W10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: archive
- Behavior: 系统执行或展示[archive]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 W10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W10 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-029 独立立项、实现和验收。

### QC-030 — permission

- ID: QC-030
- Source: SRS V1.4 W11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: permission
- Behavior: 系统执行或展示[permission]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 W11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W11 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-030 独立立项、实现和验收。

### QC-031 — audit

- ID: QC-031
- Source: SRS V1.4 W12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: audit
- Behavior: 系统执行或展示[audit]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 W12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md W12 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 QC-031 独立立项、实现和验收。

### EQUIP-001 — equipment registry

- ID: EQUIP-001
- Source: SRS V1.4 X01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: equipment registry
- Behavior: 系统执行或展示[equipment registry]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 X01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X01 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 EQUIP-001 独立立项、实现和验收。

### EQUIP-002 — manufacturer

- ID: EQUIP-002
- Source: SRS V1.4 X02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: manufacturer
- Behavior: 系统执行或展示[manufacturer]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 X02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X02 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 EQUIP-002 独立立项、实现和验收。

### EQUIP-003 — model

- ID: EQUIP-003
- Source: SRS V1.4 X03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: model
- Behavior: 系统执行或展示[model]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 X03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X03 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 EQUIP-003 独立立项、实现和验收。

### EQUIP-004 — serial

- ID: EQUIP-004
- Source: SRS V1.4 X04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: serial
- Behavior: 系统执行或展示[serial]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 X04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X04 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 EQUIP-004 独立立项、实现和验收。

### EQUIP-005 — location

- ID: EQUIP-005
- Source: SRS V1.4 X05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: location
- Behavior: 系统执行或展示[location]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 X05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X05 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 EQUIP-005 独立立项、实现和验收。

### EQUIP-006 — custodian

- ID: EQUIP-006
- Source: SRS V1.4 X06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: custodian
- Behavior: 系统执行或展示[custodian]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 X06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X06 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 EQUIP-006 独立立项、实现和验收。

### EQUIP-007 — status

- ID: EQUIP-007
- Source: SRS V1.4 X07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: status
- Behavior: 系统执行或展示[status]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 X07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X07 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 EQUIP-007 独立立项、实现和验收。

### EQUIP-008 — operating program

- ID: EQUIP-008
- Source: SRS V1.4 X08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: operating program
- Behavior: 系统执行或展示[operating program]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 X08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X08 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 EQUIP-008 独立立项、实现和验收。

### EQUIP-009 — usage

- ID: EQUIP-009
- Source: SRS V1.4 X09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: usage
- Behavior: 系统执行或展示[usage]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 X09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X09 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 EQUIP-009 独立立项、实现和验收。

### EQUIP-010 — maintenance

- ID: EQUIP-010
- Source: SRS V1.4 X10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: maintenance
- Behavior: 系统执行或展示[maintenance]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 X10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X10 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 EQUIP-010 独立立项、实现和验收。

### EQUIP-011 — preventive maintenance

- ID: EQUIP-011
- Source: SRS V1.4 X11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: preventive maintenance
- Behavior: 系统执行或展示[preventive maintenance]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 X11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X11 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 EQUIP-011 独立立项、实现和验收。

### EQUIP-012 — repair

- ID: EQUIP-012
- Source: SRS V1.4 X12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: repair
- Behavior: 系统执行或展示[repair]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 X12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X12 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 EQUIP-012 独立立项、实现和验收。

### EQUIP-013 — fault

- ID: EQUIP-013
- Source: SRS V1.4 X13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: fault
- Behavior: 系统执行或展示[fault]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 X13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X13 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 EQUIP-013 独立立项、实现和验收。

### EQUIP-014 — warranty

- ID: EQUIP-014
- Source: SRS V1.4 X14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: warranty
- Behavior: 系统执行或展示[warranty]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 X14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X14 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 EQUIP-014 独立立项、实现和验收。

### EQUIP-015 — procurement record

- ID: EQUIP-015
- Source: SRS V1.4 X15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: procurement record
- Behavior: 系统执行或展示[procurement record]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 X15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X15 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 EQUIP-015 独立立项、实现和验收。

### EQUIP-016 — lifecycle

- ID: EQUIP-016
- Source: SRS V1.4 X16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: lifecycle
- Behavior: 系统执行或展示[lifecycle]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 X16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X16 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 EQUIP-016 独立立项、实现和验收。

### EQUIP-017 — warning

- ID: EQUIP-017
- Source: SRS V1.4 X17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: warning
- Behavior: 系统执行或展示[warning]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 X17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X17 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 EQUIP-017 独立立项、实现和验收。

### EQUIP-018 — statistics

- ID: EQUIP-018
- Source: SRS V1.4 X18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: statistics
- Behavior: 系统执行或展示[statistics]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 X18 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X18 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X18 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X18 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md X18 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 EQUIP-018 独立立项、实现和验收。

### CONS-001 — consumable catalog

- ID: CONS-001
- Source: SRS V1.4 Y01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: consumable catalog
- Behavior: 系统执行或展示[consumable catalog]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Y01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y01 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CONS-001 独立立项、实现和验收。

### CONS-002 — reagent catalog

- ID: CONS-002
- Source: SRS V1.4 Y02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: reagent catalog
- Behavior: 系统执行或展示[reagent catalog]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Y02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y02 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CONS-002 独立立项、实现和验收。

### CONS-003 — supplier

- ID: CONS-003
- Source: SRS V1.4 Y03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: supplier
- Behavior: 系统执行或展示[supplier]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Y03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y03 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CONS-003 独立立项、实现和验收。

### CONS-004 — manufacturer

- ID: CONS-004
- Source: SRS V1.4 Y04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: manufacturer
- Behavior: 系统执行或展示[manufacturer]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Y04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y04 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CONS-004 独立立项、实现和验收。

### CONS-005 — purchase

- ID: CONS-005
- Source: SRS V1.4 Y05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: purchase
- Behavior: 系统执行或展示[purchase]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Y05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y05 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CONS-005 独立立项、实现和验收。

### CONS-006 — inbound

- ID: CONS-006
- Source: SRS V1.4 Y06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: inbound
- Behavior: 系统执行或展示[inbound]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Y06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y06 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CONS-006 独立立项、实现和验收。

### CONS-007 — outbound

- ID: CONS-007
- Source: SRS V1.4 Y07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: outbound
- Behavior: 系统执行或展示[outbound]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Y07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y07 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CONS-007 独立立项、实现和验收。

### CONS-008 — requisition

- ID: CONS-008
- Source: SRS V1.4 Y08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: requisition
- Behavior: 系统执行或展示[requisition]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Y08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y08 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CONS-008 独立立项、实现和验收。

### CONS-009 — inventory

- ID: CONS-009
- Source: SRS V1.4 Y09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: inventory
- Behavior: 系统执行或展示[inventory]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Y09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y09 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CONS-009 独立立项、实现和验收。

### CONS-010 — storage location

- ID: CONS-010
- Source: SRS V1.4 Y10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: storage location
- Behavior: 系统执行或展示[storage location]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Y10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y10 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CONS-010 独立立项、实现和验收。

### CONS-011 — batch

- ID: CONS-011
- Source: SRS V1.4 Y11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: batch
- Behavior: 系统执行或展示[batch]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Y11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y11 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CONS-011 独立立项、实现和验收。

### CONS-012 — expiry

- ID: CONS-012
- Source: SRS V1.4 Y12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: expiry
- Behavior: 系统执行或展示[expiry]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Y12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y12 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CONS-012 独立立项、实现和验收。

### CONS-013 — expiry warning

- ID: CONS-013
- Source: SRS V1.4 Y13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: expiry warning
- Behavior: 系统执行或展示[expiry warning]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Y13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y13 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CONS-013 独立立项、实现和验收。

### CONS-014 — usage

- ID: CONS-014
- Source: SRS V1.4 Y14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: usage
- Behavior: 系统执行或展示[usage]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Y14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y14 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CONS-014 独立立项、实现和验收。

### CONS-015 — automatic consumption linkage

- ID: CONS-015
- Source: SRS V1.4 Y15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: automatic consumption linkage
- Behavior: 系统执行或展示[automatic consumption linkage]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Y15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y15 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CONS-015 独立立项、实现和验收。

### CONS-016 — reagent quality evaluation

- ID: CONS-016
- Source: SRS V1.4 Y16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: reagent quality evaluation
- Behavior: 系统执行或展示[reagent quality evaluation]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Y16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y16 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CONS-016 独立立项、实现和验收。

### CONS-017 — hazardous chemicals

- ID: CONS-017
- Source: SRS V1.4 Y17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: hazardous chemicals
- Behavior: 系统执行或展示[hazardous chemicals]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Y17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y17 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CONS-017 独立立项、实现和验收。

### CONS-018 — inventory report

- ID: CONS-018
- Source: SRS V1.4 Y18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: inventory report
- Behavior: 系统执行或展示[inventory report]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Y18 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y18 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y18 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y18 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y18 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CONS-018 独立立项、实现和验收。

### CONS-019 — consumption report

- ID: CONS-019
- Source: SRS V1.4 Y19
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: consumption report
- Behavior: 系统执行或展示[consumption report]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Y19 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y19 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y19 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y19 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y19 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CONS-019 独立立项、实现和验收。

### CONS-020 — audit

- ID: CONS-020
- Source: SRS V1.4 Y20
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: audit
- Behavior: 系统执行或展示[audit]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Y20 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y20 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y20 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y20 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Y20 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CONS-020 独立立项、实现和验收。

### PROC-001 — purchase request

- ID: PROC-001
- Source: SRS V1.4 Z01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: purchase request
- Behavior: 系统执行或展示[purchase request]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Z01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z01 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 PROC-001 独立立项、实现和验收。

### PROC-002 — approval

- ID: PROC-002
- Source: SRS V1.4 Z02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: approval
- Behavior: 系统执行或展示[approval]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Z02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z02 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 PROC-002 独立立项、实现和验收。

### PROC-003 — items

- ID: PROC-003
- Source: SRS V1.4 Z03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: items
- Behavior: 系统执行或展示[items]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Z03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z03 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 PROC-003 独立立项、实现和验收。

### PROC-004 — quantity

- ID: PROC-004
- Source: SRS V1.4 Z04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: quantity
- Behavior: 系统执行或展示[quantity]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Z04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z04 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 PROC-004 独立立项、实现和验收。

### PROC-005 — amount

- ID: PROC-005
- Source: SRS V1.4 Z05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: amount
- Behavior: 系统执行或展示[amount]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Z05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z05 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 PROC-005 独立立项、实现和验收。

### PROC-006 — threshold

- ID: PROC-006
- Source: SRS V1.4 Z06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: threshold
- Behavior: 系统执行或展示[threshold]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Z06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z06 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 PROC-006 独立立项、实现和验收。

### PROC-007 — approval history

- ID: PROC-007
- Source: SRS V1.4 Z07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: approval history
- Behavior: 系统执行或展示[approval history]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Z07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z07 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 PROC-007 独立立项、实现和验收。

### PROC-008 — procurement progress

- ID: PROC-008
- Source: SRS V1.4 Z08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: procurement progress
- Behavior: 系统执行或展示[procurement progress]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Z08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z08 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 PROC-008 独立立项、实现和验收。

### PROC-009 — contract attachment

- ID: PROC-009
- Source: SRS V1.4 Z09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: contract attachment
- Behavior: 系统执行或展示[contract attachment]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Z09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z09 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 PROC-009 独立立项、实现和验收。

### PROC-010 — inbound link

- ID: PROC-010
- Source: SRS V1.4 Z10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: inbound link
- Behavior: 系统执行或展示[inbound link]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Z10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z10 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 PROC-010 独立立项、实现和验收。

### PROC-011 — archive

- ID: PROC-011
- Source: SRS V1.4 Z11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: archive
- Behavior: 系统执行或展示[archive]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Z11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z11 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 PROC-011 独立立项、实现和验收。

### PROC-012 — income reference/statistics

- ID: PROC-012
- Source: SRS V1.4 Z12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: income reference/statistics
- Behavior: 系统执行或展示[income reference/statistics]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 Z12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md Z12 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 PROC-012 独立立项、实现和验收。

### SPACE-001 — department space

- ID: SPACE-001
- Source: SRS V1.4 AA01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: department space
- Behavior: 系统执行或展示[department space]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AA01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA01 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SPACE-001 独立立项、实现和验收。

### SPACE-002 — hierarchical space

- ID: SPACE-002
- Source: SRS V1.4 AA02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: hierarchical space
- Behavior: 系统执行或展示[hierarchical space]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AA02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA02 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SPACE-002 独立立项、实现和验收。

### SPACE-003 — polluted zone

- ID: SPACE-003
- Source: SRS V1.4 AA03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: polluted zone
- Behavior: 系统执行或展示[polluted zone]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AA03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA03 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SPACE-003 独立立项、实现和验收。

### SPACE-004 — semi-polluted zone

- ID: SPACE-004
- Source: SRS V1.4 AA04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: semi-polluted zone
- Behavior: 系统执行或展示[semi-polluted zone]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AA04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA04 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SPACE-004 独立立项、实现和验收。

### SPACE-005 — buffer zone

- ID: SPACE-005
- Source: SRS V1.4 AA05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: buffer zone
- Behavior: 系统执行或展示[buffer zone]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AA05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA05 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SPACE-005 独立立项、实现和验收。

### SPACE-006 — clean zone

- ID: SPACE-006
- Source: SRS V1.4 AA06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: clean zone
- Behavior: 系统执行或展示[clean zone]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AA06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA06 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SPACE-006 独立立项、实现和验收。

### SPACE-007 — area

- ID: SPACE-007
- Source: SRS V1.4 AA07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: area
- Behavior: 系统执行或展示[area]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AA07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA07 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SPACE-007 独立立项、实现和验收。

### SPACE-008 — administrator

- ID: SPACE-008
- Source: SRS V1.4 AA08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: administrator
- Behavior: 系统执行或展示[administrator]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AA08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA08 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SPACE-008 独立立项、实现和验收。

### SPACE-009 — 360-degree view

- ID: SPACE-009
- Source: SRS V1.4 AA09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 360-degree view
- Behavior: 系统执行或展示[360-degree view]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AA09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA09 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SPACE-009 独立立项、实现和验收。

### SPACE-010 — temperature

- ID: SPACE-010
- Source: SRS V1.4 AA10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: temperature
- Behavior: 系统执行或展示[temperature]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AA10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA10 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SPACE-010 独立立项、实现和验收。

### SPACE-011 — humidity

- ID: SPACE-011
- Source: SRS V1.4 AA11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: humidity
- Behavior: 系统执行或展示[humidity]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AA11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA11 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SPACE-011 独立立项、实现和验收。

### SPACE-012 — hazardous gas

- ID: SPACE-012
- Source: SRS V1.4 AA12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: hazardous gas
- Behavior: 系统执行或展示[hazardous gas]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AA12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA12 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SPACE-012 独立立项、实现和验收。

### SPACE-013 — fire safety

- ID: SPACE-013
- Source: SRS V1.4 AA13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: fire safety
- Behavior: 系统执行或展示[fire safety]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AA13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA13 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SPACE-013 独立立项、实现和验收。

### SPACE-014 — hazardous storage

- ID: SPACE-014
- Source: SRS V1.4 AA14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: hazardous storage
- Behavior: 系统执行或展示[hazardous storage]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AA14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA14 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SPACE-014 独立立项、实现和验收。

### SPACE-015 — threshold

- ID: SPACE-015
- Source: SRS V1.4 AA15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: threshold
- Behavior: 系统执行或展示[threshold]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AA15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA15 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SPACE-015 独立立项、实现和验收。

### SPACE-016 — alarm

- ID: SPACE-016
- Source: SRS V1.4 AA16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: alarm
- Behavior: 系统执行或展示[alarm]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AA16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA16 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SPACE-016 独立立项、实现和验收。

### SPACE-017 — history

- ID: SPACE-017
- Source: SRS V1.4 AA17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: history
- Behavior: 系统执行或展示[history]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AA17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AA17 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SPACE-017 独立立项、实现和验收。

### STAT-001 — case statistics

- ID: STAT-001
- Source: SRS V1.4 AB01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: case statistics
- Behavior: 系统执行或展示[case statistics]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB01 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-001 独立立项、实现和验收。

### STAT-002 — specimen statistics

- ID: STAT-002
- Source: SRS V1.4 AB02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: specimen statistics
- Behavior: 系统执行或展示[specimen statistics]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB02 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-002 独立立项、实现和验收。

### STAT-003 — workload

- ID: STAT-003
- Source: SRS V1.4 AB03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: workload
- Behavior: 系统执行或展示[workload]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB03 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-003 独立立项、实现和验收。

### STAT-004 — doctor workload

- ID: STAT-004
- Source: SRS V1.4 AB04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: doctor workload
- Behavior: 系统执行或展示[doctor workload]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB04 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-004 独立立项、实现和验收。

### STAT-005 — technician workload

- ID: STAT-005
- Source: SRS V1.4 AB05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: technician workload
- Behavior: 系统执行或展示[technician workload]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB05 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-005 独立立项、实现和验收。

### STAT-006 — income

- ID: STAT-006
- Source: SRS V1.4 AB06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: income
- Behavior: 系统执行或展示[income]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB06 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-006 独立立项、实现和验收。

### STAT-007 — TAT

- ID: STAT-007
- Source: SRS V1.4 AB07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: TAT
- Behavior: 系统执行或展示[TAT]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB07 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 无。统计接口从病例登记与不可变报告签发事实计算平均签发分钟数、按时率、已完成和在途时效分布，质控统计页直接展示。
- V2 Decision: 统计只读业务事实并应用当前医院启用的报告TAT策略，不创建或驱动通用任务。

### STAT-008 — overdue reports

- ID: STAT-008
- Source: SRS V1.4 AB08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: overdue reports
- Behavior: 系统执行或展示[overdue reports]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB08 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 无。统计页提供真实超期病例数量和病理号、患者引用、业务类型、耗时、目标时间、延迟登记状态明细。
- V2 Decision: 超期明细来自当前医院病例、签发报告和策略查询；不使用fixture数字，且只作为监测和下钻入口。

### STAT-009 — TechnicalOrder

- ID: STAT-009
- Source: SRS V1.4 AB09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: TechnicalOrder
- Behavior: 系统执行或展示[TechnicalOrder]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB09 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-009 独立立项、实现和验收。

### STAT-010 — IHC

- ID: STAT-010
- Source: SRS V1.4 AB10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: IHC
- Behavior: 系统执行或展示[IHC]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB10 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-010 独立立项、实现和验收。

### STAT-011 — molecular

- ID: STAT-011
- Source: SRS V1.4 AB11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: molecular
- Behavior: 系统执行或展示[molecular]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB11 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-011 独立立项、实现和验收。

### STAT-012 — frozen

- ID: STAT-012
- Source: SRS V1.4 AB12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: frozen
- Behavior: 系统执行或展示[frozen]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB12 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-012 独立立项、实现和验收。

### STAT-013 — cytology

- ID: STAT-013
- Source: SRS V1.4 AB13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: cytology
- Behavior: 系统执行或展示[cytology]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB13 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-013 独立立项、实现和验收。

### STAT-014 — slide

- ID: STAT-014
- Source: SRS V1.4 AB14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: slide
- Behavior: 系统执行或展示[slide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB14 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-014 独立立项、实现和验收。

### STAT-015 — DigitalSlide

- ID: STAT-015
- Source: SRS V1.4 AB15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: DigitalSlide
- Behavior: 系统执行或展示[DigitalSlide]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB15 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-015 独立立项、实现和验收。

### STAT-016 — QC

- ID: STAT-016
- Source: SRS V1.4 AB16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: QC
- Behavior: 系统执行或展示[QC]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB16 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-016 独立立项、实现和验收。

### STAT-017 — organization

- ID: STAT-017
- Source: SRS V1.4 AB17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: organization
- Behavior: 系统执行或展示[organization]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB17 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-017 独立立项、实现和验收。

### STAT-018 — department

- ID: STAT-018
- Source: SRS V1.4 AB18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: department
- Behavior: 系统执行或展示[department]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB18 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB18 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB18 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB18 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB18 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-018 独立立项、实现和验收。

### STAT-019 — clinician

- ID: STAT-019
- Source: SRS V1.4 AB19
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: clinician
- Behavior: 系统执行或展示[clinician]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB19 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB19 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB19 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB19 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB19 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-019 独立立项、实现和验收。

### STAT-020 — disease

- ID: STAT-020
- Source: SRS V1.4 AB20
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: disease
- Behavior: 系统执行或展示[disease]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB20 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB20 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB20 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB20 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB20 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-020 独立立项、实现和验收。

### STAT-021 — time

- ID: STAT-021
- Source: SRS V1.4 AB21
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: time
- Behavior: 系统执行或展示[time]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB21 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB21 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB21 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB21 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB21 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-021 独立立项、实现和验收。

### STAT-022 — charts

- ID: STAT-022
- Source: SRS V1.4 AB22
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: charts
- Behavior: 系统执行或展示[charts]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB22 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB22 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB22 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB22 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB22 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-022 独立立项、实现和验收。

### STAT-023 — table

- ID: STAT-023
- Source: SRS V1.4 AB23
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: table
- Behavior: 系统执行或展示[table]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB23 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB23 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB23 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB23 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB23 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-023 独立立项、实现和验收。

### STAT-024 — drill-down

- ID: STAT-024
- Source: SRS V1.4 AB24
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: drill-down
- Behavior: 系统执行或展示[drill-down]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB24 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB24 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB24 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB24 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB24 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-024 独立立项、实现和验收。

### STAT-025 — Excel

- ID: STAT-025
- Source: SRS V1.4 AB25
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Excel
- Behavior: 系统执行或展示[Excel]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB25 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB25 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB25 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB25 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB25 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-025 独立立项、实现和验收。

### STAT-026 — PDF

- ID: STAT-026
- Source: SRS V1.4 AB26
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: PDF
- Behavior: 系统执行或展示[PDF]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB26 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB26 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB26 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB26 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB26 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-026 独立立项、实现和验收。

### STAT-027 — configurable report

- ID: STAT-027
- Source: SRS V1.4 AB27
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: configurable report
- Behavior: 系统执行或展示[configurable report]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AB27 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB27 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB27 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB27 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AB27 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 STAT-027 独立立项、实现和验收。

### CFG-001 — BusinessType

- ID: CFG-001
- Source: SRS V1.4 AC01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: BusinessType
- Behavior: 系统执行或展示[BusinessType]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AC01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC01 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CFG-002 — PathologyNumberRule

- ID: CFG-002
- Source: SRS V1.4 AC02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: PathologyNumberRule
- Behavior: 系统执行或展示[PathologyNumberRule]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AC02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC02 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CFG-003 — ApplicationItemMapping

- ID: CFG-003
- Source: SRS V1.4 AC03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: ApplicationItemMapping
- Behavior: 系统执行或展示[ApplicationItemMapping]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AC03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC03 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CFG-004 — TechnicalProject

- ID: CFG-004
- Source: SRS V1.4 AC04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: TechnicalProject
- Behavior: 系统执行或展示[TechnicalProject]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AC04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC04 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CFG-005 — FeeItemMapping

- ID: CFG-005
- Source: SRS V1.4 AC05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: FeeItemMapping
- Behavior: 系统执行或展示[FeeItemMapping]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AC05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC05 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CFG-005 独立立项、实现和验收。

### CFG-006 — DiagnosisTemplate

- ID: CFG-006
- Source: SRS V1.4 AC06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: DiagnosisTemplate
- Behavior: 系统执行或展示[DiagnosisTemplate]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AC06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC06 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CFG-007 — ReportTemplate

- ID: CFG-007
- Source: SRS V1.4 AC07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: ReportTemplate
- Behavior: 系统执行或展示[ReportTemplate]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AC07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC07 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CFG-008 — TAT policy

- ID: CFG-008
- Source: SRS V1.4 AC08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: TAT policy
- Behavior: 系统执行或展示[TAT policy]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AC08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC08 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 无。管理员可按业务类型配置提醒分钟、目标分钟和启用状态，服务端校验阈值、按医院隔离、递增配置版本并记录审计。
- V2 Decision: 起点采用病例登记时间；具体阈值待各医院业务确认，因此迁移不写入默认临床阈值，未显式启用时不产生提醒。

### CFG-009 — dictionaries

- ID: CFG-009
- Source: SRS V1.4 AC09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: dictionaries
- Behavior: 系统执行或展示[dictionaries]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AC09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC09 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CFG-009 独立立项、实现和验收。

### CFG-010 — hospitals

- ID: CFG-010
- Source: SRS V1.4 AC10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: hospitals
- Behavior: 系统执行或展示[hospitals]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AC10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC10 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CFG-011 — campuses

- ID: CFG-011
- Source: SRS V1.4 AC11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: campuses
- Behavior: 系统执行或展示[campuses]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AC11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC11 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CFG-012 — departments

- ID: CFG-012
- Source: SRS V1.4 AC12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: departments
- Behavior: 系统执行或展示[departments]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AC12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC12 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CFG-013 — clinicians

- ID: CFG-013
- Source: SRS V1.4 AC13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: clinicians
- Behavior: 系统执行或展示[clinicians]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AC13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC13 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CFG-014 — experts

- ID: CFG-014
- Source: SRS V1.4 AC14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: experts
- Behavior: 系统执行或展示[experts]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AC14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC14 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CFG-014 独立立项、实现和验收。

### CFG-015 — subspecialties

- ID: CFG-015
- Source: SRS V1.4 AC15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: subspecialties
- Behavior: 系统执行或展示[subspecialties]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AC15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC15 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CFG-015 独立立项、实现和验收。

### CFG-016 — Case library configuration

- ID: CFG-016
- Source: SRS V1.4 AC16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Case library configuration
- Behavior: 系统执行或展示[Case library configuration]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AC16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC16 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CFG-016 独立立项、实现和验收。

### CFG-017 — printing configuration

- ID: CFG-017
- Source: SRS V1.4 AC17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: printing configuration
- Behavior: 系统执行或展示[printing configuration]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AC17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC17 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CFG-018 — QC configuration

- ID: CFG-018
- Source: SRS V1.4 AC18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: QC configuration
- Behavior: 系统执行或展示[QC configuration]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AC18 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC18 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC18 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC18 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC18 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 CFG-018 独立立项、实现和验收。

### CFG-019 — available actions

- ID: CFG-019
- Source: SRS V1.4 AC19
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: available actions
- Behavior: 系统执行或展示[available actions]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AC19 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC19 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC19 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC19 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC19 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### CFG-020 — feature capabilities

- ID: CFG-020
- Source: SRS V1.4 AC20
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: feature capabilities
- Behavior: 系统执行或展示[feature capabilities]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AC20 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC20 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC20 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC20 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AC20 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### INT-001 — HIS

- ID: INT-001
- Source: SRS V1.4 AD01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: HIS
- Behavior: 系统执行或展示[HIS]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD01 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-001 独立立项、实现和验收。

### INT-002 — LIS

- ID: INT-002
- Source: SRS V1.4 AD02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: LIS
- Behavior: 系统执行或展示[LIS]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD02 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-002 独立立项、实现和验收。

### INT-003 — PACS

- ID: INT-003
- Source: SRS V1.4 AD03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: PACS
- Behavior: 系统执行或展示[PACS]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD03 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-003 独立立项、实现和验收。

### INT-004 — EMR

- ID: INT-004
- Source: SRS V1.4 AD04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: EMR
- Behavior: 系统执行或展示[EMR]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD04 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-004 独立立项、实现和验收。

### INT-005 — anesthesia

- ID: INT-005
- Source: SRS V1.4 AD05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: anesthesia
- Behavior: 系统执行或展示[anesthesia]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD05 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-005 独立立项、实现和验收。

### INT-006 — health examination

- ID: INT-006
- Source: SRS V1.4 AD06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: health examination
- Behavior: 系统执行或展示[health examination]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD06 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-006 独立立项、实现和验收。

### INT-007 — integration platform

- ID: INT-007
- Source: SRS V1.4 AD07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: integration platform
- Behavior: 系统执行或展示[integration platform]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD07 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-007 独立立项、实现和验收。

### INT-008 — master data

- ID: INT-008
- Source: SRS V1.4 AD08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: master data
- Behavior: 系统执行或展示[master data]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD08 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-008 独立立项、实现和验收。

### INT-009 — patient

- ID: INT-009
- Source: SRS V1.4 AD09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: patient
- Behavior: 系统执行或展示[patient]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD09 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-009 独立立项、实现和验收。

### INT-010 — visit

- ID: INT-010
- Source: SRS V1.4 AD10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: visit
- Behavior: 系统执行或展示[visit]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD10 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-010 独立立项、实现和验收。

### INT-011 — department

- ID: INT-011
- Source: SRS V1.4 AD11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: department
- Behavior: 系统执行或展示[department]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD11 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-011 独立立项、实现和验收。

### INT-012 — ward

- ID: INT-012
- Source: SRS V1.4 AD12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: ward
- Behavior: 系统执行或展示[ward]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD12 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-012 独立立项、实现和验收。

### INT-013 — doctor

- ID: INT-013
- Source: SRS V1.4 AD13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: doctor
- Behavior: 系统执行或展示[doctor]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD13 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-013 独立立项、实现和验收。

### INT-014 — order item

- ID: INT-014
- Source: SRS V1.4 AD14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: order item
- Behavior: 系统执行或展示[order item]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD14 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-014 独立立项、实现和验收。

### INT-015 — fee item

- ID: INT-015
- Source: SRS V1.4 AD15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: fee item
- Behavior: 系统执行或展示[fee item]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD15 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-015 独立立项、实现和验收。

### INT-016 — application create

- ID: INT-016
- Source: SRS V1.4 AD16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: application create
- Behavior: 系统执行或展示[application create]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD16 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-016 独立立项、实现和验收。

### INT-017 — application update

- ID: INT-017
- Source: SRS V1.4 AD17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: application update
- Behavior: 系统执行或展示[application update]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD17 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-017 独立立项、实现和验收。

### INT-018 — application cancel

- ID: INT-018
- Source: SRS V1.4 AD18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: application cancel
- Behavior: 系统执行或展示[application cancel]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD18 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD18 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD18 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD18 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD18 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-018 独立立项、实现和验收。

### INT-019 — registration acknowledgement

- ID: INT-019
- Source: SRS V1.4 AD19
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: registration acknowledgement
- Behavior: 系统执行或展示[registration acknowledgement]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD19 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD19 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD19 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD19 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD19 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-019 独立立项、实现和验收。

### INT-020 — specimen status

- ID: INT-020
- Source: SRS V1.4 AD20
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: specimen status
- Behavior: 系统执行或展示[specimen status]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD20 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD20 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD20 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD20 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD20 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-020 独立立项、实现和验收。

### INT-021 — diagnosis status

- ID: INT-021
- Source: SRS V1.4 AD21
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: diagnosis status
- Behavior: 系统执行或展示[diagnosis status]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD21 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD21 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD21 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD21 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD21 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-021 独立立项、实现和验收。

### INT-022 — report status

- ID: INT-022
- Source: SRS V1.4 AD22
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: report status
- Behavior: 系统执行或展示[report status]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD22 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD22 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD22 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD22 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD22 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-022 独立立项、实现和验收。

### INT-023 — diagnosis content

- ID: INT-023
- Source: SRS V1.4 AD23
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: diagnosis content
- Behavior: 系统执行或展示[diagnosis content]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD23 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD23 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD23 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD23 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD23 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-023 独立立项、实现和验收。

### INT-024 — report PDF

- ID: INT-024
- Source: SRS V1.4 AD24
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: report PDF
- Behavior: 系统执行或展示[report PDF]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD24 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD24 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD24 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD24 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD24 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-024 独立立项、实现和验收。

### INT-025 — critical value

- ID: INT-025
- Source: SRS V1.4 AD25
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: critical value
- Behavior: 系统执行或展示[critical value]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD25 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD25 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD25 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD25 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD25 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-025 独立立项、实现和验收。

### INT-026 — fee confirmation

- ID: INT-026
- Source: SRS V1.4 AD26
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: fee confirmation
- Behavior: 系统执行或展示[fee confirmation]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD26 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD26 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD26 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD26 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD26 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-026 独立立项、实现和验收。

### INT-027 — fee cancellation/refund

- ID: INT-027
- Source: SRS V1.4 AD27
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: fee cancellation/refund
- Behavior: 系统执行或展示[fee cancellation/refund]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AD27 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD27 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD27 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD27 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AD27 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 INT-027 独立立项、实现和验收。

### SEC-001 — CA login

- ID: SEC-001
- Source: SRS V1.4 AE01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: CA login
- Behavior: 系统执行或展示[CA login]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AE01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE01 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-001 独立立项、实现和验收。

### SEC-002 — CA certificate

- ID: SEC-002
- Source: SRS V1.4 AE02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: CA certificate
- Behavior: 系统执行或展示[CA certificate]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AE02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE02 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-002 独立立项、实现和验收。

### SEC-003 — electronic signature

- ID: SEC-003
- Source: SRS V1.4 AE03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: electronic signature
- Behavior: 系统执行或展示[electronic signature]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AE03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE03 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-003 独立立项、实现和验收。

### SEC-004 — report signing

- ID: SEC-004
- Source: SRS V1.4 AE04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: report signing
- Behavior: 系统执行或展示[report signing]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AE04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE04 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-004 独立立项、实现和验收。

### SEC-005 — signature verification

- ID: SEC-005
- Source: SRS V1.4 AE05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: signature verification
- Behavior: 系统执行或展示[signature verification]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AE05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE05 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-005 独立立项、实现和验收。

### SEC-006 — audit

- ID: SEC-006
- Source: SRS V1.4 AE06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: audit
- Behavior: 系统执行或展示[audit]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AE06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE06 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-006 独立立项、实现和验收。

### SEC-007 — PDF signing

- ID: SEC-007
- Source: SRS V1.4 AE07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: PDF signing
- Behavior: 系统执行或展示[PDF signing]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AE07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE07 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-007 独立立项、实现和验收。

### SEC-008 — authorization change signature extension point

- ID: SEC-008
- Source: SRS V1.4 AE08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: authorization change signature extension point
- Behavior: 系统执行或展示[authorization change signature extension point]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AE08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AE08 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-008 独立立项、实现和验收。

### REGION-001 — 湖北省病理平台

- ID: REGION-001
- Source: SRS V1.4 AF01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 湖北省病理平台
- Behavior: 系统执行或展示[湖北省病理平台]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AF01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF01 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-001 独立立项、实现和验收。

### REGION-002 — 病例上传

- ID: REGION-002
- Source: SRS V1.4 AF02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 病例上传
- Behavior: 系统执行或展示[病例上传]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AF02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF02 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-002 独立立项、实现和验收。

### REGION-003 — 诊断上传

- ID: REGION-003
- Source: SRS V1.4 AF03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 诊断上传
- Behavior: 系统执行或展示[诊断上传]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AF03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF03 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-003 独立立项、实现和验收。

### REGION-004 — IHC order

- ID: REGION-004
- Source: SRS V1.4 AF04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: IHC order
- Behavior: 系统执行或展示[IHC order]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AF04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF04 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-004 独立立项、实现和验收。

### REGION-005 — consultation

- ID: REGION-005
- Source: SRS V1.4 AF05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: consultation
- Behavior: 系统执行或展示[consultation]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AF05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF05 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-005 独立立项、实现和验收。

### REGION-006 — consultation result

- ID: REGION-006
- Source: SRS V1.4 AF06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: consultation result
- Behavior: 系统执行或展示[consultation result]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AF06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF06 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-006 独立立项、实现和验收。

### REGION-007 — consultation cancellation

- ID: REGION-007
- Source: SRS V1.4 AF07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: consultation cancellation
- Behavior: 系统执行或展示[consultation cancellation]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AF07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF07 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-007 独立立项、实现和验收。

### REGION-008 — validation result

- ID: REGION-008
- Source: SRS V1.4 AF08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: validation result
- Behavior: 系统执行或展示[validation result]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AF08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF08 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-008 独立立项、实现和验收。

### REGION-009 — upload retry

- ID: REGION-009
- Source: SRS V1.4 AF09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: upload retry
- Behavior: 系统执行或展示[upload retry]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AF09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF09 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-009 独立立项、实现和验收。

### REGION-010 — upload log

- ID: REGION-010
- Source: SRS V1.4 AF10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: upload log
- Behavior: 系统执行或展示[upload log]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AF10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF10 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-010 独立立项、实现和验收。

### REGION-011 — 武汉市区域病理中心

- ID: REGION-011
- Source: SRS V1.4 AF11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 武汉市区域病理中心
- Behavior: 系统执行或展示[武汉市区域病理中心]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AF11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF11 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-011 独立立项、实现和验收。

### REGION-012 — case data

- ID: REGION-012
- Source: SRS V1.4 AF12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: case data
- Behavior: 系统执行或展示[case data]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AF12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF12 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-012 独立立项、实现和验收。

### REGION-013 — consultation

- ID: REGION-013
- Source: SRS V1.4 AF13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: consultation
- Behavior: 系统执行或展示[consultation]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AF13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF13 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-013 独立立项、实现和验收。

### REGION-014 — cancellation

- ID: REGION-014
- Source: SRS V1.4 AF14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: cancellation
- Behavior: 系统执行或展示[cancellation]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AF14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF14 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-014 独立立项、实现和验收。

### REGION-015 — diagnosis feedback

- ID: REGION-015
- Source: SRS V1.4 AF15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: diagnosis feedback
- Behavior: 系统执行或展示[diagnosis feedback]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AF15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF15 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-015 独立立项、实现和验收。

### REGION-016 — patient holistic view extension

- ID: REGION-016
- Source: SRS V1.4 AF16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: patient holistic view extension
- Behavior: 系统执行或展示[patient holistic view extension]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AF16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF16 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-016 独立立项、实现和验收。

### REGION-017 — health-record extension

- ID: REGION-017
- Source: SRS V1.4 AF17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: health-record extension
- Behavior: 系统执行或展示[health-record extension]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AF17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AF17 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-017 独立立项、实现和验收。

### COLLECT-001 — multi-system collection

- ID: COLLECT-001
- Source: SRS V1.4 AG01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: multi-system collection
- Behavior: 系统执行或展示[multi-system collection]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG01 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-001 独立立项、实现和验收。

### COLLECT-002 — scheduled collection

- ID: COLLECT-002
- Source: SRS V1.4 AG02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: scheduled collection
- Behavior: 系统执行或展示[scheduled collection]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG02 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-002 独立立项、实现和验收。

### COLLECT-003 — manual collection

- ID: COLLECT-003
- Source: SRS V1.4 AG03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: manual collection
- Behavior: 系统执行或展示[manual collection]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG03 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-003 独立立项、实现和验收。

### COLLECT-004 — progress

- ID: COLLECT-004
- Source: SRS V1.4 AG04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: progress
- Behavior: 系统执行或展示[progress]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG04 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-004 独立立项、实现和验收。

### COLLECT-005 — result

- ID: COLLECT-005
- Source: SRS V1.4 AG05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: result
- Behavior: 系统执行或展示[result]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG05 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-005 独立立项、实现和验收。

### COLLECT-006 — failed records

- ID: COLLECT-006
- Source: SRS V1.4 AG06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: failed records
- Behavior: 系统执行或展示[failed records]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG06 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-006 独立立项、实现和验收。

### COLLECT-007 — raw dataset

- ID: COLLECT-007
- Source: SRS V1.4 AG07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: raw dataset
- Behavior: 系统执行或展示[raw dataset]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG07 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-007 独立立项、实现和验收。

### COLLECT-008 — deduplication

- ID: COLLECT-008
- Source: SRS V1.4 AG08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: deduplication
- Behavior: 系统执行或展示[deduplication]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG08 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-008 独立立项、实现和验收。

### COLLECT-009 — correction

- ID: COLLECT-009
- Source: SRS V1.4 AG09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: correction
- Behavior: 系统执行或展示[correction]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG09 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-009 独立立项、实现和验收。

### COLLECT-010 — field normalization

- ID: COLLECT-010
- Source: SRS V1.4 AG10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: field normalization
- Behavior: 系统执行或展示[field normalization]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG10 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-010 独立立项、实现和验收。

### COLLECT-011 — ICD-10 mapping

- ID: COLLECT-011
- Source: SRS V1.4 AG11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: ICD-10 mapping
- Behavior: 系统执行或展示[ICD-10 mapping]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG11 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-011 独立立项、实现和验收。

### COLLECT-012 — ICD-9-CM-3 mapping

- ID: COLLECT-012
- Source: SRS V1.4 AG12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: ICD-9-CM-3 mapping
- Behavior: 系统执行或展示[ICD-9-CM-3 mapping]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG12 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-012 独立立项、实现和验收。

### COLLECT-013 — data validation

- ID: COLLECT-013
- Source: SRS V1.4 AG13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: data validation
- Behavior: 系统执行或展示[data validation]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG13 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-013 独立立项、实现和验收。

### COLLECT-014 — logical validation

- ID: COLLECT-014
- Source: SRS V1.4 AG14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: logical validation
- Behavior: 系统执行或展示[logical validation]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG14 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-014 独立立项、实现和验收。

### COLLECT-015 — abnormal data

- ID: COLLECT-015
- Source: SRS V1.4 AG15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: abnormal data
- Behavior: 系统执行或展示[abnormal data]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG15 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-015 独立立项、实现和验收。

### COLLECT-016 — manual correction

- ID: COLLECT-016
- Source: SRS V1.4 AG16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: manual correction
- Behavior: 系统执行或展示[manual correction]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG16 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-016 独立立项、实现和验收。

### COLLECT-017 — correction audit

- ID: COLLECT-017
- Source: SRS V1.4 AG17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: correction audit
- Behavior: 系统执行或展示[correction audit]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG17 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-017 独立立项、实现和验收。

### COLLECT-018 — reporting format configuration

- ID: COLLECT-018
- Source: SRS V1.4 AG18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: reporting format configuration
- Behavior: 系统执行或展示[reporting format configuration]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG18 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG18 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG18 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG18 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG18 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-018 独立立项、实现和验收。

### COLLECT-019 — reporting fields

- ID: COLLECT-019
- Source: SRS V1.4 AG19
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: reporting fields
- Behavior: 系统执行或展示[reporting fields]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG19 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG19 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG19 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG19 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG19 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-019 独立立项、实现和验收。

### COLLECT-020 — reporting filter

- ID: COLLECT-020
- Source: SRS V1.4 AG20
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: reporting filter
- Behavior: 系统执行或展示[reporting filter]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG20 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG20 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG20 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG20 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG20 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-020 独立立项、实现和验收。

### COLLECT-021 — reporting file

- ID: COLLECT-021
- Source: SRS V1.4 AG21
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: reporting file
- Behavior: 系统执行或展示[reporting file]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG21 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG21 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG21 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG21 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG21 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-021 独立立项、实现和验收。

### COLLECT-022 — task scheduler

- ID: COLLECT-022
- Source: SRS V1.4 AG22
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: task scheduler
- Behavior: 系统执行或展示[task scheduler]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG22 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG22 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG22 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG22 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG22 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-022 独立立项、实现和验收。

### COLLECT-023 — execution cycle

- ID: COLLECT-023
- Source: SRS V1.4 AG23
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: execution cycle
- Behavior: 系统执行或展示[execution cycle]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG23 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG23 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG23 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG23 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG23 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-023 独立立项、实现和验收。

### COLLECT-024 — priority

- ID: COLLECT-024
- Source: SRS V1.4 AG24
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: priority
- Behavior: 系统执行或展示[priority]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG24 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG24 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG24 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG24 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG24 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-024 独立立项、实现和验收。

### COLLECT-025 — pause

- ID: COLLECT-025
- Source: SRS V1.4 AG25
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: pause
- Behavior: 系统执行或展示[pause]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG25 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG25 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG25 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG25 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG25 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-025 独立立项、实现和验收。

### COLLECT-026 — restart

- ID: COLLECT-026
- Source: SRS V1.4 AG26
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: restart
- Behavior: 系统执行或展示[restart]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG26 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG26 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG26 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG26 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG26 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-026 独立立项、实现和验收。

### COLLECT-027 — retry

- ID: COLLECT-027
- Source: SRS V1.4 AG27
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: retry
- Behavior: 系统执行或展示[retry]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG27 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG27 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG27 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG27 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG27 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-027 独立立项、实现和验收。

### COLLECT-028 — pre-audit

- ID: COLLECT-028
- Source: SRS V1.4 AG28
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: pre-audit
- Behavior: 系统执行或展示[pre-audit]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG28 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG28 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG28 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG28 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG28 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-028 独立立项、实现和验收。

### COLLECT-029 — error location

- ID: COLLECT-029
- Source: SRS V1.4 AG29
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: error location
- Behavior: 系统执行或展示[error location]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG29 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG29 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG29 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG29 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG29 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-029 独立立项、实现和验收。

### COLLECT-030 — correction suggestions

- ID: COLLECT-030
- Source: SRS V1.4 AG30
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: correction suggestions
- Behavior: 系统执行或展示[correction suggestions]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG30 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG30 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG30 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG30 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG30 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-030 独立立项、实现和验收。

### COLLECT-031 — revalidation

- ID: COLLECT-031
- Source: SRS V1.4 AG31
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: revalidation
- Behavior: 系统执行或展示[revalidation]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG31 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG31 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG31 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG31 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG31 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-031 独立立项、实现和验收。

### COLLECT-032 — secure transmission

- ID: COLLECT-032
- Source: SRS V1.4 AG32
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: secure transmission
- Behavior: 系统执行或展示[secure transmission]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG32 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG32 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG32 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG32 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG32 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-032 独立立项、实现和验收。

### COLLECT-033 — encryption

- ID: COLLECT-033
- Source: SRS V1.4 AG33
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: encryption
- Behavior: 系统执行或展示[encryption]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG33 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG33 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG33 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG33 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG33 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-033 独立立项、实现和验收。

### COLLECT-034 — resume upload

- ID: COLLECT-034
- Source: SRS V1.4 AG34
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: resume upload
- Behavior: 系统执行或展示[resume upload]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG34 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG34 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG34 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG34 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG34 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-034 独立立项、实现和验收。

### COLLECT-035 — integrity validation

- ID: COLLECT-035
- Source: SRS V1.4 AG35
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: integrity validation
- Behavior: 系统执行或展示[integrity validation]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG35 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG35 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG35 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG35 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG35 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-035 独立立项、实现和验收。

### COLLECT-036 — transfer audit

- ID: COLLECT-036
- Source: SRS V1.4 AG36
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: transfer audit
- Behavior: 系统执行或展示[transfer audit]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AG36 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG36 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG36 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG36 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AG36 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 COLLECT-036 独立立项、实现和验收。

### ARCH-016 — scanner adapters

- ID: ARCH-016
- Source: SRS V1.4 AH01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: scanner adapters
- Behavior: 系统执行或展示[scanner adapters]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH01 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-016 独立立项、实现和验收。

### ARCH-017 — slide import

- ID: ARCH-017
- Source: SRS V1.4 AH02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: slide import
- Behavior: 系统执行或展示[slide import]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH02 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-017 独立立项、实现和验收。

### ARCH-018 — WSI format recognition

- ID: ARCH-018
- Source: SRS V1.4 AH03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: WSI format recognition
- Behavior: 系统执行或展示[WSI format recognition]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH03 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-018 独立立项、实现和验收。

### ARCH-019 — MRXS

- ID: ARCH-019
- Source: SRS V1.4 AH04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: MRXS
- Behavior: 系统执行或展示[MRXS]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH04 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-019 独立立项、实现和验收。

### ARCH-020 — NDPI

- ID: ARCH-020
- Source: SRS V1.4 AH05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: NDPI
- Behavior: 系统执行或展示[NDPI]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH05 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-020 独立立项、实现和验收。

### ARCH-021 — SVS

- ID: ARCH-021
- Source: SRS V1.4 AH06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: SVS
- Behavior: 系统执行或展示[SVS]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH06 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-021 独立立项、实现和验收。

### ARCH-022 — other vendor format adapter

- ID: ARCH-022
- Source: SRS V1.4 AH07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: other vendor format adapter
- Behavior: 系统执行或展示[other vendor format adapter]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH07 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-022 独立立项、实现和验收。

### ARCH-023 — storage path

- ID: ARCH-023
- Source: SRS V1.4 AH08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: storage path
- Behavior: 系统执行或展示[storage path]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH08 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-023 独立立项、实现和验收。

### ARCH-024 — storage tier

- ID: ARCH-024
- Source: SRS V1.4 AH09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: storage tier
- Behavior: 系统执行或展示[storage tier]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH09 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-024 独立立项、实现和验收。

### ARCH-025 — filename rule

- ID: ARCH-025
- Source: SRS V1.4 AH10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: filename rule
- Behavior: 系统执行或展示[filename rule]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH10 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-025 独立立项、实现和验收。

### ARCH-026 — index

- ID: ARCH-026
- Source: SRS V1.4 AH11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: index
- Behavior: 系统执行或展示[index]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH11 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-026 独立立项、实现和验收。

### ARCH-027 — search

- ID: ARCH-027
- Source: SRS V1.4 AH12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: search
- Behavior: 系统执行或展示[search]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH12 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-027 独立立项、实现和验收。

### ARCH-028 — pathology number

- ID: ARCH-028
- Source: SRS V1.4 AH13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: pathology number
- Behavior: 系统执行或展示[pathology number]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH13 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-028 独立立项、实现和验收。

### ARCH-029 — slide number

- ID: ARCH-029
- Source: SRS V1.4 AH14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: slide number
- Behavior: 系统执行或展示[slide number]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH14 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-029 独立立项、实现和验收。

### ARCH-030 — patient

- ID: ARCH-030
- Source: SRS V1.4 AH15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: patient
- Behavior: 系统执行或展示[patient]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH15 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-030 独立立项、实现和验收。

### ARCH-031 — organ

- ID: ARCH-031
- Source: SRS V1.4 AH16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: organ
- Behavior: 系统执行或展示[organ]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH16 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-031 独立立项、实现和验收。

### ARCH-032 — PIS binding

- ID: ARCH-032
- Source: SRS V1.4 AH17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: PIS binding
- Behavior: 系统执行或展示[PIS binding]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH17 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### ARCH-033 — integrity check

- ID: ARCH-033
- Source: SRS V1.4 AH18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: integrity check
- Behavior: 系统执行或展示[integrity check]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH18 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH18 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH18 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH18 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH18 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-033 独立立项、实现和验收。

### ARCH-034 — archive

- ID: ARCH-034
- Source: SRS V1.4 AH19
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: archive
- Behavior: 系统执行或展示[archive]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH19 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH19 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH19 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH19 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH19 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-034 独立立项、实现和验收。

### ARCH-035 — restore

- ID: ARCH-035
- Source: SRS V1.4 AH20
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: restore
- Behavior: 系统执行或展示[restore]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH20 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH20 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH20 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH20 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH20 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 ARCH-035 独立立项、实现和验收。

### ARCH-036 — remote viewing

- ID: ARCH-036
- Source: SRS V1.4 AH21
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: remote viewing
- Behavior: 系统执行或展示[remote viewing]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AH21 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH21 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH21 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH21 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AH21 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### AI-001 — AI Provider Port

- ID: AI-001
- Source: SRS V1.4 AI01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: AI Provider Port
- Behavior: 系统执行或展示[AI Provider Port]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AI01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI01 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 AI-001 独立立项、实现和验收。

### AI-002 — analysis request

- ID: AI-002
- Source: SRS V1.4 AI02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: analysis request
- Behavior: 系统执行或展示[analysis request]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AI02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI02 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 AI-002 独立立项、实现和验收。

### AI-003 — analysis result

- ID: AI-003
- Source: SRS V1.4 AI03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: analysis result
- Behavior: 系统执行或展示[analysis result]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AI03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI03 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 AI-003 独立立项、实现和验收。

### AI-004 — model metadata

- ID: AI-004
- Source: SRS V1.4 AI04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: model metadata
- Behavior: 系统执行或展示[model metadata]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AI04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI04 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 AI-004 独立立项、实现和验收。

### AI-005 — model version

- ID: AI-005
- Source: SRS V1.4 AI05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: model version
- Behavior: 系统执行或展示[model version]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AI05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI05 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 AI-005 独立立项、实现和验收。

### AI-006 — confidence

- ID: AI-006
- Source: SRS V1.4 AI06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: confidence
- Behavior: 系统执行或展示[confidence]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AI06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI06 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 AI-006 独立立项、实现和验收。

### AI-007 — lesion location

- ID: AI-007
- Source: SRS V1.4 AI07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: lesion location
- Behavior: 系统执行或展示[lesion location]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AI07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI07 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 AI-007 独立立项、实现和验收。

### AI-008 — heatmap

- ID: AI-008
- Source: SRS V1.4 AI08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: heatmap
- Behavior: 系统执行或展示[heatmap]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AI08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI08 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 AI-008 独立立项、实现和验收。

### AI-009 — overlay

- ID: AI-009
- Source: SRS V1.4 AI09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: overlay
- Behavior: 系统执行或展示[overlay]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AI09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI09 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 AI-009 独立立项、实现和验收。

### AI-010 — result history

- ID: AI-010
- Source: SRS V1.4 AI10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: result history
- Behavior: 系统执行或展示[result history]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AI10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI10 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 AI-010 独立立项、实现和验收。

### AI-011 — rerun

- ID: AI-011
- Source: SRS V1.4 AI11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: rerun
- Behavior: 系统执行或展示[rerun]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AI11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI11 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 AI-011 独立立项、实现和验收。

### AI-012 — compare results

- ID: AI-012
- Source: SRS V1.4 AI12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: compare results
- Behavior: 系统执行或展示[compare results]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AI12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI12 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 AI-012 独立立项、实现和验收。

### AI-013 — structured result import

- ID: AI-013
- Source: SRS V1.4 AI13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: structured result import
- Behavior: 系统执行或展示[structured result import]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AI13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI13 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 AI-013 独立立项、实现和验收。

### AI-014 — Viewer integration

- ID: AI-014
- Source: SRS V1.4 AI14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Viewer integration
- Behavior: 系统执行或展示[Viewer integration]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AI14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI14 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 AI-014 独立立项、实现和验收。

### AI-015 — lung provider

- ID: AI-015
- Source: SRS V1.4 AI15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: lung provider
- Behavior: 系统执行或展示[lung provider]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AI15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI15 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 AI-015 独立立项、实现和验收。

### AI-016 — gastric provider

- ID: AI-016
- Source: SRS V1.4 AI16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: gastric provider
- Behavior: 系统执行或展示[gastric provider]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AI16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI16 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 AI-016 独立立项、实现和验收。

### AI-017 — colorectal provider

- ID: AI-017
- Source: SRS V1.4 AI17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: colorectal provider
- Behavior: 系统执行或展示[colorectal provider]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AI17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI17 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 AI-017 独立立项、实现和验收。

### AI-018 — prostate provider

- ID: AI-018
- Source: SRS V1.4 AI18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: prostate provider
- Behavior: 系统执行或展示[prostate provider]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AI18 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI18 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI18 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI18 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AI18 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 AI-018 独立立项、实现和验收。

### REGION-018 — organization

- ID: REGION-018
- Source: SRS V1.4 AJ01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: organization
- Behavior: 系统执行或展示[organization]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AJ01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ01 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-018 独立立项、实现和验收。

### REGION-019 — external pathology organization

- ID: REGION-019
- Source: SRS V1.4 AJ02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: external pathology organization
- Behavior: 系统执行或展示[external pathology organization]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AJ02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ02 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-019 独立立项、实现和验收。

### REGION-020 — consultation doctor

- ID: REGION-020
- Source: SRS V1.4 AJ03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: consultation doctor
- Behavior: 系统执行或展示[consultation doctor]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AJ03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ03 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-020 独立立项、实现和验收。

### REGION-021 — consultation permissions

- ID: REGION-021
- Source: SRS V1.4 AJ04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: consultation permissions
- Behavior: 系统执行或展示[consultation permissions]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AJ04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ04 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-021 独立立项、实现和验收。

### REGION-022 — shared case

- ID: REGION-022
- Source: SRS V1.4 AJ05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: shared case
- Behavior: 系统执行或展示[shared case]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AJ05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ05 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-022 独立立项、实现和验收。

### REGION-023 — WSI sharing

- ID: REGION-023
- Source: SRS V1.4 AJ06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: WSI sharing
- Behavior: 系统执行或展示[WSI sharing]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AJ06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ06 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-023 独立立项、实现和验收。

### REGION-024 — patient-authorized distribution

- ID: REGION-024
- Source: SRS V1.4 AJ07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: patient-authorized distribution
- Behavior: 系统执行或展示[patient-authorized distribution]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AJ07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ07 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-024 独立立项、实现和验收。

### REGION-025 — receiving organization

- ID: REGION-025
- Source: SRS V1.4 AJ08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: receiving organization
- Behavior: 系统执行或展示[receiving organization]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AJ08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ08 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-025 独立立项、实现和验收。

### REGION-026 — expiration

- ID: REGION-026
- Source: SRS V1.4 AJ09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: expiration
- Behavior: 系统执行或展示[expiration]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AJ09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ09 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-026 独立立项、实现和验收。

### REGION-027 — access log

- ID: REGION-027
- Source: SRS V1.4 AJ10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: access log
- Behavior: 系统执行或展示[access log]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AJ10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ10 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-027 独立立项、实现和验收。

### REGION-028 — notification

- ID: REGION-028
- Source: SRS V1.4 AJ11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: notification
- Behavior: 系统执行或展示[notification]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AJ11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ11 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-028 独立立项、实现和验收。

### REGION-029 — workload

- ID: REGION-029
- Source: SRS V1.4 AJ12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: workload
- Behavior: 系统执行或展示[workload]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AJ12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ12 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-029 独立立项、实现和验收。

### REGION-030 — fee/settlement extension

- ID: REGION-030
- Source: SRS V1.4 AJ13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: fee/settlement extension
- Behavior: 系统执行或展示[fee/settlement extension]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AJ13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ13 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-030 独立立项、实现和验收。

### REGION-031 — regional statistics

- ID: REGION-031
- Source: SRS V1.4 AJ14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: regional statistics
- Behavior: 系统执行或展示[regional statistics]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AJ14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AJ14 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 REGION-031 独立立项、实现和验收。

### RPT-026 — HIS

- ID: RPT-026
- Source: SRS V1.4 AK01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: HIS
- Behavior: 系统执行或展示[HIS]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AK01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK01 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 RPT-026 独立立项、实现和验收。

### RPT-027 — EMR

- ID: RPT-027
- Source: SRS V1.4 AK02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: EMR
- Behavior: 系统执行或展示[EMR]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AK02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK02 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 RPT-027 独立立项、实现和验收。

### RPT-028 — clinician query

- ID: RPT-028
- Source: SRS V1.4 AK03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: clinician query
- Behavior: 系统执行或展示[clinician query]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AK03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK03 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 RPT-028 独立立项、实现和验收。

### RPT-029 — patient query

- ID: RPT-029
- Source: SRS V1.4 AK04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: patient query
- Behavior: 系统执行或展示[patient query]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AK04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK04 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 RPT-029 独立立项、实现和验收。

### RPT-030 — APP extension

- ID: RPT-030
- Source: SRS V1.4 AK05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: APP extension
- Behavior: 系统执行或展示[APP extension]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AK05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK05 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 RPT-030 独立立项、实现和验收。

### RPT-031 — WeChat extension

- ID: RPT-031
- Source: SRS V1.4 AK06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: WeChat extension
- Behavior: 系统执行或展示[WeChat extension]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AK06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK06 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 RPT-031 独立立项、实现和验收。

### RPT-032 — self-service terminal

- ID: RPT-032
- Source: SRS V1.4 AK07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: self-service terminal
- Behavior: 系统执行或展示[self-service terminal]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AK07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK07 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 RPT-032 独立立项、实现和验收。

### RPT-033 — self-service print

- ID: RPT-033
- Source: SRS V1.4 AK08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: self-service print
- Behavior: 系统执行或展示[self-service print]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: 同 RPT-024 的报告自助打印入口；终端和身份核验均为必填。
- Backend Evidence: 有效报告锁、病例身份引用匹配、`ReportOutputPort`、幂等和审计形成完整命令边界。
- DB Evidence: `report_print_record` 保存每次成功或失败尝试，不覆盖历史。
- Frontend Evidence: 自助打印表单展示打印机状态和真实服务端执行结果。
- Test Evidence: `V2BusinessOperationsSecurityTest`、`V2ClinicalOperations.test.ts`、`report-output.spec.ts`。
- Status: COMPLETE
- Gap: 无当前已知产品内缺口；真实硬件联调不伪装为 Simulator 成功。
- V2 Decision: AK08 与 N24 复用同一自助打印业务能力，不复制第二套打印模型。

### RPT-034 — OR frozen delivery

- ID: RPT-034
- Source: SRS V1.4 AK09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: OR frozen delivery
- Behavior: 系统执行或展示[OR frozen delivery]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AK09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK09 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 RPT-034 独立立项、实现和验收。

### RPT-035 — delayed report

- ID: RPT-035
- Source: SRS V1.4 AK10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: delayed report
- Behavior: 系统执行或展示[delayed report]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AK10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AK10 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 无。临期或超期待签发报告可登记受控原因、说明和预计签发时间，支持幂等关闭；策略版本和当时目标时间随记录保留。
- V2 Decision: 延迟登记是报告域业务事实而非通用WorkItem；同一诊断只允许一个活动登记，人工关闭保留原因，正式签发在同一事务中自动关闭活动登记并保留审计。

### RPT-036 — delivery history

- ID: RPT-036
- Source: SRS V1.4 AK11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: delivery history
- Behavior: 系统执行或展示[delivery history]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: 报告发放入口按报告查询并显示通道、请求时间、状态、失败原因和重试次数。
- Backend Evidence: `reportDistributions` 强制报告组织范围，按请求时间稳定倒序返回追加式历史。
- DB Evidence: `report_distribution` 保存 SENT/FAILED/RETRY_PENDING 全部尝试，V47 增加通道回执和错误码。
- Frontend Evidence: `V2ClinicalOperations.vue` 在发放后立即刷新逐报告历史。
- Test Evidence: 后端安全测试覆盖成功、失败、历史和跨医院范围；Playwright 在 1920/1366 验证历史可见。
- Status: COMPLETE
- Gap: 无产品内历史缺口；外部通道真实性仍由 RPT-025 标记。
- V2 Decision: 发放历史是输出尝试事实，绝不修改 Report 医疗内容或状态。

### RPT-037 — printer status

- ID: RPT-037
- Source: SRS V1.4 AK12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: printer status
- Behavior: 系统执行或展示[printer status]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: 报告自助打印区“检查打印机”。
- Backend Evidence: `ReportOutputPort.printerStatus` 返回 READY 或 UNCONFIGURED；Simulator 可测，未配置真实打印机不会伪报在线。
- DB Evidence: 打印尝试保存设备任务或错误证据；打印机实时状态本身不写入核心医疗表。
- Frontend Evidence: UI 显示端口返回的打印机状态和说明。
- Test Evidence: 后端测试断言 Simulator READY；Playwright 断言状态回显。
- Status: EXTERNAL_DEPENDENCY
- Gap: 产品内端口、错误语义和 Simulator 已有；真实医院报告打印机型号、地址和在线状态协议未提供。
- V2 Decision: 真实硬件状态只能由医院适配器提供，Simulator 不等同生产验证。

### RPT-038 — print history

- ID: RPT-038
- Source: SRS V1.4 AK13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: print history
- Behavior: 系统执行或展示[print history]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: 报告自助打印区按报告查询打印历史，显示时间、终端、份数和 SUCCESS/FAILED。
- Backend Evidence: `reportPrints` 强制权限与医院范围；打印命令只能保存端口返回结果，并支持幂等重放。
- DB Evidence: `report_print_record` 追加保存身份引用、终端、打印机、设备任务、份数和失败原因。
- Frontend Evidence: `V2ClinicalOperations.vue` 打印后刷新历史并明确显示失败说明。
- Test Evidence: 后端测试覆盖成功、拒绝、重放、历史和跨医院拒绝；组件及双视口浏览器测试覆盖入口。
- Status: COMPLETE
- Gap: 无产品内历史缺口；真实打印机联调状态见 RPT-037。
- V2 Decision: 重复命令不产生重复打印记录；业务重打必须使用新的幂等键并追加历史。

### SEC-009 — authentication

- ID: SEC-009
- Source: SRS V1.4 AL01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: authentication
- Behavior: 系统执行或展示[authentication]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AL01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL01 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-010 — authorization

- ID: SEC-010
- Source: SRS V1.4 AL02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: authorization
- Behavior: 系统执行或展示[authorization]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AL02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL02 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-011 — least privilege

- ID: SEC-011
- Source: SRS V1.4 AL03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: least privilege
- Behavior: 系统执行或展示[least privilege]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AL03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL03 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-012 — audit

- ID: SEC-012
- Source: SRS V1.4 AL04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: audit
- Behavior: 系统执行或展示[audit]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AL04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL04 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-013 — sensitive data classification

- ID: SEC-013
- Source: SRS V1.4 AL05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: sensitive data classification
- Behavior: 系统执行或展示[sensitive data classification]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AL05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL05 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-013 独立立项、实现和验收。

### SEC-014 — encryption-at-rest extension

- ID: SEC-014
- Source: SRS V1.4 AL06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: encryption-at-rest extension
- Behavior: 系统执行或展示[encryption-at-rest extension]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AL06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL06 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-014 独立立项、实现和验收。

### SEC-015 — encrypted transport

- ID: SEC-015
- Source: SRS V1.4 AL07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: encrypted transport
- Behavior: 系统执行或展示[encrypted transport]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AL07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL07 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-015 独立立项、实现和验收。

### SEC-016 — masking

- ID: SEC-016
- Source: SRS V1.4 AL08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: masking
- Behavior: 系统执行或展示[masking]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AL08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL08 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-016 独立立项、实现和验收。

### SEC-017 — export control

- ID: SEC-017
- Source: SRS V1.4 AL09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: export control
- Behavior: 系统执行或展示[export control]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AL09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL09 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-017 独立立项、实现和验收。

### SEC-018 — export audit

- ID: SEC-018
- Source: SRS V1.4 AL10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: export audit
- Behavior: 系统执行或展示[export audit]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AL10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL10 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-018 独立立项、实现和验收。

### SEC-019 — login lock

- ID: SEC-019
- Source: SRS V1.4 AL11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: login lock
- Behavior: 系统执行或展示[login lock]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AL11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL11 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-019 独立立项、实现和验收。

### SEC-020 — operation logs

- ID: SEC-020
- Source: SRS V1.4 AL12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: operation logs
- Behavior: 系统执行或展示[operation logs]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AL12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL12 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-021 — error logs

- ID: SEC-021
- Source: SRS V1.4 AL13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: error logs
- Behavior: 系统执行或展示[error logs]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AL13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL13 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-021 独立立项、实现和验收。

### SEC-022 — integration logs

- ID: SEC-022
- Source: SRS V1.4 AL14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: integration logs
- Behavior: 系统执行或展示[integration logs]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AL14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL14 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-023 — immutable audit extension

- ID: SEC-023
- Source: SRS V1.4 AL15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: immutable audit extension
- Behavior: 系统执行或展示[immutable audit extension]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AL15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL15 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-023 独立立项、实现和验收。

### SEC-024 — backup

- ID: SEC-024
- Source: SRS V1.4 AL16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: backup
- Behavior: 系统执行或展示[backup]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AL16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL16 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-024 独立立项、实现和验收。

### SEC-025 — restore

- ID: SEC-025
- Source: SRS V1.4 AL17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: restore
- Behavior: 系统执行或展示[restore]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AL17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL17 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-025 独立立项、实现和验收。

### SEC-026 — security events

- ID: SEC-026
- Source: SRS V1.4 AL18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: security events
- Behavior: 系统执行或展示[security events]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AL18 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL18 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL18 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL18 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL18 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-026 独立立项、实现和验收。

### SEC-027 — key management port

- ID: SEC-027
- Source: SRS V1.4 AL19
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: key management port
- Behavior: 系统执行或展示[key management port]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AL19 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL19 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL19 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL19 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL19 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-027 独立立项、实现和验收。

### SEC-028 — national cryptography extension

- ID: SEC-028
- Source: SRS V1.4 AL20
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: national cryptography extension
- Behavior: 系统执行或展示[national cryptography extension]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AL20 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL20 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL20 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL20 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AL20 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-028 独立立项、实现和验收。

### MIG-001 — legacy source

- ID: MIG-001
- Source: SRS V1.4 AM01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: legacy source
- Behavior: 系统执行或展示[legacy source]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AM01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM01 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MIG-001 独立立项、实现和验收。

### MIG-002 — extraction

- ID: MIG-002
- Source: SRS V1.4 AM02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: extraction
- Behavior: 系统执行或展示[extraction]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AM02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM02 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MIG-002 独立立项、实现和验收。

### MIG-003 — transformation

- ID: MIG-003
- Source: SRS V1.4 AM03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: transformation
- Behavior: 系统执行或展示[transformation]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AM03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM03 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MIG-003 独立立项、实现和验收。

### MIG-004 — mapping

- ID: MIG-004
- Source: SRS V1.4 AM04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: mapping
- Behavior: 系统执行或展示[mapping]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AM04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM04 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MIG-004 独立立项、实现和验收。

### MIG-005 — validation

- ID: MIG-005
- Source: SRS V1.4 AM05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: validation
- Behavior: 系统执行或展示[validation]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AM05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM05 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MIG-005 独立立项、实现和验收。

### MIG-006 — case counts

- ID: MIG-006
- Source: SRS V1.4 AM06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: case counts
- Behavior: 系统执行或展示[case counts]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AM06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM06 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MIG-006 独立立项、实现和验收。

### MIG-007 — report validation

- ID: MIG-007
- Source: SRS V1.4 AM07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: report validation
- Behavior: 系统执行或展示[report validation]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AM07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM07 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MIG-007 独立立项、实现和验收。

### MIG-008 — WSI mapping

- ID: MIG-008
- Source: SRS V1.4 AM08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: WSI mapping
- Behavior: 系统执行或展示[WSI mapping]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AM08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM08 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MIG-008 独立立项、实现和验收。

### MIG-009 — incremental migration

- ID: MIG-009
- Source: SRS V1.4 AM09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: incremental migration
- Behavior: 系统执行或展示[incremental migration]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AM09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM09 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MIG-009 独立立项、实现和验收。

### MIG-010 — retry

- ID: MIG-010
- Source: SRS V1.4 AM10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: retry
- Behavior: 系统执行或展示[retry]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AM10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM10 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MIG-010 独立立项、实现和验收。

### MIG-011 — error list

- ID: MIG-011
- Source: SRS V1.4 AM11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: error list
- Behavior: 系统执行或展示[error list]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AM11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM11 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MIG-011 独立立项、实现和验收。

### MIG-012 — new/old mapping

- ID: MIG-012
- Source: SRS V1.4 AM12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: new/old mapping
- Behavior: 系统执行或展示[new/old mapping]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AM12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM12 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MIG-012 独立立项、实现和验收。

### MIG-013 — audit

- ID: MIG-013
- Source: SRS V1.4 AM13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: audit
- Behavior: 系统执行或展示[audit]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AM13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM13 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MIG-013 独立立项、实现和验收。

### MIG-014 — read-only legacy

- ID: MIG-014
- Source: SRS V1.4 AM14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: read-only legacy
- Behavior: 系统执行或展示[read-only legacy]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AM14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM14 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MIG-014 独立立项、实现和验收。

### MIG-015 — historical query

- ID: MIG-015
- Source: SRS V1.4 AM15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: historical query
- Behavior: 系统执行或展示[historical query]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 AM15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md AM15 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 MIG-015 独立立项、实现和验收。

### SEC-029 — B/S

- ID: SEC-029
- Source: SRS V1.4 NFR01
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: B/S
- Behavior: 系统执行或展示[B/S]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR01 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR01 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR01 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR01 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR01 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-030 — browser based

- ID: SEC-030
- Source: SRS V1.4 NFR02
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: browser based
- Behavior: 系统执行或展示[browser based]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR02 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR02 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR02 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR02 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR02 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-031 — modularity

- ID: SEC-031
- Source: SRS V1.4 NFR03
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: modularity
- Behavior: 系统执行或展示[modularity]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR03 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR03 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR03 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR03 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR03 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-032 — configurable business rules

- ID: SEC-032
- Source: SRS V1.4 NFR04
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: configurable business rules
- Behavior: 系统执行或展示[configurable business rules]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR04 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR04 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR04 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR04 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR04 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-033 — configurable templates

- ID: SEC-033
- Source: SRS V1.4 NFR05
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: configurable templates
- Behavior: 系统执行或展示[configurable templates]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR05 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR05 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR05 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR05 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR05 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-034 — configurable numbering

- ID: SEC-034
- Source: SRS V1.4 NFR06
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: configurable numbering
- Behavior: 系统执行或展示[configurable numbering]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR06 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR06 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR06 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR06 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR06 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-035 — 50 concurrent users

- ID: SEC-035
- Source: SRS V1.4 NFR07
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 50 concurrent users
- Behavior: 系统执行或展示[50 concurrent users]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR07 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR07 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR07 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR07 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR07 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-035 独立立项、实现和验收。

### SEC-036 — core operations 20 concurrent users

- ID: SEC-036
- Source: SRS V1.4 NFR08
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: core operations 20 concurrent users
- Behavior: 系统执行或展示[core operations 20 concurrent users]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR08 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR08 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR08 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR08 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR08 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-036 独立立项、实现和验收。

### SEC-037 — 1000 cases/day

- ID: SEC-037
- Source: SRS V1.4 NFR09
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 1000 cases/day
- Behavior: 系统执行或展示[1000 cases/day]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR09 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR09 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR09 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR09 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR09 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-037 独立立项、实现和验收。

### SEC-038 — common page/query <=2s target

- ID: SEC-038
- Source: SRS V1.4 NFR10
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: common page/query <=2s target
- Behavior: 系统执行或展示[common page/query <=2s target]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR10 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR10 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR10 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR10 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR10 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-038 独立立项、实现和验收。

### SEC-039 — save/update/delete generally <=1s, max <=3s target

- ID: SEC-039
- Source: SRS V1.4 NFR11
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: save/update/delete generally <=1s, max <=3s target
- Behavior: 系统执行或展示[save/update/delete generally <=1s, max <=3s target]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR11 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR11 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR11 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR11 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR11 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-039 独立立项、实现和验收。

### SEC-040 — simple 50k query <=3s target

- ID: SEC-040
- Source: SRS V1.4 NFR12
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: simple 50k query <=3s target
- Behavior: 系统执行或展示[simple 50k query <=3s target]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR12 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR12 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR12 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR12 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR12 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-040 独立立项、实现和验收。

### SEC-041 — 100k query <=6s target

- ID: SEC-041
- Source: SRS V1.4 NFR13
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 100k query <=6s target
- Behavior: 系统执行或展示[100k query <=6s target]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR13 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR13 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR13 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR13 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR13 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-041 独立立项、实现和验收。

### SEC-042 — complex report <=30s

- ID: SEC-042
- Source: SRS V1.4 NFR14
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: complex report <=30s
- Behavior: 系统执行或展示[complex report <=30s]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR14 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR14 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR14 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR14 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR14 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-042 独立立项、实现和验收。

### SEC-043 — normal report <=5s

- ID: SEC-043
- Source: SRS V1.4 NFR15
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: normal report <=5s
- Behavior: 系统执行或展示[normal report <=5s]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR15 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR15 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR15 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR15 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR15 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-043 独立立项、实现和验收。

### SEC-044 — WSI first view <=2s target

- ID: SEC-044
- Source: SRS V1.4 NFR16
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: WSI first view <=2s target
- Behavior: 系统执行或展示[WSI first view <=2s target]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR16 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR16 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR16 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR16 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR16 行及对应测试；缺口状态不得视为通过
- Status: MISSING
- Gap: 当前仓库未发现可验收的完整实现证据。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-044 独立立项、实现和验收。

### SEC-045 — availability target >=99.9%

- ID: SEC-045
- Source: SRS V1.4 NFR17
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: availability target >=99.9%
- Behavior: 系统执行或展示[availability target >=99.9%]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR17 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR17 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR17 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR17 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR17 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-045 独立立项、实现和验收。

### SEC-046 — backup

- ID: SEC-046
- Source: SRS V1.4 NFR18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: backup
- Behavior: 系统执行或展示[backup]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR18 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR18 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR18 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR18 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR18 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-046 独立立项、实现和验收。

### SEC-047 — recovery

- ID: SEC-047
- Source: SRS V1.4 NFR19
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: recovery
- Behavior: 系统执行或展示[recovery]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR19 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR19 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR19 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR19 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR19 行及对应测试；缺口状态不得视为通过
- Status: EXTERNAL_DEPENDENCY
- Gap: 依赖真实外部系统、设备或临床环境；当前不得声明生产验证完成。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-047 独立立项、实现和验收。

### SEC-048 — error handling

- ID: SEC-048
- Source: SRS V1.4 NFR20
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: error handling
- Behavior: 系统执行或展示[error handling]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR20 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR20 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR20 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR20 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR20 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-049 — clear UI

- ID: SEC-049
- Source: SRS V1.4 NFR21
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: clear UI
- Behavior: 系统执行或展示[clear UI]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR21 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR21 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR21 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR21 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR21 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-050 — role-specific UI

- ID: SEC-050
- Source: SRS V1.4 NFR22
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: role-specific UI
- Behavior: 系统执行或展示[role-specific UI]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR22 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR22 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR22 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR22 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR22 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-051 — reduce irrelevant functions

- ID: SEC-051
- Source: SRS V1.4 NFR23
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: reduce irrelevant functions
- Behavior: 系统执行或展示[reduce irrelevant functions]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR23 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR23 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR23 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR23 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR23 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-052 — shortcuts

- ID: SEC-052
- Source: SRS V1.4 NFR24
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: shortcuts
- Behavior: 系统执行或展示[shortcuts]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR24 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR24 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR24 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR24 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR24 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-053 — batch processing

- ID: SEC-053
- Source: SRS V1.4 NFR25
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: batch processing
- Behavior: 系统执行或展示[batch processing]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR25 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR25 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR25 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR25 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR25 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-054 — navigation <=3 levels

- ID: SEC-054
- Source: SRS V1.4 NFR26
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: navigation <=3 levels
- Behavior: 系统执行或展示[navigation <=3 levels]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR26 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR26 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR26 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR26 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR26 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-055 — logs

- ID: SEC-055
- Source: SRS V1.4 NFR27
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: logs
- Behavior: 系统执行或展示[logs]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR27 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR27 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR27 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR27 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR27 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-056 — maintainability

- ID: SEC-056
- Source: SRS V1.4 NFR28
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: maintainability
- Behavior: 系统执行或展示[maintainability]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR28 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR28 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR28 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR28 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR28 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-057 — extensibility

- ID: SEC-057
- Source: SRS V1.4 NFR29
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: extensibility
- Behavior: 系统执行或展示[extensibility]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR29 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR29 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR29 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR29 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR29 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-058 — multi-campus

- ID: SEC-058
- Source: SRS V1.4 NFR30
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: multi-campus
- Behavior: 系统执行或展示[multi-campus]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR30 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR30 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR30 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR30 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR30 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-059 — data isolation

- ID: SEC-059
- Source: SRS V1.4 NFR31
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: data isolation
- Behavior: 系统执行或展示[data isolation]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR31 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR31 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR31 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR31 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR31 行及对应测试；缺口状态不得视为通过
- Status: COMPLETE
- Gap: 当前证据表明该原子能力已闭环；相邻能力变更时保持回归验证。
- V2 Decision: 保留现有实现与证据链；仅在相关功能变化时补充回归。

### SEC-060 — data sharing policy

- ID: SEC-060
- Source: SRS V1.4 NFR32
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: data sharing policy
- Behavior: 系统执行或展示[data sharing policy]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 NFR32 对应产品入口
- Backend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR32 行及对应仓库模块
- DB Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR32 行及对应 migration
- Frontend Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR32 行及对应前端入口
- Test Evidence: SRS-V14-V2-COVERAGE-MATRIX.md NFR32 行及对应测试；缺口状态不得视为通过
- Status: PARTIAL
- Gap: 当前仅部分闭环；缺失的 UI、API、数据或测试证据须在后续对应原子任务中补齐。
- V2 Decision: FC01A 仅记录该非 WB 缺口；后续以 SEC-060 独立立项、实现和验收。

### APP-SEND-001 — 扫描标本条码

- ID: APP-SEND-001
- Source: SRS V1.4 A14–A18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 扫描标本条码
- Behavior: 系统执行或展示[扫描标本条码]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A14–A18 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`JdbcV2ApplicationRepository` 与 `V2ApplicationController` 共享同一来样事实，支持扫码匹配、重复识别、确认、组合查询与 Excel 导出。
- DB Evidence: `V35__application_registration_closure.sql` 在 ApplicationItem 上保存稳定条码、送检时间、操作人和来样引用，并保留打印/重打日志。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供送检条码扫描、患者/申请摘要核对、确认、记录查询和 Excel 导出。
- Test Evidence: `V2ApplicationWebTest` 与 `fc02a-registration.spec.ts` 覆盖未找到、不匹配、重复扫描、事实保存、各筛选条件、稳定打印顺序及导出结果一致性。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实扫码枪与打印硬件验证仍为独立 EXTERNAL_DEPENDENCY。
- V2 Decision: 送检事实绑定申请侧来样身份；登记前不提前创建核心 Specimen，不存在条码时也不会自动创建 Application。

### APP-SEND-002 — 通过条码找到申请

- ID: APP-SEND-002
- Source: SRS V1.4 A14–A18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 通过条码找到申请
- Behavior: 系统执行或展示[通过条码找到申请]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A14–A18 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`JdbcV2ApplicationRepository` 与 `V2ApplicationController` 共享同一来样事实，支持扫码匹配、重复识别、确认、组合查询与 Excel 导出。
- DB Evidence: `V35__application_registration_closure.sql` 在 ApplicationItem 上保存稳定条码、送检时间、操作人和来样引用，并保留打印/重打日志。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供送检条码扫描、患者/申请摘要核对、确认、记录查询和 Excel 导出。
- Test Evidence: `V2ApplicationWebTest` 与 `fc02a-registration.spec.ts` 覆盖未找到、不匹配、重复扫描、事实保存、各筛选条件、稳定打印顺序及导出结果一致性。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实扫码枪与打印硬件验证仍为独立 EXTERNAL_DEPENDENCY。
- V2 Decision: 送检事实绑定申请侧来样身份；登记前不提前创建核心 Specimen，不存在条码时也不会自动创建 Application。

### APP-SEND-003 — 条码不存在时拒绝

- ID: APP-SEND-003
- Source: SRS V1.4 A14–A18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 条码不存在时拒绝
- Behavior: 系统执行或展示[条码不存在时拒绝]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A14–A18 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`JdbcV2ApplicationRepository` 与 `V2ApplicationController` 共享同一来样事实，支持扫码匹配、重复识别、确认、组合查询与 Excel 导出。
- DB Evidence: `V35__application_registration_closure.sql` 在 ApplicationItem 上保存稳定条码、送检时间、操作人和来样引用，并保留打印/重打日志。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供送检条码扫描、患者/申请摘要核对、确认、记录查询和 Excel 导出。
- Test Evidence: `V2ApplicationWebTest` 与 `fc02a-registration.spec.ts` 覆盖未找到、不匹配、重复扫描、事实保存、各筛选条件、稳定打印顺序及导出结果一致性。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实扫码枪与打印硬件验证仍为独立 EXTERNAL_DEPENDENCY。
- V2 Decision: 送检事实绑定申请侧来样身份；登记前不提前创建核心 Specimen，不存在条码时也不会自动创建 Application。

### APP-SEND-004 — 条码与申请不匹配时拒绝

- ID: APP-SEND-004
- Source: SRS V1.4 A14–A18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 条码与申请不匹配时拒绝
- Behavior: 系统执行或展示[条码与申请不匹配时拒绝]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A14–A18 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`JdbcV2ApplicationRepository` 与 `V2ApplicationController` 共享同一来样事实，支持扫码匹配、重复识别、确认、组合查询与 Excel 导出。
- DB Evidence: `V35__application_registration_closure.sql` 在 ApplicationItem 上保存稳定条码、送检时间、操作人和来样引用，并保留打印/重打日志。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供送检条码扫描、患者/申请摘要核对、确认、记录查询和 Excel 导出。
- Test Evidence: `V2ApplicationWebTest` 与 `fc02a-registration.spec.ts` 覆盖未找到、不匹配、重复扫描、事实保存、各筛选条件、稳定打印顺序及导出结果一致性。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实扫码枪与打印硬件验证仍为独立 EXTERNAL_DEPENDENCY。
- V2 Decision: 送检事实绑定申请侧来样身份；登记前不提前创建核心 Specimen，不存在条码时也不会自动创建 Application。

### APP-SEND-005 — 确认送检

- ID: APP-SEND-005
- Source: SRS V1.4 A14–A18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 确认送检
- Behavior: 系统执行或展示[确认送检]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A14–A18 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`JdbcV2ApplicationRepository` 与 `V2ApplicationController` 共享同一来样事实，支持扫码匹配、重复识别、确认、组合查询与 Excel 导出。
- DB Evidence: `V35__application_registration_closure.sql` 在 ApplicationItem 上保存稳定条码、送检时间、操作人和来样引用，并保留打印/重打日志。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供送检条码扫描、患者/申请摘要核对、确认、记录查询和 Excel 导出。
- Test Evidence: `V2ApplicationWebTest` 与 `fc02a-registration.spec.ts` 覆盖未找到、不匹配、重复扫描、事实保存、各筛选条件、稳定打印顺序及导出结果一致性。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实扫码枪与打印硬件验证仍为独立 EXTERNAL_DEPENDENCY。
- V2 Decision: 送检事实绑定申请侧来样身份；登记前不提前创建核心 Specimen，不存在条码时也不会自动创建 Application。

### APP-SEND-006 — 保存送检时间

- ID: APP-SEND-006
- Source: SRS V1.4 A14–A18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 保存送检时间
- Behavior: 系统执行或展示[保存送检时间]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A14–A18 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`JdbcV2ApplicationRepository` 与 `V2ApplicationController` 共享同一来样事实，支持扫码匹配、重复识别、确认、组合查询与 Excel 导出。
- DB Evidence: `V35__application_registration_closure.sql` 在 ApplicationItem 上保存稳定条码、送检时间、操作人和来样引用，并保留打印/重打日志。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供送检条码扫描、患者/申请摘要核对、确认、记录查询和 Excel 导出。
- Test Evidence: `V2ApplicationWebTest` 与 `fc02a-registration.spec.ts` 覆盖未找到、不匹配、重复扫描、事实保存、各筛选条件、稳定打印顺序及导出结果一致性。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实扫码枪与打印硬件验证仍为独立 EXTERNAL_DEPENDENCY。
- V2 Decision: 送检事实绑定申请侧来样身份；登记前不提前创建核心 Specimen，不存在条码时也不会自动创建 Application。

### APP-SEND-007 — 保存送检人

- ID: APP-SEND-007
- Source: SRS V1.4 A14–A18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 保存送检人
- Behavior: 系统执行或展示[保存送检人]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A14–A18 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`JdbcV2ApplicationRepository` 与 `V2ApplicationController` 共享同一来样事实，支持扫码匹配、重复识别、确认、组合查询与 Excel 导出。
- DB Evidence: `V35__application_registration_closure.sql` 在 ApplicationItem 上保存稳定条码、送检时间、操作人和来样引用，并保留打印/重打日志。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供送检条码扫描、患者/申请摘要核对、确认、记录查询和 Excel 导出。
- Test Evidence: `V2ApplicationWebTest` 与 `fc02a-registration.spec.ts` 覆盖未找到、不匹配、重复扫描、事实保存、各筛选条件、稳定打印顺序及导出结果一致性。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实扫码枪与打印硬件验证仍为独立 EXTERNAL_DEPENDENCY。
- V2 Decision: 送检事实绑定申请侧来样身份；登记前不提前创建核心 Specimen，不存在条码时也不会自动创建 Application。

### APP-SEND-008 — 查询送检记录

- ID: APP-SEND-008
- Source: SRS V1.4 A14–A18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 查询送检记录
- Behavior: 系统执行或展示[查询送检记录]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A14–A18 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`JdbcV2ApplicationRepository` 与 `V2ApplicationController` 共享同一来样事实，支持扫码匹配、重复识别、确认、组合查询与 Excel 导出。
- DB Evidence: `V35__application_registration_closure.sql` 在 ApplicationItem 上保存稳定条码、送检时间、操作人和来样引用，并保留打印/重打日志。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供送检条码扫描、患者/申请摘要核对、确认、记录查询和 Excel 导出。
- Test Evidence: `V2ApplicationWebTest` 与 `fc02a-registration.spec.ts` 覆盖未找到、不匹配、重复扫描、事实保存、各筛选条件、稳定打印顺序及导出结果一致性。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实扫码枪与打印硬件验证仍为独立 EXTERNAL_DEPENDENCY。
- V2 Decision: 送检事实绑定申请侧来样身份；登记前不提前创建核心 Specimen，不存在条码时也不会自动创建 Application。

### APP-SEND-009 — 按门诊/住院号查询

- ID: APP-SEND-009
- Source: SRS V1.4 A14–A18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 按门诊/住院号查询
- Behavior: 系统执行或展示[按门诊/住院号查询]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A14–A18 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`JdbcV2ApplicationRepository` 与 `V2ApplicationController` 共享同一来样事实，支持扫码匹配、重复识别、确认、组合查询与 Excel 导出。
- DB Evidence: `V35__application_registration_closure.sql` 在 ApplicationItem 上保存稳定条码、送检时间、操作人和来样引用，并保留打印/重打日志。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供送检条码扫描、患者/申请摘要核对、确认、记录查询和 Excel 导出。
- Test Evidence: `V2ApplicationWebTest` 与 `fc02a-registration.spec.ts` 覆盖未找到、不匹配、重复扫描、事实保存、各筛选条件、稳定打印顺序及导出结果一致性。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实扫码枪与打印硬件验证仍为独立 EXTERNAL_DEPENDENCY。
- V2 Decision: 送检事实绑定申请侧来样身份；登记前不提前创建核心 Specimen，不存在条码时也不会自动创建 Application。

### APP-SEND-010 — 按时间查询

- ID: APP-SEND-010
- Source: SRS V1.4 A14–A18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 按时间查询
- Behavior: 系统执行或展示[按时间查询]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A14–A18 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`JdbcV2ApplicationRepository` 与 `V2ApplicationController` 共享同一来样事实，支持扫码匹配、重复识别、确认、组合查询与 Excel 导出。
- DB Evidence: `V35__application_registration_closure.sql` 在 ApplicationItem 上保存稳定条码、送检时间、操作人和来样引用，并保留打印/重打日志。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供送检条码扫描、患者/申请摘要核对、确认、记录查询和 Excel 导出。
- Test Evidence: `V2ApplicationWebTest` 与 `fc02a-registration.spec.ts` 覆盖未找到、不匹配、重复扫描、事实保存、各筛选条件、稳定打印顺序及导出结果一致性。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实扫码枪与打印硬件验证仍为独立 EXTERNAL_DEPENDENCY。
- V2 Decision: 送检事实绑定申请侧来样身份；登记前不提前创建核心 Specimen，不存在条码时也不会自动创建 Application。

### APP-SEND-011 — 按申请项目查询

- ID: APP-SEND-011
- Source: SRS V1.4 A14–A18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 按申请项目查询
- Behavior: 系统执行或展示[按申请项目查询]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A14–A18 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`JdbcV2ApplicationRepository` 与 `V2ApplicationController` 共享同一来样事实，支持扫码匹配、重复识别、确认、组合查询与 Excel 导出。
- DB Evidence: `V35__application_registration_closure.sql` 在 ApplicationItem 上保存稳定条码、送检时间、操作人和来样引用，并保留打印/重打日志。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供送检条码扫描、患者/申请摘要核对、确认、记录查询和 Excel 导出。
- Test Evidence: `V2ApplicationWebTest` 与 `fc02a-registration.spec.ts` 覆盖未找到、不匹配、重复扫描、事实保存、各筛选条件、稳定打印顺序及导出结果一致性。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实扫码枪与打印硬件验证仍为独立 EXTERNAL_DEPENDENCY。
- V2 Decision: 送检事实绑定申请侧来样身份；登记前不提前创建核心 Specimen，不存在条码时也不会自动创建 Application。

### APP-SEND-012 — Excel 导出

- ID: APP-SEND-012
- Source: SRS V1.4 A14–A18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: Excel 导出
- Behavior: 系统执行或展示[Excel 导出]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A14–A18 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`JdbcV2ApplicationRepository` 与 `V2ApplicationController` 共享同一来样事实，支持扫码匹配、重复识别、确认、组合查询与 Excel 导出。
- DB Evidence: `V35__application_registration_closure.sql` 在 ApplicationItem 上保存稳定条码、送检时间、操作人和来样引用，并保留打印/重打日志。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供送检条码扫描、患者/申请摘要核对、确认、记录查询和 Excel 导出。
- Test Evidence: `V2ApplicationWebTest` 与 `fc02a-registration.spec.ts` 覆盖未找到、不匹配、重复扫描、事实保存、各筛选条件、稳定打印顺序及导出结果一致性。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实扫码枪与打印硬件验证仍为独立 EXTERNAL_DEPENDENCY。
- V2 Decision: 送检事实绑定申请侧来样身份；登记前不提前创建核心 Specimen，不存在条码时也不会自动创建 Application。

### APP-SEND-013 — 导出内容与查询结果一致

- ID: APP-SEND-013
- Source: SRS V1.4 A14–A18
- Business Actor: 与该能力权限目录对应的业务人员或管理员
- Trigger: 用户进入对应业务入口或发起该业务动作
- Precondition: 已认证；具备对应 Business Permission；数据在当前 Data Permission 范围内
- Input: 导出内容与查询结果一致
- Behavior: 系统执行或展示[导出内容与查询结果一致]这一独立可观察能力
- Output: 明确的业务结果、状态或错误语义
- Business Rule: 遵循 V2 已确认领域不变量；禁止 Generic Task/Workflow 替代业务事实
- Permission: 以 authoritative permission catalog 和后端授权为准
- Data Scope: hospital/campus/department 按当前 V2 Data Permission 语义
- UI Entry: SRS V1.4 A14–A18 对应产品入口
- Backend Evidence: `V2ApplicationApplicationService`、`JdbcV2ApplicationRepository` 与 `V2ApplicationController` 共享同一来样事实，支持扫码匹配、重复识别、确认、组合查询与 Excel 导出。
- DB Evidence: `V35__application_registration_closure.sql` 在 ApplicationItem 上保存稳定条码、送检时间、操作人和来样引用，并保留打印/重打日志。
- Frontend Evidence: `V2RegistrationWorkbench.vue` 与 `v2RegistrationApi.ts` 提供送检条码扫描、患者/申请摘要核对、确认、记录查询和 Excel 导出。
- Test Evidence: `V2ApplicationWebTest` 与 `fc02a-registration.spec.ts` 覆盖未找到、不匹配、重复扫描、事实保存、各筛选条件、稳定打印顺序及导出结果一致性。
- Status: COMPLETE
- Gap: 无当前已知产品闭环缺口；真实扫码枪与打印硬件验证仍为独立 EXTERNAL_DEPENDENCY。
- V2 Decision: 送检事实绑定申请侧来样身份；登记前不提前创建核心 Specimen，不存在条码时也不会自动创建 Application。

## 3. Workbench atomic acceptance

下表 46 条是 O01–O16 在 FC01A 中进一步拆出的最终 Workbench 统计单位。表内每一行都是一条原子记录：`ID` 为 ID；Source 固定为对应 FC01A Part/WB 条款；Actor 为 Business Actor；Capability 为 Trigger/Behavior；Expected 同时约束 Precondition、Input、Output、Business Rule、Permission 与 Data Scope；Actual 为可观察结果；Evidence 同时给出 UI Entry、Backend、DB、Frontend 与 Test Evidence；Status、Gap 和 V2 Decision 由该行 Status 及 Expected/Actual 差异确定。该压缩格式保留全部固定字段语义，并满足最终 Workbench Acceptance Table 格式。

| ID | Actor | Capability | Expected | Actual | Evidence | Status |
|---|---|---|---|---|---|---|
| WB-REG-001 | 登记员 | 待登记按未完成 ApplicationItem 投影 | 部分登记仍保留剩余项目 | 每个剩余项目一行 | Application/ApplicationItem query + registrar test | COMPLETE |
| WB-REG-002 | 登记员 | 待登记行展示业务字段 | 不展示 UUID | 申请号、患者、性别年龄、就诊号、科室医生、项目、标本、申请时间、等待时长 | capability queue DTO + V2Home | COMPLETE |
| WB-REG-003 | 登记员 | 人类可读等待时长 | 分钟/小时/天 | 前后端提供 waitingMinutes，UI 格式化 | V2Home unit test | COMPLETE |
| WB-REG-004 | 登记员 | 队列内筛选 | 姓名、申请号、就诊号、科室、类型、日期 | 同页 Filter Bar 完成组合筛选 | V2Home | COMPLETE |
| WB-REG-005 | 登记员 | 直接登记 | 不经过 Case Center | 行进入 Registration Workspace | NavigationContext component test | COMPLETE |
| WB-REG-006 | 登记员 | 登记后移出待办 | 只移除已完成项目 | 单 ApplicationItem 注册并刷新 Projection | V2Application/registrar tests | COMPLETE |
| WB-REG-007 | 登记员 | 我今天登记 | 当前用户今日 Case 创建事实 | 独立 tracking group，点击 Case Center | registered-today query | COMPLETE |
| WB-REG-008 | 登记员 | 退回待处理 | 仅真实退回事实存在时显示 | 当前无对应事实模型，不显示假队列 | schema audit | MISSING |
| WB-GROSS-001 | 取材人员 | 常规待取材 | 由 Case/Specimen/Grossing 事实投影 | ACTIVE TISSUE、标本存在、初始取材未完成 | grossing projection query | COMPLETE |
| WB-GROSS-002 | 取材人员 | 取材行字段 | 病理号、患者、类型、标本、登记、等待、来源 | 紧凑行展示摘要 | queue DTO + V2Home | COMPLETE |
| WB-GROSS-003 | 取材人员 | 直接取材 | 不经过 Case Center | 进入 Grossing Workspace | NavigationContext | COMPLETE |
| WB-GROSS-004 | 取材人员 | 冰冻待取材 | 按 FrozenRound | Round ID/轮次独立定位 | frozen-round projection | COMPLETE |
| WB-GROSS-005 | 取材人员 | 我今天取材 | 当前用户今日 completed grossing | 独立 tracking group | completed_by/at projection | COMPLETE |
| WB-TECH-001 | 技术员 | 常规制片 | 按业务来源 | Routine production queue | production projection tests | COMPLETE |
| WB-TECH-002 | 技术员 | 细胞制片 | Case+Specimen 且 zero Slide 也进入 | 按未完成 specimen 投影 | cytology repository + E2E | COMPLETE |
| WB-TECH-003 | 技术员 | 冰冻制片 | 按 FrozenRound | Round 独立行 | production repository | COMPLETE |
| WB-TECH-004 | 技术员 | 技术医嘱 | 实际未完成 item | 聚合 order item/output 事实 | technical projection | COMPLETE |
| WB-TECH-005 | 技术员 | 待完成玻片 | 仅已存在未完成 Slide | 与 zero-slide 细胞队列分离 | slide projection | COMPLETE |
| WB-TECH-006 | 技术员 | 异常/返工 | 只来自 rework/exception facts | 不解析备注生成 | material process/rework facts | COMPLETE |
| WB-DX-001 | 医生 | 待接诊 | 可主动接诊的未分配诊断 | 文案为待接诊 | public-pool projection | COMPLETE |
| WB-DX-002 | 医生 | 待初诊 | 当前医生 INITIAL 责任 | 直接进入 Diagnosis Workspace | responsibility query + navigation E2E | COMPLETE |
| WB-DX-003 | 医生 | 待复诊 | 同一 Diagnosis REVIEW 责任 | 未创建新 Diagnosis | responsibility chain tests | COMPLETE |
| WB-DX-004 | 医生 | 待审核 | 同一 Diagnosis AUDIT 责任 | 按当前审核责任显示 | responsibility query | COMPLETE |
| WB-DX-005 | 医生 | 新技术结果 | 新结果且当前医生未确认 | audit acknowledgement 后消失 | technical-result query/audit | COMPLETE |
| WB-DX-006 | 医生 | 撤回待处理 | 撤回后当前责任医生 | 已限制当前未完成 responsibility | withdrawn query | COMPLETE |
| WB-MULTI-001 | 多角色用户 | 权限能力并集 | 一套 Workbench | 后端按 permission union 生成 queues | permission test | COMPLETE |
| WB-MULTI-002 | 多角色用户 | 无角色切换首页 | 直接显示合并队列 | synthetic registrar-tech fixture | auth fixture + E2E | COMPLETE |
| WB-MULTI-003 | 多角色用户 | 无权队列隐藏 | 区分无权限和零数据 | 未授权 Queue 不在响应 | permission test | COMPLETE |
| WB-NAV-001 | 全部 | 保存来源 | origin/queue | URL NavigationContext | navigation tests | COMPLETE |
| WB-NAV-002 | 全部 | 保存筛选 | filter | session-scoped state | V2Home | COMPLETE |
| WB-NAV-003 | 全部 | 保存排序分页 | sort/page | session-scoped state | V2Home | COMPLETE |
| WB-NAV-004 | 全部 | 保存滚动位置 | scrollTop | session-scoped state | V2Home | COMPLETE |
| WB-NAV-005 | 全部 | 返回来源 | Workspace Back 回 Workbench | 不经过 Case Center | focused workspace E2E | COMPLETE |
| WB-NAV-006 | 登记员 | 登记并下一例 | 刷新原 Queue 取首个合法项 | 后端刷新后导航 | Registration Workspace | COMPLETE |
| WB-NAV-007 | 取材人员 | 取材完成并下一例 | 刷新原 Queue | 后端刷新后导航 | Grossing Workspace | COMPLETE |
| WB-NAV-008 | 技术员/医生 | 完成并下一项 | 原 Queue/权限范围 | 生产、医嘱、诊断已有闭环 | focused workspaces | COMPLETE |
| WB-CORE-001 | 全部 | 工作项来源 | 业务事实 Projection | 无 Generic Task/WorkItem 表 | schema/code audit | COMPLETE |
| WB-CORE-002 | 普通业务用户 | 我的工作 Shell | 顶部工具栏且无 Sidebar | 默认 `/v2/workbench` | App/V2Home | COMPLETE |
| WB-CORE-003 | 全部 | 紧凑列表密度 | 44–56px 行 | 52px 行 | CSS/visual acceptance | COMPLETE |
| WB-CORE-004 | 全部 | Queue Count/List 一致 | count=list.size | 同一 DTO 列表计算 count | count consistency test | COMPLETE |
| WB-CORE-005 | 全部 | 默认排序 | urgent、等待最久、业务时间 | UI 使用 urgent/waiting 排序 | V2Home | COMPLETE |
| WB-CORE-006 | 全部 | 可用动作 | 后端授权结果决定 | DTO 返回 availableActions | service/component test | COMPLETE |
| WB-CORE-007 | 全部 | 数据隔离 | 跨医院不可见 | hospital scope 强制查询 | data scope test | COMPLETE |
| WB-CORE-008 | 全部 | 零数据紧凑显示 | 不生成大空白卡 | Queue 标签显示 0，单行空态 | component test | COMPLETE |
| WB-CORE-009 | 全部 | TAT 视觉 | 正常/临近/超时 | 当前仅等待时长，无完整 TAT policy 状态 | V2Home | PARTIAL |
| WB-CORE-010 | 全部 | 全局搜索独立 | Toolbar 搜索进入 Case Center | 队列筛选与全局搜索分离 | App/V2Home | COMPLETE |

Workbench atomic totals: TOTAL=46, COMPLETE=44, PARTIAL=1, MISSING=1.

## SRS-FC03C Frozen Pathology Closure Evidence Addendum

本附录只记录 SRS-FC03C 对现有原子需求的实际证据，不改变未完成的通用诊断、报告设计器或真实外部接口范围。

| Atomic ID | Status | Backend / DB / Frontend / Test Evidence | Gap / Boundary |
|---|---|---|---|
| FROZEN-001 | COMPLETE | 统一 `pathology_case` + `business_type=FROZEN`；`V2FrozenWebTest`；浏览器 F-000003 登记后进入冰冻队列 | 无平行 FrozenCase 实体 |
| FROZEN-002 | COMPLETE | 现有 `PathologyNumberRule`；Frozen Web 与 PostgreSQL 迁移/并发测试 | 冰冻与常规编号独立 |
| FROZEN-003 | COMPLETE | `frozen_round`、round number 唯一约束；`V2FrozenWebTest`、`FrozenPostgresConcurrencyTest` | 轮次不是 Case |
| FROZEN-004 | COMPLETE | 多轮工作区、Round 2 创建与隔离断言；`V2FrozenWebTest` | Frozen End 后禁止新增轮次 |
| FROZEN-005 | COMPLETE | `frozen_round_specimen`；多标本生产投影与完成前后测试 | 每个标本按规则参与 requirement |
| FROZEN-006 | COMPLETE | 统一 `slide`，Frozen context/round/specimen 绑定；`V2FrozenWebTest` 与 `fc03c-frozen-pathology.spec.ts` 覆盖零玻片直接生成、无 Block、完成与回归 | 无 FrozenSlide 平行实体 |
| FROZEN-007 | COMPLETE | 复用统一 DigitalSlide/材料查询边界；Case Center 保持材料血缘 | 本轮不重构 WSI Viewer |
| FROZEN-008 | COMPLETE | 统一 `diagnosis` + `FROZEN_ROUND` context；医生工作台直接入口与回退浏览器验证 | 通用诊断模板能力不在本轮扩展 |
| FROZEN-009 | COMPLETE | 统一 `report` 关联 Frozen Diagnosis/Round；多轮报告保留与签发回归测试 | Frozen/常规报告不互相覆盖 |
| FROZEN-010 | COMPLETE | Round arrival time、后端 TAT 计算、刷新保持计时；工作区显示 timer/status | 阈值仍由现有 TAT policy 提供 |
| FROZEN-011 | COMPLETE | `V2FrozenApplicationService.acknowledgeTatAlert`、`V43__frozen_tat_alert_actions.sql`、Frozen 工作区的超时状态/确认操作；`V2FrozenWebTest.overdueFrozenRoundAcknowledgementCreatesAnImmutableActionFact` 验证 OVERDUE 和不可变处置事实 | 真实运营平台联动不在本任务范围 |
| FROZEN-012 | EXTERNAL_DEPENDENCY | `ClinicalResultNotification` port、mock adapter、attempt/outbox/retry 记录与接口测试 | 真实 OR/HIS 联调未完成；Simulator 不冒充生产验证 |
| FROZEN-013 | COMPLETE | `Frozen End` 事务、轮次前置校验、选定标本预览/复制与回滚；`V2FrozenWebTest`；`fc03c-frozen-pathology.spec.ts` 覆盖制片队列退出 | End 是事实，不改变 Case lifecycle |
| FROZEN-014 | COMPLETE | 新 Routine Case、新 Specimen、新 PathologyNo、`frozen_source_case_id`；End 集成测试 | 不复制 Frozen diagnosis/report/material identity |
| FROZEN-015 | COMPLETE | Routine 编号由编号规则分配；Frozen/Routine 编号独立断言 | 不复用 Frozen pathology number |
| FROZEN-016 | COMPLETE | `pathology_case.frozen_source_case_id` partial unique index；Case Center 双向显示；PG 迁移测试 | 不建立 Generic CaseRelation |
| FROZEN-017 | COMPLETE | `V2FrozenApplicationService.comparison` 与 `/frozen/cases/{caseId}/routine-comparison`；`V2FrozenRoutineComparison.vue`、Case Center 双向入口；`V2FrozenWebTest` 多轮/未诊断/快照测试及 `fc03c1-closure.spec.ts` 1920/1366 真实浏览器闭环覆盖 Frozen 各轮、Routine 最终诊断、撤回/未诊断语义和人工事实展示 | 不自动判定医学一致性；不新增人工 QC 评价枚举 |

### FC03C implementation decisions

- 初始轮次在 Frozen Application → Registration 完成时建立；统一 `Case`/`Specimen` 已创建后，由 Frozen 应用服务在同一事务内补建 `FrozenRound` 与 round-specimen 事实。手工 Frozen specimen 仍可在首次有效接收时创建首轮。
- `Frozen End` 使用 Case 行锁、Frozen End 事实和 `frozen_source_case_id` 唯一索引保证幂等；Routine Case 后续取消不会允许第二次 End 生成第二个 Routine Case。
- 真实外部 OR/HIS 通知、打印机及冰冻设备保持 `EXTERNAL_DEPENDENCY`；本轮只验证 adapter/simulator、失败记录和重试边界。
- Playwright 1920×1080 与 1366×768：`fc03c1-closure.spec.ts` 验证通知首次失败/重新发送/历史持久化、多轮未完成阻断、End 取消与选择性标本转常规、双向导航、重复 End 幂等，以及 Routine 诊断前后两侧对照；现有 `px03c-focused-workspaces.spec.ts` 和 `fc03c-frozen-pathology.spec.ts` 回归覆盖 Frozen Round 入口/计时和零玻片 → 直接 Slide → 完成 → 原队列移除。

### FC03C1 status transitions

仅本任务发生的原子状态转换：

```text
FROZEN-011 PARTIAL→COMPLETE
FROZEN-017 PARTIAL→COMPLETE
```
