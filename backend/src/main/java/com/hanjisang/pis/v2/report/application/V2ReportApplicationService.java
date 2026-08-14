package com.hanjisang.pis.v2.report.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hanjisang.pis.integration.OutboxPort;
import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.JdbcAuditEventRepository.AuditChange;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.diagnosis.domain.Diagnosis;
import com.hanjisang.pis.v2.diagnosis.domain.ResponsibilityRole;
import com.hanjisang.pis.v2.diagnosis.domain.ResponsibilityUnit;
import com.hanjisang.pis.v2.diagnosis.infrastructure.JdbcV2DiagnosisRepository;
import com.hanjisang.pis.v2.frozen.application.V2FrozenApplicationService;
import com.hanjisang.pis.v2.material.infrastructure.JdbcV2MaterialRepository;
import com.hanjisang.pis.v2.registration.domain.Case;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository;
import com.hanjisang.pis.v2.report.domain.Report;
import com.hanjisang.pis.v2.report.domain.ReportNature;
import com.hanjisang.pis.v2.report.domain.ReportStatus;
import com.hanjisang.pis.v2.report.domain.ReportTemplateVersion;
import com.hanjisang.pis.v2.report.infrastructure.JdbcV2ReportRepository;
import com.hanjisang.pis.v2.report.infrastructure.JdbcV2ReportRepository.IdempotencyResult;
import com.hanjisang.pis.v2.technical.application.V2TechnicalOrderApplicationService;
import com.hanjisang.pis.v2.technical.infrastructure.JdbcV2TechnicalOrderRepository;
import com.hanjisang.pis.v2.technical.infrastructure.JdbcV2TechnicalOrderRepository.OrderSnapshot;

@Service
public class V2ReportApplicationService {

    public static final String REPORT_SIGN_OUT = "P14-PERM-036";
    public static final String REPORT_QUERY = "P14-PERM-055";
    public static final String REPORT_TEMPLATE_MANAGE = "P14-PERM-042";

    private final JdbcV2ReportRepository repository;
    private final JdbcV2DiagnosisRepository diagnosisRepository;
    private final JdbcV2RegistrationRepository registrationRepository;
    private final JdbcV2MaterialRepository materialRepository;
    private final JdbcV2TechnicalOrderRepository technicalRepository;
    private final V2TechnicalOrderApplicationService technicalOrderService;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;
    private final V2ReportPdfRenderer pdfRenderer;
    private final V2FrozenApplicationService frozenService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public V2ReportApplicationService(JdbcV2ReportRepository repository,
            JdbcV2DiagnosisRepository diagnosisRepository, JdbcV2RegistrationRepository registrationRepository,
            JdbcV2MaterialRepository materialRepository, JdbcV2TechnicalOrderRepository technicalRepository,
            V2TechnicalOrderApplicationService technicalOrderService, P15AuthorizationService authorization,
            JdbcAuditEventRepository audit, OutboxPort outbox,
            V2ReportPdfRenderer pdfRenderer, V2FrozenApplicationService frozenService) {
        this.repository = repository;
        this.diagnosisRepository = diagnosisRepository;
        this.registrationRepository = registrationRepository;
        this.materialRepository = materialRepository;
        this.technicalRepository = technicalRepository;
        this.technicalOrderService = technicalOrderService;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.pdfRenderer = pdfRenderer;
        this.frozenService = frozenService;
    }

    @Transactional(readOnly = true)
    public PreviewResult preview(UUID diagnosisId, UUID templateVersionId) {
        ActorContext actor = authorization.require(REPORT_QUERY);
        Diagnosis diagnosis = diagnosisRepository.findDiagnosis(diagnosisId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-DIAGNOSIS-NOT-FOUND", "诊断不存在或不在当前数据范围"));
        Case pathologyCase = registrationRepository.findCase(diagnosis.caseId(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-CASE-NOT-FOUND", "病例不存在或不在当前数据范围"));
        List<ResponsibilityUnit> responsibilities = diagnosisRepository.findResponsibilities(diagnosisId,
                actor.hospitalScope());
        List<OrderSnapshot> technicalOrders = technicalRepository.findOrderSnapshotsByDiagnosis(diagnosisId,
                actor.hospitalScope());
        ReportTemplateVersion template = resolveTemplate(pathologyCase, templateVersionId, actor);
        RenderedReport rendered = render(pathologyCase, diagnosis, responsibilities, technicalOrders, template, null,
                ReportNature.ORIGINAL, actor, false);
        List<String> reasons = validationReasons(pathologyCase, diagnosis, responsibilities, technicalOrders, template,
                actor.actorId(), actor.hospitalScope());
        return new PreviewResult(reasons.isEmpty(), reasons, template.id(), template.versionNo(), rendered.content(),
                rendered.contentHash(), rendered.pdfContentHash(), actionSummary(pathologyCase, diagnosis,
                        responsibilities, technicalOrders, actor, reasons));
    }

    @Transactional
    public ReportResult signOut(UUID diagnosisId, SignOutCommand command) {
        ActorContext actor = authorization.require(REPORT_SIGN_OUT);
        requireKey(command.idempotencyKey());
        String operation = "PIS-V2-I05-REPORT-SIGN-OUT";
        String digest = digest(diagnosisId, command.templateVersionId(), command.idempotencyKey());
        ReportResult replay = replay(operation, command.idempotencyKey(), digest, actor);
        if (replay != null) return replay;
        lockDiagnosis(diagnosisId, actor);
        replay = replay(operation, command.idempotencyKey(), digest, actor);
        if (replay != null) return replay;
        Diagnosis diagnosis = diagnosisRepository.findDiagnosis(diagnosisId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-DIAGNOSIS-NOT-FOUND", "诊断不存在或不在当前数据范围"));
        Case pathologyCase = activeCase(diagnosis.caseId(), actor);
        List<ResponsibilityUnit> responsibilities = diagnosisRepository.findResponsibilities(diagnosisId,
                actor.hospitalScope());
        List<OrderSnapshot> technicalOrders = technicalRepository.findOrderSnapshotsByDiagnosis(diagnosisId,
                actor.hospitalScope());
        ReportTemplateVersion template = resolveTemplate(pathologyCase, command.templateVersionId(), actor);
        List<String> reasons = validationReasons(pathologyCase, diagnosis, responsibilities, technicalOrders, template,
                actor.actorId(), actor.hospitalScope());
        if (repository.findEffectiveOriginal(diagnosisId, actor.hospitalScope()).isPresent()) {
            reasons = append(reasons, "REPORT_ORIGINAL_ALREADY_EFFECTIVE");
        }
        if (!reasons.isEmpty()) throw reject("V2-REPORT-SIGN-OUT-BLOCKED", String.join(",", reasons));
        ResponsibilityUnit auditResponsibility = lastAudit(responsibilities);
        if (auditResponsibility.completedAt() == null) {
            auditResponsibility.complete(auditResponsibility.version(), Instant.now());
            if (!diagnosisRepository.completeResponsibility(auditResponsibility, actor.hospitalScope(),
                    auditResponsibility.version() - 1)) {
                throw conflict("签发时完成审查责任发生并发冲突");
            }
            responsibilities = diagnosisRepository.findResponsibilities(diagnosisId, actor.hospitalScope());
        }
        return createReport(pathologyCase, diagnosis, responsibilities, technicalOrders, template, null,
                ReportNature.ORIGINAL, actor, operation, command.idempotencyKey(), digest);
    }

    @Transactional
    public ReportResult withdraw(UUID reportId, WithdrawCommand command) {
        ActorContext actor = authorization.require(REPORT_SIGN_OUT);
        requireText(command.reason(), "撤回原因不能为空");
        requireKey(command.idempotencyKey());
        String operation = "PIS-V2-I05-REPORT-WITHDRAW";
        String digest = digest(reportId, command.reason());
        ReportResult replay = replay(operation, command.idempotencyKey(), digest, actor);
        if (replay != null) return replay;
        if (!repository.lockReport(reportId, actor.hospitalScope())) {
            throw reject("V2-REPORT-NOT-FOUND", "报告不存在或不在当前数据范围");
        }
        Report report = repository.findReport(reportId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-REPORT-NOT-FOUND", "报告不存在或不在当前数据范围"));
        if (report.status() != ReportStatus.EFFECTIVE) {
            throw reject("V2-REPORT-NOT-EFFECTIVE", "只有生效报告可以撤回");
        }
        Instant now = Instant.now();
        if (!repository.withdraw(reportId, actor.hospitalScope(), actor.actorId(), command.reason(), now)) {
            throw conflict("报告撤回发生并发冲突");
        }
        if (!diagnosisRepository.reopenLastAuditResponsibility(report.diagnosisId(), actor.hospitalScope())) {
            throw conflict("撤回后未找到可重新打开的最后审查责任节点");
        }
        Report updated = repository.findReport(reportId, actor.hospitalScope()).orElseThrow();
        repository.insertIdempotency(operation, command.idempotencyKey(), digest, reportId, now, actor.actorId());
        audit.appendWithChanges("PIS-V2-I05-REPORT-WITHDRAW", REPORT_SIGN_OUT, actor, "COMPLETED", reportId,
                "V2-REPORT", UUID.randomUUID().toString(), command.reason(),
                List.of(new AuditChange("reportStatus", "报告状态", ReportStatus.EFFECTIVE.name(),
                        ReportStatus.WITHDRAWN.name()),
                        new AuditChange("withdrawalReason", "撤回原因", null, command.reason())));
        publish("V2-I05-REPORT-WITHDRAWN", reportId, updated.version(), actor, digest);
        return reportResult(updated, false);
    }

    @Transactional
    public ReportResult supplement(UUID diagnosisId, SupplementalCommand command) {
        ActorContext actor = authorization.require(REPORT_SIGN_OUT);
        requireText(command.content(), "补充报告内容不能为空");
        requireKey(command.idempotencyKey());
        String operation = "PIS-V2-I05-REPORT-SUPPLEMENT";
        String digest = digest(diagnosisId, command.priorReportId(), command.templateVersionId(), command.content());
        ReportResult replay = replay(operation, command.idempotencyKey(), digest, actor);
        if (replay != null) return replay;
        lockDiagnosis(diagnosisId, actor);
        Diagnosis diagnosis = diagnosisRepository.findDiagnosis(diagnosisId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-DIAGNOSIS-NOT-FOUND", "诊断不存在或不在当前数据范围"));
        Case pathologyCase = activeCase(diagnosis.caseId(), actor);
        List<ResponsibilityUnit> responsibilities = diagnosisRepository.findResponsibilities(diagnosisId,
                actor.hospitalScope());
        List<OrderSnapshot> technicalOrders = technicalRepository.findOrderSnapshotsByDiagnosis(diagnosisId,
                actor.hospitalScope());
        Report prior = choosePriorReport(diagnosisId, command.priorReportId(), actor);
        ReportTemplateVersion template = resolveTemplate(pathologyCase, command.templateVersionId(), actor);
        ResponsibilityUnit auditResponsibility = lastAudit(responsibilities);
        if (auditResponsibility == null || !actor.actorId().equals(auditResponsibility.doctorId())) {
            throw reject("V2-REPORT-AUDIT-DOCTOR-REQUIRED", "补充报告必须由最后审查责任医生处理");
        }
        List<String> reasons = validationReasons(pathologyCase, diagnosis, responsibilities, technicalOrders, template,
                actor.actorId(), actor.hospitalScope());
        if (!reasons.isEmpty()) throw reject("V2-REPORT-SUPPLEMENT-BLOCKED", String.join(",", reasons));
        return createReport(pathologyCase, diagnosis, responsibilities, technicalOrders, template, command.content(),
                ReportNature.SUPPLEMENTAL, actor, operation, command.idempotencyKey(), digest, prior.id());
    }

    @Transactional(readOnly = true)
    public ReportResult get(UUID reportId) {
        ActorContext actor = authorization.require(REPORT_QUERY);
        return reportResult(repository.findReport(reportId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-REPORT-NOT-FOUND", "报告不存在或不在当前数据范围")), false);
    }

    @Transactional(readOnly = true)
    public List<ReportResult> history(UUID caseId) {
        ActorContext actor = authorization.require(REPORT_QUERY);
        return repository.findReportsByCase(caseId, actor.hospitalScope()).stream().map(report -> reportResult(report, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReportResult> effective(UUID caseId) {
        ActorContext actor = authorization.require(REPORT_QUERY);
        return repository.findReportsByCase(caseId, actor.hospitalScope()).stream()
                .filter(report -> report.status() == ReportStatus.EFFECTIVE).map(report -> reportResult(report, false))
                .toList();
    }

    public boolean hasEffectiveOriginal(UUID diagnosisId, String organizationReference) {
        return repository.findEffectiveOriginal(diagnosisId, organizationReference).isPresent();
    }

    @Transactional(readOnly = true)
    public PdfResult pdf(UUID reportId) {
        ActorContext actor = authorization.require(REPORT_QUERY);
        Report report = repository.findReport(reportId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-REPORT-NOT-FOUND", "报告不存在或不在当前数据范围"));
        return new PdfResult(report.id(), report.reportNo(), report.pdfFileReference(), report.pdfContentHash(),
                "AES_256_PERMISSION", repository.pdf(reportId, actor.hospitalScope()));
    }

    @Transactional
    public PdfResult encryptedPdf(UUID reportId, EncryptedPdfCommand command) {
        ActorContext actor = authorization.require(REPORT_QUERY);
        requireText(command.accessPassword(), "加密PDF访问密码不能为空");
        requireText(command.reason(), "加密PDF下载原因不能为空");
        if (command.accessPassword().length() < 8 || command.accessPassword().length() > 64) {
            throw reject("V2-REPORT-PDF-PASSWORD-LENGTH", "加密PDF访问密码长度必须为8至64个字符");
        }
        if (command.reason().length() > 500) {
            throw reject("V2-REPORT-PDF-REASON-LENGTH", "加密PDF下载原因不能超过500个字符");
        }
        Report report = repository.findReport(reportId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-REPORT-NOT-FOUND", "报告不存在或不在当前数据范围"));
        if (report.status() != ReportStatus.EFFECTIVE) {
            throw reject("V2-REPORT-PDF-NOT-EFFECTIVE", "只有生效报告可以生成对外加密副本");
        }
        byte[] content = pdfRenderer.encryptForDelivery(repository.pdf(reportId, actor.hospitalScope()),
                command.accessPassword());
        String reference = report.pdfFileReference().replace(".pdf", "-encrypted.pdf");
        String contentHash = sha256(content);
        audit.append("PIS-V2-REPORT-PDF-ENCRYPTED-DOWNLOAD", REPORT_QUERY, actor, "ALLOWED", "COMPLETED",
                report.id(), "V2-REPORT", UUID.randomUUID().toString(),
                "reportNo=" + report.reportNo() + "; reason=" + command.reason().trim());
        return new PdfResult(report.id(), report.reportNo(), reference, contentHash, "AES_256_PASSWORD", content);
    }

    @Transactional(readOnly = true)
    public WorkspaceReport workspace(UUID caseId, Diagnosis diagnosis, List<ResponsibilityUnit> responsibilities,
            List<OrderSnapshot> technicalOrders, String organizationReference, String actorId) {
        if (diagnosis == null) {
            return new WorkspaceReport(List.of(), new ReportActions(false, false, false, false),
                    List.of("DIAGNOSIS_NOT_CREATED"));
        }
        Case pathologyCase = registrationRepository.findCase(caseId, organizationReference).orElse(null);
        if (pathologyCase == null) return new WorkspaceReport(List.of(), new ReportActions(false, false, false, false),
                List.of("CASE_NOT_FOUND"));
        ReportTemplateVersion template = repository.findPublishedTemplateForBusinessType(pathologyCase.businessTypeId(),
                organizationReference).orElse(null);
        List<String> reasons = validationReasons(pathologyCase, diagnosis, responsibilities, technicalOrders, template,
                actorId, organizationReference);
        // A Frozen Case can contain multiple independently signed round diagnoses.
        // Report actions and history for a diagnosis must therefore be scoped to
        // that diagnosis; case-wide history remains available through history().
        List<Report> reports = repository.findReportsByDiagnosis(diagnosis.id(), organizationReference);
        boolean effectiveOriginal = reports.stream().anyMatch(item -> item.nature() == ReportNature.ORIGINAL
                && item.status() == ReportStatus.EFFECTIVE);
        boolean canWithdraw = reports.stream().anyMatch(item -> item.status() == ReportStatus.EFFECTIVE);
        boolean auditActor = lastAudit(responsibilities) != null
                && actorId.equals(lastAudit(responsibilities).doctorId());
        boolean canSignOut = reasons.isEmpty() && auditActor && !effectiveOriginal;
        boolean canSupplement = auditActor && effectiveOriginal && technicalReasons(technicalOrders).isEmpty();
        return new WorkspaceReport(reports.stream().map(report -> reportView(report)).toList(),
                new ReportActions(true, canSignOut, canWithdraw, canSupplement), reasons);
    }

    @Transactional
    public TemplateResult createTemplate(CreateTemplateCommand command) {
        ActorContext actor = authorization.require(REPORT_TEMPLATE_MANAGE);
        requireText(command.code(), "报告模板编码不能为空");
        requireText(command.name(), "报告模板名称不能为空");
        var template = new com.hanjisang.pis.v2.report.domain.ReportTemplate(UUID.randomUUID(), actor.hospitalScope(),
                command.businessTypeId(), command.code(), command.name(), true, 1, Instant.now(), actor.actorId(),
                Instant.now(), actor.actorId());
        repository.insertTemplate(template, Instant.now(), actor.actorId());
        audit.append("PIS-V2-I05-REPORT-TEMPLATE-CREATE", REPORT_TEMPLATE_MANAGE, actor, "ALLOWED", "COMPLETED",
                template.id(), "V2-REPORT-TEMPLATE", UUID.randomUUID().toString(), template.code());
        return new TemplateResult(template.id(), template.code(), template.name(), false);
    }

    @Transactional
    public TemplateVersionResult createTemplateVersion(UUID templateId, CreateTemplateVersionCommand command) {
        ActorContext actor = authorization.require(REPORT_TEMPLATE_MANAGE);
        requireText(command.definition(), "报告模板定义不能为空");
        if (repository.findTemplate(templateId, actor.hospitalScope()).isEmpty()) {
            throw reject("V2-REPORT-TEMPLATE-NOT-FOUND", "报告模板不存在");
        }
        int versionNo = repository.nextTemplateVersion(templateId);
        ReportTemplateVersion version = new ReportTemplateVersion(UUID.randomUUID(), templateId, versionNo,
                command.definition(), "DRAFT", null, null, Instant.now(), actor.actorId(), 0);
        repository.insertTemplateVersion(version);
        return templateVersionResult(version, false);
    }

    @Transactional
    public TemplateVersionResult publishTemplateVersion(UUID versionId, String idempotencyKey) {
        ActorContext actor = authorization.require(REPORT_TEMPLATE_MANAGE);
        requireKey(idempotencyKey);
        ReportTemplateVersion version = repository.findTemplateVersionForUpdate(versionId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-REPORT-TEMPLATE-VERSION-NOT-FOUND", "报告模板版本不存在"));
        if (version.published()) return templateVersionResult(version, true);
        if (!repository.publishTemplateVersion(versionId, actor.hospitalScope(), Instant.now(), actor.actorId())) {
            throw conflict("报告模板版本发布发生并发冲突");
        }
        ReportTemplateVersion published = repository.findTemplateVersion(versionId, actor.hospitalScope()).orElseThrow();
        return templateVersionResult(published, false);
    }

    private ReportResult createReport(Case pathologyCase, Diagnosis diagnosis, List<ResponsibilityUnit> responsibilities,
            List<OrderSnapshot> technicalOrders, ReportTemplateVersion template, String supplementalContent,
            ReportNature nature, ActorContext actor, String operation, String idempotencyKey, String digest) {
        return createReport(pathologyCase, diagnosis, responsibilities, technicalOrders, template, supplementalContent,
                nature, actor, operation, idempotencyKey, digest, null);
    }

    private ReportResult createReport(Case pathologyCase, Diagnosis diagnosis, List<ResponsibilityUnit> responsibilities,
            List<OrderSnapshot> technicalOrders, ReportTemplateVersion template, String supplementalContent,
            ReportNature nature, ActorContext actor, String operation, String idempotencyKey, String digest,
            UUID priorReportId) {
        RenderedReport rendered = render(pathologyCase, diagnosis, responsibilities, technicalOrders, template,
                supplementalContent, nature, actor, true);
        Instant now = Instant.now();
        UUID reportId = UUID.randomUUID();
        String reportNo = (nature == ReportNature.ORIGINAL ? "R" : "S")
                + String.format("%03d", repository.nextReportSerial(pathologyCase.id(), nature, actor.hospitalScope()));
        String fileReference = "pis-v2/reports/" + reportId + ".pdf";
        Report report = new Report(reportId, reportNo, actor.hospitalScope(), pathologyCase.id(), diagnosis.id(),
                template.id(), nature, priorReportId, ReportStatus.EFFECTIVE, rendered.diagnosisSnapshot(),
                rendered.responsibilitySnapshot(), rendered.caseSnapshot(), rendered.materialSnapshot(),
                rendered.technicalSnapshot(), supplementalContent, rendered.content(), rendered.contentHash(),
                fileReference, rendered.pdfContentHash(), actor.actorId(), now, null, null, null, 0, now, actor.actorId());
        repository.insertReport(report);
        repository.insertPdf(reportId, fileReference, rendered.pdf(), rendered.pdfContentHash(), now, actor.actorId());
        if (nature == ReportNature.ORIGINAL && diagnosis.contextType() == com.hanjisang.pis.v2.diagnosis.domain.DiagnosisContextType.FROZEN_ROUND) {
            frozenService.markReportSigned(diagnosis.id(), actor.hospitalScope());
            frozenService.notifyReportSigned(diagnosis.id(), report.id(), report.reportNo(), actor.hospitalScope(), now);
        }
        repository.insertIdempotency(operation, idempotencyKey, digest, reportId, now, actor.actorId());
        audit.append(nature == ReportNature.ORIGINAL ? "PIS-V2-I05-REPORT-SIGN-OUT" : "PIS-V2-I05-REPORT-SUPPLEMENT",
                REPORT_SIGN_OUT, actor, "ALLOWED", "COMPLETED", reportId, "V2-REPORT", UUID.randomUUID().toString(),
                "reportNo=" + reportNo);
        publish(nature == ReportNature.ORIGINAL ? "V2-I05-REPORT-SIGNED-OUT" : "V2-I05-REPORT-SUPPLEMENTED",
                reportId, 0, actor, digest);
        return reportResult(report, false);
    }

    private RenderedReport render(Case pathologyCase, Diagnosis diagnosis, List<ResponsibilityUnit> responsibilities,
            List<OrderSnapshot> technicalOrders, ReportTemplateVersion template, String supplementalContent,
            ReportNature nature, ActorContext actor, boolean signOut) {
        String diagnosisSnapshot = json(Map.of("diagnosisId", diagnosis.id(), "structuredData",
                diagnosis.structuredData(), "microscopicDescription", value(diagnosis.microscopicDescription()),
                "diagnosisText", value(diagnosis.diagnosisText()), "comment", value(diagnosis.comment()),
                "version", diagnosis.version()));
        String responsibilitySnapshot = json(responsibilities.stream().map(item -> Map.of("id", item.id(), "role",
                item.role().name(), "doctorId", item.doctorId(), "sequence", item.sequence(), "completedAt",
                value(item.completedAt()), "endedAt", value(item.endedAt()), "version", item.version())).toList());
        String caseSnapshot = json(Map.of("caseId", pathologyCase.id(), "pathologyNo", pathologyCase.caseNo(),
                "businessTypeCode", pathologyCase.businessTypeCode(), "patientReference", pathologyCase.patientReference(),
                "visitReference", value(pathologyCase.visitReference()), "applicationItemCode",
                pathologyCase.applicationItemCode(), "sourceSystemCode", pathologyCase.sourceSystemCode(),
                "externalApplicationId", pathologyCase.externalApplicationId()));
        var materialRows = materialRepository.findMaterialTree(pathologyCase.id(), actor.hospitalScope());
        if (diagnosis.contextType() == com.hanjisang.pis.v2.diagnosis.domain.DiagnosisContextType.FROZEN_ROUND) {
            var roundSpecimenIds = materialRepository.findFrozenRoundSpecimenIds(diagnosis.contextId(),
                    pathologyCase.id(), actor.hospitalScope());
            materialRows = materialRows.stream().filter(row -> roundSpecimenIds.contains(row.specimenId())).toList();
        }
        String materialSnapshot = json(materialRows.stream().map(this::materialRowSnapshot).toList());
        String technicalSnapshot = json(technicalOrders.stream().map(item -> Map.of("orderId", item.order().id(),
                "orderNo", item.order().orderNo(), "status", item.derivedStatus().name(), "blocking", item.blocking(),
                "items", item.items().stream().map(detail -> Map.of("projectCode", detail.item().project().code(),
                        "status", detail.status().name(), "completedCount", detail.completedCount(), "expectedCount",
                        detail.expectedCount(), "result", value(detail.result() == null ? null : detail.result().data())))
                        .toList())).toList());
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("templateVersionId", template.id());
        model.put("templateVersionNo", template.versionNo());
        model.put("nature", nature.name());
        model.put("case", object(caseSnapshot));
        model.put("diagnosis", object(diagnosisSnapshot));
        model.put("responsibility", object(responsibilitySnapshot));
        model.put("material", object(materialSnapshot));
        model.put("technicalResults", object(technicalSnapshot));
        model.put("signedBy", actor.actorId());
        model.put("signedAt", Instant.now().toString());
        if (supplementalContent != null) model.put("supplementalContent", supplementalContent);
        String content = json(model);
        String contentHash = sha256(content);
        byte[] pdf = pdfRenderer.render(pathologyCase.caseNo(), content, contentHash);
        return new RenderedReport(diagnosisSnapshot, responsibilitySnapshot, caseSnapshot, materialSnapshot,
                technicalSnapshot, content, contentHash, pdf, sha256(pdf));
    }

    private ReportTemplateVersion resolveTemplate(Case pathologyCase, UUID templateVersionId, ActorContext actor) {
        ReportTemplateVersion template = templateVersionId == null
                ? repository.findPublishedTemplateForBusinessType(pathologyCase.businessTypeId(), actor.hospitalScope())
                        .orElseThrow(() -> reject("V2-REPORT-TEMPLATE-NOT-PUBLISHED", "没有可用的已发布报告模板"))
                : repository.findTemplateVersion(templateVersionId, actor.hospitalScope())
                        .orElseThrow(() -> reject("V2-REPORT-TEMPLATE-VERSION-NOT-FOUND", "报告模板版本不存在"));
        if (templateVersionId != null && !repository.templateVersionMatchesBusinessType(templateVersionId,
                pathologyCase.businessTypeId(), actor.hospitalScope())) {
            throw reject("V2-REPORT-TEMPLATE-BUSINESS-TYPE", "报告模板版本与病例业务类型不匹配");
        }
        if (!template.published()) throw reject("V2-REPORT-TEMPLATE-NOT-PUBLISHED", "报告模板版本尚未发布");
        return template;
    }

    private Map<String, Object> materialRowSnapshot(JdbcV2MaterialRepository.MaterialTreeRow row) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("specimenId", row.specimenId());
        snapshot.put("specimenNo", row.specimenNo());
        snapshot.put("specimenCode", row.specimenCode());
        snapshot.put("specimenKindCode", row.specimenKindCode());
        snapshot.put("blockId", value(row.blockId()));
        snapshot.put("blockCode", value(row.blockCode()));
        snapshot.put("blockType", value(row.blockType()));
        snapshot.put("slideId", value(row.slideId()));
        snapshot.put("slideCode", value(row.slideCode()));
        snapshot.put("slideType", value(row.slideType()));
        snapshot.put("sourceContextType", row.sourceContextType());
        snapshot.put("completedAt", value(row.completedAt()));
        snapshot.put("completedByRef", value(row.completedByRef()));
        snapshot.put("required", row.required());
        snapshot.put("concurrencyVersion", row.concurrencyVersion());
        return snapshot;
    }

    private List<String> validationReasons(Case pathologyCase, Diagnosis diagnosis,
            List<ResponsibilityUnit> responsibilities, List<OrderSnapshot> technicalOrders,
            ReportTemplateVersion template, String actorId, String organizationReference) {
        List<String> reasons = new ArrayList<>();
        if (pathologyCase == null || !Case.ACTIVE.equals(pathologyCase.lifecycleStateCode())) reasons.add("CASE_NOT_ACTIVE");
        if (diagnosis == null) reasons.add("DIAGNOSIS_NOT_FOUND");
        if (diagnosis != null && (diagnosis.diagnosisText() == null || diagnosis.diagnosisText().isBlank())) {
            reasons.add("DIAGNOSIS_NOT_VALID");
        }
        if (template == null || !template.published()) reasons.add("REPORT_TEMPLATE_NOT_VALID");
        ResponsibilityUnit auditResponsibility = lastAudit(responsibilities);
        if (auditResponsibility == null) reasons.add("AUDIT_RESPONSIBILITY_NOT_FOUND");
        else if (!actorId.equals(auditResponsibility.doctorId())) reasons.add("AUDIT_DOCTOR_MISMATCH");
        reasons.addAll(technicalReasons(technicalOrders));
        if (diagnosis != null && pathologyCase != null
                && technicalOrderService.hasBlockingTechnicalOrders(diagnosis.id(), organizationReference)
                && technicalReasons(technicalOrders).isEmpty()) {
            reasons.add("BLOCKING_TECHNICAL_ORDER");
        }
        return reasons;
    }

    private List<String> technicalReasons(List<OrderSnapshot> technicalOrders) {
        return technicalOrders.stream().filter(OrderSnapshot::blocking)
                .map(order -> "BLOCKING_TECHNICAL_ORDER:" + order.order().orderNo()).toList();
    }

    private ReportActions actionSummary(Case pathologyCase, Diagnosis diagnosis, List<ResponsibilityUnit> responsibilities,
            List<OrderSnapshot> technicalOrders, ActorContext actor, List<String> reasons) {
        List<Report> reports = repository.findReportsByCase(pathologyCase.id(), actor.hospitalScope());
        boolean effectiveOriginal = reports.stream().anyMatch(item -> item.nature() == ReportNature.ORIGINAL
                && item.status() == ReportStatus.EFFECTIVE);
        boolean auditActor = lastAudit(responsibilities) != null
                && actor.actorId().equals(lastAudit(responsibilities).doctorId());
        return new ReportActions(true, reasons.isEmpty() && auditActor && !effectiveOriginal,
                reports.stream().anyMatch(item -> item.status() == ReportStatus.EFFECTIVE),
                auditActor && effectiveOriginal && technicalReasons(technicalOrders).isEmpty());
    }

    private Report choosePriorReport(UUID diagnosisId, UUID requestedId, ActorContext actor) {
        Report report = requestedId == null ? repository.findEffectiveOriginal(diagnosisId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-REPORT-PRIOR-EFFECTIVE-REQUIRED", "补充报告需要已有生效的原始报告"))
                : repository.findReport(requestedId, actor.hospitalScope())
                        .orElseThrow(() -> reject("V2-REPORT-NOT-FOUND", "关联报告不存在"));
        if (report.nature() != ReportNature.ORIGINAL || report.status() != ReportStatus.EFFECTIVE) {
            throw reject("V2-REPORT-PRIOR-EFFECTIVE-REQUIRED", "补充报告必须关联生效的原始报告");
        }
        return report;
    }

    private ResponsibilityUnit lastAudit(List<ResponsibilityUnit> responsibilities) {
        return responsibilities.stream().filter(item -> item.role() == ResponsibilityRole.AUDIT)
                .filter(item -> item.endedAt() == null).max(Comparator.comparingInt(ResponsibilityUnit::sequence))
                .orElse(null);
    }

    private void lockDiagnosis(UUID diagnosisId, ActorContext actor) {
        if (!diagnosisRepository.lockDiagnosis(diagnosisId, actor.hospitalScope())) {
            throw reject("V2-DIAGNOSIS-NOT-FOUND", "诊断不存在或不在当前数据范围");
        }
    }

    private Case activeCase(UUID caseId, ActorContext actor) {
        Case pathologyCase = registrationRepository.findCase(caseId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-CASE-NOT-FOUND", "病例不存在或不在当前数据范围"));
        if (!Case.ACTIVE.equals(pathologyCase.lifecycleStateCode())) throw reject("V2-CASE-NOT-ACTIVE", "病例不是ACTIVE状态");
        return pathologyCase;
    }

    private ReportResult replay(String operation, String key, String digest, ActorContext actor) {
        IdempotencyResult existing = repository.findIdempotency(operation, key).orElse(null);
        if (existing == null) return null;
        if (!existing.payloadDigest().equals(digest)) throw reject("V2-IDEMPOTENCY-CONFLICT", "幂等键对应的报告命令摘要冲突");
        return reportResult(repository.findReport(existing.reportId(), actor.hospitalScope()).orElseThrow(), true);
    }

    private ReportResult reportResult(Report report, boolean duplicate) {
        return new ReportResult(report.id(), report.reportNo(), report.caseId(), report.diagnosisId(), report.nature(),
                report.nature() == ReportNature.SUPPLEMENTAL, report.priorReportId(), report.status(), report.templateVersionId(), report.renderedContentHash(),
                report.pdfFileReference(), report.pdfContentHash(), report.signedBy(), report.signedAt(),
                report.withdrawnBy(), report.withdrawnAt(), report.withdrawalReason(), report.renderedContent(), duplicate);
    }

    private ReportView reportView(Report report) {
        return new ReportView(report.id(), report.reportNo(), report.nature(), report.nature() == ReportNature.SUPPLEMENTAL,
                report.status(), report.priorReportId(), report.templateVersionId(), report.pdfFileReference(), report.pdfContentHash(), report.signedBy(),
                report.signedAt(), report.withdrawnAt(), report.withdrawalReason());
    }

    private TemplateVersionResult templateVersionResult(ReportTemplateVersion version, boolean duplicate) {
        return new TemplateVersionResult(version.id(), version.templateId(), version.versionNo(), version.definition(),
                version.status(), version.publishedAt(), duplicate);
    }

    private void publish(String event, UUID subjectId, long version, ActorContext actor, String digest) {
        outbox.append(event, subjectId, "V2-REPORT", version, UUID.randomUUID().toString(), digest, actor.actorId());
    }

    private static List<String> append(List<String> values, String value) {
        List<String> copy = new ArrayList<>(values);
        copy.add(value);
        return copy;
    }

    private static String value(Object value) { return value == null ? "" : value.toString(); }
    private Object object(String json) {
        try { return objectMapper.readTree(json); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("快照JSON无效", exception); }
    }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("报告快照无法序列化", exception); }
    }
    private static String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
    private static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256不可用", exception); }
    }
    private static String digest(Object... values) { return sha256(java.util.Arrays.toString(values)); }
    private static void requireKey(String value) { requireText(value, "幂等键不能为空"); }
    private static void requireText(String value, String message) { if (value == null || value.isBlank()) throw reject("V2-INVALID-REQUEST", message); }
    private static P15BusinessException reject(String code, String message) { return new P15BusinessException(code, message); }
    private static P15BusinessException conflict(String message) { return new P15BusinessException("V2-VERSION-CONFLICT", message, 409); }

    private record RenderedReport(String diagnosisSnapshot, String responsibilitySnapshot, String caseSnapshot,
            String materialSnapshot, String technicalSnapshot, String content, String contentHash, byte[] pdf,
            String pdfContentHash) { }
    public record SignOutCommand(UUID templateVersionId, String idempotencyKey) { }
    public record WithdrawCommand(String reason, String idempotencyKey) { }
    public record SupplementalCommand(UUID priorReportId, UUID templateVersionId, String content,
            String idempotencyKey) { }
    public record EncryptedPdfCommand(String accessPassword, String reason) { }
    public record CreateTemplateCommand(String code, String name, UUID businessTypeId) { }
    public record CreateTemplateVersionCommand(String definition) { }
    public record PreviewResult(boolean valid, List<String> blockingReasons, UUID templateVersionId, int templateVersionNo,
            String renderedContent, String renderedContentHash, String pdfContentHash, ReportActions actions) { }
    public record ReportResult(UUID reportId, String reportNo, UUID caseId, UUID diagnosisId, ReportNature nature,
            boolean supplemental, UUID priorReportId, ReportStatus status, UUID templateVersionId, String renderedContentHash,
            String pdfFileReference, String pdfContentHash, String signedBy, Instant signedAt, String withdrawnBy,
            Instant withdrawnAt, String withdrawalReason, String renderedContent, boolean duplicate) { }
    public record ReportView(UUID reportId, String reportNo, ReportNature nature, boolean supplemental, ReportStatus status, UUID priorReportId,
            UUID templateVersionId, String pdfFileReference, String pdfContentHash, String signedBy, Instant signedAt,
            Instant withdrawnAt, String withdrawalReason) { }
    public record ReportActions(boolean canPreview, boolean canSignOut, boolean canWithdraw, boolean canSupplement) { }
    public record WorkspaceReport(List<ReportView> reports, ReportActions actions, List<String> blockingReasons) { }
    public record PdfResult(UUID reportId, String reportNo, String fileReference, String contentHash,
            String protection, byte[] content) { }
    public record TemplateResult(UUID templateId, String code, String name, boolean duplicate) { }
    public record TemplateVersionResult(UUID versionId, UUID templateId, int versionNo, String definition, String status,
            Instant publishedAt, boolean duplicate) { }
}
