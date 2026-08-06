# P17 测试与运行证据

状态：已完成最终验证，具备提交与推送条件。

| 验证项 | 实测结果 |
|---|---|
| Maven Wrapper | `backend/mvnw.cmd -B -ntp -f backend/pom.xml clean verify` 成功；Java 21.0.11；Wrapper Maven 3.9.16。 |
| 后端完整回归 | 21 个测试通过，失败 0，错误 0，跳过 0；包含 P17 应用服务 3、领域 3、Web 1、PostgreSQL 集成 1。 |
| PostgreSQL 18.4 | Testcontainers 启动 `postgres:18.4-alpine`；Flyway V1–V6 全部迁移成功；P17 20 张表、合成种子和唯一约束验证通过。 |
| 前端依赖与静态门禁 | `npm.cmd ci` 成功，审计报告 0 vulnerabilities；Prettier、ESLint、vue-tsc 全部通过。 |
| 前端测试与构建 | Vitest 3 个测试文件、3 个测试通过；Vite 8.1.0 生产构建成功。 |
| Docker 门禁 | Docker 客户端/服务端 29.6.2；Compose v5.3.1；`docker info`、Linux `hello-world`、`docker compose config` 均通过。 |
| 镜像构建 | `pis-next-backend:p17`、`pis-next-frontend:p17` 均构建成功；Compose 默认镜像也构建成功。 |
| 全栈运行 | PostgreSQL healthy；后端 `/actuator/health` 返回 `UP`；前端 `http://localhost:5173` 返回 200；`/api/foundation` 阶段返回 P17。 |
| P17 正常 smoke | `scripts/p17-smoke.ps1` 通过：任务、批次、程序版本、原始结果、人工确认、包埋和实际蜡块形成链路成功，形成事实为 current/active。 |
| P17 异常恢复 smoke | 同一脚本通过：中断、成员影响判定、异常恢复和重处理链路成功，生成替代处理任务。 |
| 最终额外门禁 | Wrapper 版本、`npm.cmd --prefix frontend --version`、Docker 版本、Compose 版本和 Compose 配置均通过。 |

## 运行注意事项

- PostgreSQL 18.4 高于当前 Flyway 已测试的最高版本 17，运行日志有兼容性提示；本次 Testcontainers 和 Compose 迁移均已实际成功。
- Java 编译的注解处理、Mockito 动态 Agent 和 npm 依赖有上游提示，但没有导致构建、测试或运行失败。
- smoke 只使用合成标识、合成引用和 `change-me-local-only` 本地密码；没有写入真实患者信息、凭据或生产连接信息。
