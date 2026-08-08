# V2-I01 实施范围与契约

## 1. 目标

在 P00–P09 领域基线上，从零建立病例、病理业务类型、申请项目映射、病理号规则和标本的最小可运行闭环。该闭环不修改 Legacy 表，不以旧数据迁移便利反向设计 V2。

## 2. 后端边界

模块根：`com.hanjisang.pis.v2`。

命令入口：

- `POST /api/v2/registration/cases`：按申请项目解析业务类型，建立 ACTIVE 病例上下文快照并使病理号生效；
- `POST /api/v2/registration/specimens`：在 ACTIVE 病例下登记独立标本事实；
- `PUT /api/v2/registration/specimens/{id}`：按并发版本修改标本事实；
- `POST /api/v2/registration/specimens/{id}/soft-delete`：按并发版本追加软删除事实。

查询入口只返回当前范围内的 V2 病例或标本摘要，不返回患者正文扩展字段。

## 3. 数据边界

Flyway `V11__v2_i01_registration_and_specimen.sql` 建立初始 `pis_v2` schema，`V12__v2_i01a_model_drift_correction.sql` 清理错误的流程状态结构。核心关系通过明确外键表达；上下文快照、软删除事实、审计、Outbox 和幂等记录保存。编号序列通过同一事务内的数据库行锁和条件更新递增，不使用 `SELECT MAX + 1`。

## 4. 测试边界

- 领域测试覆盖 Case ACTIVE/CANCELLED 守卫、Specimen 修改/软删除、编号格式；
- Web/JDBC 测试覆盖病例幂等、幂等摘要冲突、编号与内部身份分离、同病例 specimenCode 唯一、跨病例代码复用和软删除；
- 前端测试覆盖 V2 独立工作台和不要求录入内部 UUID；
- PostgreSQL/Flyway 迁移验证覆盖 V1–V12；真实医院接口、生产部署和医院配置仍不属于本 Increment。
