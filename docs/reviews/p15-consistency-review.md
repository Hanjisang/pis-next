# P15 登记与标本接收一致性与关闭审查

日期：2026-08-06

结论：通过

以下审查针对 P15 规定范围执行；“生产 IAM、正式机构字典、医院协议、取材及下游病理业务”是已记录的后续边界，不属于本阶段阻塞项。

## 逐项审查

1. [x] P04～P14 均已完成；依据：既有阶段关闭记录。
2. [x] P15 范围与 MASTER_PLAN 一致；依据：`docs/project/MASTER_PLAN.md` 与实现范围文档。
3. [x] Git 基线正确；依据：HEAD 与 origin/main 均为 P14 完成基线，工作区起始时干净。
4. [x] 只实现登记与标本接收；依据：P15 实现范围文档和代码目录。
5. [x] 未实现取材、制片、诊断或报告；依据：后端/前端范围扫描。
6. [x] 使用现有模块化单体；依据：Spring Modulith 模块边界测试。
7. [x] 未创建新微服务；依据：Compose 只有 PostgreSQL、后端和前端适配器。
8. [x] 模块依赖验证通过；依据：`ModuleBoundariesTest`。
9. [x] 领域模型不依赖 Spring；依据：accession/specimen domain 源码审查。
10. [x] 未使用 JPA 或 Hibernate；依据：依赖与源码扫描。
11. [x] 未创建通用 CRUD；依据：P15 命令式应用服务和控制器审查。
12. [x] 未创建通用 setStatus；依据：源码扫描无匹配。
13. [x] P15 命令全部来自 P12 正式契约；依据：API/security mapping。
14. [x] P15 查询全部来自 P12 正式契约；依据：API/security mapping。
15. [x] P15 错误代码来自 P12；依据：P15BusinessException 使用 P12-ERR 集合。
16. [x] P15 权限来自 P14；依据：每个应用命令先执行 P14-PERM 校验。
17. [x] 所有写操作具有后端授权；依据：应用服务统一调用 P15AuthorizationService。
18. [x] 所有查询具有数据范围；依据：V3 organization_reference 与 SQL 参数过滤。
19. [x] 开发身份只在 local/test 有效；依据：运行环境门禁。
20. [x] 非 local/test 默认拒绝未认证业务访问；依据：P15AuthorizationService。
21. [x] 未创建默认密码或共享登录账号；依据：仅使用环境注入的 synthetic actor，不创建登录账号。
22. [x] 患者和就诊保持外部引用及快照定位；依据：context reference 与 patient_visit_snapshot。
23. [x] PIS 未被实现为患者主索引；依据：仅保存外部引用，不保存主索引。
24. [x] 内部身份、业务编号和外部标识已分离；依据：UUID、DEV-* 编号、外部 reference 三层结构。
25. [x] 业务编号并发唯一；依据：数据库唯一约束和 UUID-backed local allocator。
26. [x] 未使用 SELECT MAX + 1；依据：源码扫描与 allocator 审查。
27. [x] 未虚构正式病例编号格式；依据：生产/未配置环境拒绝 DEV 编号。
28. [x] 预计标本可追溯；依据：case_id、specimen_id、container_barcode 和 trace 查询。
29. [x] 标本接收形成不可变事实；依据：handoff_record 与 state_transition_history 追加写入。
30. [x] 标本拒收和隔离与正常接收分离；依据：P12-API-010 隔离命令与 business_exception。
31. [x] 接收责任和实际操作者分离；依据：operation_responsibility 与 ActorContext。
32. [x] 重复扫码不会形成重复接收；依据：重复 HTTP 烟测返回 duplicate=true。
33. [x] 相同幂等键不同载荷被拒绝；依据：外部登记摘要冲突返回 P12-ERR-003。
34. [x] 并发接收最多形成一次首次接收；依据：一成功、一 P12-ERR-024，单 handoff。
35. [x] 状态转换通过业务命令执行；依据：领域聚合和应用服务入口。
36. [x] 禁止转换不可被管理员绕过；依据：无通用状态更新入口，SQL 带源状态/版本条件。
37. [x] 患者安全不匹配可以阻断；依据：条码、来源、数量和版本校验。
38. [x] 不存在无审计强制通过；依据：成功写操作同事务追加 audit_event。
39. [x] P15 业务事件与业务事实同事务写入发件箱；依据：事务应用服务和 outbox_event。
40. [x] 失败事务不留下半成品；依据：事务边界和 JDBC 回滚语义；运行时异常回滚验证。
41. [x] 审计只追加；依据：audit_event 无更新/删除入口。
42. [x] 状态历史只追加；依据：state_transition_history 仅 INSERT。
43. [x] 无临床事实级联删除；依据：迁移无 DELETE/CASCADE 业务操作。
44. [x] 无通用软删除；依据：无 deleted/status 通用 CRUD 字段或入口。
45. [x] 无 EAV；依据：核心字段使用结构化表列。
46. [x] 无万能 JSON 业务模型；依据：P15 核心业务表无 JSON 承载字段。
47. [x] 只创建 P15 所需业务表；依据：V2 最小表集与 V3 范围列迁移。
48. [x] 未创建 P11 其余业务表；依据：迁移审查。
49. [x] 未锁定正式产品数据库；依据：PostgreSQL 仅作为参考运行实现。
50. [x] PostgreSQL 仍是参考实现；依据：P15 后端数据设计文档。
51. [x] 浏览器 Web 适配器未改变 P12 内部 API 结论；依据：控制器仅映射 DTO 到应用命令。
52. [x] 未生成虚假医院协议；依据：未创建医院协议或生产适配器。
53. [x] 未生成虚假医院字段；依据：外部字段均为合成引用或 P12 明确字段。
54. [x] 前端只包含 P15 页面；依据：P15RegistrationWorkbench。
55. [x] 扫码工作流可键盘操作；依据：条码输入框 autofocus、required 和 submit。
56. [x] 前端不以按钮隐藏代替授权；依据：后端每个命令再次授权。
57. [x] 前端不使用乐观假接收；依据：接收结果来自后端响应。
58. [x] 前端患者安全提示不只依赖颜色；依据：错误码、文字提示和状态输出。
59. [x] P15 领域测试通过；依据：PathologyRequestTest、SpecimenLifecycleTest。
60. [x] P15 应用测试通过；依据：应用路径由 Spring Boot 集成与 HTTP 烟测覆盖。
61. [x] P15 JDBC 集成测试通过；依据：JdbcSpecimenRepositoryTest 和 PostgreSQL 迁移烟测。
62. [x] P15 Web 测试通过；依据：健康、foundation、登记、接收、查询 HTTP 烟测。
63. [x] P15 前端测试通过；依据：Vitest 1 文件、1 测试通过。
64. [x] Maven verify 通过；依据：`backend\mvnw.cmd -B -ntp clean verify`。
65. [x] 前端 format 检查通过；依据：`npm.cmd --prefix frontend run format:check`。
66. [x] 前端 Lint 通过；依据：`npm.cmd --prefix frontend run lint`。
67. [x] 前端类型检查通过；依据：`npm.cmd --prefix frontend run typecheck`。
68. [x] 前端构建通过；依据：`npm.cmd --prefix frontend run build`。
69. [x] Docker 后端镜像构建通过；依据：`infra/docker/backend.Dockerfile`。
70. [x] Docker 前端镜像构建通过；依据：`infra/docker/frontend.Dockerfile`。
71. [x] Compose 配置验证通过；依据：`docker compose config`。
72. [x] 全栈运行验证通过；依据：三个容器运行，PostgreSQL healthy、后端 UP、前端 HTTP 200。
73. [x] Flyway 迁移实际成功；依据：V1、V2、V3 success=true。
74. [x] P15 规定范围实现和追溯完成；依据：p15-traceability.md。
75. [x] P15 规定范围验收场景完成；依据：手工、外部、重复、并发、数量不匹配路径烟测。
76. [x] P15 相关状态机覆盖完成；依据：P08-SM-001/002/003 代码和历史记录。
77. [x] P15 相关异常覆盖完成；依据：P12-ERR-003/021/022/023/024/025/026/027。
78. [x] P15 相关 API、权限和错误映射完成；依据：p15-api-security-mapping.md。
79. [x] 无来源业务实现为 0；依据：实现文档逐项列出正式来源编号。
80. [x] 无权限写操作为 0；依据：应用服务权限门禁审查。
81. [x] 无数据范围查询为 0；依据：V3 范围列和队列/条码/追溯 SQL。
82. [x] 无来源事件为 0；依据：outbox 仅使用 P12-EVC-001/002/003。
83. [x] 未修改 P04～P14 正式正文；依据：Git 修改文件审查。
84. [x] 未使用真实患者数据；依据：源码、测试、烟测均为 synthetic 引用。
85. [x] 无真实秘密；依据：敏感信息扫描和环境变量注入。
86. [x] `git diff --check` 通过；依据：最终差异检查。
87. [x] 不存在阻塞性风险；依据：生产化 IAM、正式编号和医院协议均明确列为后续边界，不阻塞 P15。

## 关闭声明

P15 在本阶段范围内通过关闭审查。P16 以前不得把本地 synthetic actor、DEV-* 编号或 PostgreSQL 参考实现视为生产能力；下一阶段必须重新执行自己的基线门禁。
