# P18 测试与运行验证证据

日期：2026-08-06

## 1. 自动化测试

- `TechnicalOrderDomainTest`：技术项目类型、状态聚合和生命周期规则；
- `TechnicalOrderApplicationServiceTest`：正常全流程、幂等重放、目标完整性、版本冲突、取消、责任、结果引用、审计和 outbox；
- `TechnicalOrderWebTest`：列表和建立医嘱路由委托；
- `TechnicalOrderPostgresIntegrationTest`：PostgreSQL 18.4 Testcontainers、Flyway V1 至 V7、P18 13 张表和 5 条合成配置；
- `P18TechnicalOrderWorkbench.test.ts`：前端创建/提交请求边界；
- P15～P17 既有测试保持回归运行。

## 2. 实际运行门禁

`.\scripts\verify.ps1` 已通过：Wrapper `clean verify` 通过 28 个后端测试；前端 `npm.cmd ci`、format、lint、typecheck、unit（4 个测试文件/4 个测试）和 build 均通过；Docker client/server、Compose、Engine、`hello-world`、后端/前端镜像、Compose 启动、后端健康检查和前端 HTTP 检查均通过。

P17 正常/异常 smoke 通过：formation `fffbb3ac-ab43-4703-9c92-5a4f02f58b64`，replacement task `7070093a-f91c-45b0-a2e4-b5a207bd4e2b`。P18 正常/异常 smoke 通过：order `06695ca2-8e99-4088-aacd-5b04aa3f0afc`，project `68ba0c19-04dc-4315-bbfb-7c4861911425`，cancellation `e1cc3009-e8c5-4093-8f80-9bc05e947d00`。

额外版本/配置门禁通过：Maven Wrapper 3.9.16、Java 21.0.11、npm 11.16.0（Node 24.18.0）、Docker 29.6.2 client/server、Compose v5.3.1、`docker compose config` 有效。PostgreSQL 18.4 Testcontainers 验证 Flyway V1～V7，并确认 P18 13 张表和 5 条合成项目配置。

## 3. 测试数据与限制

使用合成病例、合成 P17 实际蜡块和本地 `change-me-local-only` 数据库密码。未进行真实医院接口、真实设备、真实患者、生产部署或临床验证。PostgreSQL 18.4 为参考数据库运行时。
