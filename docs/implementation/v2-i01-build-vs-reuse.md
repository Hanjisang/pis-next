# V2-I01 Build vs Reuse 决策

文档状态：V2-I01A 收口审查输入
基线：`31c4793` 指定的 P00–P09 设计基线；实现同时遵循 BCR-01 已确认的领域纠偏。
日期：2026-08-08

## 1. 决策结论

| Component | Decision | Reason |
|---|---|---|
| Case | NEW | Legacy `accession.PathologyCase` 绑定 P15 申请表和旧快照结构；V2 仅保留 ACTIVE/CANCELLED 生命周期，病例号、业务类型和上下文快照独立保存。 |
| Specimen | NEW | Legacy `specimen.Specimen` 将容器和接收流程状态带入核心对象；V2 只保存可修改事实、同病例 specimenCode、技术标签和软删除事实。 |
| BusinessType | NEW | Legacy 没有独立的病理业务类型配置对象；V2 需要保留未启用类型及历史关联，不能用部署开关删除模型。 |
| ApplicationItemMapping | NEW | Legacy 没有申请项目到业务类型的受控映射；V2 不能把页面字段或病例状态当作路由规则。 |
| PathologyNumberRule | NEW | Legacy 使用环境专用编号分配器，未形成独立的可版本化规则和数据库并发序列。 |
| Database | NEW | 新建 `pis_v2` schema；不为旧表改名、合并或长期双写。Legacy 只保留为未来迁移数据源。 |
| API | NEW | 建立 `/api/v2/registration` 命令式接口；不在 Legacy Domain 中添加 V2 行为，不提供通用状态更新接口。 |
| Frontend | NEW | 新增独立 `V2RegistrationWorkbench`，按建案、标本登记、事实修改/软删除表达 I01A 边界；不把 V2 API 混接到仍依赖 P15–P19 旧表的纵向工作台。 |
| Authentication / Audit / Outbox | REUSE | 现有能力位于 shared infrastructure，V2 Domain 不依赖其业务对象；I01 复用其服务端授权、追加审计和事务发件箱入口。 |

## 2. 旧实现检查证据

1. Legacy `PathologyCase` 直接依赖 `PathologyRequest` 和旧快照身份，病例建立路径受 P15 申请状态驱动。
2. Legacy `Specimen` 以 `specimen_container` 作为默认扫码入口，状态代码和应用服务围绕旧表组织；V2-I01 的默认模型不把容器升级为核心对象。
3. Legacy 没有 `BusinessType`、`ApplicationItemMapping` 和独立 `PathologyNumberRule` 领域对象。
4. 继续修补会要求 V2 长期同时维护 Legacy 表、旧状态和新业务语义，违反本次战略调整指令的“只写 V2”原则。

## 3. V2-I01 已实现边界

- `Case`、`Specimen`、`BusinessType`、`ApplicationItemMapping` 和 `PathologyNumberRule` 位于 `com.hanjisang.pis.v2`；不引用 `accession` 或 Legacy `specimen` Domain。
- `Case` 保存来源系统、外部申请标识、申请项目、业务类型和病例上下文快照；内部 UUID、病理号和外部标识分离。
- `Specimen` 保存独立来源链和自己的并发版本；容器不是默认核心身份。
- `Case` 只有 `ACTIVE` 和 `CANCELLED`；取消时保留病例和病理号历史，将有效病理号绑定标记为失效，不自动回收或复用号码。
- `Specimen` 没有 RECEIVED、ACCEPTED、PROCESSING、COMPLETED 等流程状态；登记后可修改，软删除只追加删除事实并保留原记录。
- `specimenCode` 仅在同一 Case 的未删除标本中唯一；不同 Case 可以使用相同代码。技术标签只有活动唯一索引，没有 Label 领域实体。
- V2 所有新业务写入 `pis_v2`；未实现 Legacy 双写、Legacy 读取适配和数据迁移，这些属于后续独立工程。

## 4. 明确假设与待业务确认

1. `application_item_code` 到业务类型的映射采用可版本化配置；P04-002 对申请内容、必填范围和业务类型的医院级细则仍标记为“待业务确认”，本 Increment 不把合成种子配置写成正式医院规则。
2. 迁移使用 `LOCAL_HOSPITAL`、`H-*` 和 `HS-*` 合成编号规则，仅用于开发与测试；正式医院号段、年份规则和跨院区唯一范围仍需配置确认。
3. 申请与病例的拆分、归并和受控纠错未在 I01 提前实现；病例表保留外部申请引用，后续以独立命令和追加关系记录处理。Case 取消只保留为生命周期守卫和事实边界，不启动下一阶段流程。
4. 当前前端工作台尚未替换仍依赖 P15–P19 Legacy 表的综合工作台；切换必须在下游 V2 读写边界完成后进行，避免形成 V2/Legacy 长期双写。
