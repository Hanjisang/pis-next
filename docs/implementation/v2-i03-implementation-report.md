# V2-I03 实施报告

## 1. 结论

V2-I03 已完成本阶段实施并通过当前可运行门禁。V2 Diagnosis 使用 Case 级连续编辑事实；INITIAL、REVIEW、AUDIT 仅作为 ResponsibilityUnit 责任链节点，不创建 Diagnosis 状态机、DiagnosisVersion 或 DiagnosisTask。工作区查询为事务只读聚合，不创建 `diagnosis_workspace` 源表。

## 2. 已实现范围

### 后端

1. Flyway V14：`diagnosis_template`、`diagnosis_template_version`、`diagnosis`、`responsibility_unit`、`assignment_rule`、`diagnosis_command_idempotency`。
2. DiagnosisTemplate 与 DiagnosisTemplateVersion 分离；已发布模板版本可并存、按版本号解析当前版本，已发布版本由应用规则和 PostgreSQL 触发器保护为不可变。
3. Diagnosis 固定保存 `structuredData`、`microscopicDescription`、`diagnosisText`、`comment` 和乐观锁版本。
4. ResponsibilityChain 累积保存 role、doctor、sequence、accepted/completed/ended、assignment source/reason 和版本；重分配关闭旧节点并新增节点。
5. 公开池查询和自主认领、手工分配、重分配；认领/分配先锁定 Case，同一 Case 并发认领只有一个成功。
6. 诊断保存和 INITIAL/REVIEW/AUDIT 完成命令；审核完成只返回 `readyForSignOut` 投影，不创建 Report 或签发事实。
7. `GET /api/v2/diagnosis-workspaces/{caseId}` 聚合 Case、患者/就诊快照、申请、V2 Material Tree、Diagnosis、模板版本、责任链、动作能力和后续占位；另提供公开池查询。
8. 复用 P15 Authorization、Audit、Outbox 和 I02 Material Tree 只读查询，不写 Legacy Diagnosis/Report 表。

### 前端

1. 新增独立 `workspace=v2-diagnosis&caseId=...` 路由入口和 `V2DiagnosisWorkspace`。
2. 布局包含 Header、Case/Patient/Application 快照、Specimen→Block→Slide 材料树、按 DiagnosisTemplateVersion 动态渲染的基础结构化组件、自由文本/备注、责任链、分配/重分配/完成命令和 TechnicalOrder/Report 占位。
3. 使用显式保存和 Diagnosis version；冲突保留错误反馈，不实现未经确认的自动保存。
4. 前端不提交 CaseStatus、completed、signed 或 withdrawn 等核心状态字段。

## 3. 测试证据

| 验证 | 结果 |
|---|---|
| `mvn -B -ntp -Dtest=V2DiagnosisWebTest test` | 5/5 通过，含认领幂等、单签/三签责任链、模板版本、重分配、READY_FOR_SIGN_OUT 和并发认领 |
| `mvn -B -ntp -Dtest=V2RegistrationPostgresIntegrationTest test` | 1/1 通过，含 PostgreSQL V14/Flyway、JSONB 写入和已发布版本不可变 |
| 现有 V2 Web/Architecture 测试 | 8/8 通过 |
| `mvn -B -ntp clean verify` | 50/50 通过，构建成功 |
| `npm.cmd run typecheck` | 通过 |
| `npm.cmd run test:unit -- --run` | 8 个测试文件、9 个测试通过 |
| `npm.cmd run format:check` | 通过 |
| `npm.cmd run build` | 通过 |
| ESLint | I03 新增文件 0 错误/0 警告；仓库已有 I02 Vue 模板 12 条格式警告仍存在 |

## 4. 权限与待确认

I03 语义权限映射到当前 P14 共享能力：DIAGNOSIS_VIEW→P14-PERM-055，INITIAL/REVIEW/AUDIT/ASSIGN/REASSIGN→P14-PERM-034，TEMPLATE_MANAGE→P14-PERM-042。当前仓库没有可供 V2 直接引用的 User/Doctor 主数据表，因此 I03 对 doctorId 执行非空和责任一致性校验；主体存在性、资格和组织范围由 identity-access 适配器接入时补强，属于“待业务确认”。

TechnicalOrder、DigitalSlide、Report 和正式签发不在 I03 实施范围，工作区只显示占位，不伪造后续领域事实。V2-I04 未启动。

## 5. 检查结论

- 未创建 `InitialDiagnosis`、`ReviewDiagnosis`、`AuditDiagnosis`、`SingleSignWorkflow`、`DoubleSignWorkflow`、`TripleSignWorkflow`、`DiagnosisTask` 或 `AssignmentTask`。
- 未创建 `DiagnosisVersion`；`DiagnosisTemplateVersion` 仅表示模板配置版本。
- 未发现 V2 代码到 Legacy Diagnosis/Report/Task/CaseStatus 的依赖。
- 测试和文档只使用虚构/合成数据；未发现密钥、Token、真实患者信息或生产连接配置。
