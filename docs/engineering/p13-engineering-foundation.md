# P13 工程基础初始化

文档状态：P13 已完成
阶段名称：`P13-ENGINEERING-FOUNDATION-FINAL`
设计原则：模块化单体、净室设计、业务事实不可覆盖、测试先于阶段关闭

## 1. 交付边界

P13 只建立可运行的工程基础，不提前实现 P14 及后续阶段的病理业务流程。当前交付包括：

1. Java 21、Spring Boot 4.1.0 和 Spring Modulith 2.1.0 后端骨架；
2. 与 P10 一一对应的 15 个逻辑模块边界；
3. Vue 3.5.40、TypeScript、Vite 8.1.0 前端骨架；
4. PostgreSQL 18.4 本地参考运行时和 Flyway 基础迁移；
5. 后端与前端容器、Docker Compose 全栈编排；
6. Windows PowerShell 与 Unix shell 构建/运行脚本；
7. GitHub Actions 与 Dependabot 基础配置；
8. 工程级模块边界测试、前端单元测试、构建和运行验证。

P13 不交付申请、标本、蜡块、玻片、诊断、报告、权限、接口、ORM 或生产数据功能；这些属于后续阶段，不能由本阶段的健康检查页面代替。

## 2. 版本基线

| 能力 | 基线 |
|---|---|
| Java | 21 |
| Maven Wrapper | 3.9.16，Wrapper 分发类型为 `only-script` |
| Spring Boot | 4.1.0 |
| Spring Modulith | 2.1.0 |
| Node.js | 24.x |
| Vue | 3.5.40 |
| Vite | 8.1.0 |
| PostgreSQL | 18.4，仅作为参考数据库运行时 |

后端正式构建必须使用 `backend/mvnw` 或 `backend/mvnw.cmd`。系统 Maven 仅用于生成 Wrapper，不属于正式构建路径。

## 3. 15 个模块边界

模块包位于 `backend/src/main/java/com/hanjisang/pis` 下，每个模块以 `package-info.java` 的 `@ApplicationModule` 声明边界，并由 `ModuleBoundariesTest` 使用 Spring Modulith `ApplicationModules.verify()` 验证。

| P10 模块 | P10 名称 | Java 包 | P13 边界状态 |
|---|---|---|---|
| MOD-001 | 申请与病例 | `accession` | 已声明、已验证 |
| MOD-002 | 标本与来源材料 | `specimen` | 已声明、已验证 |
| MOD-003 | 组织技术与蜡块玻片 | `technical` | 已声明、已验证 |
| MOD-004 | 术中冰冻 | `frozen` | 已声明、已验证 |
| MOD-005 | 细胞病理 | `cytology` | 已声明、已验证 |
| MOD-006 | 分子病理 | `molecular` | 已声明、已验证 |
| MOD-007 | 外送检测与外部结果 | `referral` | 已声明、已验证 |
| MOD-008 | 诊断与报告 | `diagnosis` | 已声明、已验证 |
| MOD-009 | 多模态诊断关联 | `multimodal` | 已声明、已验证 |
| MOD-010 | 数字材料 | `digital` | 已声明、已验证 |
| MOD-011 | 出站集成与对账 | `integration` | 已声明、已验证 |
| MOD-012 | 质量、异常与受控纠错 | `quality` | 已声明、已验证 |
| MOD-013 | 授权、代理与审计 | `security` | 已声明、已验证 |
| MOD-014 | 归档、销毁与恢复 | `archive` | 已声明、已验证 |
| MOD-015 | 报告呈现与医院配置 | `presentation` | 已声明、已验证 |

P13 未建立模块间业务写入关系，也未将 15 个模块错误拆分为 15 个远程服务；跨模块协作留待后续应用能力和事件实现。

## 4. 数据库与迁移

P11 的数据库平台当时标记为待确认。本阶段遵循用户指定版本建立 PostgreSQL 18.4 参考运行时，但不把该参考运行时写成已完成的生产平台决策。

`backend/src/main/resources/db/migration/V1__foundation_baseline.sql` 只创建：

- `pis` schema；
- `pis.foundation_schema_metadata` 基础元数据表；
- P13 迁移版本记录。

P11 设计的正式病理表、状态历史、版本链、审计、集成和对账表不在 P13 批量生成，避免提前进入 P14–P23 的业务实现范围。

由于 Spring Boot 4.1.0 的当前依赖组合未自动注册 Flyway 迁移器，P13 在 `FlywayConfiguration` 中显式创建 Flyway Bean，并仅在非 `test` profile 执行迁移；测试 profile 使用 H2，避免测试隐式依赖外部数据库。

## 5. 可运行入口

后端基础诊断入口：`GET /api/foundation`。它只返回 P13 阶段和模块目录，不承载病理业务事实。

健康入口：`GET /actuator/health`，开放 liveness/readiness 组。

前端通过 NGINX 将 `/api/` 和 `/actuator/` 代理至后端容器，静态页面使用 history fallback。

## 6. 本地命令

Windows 使用 `npm.cmd`，后端使用 Maven Wrapper：

```powershell
Set-Location 'D:\Projects\pis-next'
.\scripts\build.ps1
```

本地全栈运行需要注入合成数据库密码：

```powershell
$env:PIS_DB_PASSWORD = 'change-me-local-only'
.\scripts\compose-up.ps1
```

Unix 使用：

```bash
./scripts/build.sh
PIS_DB_PASSWORD=change-me-local-only ./scripts/compose-up.sh
```

`Set-ExecutionPolicy`、`ExecutionPolicy Bypass` 和系统级策略修改不属于本项目脚本。

## 7. 追溯矩阵

| 输入基线 | P13 产出 | 验证入口 |
|---|---|---|
| P10 15 个模块边界 | 15 个 `@ApplicationModule` 包声明 | `ModuleBoundariesTest` |
| P10 模块化单体原则 | 单一 Spring Boot 应用和模块边界测试 | Maven `test` |
| P11 数据库迁移规则 | Flyway V1、PostgreSQL Compose 服务 | 容器启动 + SQL 查询 |
| P12 稳定业务契约边界 | P13 只提供工程诊断入口，不伪造业务 API | 代码范围审查 |
| AGENTS.md 安全规则 | 环境变量密码、合成数据、`.gitignore` | 敏感信息扫描 |
| 环境修正规则 | Wrapper 3.9.16、npm.cmd、Docker 门禁 | 版本与 Compose 验证 |

## 8. 假设、影响与后续确认

| 假设 | 选择原因 | 影响 | 后续确认方式 |
|---|---|---|---|
| PostgreSQL 18.4 只作为参考运行时 | P13 需要真实数据库容器验证迁移；P11 平台仍未冻结 | 生产数据库适配仍未完成 | P27 前完成平台 ADR 和生产参数确认 |
| P13 只创建基础迁移 | 避免提前生成 P11 的 89 个逻辑表并越界实现业务 | 后续阶段仍需逐项迁移和集成测试 | 按 P14–P23 业务范围追加不可变迁移 |
| 本地密码由环境变量注入 | 仓库不得保存密钥或生产连接信息 | 首次 Compose 运行必须设置 `PIS_DB_PASSWORD` | 使用部署平台密钥管理机制替换 |

上述仍未确认的内容必须标记为“待业务确认”，不能从当前骨架推断为已确认业务规则。
