# S06 Production Readiness Checklist

文档状态：FOUNDATION READY

生产部署批准：NOT GRANTED

使用方式：每个医院环境复制本清单并附证据；`NOT VERIFIED` 不得改写为 PASS。

## 1. 当前基础结论

仓库已提供生产 Compose 基线、外部化环境配置、Flyway 自动迁移、健康端点、日志轮转、PostgreSQL 备份/恢复脚本以及生产授权环境白名单。它们是部署基础，不等于生产容量、安全、灾备或医院验收已通过。

## 2. Environment Gate

| ID | 检查项 | 基础状态 | 医院现场状态/证据 |
|---|---|---|---|
| S06-ENV-001 | OS/容器运行时版本和补丁基线 | 待业务确认 | NOT VERIFIED |
| S06-ENV-002 | PostgreSQL 版本、参数、时区、连接数 | PG 18.4 自动化可迁移 | NOT VERIFIED |
| S06-ENV-003 | 数据库、报告、附件、数字切片存储容量和增长 | 待业务确认 | NOT VERIFIED |
| S06-ENV-004 | PIS、HIS/LIS/EMR、设备和运维网络矩阵 | 待业务确认 | NOT VERIFIED |
| S06-ENV-005 | NTP、DNS、SMTP/告警和证书服务 | 待业务确认 | NOT VERIFIED |
| S06-ENV-006 | 监控、日志、Trace、磁盘和队列告警 | 接口日志/health 基础存在 | NOT VERIFIED |

S06-PRD-001：生产镜像必须使用批准的不可变 Tag 或 Digest；禁止生产服务器现场编译未审查代码。

S06-PRD-002：`docker-compose.production.yml` 不发布 PostgreSQL 端口，只由内部数据网络访问；前端是唯一默认 HTTP 入口。医院 HTTPS 必须由受控反向代理或网关终止。

S06-PRD-003：生产必须设置 `PIS_REQUIRE_AUTH=true`、Secure Cookie，并确保 `PIS_AUTH_TEST_PASSWORD` 为空。

## 3. Database/Flyway Gate

| ID | 检查项 | 当前状态 |
|---|---|---|
| S06-DB-001 | 空 PostgreSQL 执行 V1→latest | PASS（自动化至 V25） |
| S06-DB-002 | Flyway checksum 与制品一致 | 自动化验证；发布流程待建立 |
| S06-DB-003 | 发布迁移不可重写 | 强制规则 |
| S06-DB-004 | 生产备份后再迁移 | NOT VERIFIED |
| S06-DB-005 | 大数据量迁移锁/时长评估 | NOT VERIFIED |
| S06-DB-006 | 只读历史源和 V2 Source of Truth 边界 | 设计完成；现场未验证 |

Flyway 11.8.2 对 PostgreSQL 18.4 当前输出“最新已测试支持 PostgreSQL 17”的兼容性提示。V1→V25 和集成测试成功；该提示作为工具兼容风险记录，不在无回归证据时擅自升级或重写迁移。

## 4. Backup/Restore Gate

仓库脚本：`scripts/backup-postgres.ps1|sh` 和 `scripts/restore-postgres.ps1|sh`。

备份脚本使用 PostgreSQL custom format、无 owner/privilege，并生成 SHA-256。恢复脚本要求明确目标、校验文件和二次确认；恢复使用单事务、错误即停止。

S06-BKP-001：密码只通过 `PIS_DB_PASSWORD` 注入，脚本不得输出密码。

S06-BKP-002：生产备份必须写入独立受控存储，配置加密、访问控制、不可变保留和异地副本。

S06-BKP-003：备份成功不等于可恢复。仓库已用隔离 PostgreSQL 18.4 容器完成一次合成 `backup→SHA-256→restore→query` 演练；医院仍必须在自己的隔离环境完成全量恢复、Flyway 校验、关系对账和业务抽样，记录 RPO/RTO。医院恢复演练：NOT VERIFIED。

S06-BKP-004：恢复会清理目标 Schema 内对象，只能对已确认的隔离恢复库或获批目标执行。

## 5. Performance Gate

以下目标不能由仓库猜测，必须由医院提供并冻结：

| 指标 | 目标 | 验证结果 |
|---|---|---|
| Concurrent users | 待业务确认 | NOT VERIFIED |
| Daily cases / peak registration | 待业务确认 | NOT VERIFIED |
| Report preview/PDF P95 | 待业务确认 | NOT VERIFIED |
| Global search P95 and index lag | 待业务确认 | NOT VERIFIED |
| Integration throughput/backlog | 待业务确认 | NOT VERIFIED |
| Digital metadata callback rate | 待业务确认 | NOT VERIFIED |

S06-PERF-001：性能测试使用合成或批准脱敏数据，覆盖峰值、持续负载、故障恢复和数据增长；不得只测空库单用户。

## 6. Security Gate

- 权限矩阵和最小权限抽查：Core 合成验证 PASS，医院角色 NOT VERIFIED；
- 审计完整性和保存期：实现基础存在，医院合规 NOT VERIFIED；
- LDAP/AD/SSO/OAuth、MFA、离职停用：NOT VERIFIED；
- HTTPS、HSTS、Cookie、反向代理 Header：Secure Cookie 配置完成，其余 NOT VERIFIED；
- 数据库账号、密钥轮换和 Secret 管理：NOT VERIFIED；
- 报告/数字切片/附件越权和安全测试：NOT VERIFIED；
- 漏洞扫描、SBOM、镜像签名和渗透测试：NOT VERIFIED。

## 7. Operations Gate

- 业务健康检查：`/actuator/health`；
- 数据库健康检查：`pg_isready`；
- 日志轮转：Compose 基线为 20MB × 5；现场值待确认；
- 接口死信、重试和每日对账告警：数据结构完成，调度/告警平台 NOT VERIFIED；
- Runbook、值班表、升级路径、停机通知：待业务确认；
- 监控保留期、备份保留期和审计保留期：待业务确认。

## 8. Production Approval

只有 Environment、Database、Backup/Restore、Performance、Security、Operations、Integration、Device、Migration 和 Pilot Gate 均有证据并由责任人签字后，才可批准生产部署。当前结论：FOUNDATION READY，PRODUCTION READINESS NOT VERIFIED。
