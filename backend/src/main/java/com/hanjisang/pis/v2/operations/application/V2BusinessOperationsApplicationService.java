package com.hanjisang.pis.v2.operations.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.operations.api.ReportOutputPort;
import com.hanjisang.pis.v2.molecular.application.V2MolecularApplicationService;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.AddressCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.ArchiveCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.ConsumableBatchCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.ConsumableCatalogCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.ConsumableTransactionCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.CriticalNotificationCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.CriticalValueCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.EquipmentCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.EquipmentEventCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.EnvironmentCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.IncomeCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.MigrationErrorCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.MigrationJobCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.MigrationRecordCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.MolecularInstrumentCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.MolecularProjectCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.MolecularReagentCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.MolecularTestCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.PackageCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.PackageItem;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.ProcurementRequestCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.QualityDocumentCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.RequisitionCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.ScheduleCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.SafetyCheckCommand;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository.SpaceCommand;

@Service
public class V2BusinessOperationsApplicationService {

    private static final String QUERY = "P14-PERM-048";
    private static final String ADMIN = "P14-PERM-001";
    private static final String DIAGNOSIS = "P14-PERM-034";
    private static final String MATERIAL = "P14-PERM-014";

    private final JdbcV2BusinessOperationsRepository repository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final ReportOutputPort reportOutputPort;
    private final V2MolecularApplicationService molecularService;

    public V2BusinessOperationsApplicationService(JdbcV2BusinessOperationsRepository repository,
            P15AuthorizationService authorization, JdbcAuditEventRepository audit,
            ReportOutputPort reportOutputPort, V2MolecularApplicationService molecularService) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
        this.reportOutputPort = reportOutputPort;
        this.molecularService = molecularService;
    }

    @Transactional(readOnly = true)
    public List<JdbcV2BusinessOperationsRepository.NotificationRow> notifications() {
        ActorContext actor = authorization.require(QUERY);
        return repository.notifications(actor.actorId(), actor.hospitalScope());
    }

    @Transactional(readOnly = true)
    public Map<String, List<Map<String, Object>>> overview() {
        ActorContext actor = authorization.require(ADMIN);
        return repository.overview(actor.hospitalScope());
    }

    @Transactional
    public void readNotification(UUID id) {
        ActorContext actor = authorization.require(QUERY);
        if (!repository.markNotificationRead(id, actor.actorId(), actor.hospitalScope(), Instant.now())) {
            throw reject("V2-NOTIFICATION-NOT-FOUND", "通知不存在");
        }
        append("PIS-V2-NOTIFICATION-READ", QUERY, actor, id, "通知已读");
    }

    @Transactional
    public UUID createSchedule(ScheduleCommand command) {
        ActorContext actor = authorization.require(ADMIN);
        require(command.staffReference(), "人员不能为空"); require(command.scheduleDate(), "排班日期不能为空");
        require(command.shiftCode(), "班次不能为空"); require(command.workArea(), "工作区域不能为空");
        UUID id = repository.insertSchedule(command, actor.hospitalScope(), actor.actorId(), Instant.now());
        append("PIS-V2-STAFF-SCHEDULE-CREATE", ADMIN, actor, id, "人员排班已保存"); return id;
    }

    @Transactional(readOnly = true)
    public List<JdbcV2BusinessOperationsRepository.ScheduleRow> schedules(String staff, LocalDate from, LocalDate to) {
        ActorContext actor = authorization.require(QUERY);
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? start.plusMonths(1).minusDays(1) : to;
        return repository.schedules(staff, start, end, actor.hospitalScope());
    }

    @Transactional
    public UUID createQualityDocument(QualityDocumentCommand command) {
        ActorContext actor = authorization.require(ADMIN);
        require(command.title(), "文档标题不能为空"); require(command.documentNo(), "文档编号不能为空");
        UUID id = repository.insertQualityDocument(command, actor.hospitalScope(), actor.actorId(), Instant.now());
        append("PIS-V2-QUALITY-DOCUMENT-CREATE", ADMIN, actor, id, "质量文档草稿已保存"); return id;
    }

    @Transactional(readOnly = true)
    public List<JdbcV2BusinessOperationsRepository.QualityDocumentRow> qualityDocuments() {
        ActorContext actor = authorization.require(QUERY); return repository.qualityDocuments(actor.hospitalScope());
    }

    @Transactional
    public JdbcV2BusinessOperationsRepository.QualityDocumentRow transitionQualityDocument(UUID id, String status) {
        ActorContext actor = authorization.require(ADMIN);
        if (!List.of("REVIEW", "PUBLISHED", "ARCHIVED").contains(status)) throw reject("V2-QUALITY-DOCUMENT-STATUS-INVALID", "质量文档状态无效");
        return repository.transitionQualityDocument(id, status, actor.actorId(), Instant.now(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-QUALITY-DOCUMENT-CONFLICT", "质量文档状态不可变更"));
    }

    @Transactional
    public UUID createEquipment(EquipmentCommand command) {
        ActorContext actor = authorization.require(ADMIN); require(command.equipmentCode(), "设备编码不能为空"); require(command.name(), "设备名称不能为空");
        UUID id = repository.insertEquipment(command, actor.hospitalScope(), actor.actorId(), Instant.now());
        append("PIS-V2-EQUIPMENT-CREATE", ADMIN, actor, id, "设备档案已保存"); return id;
    }

    @Transactional(readOnly = true)
    public List<JdbcV2BusinessOperationsRepository.EquipmentRow> equipment() {
        ActorContext actor = authorization.require(QUERY); return repository.equipment(actor.hospitalScope());
    }

    @Transactional
    public UUID createEquipmentEvent(UUID equipmentId, EquipmentEventCommand command) {
        ActorContext actor = authorization.require(ADMIN); require(equipmentId, "设备不能为空"); require(command.eventCode(), "设备事件不能为空");
        requireReference("EQUIPMENT", equipmentId, actor, "设备不存在或不在当前医院范围内");
        UUID id = repository.insertEquipmentEvent(equipmentId, command, actor.hospitalScope(), actor.actorId(), Instant.now());
        append("PIS-V2-EQUIPMENT-EVENT", ADMIN, actor, id, "设备维护/故障记录已保存"); return id;
    }

    @Transactional(readOnly = true)
    public List<JdbcV2BusinessOperationsRepository.EquipmentEventRow> equipmentEvents(UUID equipmentId) {
        ActorContext actor = authorization.require(QUERY); return repository.equipmentEvents(equipmentId, actor.hospitalScope());
    }

    @Transactional
    public UUID createCatalog(ConsumableCatalogCommand command) {
        ActorContext actor = authorization.require(ADMIN); require(command.materialCode(), "耗材编码不能为空"); require(command.name(), "耗材名称不能为空");
        UUID id = repository.insertCatalog(command, actor.hospitalScope(), actor.actorId(), Instant.now());
        append("PIS-V2-CONSUMABLE-CATALOG-CREATE", ADMIN, actor, id, "耗材目录已保存"); return id;
    }

    @Transactional(readOnly = true)
    public List<JdbcV2BusinessOperationsRepository.ConsumableCatalogRow> catalogs() {
        ActorContext actor = authorization.require(QUERY); return repository.catalogs(actor.hospitalScope());
    }

    @Transactional
    public UUID createBatch(UUID catalogId, ConsumableBatchCommand command) {
        ActorContext actor = authorization.require(ADMIN); require(catalogId, "耗材目录不能为空"); require(command.batchNo(), "批号不能为空");
        requireReference("CATALOG", catalogId, actor, "耗材目录不存在或不在当前医院范围内");
        UUID id = repository.insertBatch(catalogId, command, actor.hospitalScope(), Instant.now());
        append("PIS-V2-CONSUMABLE-BATCH-CREATE", ADMIN, actor, id, "耗材批次已保存"); return id;
    }

    @Transactional
    public UUID recordConsumableTransaction(UUID batchId, ConsumableTransactionCommand command) {
        ActorContext actor = authorization.require(MATERIAL); require(batchId, "耗材批次不能为空"); require(command.reason(), "库存交易原因不能为空");
        requireReference("BATCH", batchId, actor, "耗材批次不存在或不在当前医院范围内");
        if (!List.of("INBOUND", "OUTBOUND", "ADJUSTMENT").contains(command.directionCode())
                || command.quantity() == null || command.quantity().signum() <= 0) {
            throw reject("V2-CONSUMABLE-TRANSACTION-INVALID", "库存方向或数量无效");
        }
        try {
            UUID id = repository.insertConsumableTransaction(batchId, command, actor.hospitalScope(), actor.actorId(), Instant.now());
            append("PIS-V2-CONSUMABLE-TRANSACTION", MATERIAL, actor, id, "耗材库存交易已记录"); return id;
        } catch (IllegalStateException exception) {
            throw reject("V2-CONSUMABLE-INSUFFICIENT-STOCK", "库存不足，不能出库");
        }
    }

    @Transactional(readOnly = true)
    public List<JdbcV2BusinessOperationsRepository.StockRow> stock() {
        ActorContext actor = authorization.require(QUERY); return repository.stock(actor.hospitalScope());
    }

    @Transactional
    public UUID createRequisition(RequisitionCommand command) {
        ActorContext actor = authorization.require(MATERIAL); require(command.requestNo(), "领用单号不能为空");
        UUID id = repository.insertRequisition(command, actor.hospitalScope(), actor.actorId(), Instant.now());
        append("PIS-V2-CONSUMABLE-REQUISITION-CREATE", MATERIAL, actor, id, "耗材领用申请已保存"); return id;
    }

    @Transactional
    public void decideRequisition(UUID id, String status) {
        ActorContext actor = authorization.require(ADMIN);
        if (!List.of("APPROVED", "REJECTED").contains(status) || !repository.decideRequisition(id, status, actor.actorId(), Instant.now(), actor.hospitalScope())) {
            throw reject("V2-CONSUMABLE-REQUISITION-CONFLICT", "领用申请不可审批");
        }
        append("PIS-V2-CONSUMABLE-REQUISITION-DECIDE", ADMIN, actor, id, "耗材领用申请已审批");
    }

    @Transactional
    public UUID createProcurementRequest(ProcurementRequestCommand command) {
        ActorContext actor = authorization.require(ADMIN); require(command.requestNo(), "采购申请编号不能为空");
        require(command.departmentReference(), "申请科室不能为空"); require(command.reason(), "采购原因不能为空");
        if (command.items() == null || command.items().isEmpty() || command.items().stream().anyMatch(item -> item.quantity() == null
                || item.quantity().signum() <= 0 || item.estimatedAmount() == null || item.estimatedAmount().signum() < 0)) {
            throw reject("V2-PROCUREMENT-ITEM-INVALID", "采购项目及数量金额无效");
        }
        UUID id = repository.insertProcurementRequest(command, actor.hospitalScope(), actor.actorId(), Instant.now());
        append("PIS-V2-PROCUREMENT-REQUEST-CREATE", ADMIN, actor, id, "采购申请已保存"); return id;
    }

    @Transactional
    public void approveProcurement(UUID id, String decision, String comment) {
        ActorContext actor = authorization.require(ADMIN);
        requireReference("PROCUREMENT", id, actor, "采购申请不存在或不在当前医院范围内");
        if (!List.of("APPROVED", "REJECTED").contains(decision) || !repository.approveProcurement(id, decision, comment, actor.actorId(), Instant.now(), actor.hospitalScope())) {
            throw reject("V2-PROCUREMENT-APPROVAL-INVALID", "采购审批无效");
        }
        append("PIS-V2-PROCUREMENT-APPROVAL", ADMIN, actor, id, "采购申请已审批");
    }

    @Transactional
    public UUID attachProcurement(UUID requestId, String kind, String reference) {
        ActorContext actor = authorization.require(ADMIN); require(reference, "附件引用不能为空");
        requireReference("PROCUREMENT", requestId, actor, "采购申请不存在或不在当前医院范围内");
        UUID id = repository.insertProcurementAttachment(requestId, kind, reference, actor.actorId(), Instant.now(), actor.hospitalScope());
        append("PIS-V2-PROCUREMENT-ATTACHMENT", ADMIN, actor, id, "采购附件已关联"); return id;
    }

    @Transactional
    public UUID createSpace(SpaceCommand command) {
        ActorContext actor = authorization.require(ADMIN); require(command.spaceCode(), "空间编码不能为空"); require(command.name(), "空间名称不能为空");
        if (!List.of("POLLUTED", "SEMI_POLLUTED", "BUFFER", "CLEAN").contains(command.zoneCode())) throw reject("V2-SPACE-ZONE-INVALID", "空间分区无效");
        UUID id = repository.insertSpace(command, actor.hospitalScope(), actor.actorId(), Instant.now());
        append("PIS-V2-DEPARTMENT-SPACE-CREATE", ADMIN, actor, id, "科室空间已保存"); return id;
    }

    @Transactional(readOnly = true)
    public List<JdbcV2BusinessOperationsRepository.SpaceRow> spaces() {
        ActorContext actor = authorization.require(QUERY); return repository.spaces(actor.hospitalScope());
    }

    @Transactional
    public UUID addEnvironment(UUID spaceId, EnvironmentCommand command) {
        ActorContext actor = authorization.require(ADMIN); require(spaceId, "空间不能为空"); require(command.metricCode(), "环境指标不能为空");
        requireReference("SPACE", spaceId, actor, "空间不存在或不在当前医院范围内");
        UUID id = repository.insertEnvironment(spaceId, command, actor.hospitalScope(), Instant.now());
        append("PIS-V2-SPACE-ENVIRONMENT", ADMIN, actor, id, "环境记录已保存"); return id;
    }

    @Transactional
    public UUID addSafetyCheck(UUID spaceId, SafetyCheckCommand command) {
        ActorContext actor = authorization.require(ADMIN); require(spaceId, "空间不能为空"); require(command.checkCode(), "检查项目不能为空");
        requireReference("SPACE", spaceId, actor, "空间不存在或不在当前医院范围内");
        UUID id = repository.insertSafetyCheck(spaceId, command, actor.hospitalScope(), actor.actorId(), Instant.now());
        append("PIS-V2-SPACE-SAFETY", ADMIN, actor, id, "空间安全检查已保存"); return id;
    }

    @Transactional
    public UUID createCriticalValue(UUID caseId, CriticalValueCommand command) {
        ActorContext actor = authorization.require(DIAGNOSIS); require(caseId, "病例不能为空"); require(command.valueTypeCode(), "危急值类型不能为空");
        requireReference("CASE", caseId, actor, "病例不存在或不在当前医院范围内");
        UUID id = repository.insertCriticalValue(caseId, command, actor.hospitalScope(), actor.actorId(), Instant.now());
        append("PIS-V2-CRITICAL-VALUE-CREATE", DIAGNOSIS, actor, id, "危急值已登记"); return id;
    }

    @Transactional(readOnly = true)
    public List<JdbcV2BusinessOperationsRepository.CriticalValueRow> criticalValues() {
        ActorContext actor = authorization.require(QUERY); return repository.criticalValues(actor.hospitalScope());
    }

    @Transactional
    public UUID notifyCriticalValue(UUID id, CriticalNotificationCommand command) {
        ActorContext actor = authorization.require(DIAGNOSIS); require(id, "危急值不能为空"); require(command.recipientReference(), "接收人不能为空");
        require(command.departmentReference(), "接收科室不能为空");
        require(command.methodCode(), "通知方式不能为空");
        requireReference("CRITICAL_VALUE", id, actor, "危急值不存在或不在当前医院范围内");
        UUID notificationId = repository.notifyCriticalValue(id, command, actor.hospitalScope(), actor.actorId(), Instant.now());
        append("PIS-V2-CRITICAL-VALUE-NOTIFY", DIAGNOSIS, actor, notificationId, "危急值已通知"); return notificationId;
    }

    @Transactional
    public void acknowledgeCriticalValue(UUID notificationId) {
        ActorContext actor = authorization.require(DIAGNOSIS);
        if (!repository.acknowledgeCriticalValue(notificationId, actor.actorId(), Instant.now(), actor.hospitalScope())) throw reject("V2-CRITICAL-VALUE-CONFLICT", "危急值通知已确认或不存在");
        append("PIS-V2-CRITICAL-VALUE-ACK", DIAGNOSIS, actor, notificationId, "危急值已确认");
    }

    @Transactional
    public UUID addCriticalFeedback(UUID id, String content) {
        ActorContext actor = authorization.require(DIAGNOSIS); require(content, "反馈内容不能为空");
        UUID feedbackId = repository.addCriticalFeedback(id, content, actor.hospitalScope(), actor.actorId(), Instant.now());
        append("PIS-V2-CRITICAL-VALUE-FEEDBACK", DIAGNOSIS, actor, feedbackId, "危急值反馈已记录"); return feedbackId;
    }

    @Transactional
    public OutputActionResult distributeReport(UUID reportId, String target, String idempotencyKey) {
        ActorContext actor = authorization.require("P14-PERM-055");
        require(reportId, "报告不能为空"); require(target, "发放目标不能为空");
        require(idempotencyKey, "幂等键不能为空");
        String operation = "REPORT_DISTRIBUTION";
        String payloadDigest = digest(reportId, target);
        UUID replay = replayOutput(operation, idempotencyKey, payloadDigest, actor);
        if (replay != null) return new OutputActionResult(replay, "REPLAYED", true, null);
        if (!repository.lockEffectiveReport(reportId, actor.hospitalScope())) {
            throw reject("V2-REPORT-OUTPUT-NOT-EFFECTIVE", "只有当前医院范围内的生效报告可以发放");
        }
        replay = replayOutput(operation, idempotencyKey, payloadDigest, actor);
        if (replay != null) return new OutputActionResult(replay, "REPLAYED", true, null);
        var report = repository.reportOutput(reportId, actor.hospitalScope()).orElseThrow();
        var delivery = reportOutputPort.distribute(new ReportOutputPort.DistributionCommand(report.reportId(),
                report.reportNo(), target, report.pdfContentHash()));
        Instant now = Instant.now();
        UUID id = repository.insertDistribution(reportId, target, delivery.statusCode(),
                delivery.deliveryReference(), delivery.errorCode(), delivery.errorMessage(), actor.hospitalScope(),
                actor.actorId(), now);
        repository.insertOutputIdempotency(operation, idempotencyKey, payloadDigest, id, actor.hospitalScope(),
                actor.actorId(), now);
        append("PIS-V2-REPORT-DISTRIBUTION-REQUEST", "P14-PERM-055", actor, id,
                "报告发放结果=" + delivery.statusCode());
        return new OutputActionResult(id, delivery.statusCode(), false, delivery.errorMessage());
    }

    @Transactional
    public void updateDistribution(UUID id, String status, String error) {
        ActorContext actor = authorization.require("P14-PERM-055");
        if (!List.of("SENT", "RETRY_PENDING", "FAILED").contains(status) || !repository.updateDistribution(id, status, error, actor.hospitalScope(), Instant.now())) throw reject("V2-REPORT-DISTRIBUTION-CONFLICT", "报告发放不可更新");
        append("PIS-V2-REPORT-DISTRIBUTION-UPDATE", "P14-PERM-055", actor, id, "报告发放状态已记录");
    }

    @Transactional
    public OutputActionResult printReport(UUID reportId, PrintCommand command) {
        ActorContext actor = authorization.require("P14-PERM-055");
        require(command.identityReference(), "身份凭据不能为空");
        require(command.terminalReference(), "终端标识不能为空");
        require(command.printerReference(), "打印机配置不能为空");
        require(command.idempotencyKey(), "幂等键不能为空");
        if (command.copyCount() < 1 || command.copyCount() > 10) {
            throw reject("V2-REPORT-PRINT-COPIES", "单次报告打印份数必须为1至10份");
        }
        String operation = "REPORT_PRINT";
        String payloadDigest = digest(reportId, command.identityReference(), command.terminalReference(),
                command.printerReference(), command.copyCount());
        UUID replay = replayOutput(operation, command.idempotencyKey(), payloadDigest, actor);
        if (replay != null) return new OutputActionResult(replay, "REPLAYED", true, null);
        if (!repository.lockEffectiveReport(reportId, actor.hospitalScope())) {
            throw reject("V2-REPORT-OUTPUT-NOT-EFFECTIVE", "只有当前医院范围内的生效报告可以打印");
        }
        replay = replayOutput(operation, command.idempotencyKey(), payloadDigest, actor);
        if (replay != null) return new OutputActionResult(replay, "REPLAYED", true, null);
        var report = repository.reportOutput(reportId, actor.hospitalScope()).orElseThrow();
        if (report.patientReference() == null || !report.patientReference().equals(command.identityReference())) {
            throw reject("V2-REPORT-PRINT-IDENTITY-MISMATCH", "身份核验未通过，不得打印报告");
        }
        var print = reportOutputPort.print(new ReportOutputPort.PrintCommand(report.reportId(), report.reportNo(),
                command.identityReference(), command.terminalReference(), command.printerReference(),
                command.copyCount(), report.pdfContent(), report.pdfContentHash()));
        Instant now = Instant.now();
        UUID id = repository.insertPrintRecord(reportId, command.identityReference(), command.terminalReference(),
                command.printerReference(), print.resultCode(), command.copyCount(), print.deviceJobReference(),
                print.errorCode(), print.errorMessage(), actor.hospitalScope(), actor.actorId(), now);
        repository.insertOutputIdempotency(operation, command.idempotencyKey(), payloadDigest, id,
                actor.hospitalScope(), actor.actorId(), now);
        append("PIS-V2-REPORT-PRINT", "P14-PERM-055", actor, id, "报告打印结果=" + print.resultCode());
        return new OutputActionResult(id, print.resultCode(), false, print.errorMessage());
    }

    @Transactional(readOnly = true)
    public List<JdbcV2BusinessOperationsRepository.ReportDistributionRow> reportDistributions(UUID reportId) {
        ActorContext actor = authorization.require("P14-PERM-055");
        requireReference("REPORT", reportId, actor, "报告不存在或不在当前医院范围内");
        return repository.reportDistributions(reportId, actor.hospitalScope());
    }

    @Transactional(readOnly = true)
    public List<JdbcV2BusinessOperationsRepository.ReportPrintRow> reportPrints(UUID reportId) {
        ActorContext actor = authorization.require("P14-PERM-055");
        requireReference("REPORT", reportId, actor, "报告不存在或不在当前医院范围内");
        return repository.reportPrints(reportId, actor.hospitalScope());
    }

    @Transactional(readOnly = true)
    public ReportOutputPort.PrinterStatus reportPrinterStatus(String printerReference) {
        authorization.require("P14-PERM-055");
        require(printerReference, "打印机配置不能为空");
        return reportOutputPort.printerStatus(printerReference);
    }

    @Transactional
    public UUID createAddress(AddressCommand command) {
        ActorContext actor = authorization.require(ADMIN); require(command.addressName(), "地址名称不能为空"); require(command.addressText(), "地址不能为空");
        UUID id = repository.insertAddress(command, actor.hospitalScope(), actor.actorId(), Instant.now()); append("PIS-V2-LOGISTICS-ADDRESS", ADMIN, actor, id, "常用地址已保存"); return id;
    }

    @Transactional(readOnly = true)
    public List<JdbcV2BusinessOperationsRepository.AddressRow> addresses() { ActorContext actor = authorization.require(QUERY); return repository.addresses(actor.hospitalScope()); }

    @Transactional
    public UUID createPackage(PackageCommand command, List<PackageItem> items) {
        ActorContext actor = authorization.require(MATERIAL); require(command.caseId(), "病例不能为空"); require(command.courierCompany(), "快递公司不能为空");
        require(command.recipientReference(), "接收人不能为空"); require(command.addressText(), "收件地址不能为空");
        requireReference("CASE", command.caseId(), actor, "病例不存在或不在当前医院范围内");
        UUID id = repository.insertPackage(command, actor.hospitalScope(), actor.actorId(), Instant.now()); for (PackageItem item : items == null ? List.<PackageItem>of() : items) repository.insertPackageItem(id, item);
        append("PIS-V2-LOGISTICS-PACKAGE-CREATE", MATERIAL, actor, id, "外送包裹已创建"); return id;
    }

    @Transactional
    public UUID addPackageEvent(UUID packageId, String status, String note) {
        ActorContext actor = authorization.require(MATERIAL);
        if (!List.of("SENT", "IN_TRANSIT", "DELIVERED", "DELAYED", "LOST", "RETURNED", "DAMAGED").contains(status)) throw reject("V2-LOGISTICS-STATUS-INVALID", "物流状态无效");
        requireReference("PACKAGE", packageId, actor, "物流包裹不存在或不在当前医院范围内");
        UUID id = repository.insertLogisticsEvent(packageId, status, note, actor.hospitalScope(), actor.actorId(), Instant.now()); append("PIS-V2-LOGISTICS-EVENT", MATERIAL, actor, id, "物流状态已记录"); return id;
    }

    @Transactional
    public UUID createMolecularProject(MolecularProjectCommand command) { ActorContext actor = authorization.require(ADMIN); require(command.projectCode(), "项目编码不能为空"); require(command.projectName(), "项目名称不能为空"); require(command.projectTypeCode(), "项目类型不能为空"); UUID id = repository.insertMolecularProject(command, actor.hospitalScope()); append("PIS-V2-MOLECULAR-PROJECT", ADMIN, actor, id, "分子项目已保存"); return id; }
    @Transactional
    public UUID createMolecularInstrument(MolecularInstrumentCommand command) { ActorContext actor = authorization.require(ADMIN); require(command.instrumentCode(), "设备编码不能为空"); require(command.name(), "设备名称不能为空"); require(command.adapterCode(), "设备适配器不能为空"); UUID id = repository.insertMolecularInstrument(command, actor.hospitalScope()); append("PIS-V2-MOLECULAR-INSTRUMENT", ADMIN, actor, id, "分子设备已保存"); return id; }
    @Transactional
    public UUID createMolecularReagent(MolecularReagentCommand command) { ActorContext actor = authorization.require(ADMIN); require(command.kitCode(), "试剂盒编码不能为空"); require(command.batchNo(), "试剂批号不能为空"); require(command.expiryDate(), "试剂有效期不能为空"); if (command.expiryDate().isBefore(LocalDate.now())) throw reject("V2-MOLECULAR-REAGENT-EXPIRED", "不能新增已过期试剂批次"); UUID id = repository.insertMolecularReagent(command, actor.hospitalScope()); append("PIS-V2-MOLECULAR-REAGENT", ADMIN, actor, id, "分子试剂批次已保存"); return id; }
    @Transactional
    public UUID createMolecularTest(MolecularTestCommand command) { return molecularService.createTest(
            new V2MolecularApplicationService.CreateTestCommand(command.caseId(), command.specimenId(), command.projectId(),
                    command.detectionNo(), command.instrumentId(), command.reagentKitId(), command.rawDataReference(),
                    "LEGACY-CREATE-" + UUID.randomUUID())).id(); }
    @Transactional(noRollbackFor = P15BusinessException.class)
    public void startMolecularTest(UUID id) { molecularService.startTest(id,
            new V2MolecularApplicationService.StartTestCommand("LEGACY-START-" + id)); }
    @Transactional
    public void completeMolecularTest(UUID id, String structuredResult, String analysisResult) { molecularService.completeTest(id,
            new V2MolecularApplicationService.CompleteTestCommand(structuredResult, analysisResult, "LEGACY-COMPLETE-" + id)); }

    @Transactional
    public UUID archiveDigitalSlide(ArchiveCommand command) { ActorContext actor = authorization.require(MATERIAL); require(command.storagePath(), "存储路径不能为空"); requireReference("DIGITAL_SLIDE", command.digitalSlideId(), actor, "数字切片不存在或不在当前医院范围内"); UUID id = repository.archiveDigitalSlide(command, actor.hospitalScope(), Instant.now()); append("PIS-V2-DIGITAL-ARCHIVE", MATERIAL, actor, id, "数字切片已归档"); return id; }
    @Transactional
    public void updateDigitalArchive(UUID id, String status) { ActorContext actor = authorization.require(MATERIAL); if (!List.of("INDEXED", "RESTORED", "INTEGRITY_ERROR").contains(status) || !repository.updateArchiveStatus(id, status, actor.hospitalScope(), Instant.now())) throw reject("V2-DIGITAL-ARCHIVE-CONFLICT", "数字切片归档状态不可更新"); append("PIS-V2-DIGITAL-ARCHIVE-UPDATE", MATERIAL, actor, id, "数字切片归档状态已更新"); }

    @Transactional
    public UUID createRegionalShare(JdbcV2BusinessOperationsRepository.RegionalShareCommand command) { ActorContext actor = authorization.require(DIAGNOSIS); require(command.receivingOrganization(), "接收机构不能为空"); requireReference("CASE", command.caseId(), actor, "病例不存在或不在当前医院范围内"); UUID id = repository.createRegionalShare(command, actor.hospitalScope(), actor.actorId(), Instant.now()); append("PIS-V2-REGIONAL-SHARE-CREATE", DIAGNOSIS, actor, id, "区域病例共享已创建"); return id; }
    @Transactional
    public UUID recordRegionalAccess(UUID shareId, String accessor, String action) { ActorContext actor = authorization.require(QUERY); require(accessor, "访问人不能为空"); require(action, "访问动作不能为空"); requireReference("REGIONAL_SHARE", shareId, actor, "共享记录不存在或不在当前医院范围内"); UUID id = repository.recordRegionalAccess(shareId, accessor, action, actor.hospitalScope(), Instant.now()); append("PIS-V2-REGIONAL-SHARE-ACCESS", QUERY, actor, id, "区域共享访问已审计"); return id; }

    @Transactional
    public UUID recordIncome(IncomeCommand command) { ActorContext actor = authorization.require(ADMIN); UUID id = repository.insertIncome(command, actor.hospitalScope(), Instant.now()); append("PIS-V2-INCOME-FACT", ADMIN, actor, id, "收入事实已记录"); return id; }
    @Transactional
    public UUID createMigrationJob(MigrationJobCommand command) { ActorContext actor = authorization.require(ADMIN); require(command.sourceCode(), "来源编码不能为空"); if (!List.of("IMPORT", "READ_ONLY").contains(command.modeCode())) throw reject("V2-MIGRATION-MODE-INVALID", "迁移任务模式无效"); if (!List.of("CREATED", "RUNNING", "COMPLETED", "FAILED", "READ_ONLY").contains(command.statusCode())) throw reject("V2-MIGRATION-STATUS-INVALID", "迁移任务状态无效"); UUID id = repository.createMigrationJob(command, actor.hospitalScope(), actor.actorId(), Instant.now()); append("PIS-V2-MIGRATION-JOB", ADMIN, actor, id, "迁移任务已建立"); return id; }
    @Transactional
    public UUID addMigrationRecord(MigrationRecordCommand command) { ActorContext actor = authorization.require(ADMIN); requireReference("MIGRATION_JOB", command.jobId(), actor, "迁移任务不存在或不在当前医院范围内"); require(command.legacyType(), "历史数据类型不能为空"); require(command.legacyKey(), "历史数据键不能为空"); if (!List.of("PENDING", "MAPPED", "SKIPPED", "FAILED").contains(command.recordStatus())) throw reject("V2-MIGRATION-RECORD-STATUS-INVALID", "迁移记录状态无效"); UUID id = repository.insertMigrationRecord(command, actor.hospitalScope()); append("PIS-V2-MIGRATION-RECORD", ADMIN, actor, id, "迁移映射已保存"); return id; }
    @Transactional
    public UUID addMigrationError(MigrationErrorCommand command) { ActorContext actor = authorization.require(ADMIN); requireReference("MIGRATION_JOB", command.jobId(), actor, "迁移任务不存在或不在当前医院范围内"); require(command.errorCode(), "错误编码不能为空"); require(command.errorMessage(), "错误说明不能为空"); if (command.retryCount() < 0) throw reject("V2-MIGRATION-RETRY-INVALID", "重试次数不能小于零"); UUID id = repository.insertMigrationError(command, actor.hospitalScope()); append("PIS-V2-MIGRATION-ERROR", ADMIN, actor, id, "迁移错误已记录"); return id; }

    private void append(String action, String permission, ActorContext actor, UUID id, String detail) {
        audit.append(action, permission, actor, "ALLOWED", "COMPLETED", id, "PIS-V2-BUSINESS-FACT", UUID.randomUUID().toString(), detail);
    }
    private static void require(Object value, String message) { if (value == null || value.toString().isBlank()) throw reject("V2-OPERATIONS-INVALID", message); }
    private void requireReference(String entity, UUID id, ActorContext actor, String message) {
        if (!repository.belongs(entity, id, actor.hospitalScope())) throw reject("V2-OPERATIONS-REFERENCE-NOT-FOUND", message);
    }
    private UUID replayOutput(String operation, String key, String payloadDigest, ActorContext actor) {
        var prior = repository.findOutputIdempotency(operation, key, actor.hospitalScope()).orElse(null);
        if (prior == null) return null;
        if (!prior.payloadDigest().equals(payloadDigest)) {
            throw reject("V2-REPORT-OUTPUT-IDEMPOTENCY-CONFLICT", "同一幂等键对应的报告输出内容冲突");
        }
        return prior.resultEntityId();
    }
    private static String digest(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }
    private static P15BusinessException reject(String code, String message) { return new P15BusinessException(code, message); }

    public record PrintCommand(String identityReference, String terminalReference, String printerReference,
            int copyCount, String idempotencyKey) { }
    public record OutputActionResult(UUID id, String statusCode, boolean duplicate, String errorMessage) { }
}
