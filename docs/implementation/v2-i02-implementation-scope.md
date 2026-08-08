# V2-I02 实施范围与契约

文档状态：实施中

## 1. 本 Increment 目标

在 V2 Case/Specimen 之上建立可独立运行的普通组织病理材料主链：

`Case → Specimen → Grossing → Block → Slide → Slide Completion`

I02 完成后，Grossing 完成会按照数据库配置的 SlideRule 为每个有效 Block 生成默认 INITIAL HE Slide，并通过 PrintRule/PrintService 记录打印事实。Initial Production Completion 只按有效 INITIAL Slide 的完成事实查询，不写入 Case 生命周期。

## 2. 实现对象

- `Grossing`：Case 级取材活动，支持多个 Specimen、OPEN/COMPLETED 事实、授权 reopen。
- `GrossingSpecimen`：明确的 Grossing↔Specimen 关系事实。
- `Block`：单一真实蜡块实体，Case 内有效 blockCode 唯一，可修改和软删除。
- `Slide`：单一真实切片实体；当前实现 Block→Slide，模型保留 Specimen/External 来源字段。
- `SlideRule`：默认一 Block 一张 HE INITIAL Slide 的数据库配置。
- `PrintRule`、`PrintLog`、`PrintService`：材料打印与材料创建分离，补打复用同一实体。
- Initial Production Projection：按 sourceContextType=INITIAL 且有效的 Slide 计算完成比例。
- Material Tree Query：从真实关系返回 Case→Specimen→Block→Slide。

## 3. API 契约

### Grossing

- `POST /api/v2/cases/{caseId}/grossings`
- `PUT /api/v2/grossings/{grossingId}`
- `POST /api/v2/grossings/{grossingId}/specimens`
- `POST /api/v2/grossings/{grossingId}/complete`
- `POST /api/v2/grossings/{grossingId}/reopen`

### Block

- `POST /api/v2/grossings/{grossingId}/blocks`
- `PUT /api/v2/blocks/{blockId}`
- `POST /api/v2/blocks/{blockId}/soft-delete`

### Slide/Print/Query

- `POST /api/v2/slides/{slideId}/complete`
- `POST /api/v2/slides/complete-batch`
- `POST /api/v2/blocks/{blockId}/print`
- `POST /api/v2/slides/{slideId}/print`
- `GET /api/v2/cases/{caseId}/materials`

所有写命令使用应用服务授权、幂等键或 expectedVersion；不提供通用状态更新 API。

## 4. 非目标

I02 不实现 Diagnosis、Responsibility、Report、TechnicalOrder、FrozenRound 完整业务、Molecular、Archive、Loan、QC、数字切片工作流或完整 TechnicalRecord。`TECHNICAL_ORDER`/`FROZEN_CONTEXT` 只作为来源类型的稳定扩展值，不产生对应模块。

## 5. 数据与安全边界

1. 新表全部位于 `pis_v2`，使用 V13 Flyway 迁移。
2. 核心关系使用外键和结构化列；不使用通用多态外键、EAV 或 material_tree 复制表。
3. Block/Slide/Grossing 只软删除；历史打印、完成和来源事实保留。
4. 完成 Grossing 的 Block→Slide 同步、Grossing 完成事实、Audit 和 Outbox 在同一业务事务内提交。
5. 打印外设失败只产生失败 PrintLog，不删除或回滚已存在的材料。
6. 所有测试数据均为合成数据。
