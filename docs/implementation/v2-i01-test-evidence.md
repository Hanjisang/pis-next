# V2-I01A 验证证据

文档状态：已执行
日期：2026-08-08

## 1. 后端

| 验证项 | 命令/范围 | 结果 |
|---|---|---|
| 全量 Maven 测试 | `mvn -B -ntp test` | 45 项通过，0 失败，0 错误 |
| V2 领域、Web、架构漂移、模块边界 | `V2RegistrationDomainTest`、`V2RegistrationWebTest`、`V2ArchitectureDriftTest`、`ModuleBoundariesTest` | 10 项通过 |
| PostgreSQL 迁移 | `V2RegistrationPostgresIntegrationTest`，Testcontainers `postgres:18.4-alpine` | 通过；Flyway 执行 V1–V12，V2-I01A schema、约束和种子配置可用 |

V2 Web 测试实际覆盖：病例幂等重放、幂等摘要冲突、同病例 specimenCode 唯一、跨病例 specimenCode 复用、标本修改、乐观版本冲突、软删除、软删除后的编码复用和编号与内部身份分离。PostgreSQL 测试额外验证 `pis_v2` 中 8 个业务类型、4 个申请项目映射、16 条编号规则、软删除唯一边界、I01A 删除的状态表和幂等 `MERGE` 语法。

## 2. 前端

| 验证项 | 命令 | 结果 |
|---|---|---|
| 类型检查 | `npm.cmd run typecheck` | 通过 |
| 前端全量单测 | `npm.cmd run test:unit -- --run` | 8 项通过 |
| 格式检查 | `npm.cmd run format:check` | 通过 |
| 生产构建 | `npm.cmd run build` | 通过 |
| ESLint | `npm.cmd run lint` | 0 错误，75 条现有 Legacy 工作台格式 warning |

| Architecture Drift Guard | `V2ArchitectureDriftTest` | 2 项通过；V2 不包含下游 Block/Slide/Report 核心类型，Case 仅暴露 ACTIVE/CANCELLED |

## 3. 未包含的验收

1. 尚未执行真实医院接口联调、真实患者数据测试或生产部署验证。
2. 尚未将 V2 工作台切换为综合工作台唯一入口；当前切换需要等待下游 V2 读写边界，避免 V2/Legacy 双写。
3. 正式医院业务类型、申请项目映射和病理号规则仍以配置及业务确认结果为准；迁移中的 `LOCAL_HOSPITAL` 和 `SYNTH-*` 仅为合成开发数据。
