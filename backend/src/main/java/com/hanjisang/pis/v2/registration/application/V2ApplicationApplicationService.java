package com.hanjisang.pis.v2.registration.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.integration.device.LabelPrintService;
import com.hanjisang.pis.integration.patient.PatientInfoProviderPort;
import com.hanjisang.pis.integration.patient.PatientInfoProviderPort.PatientInfo;
import com.hanjisang.pis.integration.patient.PatientInfoProviderPort.PatientLookup;
import com.hanjisang.pis.integration.patient.PatientInfoProviderPort.PatientLookupFailure;
import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.registration.domain.Specimen;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2ApplicationRepository;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2ApplicationRepository.ApplicationItemInput;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2ApplicationRepository.ApplicationItemRow;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2ApplicationRepository.ApplicationQueueRow;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2ApplicationRepository.ApplicationRow;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2ApplicationRepository.BarcodeContextRow;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2ApplicationRepository.BarcodePrintRow;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2ApplicationRepository.DeliveryRow;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository.CaseSnapshotInput;

@Service
public class V2ApplicationApplicationService {

    private static final String APPLICATION_CREATE = "P14-PERM-002";
    private static final String APPLICATION_READ = "P14-PERM-048";
    private static final String APPLICATION_UPDATE = "P14-PERM-007";
    private static final String APPLICATION_CANCEL = "P14-PERM-006";
    private static final String APPLICATION_ACCEPT = "P14-PERM-003";
    private static final String CASE_CREATE = "P14-PERM-004";
    private static final String INCOMING_LABEL_PRINT = "P14-PERM-008";
    private static final String SPECIMEN_ACCEPT = "P14-PERM-009";
    private static final String SPECIMEN_REJECT = "P14-PERM-010";

    private final JdbcV2ApplicationRepository repository;
    private final JdbcV2RegistrationRepository registrationRepository;
    private final V2RegistrationApplicationService registrationService;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final LabelPrintService labelPrintService;
    private final PatientInfoProviderPort patientInfoProvider;

    public V2ApplicationApplicationService(JdbcV2ApplicationRepository repository,
            JdbcV2RegistrationRepository registrationRepository,
            V2RegistrationApplicationService registrationService, P15AuthorizationService authorization,
            JdbcAuditEventRepository audit, LabelPrintService labelPrintService,
            PatientInfoProviderPort patientInfoProvider) {
        this.repository = repository;
        this.registrationRepository = registrationRepository;
        this.registrationService = registrationService;
        this.authorization = authorization;
        this.audit = audit;
        this.labelPrintService = labelPrintService;
        this.patientInfoProvider = patientInfoProvider;
    }

    @Transactional
    public ApplicationResult create(CreateApplicationCommand command) {
        ActorContext actor = authorization.require(APPLICATION_CREATE);
        List<ValidationIssue> issues = validateCreate(command);
        rejectErrors(issues);
        UUID applicationId = UUID.randomUUID();
        String applicationNo = blank(command.applicationNo()) ? generatedApplicationNo(applicationId)
                : command.applicationNo().trim();
        Instant now = Instant.now();
        List<ApplicationItemInput> items = resolveItems(command.items());
        String patientSource = blank(command.patientInfoSourceCode())
                ? ("HIS".equals(command.sourceTypeCode()) ? "HIS" : "MANUAL")
                : command.patientInfoSourceCode().trim().toUpperCase();
        ApplicationRow row = new ApplicationRow(applicationId, applicationNo, command.sourceTypeCode().trim(),
                command.sourceSystemCode().trim(), command.patientReference().trim(), command.patientName(),
                command.patientSexCode(), command.patientBirthDate(), patientSource, command.patientIdentityNo(),
                command.visitCardNo(), command.contactPhone(), command.ageValue(), command.ageUnitCode(),
                command.visitReference(), command.visitTypeCode(), command.wardReference(), command.bedReference(),
                command.applicationDepartment(), command.applicantReference(),
                command.appliedAt() == null ? now : command.appliedAt(), command.clinicalDiagnosis(),
                command.medicalHistory(), command.operationFinding(), command.surgeryName(),
                command.examinationPurpose(), command.specimenDescription(), command.note(), "RECEIVED", 0);
        try {
            repository.insertApplication(row, items, actor.hospitalScope(), actor.actorId(), now);
        } catch (DuplicateKeyException exception) {
            var existing = repository.findBySourceIdentity(row.sourceSystemCode(), applicationNo,
                    actor.hospitalScope());
            if (existing.isPresent()) return application(existing.get().id(), actor, true);
            throw conflict("V2-APPLICATION-DUPLICATE", "申请号已存在，不能创建重复申请");
        }
        audit.append("PIS-V2-APPLICATION-CREATE", APPLICATION_CREATE, actor, "ALLOWED", "COMPLETED",
                applicationId, "V2-APPLICATION", UUID.randomUUID().toString(), "申请及申请项目已建立");
        return application(applicationId, actor, false);
    }

    @Transactional(readOnly = true)
    public ValidationResult validate(CreateApplicationCommand command) {
        authorization.require(APPLICATION_CREATE);
        List<ValidationIssue> issues = validateCreate(command);
        return new ValidationResult(issues.stream().noneMatch(item -> "ERROR".equals(item.severity())), issues);
    }

    @Transactional(readOnly = true)
    public List<ApplicationQueueResult> queue() {
        ActorContext actor = authorization.require(CASE_CREATE);
        return repository.findQueue(actor.hospitalScope()).stream().map(this::queueRow).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationResult get(UUID applicationId) {
        ActorContext actor = authorization.require(APPLICATION_READ);
        return application(applicationId, actor, false);
    }

    public PatientLookupResult lookupPatient(PatientLookupCommand command) {
        ActorContext actor = authorization.require(APPLICATION_CREATE);
        PatientLookup query = new PatientLookup(command.patientId(), command.visitId(), command.outpatientNo(),
                command.inpatientNo());
        if (query.empty()) throw badRequest("V2-PATIENT-LOOKUP-KEY-REQUIRED", "请至少输入一个患者或就诊标识");
        try {
            var result = patientInfoProvider.lookup(query);
            audit.append("PIS-V2-HIS-PATIENT-LOOKUP", APPLICATION_CREATE, actor, "ALLOWED",
                    result.isPresent() ? "COMPLETED" : "NOT_FOUND", null, "V2-INTEGRATION",
                    UUID.randomUUID().toString(), result.isPresent() ? "患者信息查询成功" : "患者信息未找到");
            return result.map(value -> PatientLookupResult.found(patientInfoProvider.adapterCode(), value))
                    .orElseGet(() -> PatientLookupResult.notFound(patientInfoProvider.adapterCode()));
        } catch (PatientLookupFailure exception) {
            audit.append("PIS-V2-HIS-PATIENT-LOOKUP", APPLICATION_CREATE, actor, "ALLOWED", "FAILED",
                    null, "V2-INTEGRATION", UUID.randomUUID().toString(), exception.errorCode());
            throw new P15BusinessException(exception.errorCode(), exception.getMessage(), 503);
        }
    }

    @Transactional
    public ApplicationResult update(UUID applicationId, UpdateApplicationCommand command) {
        ActorContext actor = authorization.require(APPLICATION_UPDATE);
        ApplicationRow current = find(applicationId, actor);
        if ("CANCELLED".equals(current.statusCode())) {
            throw conflict("V2-APPLICATION-CANCELLED", "已取消申请不能修改");
        }
        ApplicationRow updated = new ApplicationRow(current.id(), current.applicationNo(),
                value(command.sourceTypeCode(), current.sourceTypeCode()),
                value(command.sourceSystemCode(), current.sourceSystemCode()),
                value(command.patientReference(), current.patientReference()),
                value(command.patientName(), current.patientName()), value(command.patientSexCode(), current.patientSexCode()),
                command.patientBirthDate() == null ? current.patientBirthDate() : command.patientBirthDate(),
                value(command.patientInfoSourceCode(), current.patientInfoSourceCode()),
                value(command.patientIdentityNo(), current.patientIdentityNo()),
                value(command.visitCardNo(), current.visitCardNo()), value(command.contactPhone(), current.contactPhone()),
                command.ageValue() == null ? current.ageValue() : command.ageValue(),
                value(command.ageUnitCode(), current.ageUnitCode()), value(command.visitReference(), current.visitReference()),
                value(command.visitTypeCode(), current.visitTypeCode()), value(command.wardReference(), current.wardReference()),
                value(command.bedReference(), current.bedReference()),
                value(command.applicationDepartment(), current.applicationDepartment()),
                value(command.applicantReference(), current.applicantReference()),
                command.appliedAt() == null ? current.appliedAt() : command.appliedAt(),
                value(command.clinicalDiagnosis(), current.clinicalDiagnosis()),
                value(command.medicalHistory(), current.medicalHistory()),
                value(command.operationFinding(), current.operationFinding()), value(command.surgeryName(), current.surgeryName()),
                value(command.examinationPurpose(), current.examinationPurpose()),
                value(command.specimenDescription(), current.specimenDescription()), value(command.note(), current.note()),
                current.statusCode(), current.concurrencyVersion());
        rejectErrors(validateUpdate(updated, command.items()));
        Instant now = Instant.now();
        if (!repository.updateApplication(updated, current.concurrencyVersion(), actor.hospitalScope(),
                actor.actorId(), now)) {
            throw conflict("V2-APPLICATION-VERSION-CONFLICT", "申请已被其他用户修改，请刷新后重试");
        }
        if (command.items() != null) {
            repository.cancelPendingItems(applicationId, "申请项目修改", actor.actorId(), now);
            if (!command.items().isEmpty()) {
                repository.insertItems(applicationId, resolveItems(command.items()), actor.actorId(), now);
            }
            repository.updateApplicationStatus(applicationId, repository.hasLinkedCase(applicationId)
                    ? "PARTIALLY_REGISTERED" : "RECEIVED", actor.actorId(), now);
        }
        audit.append("PIS-V2-APPLICATION-UPDATE", APPLICATION_UPDATE, actor, "ALLOWED", "COMPLETED",
                applicationId, "V2-APPLICATION", UUID.randomUUID().toString(),
                repository.hasLinkedCase(applicationId) ? "已登记项目保持不变，仅更新申请侧资料和待登记项目" : "申请资料已修改");
        return application(applicationId, actor, false);
    }

    @Transactional
    public ApplicationResult cancel(UUID applicationId, CancelApplicationCommand command) {
        ActorContext actor = authorization.require(APPLICATION_CANCEL);
        require(command.reason(), "取消原因");
        ApplicationRow current = find(applicationId, actor);
        if ("CANCELLED".equals(current.statusCode())) return application(applicationId, actor, true);
        Instant now = Instant.now();
        repository.cancelPendingItems(applicationId, command.reason(), actor.actorId(), now);
        if (!repository.cancelApplicationAnyOpenState(applicationId, current.concurrencyVersion(), command.reason(),
                actor.hospitalScope(), actor.actorId(), now)) {
            throw conflict("V2-APPLICATION-VERSION-CONFLICT", "申请已被其他用户修改，请刷新后重试");
        }
        audit.append("PIS-V2-APPLICATION-CANCEL", APPLICATION_CANCEL, actor, "ALLOWED", "COMPLETED",
                applicationId, "V2-APPLICATION", UUID.randomUUID().toString(), command.reason());
        return application(applicationId, actor, false);
    }

    @Transactional
    public ApplicationResult cancelItem(UUID applicationId, UUID itemId, CancelApplicationCommand command) {
        ActorContext actor = authorization.require(APPLICATION_CANCEL);
        require(command.reason(), "取消原因");
        find(applicationId, actor);
        if (!repository.cancelItem(applicationId, itemId, command.reason(), actor.actorId(), Instant.now())) {
            throw conflict("V2-APPLICATION-ITEM-NOT-PENDING", "只有待登记申请项目可以取消");
        }
        audit.append("PIS-V2-APPLICATION-ITEM-CANCEL", APPLICATION_CANCEL, actor, "ALLOWED", "COMPLETED",
                itemId, "V2-APPLICATION-ITEM", UUID.randomUUID().toString(), command.reason());
        return application(applicationId, actor, false);
    }

    @Transactional
    public DeliveryResult verifyDelivery(DeliveryCommand command) {
        String outcome = normalizedOutcome(command.outcomeCode());
        ActorContext actor = authorization.require("REJECTED".equals(outcome) ? SPECIMEN_REJECT : SPECIMEN_ACCEPT);
        require(command.applicationId(), "applicationId");
        require(command.applicationItemId(), "applicationItemId");
        require(command.incomingSpecimenReference(), "送检标本标识");
        require(command.patientReference(), "患者标识");
        ApplicationRow application = find(command.applicationId(), actor);
        ApplicationItemRow item = repository.findItem(command.applicationItemId(), application.id())
                .orElseThrow(() -> notFound("V2-APPLICATION-ITEM-NOT-FOUND", "申请项目不存在"));
        if (!"PENDING".equals(item.statusCode())) {
            throw conflict("V2-APPLICATION-ITEM-NOT-PENDING", "该申请项目已处理，不能重复核对");
        }
        boolean patientMatch = Boolean.TRUE.equals(command.patientMatch())
                && application.patientReference().equals(command.patientReference());
        boolean allVerified = patientMatch && yes(command.applicationMatch()) && yes(command.quantityMatch())
                && yes(command.specimenMatch()) && yes(command.containerMatch()) && yes(command.fixationMatch());
        if ("ACCEPTED".equals(outcome) && !allVerified) {
            throw badRequest("V2-SPECIMEN-VERIFICATION-INCOMPLETE", "患者、申请、数量、标本、容器及固定核对全部通过后才能接收");
        }
        if (!"ACCEPTED".equals(outcome)) {
            require(command.reasonCode(), "拒收/补正原因");
            require(command.reasonText(), "拒收/补正说明");
        }
        if ("ACCEPTED".equals(outcome)) {
            var duplicate = repository.findAcceptedDeliveryByReference(command.incomingSpecimenReference(),
                    actor.hospitalScope());
            if (duplicate.isPresent()) {
                DeliveryRow existing = duplicate.get();
                return new DeliveryResult(application.id(), existing.applicationItemId(), existing.deliveryId(),
                        existing.verificationStatusCode(), existing.rejectionReason(), existing.deliveredAt(), true);
            }
        }
        Instant now = Instant.now();
        UUID deliveryId;
        try {
            deliveryId = repository.insertDelivery(application.id(), item.id(), command.incomingSpecimenReference(),
                    command.specimenLabelCode(), command.patientReference(), command.actualSpecimenDescription(),
                    outcome, command.reasonCode(), command.reasonText(), patientMatch,
                    yes(command.applicationMatch()), yes(command.quantityMatch()), yes(command.specimenMatch()),
                    yes(command.containerMatch()), yes(command.fixationMatch()), actor.hospitalScope(),
                    actor.actorId(), now);
        } catch (DuplicateKeyException exception) {
            DeliveryRow existing = repository.findAcceptedDelivery(item.id()).orElseThrow(() -> exception);
            return new DeliveryResult(application.id(), item.id(), existing.deliveryId(), "ACCEPTED", null,
                    existing.deliveredAt(), true);
        }
        if ("REJECTED".equals(outcome) && !repository.rejectItem(application.id(), item.id(), command.reasonCode(),
                command.reasonText(), actor.actorId(), now)) {
            throw conflict("V2-APPLICATION-ITEM-NOT-PENDING", "该申请项目已由其他用户处理");
        }
        audit.append("PIS-V2-APPLICATION-DELIVERY-VERIFY",
                "REJECTED".equals(outcome) ? SPECIMEN_REJECT : SPECIMEN_ACCEPT, actor, "ALLOWED", outcome,
                item.id(), "V2-APPLICATION-DELIVERY", UUID.randomUUID().toString(),
                "ACCEPTED".equals(outcome) ? "送检标本核对通过" : command.reasonCode() + "：" + command.reasonText());
        return new DeliveryResult(application.id(), item.id(), deliveryId, outcome, command.reasonText(), now, false);
    }

    @Transactional(readOnly = true)
    public BarcodeScanResult scanBarcode(String barcode) {
        ActorContext actor = authorization.require(SPECIMEN_ACCEPT);
        require(barcode, "条码");
        BarcodeContextRow row = repository.findBarcodeContext(barcode.trim(), actor.hospitalScope())
                .orElseThrow(() -> notFound("V2-APPLICATION-BARCODE-NOT-FOUND", "未找到该送检条码"));
        DeliveryRow delivery = row.delivered() ? repository.findAcceptedDelivery(row.applicationItemId()).orElse(null)
                : null;
        return new BarcodeScanResult(barcode.trim(), row.applicationId(), row.applicationNo(), row.patientReference(),
                row.patientName(), row.applicationItemId(), row.itemName(), row.specimenDescription(),
                row.itemStatusCode(), row.delivered(), delivery == null ? null : delivery.deliveredAt(),
                delivery == null ? null : delivery.deliveredByRef());
    }

    @Transactional
    public PrintResult printBarcodes(UUID applicationId, PrintBarcodeCommand command) {
        ActorContext actor = authorization.require(INCOMING_LABEL_PRINT);
        ApplicationRow application = find(applicationId, actor);
        int copies = command.copies() <= 0 ? 1 : command.copies();
        if (copies > 100) throw badRequest("V2-PRINT-COPIES-INVALID", "打印份数不能超过 100");
        Set<UUID> selected = command.applicationItemIds() == null ? Set.of()
                : new HashSet<>(command.applicationItemIds());
        List<ApplicationItemRow> items = repository.findItems(applicationId).stream()
                .filter(item -> selected.isEmpty() || selected.contains(item.id())).toList();
        if (items.isEmpty()) throw badRequest("V2-APPLICATION-ITEM-REQUIRED", "请选择至少一个送检标本");
        if (!selected.isEmpty() && items.size() != selected.size()) {
            throw notFound("V2-APPLICATION-ITEM-NOT-FOUND", "所选申请项目不存在或不属于当前申请");
        }
        int printed = 0;
        for (ApplicationItemRow item : items) {
            String barcode = application.applicationNo() + "-" + item.sequenceNo();
            int version = repository.nextBarcodePrintVersion(applicationId, item.id());
            String operation = version == 1 ? "PRINT" : "REPRINT";
            String rendered = "患者：" + display(application.patientName(), application.patientReference())
                    + "\n申请号：" + application.applicationNo() + "\n标本："
                    + display(item.specimenDescription(), item.itemName()) + "\n申请科室："
                    + display(application.applicationDepartment(), "未填写") + "\n条码：" + barcode;
            LabelPrintService.PrintResult result = print("APPLICATION_SPECIMEN", item.id(), barcode,
                    command.printerProfileCode(), rendered, actor.actorId());
            repository.insertBarcodePrint(applicationId, item.id(), barcode, version, operation, copies,
                    printer(command.printerProfileCode()), rendered, result.resultCode(), result.failureReason(),
                    actor.actorId(), Instant.now());
            if (result.succeeded()) printed++;
        }
        audit.append("PIS-V2-APPLICATION-BARCODE-PRINT", INCOMING_LABEL_PRINT, actor, "ALLOWED",
                printed == items.size() ? "COMPLETED" : "PARTIAL", applicationId, "V2-APPLICATION",
                UUID.randomUUID().toString(), "送检标本标签打印：" + printed + "/" + items.size());
        return new PrintResult(applicationId, printed, items.size(), printed == items.size());
    }

    @Transactional(readOnly = true)
    public List<BarcodePrintView> barcodePrintHistory(UUID applicationId) {
        ActorContext actor = authorization.require(APPLICATION_READ);
        find(applicationId, actor);
        return repository.findBarcodePrints(applicationId, actor.hospitalScope()).stream()
                .map(row -> new BarcodePrintView(row.printId(), row.applicationItemId(), row.barcodeValue(),
                        row.printVersion(), row.operationCode(), row.copies(), row.printerProfileCode(),
                        row.resultCode(), row.failureReason(), row.requestedAt(), row.requestedByRef())).toList();
    }

    @Transactional(readOnly = true)
    public String deliveryExport(UUID applicationId) {
        ActorContext actor = authorization.require(APPLICATION_READ);
        ApplicationRow application = find(applicationId, actor);
        StringBuilder csv = new StringBuilder("applicationNo,deliveryId,applicationItemId,specimenLabelCode,patientReference,actualSpecimenDescription,status,reason,deliveredBy,deliveredAt\n");
        for (DeliveryRow row : repository.findDeliveries(applicationId, actor.hospitalScope())) {
            csv.append(csv(application.applicationNo())).append(',').append(csv(row.deliveryId())).append(',')
                    .append(csv(row.applicationItemId())).append(',').append(csv(row.specimenLabelCode())).append(',')
                    .append(csv(row.patientReference())).append(',').append(csv(row.actualSpecimenDescription())).append(',')
                    .append(csv(row.verificationStatusCode())).append(',').append(csv(row.rejectionReason())).append(',')
                    .append(csv(row.deliveredByRef())).append(',').append(csv(row.deliveredAt())).append('\n');
        }
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public List<DeliverySearchView> searchDeliveries(String visitReference, Instant from, Instant to,
            String externalItemCode) {
        ActorContext actor = authorization.require(APPLICATION_READ);
        if (from != null && to != null && from.isAfter(to)) {
            throw badRequest("V2-DELIVERY-TIME-RANGE-INVALID", "送检开始时间不能晚于结束时间");
        }
        return repository.searchDeliveries(actor.hospitalScope(), visitReference, from, to, externalItemCode)
                .stream().map(row -> new DeliverySearchView(row.deliveryId(), row.applicationId(),
                        row.applicationItemId(), row.applicationNo(), row.visitReference(), row.patientReference(),
                        row.patientName(), row.externalItemCode(), row.itemName(), row.incomingSpecimenReference(),
                        row.specimenLabelCode(), row.statusCode(), row.reason(), row.deliveredBy(),
                        row.deliveredAt())).toList();
    }

    @Transactional(readOnly = true)
    public String deliveryExcel(String visitReference, Instant from, Instant to, String externalItemCode) {
        List<DeliverySearchView> rows = searchDeliveries(visitReference, from, to, externalItemCode);
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <?mso-application progid="Excel.Sheet"?>
                <Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
                  xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">
                  <Worksheet ss:Name="送检记录"><Table>
                    <Row><Cell><Data ss:Type="String">申请号</Data></Cell>
                    <Cell><Data ss:Type="String">门诊/住院号</Data></Cell>
                    <Cell><Data ss:Type="String">患者</Data></Cell>
                    <Cell><Data ss:Type="String">申请项目</Data></Cell>
                    <Cell><Data ss:Type="String">送检条码</Data></Cell>
                    <Cell><Data ss:Type="String">状态</Data></Cell>
                    <Cell><Data ss:Type="String">送检人</Data></Cell>
                    <Cell><Data ss:Type="String">送检时间</Data></Cell></Row>
                """);
        for (DeliverySearchView row : rows) {
            xml.append("<Row>")
                    .append(cell(row.applicationNo())).append(cell(row.visitReference()))
                    .append(cell(display(row.patientName(), row.patientReference())))
                    .append(cell(display(row.itemName(), row.externalItemCode())))
                    .append(cell(row.incomingSpecimenReference())).append(cell(row.statusCode()))
                    .append(cell(row.deliveredBy())).append(cell(row.deliveredAt()))
                    .append("</Row>");
        }
        return xml.append("</Table></Worksheet></Workbook>").toString();
    }

    @Transactional(readOnly = true)
    public String barcodePrintExport(UUID applicationId) {
        ActorContext actor = authorization.require(APPLICATION_READ);
        ApplicationRow application = find(applicationId, actor);
        StringBuilder csv = new StringBuilder("applicationNo,printId,applicationItemId,barcode,printVersion,operation,copies,printer,result,failureReason,requestedAt,requestedBy\n");
        for (BarcodePrintRow row : repository.findBarcodePrints(applicationId, actor.hospitalScope())) {
            csv.append(csv(application.applicationNo())).append(',').append(csv(row.printId())).append(',')
                    .append(csv(row.applicationItemId())).append(',').append(csv(row.barcodeValue())).append(',')
                    .append(row.printVersion()).append(',').append(csv(row.operationCode())).append(',')
                    .append(row.copies()).append(',').append(csv(row.printerProfileCode())).append(',')
                    .append(csv(row.resultCode())).append(',').append(csv(row.failureReason())).append(',')
                    .append(csv(row.requestedAt())).append(',').append(csv(row.requestedByRef())).append('\n');
        }
        return csv.toString();
    }

    @Transactional
    public RegistrationResult register(UUID applicationId, RegisterApplicationCommand command) {
        return registerItems(applicationId, null, command);
    }

    @Transactional
    public RegistrationResult registerItem(UUID applicationId, UUID applicationItemId,
            RegisterApplicationCommand command) {
        if (applicationItemId == null) throw badRequest("V2-APPLICATION-ITEM-REQUIRED", "请选择申请项目");
        return registerItems(applicationId, applicationItemId, command);
    }

    private RegistrationResult registerItems(UUID applicationId, UUID requestedItemId,
            RegisterApplicationCommand command) {
        ActorContext actor = authorization.require(CASE_CREATE);
        authorization.require(APPLICATION_ACCEPT);
        ApplicationRow application = find(applicationId, actor);
        if ("CANCELLED".equals(application.statusCode())) {
            throw conflict("V2-APPLICATION-CANCELLED", "已取消申请不能登记");
        }
        List<UUID> requestedIds = requestedItemId == null
                ? repository.findItems(applicationId).stream().filter(item -> "PENDING".equals(item.statusCode()))
                        .map(ApplicationItemRow::id).toList()
                : List.of(requestedItemId);
        if (requestedIds.isEmpty()) return new RegistrationResult(applicationId, 0, true, List.of());
        List<ApplicationItemRow> items = requestedIds.stream().map(id -> repository.findItemForUpdate(id, applicationId)
                .orElseThrow(() -> notFound("V2-APPLICATION-ITEM-NOT-FOUND", "申请项目不存在"))).toList();
        for (ApplicationItemRow item : items) {
            if (!"PENDING".equals(item.statusCode())) {
                throw conflict("V2-APPLICATION-ITEM-NOT-PENDING", "该申请项目已完成登记或已终止");
            }
            if (item.businessTypeId() == null || blank(item.businessTypeCode())) {
                throw conflict("V2-APPLICATION-ITEM-UNMAPPED", "该申请项目尚未配置病理业务类型");
            }
            if (!repository.hasAcceptedDelivery(item.id())) {
                throw conflict("V2-SPECIMEN-NOT-VERIFIED", "请先完成送检标本核对并确认接收");
            }
        }
        List<CaseResultView> cases = new ArrayList<>();
        for (ApplicationItemRow item : items) {
            V2RegistrationApplicationService.CaseResult created = registrationService.createCase(
                    new V2RegistrationApplicationService.CreateCaseCommand(application.sourceSystemCode(),
                            application.applicationNo(), item.externalItemCode(), application.patientReference(),
                            application.visitReference(), "application-register-" + application.id() + "-" + item.id()));
            registrationRepository.enrichCaseSnapshot(created.caseId(), new CaseSnapshotInput(application.patientName(),
                    application.patientSexCode(), application.patientBirthDate(), application.ageValue(),
                    application.ageUnitCode(), application.visitTypeCode(), application.applicationDepartment(),
                    application.applicantReference(), application.clinicalDiagnosis(), application.medicalHistory(),
                    application.surgeryName(), application.operationFinding()));
            DeliveryRow accepted = repository.findAcceptedDelivery(item.id()).orElseThrow();
            Instant now = Instant.now();
            UUID specimenId = UUID.randomUUID();
            String specimenNo = registrationRepository.allocateNumber(actor.hospitalScope(), item.businessTypeCode(),
                    "SPECIMEN", now);
            String specimenCode = String.valueOf(item.sequenceNo());
            String site = display(accepted.actualSpecimenDescription(),
                    display(item.specimenDescription(), display(application.specimenDescription(), "送检标本")));
            String labelCode = created.caseNo() + "-" + specimenCode;
            Specimen specimen = Specimen.register(specimenId, created.caseId(), specimenNo, specimenCode,
                    display(item.specimenKindCode(), "TISSUE"), "APPLICATION", application.applicationNo() + "-" + item.sequenceNo(),
                    site, "SUBMITTED", null, null, null, site, null, null, accepted.deliveredAt(), labelCode);
            registrationRepository.insertSpecimen(specimen, actor.hospitalScope(), actor.actorId(), now);
            if (!repository.linkCase(application.id(), item.id(), created.caseId(), actor.actorId(), now)
                    || !repository.markItemRegistered(item.id())) {
                throw conflict("V2-APPLICATION-ITEM-ALREADY-REGISTERED", "该申请项目已完成登记");
            }
            cases.add(new CaseResultView(created.caseId(), created.caseNo(), item.id(), item.externalItemCode(),
                    item.businessTypeId(), specimenId, created.duplicate()));
        }
        repository.updateApplicationStatus(application.id(), repository.hasPendingItems(application.id())
                ? "PARTIALLY_REGISTERED" : "REGISTERED", actor.actorId(), Instant.now());
        audit.append("PIS-V2-APPLICATION-REGISTER", CASE_CREATE, actor, "ALLOWED", "COMPLETED",
                application.id(), "V2-APPLICATION", UUID.randomUUID().toString(), "核对通过并按申请项目建立独立病例");
        return new RegistrationResult(application.id(), cases.size(), false, cases);
    }

    private List<ValidationIssue> validateCreate(CreateApplicationCommand command) {
        List<ValidationIssue> issues = new ArrayList<>();
        issueIf(blank(command.sourceTypeCode()) || !Set.of("HIS", "CLINICAL", "MANUAL")
                .contains(command.sourceTypeCode()), issues, "sourceTypeCode", "申请来源类型无效", "ERROR", null);
        issueIf(blank(command.sourceSystemCode()), issues, "sourceSystemCode", "申请来源系统不能为空", "ERROR", null);
        issueIf(blank(command.patientReference()), issues, "patientReference", "患者标识不能为空", "ERROR", null);
        issueIf(blank(command.patientName()), issues, "patientName", "患者姓名不能为空", "ERROR", null);
        issueIf(blank(command.visitReference()) && !("MANUAL".equals(command.sourceTypeCode()) && !blank(command.note())),
                issues, "visitReference", "请填写就诊标识；手工特殊场景可在备注说明", "ERROR", null);
        issueIf(blank(command.applicationDepartment()), issues, "applicationDepartment", "申请科室不能为空", "ERROR", null);
        issueIf(blank(command.applicantReference()), issues, "applicantReference", "申请医生不能为空", "ERROR", null);
        issueIf(command.items() == null || command.items().isEmpty(), issues, "items", "至少需要一个申请项目", "ERROR", null);
        if (!blank(command.visitTypeCode()) && !Set.of("OUTPATIENT", "INPATIENT", "EMERGENCY",
                "PHYSICAL_EXAM", "OTHER").contains(command.visitTypeCode())) {
            issues.add(new ValidationIssue("visitTypeCode", "就诊类型无效", "ERROR", null));
        }
        if ((command.ageValue() == null) != blank(command.ageUnitCode())) {
            issues.add(new ValidationIssue("ageValue", "年龄数值和单位必须同时填写", "ERROR", null));
        }
        if (command.ageValue() != null && command.ageValue() < 0) {
            issues.add(new ValidationIssue("ageValue", "年龄不能为负数", "ERROR", null));
        }
        if (command.items() != null) validateItems(command.items(), issues);
        return issues;
    }

    private List<ValidationIssue> validateUpdate(ApplicationRow application, List<ApplicationItemCommand> items) {
        CreateApplicationCommand merged = new CreateApplicationCommand(application.applicationNo(),
                application.sourceTypeCode(), application.sourceSystemCode(), application.patientReference(),
                application.patientName(), application.patientSexCode(), application.patientBirthDate(),
                application.patientInfoSourceCode(), application.patientIdentityNo(), application.visitCardNo(),
                application.contactPhone(), application.ageValue(), application.ageUnitCode(), application.visitReference(),
                application.visitTypeCode(), application.wardReference(), application.bedReference(),
                application.applicationDepartment(), application.applicantReference(), application.appliedAt(),
                application.clinicalDiagnosis(), application.medicalHistory(), application.operationFinding(),
                application.surgeryName(), application.examinationPurpose(), application.specimenDescription(),
                application.note(), items == null ? List.of(new ApplicationItemCommand("UNCHANGED", null, null, null, 1)) : items);
        List<ValidationIssue> issues = validateCreate(merged);
        if (items == null) issues.removeIf(issue -> "items[0].externalItemCode".equals(issue.field()));
        return issues;
    }

    private void validateItems(List<ApplicationItemCommand> items, List<ValidationIssue> issues) {
        Set<Integer> sequences = new HashSet<>();
        for (int index = 0; index < items.size(); index++) {
            ApplicationItemCommand item = items.get(index);
            String prefix = "items[" + index + "]";
            if (blank(item.externalItemCode())) {
                issues.add(new ValidationIssue(prefix + ".externalItemCode", "申请项目编码不能为空", "ERROR", item.itemId()));
            } else if (registrationRepository.findRouting(item.externalItemCode()).isEmpty()) {
                issues.add(new ValidationIssue(prefix + ".externalItemCode", "该申请项目尚未配置病理业务类型", "ERROR", item.itemId()));
            }
            if (item.sequenceNo() <= 0 || !sequences.add(item.sequenceNo())) {
                issues.add(new ValidationIssue(prefix + ".sequenceNo", "申请项目顺序必须为不重复的正整数", "ERROR", item.itemId()));
            }
            if (blank(item.specimenDescription())) {
                issues.add(new ValidationIssue(prefix + ".specimenDescription", "建议填写标本名称或部位，并在登记核对时确认", "WARNING", item.itemId()));
            }
        }
    }

    private List<ApplicationItemInput> resolveItems(List<ApplicationItemCommand> commands) {
        return commands.stream().map(item -> {
            var routing = registrationRepository.findRouting(item.externalItemCode())
                    .orElseThrow(() -> conflict("V2-APPLICATION-ITEM-UNMAPPED",
                            "该申请项目尚未配置病理业务类型：" + item.externalItemCode()));
            return new ApplicationItemInput(UUID.randomUUID(), item.externalItemCode(), item.itemName(),
                    routing.mapping().id(), routing.businessType().id(),
                    blank(item.specimenKindCode()) ? routing.mapping().defaultSpecimenKindCode() : item.specimenKindCode(),
                    item.specimenDescription(), item.sequenceNo());
        }).toList();
    }

    private ApplicationResult application(UUID applicationId, ActorContext actor, boolean duplicate) {
        ApplicationRow row = find(applicationId, actor);
        return new ApplicationResult(row.id(), row.applicationNo(), row.sourceTypeCode(), row.sourceSystemCode(),
                row.patientReference(), row.patientName(), row.patientSexCode(), row.patientBirthDate(),
                row.patientInfoSourceCode(), row.patientIdentityNo(), row.visitCardNo(), row.contactPhone(),
                row.ageValue(), row.ageUnitCode(), row.visitReference(), row.visitTypeCode(), row.wardReference(),
                row.bedReference(), row.applicationDepartment(), row.applicantReference(), row.appliedAt(),
                row.clinicalDiagnosis(), row.medicalHistory(), row.operationFinding(), row.surgeryName(),
                row.examinationPurpose(), row.specimenDescription(), row.note(), row.statusCode(),
                row.concurrencyVersion(), duplicate, repository.findItems(row.id()).stream()
                        .map(item -> new ApplicationItemView(item.id(), item.externalItemCode(), item.itemName(),
                                item.specimenKindCode(), item.specimenDescription(), item.sequenceNo(), item.statusCode(),
                                item.businessTypeCode(), item.caseId(), item.pathologyNo())).toList());
    }

    private ApplicationRow find(UUID id, ActorContext actor) {
        return repository.findApplication(id, actor.hospitalScope())
                .orElseThrow(() -> notFound("V2-APPLICATION-NOT-FOUND", "申请不存在或不在当前数据范围"));
    }

    private ApplicationQueueResult queueRow(ApplicationQueueRow row) {
        return new ApplicationQueueResult(row.applicationId(), row.applicationNo(), row.sourceTypeCode(),
                row.sourceSystemCode(), row.patientReference(), row.patientName(), row.visitReference(),
                row.patientSexCode(), row.patientBirthDate(), row.applicationDepartment(), row.applicantReference(),
                row.appliedAt(), row.statusCode(), row.itemId(), row.externalItemCode(), row.itemName(),
                row.specimenKindCode(), row.specimenDescription(), row.itemStatusCode(), row.businessTypeCode());
    }

    private LabelPrintService.PrintResult print(String entityKind, UUID entityId, String businessCode,
            String printerProfileCode, String rendered, String actorId) {
        try {
            return labelPrintService.print(new LabelPrintService.PrintRequest(entityKind, entityId, businessCode,
                    printer(printerProfileCode), rendered, actorId));
        } catch (IllegalArgumentException exception) {
            return new LabelPrintService.PrintResult("FAILED", "INVALID_PRINTER_PROFILE", exception.getMessage());
        }
    }

    private static String printer(String value) {
        return blank(value) ? "MOCK://SYNTH-PRINTER" : value.trim();
    }

    private static String normalizedOutcome(String value) {
        String result = blank(value) ? "ACCEPTED" : value.trim().toUpperCase();
        if (!Set.of("ACCEPTED", "REJECTED", "SUPPLEMENT_REQUIRED").contains(result)) {
            throw badRequest("V2-SPECIMEN-VERIFICATION-OUTCOME-INVALID", "标本核对结果无效");
        }
        return result;
    }

    private static boolean yes(Boolean value) { return Boolean.TRUE.equals(value); }

    private static String generatedApplicationNo(UUID id) {
        return "APP-" + id.toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private static String value(String candidate, String fallback) {
        return candidate == null ? fallback : candidate.trim();
    }

    private static String display(String candidate, String fallback) {
        return blank(candidate) ? fallback : candidate.trim();
    }

    private static String csv(Object value) {
        if (value == null) return "";
        return "\"" + value.toString().replace("\"", "\"\"") + "\"";
    }

    private static String cell(Object value) {
        return "<Cell><Data ss:Type=\"String\">" + xml(value) + "</Data></Cell>";
    }

    private static String xml(Object value) {
        if (value == null) return "";
        return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static void issueIf(boolean condition, List<ValidationIssue> issues, String field, String message,
            String severity, UUID itemId) {
        if (condition) issues.add(new ValidationIssue(field, message, severity, itemId));
    }

    private static void rejectErrors(List<ValidationIssue> issues) {
        List<ValidationIssue> errors = issues.stream().filter(item -> "ERROR".equals(item.severity())).toList();
        if (!errors.isEmpty()) {
            throw badRequest("V2-APPLICATION-VALIDATION-FAILED",
                    errors.stream().map(ValidationIssue::message).distinct().reduce((a, b) -> a + "；" + b).orElse("申请校验失败"));
        }
    }

    private static void require(Object value, String field) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            throw badRequest("V2-INVALID-REQUEST", field + "不能为空");
        }
    }

    private static P15BusinessException badRequest(String code, String message) {
        return new P15BusinessException(code, message, 400);
    }

    private static P15BusinessException notFound(String code, String message) {
        return new P15BusinessException(code, message, 404);
    }

    private static P15BusinessException conflict(String code, String message) {
        return new P15BusinessException(code, message, 409);
    }

    public record CreateApplicationCommand(String applicationNo, String sourceTypeCode, String sourceSystemCode,
            String patientReference, String patientName, String patientSexCode, LocalDate patientBirthDate,
            String patientInfoSourceCode, String patientIdentityNo, String visitCardNo, String contactPhone,
            Integer ageValue, String ageUnitCode, String visitReference, String visitTypeCode,
            String wardReference, String bedReference, String applicationDepartment, String applicantReference,
            Instant appliedAt, String clinicalDiagnosis, String medicalHistory, String operationFinding,
            String surgeryName, String examinationPurpose, String specimenDescription, String note,
            List<ApplicationItemCommand> items) { }

    public record UpdateApplicationCommand(String sourceTypeCode, String sourceSystemCode, String patientReference,
            String patientName, String patientSexCode, LocalDate patientBirthDate, String patientInfoSourceCode,
            String patientIdentityNo, String visitCardNo, String contactPhone, Integer ageValue, String ageUnitCode,
            String visitReference, String visitTypeCode, String wardReference, String bedReference,
            String applicationDepartment, String applicantReference, Instant appliedAt, String clinicalDiagnosis,
            String medicalHistory, String operationFinding, String surgeryName, String examinationPurpose,
            String specimenDescription, String note, List<ApplicationItemCommand> items) { }

    public record ApplicationItemCommand(UUID itemId, String externalItemCode, String itemName,
            String specimenKindCode, String specimenDescription, int sequenceNo) {
        public ApplicationItemCommand(String externalItemCode, String itemName, String specimenKindCode,
                String specimenDescription, int sequenceNo) {
            this(null, externalItemCode, itemName, specimenKindCode, specimenDescription, sequenceNo);
        }
    }

    public record PatientLookupCommand(String patientId, String visitId, String outpatientNo, String inpatientNo) { }
    public record CancelApplicationCommand(String reason) { }
    public record DeliveryCommand(UUID applicationId, UUID applicationItemId, String incomingSpecimenReference,
            String specimenLabelCode, String patientReference, String actualSpecimenDescription, String outcomeCode,
            String reasonCode, String reasonText, Boolean patientMatch, Boolean applicationMatch,
            Boolean quantityMatch, Boolean specimenMatch, Boolean containerMatch, Boolean fixationMatch) { }
    public record PrintBarcodeCommand(List<UUID> applicationItemIds, int copies, String printerProfileCode) { }
    public record RegisterApplicationCommand(String receiptKindCode, String printerProfileCode) { }

    public record ValidationIssue(String field, String message, String severity, UUID applicationItemId) { }
    public record ValidationResult(boolean valid, List<ValidationIssue> issues) { }

    public record PatientLookupResult(boolean found, String adapterCode, String message, String patientReference,
            String patientName, String patientSexCode, LocalDate birthDate, Integer ageValue, String ageUnitCode,
            String identityNo, String visitReference, String visitTypeCode, String visitCardNo, String contactPhone,
            String departmentReference, String wardReference, String bedReference, String clinicalDiagnosis,
            String medicalHistory) {
        static PatientLookupResult found(String adapter, PatientInfo value) {
            return new PatientLookupResult(true, adapter, "已获取患者与就诊信息", value.patientReference(),
                    value.patientName(), value.patientSexCode(), value.birthDate(), value.ageValue(),
                    value.ageUnitCode(), value.identityNo(), value.visitReference(), value.visitTypeCode(),
                    value.visitCardNo(), value.contactPhone(), value.departmentReference(), value.wardReference(),
                    value.bedReference(), value.clinicalDiagnosis(), value.medicalHistory());
        }

        static PatientLookupResult notFound(String adapter) {
            return new PatientLookupResult(false, adapter, "未查询到患者信息，可人工补录", null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null, null);
        }
    }

    public record ApplicationResult(UUID applicationId, String applicationNo, String sourceTypeCode,
            String sourceSystemCode, String patientReference, String patientName, String patientSexCode,
            LocalDate patientBirthDate, String patientInfoSourceCode, String patientIdentityNo, String visitCardNo,
            String contactPhone, Integer ageValue, String ageUnitCode, String visitReference, String visitTypeCode,
            String wardReference, String bedReference, String applicationDepartment, String applicantReference,
            Instant appliedAt, String clinicalDiagnosis, String medicalHistory, String operationFinding,
            String surgeryName, String examinationPurpose, String specimenDescription, String note,
            String statusCode, long concurrencyVersion, boolean duplicate, List<ApplicationItemView> items) { }

    public record ApplicationItemView(UUID itemId, String externalItemCode, String itemName, String specimenKindCode,
            String specimenDescription, int sequenceNo, String statusCode, String businessTypeCode,
            UUID caseId, String pathologyNo) { }
    public record ApplicationQueueResult(UUID applicationId, String applicationNo, String sourceTypeCode,
            String sourceSystemCode, String patientReference, String patientName, String visitReference,
            String patientSexCode, LocalDate patientBirthDate, String applicationDepartment,
            String applicantReference, Instant appliedAt, String statusCode, UUID applicationItemId,
            String externalItemCode, String itemName, String specimenKindCode, String specimenDescription,
            String itemStatusCode, String businessTypeCode) { }
    public record DeliveryResult(UUID applicationId, UUID applicationItemId, UUID deliveryId,
            String statusCode, String reason, Instant deliveredAt, boolean duplicate) { }
    public record DeliverySearchView(UUID deliveryId, UUID applicationId, UUID applicationItemId,
            String applicationNo, String visitReference, String patientReference, String patientName,
            String externalItemCode, String itemName, String incomingSpecimenReference, String specimenLabelCode,
            String statusCode, String reason, String deliveredBy, Instant deliveredAt) { }
    public record BarcodeScanResult(String barcode, UUID applicationId, String applicationNo,
            String patientReference, String patientName, UUID applicationItemId, String itemName,
            String specimenDescription, String itemStatusCode, boolean delivered, Instant deliveredAt,
            String deliveredBy) { }
    public record BarcodePrintView(UUID printId, UUID applicationItemId, String barcode, int printVersion,
            String operationCode, int copies, String printerProfileCode, String resultCode, String failureReason,
            Instant requestedAt, String requestedBy) { }
    public record PrintResult(UUID applicationId, int successCount, int requestedCount, boolean allSucceeded) { }
    public record RegistrationResult(UUID applicationId, int createdCaseCount, boolean duplicate,
            List<CaseResultView> cases) { }
    public record CaseResultView(UUID caseId, String caseNo, UUID applicationItemId, String externalItemCode,
            UUID businessTypeId, UUID specimenId, boolean duplicate) { }
}
