# V2-I05 实施报告

## 1. 结论

V2-I05 已实现 Diagnosis 到报告签发的闭环，未进入 V2-I06。

## 2. 后端实现

1. Flyway V16 新增 `report_template`、`report_template_version`、`report`、`report_pdf_output` 和报告命令幂等表，并为现有业务类型播种默认已发布模板。
2. 新增 `V2ReportApplicationService`、报告领域类型、JDBC 仓储、PDF 渲染适配器和 Web Controller。
3. 提供预览、签发、撤回、补充、报告详情、历史、有效报告和 PDF 下载接口。
4. 签发在同一事务内校验 ACTIVE Case、Diagnosis 有效性、最后 AUDIT 医生、已发布模板和 I04 技术医嘱阻断，并写入报告快照、PDF、Audit、Outbox 和幂等记录。
5. 撤回保留原始快照/PDF，仅重开最后一个未结束 AUDIT 责任节点；重新签发生成新的 `R002` 等报告事实。
6. 诊断报告生效后禁止普通 Diagnosis 编辑；撤回后允许最后 AUDIT 医生继续处理。

## 3. 前端实现

V2 Diagnosis Workspace 增加后端驱动的 `canPreview`、`canSignOut`、`canWithdraw`、`canSupplement`、阻断原因、预览内容、报告历史、PDF 入口、撤回和补充报告操作。

## 4. 验证证据

1. `mvn -B -ntp -Dtest=V2DiagnosisWebTest test`：5/5 通过。
2. `mvn -B -ntp -Dtest=V2ReportWebTest test`：1/1 通过，覆盖预览不落库、R001、PDF、撤回重开、R002、S001、历史和有效报告。
3. `mvn -B -ntp -Dtest=V2RegistrationPostgresIntegrationTest test`：PostgreSQL/Testcontainers V16 迁移验证通过，包含 8 个默认模板版本和 4 张报告相关表。
4. `npm.cmd run typecheck`、`npm.cmd run test:unit -- --run`、`npm.cmd run format:check` 和 `npm.cmd run build`：已通过；前端既有 ESLint 警告仍需单独治理，I05 新增文件无业务错误。

## 5. 假设与剩余风险

1. 当前测试和默认 actor 均为合成身份，不代表真实医院 User/Doctor 主数据联调。
2. PDF 内容已持久化并可下载，但医院正式字体、版式和电子签章属于后续适配工作。
3. 当前报告补充内容作为签发命令输入和快照字段保存，不增加 DiagnosisSupplement 核心实体。
