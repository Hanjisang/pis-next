# PIS V2 Runtime Readiness Report

## 1. 已验证

1. Docker Compose 可启动 PostgreSQL 18.4、backend 和 frontend；backend health 返回 UP，frontend HTTP 返回 200。
2. 空 PostgreSQL 可从 V1 迁移到 V19；Flyway migration、V2 PostgreSQL integration 和 V2 web tests 通过。
3. `backend clean verify` 通过：32 tests passed，0 failures，0 errors。
4. `frontend format:check`、`lint`、`typecheck`、`test:unit -- --run`、`build` 通过：6 files / 8 tests passed。
5. V2 architecture drift and module boundary checks 通过。
6. 关键写入拥有事务边界、幂等键、审计事实、Outbox 和并发版本；医疗记录未通过物理删除实现业务撤销。

## 2. 工具兼容风险

PostgreSQL 18.4 高于当前 Flyway 支持提示中的 PostgreSQL 17。当前结论为：这是兼容提示，不是失败；V1→V19、集成测试和 clean bootstrap 均通过。后续可在不改变已发布 migration checksum 的前提下评估 Flyway 版本升级。

## 3. 不可宣称的验证

以下均为 `EXTERNAL ENVIRONMENT NOT VERIFIED`：真实医院 HIS/LIS 接口、真实患者数据、消息中间件重放、扫描仪/WSI 平台、PDF 生产打印、CA/电子签章、真实 LDAP/OIDC/组织架构、生产部署、医院用户验收和性能容量指标。

## 4. 上线前阻断清单

### P1

1. 补齐 Frozen Round 2+ 材料分流与独立签发的浏览器 E2E。
2. 完成 Cytology、Molecular、Consultation/Send-out、Supplemental 的浏览器 E2E。
3. 将合成 actor 映射到真实 Current Auth User → Doctor Identity，并重新验证权限、责任和签发审计。

### P2

1. 完成配置/系统管理/外挂报表的专用页面和权限矩阵。
2. 评估 PostgreSQL 18.4 对应 Flyway 支持版本。

P0 = 0，但 P1 未清零，因此本报告不是生产就绪批准，也不是医院联调通过证明。
