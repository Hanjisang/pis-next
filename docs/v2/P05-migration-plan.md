# PIS-Next V2 P05 数据迁移计划

文档状态：计划，待建立隔离迁移工作区
文档版本：V2-0.1
重要边界：本轮未读取、未分析任何旧 PIS 数据库或旧系统材料

## 1. 迁移原则

V2 领域设计与历史迁移分离。只有在 V2 数据模型、映射规则和迁移工具完成评审后，才允许在独立迁移工作区接入经过批准的数据样本。不得为了迁移方便把旧错误语义保留到 V2 核心模型。

## 2. 旧对象处置分类

当前没有可审计的旧数据库表清单，因此以下是处置规则，不是已完成映射：

| 旧对象类别 | 处置 | 说明 |
|---|---|---|
| 身份、组织、用户、权限、审计、文件、接口可靠性 | KEEP/MAP | 逐字段核对语义，不能把服务身份当人工责任 |
| Application、旧 Case、Specimen | MAP | 映射到 V2 独立身份；重复、取消和病理号必须保留历史 |
| 旧 Block/蜡块业务记录 | MANUAL REVIEW 后 MAP | 只有能证明来源、物理身份和业务语义时才映射为 Block |
| PlannedBlock/ActualBlock | 不直接 MERGE | 不得按随机规则合并；无法证明语义时进入 MigrationWarning/ManualReview |
| PlannedSlide/ActualSlide | 不直接 MERGE | V2 只接收可证明已形成的 Slide；计划数据保留为迁移证据或丢弃清单 |
| 旧诊断、报告和嵌套版本 | MAP/MANUAL REVIEW | 必须重建 Diagnosis 和独立 Report；不可把旧版本静默覆盖 |
| 旧技术 Task/Generic Result | MANUAL REVIEW | 仅能映射到 TechnicalRecord 或具体输出，不能自动塞进万能结果 |
| Frozen、Round、冰剩关系 | MAP/MANUAL REVIEW | 来源不完整时不得猜测 frozenSourceCaseId |
| 历史审计、取消、报告回传和文件 | KEEP/MAP | 证据优先；文件哈希和业务绑定单独校验 |
| 无法可靠映射或真实含义不明的数据 | DROP with warning | 不静默进入 V2；必须形成 MigrationWarning 和人工清单 |

## 3. 迁移产物

后续必须产生：

- `MigrationMapping`：旧标识到 V2 稳定身份的映射；
- `MigrationWarning`：无法可靠转换、缺少来源或存在冲突的记录；
- `ManualReview`：需业务人员判断的病例、材料、报告和 Frozen 关系；
- 迁移前后数量、唯一性、来源闭合、报告快照和审计完整性对账；
- 幂等、可重跑、可暂停和可回滚的工具链；
- 不将真实患者数据提交到代码、测试或文档仓库。

## 4. 当前阻断

旧表清单、字段语义、数据量和历史数据质量均为“待业务确认/待批准迁移输入”。在获得明确授权前，不执行旧库读取、导入、转换或生产迁移。
