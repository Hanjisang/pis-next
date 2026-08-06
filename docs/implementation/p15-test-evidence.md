# P15 测试与证据计划

## 1. 后端

- 领域单元测试：申请幂等、病例建立、标本状态转换、禁止转换、版本冲突。
- 应用测试：权限拒绝、服务端身份、外部/手工登记、预计标本、接收、隔离、交接和重复提交。
- JDBC 集成测试：唯一约束、外键、状态历史、审计和 outbox 同事务，以及重复扫码不新增事实。
- Web 测试：请求字段校验、稳定错误响应和查询范围。
- 并发测试：两个相同 `expected_version` 的接收请求最多一个形成新的接收事实，另一个返回版本/幂等结果。

## 2. 前端与运行验证

前端必须通过 format、lint、typecheck、unit test 和 build。后端必须通过 `mvnw.cmd -B -ntp clean verify`。Docker 需构建现有 `backend/Dockerfile` 和 `frontend/Dockerfile`，`docker compose config` 通过，并启动 PostgreSQL 18.4、后端和前端完成合成数据烟雾流程。所有结果记录实际命令和输出，不把未运行的验证写成通过。
## 3. 实际执行记录（2026-08-06）

- `backend\mvnw.cmd -B -ntp clean verify`：通过，5 个后端测试，0 失败、0 错误。
- `npm.cmd --prefix frontend ci`、`format:check`、`lint`、`typecheck`、`test:unit -- --run`、`build`：全部通过；Vitest 1 个测试文件、1 个测试通过。
- `docker version`、`docker compose version`、`docker info`、`docker run --rm hello-world`：通过；Docker Engine 29.6.2、Compose v5.3.1、Linux 容器实际启动。
- `docker build -f infra/docker/backend.Dockerfile -t pis-next-backend:p15 .` 与前端对应命令：通过；`docker compose config`：通过。
- `docker compose --profile full up -d --build`：PostgreSQL 18.4 healthy，后端 health `UP`，前端 HTTP 200；Flyway V1、V2、V3 均成功，`PIS_NEXT/P15` 生效。
- HTTP 合成烟测：手工登记 → 接受 → 建立病例 → 预计标本 → 服务端期望数量核对 → 接收；重复扫码返回 `duplicate=true`，队列清空，追溯返回期望/实际数量一致。
- 并发合成烟测：相同 `expected_version` 的两个接收请求返回一条成功和一条 `P12-ERR-024`；每个验证标本只有 1 条 handoff、3 条状态历史。
- 外部登记烟测：首次写入、相同载荷幂等重放、不同载荷 `P12-ERR-003`；原始报文与 Inbox 各 2 条（均为合成数据）。
- `scripts\verify.ps1`：通过，包含后端、前端、Docker、Compose、全栈启动和最终版本检查。
