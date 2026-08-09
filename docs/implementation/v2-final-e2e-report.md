# PIS V2 最终浏览器 E2E 报告

## 1. 环境

验证环境为 Docker Compose：PostgreSQL 18.4、V2 backend、V2 frontend。所有病例、患者引用、外部医院、阅片器和借阅人均为合成数据。真实医院接口、真实扫描仪/WSI、生产打印和生产认证环境均标记为 `EXTERNAL ENVIRONMENT NOT VERIFIED`。

## 2. E2E Matrix

| 场景 | 结果 | 业务结果证据 |
|---|---|---|
| E2E-01 Routine | PASS | H-000005 从真实登录、登记、Specimen、Grossing、Block、INITIAL Slide 完成、诊断责任链、Preview、Sign-out 到 R001 PDF。 |
| E2E-02 Technical Loop | PASS | H-000005 的同一 Case 创建 `TO001`/`MOLECULAR-STRUCTURED`，技术工作台录入结构化 Result，原 Diagnosis Workspace 可见结果并完成签发；未创建 Molecular Case。 |
| E2E-03 Withdraw / Re-sign | PASS | M-000001 的 R001 保留为 `WITHDRAWN`，修改 Diagnosis 后 R002 为 `EFFECTIVE`。 |
| E2E-04 Supplemental | PASS | R-000001 在 R001 `EFFECTIVE` 后签发 S001 `SUPPLEMENTAL/EFFECTIVE`；原 R001 仍有效。 |
| E2E-05 Frozen 1 Round | PASS | F-000001 完成 Round 1 的材料、Diagnosis、Preview、Report 和 Frozen End。 |
| E2E-06 Frozen 2+ Rounds | PASS | F-000002 完成 Round 1 独立签发；签发后材料创建 Round 2；Round 2 包含独立材料、Diagnosis、Report；Frozen End 后只有一个 Routine Case，重复 End 返回同一 Routine Case。 |
| E2E-07 Cytology | PASS | C-000002 使用 `Specimen → direct Slide`，没有伪造 Block；完成 1/1 Slide、认领、INITIAL/REVIEW/AUDIT、PDF 签发。 |
| E2E-08 Molecular Independent | PASS | M-000001 通过 `BusinessType=MOLECULAR` 建立独立 Case/PathologyNo，录入 Structured Result 后在同一 Case 完成 Diagnosis/Report。 |
| E2E-09 Molecular via TechnicalOrder | PASS | Routine H-000005 的 Diagnosis→TechnicalOrder→Structured Result 回到原 Case；TechnicalOrder 结果在 Workspace 可见，未产生新的 Molecular Case。 |
| E2E-10 Consultation | PASS | R-000001 建立本院 Case/PathologyNo；External Block 生成统一 V2 Local Slide，完成后进入 Diagnosis/Report。 |
| E2E-11 DigitalSlide | PASS | H-000005 的 DigitalSlide 绑定 Case/Block/Slide，更新 viewer reference 后完成手工改绑；不阻塞物理 Slide 完成和签发。 |
| E2E-12 Archive / Loan / Return | PASS | Slide 归档至 ArchiveLocation，登记 Loan，归还后归档位置仍保留。 |
| E2E-13 Global Search | PASS | 工作台全局查询 `H-000005` 返回 CASE 结果，并进入材料上下文工作台。 |
| E2E-14 QC Warning | PASS | QC 事实评估产生 NORMAL/OVERDUE 事实结果；页面明确提示 QC 提醒默认不阻断签发。基础统计可见 registration/specimen/material/diagnosis/report/frozen/technicalOrder 指标。 |
| E2E-15 Real Auth / Responsibility | PASS | 真实登录 `doctor-a`/`doctor-b`/`doctor-c`、registrar、technician；Doctor A/B/C 分别形成责任链，签发者为 DOC-C；registrar/technician 签发按钮禁用并被权限/责任阻断。 |

## 3. 认证和责任证据

- `doctor-a` 登录后显示 `Doctor A（DOC-A）`。
- Cytology 链验证 `INITIAL=DOC-A`、`REVIEW=DOC-B`、`AUDIT/SIGN=DOC-C`。
- Audit 医生不匹配时收到 `AUDIT_DOCTOR_MISMATCH`，不能代签。
- registrar 和 technician 没有签发权限；权限边界在浏览器中可见并由后端再次校验。

## 4. 机器验证

- Backend：`./mvnw.cmd -B -ntp clean verify`，32/32 tests，0 failures，0 errors。
- PostgreSQL/Flyway：Testcontainers 使用 PostgreSQL 18.4，空库从 V1 到 V20 完成 clean migration；应用运行库已验证 schema version 20。
- Frontend：format、lint、typecheck、unit、build 全部通过；unit 为 6 files / 8 tests。
- Architecture：Module Boundaries 和 V2 Architecture Drift 全部通过。
- Flyway 仍报告 PostgreSQL 18.4 高于当前已测试支持的 PostgreSQL 17；这是兼容性提示，不是 migration/integration failure。

## 5. 未验证的外部环境

真实 HIS/LIS/EMR、消息中间件重放、真实扫描仪/WSI tile 服务、生产 PDF 打印链、CA/电子签章、真实 LDAP/OIDC/组织目录、生产部署、医院用户验收、医院历史数据迁移均未验证，不得将本报告解释为医院联调或生产批准。
<!-- End of report -->
