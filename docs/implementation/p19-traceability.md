# P19 追溯矩阵

| P19规则/结果 | 实现 | 验证 |
|---|---|---|
| 任务与意见分离 | `p19_diagnosis_task`、`p19_diagnosis_opinion_version` | 应用测试、V8 schema |
| 草稿与版本分离 | `p19_diagnosis_work_draft` | 应用测试 |
| 完整报告快照 | `p19_report_content_version`、section 表 | 应用测试、Docker smoke |
| 独立审核与职责分离 | `p19_diagnosis_review`、应用守卫 | 应用/Web 测试 |
| 高风险签发 | `EnhancedAuthenticationPort`、signing fact | 应用测试、异常 smoke |
| 版本/事实不可变 | CHECK、UNIQUE、应用状态守卫 | JDBC/并发测试 |
| 补充/更正/撤回 | revision/supplement/correction/withdrawal 表 | 修订 smoke |
| P18 技术结果边界 | 结果引用表与阻断分类查询 | P15～P18 回归、P19 sign gate |
| 审计/outbox | 既有 `JdbcAuditEventRepository`、`OutboxPort` | 事务测试、数据库查询 |

孤儿检查：最终关闭前以 `rg`、`git diff --check`、迁移清单、API/权限映射和脚本实际输出为准。
