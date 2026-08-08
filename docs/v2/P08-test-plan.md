# PIS-Next V2 P08 测试计划

文档状态：计划
文档版本：V2-0.1

## 1. 测试层级

- 领域单元：身份、来源、软删除、责任链、Report 签发/撤回/重新签发；
- 应用集成：Case → Specimen → Grossing → Block → Slide → Diagnosis → Report；
- 数据库集成：外键、唯一性、事务、乐观锁、幂等和审计；
- API/契约：命令边界、权限、错误、外部消息和回传；
- 前端：Diagnosis Workspace、搜索抽屉、打印/补打、报告历史；
- 端到端：常规、Frozen、Cytology、Molecular、Consultation、Send-out；
- 恢复和运维：备份恢复、文件完整性、接口死信和重放。

## 2. 必测 V2 场景

至少覆盖：一申请多 Case、一 Case 多 Specimen、一 Grossing 多 Specimen、补取材、Block/Slide 来源、打印补打、TechnicalOrder 多 Item/Target、诊断责任链、自审、Review→Audit、Report withdraw/resign、Frozen 多 Round、Frozen→Routine、数字切片未完成不阻断诊断、外部失败不回滚、内部失败一致回滚、QC 提醒不阻断、并发编辑和权限越权。

## 3. 架构偏离扫描

CI 和代码审查必须扫描并阻断新增：`PlannedBlock`、`ActualBlock`、`PlannedSlide`、`ActualSlide`、`CaseStatus`、`ReportVersion`、`Generic TechnicalResult`、实验室动作 Task 主流程及医院项目号硬编码分支。

测试通过不能替代领域审查；旧测试不作为 V2 正确性的证明。
