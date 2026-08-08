# PIS-Next V2 P04 数据模型计划

文档状态：计划
文档版本：V2-0.1
前置：P00-P03

## 1. 目标

将 P01 的稳定身份、来源链、不变量和模块所有权落为可迁移、可审计、可并发控制的数据库模型。当前不冻结表结构，不读取旧数据库，不把本计划当作已完成数据库设计。

## 2. 设计原则

- 内部 ID 与业务编号分离，核心外键使用稳定内部 ID；
- Case、Specimen、Grossing、Block、Slide、Diagnosis、Report、TechnicalOrder 等核心对象使用明确关系；
- 不创建 PlannedBlock、ActualBlock、PlannedSlide、ActualSlide 或 ReportVersion 表；
- 不用 EAV 或无约束 JSON 承载核心关系；模板的可变结构与核心身份分离；
- 软删除、撤回、取消、失效和销毁均保留事实；
- 重要写入具备乐观锁或等效并发控制；
- 关键写操作具有明确事务边界、审计和幂等键；
- 每次迁移使用不可修改的版本化迁移脚本。

## 3. 待设计实体组

1. 身份和配置：Case、BusinessType、ApplicationItemMapping、PathologyNumberRule；
2. 材料主链：Specimen、Grossing、Block、Slide、PrintLog；
3. 诊断和报告：Diagnosis、TemplateVersion、Responsibility、Assignment、Report、ReportTemplate；
4. 技术和数字：TechnicalOrder、TechnicalOrderItem、Target、TechnicalRecord、DigitalSlide、MolecularResult；
5. 特殊业务：FrozenRound、ExternalMaterial、ExternalResult、SendOut；
6. 横向治理：Audit、Idempotency、Outbox/Inbox、Lock、File、ArchiveHistory、Loan、QCEvent。

## 4. P04 验收出口

- ER 关系和所有权通过 P03 复核；
- 每个核心对象有唯一约束、外键、索引、状态/事实保护策略；
- 并发、事务、审计、软删除和幂等方案有测试入口；
- 数据字典不出现 V2 禁止概念；
- 不开始真实迁移，迁移进入独立 P05 文档。
