# PIS SRS V1.4 Functional Baseline

来源：武汉市肺科医院数智化病理信息化建设项目《需求规格说明书 SRS V1.4》

## 1. 基线说明

本文件是从原始 SRS V1.4 中提取并规范化的功能需求基线。

它决定：“PIS V2 最终必须具备什么能力”。

它不决定：“领域模型必须怎么设计”。

如果 SRS 中的旧式流程、状态机或技术实现方式与已经冻结的 PIS V2 Domain Baseline 冲突，功能需求保留，实现方式以 PIS V2 Domain Baseline 为准。

当前任务中用户提供的完整 Prompt 是本文件的权威输入；本文件不声称逐字复制原始 SRS，而是保留经提取、归并后的完整功能能力。

## 2. 全局 V2 实现约束

- 禁止 Generic Workflow Engine、Generic Task entity、Persisted WorkItem 和 Giant Case State Machine。
- 禁止 CaseBusinessRecord、Generic CaseRelation。
- 禁止 PlannedBlock、ActualBlock、FrozenBlock、PlannedSlide、ActualSlide、FrozenSlide。
- 禁止 ReportVersion wrapper 和 GenericTechnicalResult。
- 普通业务 UI 禁止全业务 Sidebar、所有用户相同 Workbench、物理技术环节主导航。
- 普通业务 UI 禁止暴露 Responsibility、责任链、Public Pool、Projection、Capability、WorkItem。
- Workbench 是人的横向入口；Case Center 是 Case 的纵向中心；Focused Workspace 是当前操作入口。
- Workbench → Workspace → Workbench；Search → Case Center → Workspace → Case Center。
- 外部系统、设备或临床模型没有真实环境时，只能声明 ADAPTER READY、SIMULATOR VERIFIED、EXTERNAL_DEPENDENCY，不得声明 PRODUCTION VERIFIED。

## 3. 功能需求

### A. 病理检查电子申请

- **A01** 门诊病理电子申请
- **A02** 住院病理电子申请
- **A03** HIS患者基本信息自动获取
- **A04** 人工患者信息补录
- **A05** 多种病理申请类型
- **A06** 申请新增
- **A07** 申请修改
- **A08** 申请取消
- **A09** 申请项目到 BusinessType 映射
- **A10** 申请单完整性校验
- **A11** 标本条码打印
- **A12** 批量条码打印
- **A13** 打印日志
- **A14** 标本扫码送检
- **A15** 标本与申请一致性校验
- **A16** 送检人/送检时间
- **A17** 送检记录查询
- **A18** 送检记录导出

V2 约束：Application 与 Case 分离；一个 Application 可生成多个 Case，Case 可无 Application。

### B. 登记与接收

- **B01** HIS申请登记
- **B02** 手工登记
- **B03** 无申请直接登记
- **B04** 多 BusinessType 登记
- **B05** 病理号自动生成
- **B06** BusinessType 独立编号规则
- **B07** 病理号重复校验
- **B08** 授权病理号纠正
- **B09** 标本接收
- **B10** 标本核对
- **B11** 不合格标本拒收
- **B12** 拒收原因
- **B13** 标本补充信息
- **B14** 登记标签打印
- **B15** 门诊回执打印
- **B16** 电子登记本
- **B17** 登记操作日志
- **B18** 登记后 Case Progress Projection

V2 约束：拒收标本不得生成正式 Case；接受后才创建 Case 并分配 PathologyNo。

### C. Case

- **C01** CaseId
- **C02** BusinessType
- **C03** PathologyNo
- **C04** ACTIVE / CANCELLED
- **C05** Case cancellation
- **C06** pathology number release
- **C07** Case progress projection
- **C08** current handler projection
- **C09** report status projection
- **C10** patient context
- **C11** clinical context
- **C12** Case Center
- **C13** complete history
- **C14** audit history

V2 约束：Case 只保留 ACTIVE/CANCELLED 生命周期；待取材、待制片、待诊断、已签发均为投影。

### D. Specimen

- **D01** 一个 Case 多 Specimen
- **D02** 标本编号
- **D03** 标本类型
- **D04** 标本部位
- **D05** 离体时间
- **D06** 固定时间
- **D07** 接收时间
- **D08** 标本信息修改
- **D09** 标本新增
- **D10** 标本删除
- **D11** 标本拆分
- **D12** 标本异常
- **D13** 标本追踪
- **D14** Specimen → Slide 支持

### E. 取材 Grossing

- **E01** 待取材工作队列
- **E02** 扫码进入取材
- **E03** 患者/标本信息
- **E04** 大体描述
- **E05** 标本尺寸
- **E06** 大体图像
- **E07** 拍摄台接口
- **E08** 图像与 Case 绑定
- **E09** 图像标注
- **E10** 尺寸测量
- **E11** 多 Specimen 一次 Grossing
- **E12** 多次 Grossing
- **E13** Block 创建
- **E14** Block 编号
- **E15** Block 标签
- **E16** Block 打印
- **E17** Block 信息纠正
- **E18** 材块核对
- **E19** 操作人/时间
- **E20** 取材完成事实

V2 约束：Reopen/Correction 修改同一 Grossing；补取通过 TechnicalOrder 产生新 Grossing。

### F. 技术生产

- **F01** 常规制片
- **F02** 细胞制片
- **F03** 冰冻制片
- **F04** TechnicalOrder 制片
- **F05** Block → Slide
- **F06** Specimen → Slide
- **F07** External Slide
- **F08** Slide 编号
- **F09** Slide 标签
- **F10** Slide 批量打印
- **F11** Slide 扫码
- **F12** Slide completion
- **F13** 异常
- **F14** 返工
- **F15** 技术人员
- **F16** 时间记录
- **F17** 脱水记录
- **F18** 包埋记录
- **F19** 切片记录
- **F20** 染色记录
- **F21** 封片记录
- **F22** 设备信息
- **F23** 开始时间
- **F24** 完成时间
- **F25** 操作人员
- **F26** 异常备注
- **F27** 批量操作

V2 约束：F17–F27 仅为 Optional Technical Trace；一级队列按业务来源，禁止物理环节强制状态机。

### G. Block / Slide

- **G01** Unified Block
- **G02** Local Block
- **G03** External Block
- **G04** Block → Specimen
- **G05** Block → Grossing
- **G06** Block correction
- **G07** Block archive
- **G08** Block loan
- **G09** Unified Slide
- **G10** Block → Slide
- **G11** Specimen → Slide
- **G12** External Slide
- **G13** Slide soft delete
- **G14** Slide completion
- **G15** Slide correction
- **G16** Slide archive
- **G17** Slide loan

V2 约束：统一 Block/Slide；禁止 Planned/Actual/Frozen 平行实体。

### H. TechnicalOrder

- **H01** 医生下达技术医嘱
- **H02** 重切
- **H03** 深切
- **H04** 补取
- **H05** 重包埋
- **H06** IHC
- **H07** 特殊染色
- **H08** 其他技术项目
- **H09** target Case
- **H10** target Specimen
- **H11** target Block
- **H12** target Slide
- **H13** 一个 Order 多 Item
- **H14** 一个 Item 多 Target
- **H15** 条码生成
- **H16** 标签打印
- **H17** 执行
- **H18** 取消
- **H19** 进度
- **H20** 执行人员
- **H21** 执行时间
- **H22** 结构化结果
- **H23** 染色质量评价
- **H24** DigitalSlide 关联
- **H25** 医生获得新技术结果提醒

V2 约束：输出统一为 Block、Slide、Structured Result；禁止 GenericTechnicalResult 大杂烩。

### I. 免疫组化/特殊染色

- **I01** IHC 项目
- **I02** Special Stain 项目
- **I03** 批量技术医嘱
- **I04** 包埋盒扫码
- **I05** Slide 生成
- **I06** Slide 标签
- **I07** IHC设备接口
- **I08** 批量下发设备
- **I09** 执行结果
- **I10** 染色质量评价
- **I11** 质控表
- **I12** 项目明细
- **I13** 工作量统计
- **I14** 项目分类统计
- **I15** DigitalSlide
- **I16** 试剂消耗关联
- **I17** 费用状态记录

V2 约束：费用状态是旁路事实，收费接口失败不得锁死核心流程。

### J. 细胞病理

- **J01** 独立 BusinessType
- **J02** 细胞标本类型
- **J03** 标本处理方法
- **J04** 登记
- **J05** Specimen → Slide
- **J06** 细胞制片
- **J07** 染色封片记录
- **J08** DigitalSlide
- **J09** 细胞诊断
- **J10** TBS 等结构化模板
- **J11** 细胞报告

V2 约束：细胞默认不强制 Grossing、Block、Dehydration、Embedding。

### K. Frozen 冰冻

- **K01** Frozen Case
- **K02** 独立 Frozen PathologyNo
- **K03** FrozenRound
- **K04** 一个 Frozen Case 多 Round
- **K05** 一个 Round 多 Specimen
- **K06** Frozen Slide
- **K07** Frozen DigitalSlide
- **K08** Frozen Diagnosis
- **K09** Frozen Report
- **K10** Frozen TAT
- **K11** 超时提醒
- **K12** 术中报告发送
- **K13** Frozen End
- **K14** 自动创建 Routine Case
- **K15** Routine Case 新病理号
- **K16** routine_case.frozen_source_case_id
- **K17** 冰冻/石蜡诊断对照

V2 约束：Frozen Case 与 Routine Case 独立；通过 frozen_source_case_id 明确来源，禁止 Generic CaseRelation。

### L. Diagnosis

- **L01** 医生工作台
- **L02** 待接诊
- **L03** 待初诊
- **L04** 待复诊
- **L05** 待审核
- **L06** 新技术结果
- **L07** 撤回待处理
- **L08** 主动接诊
- **L09** 自动分诊
- **L10** 手工指派
- **L11** 转交
- **L12** 亚专科
- **L13** 每日最大接诊量
- **L14** 镜下所见
- **L15** 病理诊断
- **L16** 备注
- **L17** Structured Diagnosis
- **L18** Free Text
- **L19** DiagnosisTemplate
- **L20** 医嘱下达
- **L21** 诊断时限
- **L22** 保存
- **L23** 初诊
- **L24** 复诊
- **L25** 审核
- **L26** 签审记录
- **L27** 科内会诊记录
- **L28** 病例收藏
- **L29** 随访

V2 约束：Routine Case 的 INITIAL/REVIEW/AUDIT 是同一 Diagnosis 的签审过程；UI 使用签审记录等业务语言。

### M. Digital Pathology / Viewer

- **M01** DigitalSlide
- **M02** DigitalSlide → Case
- **M03** optional Block
- **M04** optional Slide
- **M05** 一个 Slide 多 DigitalSlide
- **M06** manual bind
- **M07** rebind
- **M08** bind audit
- **M09** WSI open
- **M10** zoom
- **M11** pan
- **M12** fullscreen
- **M13** minimap
- **M14** multiple slide switch
- **M15** annotation
- **M16** measurement
- **M17** screenshot
- **M18** slide metadata
- **M19** viewer link
- **M20** no-WSI stable state
- **M21** remote viewing

V2 约束：Diagnosis 首屏为 Material + 最大 Viewer + Diagnosis；无 WSI 时布局稳定。

### N. Report

- **N01** DiagnosisTemplate
- **N02** ReportTemplate
- **N03** Template Designer
- **N04** Structured elements
- **N05** single-select
- **N06** multi-select
- **N07** input
- **N08** free text
- **N09** 常用肿瘤模板
- **N10** report preview
- **N11** pagination
- **N12** PDF
- **N13** PDF encryption
- **N14** CA signature
- **N15** sign-out
- **N16** withdrawal
- **N17** re-sign
- **N18** supplemental report
- **N19** historical report
- **N20** immutable signed artifact
- **N21** report status
- **N22** report TAT
- **N23** frozen report
- **N24** self-service printing
- **N25** report distribution

V2 约束：一次签发一份 Report；撤回后继续编辑原 Diagnosis，再签发产生新 Report，禁止 ReportVersion wrapper。

### O. Workbench

- **O01** 每个用户自己的工作台
- **O02** 根据业务权限显示 Queue
- **O03** 根据数据权限筛选 Case
- **O04** 根据业务事实生成待办
- **O05** Registrar queues
- **O06** Grossing queues
- **O07** Technician queues
- **O08** Doctor queues
- **O09** Audit queues
- **O10** Multi-role aggregation
- **O11** overdue warning
- **O12** notifications
- **O13** personal workload
- **O14** quick actions
- **O15** complete and next
- **O16** retain queue/filter/sort/scroll state

V2 约束：Workbench 由权限、数据范围和业务事实决定；禁止所有用户相同队列或全业务菜单首页。

### P. Case Center

- **P01** Case Header
- **P02** patient
- **P03** clinical info
- **P04** Application
- **P05** Specimen
- **P06** Grossing
- **P07** Block
- **P08** Slide
- **P09** DigitalSlide
- **P10** Frozen
- **P11** TechnicalOrder
- **P12** Diagnosis
- **P13** Report
- **P14** Archive
- **P15** Loan
- **P16** complete timeline
- **P17** audit history

V2 约束：Workbench、Case Center、Focused Workspace 是并列职责；返回必须 Return To Origin。

### Q. 分子病理

- **Q01** Molecular BusinessType
- **Q02** 独立检测号
- **Q03** 分子申请
- **Q04** 检测项目
- **Q05** 标本
- **Q06** raw data
- **Q07** structured result
- **Q08** analysis result
- **Q09** instrument
- **Q10** reagent kit
- **Q11** equipment binding
- **Q12** reagent kit binding
- **Q13** molecular diagnosis
- **Q14** molecular report template
- **Q15** molecular report
- **Q16** routine report linkage
- **Q17** DigitalSlide/附件支持
- **Q18** workbench queue

### R. 人员与权限

- **R01** Account
- **R02** login
- **R03** logout
- **R04** activation
- **R05** disable
- **R06** password management
- **R07** User
- **R08** Staff Profile
- **R09** Role
- **R10** multiple roles
- **R11** Business Permission
- **R12** Data Permission
- **R13** Action Permission
- **R14** individual override
- **R15** minimum privilege
- **R16** scheduling
- **R17** operation log
- **R18** permission audit

V2 约束：Role 仅是权限模板，最终行为由 Permission 决定，前端不得按角色名硬编码。

### S. 档案

- **S01** Block archive
- **S02** Slide archive
- **S03** archive location
- **S04** batch archive
- **S05** archive query
- **S06** physical inventory
- **S07** archive inspection
- **S08** loan
- **S09** multiple materials loan
- **S10** expected return
- **S11** return
- **S12** overdue
- **S13** destruction
- **S14** destruction approval
- **S15** history

### T. 包裹 / 外送 / 物流

- **T01** external consultation package
- **T02** package contents
- **T03** Block
- **T04** Slide
- **T05** documents
- **T06** courier company
- **T07** tracking number
- **T08** recipient
- **T09** sender
- **T10** logistics tracking
- **T11** logistics abnormal
- **T12** common addresses
- **T13** loan relationship
- **T14** return relationship

### U. 危急值与沟通

- **U01** Critical Value
- **U02** grading
- **U03** clinical notification
- **U04** notification method
- **U05** notification time
- **U06** recipient
- **U07** acknowledgement
- **U08** feedback
- **U09** report
- **U10** communication history
- **U11** multi-condition search
- **U12** export
- **U13** TAT

### V. QC

- **V01** specimen QC
- **V02** grossing QC
- **V03** block QC
- **V04** slide QC
- **V05** IHC QC
- **V06** frozen QC
- **V07** diagnosis QC
- **V08** report QC
- **V09** TAT QC
- **V10** custom QC indicators
- **V11** daily
- **V12** weekly
- **V13** monthly
- **V14** quarterly
- **V15** annual
- **V16** statistics
- **V17** charts
- **V18** export
- **V19** abnormal warning

V2 约束：QC 评价业务事实，不控制正常病理流程。

### W. 质量体系文档

- **W01** quality manual
- **W02** procedure documents
- **W03** work instructions
- **W04** forms
- **W05** upload
- **W06** online view
- **W07** revision
- **W08** revision history
- **W09** review
- **W10** archive
- **W11** permission
- **W12** audit

### X. 设备管理

- **X01** equipment registry
- **X02** manufacturer
- **X03** model
- **X04** serial
- **X05** location
- **X06** custodian
- **X07** status
- **X08** operating program
- **X09** usage
- **X10** maintenance
- **X11** preventive maintenance
- **X12** repair
- **X13** fault
- **X14** warranty
- **X15** procurement record
- **X16** lifecycle
- **X17** warning
- **X18** statistics

### Y. 试剂耗材

- **Y01** consumable catalog
- **Y02** reagent catalog
- **Y03** supplier
- **Y04** manufacturer
- **Y05** purchase
- **Y06** inbound
- **Y07** outbound
- **Y08** requisition
- **Y09** inventory
- **Y10** storage location
- **Y11** batch
- **Y12** expiry
- **Y13** expiry warning
- **Y14** usage
- **Y15** automatic consumption linkage
- **Y16** reagent quality evaluation
- **Y17** hazardous chemicals
- **Y18** inventory report
- **Y19** consumption report
- **Y20** audit

### Z. 采购

- **Z01** purchase request
- **Z02** approval
- **Z03** items
- **Z04** quantity
- **Z05** amount
- **Z06** threshold
- **Z07** approval history
- **Z08** procurement progress
- **Z09** contract attachment
- **Z10** inbound link
- **Z11** archive
- **Z12** income reference/statistics

### AA. 科室空间

- **AA01** department space
- **AA02** hierarchical space
- **AA03** polluted zone
- **AA04** semi-polluted zone
- **AA05** buffer zone
- **AA06** clean zone
- **AA07** area
- **AA08** administrator
- **AA09** 360-degree view
- **AA10** temperature
- **AA11** humidity
- **AA12** hazardous gas
- **AA13** fire safety
- **AA14** hazardous storage
- **AA15** threshold
- **AA16** alarm
- **AA17** history

### AB. Statistics / BI

- **AB01** case statistics
- **AB02** specimen statistics
- **AB03** workload
- **AB04** doctor workload
- **AB05** technician workload
- **AB06** income
- **AB07** TAT
- **AB08** overdue reports
- **AB09** TechnicalOrder
- **AB10** IHC
- **AB11** molecular
- **AB12** frozen
- **AB13** cytology
- **AB14** slide
- **AB15** DigitalSlide
- **AB16** QC
- **AB17** organization
- **AB18** department
- **AB19** clinician
- **AB20** disease
- **AB21** time
- **AB22** charts
- **AB23** table
- **AB24** drill-down
- **AB25** Excel
- **AB26** PDF
- **AB27** configurable report

V2 约束：管理 Dashboard 与普通 Workbench 分离。

### AC. System Configuration

- **AC01** BusinessType
- **AC02** PathologyNumberRule
- **AC03** ApplicationItemMapping
- **AC04** TechnicalProject
- **AC05** FeeItemMapping
- **AC06** DiagnosisTemplate
- **AC07** ReportTemplate
- **AC08** TAT policy
- **AC09** dictionaries
- **AC10** hospitals
- **AC11** campuses
- **AC12** departments
- **AC13** clinicians
- **AC14** experts
- **AC15** subspecialties
- **AC16** Case library configuration
- **AC17** printing configuration
- **AC18** QC configuration
- **AC19** available actions
- **AC20** feature capabilities

### AD. 院内互联互通

- **AD01** HIS
- **AD02** LIS
- **AD03** PACS
- **AD04** EMR
- **AD05** anesthesia
- **AD06** health examination
- **AD07** integration platform
- **AD08** master data
- **AD09** patient
- **AD10** visit
- **AD11** department
- **AD12** ward
- **AD13** doctor
- **AD14** order item
- **AD15** fee item
- **AD16** application create
- **AD17** application update
- **AD18** application cancel
- **AD19** registration acknowledgement
- **AD20** specimen status
- **AD21** diagnosis status
- **AD22** report status
- **AD23** diagnosis content
- **AD24** report PDF
- **AD25** critical value
- **AD26** fee confirmation
- **AD27** fee cancellation/refund

V2 约束：集成失败必须记录、重试、可观察；收费状态不得阻塞核心病理流程。

### AE. CA / 无纸化

- **AE01** CA login
- **AE02** CA certificate
- **AE03** electronic signature
- **AE04** report signing
- **AE05** signature verification
- **AE06** audit
- **AE07** PDF signing
- **AE08** authorization change signature extension point

V2 约束：无真实 CA 时只允许 Port/Adapter/Simulator/Configuration/Audit，并标记 External Dependency。

### AF. Province / City Platform

- **AF01** 湖北省病理平台
- **AF02** 病例上传
- **AF03** 诊断上传
- **AF04** IHC order
- **AF05** consultation
- **AF06** consultation result
- **AF07** consultation cancellation
- **AF08** validation result
- **AF09** upload retry
- **AF10** upload log
- **AF11** 武汉市区域病理中心
- **AF12** case data
- **AF13** consultation
- **AF14** cancellation
- **AF15** diagnosis feedback
- **AF16** patient holistic view extension
- **AF17** health-record extension

### AG. Data Collection Front Service

- **AG01** multi-system collection
- **AG02** scheduled collection
- **AG03** manual collection
- **AG04** progress
- **AG05** result
- **AG06** failed records
- **AG07** raw dataset
- **AG08** deduplication
- **AG09** correction
- **AG10** field normalization
- **AG11** ICD-10 mapping
- **AG12** ICD-9-CM-3 mapping
- **AG13** data validation
- **AG14** logical validation
- **AG15** abnormal data
- **AG16** manual correction
- **AG17** correction audit
- **AG18** reporting format configuration
- **AG19** reporting fields
- **AG20** reporting filter
- **AG21** reporting file
- **AG22** task scheduler
- **AG23** execution cycle
- **AG24** priority
- **AG25** pause
- **AG26** restart
- **AG27** retry
- **AG28** pre-audit
- **AG29** error location
- **AG30** correction suggestions
- **AG31** revalidation
- **AG32** secure transmission
- **AG33** encryption
- **AG34** resume upload
- **AG35** integrity validation
- **AG36** transfer audit

### AH. Digital Slide Archive

- **AH01** scanner adapters
- **AH02** slide import
- **AH03** WSI format recognition
- **AH04** MRXS
- **AH05** NDPI
- **AH06** SVS
- **AH07** other vendor format adapter
- **AH08** storage path
- **AH09** storage tier
- **AH10** filename rule
- **AH11** index
- **AH12** search
- **AH13** pathology number
- **AH14** slide number
- **AH15** patient
- **AH16** organ
- **AH17** PIS binding
- **AH18** integrity check
- **AH19** archive
- **AH20** restore
- **AH21** remote viewing

V2 约束：优先原格式读取和 adapter architecture，不机械统一转换 WSI。

### AI. AI Integration

- **AI01** AI Provider Port
- **AI02** analysis request
- **AI03** analysis result
- **AI04** model metadata
- **AI05** model version
- **AI06** confidence
- **AI07** lesion location
- **AI08** heatmap
- **AI09** overlay
- **AI10** result history
- **AI11** rerun
- **AI12** compare results
- **AI13** structured result import
- **AI14** Viewer integration
- **AI15** lung provider
- **AI16** gastric provider
- **AI17** colorectal provider
- **AI18** prostate provider

V2 约束：无真实模型时只实现 Port/Adapter/Simulator/Schema/UI/Overlay，不伪造临床性能。

### AJ. 区域协同 / 转诊

- **AJ01** organization
- **AJ02** external pathology organization
- **AJ03** consultation doctor
- **AJ04** consultation permissions
- **AJ05** shared case
- **AJ06** WSI sharing
- **AJ07** patient-authorized distribution
- **AJ08** receiving organization
- **AJ09** expiration
- **AJ10** access log
- **AJ11** notification
- **AJ12** workload
- **AJ13** fee/settlement extension
- **AJ14** regional statistics

### AK. 报告发放

- **AK01** HIS
- **AK02** EMR
- **AK03** clinician query
- **AK04** patient query
- **AK05** APP extension
- **AK06** WeChat extension
- **AK07** self-service terminal
- **AK08** self-service print
- **AK09** OR frozen delivery
- **AK10** delayed report
- **AK11** delivery history
- **AK12** printer status
- **AK13** print history

### AL. Security

- **AL01** authentication
- **AL02** authorization
- **AL03** least privilege
- **AL04** audit
- **AL05** sensitive data classification
- **AL06** encryption-at-rest extension
- **AL07** encrypted transport
- **AL08** masking
- **AL09** export control
- **AL10** export audit
- **AL11** login lock
- **AL12** operation logs
- **AL13** error logs
- **AL14** integration logs
- **AL15** immutable audit extension
- **AL16** backup
- **AL17** restore
- **AL18** security events
- **AL19** key management port
- **AL20** national cryptography extension

V2 约束：真实密码机/国密环境属于 External Dependency。

### AM. Data Migration

- **AM01** legacy source
- **AM02** extraction
- **AM03** transformation
- **AM04** mapping
- **AM05** validation
- **AM06** case counts
- **AM07** report validation
- **AM08** WSI mapping
- **AM09** incremental migration
- **AM10** retry
- **AM11** error list
- **AM12** new/old mapping
- **AM13** audit
- **AM14** read-only legacy
- **AM15** historical query

V2 约束：V2 使用干净新 Schema；Legacy 只作为迁移源，不长期依赖旧 Domain。

### NFR. 非功能要求

- **NFR01** B/S
- **NFR02** browser based
- **NFR03** modularity
- **NFR04** configurable business rules
- **NFR05** configurable templates
- **NFR06** configurable numbering
- **NFR07** 50 concurrent users
- **NFR08** core operations 20 concurrent users
- **NFR09** 1000 cases/day
- **NFR10** common page/query <=2s target
- **NFR11** save/update/delete generally <=1s, max <=3s target
- **NFR12** simple 50k query <=3s target
- **NFR13** 100k query <=6s target
- **NFR14** complex report <=30s
- **NFR15** normal report <=5s
- **NFR16** WSI first view <=2s target
- **NFR17** availability target >=99.9%
- **NFR18** backup
- **NFR19** recovery
- **NFR20** error handling
- **NFR21** clear UI
- **NFR22** role-specific UI
- **NFR23** reduce irrelevant functions
- **NFR24** shortcuts
- **NFR25** batch processing
- **NFR26** navigation <=3 levels
- **NFR27** logs
- **NFR28** maintainability
- **NFR29** extensibility
- **NFR30** multi-campus
- **NFR31** data isolation
- **NFR32** data sharing policy

## 4. 基线规模

- 功能与非功能要求合计：**750**
- 功能组：A–AM
- 非功能组：NFR

## 5. 冲突裁决

SRS 决定系统必须具备的能力；PIS V2 Final Domain Baseline 决定能力的正确实现。任何覆盖工作必须保存患者安全、对象独立生命周期、不可篡改报告、审计、幂等、权限和数据隔离边界。
