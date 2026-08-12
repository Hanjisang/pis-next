package com.hanjisang.pis.v2.operations.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.operations.application.V2BusinessOperationsApplicationService;
import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository;

@RestController
@RequestMapping("/api/v2/operations")
public class V2BusinessOperationsController {

    private final V2BusinessOperationsApplicationService service;
    public V2BusinessOperationsController(V2BusinessOperationsApplicationService service) { this.service = service; }

    @GetMapping("/overview")
    public Map<String, List<Map<String, Object>>> overview() { return service.overview(); }

    @GetMapping("/notifications")
    public List<JdbcV2BusinessOperationsRepository.NotificationRow> notifications() { return service.notifications(); }
    @PostMapping("/notifications/{id}/read")
    public void readNotification(@PathVariable UUID id) { service.readNotification(id); }

    @GetMapping("/staff-schedules")
    public List<JdbcV2BusinessOperationsRepository.ScheduleRow> schedules(@RequestParam(required = false) String staffReference,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return service.schedules(staffReference, from, to);
    }
    @PostMapping("/staff-schedules")
    public IdResponse createSchedule(@RequestBody ScheduleRequest request) {
        return new IdResponse(service.createSchedule(new JdbcV2BusinessOperationsRepository.ScheduleCommand(request.staffReference(), request.scheduleDate(), request.shiftCode(), request.workArea(), request.note())));
    }

    @GetMapping("/quality-documents")
    public List<JdbcV2BusinessOperationsRepository.QualityDocumentRow> qualityDocuments() { return service.qualityDocuments(); }
    @PostMapping("/quality-documents")
    public IdResponse createQualityDocument(@RequestBody QualityDocumentRequest request) { return new IdResponse(service.createQualityDocument(new JdbcV2BusinessOperationsRepository.QualityDocumentCommand(request.title(), request.documentNo(), request.categoryCode(), request.versionLabel(), request.effectiveAt(), request.ownerReference(), request.contentReference(), request.previousDocumentId()))); }
    @PostMapping("/quality-documents/{id}/{status}")
    public JdbcV2BusinessOperationsRepository.QualityDocumentRow transitionQualityDocument(@PathVariable UUID id, @PathVariable String status) { return service.transitionQualityDocument(id, status.toUpperCase()); }

    @GetMapping("/equipment")
    public List<JdbcV2BusinessOperationsRepository.EquipmentRow> equipment() { return service.equipment(); }
    @PostMapping("/equipment")
    public IdResponse createEquipment(@RequestBody EquipmentRequest request) { return new IdResponse(service.createEquipment(new JdbcV2BusinessOperationsRepository.EquipmentCommand(request.equipmentCode(), request.name(), request.categoryCode(), request.manufacturer(), request.model(), request.serialNo(), request.locationReference(), request.custodianReference(), request.purchaseDate(), request.warrantyUntil(), request.calibrationDueAt(), request.statusCode()))); }
    @GetMapping("/equipment/{id}/events")
    public List<JdbcV2BusinessOperationsRepository.EquipmentEventRow> equipmentEvents(@PathVariable UUID id) { return service.equipmentEvents(id); }
    @PostMapping("/equipment/{id}/events")
    public IdResponse createEquipmentEvent(@PathVariable UUID id, @RequestBody EquipmentEventRequest request) { return new IdResponse(service.createEquipmentEvent(id, new JdbcV2BusinessOperationsRepository.EquipmentEventCommand(request.eventCode(), request.occurredAt(), request.description(), request.amount()))); }

    @GetMapping("/consumables/catalog")
    public List<JdbcV2BusinessOperationsRepository.ConsumableCatalogRow> catalogs() { return service.catalogs(); }
    @PostMapping("/consumables/catalog")
    public IdResponse createCatalog(@RequestBody ConsumableCatalogRequest request) { return new IdResponse(service.createCatalog(new JdbcV2BusinessOperationsRepository.ConsumableCatalogCommand(request.materialCode(), request.name(), request.categoryCode(), request.specification(), request.unitCode(), request.manufacturer(), request.supplier(), request.hazardous()))); }
    @PostMapping("/consumables/catalog/{catalogId}/batches")
    public IdResponse createBatch(@PathVariable UUID catalogId, @RequestBody ConsumableBatchRequest request) { return new IdResponse(service.createBatch(catalogId, new JdbcV2BusinessOperationsRepository.ConsumableBatchCommand(request.batchNo(), request.expiryDate(), request.storageLocation()))); }
    @GetMapping("/consumables/stock")
    public List<JdbcV2BusinessOperationsRepository.StockRow> stock() { return service.stock(); }
    @PostMapping("/consumables/batches/{batchId}/transactions")
    public IdResponse transaction(@PathVariable UUID batchId, @RequestBody ConsumableTransactionRequest request) { return new IdResponse(service.recordConsumableTransaction(batchId, new JdbcV2BusinessOperationsRepository.ConsumableTransactionCommand(request.directionCode(), request.quantity(), request.reason(), request.sourceReference(), request.occurredAt()))); }
    @PostMapping("/consumables/requisitions")
    public IdResponse requisition(@RequestBody RequisitionRequest request) { return new IdResponse(service.createRequisition(new JdbcV2BusinessOperationsRepository.RequisitionCommand(request.requestNo(), request.departmentReference(), request.purpose(), request.items() == null ? List.of() : request.items().stream().map(item -> new JdbcV2BusinessOperationsRepository.RequisitionItem(item.catalogId(), item.quantity())).toList()))); }
    @PostMapping("/consumables/requisitions/{id}/decision")
    public void requisitionDecision(@PathVariable UUID id, @RequestBody DecisionRequest request) { service.decideRequisition(id, request.status()); }

    @PostMapping("/procurement/requests")
    public IdResponse procurement(@RequestBody ProcurementRequest request) { return new IdResponse(service.createProcurementRequest(new JdbcV2BusinessOperationsRepository.ProcurementRequestCommand(request.requestNo(), request.departmentReference(), request.reason(), request.items() == null ? List.of() : request.items().stream().map(item -> new JdbcV2BusinessOperationsRepository.ProcurementItem(item.materialReference(), item.quantity(), item.estimatedAmount(), item.supplier())).toList()))); }
    @PostMapping("/procurement/requests/{id}/approval")
    public void procurementApproval(@PathVariable UUID id, @RequestBody ApprovalRequest request) { service.approveProcurement(id, request.decision(), request.comment()); }
    @PostMapping("/procurement/requests/{id}/attachments")
    public IdResponse procurementAttachment(@PathVariable UUID id, @RequestBody AttachmentRequest request) { return new IdResponse(service.attachProcurement(id, request.kind(), request.reference())); }

    @GetMapping("/spaces")
    public List<JdbcV2BusinessOperationsRepository.SpaceRow> spaces() { return service.spaces(); }
    @PostMapping("/spaces")
    public IdResponse space(@RequestBody SpaceRequest request) { return new IdResponse(service.createSpace(new JdbcV2BusinessOperationsRepository.SpaceCommand(request.parentId(), request.spaceCode(), request.name(), request.zoneCode(), request.areaValue(), request.administratorReference(), request.description(), request.viewReference()))); }
    @PostMapping("/spaces/{id}/environment")
    public IdResponse environment(@PathVariable UUID id, @RequestBody EnvironmentRequest request) { return new IdResponse(service.addEnvironment(id, new JdbcV2BusinessOperationsRepository.EnvironmentCommand(request.metricCode(), request.measureValue(), request.unitCode(), request.measuredAt(), request.sourceReference()))); }
    @PostMapping("/spaces/{id}/safety")
    public IdResponse safety(@PathVariable UUID id, @RequestBody SafetyRequest request) { return new IdResponse(service.addSafetyCheck(id, new JdbcV2BusinessOperationsRepository.SafetyCheckCommand(request.checkCode(), request.resultCode(), request.note()))); }

    @GetMapping("/critical-values")
    public List<JdbcV2BusinessOperationsRepository.CriticalValueRow> criticalValues() { return service.criticalValues(); }
    @PostMapping("/cases/{caseId}/critical-values")
    public IdResponse critical(@PathVariable UUID caseId, @RequestBody CriticalValueRequest request) { return new IdResponse(service.createCriticalValue(caseId, new JdbcV2BusinessOperationsRepository.CriticalValueCommand(request.valueTypeCode(), request.gradeCode(), request.triggerReference(), request.dueAt()))); }
    @PostMapping("/critical-values/{id}/notify")
    public IdResponse criticalNotify(@PathVariable UUID id, @RequestBody CriticalNotificationRequest request) { return new IdResponse(service.notifyCriticalValue(id, new JdbcV2BusinessOperationsRepository.CriticalNotificationCommand(request.departmentReference(), request.recipientReference(), request.methodCode(), request.message(), request.businessPath()))); }
    @PostMapping("/critical-value-notifications/{id}/acknowledge")
    public void criticalAck(@PathVariable UUID id) { service.acknowledgeCriticalValue(id); }
    @PostMapping("/critical-values/{id}/feedback")
    public IdResponse criticalFeedback(@PathVariable UUID id, @RequestBody FeedbackRequest request) { return new IdResponse(service.addCriticalFeedback(id, request.content())); }

    @PostMapping("/reports/{reportId}/distribution")
    public IdResponse distribution(@PathVariable UUID reportId, @RequestBody DistributionRequest request) { return new IdResponse(service.distributeReport(reportId, request.targetCode())); }
    @PostMapping("/report-distributions/{id}/status")
    public void distributionStatus(@PathVariable UUID id, @RequestBody DistributionStatusRequest request) { service.updateDistribution(id, request.status(), request.error()); }
    @PostMapping("/reports/{reportId}/print")
    public IdResponse print(@PathVariable UUID reportId, @RequestBody PrintRequest request) { return new IdResponse(service.printReport(reportId, new V2BusinessOperationsApplicationService.PrintCommand(request.identityReference(), request.terminalReference(), request.printerReference(), request.resultCode(), request.copyCount()))); }

    @GetMapping("/logistics/addresses")
    public List<JdbcV2BusinessOperationsRepository.AddressRow> addresses() { return service.addresses(); }
    @PostMapping("/logistics/addresses")
    public IdResponse address(@RequestBody AddressRequest request) { return new IdResponse(service.createAddress(new JdbcV2BusinessOperationsRepository.AddressCommand(request.addressName(), request.recipientName(), request.phone(), request.addressText()))); }
    @PostMapping("/logistics/packages")
    public IdResponse logisticsPackage(@RequestBody PackageRequest request) { return new IdResponse(service.createPackage(new JdbcV2BusinessOperationsRepository.PackageCommand(request.caseId(), request.consultationId(), request.courierCompany(), request.trackingNo(), request.senderReference(), request.recipientReference(), request.addressText()), request.items() == null ? List.of() : request.items().stream().map(item -> new JdbcV2BusinessOperationsRepository.PackageItem(item.blockId(), item.slideId(), item.documentReference())).toList())); }
    @PostMapping("/logistics/packages/{id}/events")
    public IdResponse logisticsEvent(@PathVariable UUID id, @RequestBody LogisticsEventRequest request) { return new IdResponse(service.addPackageEvent(id, request.statusCode(), request.note())); }

    @PostMapping("/molecular/projects")
    public IdResponse molecularProject(@RequestBody MolecularProjectRequest request) { return new IdResponse(service.createMolecularProject(new JdbcV2BusinessOperationsRepository.MolecularProjectCommand(request.projectCode(), request.projectName(), request.projectTypeCode()))); }
    @PostMapping("/molecular/instruments")
    public IdResponse molecularInstrument(@RequestBody MolecularInstrumentRequest request) { return new IdResponse(service.createMolecularInstrument(new JdbcV2BusinessOperationsRepository.MolecularInstrumentCommand(request.instrumentCode(), request.name(), request.adapterCode()))); }
    @PostMapping("/molecular/reagents")
    public IdResponse molecularReagent(@RequestBody MolecularReagentRequest request) { return new IdResponse(service.createMolecularReagent(new JdbcV2BusinessOperationsRepository.MolecularReagentCommand(request.kitCode(), request.manufacturer(), request.batchNo(), request.expiryDate()))); }
    @PostMapping("/molecular/tests")
    public IdResponse molecularTest(@RequestBody MolecularTestRequest request) { return new IdResponse(service.createMolecularTest(new JdbcV2BusinessOperationsRepository.MolecularTestCommand(request.caseId(), request.specimenId(), request.projectId(), request.detectionNo(), request.instrumentId(), request.reagentKitId(), request.rawDataReference(), request.structuredResult(), request.analysisResult()))); }
    @PostMapping("/molecular/tests/{id}/complete")
    public void molecularComplete(@PathVariable UUID id, @RequestBody MolecularCompleteRequest request) { service.completeMolecularTest(id, request.structuredResult(), request.analysisResult()); }

    @PostMapping("/digital-archive")
    public IdResponse digitalArchive(@RequestBody ArchiveRequest request) { return new IdResponse(service.archiveDigitalSlide(new JdbcV2BusinessOperationsRepository.ArchiveCommand(request.digitalSlideId(), request.storagePath(), request.storageTier(), request.filename(), request.formatCode(), request.pathologyNo(), request.slideNo(), request.patientReference(), request.organReference(), request.integrityDigest()))); }
    @PostMapping("/digital-archive/{id}/status")
    public void digitalArchiveStatus(@PathVariable UUID id, @RequestBody StatusRequest request) { service.updateDigitalArchive(id, request.status()); }

    @PostMapping("/regional/shares")
    public IdResponse regionalShare(@RequestBody RegionalShareRequest request) { return new IdResponse(service.createRegionalShare(new JdbcV2BusinessOperationsRepository.RegionalShareCommand(request.caseId(), request.receivingOrganization(), request.receivingDoctor(), request.expiresAt(), request.patientAuthorized(), request.items() == null ? List.of() : request.items().stream().map(item -> new JdbcV2BusinessOperationsRepository.RegionalShareItem(item.reportId(), item.digitalSlideId(), item.attachmentReference())).toList()))); }
    @PostMapping("/regional/shares/{id}/access")
    public IdResponse regionalAccess(@PathVariable UUID id, @RequestBody RegionalAccessRequest request) { return new IdResponse(service.recordRegionalAccess(id, request.accessorReference(), request.actionCode())); }

    @PostMapping("/income")
    public IdResponse income(@RequestBody IncomeRequest request) { return new IdResponse(service.recordIncome(new JdbcV2BusinessOperationsRepository.IncomeCommand(request.caseId(), request.projectCode(), request.amount(), request.occurredAt(), request.sourceReference()))); }
    @PostMapping("/migration/jobs")
    public IdResponse migrationJob(@RequestBody MigrationJobRequest request) { return new IdResponse(service.createMigrationJob(new JdbcV2BusinessOperationsRepository.MigrationJobCommand(request.sourceCode(), request.modeCode(), request.statusCode()))); }
    @PostMapping("/migration/records")
    public IdResponse migrationRecord(@RequestBody MigrationRecordRequest request) { return new IdResponse(service.addMigrationRecord(new JdbcV2BusinessOperationsRepository.MigrationRecordCommand(request.jobId(), request.legacyType(), request.legacyKey(), request.localType(), request.localId(), request.recordStatus(), request.rawReference(), request.mappedAt()))); }
    @PostMapping("/migration/errors")
    public IdResponse migrationError(@RequestBody MigrationErrorRequest request) { return new IdResponse(service.addMigrationError(new JdbcV2BusinessOperationsRepository.MigrationErrorCommand(request.jobId(), request.recordId(), request.errorCode(), request.errorMessage(), request.retryCount()))); }

    public record IdResponse(UUID id) { }
    public record ScheduleRequest(String staffReference, LocalDate scheduleDate, String shiftCode, String workArea, String note) { }
    public record QualityDocumentRequest(String title, String documentNo, String categoryCode, String versionLabel, Instant effectiveAt, String ownerReference, String contentReference, UUID previousDocumentId) { }
    public record EquipmentRequest(String equipmentCode, String name, String categoryCode, String manufacturer, String model, String serialNo, String locationReference, String custodianReference, LocalDate purchaseDate, LocalDate warrantyUntil, LocalDate calibrationDueAt, String statusCode) { }
    public record EquipmentEventRequest(String eventCode, Instant occurredAt, String description, BigDecimal amount) { }
    public record ConsumableCatalogRequest(String materialCode, String name, String categoryCode, String specification, String unitCode, String manufacturer, String supplier, boolean hazardous) { }
    public record ConsumableBatchRequest(String batchNo, LocalDate expiryDate, String storageLocation) { }
    public record ConsumableTransactionRequest(String directionCode, BigDecimal quantity, String reason, String sourceReference, Instant occurredAt) { }
    public record RequisitionRequest(String requestNo, String departmentReference, String purpose, List<RequisitionItemRequest> items) { }
    public record RequisitionItemRequest(UUID catalogId, BigDecimal quantity) { }
    public record ProcurementRequest(String requestNo, String departmentReference, String reason, List<ProcurementItemRequest> items) { }
    public record ProcurementItemRequest(String materialReference, BigDecimal quantity, BigDecimal estimatedAmount, String supplier) { }
    public record DecisionRequest(String status) { }
    public record ApprovalRequest(String decision, String comment) { }
    public record AttachmentRequest(String kind, String reference) { }
    public record SpaceRequest(UUID parentId, String spaceCode, String name, String zoneCode, BigDecimal areaValue, String administratorReference, String description, String viewReference) { }
    public record EnvironmentRequest(String metricCode, BigDecimal measureValue, String unitCode, Instant measuredAt, String sourceReference) { }
    public record SafetyRequest(String checkCode, String resultCode, String note) { }
    public record CriticalValueRequest(String valueTypeCode, String gradeCode, String triggerReference, Instant dueAt) { }
    public record CriticalNotificationRequest(String departmentReference, String recipientReference, String methodCode, String message, String businessPath) { }
    public record FeedbackRequest(String content) { }
    public record DistributionRequest(String targetCode) { }
    public record DistributionStatusRequest(String status, String error) { }
    public record PrintRequest(String identityReference, String terminalReference, String printerReference, String resultCode, int copyCount) { }
    public record AddressRequest(String addressName, String recipientName, String phone, String addressText) { }
    public record PackageRequest(UUID caseId, UUID consultationId, String courierCompany, String trackingNo, String senderReference, String recipientReference, String addressText, List<PackageItemRequest> items) { }
    public record PackageItemRequest(UUID blockId, UUID slideId, String documentReference) { }
    public record LogisticsEventRequest(String statusCode, String note) { }
    public record MolecularProjectRequest(String projectCode, String projectName, String projectTypeCode) { }
    public record MolecularInstrumentRequest(String instrumentCode, String name, String adapterCode) { }
    public record MolecularReagentRequest(String kitCode, String manufacturer, String batchNo, LocalDate expiryDate) { }
    public record MolecularTestRequest(UUID caseId, UUID specimenId, UUID projectId, String detectionNo, UUID instrumentId, UUID reagentKitId, String rawDataReference, String structuredResult, String analysisResult) { }
    public record MolecularCompleteRequest(String structuredResult, String analysisResult) { }
    public record ArchiveRequest(UUID digitalSlideId, String storagePath, String storageTier, String filename, String formatCode, String pathologyNo, String slideNo, String patientReference, String organReference, String integrityDigest) { }
    public record StatusRequest(String status) { }
    public record RegionalShareRequest(UUID caseId, String receivingOrganization, String receivingDoctor, Instant expiresAt, boolean patientAuthorized, List<RegionalShareItemRequest> items) { }
    public record RegionalShareItemRequest(UUID reportId, UUID digitalSlideId, String attachmentReference) { }
    public record RegionalAccessRequest(String accessorReference, String actionCode) { }
    public record IncomeRequest(UUID caseId, String projectCode, BigDecimal amount, Instant occurredAt, String sourceReference) { }
    public record MigrationJobRequest(String sourceCode, String modeCode, String statusCode) { }
    public record MigrationRecordRequest(UUID jobId, String legacyType, String legacyKey, String localType, UUID localId, String recordStatus, String rawReference, Instant mappedAt) { }
    public record MigrationErrorRequest(UUID jobId, UUID recordId, String errorCode, String errorMessage, int retryCount) { }
}
