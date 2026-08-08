# V2-I05 构建与复用边界

## 1. 本阶段边界

V2-I05 只实现 `Diagnosis → Responsibility Audit → Report Preview → Sign-out → Report`，并包含撤回、重新签发、补充报告和历史查询。V2-I06 不在本阶段范围内。

## 2. 新建模型

1. `ReportTemplate` 与 `ReportTemplateVersion` 独立于 `DiagnosisTemplate`，已发布模板版本不可更新。
2. `Report` 是一次签发事实；每次签发生成新报告号和不可变诊断、责任链、病例、材料、技术结果、渲染内容快照。
3. PDF 作为独立 `report_pdf_output` 正式输出持久化，带文件引用和 SHA-256 内容摘要；历史输出不覆盖。
4. 预览是即时渲染结果，不持久化 `PreviewReport`。
5. 撤回只改变报告状态并保留原快照/PDF；补充报告使用独立 `SUPPLEMENTAL` 报告，原始有效报告不被替换。

## 3. 复用边界

1. 复用 V2-I03 的 Diagnosis、ResponsibilityUnit 和 Case 读取/锁定能力，不复用 Legacy Diagnosis/Report 表。
2. 复用 V2-I04 `OrderSnapshot.blocking` 投影，并通过 `hasBlockingTechnicalOrders` 作为统一签发阻断门禁。
3. 复用 P15 授权、Audit 和 Outbox 端口；HIS/打印/文件服务联调仍是扩展点，不作为签发成功条件。
4. 采用最小 PDF 渲染适配器，生产部署可替换实现，但不改变报告快照和正式输出持久化契约。

## 4. 明确不引入

本阶段不创建报告版本链、签发工作流、撤回工作流、重新签发工作流或签名层伪实体；重新签发只是撤回后的下一次普通签发。

## 5. 待业务确认

1. 当前身份适配器仍使用现有 actor 字符串，真实 User/Doctor 主数据接入待业务确认。
2. 当前 PDF 渲染器是可替换的最小正式输出实现，医院版式、字体和签章要求待业务确认。
3. 外部交付只预留 Outbox 扩展点，HIS 回执协议待业务确认。
