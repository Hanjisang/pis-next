package com.hanjisang.pis.v2.registration.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.integration.device.LabelPrintService;
import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2ApplicationRepository;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2ApplicationRepository.ApplicationItemInput;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2ApplicationRepository.ApplicationItemRow;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2ApplicationRepository.ApplicationQueueRow;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2ApplicationRepository.ApplicationRow;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2ApplicationRepository.BarcodePrintRow;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2ApplicationRepository.DeliveryRow;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository;

@Service
public class V2ApplicationApplicationService {

    private static final String REGISTRATION_PERMISSION = "P14-PERM-004";
    private final JdbcV2ApplicationRepository repository;
    private final JdbcV2RegistrationRepository registrationRepository;
    private final V2RegistrationApplicationService registrationService;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final LabelPrintService labelPrintService;

    public V2ApplicationApplicationService(JdbcV2ApplicationRepository repository,
            JdbcV2RegistrationRepository registrationRepository,
            V2RegistrationApplicationService registrationService, P15AuthorizationService authorization,
            JdbcAuditEventRepository audit, LabelPrintService labelPrintService) {
        this.repository = repository;
        this.registrationRepository = registrationRepository;
        this.registrationService = registrationService;
        this.authorization = authorization;
        this.audit = audit;
        this.labelPrintService = labelPrintService;
    }

    @Transactional
    public ApplicationResult create(CreateApplicationCommand command) {
        ActorContext actor = authorization.require(REGISTRATION_PERMISSION);
        validate(command.sourceTypeCode(), "sourceTypeCode");
        validate(command.sourceSystemCode(), "sourceSystemCode");
        validate(command.patientReference(), "patientReference");
        if (command.items() == null || command.items().isEmpty()) {
            throw reject("V2-APPLICATION-ITEM-REQUIRED", "At least one application item is required");
        }
        UUID applicationId = UUID.randomUUID();
        String applicationNo = command.applicationNo() == null || command.applicationNo().isBlank()
                ? generatedApplicationNo(applicationId) : command.applicationNo().trim();
        Instant now = Instant.now();
        List<ApplicationItemInput> items = resolveItems(command.items());
        ApplicationRow row = new ApplicationRow(applicationId, applicationNo, command.sourceTypeCode(),
                command.sourceSystemCode(), command.patientReference(), command.patientName(), command.patientSexCode(),
                command.patientBirthDate(), command.visitReference(), command.visitTypeCode(),
                command.applicationDepartment(), command.applicantReference(), command.appliedAt() == null ? now
                        : command.appliedAt(), command.clinicalDiagnosis(), command.medicalHistory(),
                command.operationFinding(), command.examinationPurpose(), command.specimenDescription(), command.note(),
                "RECEIVED", 0);
        try {
            repository.insertApplication(row, items, actor.hospitalScope(), actor.actorId(), now);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw reject("V2-APPLICATION-DUPLICATE", "Application number already exists");
        }
        audit.append("PIS-V2-APPLICATION-CREATE", REGISTRATION_PERMISSION, actor, "ALLOWED", "COMPLETED",
                applicationId, "V2-APPLICATION", UUID.randomUUID().toString(), "Application created");
        return application(applicationId, actor);
    }

    @Transactional(readOnly = true)
    public List<ApplicationQueueResult> queue() {
        ActorContext actor = authorization.require(REGISTRATION_PERMISSION);
        return repository.findQueue(actor.hospitalScope()).stream().map(this::queueRow).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationResult get(UUID applicationId) {
        ActorContext actor = authorization.require(REGISTRATION_PERMISSION);
        return application(applicationId, actor);
    }

    @Transactional
    public ApplicationResult update(UUID applicationId, UpdateApplicationCommand command) {
        ActorContext actor = authorization.require(REGISTRATION_PERMISSION);
        ApplicationRow current = find(applicationId, actor);
        if (!"RECEIVED".equals(current.statusCode()) || repository.hasLinkedCase(applicationId)) {
            throw reject("V2-APPLICATION-IMMUTABLE-AFTER-REGISTRATION",
                    "Application changes are only allowed before a Case is created");
        }
        ApplicationRow updated = new ApplicationRow(current.id(), current.applicationNo(),
                value(command.sourceTypeCode(), current.sourceTypeCode()), value(command.sourceSystemCode(), current.sourceSystemCode()),
                value(command.patientReference(), current.patientReference()), value(command.patientName(), current.patientName()),
                value(command.patientSexCode(), current.patientSexCode()), command.patientBirthDate() == null
                        ? current.patientBirthDate() : command.patientBirthDate(),
                value(command.visitReference(), current.visitReference()), value(command.visitTypeCode(), current.visitTypeCode()),
                value(command.applicationDepartment(), current.applicationDepartment()), value(command.applicantReference(), current.applicantReference()),
                command.appliedAt() == null ? current.appliedAt() : command.appliedAt(),
                value(command.clinicalDiagnosis(), current.clinicalDiagnosis()), value(command.medicalHistory(), current.medicalHistory()),
                value(command.operationFinding(), current.operationFinding()), value(command.examinationPurpose(), current.examinationPurpose()),
                value(command.specimenDescription(), current.specimenDescription()), value(command.note(), current.note()),
                current.statusCode(), current.concurrencyVersion());
        if (!repository.updateApplication(updated, current.concurrencyVersion(), actor.hospitalScope(), actor.actorId(), Instant.now())) {
            throw reject("V2-APPLICATION-VERSION-CONFLICT", "Application was changed by another user");
        }
        if (command.items() != null && !command.items().isEmpty()) {
            repository.rejectPendingItems(applicationId);
            repository.insertItems(applicationId, resolveItems(command.items()), actor.actorId(), Instant.now());
        }
        audit.append("PIS-V2-APPLICATION-UPDATE", REGISTRATION_PERMISSION, actor, "ALLOWED", "COMPLETED",
                applicationId, "V2-APPLICATION", UUID.randomUUID().toString(), "Application corrected before registration");
        return application(applicationId, actor);
    }

    @Transactional
    public ApplicationResult cancel(UUID applicationId, CancelApplicationCommand command) {
        ActorContext actor = authorization.require(REGISTRATION_PERMISSION);
        require(command.reason(), "reason");
        ApplicationRow current = find(applicationId, actor);
        if (repository.hasLinkedCase(applicationId)) {
            throw reject("V2-APPLICATION-CASE-EXISTS", "An Application with a Case cannot cancel the Case through Application");
        }
        if (!repository.cancelApplication(applicationId, current.concurrencyVersion(), command.reason(),
                actor.hospitalScope(), actor.actorId(), Instant.now())) {
            throw reject("V2-APPLICATION-VERSION-CONFLICT", "Application was changed by another user");
        }
        audit.append("PIS-V2-APPLICATION-CANCEL", REGISTRATION_PERMISSION, actor, "ALLOWED", "COMPLETED",
                applicationId, "V2-APPLICATION", UUID.randomUUID().toString(), command.reason());
        return application(applicationId, actor);
    }

    @Transactional
    public DeliveryResult verifyDelivery(DeliveryCommand command) {
        ActorContext actor = authorization.require(REGISTRATION_PERMISSION);
        require(command.applicationId(), "applicationId");
        require(command.patientReference(), "patientReference");
        ApplicationRow application = find(command.applicationId(), actor);
        ApplicationItemRow item = command.applicationItemId() == null ? null
                : repository.findItem(command.applicationItemId(), application.id())
                        .orElseThrow(() -> reject("V2-APPLICATION-ITEM-NOT-FOUND", "Application item not found"));
        boolean patientMatches = application.patientReference().equals(command.patientReference());
        boolean labelPresent = command.specimenLabelCode() != null && !command.specimenLabelCode().isBlank();
        String status = patientMatches && labelPresent ? "ACCEPTED"
                : Boolean.TRUE.equals(command.supplementRequired()) ? "SUPPLEMENT_REQUIRED" : "REJECTED";
        String reason = "ACCEPTED".equals(status) ? null : (command.rejectionReason() == null || command.rejectionReason().isBlank()
                ? "Patient or specimen verification failed" : command.rejectionReason());
        Instant now = Instant.now();
        repository.insertDelivery(application.id(), item == null ? null : item.id(), command.specimenLabelCode(),
                command.patientReference(), command.actualSpecimenDescription(), status, reason,
                actor.hospitalScope(), actor.actorId(), now);
        audit.append("PIS-V2-APPLICATION-DELIVERY-VERIFY", REGISTRATION_PERMISSION, actor, "ALLOWED", status,
                application.id(), "V2-APPLICATION-DELIVERY", UUID.randomUUID().toString(), reason);
        return new DeliveryResult(application.id(), item == null ? null : item.id(), status, reason, now);
    }

    @Transactional
    public PrintResult printBarcodes(UUID applicationId, PrintBarcodeCommand command) {
        ActorContext actor = authorization.require(REGISTRATION_PERMISSION);
        ApplicationRow application = find(applicationId, actor);
        List<ApplicationItemRow> items = command.applicationItemId() == null ? repository.findItems(applicationId)
                : List.of(repository.findItem(command.applicationItemId(), applicationId)
                        .orElseThrow(() -> reject("V2-APPLICATION-ITEM-NOT-FOUND", "Application item not found")));
        if (items.isEmpty()) throw reject("V2-APPLICATION-ITEM-REQUIRED", "No application item is available for printing");
        int printed = 0;
        Instant now = Instant.now();
        for (ApplicationItemRow item : items) {
            String barcode = application.applicationNo() + "-" + item.sequenceNo();
            int version = repository.nextBarcodePrintVersion(applicationId, item.id());
            LabelPrintService.PrintResult result;
            try {
                result = labelPrintService.print(new LabelPrintService.PrintRequest("APPLICATION_SPECIMEN",
                        item.id(), barcode, command.printerProfileCode(), barcode, actor.actorId()));
            } catch (IllegalArgumentException exception) {
                result = new LabelPrintService.PrintResult("FAILED", "INVALID_PRINTER_PROFILE", exception.getMessage());
            }
            repository.insertBarcodePrint(applicationId, item.id(), barcode, version, command.printerProfileCode(),
                    result.resultCode(), result.failureReason(), actor.actorId(), now);
            if (result.succeeded()) printed++;
        }
        audit.append("PIS-V2-APPLICATION-BARCODE-PRINT", REGISTRATION_PERMISSION, actor, "ALLOWED",
                printed == items.size() ? "COMPLETED" : "PARTIAL", applicationId, "V2-APPLICATION",
                UUID.randomUUID().toString(), "Barcode print requested");
        return new PrintResult(applicationId, printed, items.size(), printed == items.size());
    }

    @Transactional(readOnly = true)
    public String deliveryExport(UUID applicationId) {
        ActorContext actor = authorization.require(REGISTRATION_PERMISSION);
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
    public String barcodePrintExport(UUID applicationId) {
        ActorContext actor = authorization.require(REGISTRATION_PERMISSION);
        ApplicationRow application = find(applicationId, actor);
        StringBuilder csv = new StringBuilder("applicationNo,printId,applicationItemId,barcode,printVersion,printer,result,failureReason,requestedAt,requestedBy\n");
        for (BarcodePrintRow row : repository.findBarcodePrints(applicationId, actor.hospitalScope())) {
            csv.append(csv(application.applicationNo())).append(',').append(csv(row.printId())).append(',')
                    .append(csv(row.applicationItemId())).append(',').append(csv(row.barcodeValue())).append(',')
                    .append(row.printVersion()).append(',').append(csv(row.printerProfileCode())).append(',')
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
        if (applicationItemId == null) throw reject("V2-APPLICATION-ITEM-REQUIRED", "Application item is required");
        return registerItems(applicationId, applicationItemId, command);
    }

    private RegistrationResult registerItems(UUID applicationId, UUID requestedItemId,
            RegisterApplicationCommand command) {
        ActorContext actor = authorization.require(REGISTRATION_PERMISSION);
        ApplicationRow application = find(applicationId, actor);
        if ("CANCELLED".equals(application.statusCode())) throw reject("V2-APPLICATION-CANCELLED", "Cancelled Application cannot register");
        List<ApplicationItemRow> items = repository.findItems(applicationId).stream()
                .filter(item -> "PENDING".equals(item.statusCode()))
                .filter(item -> requestedItemId == null || requestedItemId.equals(item.id())).toList();
        if (requestedItemId != null && items.isEmpty()) {
            throw reject("V2-APPLICATION-ITEM-NOT-PENDING", "Application item is not pending or does not exist");
        }
        if (items.isEmpty()) return new RegistrationResult(applicationId, 0, true, List.of());
        List<CaseResultView> cases = new java.util.ArrayList<>();
        for (ApplicationItemRow item : items) {
            if (item.businessTypeId() == null) throw reject("V2-APPLICATION-ITEM-UNMAPPED", "Application item has no BusinessType mapping");
            V2RegistrationApplicationService.CaseResult created = registrationService.createCase(
                    new V2RegistrationApplicationService.CreateCaseCommand(application.sourceSystemCode(),
                            application.applicationNo(), item.externalItemCode(), application.patientReference(),
                            application.visitReference(), "application-register-" + application.id() + "-" + item.id()));
            UUID caseId = created.caseId();
            repository.linkCase(application.id(), item.id(), caseId, actor.actorId(), Instant.now());
            repository.markItemRegistered(item.id());
            repository.insertReceiptPrint(application.id(), caseId, command.receiptKindCode(),
                    command.printerProfileCode(), "SUCCESS", null, actor.actorId(), Instant.now());
            cases.add(new CaseResultView(caseId, created.caseNo(), item.id(), item.externalItemCode(),
                    item.businessTypeId(), created.duplicate()));
        }
        repository.updateApplicationStatus(application.id(), repository.hasPendingItems(application.id())
                ? "PARTIALLY_REGISTERED" : "REGISTERED", actor.actorId(), Instant.now());
        audit.append("PIS-V2-APPLICATION-REGISTER", REGISTRATION_PERMISSION, actor, "ALLOWED", "COMPLETED",
                application.id(), "V2-APPLICATION", UUID.randomUUID().toString(), "Application accepted and Cases created");
        return new RegistrationResult(application.id(), cases.size(), false, cases);
    }

    private List<ApplicationItemInput> resolveItems(List<ApplicationItemCommand> commands) {
        return commands.stream().map((item) -> {
            var routing = registrationRepository.findRouting(item.externalItemCode())
                    .orElseThrow(() -> reject("V2-APPLICATION-ITEM-UNMAPPED",
                            "No active ApplicationItemMapping for " + item.externalItemCode()));
            return new ApplicationItemInput(UUID.randomUUID(), item.externalItemCode(), item.itemName(),
                    routing.mapping().id(), routing.businessType().id(),
                    item.specimenKindCode() == null ? routing.mapping().defaultSpecimenKindCode() : item.specimenKindCode(),
                    item.specimenDescription(), item.sequenceNo());
        }).toList();
    }

    private ApplicationResult application(UUID applicationId, ActorContext actor) {
        ApplicationRow row = find(applicationId, actor);
        return new ApplicationResult(row.id(), row.applicationNo(), row.sourceTypeCode(), row.sourceSystemCode(),
                row.patientReference(), row.patientName(), row.patientSexCode(), row.patientBirthDate(),
                row.visitReference(), row.visitTypeCode(), row.applicationDepartment(), row.applicantReference(),
                row.appliedAt(), row.clinicalDiagnosis(), row.medicalHistory(), row.operationFinding(),
                row.examinationPurpose(), row.specimenDescription(), row.note(), row.statusCode(),
                row.concurrencyVersion(), repository.findItems(row.id()).stream().map(item -> new ApplicationItemView(
                        item.id(), item.externalItemCode(), item.itemName(), item.specimenKindCode(),
                        item.specimenDescription(), item.sequenceNo(), item.statusCode())).toList());
    }

    private ApplicationRow find(UUID id, ActorContext actor) {
        return repository.findApplication(id, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-APPLICATION-NOT-FOUND", "Application not found"));
    }

    private ApplicationQueueResult queueRow(ApplicationQueueRow row) {
        return new ApplicationQueueResult(row.applicationId(), row.applicationNo(), row.sourceTypeCode(),
                row.sourceSystemCode(), row.patientReference(), row.patientName(), row.visitReference(),
                row.patientSexCode(), row.patientBirthDate(),
                row.applicationDepartment(), row.applicantReference(), row.appliedAt(), row.statusCode(), row.itemId(),
                row.externalItemCode(), row.itemName(), row.specimenKindCode(), row.specimenDescription(),
                row.itemStatusCode(), row.businessTypeCode());
    }

    private static String generatedApplicationNo(UUID id) {
        return "APP-" + id.toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private static String value(String candidate, String fallback) {
        return candidate == null || candidate.isBlank() ? fallback : candidate;
    }

    private static String csv(Object value) {
        if (value == null) return "";
        return "\"" + value.toString().replace("\"", "\"\"") + "\"";
    }

    private static void validate(String value, String field) { require(value, field); }
    private static void require(Object value, String field) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            throw reject("V2-INVALID-REQUEST", field + " is required");
        }
    }

    private static P15BusinessException reject(String code, String message) {
        return new P15BusinessException(code, message);
    }

    @SuppressWarnings("unused")
    private static String digest(Object... values) {
        try {
            String payload = java.util.Arrays.stream(values).map(value -> value == null ? "<null>" : value.toString())
                    .reduce((left, right) -> left + "|" + right).orElse("");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record CreateApplicationCommand(String applicationNo, String sourceTypeCode, String sourceSystemCode,
            String patientReference, String patientName, String patientSexCode, LocalDate patientBirthDate,
            String visitReference, String visitTypeCode, String applicationDepartment, String applicantReference,
            Instant appliedAt, String clinicalDiagnosis, String medicalHistory, String operationFinding,
            String examinationPurpose, String specimenDescription, String note, List<ApplicationItemCommand> items) { }

    public record UpdateApplicationCommand(String sourceTypeCode, String sourceSystemCode, String patientReference,
            String patientName, String patientSexCode, LocalDate patientBirthDate, String visitReference,
            String visitTypeCode, String applicationDepartment, String applicantReference, Instant appliedAt,
            String clinicalDiagnosis, String medicalHistory, String operationFinding, String examinationPurpose,
            String specimenDescription, String note, List<ApplicationItemCommand> items) { }

    public record ApplicationItemCommand(String externalItemCode, String itemName, String specimenKindCode,
            String specimenDescription, int sequenceNo) { }

    public record CancelApplicationCommand(String reason) { }
    public record DeliveryCommand(UUID applicationId, UUID applicationItemId, String specimenLabelCode,
            String patientReference, String actualSpecimenDescription, Boolean supplementRequired,
            String rejectionReason) { }
    public record PrintBarcodeCommand(UUID applicationItemId, String printerProfileCode) { }
    public record RegisterApplicationCommand(String receiptKindCode, String printerProfileCode) {
        public RegisterApplicationCommand {
            if (receiptKindCode == null || receiptKindCode.isBlank()) receiptKindCode = "REGISTRATION";
            if (printerProfileCode == null || printerProfileCode.isBlank()) printerProfileCode = "MOCK://SYNTH-PRINTER";
        }
    }

    public record ApplicationResult(UUID applicationId, String applicationNo, String sourceTypeCode,
            String sourceSystemCode, String patientReference, String patientName, String patientSexCode,
            LocalDate patientBirthDate, String visitReference, String visitTypeCode, String applicationDepartment,
            String applicantReference, Instant appliedAt, String clinicalDiagnosis, String medicalHistory,
            String operationFinding, String examinationPurpose, String specimenDescription, String note,
            String statusCode, long concurrencyVersion, List<ApplicationItemView> items) { }

    public record ApplicationItemView(UUID itemId, String externalItemCode, String itemName, String specimenKindCode,
            String specimenDescription, int sequenceNo, String statusCode) { }
    public record ApplicationQueueResult(UUID applicationId, String applicationNo, String sourceTypeCode,
            String sourceSystemCode, String patientReference, String patientName, String visitReference,
            String patientSexCode, LocalDate patientBirthDate,
            String applicationDepartment, String applicantReference, Instant appliedAt, String statusCode,
            UUID applicationItemId, String externalItemCode, String itemName, String specimenKindCode,
            String specimenDescription, String itemStatusCode, String businessTypeCode) { }
    public record DeliveryResult(UUID applicationId, UUID applicationItemId, String statusCode, String reason,
            Instant deliveredAt) { }
    public record PrintResult(UUID applicationId, int successCount, int requestedCount, boolean allSucceeded) { }
    public record RegistrationResult(UUID applicationId, int createdCaseCount, boolean duplicate,
            List<CaseResultView> cases) { }
    public record CaseResultView(UUID caseId, String caseNo, UUID applicationItemId, String externalItemCode,
            UUID businessTypeId, boolean duplicate) { }
}
