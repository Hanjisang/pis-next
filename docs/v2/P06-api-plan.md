# PIS-Next V2 P06 API 计划

文档状态：计划
文档版本：V2-0.1

## 1. API 原则

API 以应用命令和只读查询为边界。关键状态由领域服务改变，前端或外部系统不能直接提交任意状态字段。外部消息先经过 Inbox、幂等和原始报文记录，再进入应用命令。

## 2. 命令组

- Registration：接收申请、人工登记、映射选择、创建 Case/Specimen、取消；
- Material：创建/重开 Grossing、创建 Block、生成 Slide、完成/软删除、打印/补打；
- Diagnosis：创建/编辑 Diagnosis、分配、认领、Review、Audit、TechnicalOrder；
- Report：Preview、Sign、Withdraw、Resign、Supplement；
- Frozen：创建 Round、追加 Specimen、Frozen End、创建 Routine Case；
- Digital：绑定、手工重绑、扫描元数据和访问入口；
- Archive：归档、借出、归还、受控销毁；
- Integration：重试、死信处理、人工重放、对账和报告回传。

## 3. 查询组

提供 Case context、Workbench Projection、Global Search、QC drill-down、报告历史、材料来源链和接口异常查询。查询结果不作为写入命令的隐式状态来源。

## 4. 外部接口约束

外部接口必须支持消息唯一标识、重复识别、乱序处理、重试、死信、人工重放、外部标识映射、原始报文追溯和脱敏日志。收费和报告回传失败不回滚内部业务。

详细资源、字段、错误码、权限矩阵和契约测试待 P04 数据模型及业务流程评审后确定。
