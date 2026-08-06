# P19 测试与验证证据

## 自动化层

- `DiagnosisReportApplicationServiceTest` 覆盖任务接管、诊断草稿、初诊版本、报告快照、独立审核、签发和签发后不可重复修改。
- `DiagnosisReportWebTest` 覆盖 `/api/p19` 队列查询与诊断任务创建入口。
- P19 H2 测试 schema 覆盖 P19 表、幂等、审计与 outbox 结构；PostgreSQL 18.4 Testcontainers 在全量 Maven verify 中执行 Flyway V1-V9。
- Vue/TypeScript 工作台测试覆盖 P19 标题、独立审核和签发边界。

## 本次实际结果

- Wrapper `clean verify`：30 个后端测试通过，0 failures，0 errors，0 skipped。
- `npm.cmd --prefix frontend ci`、`format:check`、`lint`、`typecheck`、`test:unit -- --run` 和 `build` 全部通过；前端 5 个测试文件、5 个测试通过。
- Docker Engine、Compose v5.3.1、Linux `hello-world` 和 `docker compose config` 全部通过。
- 后端和前端 P19 镜像构建成功；Compose 全栈启动成功，PostgreSQL healthy，后端 health UP，前端 HTTP 200。
- P17 正常/异常恢复烟测、P18 正常/异常烟测和 P19 诊断/报告正常/异常烟测全部通过。
- 额外版本门禁通过：Maven Wrapper 3.9.16、Java 21.0.11、Node 24.18.0、npm.cmd 11.16.0。

任何未在此处列出的性能、生产部署、医院接口联调、电子签章供应商或真实患者数据验证均不属于本 P19 交付结论。
