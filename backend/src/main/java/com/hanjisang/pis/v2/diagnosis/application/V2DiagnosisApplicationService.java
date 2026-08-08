package com.hanjisang.pis.v2.diagnosis.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.integration.OutboxPort;
import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.diagnosis.domain.AssignmentRule;
import com.hanjisang.pis.v2.diagnosis.domain.AssignmentSource;
import com.hanjisang.pis.v2.diagnosis.domain.Diagnosis;
import com.hanjisang.pis.v2.diagnosis.domain.DiagnosisContextType;
import com.hanjisang.pis.v2.diagnosis.domain.DiagnosisTemplate;
import com.hanjisang.pis.v2.diagnosis.domain.DiagnosisTemplateVersion;
import com.hanjisang.pis.v2.diagnosis.domain.ResponsibilityRole;
import com.hanjisang.pis.v2.diagnosis.domain.ResponsibilityUnit;
import com.hanjisang.pis.v2.diagnosis.infrastructure.JdbcV2DiagnosisRepository;
import com.hanjisang.pis.v2.diagnosis.infrastructure.JdbcV2DiagnosisRepository.IdempotencyResult;
import com.hanjisang.pis.v2.diagnosis.infrastructure.JdbcV2DiagnosisRepository.PublicPoolCase;
import com.hanjisang.pis.v2.material.infrastructure.JdbcV2MaterialRepository;
import com.hanjisang.pis.v2.material.infrastructure.JdbcV2MaterialRepository.MaterialTreeRow;
import com.hanjisang.pis.v2.digital.infrastructure.JdbcV2DigitalSlideRepository;
import com.hanjisang.pis.v2.molecular.infrastructure.JdbcV2MolecularResultRepository;
import com.hanjisang.pis.v2.registration.domain.Case;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository;
import com.hanjisang.pis.v2.report.application.V2ReportApplicationService;
import com.hanjisang.pis.v2.report.application.V2ReportApplicationService.WorkspaceReport;
import com.hanjisang.pis.v2.technical.domain.TechnicalOrderStatus;
import com.hanjisang.pis.v2.technical.infrastructure.JdbcV2TechnicalOrderRepository;
import com.hanjisang.pis.v2.technical.infrastructure.JdbcV2TechnicalOrderRepository.ItemSnapshot;
import com.hanjisang.pis.v2.technical.infrastructure.JdbcV2TechnicalOrderRepository.OrderSnapshot;

@Service
public class V2DiagnosisApplicationService {

    public static final String DIAGNOSIS_VIEW = "P14-PERM-055";
    public static final String DIAGNOSIS_INITIAL = "P14-PERM-034";
    public static final String DIAGNOSIS_REVIEW = "P14-PERM-034";
    public static final String DIAGNOSIS_AUDIT = "P14-PERM-034";
    public static final String DIAGNOSIS_ASSIGN = "P14-PERM-034";
    public static final String DIAGNOSIS_REASSIGN = "P14-PERM-034";
    public static final String TEMPLATE_MANAGE = "P14-PERM-042";

    private final JdbcV2DiagnosisRepository repository;
    private final JdbcV2RegistrationRepository registrationRepository;
    private final JdbcV2MaterialRepository materialRepository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;
    private final JdbcV2TechnicalOrderRepository technicalRepository;
    private final V2ReportApplicationService reportService;
    private final JdbcV2MolecularResultRepository molecularResultRepository;
    private final JdbcV2DigitalSlideRepository digitalSlideRepository;

    public V2DiagnosisApplicationService(JdbcV2DiagnosisRepository repository,
            JdbcV2RegistrationRepository registrationRepository, JdbcV2MaterialRepository materialRepository,
            P15AuthorizationService authorization, JdbcAuditEventRepository audit, OutboxPort outbox,
            JdbcV2TechnicalOrderRepository technicalRepository, V2ReportApplicationService reportService,
            JdbcV2MolecularResultRepository molecularResultRepository, JdbcV2DigitalSlideRepository digitalSlideRepository) {
        this.repository = repository;
        this.registrationRepository = registrationRepository;
        this.materialRepository = materialRepository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.technicalRepository = technicalRepository;
        this.reportService = reportService;
        this.molecularResultRepository = molecularResultRepository;
        this.digitalSlideRepository = digitalSlideRepository;
    }

    @Transactional
    public AssignmentResult claimDiagnosis(UUID caseId, String idempotencyKey) {
        ActorContext actor = authorization.require(DIAGNOSIS_INITIAL);
        requireKey(idempotencyKey);
        String operation = "PIS-V2-I03-DIAGNOSIS-CLAIM";
        String digest = digest(caseId, actor.actorId(), "SELF_CLAIM");
        AssignmentResult replay = replayAssignment(operation, idempotencyKey, digest, actor);
        if (replay != null) { return replay; }
        lockCase(caseId, actor);
        replay = replayAssignment(operation, idempotencyKey, digest, actor);
        if (replay != null) { return replay; }
        Case pathologyCase = activeCase(caseId, actor);
        requireInitialMaterialComplete(caseId, actor);
        Diagnosis diagnosis = repository.findDiagnosisByCase(caseId, actor.hospitalScope()).orElse(null);
        ensureNoOpenInitial(diagnosis, actor);
        if (diagnosis == null) {
            diagnosis = createDiagnosis(pathologyCase, actor);
        }
        ResponsibilityUnit responsibility = createResponsibility(diagnosis, ResponsibilityRole.INITIAL,
                actor.actorId(), AssignmentSource.SELF_CLAIM, "公开池自主认领", actor);
        repository.insertResponsibility(responsibility);
        repository.insertIdempotency(operation, idempotencyKey, digest, "DIAGNOSIS", diagnosis.id(),
                actor.actorId(), Instant.now());
        audit.append("PIS-V2-I03-DIAGNOSIS-CLAIM", DIAGNOSIS_INITIAL, actor, "ALLOWED", "COMPLETED",
                responsibility.id(), "V2-RESPONSIBILITY", UUID.randomUUID().toString(), "source=SELF_CLAIM");
        publish("V2-I03-DIAGNOSIS-CLAIMED", diagnosis.id(), 0, actor, digest);
        return assignmentResult(diagnosis, responsibility, false);
    }

    @Transactional
    public AssignmentResult assignDiagnosis(AssignDiagnosisCommand command) {
        ActorContext actor = authorization.require(DIAGNOSIS_ASSIGN);
        validateCaseCommand(command.caseId(), command.idempotencyKey());
        requireDoctor(command.doctorId());
        String operation = "PIS-V2-I03-DIAGNOSIS-ASSIGN";
        String digest = digest(command.caseId(), command.doctorId(), command.reason(), "MANUAL");
        AssignmentResult replay = replayAssignment(operation, command.idempotencyKey(), digest, actor);
        if (replay != null) { return replay; }
        lockCase(command.caseId(), actor);
        replay = replayAssignment(operation, command.idempotencyKey(), digest, actor);
        if (replay != null) { return replay; }
        Case pathologyCase = activeCase(command.caseId(), actor);
        requireInitialMaterialComplete(command.caseId(), actor);
        Diagnosis diagnosis = repository.findDiagnosisByCase(command.caseId(), actor.hospitalScope()).orElse(null);
        ensureNoOpenInitial(diagnosis, actor);
        if (diagnosis == null) { diagnosis = createDiagnosis(pathologyCase, actor); }
        ResponsibilityUnit responsibility = createResponsibility(diagnosis, ResponsibilityRole.INITIAL,
                command.doctorId(), AssignmentSource.MANUAL, command.reason(), actor);
        repository.insertResponsibility(responsibility);
        repository.insertIdempotency(operation, command.idempotencyKey(), digest, "DIAGNOSIS", diagnosis.id(),
                actor.actorId(), Instant.now());
        audit.append("PIS-V2-I03-DIAGNOSIS-ASSIGN", DIAGNOSIS_ASSIGN, actor, "ALLOWED", "COMPLETED",
                responsibility.id(), "V2-RESPONSIBILITY", UUID.randomUUID().toString(), "source=MANUAL;doctor="
                        + command.doctorId());
        publish("V2-I03-DIAGNOSIS-ASSIGNED", diagnosis.id(), 0, actor, digest);
        return assignmentResult(diagnosis, responsibility, false);
    }

    @Transactional
    public AssignmentResult selfClaimDiagnosis(SelfClaimCommand command) {
        return claimDiagnosis(command.caseId(), command.idempotencyKey());
    }

    @Transactional
    public AssignmentResult reassignDiagnosis(ReassignDiagnosisCommand command) {
        ActorContext actor = authorization.require(DIAGNOSIS_REASSIGN);
        validateCaseCommand(command.caseId(), command.idempotencyKey());
        requireDoctor(command.doctorId());
        requireText(command.reason(), "重分配原因不能为空");
        String operation = "PIS-V2-I03-DIAGNOSIS-REASSIGN";
        String digest = digest(command.caseId(), command.doctorId(), command.reason(), "REASSIGN");
        AssignmentResult replay = replayAssignment(operation, command.idempotencyKey(), digest, actor);
        if (replay != null) { return replay; }
        lockCase(command.caseId(), actor);
        replay = replayAssignment(operation, command.idempotencyKey(), digest, actor);
        if (replay != null) { return replay; }
        Case pathologyCase = activeCase(command.caseId(), actor);
        Diagnosis diagnosis = repository.findDiagnosisByCase(command.caseId(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-DIAGNOSIS-NOT-FOUND", "病例尚未建立诊断"));
        ResponsibilityUnit current = currentResponsibility(diagnosis, ResponsibilityRole.INITIAL, actor);
        if (current == null) { throw reject("V2-RESPONSIBILITY-NOT-OPEN", "初诊责任已完成或不存在"); }
        if (current.completedAt() != null) { throw reject("V2-RESPONSIBILITY-COMPLETED", "初诊完成后不能用重分配覆盖历史"); }
        current.end(current.version(), command.reason(), Instant.now());
        if (!repository.endResponsibility(current, actor.hospitalScope(), current.version() - 1)) {
            throw conflict("责任重分配版本冲突");
        }
        ResponsibilityUnit replacement = createResponsibility(diagnosis, ResponsibilityRole.INITIAL,
                command.doctorId(), AssignmentSource.REASSIGN, command.reason(), actor);
        repository.insertResponsibility(replacement);
        repository.insertIdempotency(operation, command.idempotencyKey(), digest, "DIAGNOSIS", diagnosis.id(),
                actor.actorId(), Instant.now());
        audit.append("PIS-V2-I03-DIAGNOSIS-REASSIGN", DIAGNOSIS_REASSIGN, actor, "ALLOWED", "COMPLETED",
                replacement.id(), "V2-RESPONSIBILITY", UUID.randomUUID().toString(), "old=" + current.id()
                        + ";new=" + replacement.id() + ";reason=" + command.reason());
        publish("V2-I03-DIAGNOSIS-REASSIGNED", diagnosis.id(), 0, actor, digest);
        return assignmentResult(diagnosis, replacement, false);
    }

    @Transactional
    public DiagnosisResult saveDiagnosis(UUID diagnosisId, SaveDiagnosisCommand command) {
        requireKey(command.idempotencyKey());
        ActorContext actor = authorization.require(DIAGNOSIS_VIEW);
        String operation = "PIS-V2-I03-DIAGNOSIS-SAVE";
        String digest = digest(diagnosisId, command.expectedVersion(), command.structuredData(),
                command.microscopicDescription(), command.diagnosisText(), command.comment());
        DiagnosisResult replay = replayDiagnosis(operation, command.idempotencyKey(), digest, actor);
        if (replay != null) { return replay; }
        if (!repository.lockDiagnosis(diagnosisId, actor.hospitalScope())) {
            throw reject("V2-DIAGNOSIS-NOT-FOUND", "诊断不存在或不在当前数据范围");
        }
        replay = replayDiagnosis(operation, command.idempotencyKey(), digest, actor);
        if (replay != null) { return replay; }
        Diagnosis diagnosis = requireDiagnosis(diagnosisId, actor);
        if (reportService.hasEffectiveOriginal(diagnosisId, actor.hospitalScope())) {
            throw reject("V2-DIAGNOSIS-REPORT-EFFECTIVE", "报告生效后不能直接修改诊断，请撤回后重新处理或创建补充报告");
        }
        ResponsibilityUnit current = currentResponsibility(diagnosis, null, actor);
        requireCurrentDoctor(current, actor);
        authorizeRole(current.role(), actor);
        String before = contentDigest(diagnosis);
        try {
            diagnosis.updateContent(diagnosis.templateVersionId(), command.structuredData(),
                    command.microscopicDescription(), command.diagnosisText(), command.comment(),
                    command.expectedVersion(), Instant.now(), actor.actorId());
        } catch (IllegalStateException exception) {
            throw conflict("诊断版本冲突，请重新读取后重试");
        }
        if (!repository.updateDiagnosis(diagnosis, actor.hospitalScope(), command.expectedVersion(), Instant.now(),
                actor.actorId())) {
            throw conflict("诊断版本冲突，请重新读取后重试");
        }
        repository.insertIdempotency(operation, command.idempotencyKey(), digest, "DIAGNOSIS", diagnosis.id(),
                actor.actorId(), Instant.now());
        audit.append("PIS-V2-I03-DIAGNOSIS-EDIT", permissionFor(current.role()), actor, "ALLOWED", "COMPLETED",
                diagnosis.id(), "V2-DIAGNOSIS", UUID.randomUUID().toString(), "beforeDigest=" + before
                        + ";afterDigest=" + contentDigest(diagnosis));
        publish("V2-I03-DIAGNOSIS-EDITED", diagnosis.id(), diagnosis.version(), actor, digest);
        return diagnosisResult(diagnosis, false);
    }

    @Transactional
    public ResponsibilityCompletionResult completeInitialDiagnosis(UUID diagnosisId,
            CompleteResponsibilityCommand command) {
        return completeResponsibility(diagnosisId, ResponsibilityRole.INITIAL, command);
    }

    @Transactional
    public ResponsibilityCompletionResult completeReviewDiagnosis(UUID diagnosisId,
            CompleteResponsibilityCommand command) {
        return completeResponsibility(diagnosisId, ResponsibilityRole.REVIEW, command);
    }

    @Transactional
    public ResponsibilityCompletionResult completeAuditDiagnosis(UUID diagnosisId,
            CompleteResponsibilityCommand command) {
        return completeResponsibility(diagnosisId, ResponsibilityRole.AUDIT, command);
    }

    @Transactional
    public TemplateResult createTemplate(CreateTemplateCommand command) {
        ActorContext actor = authorization.require(TEMPLATE_MANAGE);
        requireText(command.code(), "模板编码不能为空");
        requireText(command.name(), "模板名称不能为空");
        requireKey(command.idempotencyKey());
        Instant now = Instant.now();
        DiagnosisTemplate template = DiagnosisTemplate.create(UUID.randomUUID(), command.code(), command.name(),
                command.businessTypeId(), command.scope(), now, actor.actorId());
        repository.insertTemplate(template, actor.hospitalScope(), now, actor.actorId());
        audit.append("PIS-V2-I03-TEMPLATE-CREATE", TEMPLATE_MANAGE, actor, "ALLOWED", "COMPLETED", template.id(),
                "V2-DIAGNOSIS-TEMPLATE", UUID.randomUUID().toString(), "templateCode=" + template.code());
        return new TemplateResult(template.id(), template.code(), template.name(), template.version(), false);
    }

    @Transactional
    public TemplateVersionResult createTemplateVersion(CreateTemplateVersionCommand command) {
        ActorContext actor = authorization.require(TEMPLATE_MANAGE);
        requireText(command.schemaDefinition(), "模板Schema不能为空");
        requireKey(command.idempotencyKey());
        if (!repository.lockTemplate(command.templateId(), actor.hospitalScope())) {
            throw reject("V2-TEMPLATE-NOT-FOUND", "诊断模板不存在或不在当前数据范围");
        }
        int versionNo = repository.nextTemplateVersion(command.templateId());
        DiagnosisTemplateVersion version = DiagnosisTemplateVersion.draft(UUID.randomUUID(), command.templateId(),
                versionNo, command.schemaDefinition(), Instant.now(), actor.actorId());
        repository.insertTemplateVersion(version);
        audit.append("PIS-V2-I03-TEMPLATE-VERSION-CREATE", TEMPLATE_MANAGE, actor, "ALLOWED", "COMPLETED",
                version.id(), "V2-DIAGNOSIS-TEMPLATE-VERSION", UUID.randomUUID().toString(),
                "templateId=" + command.templateId() + ";versionNo=" + versionNo);
        return templateVersionResult(version, false);
    }

    @Transactional
    public TemplateVersionResult publishTemplateVersion(UUID versionId, String idempotencyKey) {
        ActorContext actor = authorization.require(TEMPLATE_MANAGE);
        requireKey(idempotencyKey);
        DiagnosisTemplateVersion version = repository.findTemplateVersionForUpdate(versionId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-TEMPLATE-VERSION-NOT-FOUND", "诊断模板版本不存在或不在当前数据范围"));
        if (DiagnosisTemplateVersion.PUBLISHED.equals(version.status())) {
            return templateVersionResult(version, true);
        }
        version.publish(Instant.now(), actor.actorId());
        if (!repository.publishTemplateVersion(versionId, actor.hospitalScope(), version.publishedAt(), actor.actorId())) {
            throw conflict("模板版本发布冲突，请重新读取后重试");
        }
        audit.append("PIS-V2-I03-TEMPLATE-PUBLISH", TEMPLATE_MANAGE, actor, "ALLOWED", "COMPLETED", version.id(),
                "V2-DIAGNOSIS-TEMPLATE-VERSION", UUID.randomUUID().toString(), "versionNo=" + version.versionNo());
        return templateVersionResult(version, false);
    }

    @Transactional
    public void createAssignmentRule(AssignmentRule rule) {
        ActorContext actor = authorization.require(TEMPLATE_MANAGE);
        repository.insertAssignmentRule(rule);
        audit.append("PIS-V2-I03-ASSIGNMENT-RULE-CREATE", TEMPLATE_MANAGE, actor, "ALLOWED", "COMPLETED", rule.id(),
                "V2-ASSIGNMENT-RULE", UUID.randomUUID().toString(), "priority=" + rule.priority());
    }

    @Transactional(readOnly = true)
    public DiagnosisWorkspaceResult workspace(UUID caseId) {
        ActorContext actor = authorization.require(DIAGNOSIS_VIEW);
        Case pathologyCase = registrationRepository.findCase(caseId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-CASE-NOT-FOUND", "病例不存在或不在当前数据范围"));
        List<MaterialTreeRow> rows = materialRepository.findMaterialTree(caseId, actor.hospitalScope());
        MaterialTreeResult materialTree = materialTree(pathologyCase, rows);
        List<DigitalSlideView> digitalSlides = digitalSlideRepository.findByCase(caseId, actor.hospitalScope()).stream()
                .map(item -> new DigitalSlideView(item.id(), item.blockId(), item.slideId(), item.statusCode(),
                        item.viewerReference(), item.sourcePlatform())).toList();
        Diagnosis diagnosis = repository.findDiagnosisByCase(caseId, actor.hospitalScope()).orElse(null);
        DiagnosisTemplateVersion templateVersion = diagnosis == null ? null
                : repository.findTemplateVersion(diagnosis.templateVersionId(), actor.hospitalScope()).orElse(null);
        List<ResponsibilityUnit> responsibilities = diagnosis == null ? List.of()
                : repository.findResponsibilities(diagnosis.id(), actor.hospitalScope());
        ResponsibilityUnit current = responsibilities.stream().filter(ResponsibilityUnit::isCurrent)
                .max(java.util.Comparator.comparingInt(ResponsibilityUnit::sequence)).orElse(null);
        boolean materialReady = materialTree.initialProductionComplete();
        boolean active = Case.ACTIVE.equals(pathologyCase.lifecycleStateCode());
        boolean hasInitialHistory = responsibilities.stream().anyMatch(item -> item.role() == ResponsibilityRole.INITIAL);
        boolean actorCurrent = current != null && actor.actorId().equals(current.doctorId());
        boolean readyForSignOut = responsibilities.stream().anyMatch(item -> item.role() == ResponsibilityRole.AUDIT
                && item.completedAt() != null && item.endedAt() == null);
        Actions actions = new Actions(active && materialReady && !hasInitialHistory,
                active && materialReady && !hasInitialHistory,
                active && actorCurrent && current.role() == ResponsibilityRole.INITIAL,
                active && actorCurrent && current.role() == ResponsibilityRole.REVIEW,
                active && actorCurrent && current.role() == ResponsibilityRole.AUDIT,
                active && current != null && current.role() == ResponsibilityRole.INITIAL, readyForSignOut,
                active && actorCurrent && diagnosis != null, false, false, false, false);
        List<OrderSnapshot> technicalOrderSnapshots = diagnosis == null ? List.of()
                : technicalRepository.findOrderSnapshotsByDiagnosis(diagnosis.id(), actor.hospitalScope());
        List<TechnicalOrderView> technicalOrders = technicalOrderSnapshots.stream().map(this::technicalOrderView).toList();
        int blockingTechnicalOrderCount = (int) technicalOrders.stream().filter(TechnicalOrderView::blocking).count();
        WorkspaceReport reportWorkspace = reportService.workspace(caseId, diagnosis, responsibilities,
                technicalOrderSnapshots, actor.hospitalScope(), actor.actorId());
        actions = new Actions(actions.canClaim(), actions.canAssign(), actions.canCompleteInitial(),
                actions.canCompleteReview(), actions.canCompleteAudit(), actions.canReassign(), actions.readyForSignOut(),
                actions.canCreateTechnicalOrder(), reportWorkspace.actions().canPreview(),
                reportWorkspace.actions().canSignOut(), reportWorkspace.actions().canWithdraw(),
                reportWorkspace.actions().canSupplement());
        return new DiagnosisWorkspaceResult(new CaseSummary(pathologyCase.id(), pathologyCase.caseNo(),
                pathologyCase.businessTypeCode(), pathologyCase.lifecycleStateCode()),
                new ApplicationSummary(pathologyCase.applicationItemCode(), pathologyCase.sourceSystemCode(),
                        pathologyCase.externalApplicationId()),
                new PatientSnapshot(pathologyCase.patientReference(), pathologyCase.visitReference()), materialTree,
                diagnosis == null ? null : diagnosisView(diagnosis), templateVersion == null ? null
                        : templateVersionView(templateVersion), responsibilities.stream().map(this::responsibilityView).toList(),
                current == null ? null : responsibilityView(current), actions, technicalOrders,
                blockingTechnicalOrderCount,
                new Placeholder("TECHNICAL_ORDER", "V2-I04已实现"), new Placeholder("REPORT", "V2-I05待实现"),
                reportWorkspace.reports(), reportWorkspace.blockingReasons(), digitalSlides,
                Instant.now());
    }

    @Transactional(readOnly = true)
    public List<PublicPoolEntry> publicPool() {
        ActorContext actor = authorization.require(DIAGNOSIS_VIEW);
        return repository.findPublicPoolCases(actor.hospitalScope()).stream()
                .map(item -> new PublicPoolEntry(item.caseId(), item.pathologyNo(), item.businessTypeCode()))
                .toList();
    }

    private ResponsibilityCompletionResult completeResponsibility(UUID diagnosisId, ResponsibilityRole role,
            CompleteResponsibilityCommand command) {
        ActorContext actor = authorization.require(permissionFor(role));
        requireKey(command.idempotencyKey());
        String operation = "PIS-V2-I03-RESPONSIBILITY-COMPLETE-" + role.name();
        String commandDigest = digest(diagnosisId, role, command.responsibilityId(),
                command.responsibilityExpectedVersion(), command.structuredData(), command.microscopicDescription(),
                command.diagnosisText(), command.comment(), command.diagnosisExpectedVersion(), command.nextRole(),
                command.nextDoctorId(), command.nextReason());
        ResponsibilityCompletionResult replay = replayCompletion(operation, command.idempotencyKey(), commandDigest,
                role, actor);
        if (replay != null) { return replay; }
        if (!repository.lockDiagnosis(diagnosisId, actor.hospitalScope())) {
            throw reject("V2-DIAGNOSIS-NOT-FOUND", "诊断不存在或不在当前数据范围");
        }
        replay = replayCompletion(operation, command.idempotencyKey(), commandDigest, role, actor);
        if (replay != null) { return replay; }
        Diagnosis diagnosis = requireDiagnosis(diagnosisId, actor);
        ResponsibilityUnit current = currentResponsibility(diagnosis, role, actor);
        if (current == null) { throw reject("V2-RESPONSIBILITY-NOT-OPEN", "当前责任节点不存在或已完成"); }
        requireCurrentDoctor(current, actor);
        if (!current.id().equals(command.responsibilityId())) {
            throw reject("V2-RESPONSIBILITY-MISMATCH", "提交的责任节点不是当前责任");
        }
        if (command.diagnosisExpectedVersion() != diagnosis.version()) {
            throw conflict("诊断版本冲突，请重新读取后重试");
        }
        validateNextResponsibility(role, command.nextRole());
        String before = contentDigest(diagnosis);
        diagnosis.updateContent(diagnosis.templateVersionId(), command.structuredData(),
                command.microscopicDescription(), command.diagnosisText(), command.comment(),
                command.diagnosisExpectedVersion(), Instant.now(), actor.actorId());
        if (!repository.updateDiagnosis(diagnosis, actor.hospitalScope(), command.diagnosisExpectedVersion(),
                Instant.now(), actor.actorId())) {
            throw conflict("诊断版本冲突，请重新读取后重试");
        }
        current.complete(command.responsibilityExpectedVersion(), Instant.now());
        if (!repository.completeResponsibility(current, actor.hospitalScope(), command.responsibilityExpectedVersion())) {
            throw conflict("责任节点版本冲突，请重新读取后重试");
        }
        ResponsibilityUnit next = null;
        if (command.nextRole() != null) {
            requireDoctor(command.nextDoctorId());
            if (command.nextRole() == role) { throw reject("V2-RESPONSIBILITY-ROLE", "后续责任角色不能与当前角色相同"); }
            next = createResponsibility(diagnosis, command.nextRole(), command.nextDoctorId(), AssignmentSource.MANUAL,
                    command.nextReason(), actor);
            repository.insertResponsibility(next);
        }
        repository.insertIdempotency(operation, command.idempotencyKey(), commandDigest, "DIAGNOSIS", diagnosis.id(),
                actor.actorId(), Instant.now());
        audit.append("PIS-V2-I03-RESPONSIBILITY-COMPLETE", permissionFor(role), actor, "ALLOWED", "COMPLETED",
                current.id(), "V2-RESPONSIBILITY", UUID.randomUUID().toString(), "role=" + role + ";beforeDigest="
                        + before + ";afterDigest=" + contentDigest(diagnosis));
        publish("V2-I03-RESPONSIBILITY-COMPLETED", diagnosis.id(), diagnosis.version(), actor,
                digest(diagnosis.id(), current.id(), command.nextRole()));
        return new ResponsibilityCompletionResult(diagnosis.id(), current.id(), next == null ? null : next.id(),
                diagnosis.version(), role == ResponsibilityRole.AUDIT && next == null, false);
    }

    private ResponsibilityCompletionResult replayCompletion(String operation, String key, String digest,
            ResponsibilityRole role, ActorContext actor) {
        IdempotencyResult result = existingIdempotency(operation, key, digest);
        if (result == null) { return null; }
        Diagnosis diagnosis = repository.findDiagnosis(result.resultEntityId(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-IDEMPOTENCY-INVALID", "幂等结果对应诊断不存在"));
        ResponsibilityUnit next = currentResponsibility(diagnosis, null, actor);
        return new ResponsibilityCompletionResult(diagnosis.id(), null, next == null ? null : next.id(),
                diagnosis.version(), role == ResponsibilityRole.AUDIT && next == null, true);
    }

    private Diagnosis createDiagnosis(Case pathologyCase, ActorContext actor) {
        DiagnosisTemplateVersion template = repository.findPublishedTemplateVersion(pathologyCase.businessTypeId(),
                actor.hospitalScope()).orElseThrow(() -> reject("V2-TEMPLATE-NOT-PUBLISHED", "业务类型没有已发布诊断模板"));
        Diagnosis diagnosis = Diagnosis.create(UUID.randomUUID(), pathologyCase.id(), template.id(), "{}", null, null,
                null, Instant.now(), actor.actorId());
        repository.insertDiagnosis(diagnosis, actor.hospitalScope(), Instant.now(), actor.actorId());
        audit.append("PIS-V2-I03-DIAGNOSIS-CREATE", DIAGNOSIS_INITIAL, actor, "ALLOWED", "COMPLETED", diagnosis.id(),
                "V2-DIAGNOSIS", UUID.randomUUID().toString(), "templateVersion=" + template.id());
        return diagnosis;
    }

    private ResponsibilityUnit createResponsibility(Diagnosis diagnosis, ResponsibilityRole role, String doctorId,
            AssignmentSource source, String reason, ActorContext actor) {
        return ResponsibilityUnit.assign(UUID.randomUUID(), diagnosis.id(), role, doctorId,
                repository.nextResponsibilitySequence(diagnosis.id()), source, reason, Instant.now(), actor.actorId());
    }

    private AssignmentResult replayAssignment(String operation, String key, String digest, ActorContext actor) {
        IdempotencyResult result = existingIdempotency(operation, key, digest);
        if (result == null) { return null; }
        Diagnosis diagnosis = result.resultEntityId() == null ? null
                : repository.findDiagnosis(result.resultEntityId(), actor.hospitalScope()).orElse(null);
        if (diagnosis == null) {
            diagnosis = repository.findDiagnosisByCase(result.resultEntityId(), actor.hospitalScope()).orElse(null);
        }
        if (diagnosis == null) { throw reject("V2-IDEMPOTENCY-INVALID", "幂等结果对应诊断不存在"); }
        ResponsibilityUnit responsibility = currentResponsibility(diagnosis, null, actor);
        return assignmentResult(diagnosis, responsibility, true);
    }

    private DiagnosisResult replayDiagnosis(String operation, String key, String digest, ActorContext actor) {
        IdempotencyResult result = existingIdempotency(operation, key, digest);
        if (result == null) { return null; }
        Diagnosis diagnosis = repository.findDiagnosis(result.resultEntityId(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-IDEMPOTENCY-INVALID", "幂等结果对应诊断不存在"));
        return diagnosisResult(diagnosis, true);
    }

    private IdempotencyResult existingIdempotency(String operation, String key, String digest) {
        IdempotencyResult result = repository.findIdempotency(operation, key).orElse(null);
        if (result != null && !result.payloadDigest().equals(digest)) {
            throw reject("V2-IDEMPOTENCY-CONFLICT", "同一幂等键对应的诊断命令摘要冲突");
        }
        return result;
    }

    private Diagnosis requireDiagnosis(UUID diagnosisId, ActorContext actor) {
        return repository.findDiagnosis(diagnosisId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-DIAGNOSIS-NOT-FOUND", "诊断不存在或不在当前数据范围"));
    }

    private ResponsibilityUnit currentResponsibility(Diagnosis diagnosis, ResponsibilityRole role, ActorContext actor) {
        List<ResponsibilityUnit> responsibilities = repository.findResponsibilities(diagnosis.id(), actor.hospitalScope());
        return responsibilities.stream().filter(ResponsibilityUnit::isCurrent)
                .filter(item -> role == null || item.role() == role)
                .max(java.util.Comparator.comparingInt(ResponsibilityUnit::sequence)).orElse(null);
    }

    private void ensureNoOpenInitial(Diagnosis diagnosis, ActorContext actor) {
        if (diagnosis == null) { return; }
        List<ResponsibilityUnit> responsibilities = repository.findResponsibilities(diagnosis.id(), actor.hospitalScope());
        if (responsibilities.stream().anyMatch(item -> item.role() == ResponsibilityRole.INITIAL)) {
            throw reject("V2-DIAGNOSIS-INITIAL-ALREADY-HANDLED", "病例已有初诊责任历史，不能重新认领");
        }
    }

    private void requireCurrentDoctor(ResponsibilityUnit current, ActorContext actor) {
        if (current == null || !actor.actorId().equals(current.doctorId())) {
            throw reject("V2-RESPONSIBILITY-FORBIDDEN", "当前主体不是诊断责任人");
        }
    }

    private void authorizeRole(ResponsibilityRole role, ActorContext actor) {
        authorization.require(permissionFor(role));
    }

    private void requireInitialMaterialComplete(UUID caseId, ActorContext actor) {
        Case pathologyCase = activeCase(caseId, actor);
        if ("MOLECULAR".equals(pathologyCase.businessTypeCode())) {
            if (!molecularResultRepository.hasCompletedResult(caseId, actor.hospitalScope())) {
                throw reject("V2-DIAGNOSIS-MATERIAL-NOT-READY", "独立分子病例尚未录入已完成结果");
            }
            return;
        }
        List<MaterialTreeRow> rows = materialRepository.findMaterialTree(caseId, actor.hospitalScope());
        int required = 0;
        int completed = 0;
        for (MaterialTreeRow row : rows) {
            boolean initialProduction = "INITIAL".equals(row.sourceContextType())
                    || (pathologyCase.businessTypeCode().startsWith("CYTOLOGY_")
                            && "CYTOLOGY".equals(row.sourceContextType()))
                    || ("REFERRAL".equals(pathologyCase.businessTypeCode())
                            && "EXTERNAL".equals(row.sourceContextType()));
            if (!initialProduction || !Boolean.TRUE.equals(row.required())) { continue; }
            required++;
            if (row.completedAt() != null) { completed++; }
        }
        if (required == 0 || required != completed) {
            throw reject("V2-DIAGNOSIS-MATERIAL-NOT-READY", "初始必需切片尚未全部完成");
        }
    }

    private static void validateNextResponsibility(ResponsibilityRole role, ResponsibilityRole nextRole) {
        switch (role) {
            case INITIAL -> {
                if (nextRole != ResponsibilityRole.REVIEW && nextRole != ResponsibilityRole.AUDIT) {
                    throw reject("V2-RESPONSIBILITY-NEXT-REQUIRED", "初诊完成后必须选择复诊或审核责任");
                }
            }
            case REVIEW -> {
                if (nextRole != ResponsibilityRole.AUDIT) {
                    throw reject("V2-RESPONSIBILITY-NEXT-AUDIT", "复诊完成后必须建立审核责任");
                }
            }
            case AUDIT -> {
                if (nextRole != null) {
                    throw reject("V2-RESPONSIBILITY-AUDIT-TERMINAL", "审核责任完成后不能继续创建后续责任");
                }
            }
        }
    }

    private Case activeCase(UUID caseId, ActorContext actor) {
        Case pathologyCase = registrationRepository.findCase(caseId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-CASE-NOT-FOUND", "病例不存在或不在当前数据范围"));
        if (!Case.ACTIVE.equals(pathologyCase.lifecycleStateCode())) {
            throw reject("V2-CASE-CANCELLED", "已取消病例不能开展诊断操作");
        }
        return pathologyCase;
    }

    private void lockCase(UUID caseId, ActorContext actor) {
        if (!repository.lockCase(caseId, actor.hospitalScope())) {
            throw reject("V2-CASE-NOT-FOUND", "病例不存在或不在当前数据范围");
        }
    }

    private static void validateCaseCommand(UUID caseId, String key) {
        if (caseId == null) { throw reject("V2-INVALID-REQUEST", "病例内部ID不能为空"); }
        requireKey(key);
    }

    private static void requireKey(String key) {
        requireText(key, "幂等键不能为空");
    }

    private static void requireDoctor(String doctorId) { requireText(doctorId, "责任医生不能为空"); }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) { throw reject("V2-INVALID-REQUEST", message); }
    }

    private static P15BusinessException reject(String code, String message) { return new P15BusinessException(code, message); }
    private static P15BusinessException conflict(String message) { return new P15BusinessException("V2-VERSION-CONFLICT", message, 409); }

    private static String permissionFor(ResponsibilityRole role) {
        return switch (role) {
            case INITIAL -> DIAGNOSIS_INITIAL;
            case REVIEW -> DIAGNOSIS_REVIEW;
            case AUDIT -> DIAGNOSIS_AUDIT;
        };
    }

    private static String digest(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = java.util.Arrays.stream(values).map(value -> value == null ? "<null>" : value.toString())
                    .reduce((left, right) -> left + "|" + right).orElse("");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private static String contentDigest(Diagnosis diagnosis) {
        return digest(diagnosis.structuredData(), diagnosis.microscopicDescription(), diagnosis.diagnosisText(),
                diagnosis.comment());
    }

    private void publish(String event, UUID subjectId, long version, ActorContext actor, String payloadDigest) {
        outbox.append(event, subjectId, "V2-DIAGNOSIS", version, UUID.randomUUID().toString(), payloadDigest,
                actor.actorId());
    }

    private static AssignmentResult assignmentResult(Diagnosis diagnosis, ResponsibilityUnit responsibility,
            boolean duplicate) {
        return new AssignmentResult(diagnosis.id(), diagnosis.caseId(), responsibility == null ? null : responsibility.id(),
                responsibility == null ? null : responsibility.role(), responsibility == null ? null : responsibility.doctorId(),
                responsibility == null ? null : responsibility.sequence(), diagnosis.version(), duplicate);
    }

    private static DiagnosisResult diagnosisResult(Diagnosis diagnosis, boolean duplicate) {
        return new DiagnosisResult(diagnosis.id(), diagnosis.caseId(), diagnosis.templateVersionId(),
                diagnosis.structuredData(), diagnosis.microscopicDescription(), diagnosis.diagnosisText(), diagnosis.comment(),
                diagnosis.version(), duplicate);
    }

    private static TemplateVersionResult templateVersionResult(DiagnosisTemplateVersion version, boolean duplicate) {
        return new TemplateVersionResult(version.id(), version.templateId(), version.versionNo(), version.schemaDefinition(),
                version.status(), version.publishedAt(), duplicate);
    }

    private DiagnosisView diagnosisView(Diagnosis diagnosis) {
        return new DiagnosisView(diagnosis.id(), diagnosis.templateVersionId(), diagnosis.structuredData(),
                diagnosis.microscopicDescription(), diagnosis.diagnosisText(), diagnosis.comment(), diagnosis.version(),
                diagnosis.updatedAt());
    }

    private TemplateVersionView templateVersionView(DiagnosisTemplateVersion version) {
        return new TemplateVersionView(version.id(), version.templateId(), version.versionNo(), version.schemaDefinition(),
                version.status(), version.publishedAt());
    }

    private TechnicalOrderView technicalOrderView(OrderSnapshot snapshot) {
        return new TechnicalOrderView(snapshot.order().id(), snapshot.order().orderNo(), snapshot.derivedStatus(),
                snapshot.order().requiredBeforeSignOut(), snapshot.blocking(), snapshot.order().version(),
                snapshot.items().stream().map(this::technicalItemView).toList());
    }

    private TechnicalOrderItemView technicalItemView(ItemSnapshot snapshot) {
        return new TechnicalOrderItemView(snapshot.item().id(), snapshot.item().project().code(),
                snapshot.item().project().name(), snapshot.item().quantity(), snapshot.status().name(),
                snapshot.expectedCount(), snapshot.completedCount(), snapshot.targets().stream()
                        .map(target -> new TechnicalTargetView(target.target().id(), target.target().targetType().name(),
                                target.target().targetId(), target.target().displayCode())).toList(),
                snapshot.outputs().stream().map(output -> new TechnicalOutputView(output.kind().name(), output.outputId(),
                        output.occurrenceNo())).toList(), snapshot.result() == null ? null
                                : new TechnicalResultView(snapshot.result().id(), snapshot.result().data(),
                                        snapshot.result().version(), snapshot.result().enteredAt()));
    }

    private ResponsibilityView responsibilityView(ResponsibilityUnit responsibility) {
        return new ResponsibilityView(responsibility.id(), responsibility.role(), responsibility.doctorId(),
                responsibility.sequence(), responsibility.assignmentSource(), responsibility.assignmentReason(),
                responsibility.acceptedAt(), responsibility.completedAt(), responsibility.endedAt(),
                responsibility.endReason(), responsibility.version(), responsibility.isCurrent());
    }

    private MaterialTreeResult materialTree(Case pathologyCase, List<MaterialTreeRow> rows) {
        Map<UUID, SpecimenNodeBuilder> specimens = new LinkedHashMap<>();
        for (MaterialTreeRow row : rows) {
            SpecimenNodeBuilder specimen = specimens.computeIfAbsent(row.specimenId(), ignored ->
                    new SpecimenNodeBuilder(row.specimenId(), row.specimenNo(), row.specimenCode(), row.specimenKindCode()));
            if (row.blockId() != null) {
                BlockNodeBuilder block = specimen.blocks.computeIfAbsent(row.blockId(), ignored ->
                        new BlockNodeBuilder(row.blockId(), row.blockCode(), row.blockType()));
                if (row.slideId() != null) {
                    block.slides.add(slideNode(row));
                }
            } else if (row.slideId() != null) {
                specimen.directSlides.add(slideNode(row));
            }
        }
        List<SpecimenNode> specimenNodes = specimens.values().stream().map(SpecimenNodeBuilder::build).toList();
        int required = 0;
        int completed = 0;
        for (SpecimenNode specimen : specimenNodes) {
            for (BlockNode block : specimen.blocks()) {
                for (SlideNode slide : block.slides()) {
                    if ("INITIAL".equals(slide.sourceContextType()) && slide.required()) {
                        required++; if (slide.completed()) completed++;
                    }
                }
            }
            for (SlideNode slide : specimen.directSlides()) {
                if ("INITIAL".equals(slide.sourceContextType()) && slide.required()) {
                    required++; if (slide.completed()) completed++;
                }
            }
        }
        return new MaterialTreeResult(pathologyCase.id(), pathologyCase.caseNo(), pathologyCase.businessTypeCode(),
                specimenNodes, required, completed, required > 0 && required == completed);
    }

    private static SlideNode slideNode(MaterialTreeRow row) {
        return new SlideNode(row.slideId(), row.slideCode(), row.slideType(), row.sourceContextType(), row.completedAt(),
                row.completedAt() != null, Boolean.TRUE.equals(row.required()), row.concurrencyVersion());
    }

    private record SpecimenNodeBuilder(UUID id, String specimenNo, String specimenCode, String specimenKindCode,
            Map<UUID, BlockNodeBuilder> blocks, List<SlideNode> directSlides) {
        private SpecimenNodeBuilder(UUID id, String specimenNo, String specimenCode, String specimenKindCode) {
            this(id, specimenNo, specimenCode, specimenKindCode, new LinkedHashMap<>(), new ArrayList<>());
        }
        private SpecimenNode build() { return new SpecimenNode(id, specimenNo, specimenCode, specimenKindCode,
                blocks.values().stream().map(BlockNodeBuilder::build).toList(), directSlides); }
    }
    private record BlockNodeBuilder(UUID id, String blockCode, String blockType, List<SlideNode> slides) {
        private BlockNodeBuilder(UUID id, String blockCode, String blockType) { this(id, blockCode, blockType, new ArrayList<>()); }
        private BlockNode build() { return new BlockNode(id, blockCode, blockType, slides); }
    }

    public record AssignDiagnosisCommand(UUID caseId, String doctorId, String reason, String idempotencyKey) { }
    public record SelfClaimCommand(UUID caseId, String idempotencyKey) { }
    public record ReassignDiagnosisCommand(UUID caseId, String doctorId, String reason, String idempotencyKey) { }
    public record SaveDiagnosisCommand(String structuredData, String microscopicDescription, String diagnosisText,
            String comment, long expectedVersion, String idempotencyKey) { }
    public record CompleteResponsibilityCommand(UUID responsibilityId, long responsibilityExpectedVersion,
            String structuredData, String microscopicDescription, String diagnosisText, String comment,
            long diagnosisExpectedVersion, ResponsibilityRole nextRole, String nextDoctorId, String nextReason,
            String idempotencyKey) { }
    public record CreateTemplateCommand(String code, String name, UUID businessTypeId, String scope,
            String idempotencyKey) { }
    public record CreateTemplateVersionCommand(UUID templateId, String schemaDefinition, String idempotencyKey) { }

    public record AssignmentResult(UUID diagnosisId, UUID caseId, UUID responsibilityId, ResponsibilityRole role,
            String doctorId, Integer sequence, long diagnosisVersion, boolean duplicate) { }
    public record DiagnosisResult(UUID diagnosisId, UUID caseId, UUID templateVersionId, String structuredData,
            String microscopicDescription, String diagnosisText, String comment, long version, boolean duplicate) { }
    public record ResponsibilityCompletionResult(UUID diagnosisId, UUID completedResponsibilityId,
            UUID nextResponsibilityId, long diagnosisVersion, boolean readyForSignOut, boolean duplicate) { }
    public record TemplateResult(UUID templateId, String code, String name, long version, boolean duplicate) { }
    public record TemplateVersionResult(UUID versionId, UUID templateId, int versionNo, String schemaDefinition,
            String status, Instant publishedAt, boolean duplicate) { }

    public record DiagnosisWorkspaceResult(CaseSummary caseSummary, ApplicationSummary application,
            PatientSnapshot patient, MaterialTreeResult materialTree, DiagnosisView diagnosis,
            TemplateVersionView templateVersion, List<ResponsibilityView> responsibilityChain,
            ResponsibilityView currentResponsibility, Actions actions, List<TechnicalOrderView> technicalOrders,
            int blockingTechnicalOrderCount, Placeholder technicalOrder,
            Placeholder report, List<V2ReportApplicationService.ReportView> reports,
            List<String> blockingReasons, List<DigitalSlideView> digitalSlides, Instant refreshedAt) { }
    public record CaseSummary(UUID caseId, String pathologyNo, String businessTypeCode, String lifecycle) { }
    public record ApplicationSummary(String applicationItemCode, String sourceSystemCode, String externalApplicationId) { }
    public record PatientSnapshot(String patientReference, String visitReference) { }
    public record DiagnosisView(UUID diagnosisId, UUID templateVersionId, String structuredData,
            String microscopicDescription, String diagnosisText, String comment, long version, Instant updatedAt) { }
    public record TemplateVersionView(UUID versionId, UUID templateId, int versionNo, String schemaDefinition,
            String status, Instant publishedAt) { }
    public record ResponsibilityView(UUID responsibilityId, ResponsibilityRole role, String doctorId, int sequence,
            AssignmentSource assignmentSource, String assignmentReason, Instant acceptedAt, Instant completedAt,
            Instant endedAt, String endReason, long version, boolean current) { }
    public record Actions(boolean canClaim, boolean canAssign, boolean canCompleteInitial, boolean canCompleteReview,
            boolean canCompleteAudit, boolean canReassign, boolean readyForSignOut,
            boolean canCreateTechnicalOrder, boolean canPreview, boolean canSignOut,
            boolean canWithdraw, boolean canSupplement) { }
    public record TechnicalOrderView(UUID orderId, String orderNo, TechnicalOrderStatus status,
            boolean requiredBeforeSignOut, boolean blocking, long version, List<TechnicalOrderItemView> items) { }
    public record TechnicalOrderItemView(UUID itemId, String projectCode, String projectName, int quantity,
            String status, int expectedCount, int completedCount, List<TechnicalTargetView> targets,
            List<TechnicalOutputView> outputs, TechnicalResultView result) { }
    public record TechnicalTargetView(UUID targetId, String targetType, UUID targetObjectId, String displayCode) { }
    public record TechnicalOutputView(String outputKind, UUID outputId, int occurrenceNo) { }
    public record TechnicalResultView(UUID resultId, String resultData, long version, Instant enteredAt) { }
    public record DigitalSlideView(UUID digitalSlideId, UUID blockId, UUID slideId, String statusCode,
            String viewerReference, String sourcePlatform) { }
    public record Placeholder(String kind, String status) { }
    public record PublicPoolEntry(UUID caseId, String pathologyNo, String businessTypeCode) { }
    public record MaterialTreeResult(UUID caseId, String caseNo, String businessTypeCode,
            List<SpecimenNode> specimens, int initialRequiredCount, int initialCompletedCount,
            boolean initialProductionComplete) { }
    public record SpecimenNode(UUID specimenId, String specimenNo, String specimenCode, String specimenKindCode,
            List<BlockNode> blocks, List<SlideNode> directSlides) { }
    public record BlockNode(UUID blockId, String blockCode, String blockType, List<SlideNode> slides) { }
    public record SlideNode(UUID slideId, String slideCode, String slideType, String sourceContextType,
            Instant completedAt, boolean completed, boolean required, long concurrencyVersion) { }
}
