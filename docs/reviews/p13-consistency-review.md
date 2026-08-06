# P13 工程基础关闭审查

阶段：`P13-ENGINEERING-FOUNDATION-FINAL`
审查日期：2026-08-06
结论：通过

## 1. 前置环境门禁

| 门禁 | 结果 | 证据 |
|---|---|---|
| Git 基线 | 通过 | HEAD、origin/main 均为 `c3aafb372a7bb92c647951aac8d4318143d9e253`，初始工作区干净 |
| Java | 通过 | Java `21.0.11` |
| 系统 Maven | 通过 | Maven `3.9.11`，仅用于生成 Wrapper |
| Maven Wrapper | 通过 | `backend/mvnw.cmd --version` 显示 Maven `3.9.16`、Java 21 |
| Node.js | 通过 | Node `v24.18.0` |
| npm | 通过 | `npm.cmd --version` 为 `11.16.0` |
| Docker Engine | 通过 | Docker client/server `29.6.2`，Docker Desktop `4.85.0` |
| Compose | 通过 | Docker Compose `v5.3.1`，满足 Compose v2 门禁 |
| Linux 容器 | 通过 | `docker run --rm hello-world` 成功 |

## 2. 工程产出审查

1. `backend/pom.xml` 使用 Java 21、Spring Boot 4.1.0 和 Spring Modulith 2.1.0；正式构建只使用 Wrapper。
2. 15 个 P10 模块均有独立包边界和 marker，`ApplicationModules.verify()` 通过并断言数量为 15。
3. 前端固定 Vue 3.5.40、Vite 8.1.0，包含 TypeScript、ESLint、Prettier、Vitest 和生产构建。
4. PostgreSQL 18.4 仅作为参考容器；Flyway V1 创建基础 schema 和迁移元数据，不提前实现病理表。
5. 后端、前端 Dockerfile、NGINX 反向代理和 Compose 服务已建立。
6. Windows 脚本通过 `npm.cmd` 调用 npm，Unix 脚本使用 `npm`；没有执行策略绕过或系统策略修改。
7. GitHub Actions 覆盖后端、前端和容器配置/镜像验证；Dependabot 覆盖 Maven、npm 和 GitHub Actions。

## 3. 验证结果

| 验证 | 结果 |
|---|---|
| `backend/mvnw.cmd test` | 通过，2 项测试 |
| `npm.cmd ci` | 通过，无漏洞报告 |
| `npm.cmd run format:check` | 通过 |
| `npm.cmd run lint` | 通过，无错误、无 warning |
| `npm.cmd run typecheck` | 通过 |
| `npm.cmd run test:unit -- --run` | 通过，1 个文件、1 项测试 |
| `npm.cmd run build` | 通过 |
| 后端 Docker build | 通过 |
| 前端 Docker build | 通过 |
| `docker compose config` | 通过 |
| 全栈 Compose 启动 | 通过，PostgreSQL healthy、后端和前端容器运行 |
| `GET /actuator/health` | 通过，status `UP` |
| `GET /` | 通过，返回前端 HTML |
| `GET /api/foundation` | 通过，返回 P13 和 15 个模块 |
| Flyway SQL 查询 | 通过，`PIS_NEXT / P13` 已写入 |

首次 Compose 尝试暴露了 PostgreSQL 18+ 数据目录挂载规则不兼容；已按容器日志将卷挂载从 `/var/lib/postgresql/data` 修正为 `/var/lib/postgresql`，并完成全栈重新启动验证。

## 4. 安全与范围审查

- 仓库不包含真实患者数据、Token、私钥或生产连接信息；测试和展示内容为合成的工程元数据。
- 数据库密码通过 `PIS_DB_PASSWORD` 注入；`.env` 等本地配置已加入 `.gitignore`。
- P13 未实现报告写覆盖、状态直接赋值、物理删除、外部接口直写核心表等业务路径。
- P13 未将健康入口或前端模块目录宣称为已完成的病理业务功能。
- P11/P12 正式文档未被重写；P13 文档记录了范围、追溯、假设和剩余风险。

## 5. 关闭结论

P13 当前范围内的工程基础、测试、容器、文档和环境门禁均已完成。后续 P14 起仍需实现组织权限、审计和各病理业务模块；这些不属于本次关闭范围。
