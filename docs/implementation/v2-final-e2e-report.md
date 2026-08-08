# PIS V2 最终浏览器 E2E 报告

## 1. 环境与数据边界

验证环境为本地 Docker Compose：PostgreSQL 18.4、V2 backend、V2 frontend。所有病例、患者、就诊、阅片器和借阅人均为合成标识。外部医院系统、扫描仪、真实 WSI 平台和真实认证环境未验证，统一标记为 `EXTERNAL ENVIRONMENT NOT VERIFIED`。

## 2. 已通过场景

| 场景 | 结果 | 说明 |
|---|---|---|
| E2E-01 Routine | PASS | 登记 H-000001、标本、Grossing、Block、INITIAL Slide、制片完成、公共池认领、INITIAL/REVIEW/AUDIT、预览、R001 签发和 PDF 获取。 |
| E2E-02 Technical Loop | PASS | H-000002 诊断创建 IHC TechnicalOrder；技术工作台执行并生成正式 IHC Slide；完成产出切片；诊断工作区显示技术 Slide；完成责任链并签发 R001。 |
| E2E-03 Withdraw/Re-sign | PASS | R001 撤回、诊断修改、AUDIT 重开、R002 签发；R001 历史保留为 WITHDRAWN，R002 为 EFFECTIVE。 |
| E2E-04 Supplemental | CONDITIONAL | 后端报告测试覆盖 Supplemental；本轮浏览器未完成从有效 R001 到新结果再到 Supplemental R002 的全流程。 |
| E2E-05 Frozen | CONDITIONAL | F-000001、Round 1、FROZEN_ROUND Slide、FrozenRound Diagnosis、Report Preview/Sign-out 和 Frozen End → Routine Case 已通过浏览器；Round 2+ 的浏览器验证仍待补齐。 |
| E2E-06 Cytology | CONDITIONAL | V2 API/领域测试支持 Specimen→Slide→Diagnosis→Report；未完成浏览器签发链。 |
| E2E-07 Digital | PASS | DigitalSlide 创建、Case/Block/Slide 绑定、手工改绑已通过浏览器；阅片器使用合成 viewer reference。 |
| E2E-08 Archive/Loan | PASS | Slide/Block 批量归档、借阅、归还已通过浏览器；归档位置未因借阅而丢失。 |
| Search/QC/Statistics | PASS | 全局查询、质控统计工作区、QC 规则/事实评估、基础统计已通过浏览器。 |

## 3. 机器验证

1. `backend`: `./mvnw.cmd -B -ntp clean verify`，32 tests passed。
2. `frontend`: format check、lint、typecheck、build passed；6 test files / 8 tests passed。
3. PostgreSQL clean bootstrap：V1→V19 migrations passed，V2 registration/PostgreSQL integration passed。
4. Architecture drift and module boundaries passed；当前活动 Spring Modulith module count 为 12。
5. PostgreSQL 18.4 仍产生 Flyway “newer than latest supported PostgreSQL 17”兼容提示；未导致 migration/integration failure。

## 4. 未验证项

真实医院接口、消息乱序/重放、真实扫描仪回调、WSI tile 服务、真实 PDF 打印链、CA/电子签章供应商、真实登录和真实角色组织数据均未验证。相关结果不能作为生产联调或用户验收结论。
