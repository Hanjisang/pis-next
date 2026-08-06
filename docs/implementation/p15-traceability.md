# P15 实现追溯

| 实现层 | 正式依据 | 代码/数据落点 |
|---|---|---|
| 申请入站与手工登记 | SCN-PIS-001/002；P06-PROC-001；P09-REQ-0001/0002/0003/0004 | accession domain/application/web；`pathology_request`、`inbound_raw_message`、`external_request_reference`、`inbox_consumption` |
| 病例建立 | SCN-PIS-003；P06-PROC-002；P08-SM-002；P09-REQ-0005/0006/0008/0009 | accession domain/application；`pathology_case`、上下文引用、快照、`clinical_state_current` |
| 预计标本和容器 | SCN-PIS-004；P09-REQ-0011/0014 | specimen domain/application；`specimen`、`specimen_container` |
| 接收与核对 | SCN-PIS-010/011/012；P06-PROC-003；P08-SM-003；P09-REQ-0012/0014 | specimen domain/application/web；接收事实、状态历史、异常 |
| 交接 | SCN-PIS-013；P09-REQ-0013 | `handoff_record`、责任端口和交接命令 |
| 授权与审计 | P14-PERM-001–011、P14-SCOPE-001–010、P14-TASK-002/003、P09-REQ-0018/0020/0070/0073/0074/0076/0080/0081/0082 | security ActorContext/Decision、`audit_event` |
| 事件 | P12-EVC-001/002/003；P11-TBL-086/087 | `outbox_event`、同事务写入和重复消费键 |

下游取材、技术、诊断、报告、文件和生产接口不在本阶段追溯为已实现功能。
