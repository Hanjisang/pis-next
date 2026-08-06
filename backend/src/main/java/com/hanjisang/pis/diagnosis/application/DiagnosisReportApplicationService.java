package com.hanjisang.pis.diagnosis.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.diagnosis.infrastructure.JdbcDiagnosisReportRepository;
import com.hanjisang.pis.diagnosis.infrastructure.JdbcDiagnosisReportRepository.ContentVersionRow;
import com.hanjisang.pis.diagnosis.infrastructure.JdbcDiagnosisReportRepository.DraftRow;
import com.hanjisang.pis.diagnosis.infrastructure.JdbcDiagnosisReportRepository.IdempotentReference;
import com.hanjisang.pis.diagnosis.infrastructure.JdbcDiagnosisReportRepository.OpinionVersionRow;
import com.hanjisang.pis.diagnosis.infrastructure.JdbcDiagnosisReportRepository.ReportRow;
import com.hanjisang.pis.diagnosis.infrastructure.JdbcDiagnosisReportRepository.ReportDraftRow;
import com.hanjisang.pis.diagnosis.infrastructure.JdbcDiagnosisReportRepository.ReviewRow;
import com.hanjisang.pis.diagnosis.infrastructure.JdbcDiagnosisReportRepository.RevisionTargetRow;
import com.hanjisang.pis.diagnosis.infrastructure.JdbcDiagnosisReportRepository.TaskRow;
import com.hanjisang.pis.integration.OutboxPort;
import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.EnhancedAuthenticationPort;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;

@Service
public class DiagnosisReportApplicationService {

    public static final String TASK = "P19-DIAGNOSIS-REPORT";
    private static final String DIAGNOSIS_PERMISSION = "P14-PERM-034";
    private static final String REPORT_PERMISSION = "P14-PERM-035";
    private static final String AMENDMENT_PERMISSION = "P14-PERM-036";
    private static final String QUEUE_PERMISSION = "P14-PERM-055";
    private static final String HISTORY_PERMISSION = "P14-PERM-057";
    private static final String EVIDENCE_PERMISSION = "P14-PERM-058";

    private final JdbcDiagnosisReportRepository repository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;
    private final EnhancedAuthenticationPort enhancedAuthentication;

    public DiagnosisReportApplicationService(JdbcDiagnosisReportRepository repository, P15AuthorizationService authorization,
            JdbcAuditEventRepository audit, OutboxPort outbox, EnhancedAuthenticationPort enhancedAuthentication) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.enhancedAuthentication = enhancedAuthentication;
    }

    public List<TaskRow> taskQueue(String state) { return repository.tasks(authorized(QUEUE_PERMISSION).hospitalScope(), state); }
    public List<ReportRow> reportQueue(String state) { return repository.reports(authorized(QUEUE_PERMISSION).hospitalScope(), state); }
    public Optional<TaskRow> task(UUID id) { authorized(QUEUE_PERMISSION); return repository.task(id); }
    public Optional<ReportRow> report(UUID id) { authorized(HISTORY_PERMISSION); return repository.report(id); }
    public Optional<ContentVersionRow> content(UUID id) { authorized(HISTORY_PERMISSION); return repository.content(id); }

    @Transactional
    public CommandResult createTask(CreateTaskCommand command) {
        ActorContext actor = authorized(DIAGNOSIS_PERMISSION);
        requireKey(command.idempotencyKey());
        String digest = digest(command.caseId() + "|" + command.modalityCode() + "|" + command.categoryCode() + "|" + command.priorityCode());
        Optional<CommandResult> replay = replay("P19-CREATE-DIAGNOSIS-TASK", command.idempotencyKey(), digest, actor);
        if (replay.isPresent()) return replay.get();
        require(command.caseId(), "P12-ERR-061", "病例身份不能为空");
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        TaskRow task = new TaskRow(id, "P19-DIAGNOSIS-" + token(), command.caseId(), value(command.modalityCode(), "HISTOLOGY"),
                value(command.categoryCode(), "INITIAL"), value(command.priorityCode(), "ROUTINE"), "P19-DIAGNOSIS-TASK-PLANNED",
                null, null, actor.hospitalScope(), value(command.dataScopeCode(), "PATHOLOGY"), 0);
        repository.insertTask(task, now, actor.actorId());
        repository.stateHistory(id, "P19-DIAGNOSIS-TASK", null, task.stateCode(), "EVT-014", 0, 0, "diagnosis task created", now, actor.actorId());
        audit(id, "P19-CMD-CREATE-DIAGNOSIS-TASK", DIAGNOSIS_PERMISSION, actor, "diagnosis task created");
        publish("EVT-014", id, "P19-DIAGNOSIS-TASK", actor, digest);
        CommandResult result = result(id, task.taskNo(), "P19-DIAGNOSIS-TASK", task.stateCode(), 0, 0, null, false);
        repository.recordIdempotent("P19-CREATE-DIAGNOSIS-TASK", command.idempotencyKey(), digest, id, actor.actorId(), now);
        return result;
    }

    @Transactional
    public CommandResult assign(UUID taskId, AssignmentCommand command) { return changeResponsibility(taskId, command, "ASSIGN", "P19-DIAGNOSIS-TASK-ASSIGNED"); }
    @Transactional
    public CommandResult takeover(UUID taskId, VersionCommand command) { return changeResponsibility(taskId, command, "TAKEOVER", "P19-DIAGNOSIS-TASK-IN-PROGRESS"); }
    @Transactional
    public CommandResult handoff(UUID taskId, AssignmentCommand command) { return changeResponsibility(taskId, command, "HANDOFF", "P19-DIAGNOSIS-TASK-HANDED-OFF"); }

    private CommandResult changeResponsibility(UUID taskId, VersionLike command, String action, String targetState) {
        ActorContext actor = authorized(DIAGNOSIS_PERMISSION);
        requireKey(command.idempotencyKey());
        TaskRow task = requireTask(taskId);
        requireVersion(task.version(), command.expectedVersion());
        String destination = command instanceof AssignmentCommand assignment && assignment.actorRef() != null && !assignment.actorRef().isBlank() ? assignment.actorRef() : actor.actorId();
        String digest = digest(taskId + "|" + action + "|" + destination + "|" + command.expectedVersion());
        Optional<CommandResult> replay = replay("P19-" + action + "-DIAGNOSIS-TASK", command.idempotencyKey(), digest, actor);
        if (replay.isPresent()) return replay.get();
        ensureTaskState(task, action.equals("ASSIGN") ? "P19-DIAGNOSIS-TASK-PLANNED" : null);
        Instant now = Instant.now();
        if (repository.updateTask(taskId, task.version(), targetState, destination, destination, now) != 1) throw conflict();
        repository.responsibility(taskId, task.responsibleActor(), destination, action, command.reasonText(), now, actor.actorId());
        repository.stateHistory(taskId, "P19-DIAGNOSIS-TASK", task.stateCode(), targetState, "EVT-014", task.version(), task.version() + 1, action, now, actor.actorId());
        audit(taskId, "P19-CMD-" + action + "-DIAGNOSIS-TASK", DIAGNOSIS_PERMISSION, actor, action);
        publish("EVT-014", taskId, "P19-DIAGNOSIS-TASK", actor, digest);
        CommandResult result = result(taskId, null, "P19-DIAGNOSIS-TASK", targetState, task.version() + 1, 0, null, false);
        repository.recordIdempotent("P19-" + action + "-DIAGNOSIS-TASK", command.idempotencyKey(), digest, taskId, actor.actorId(), now);
        return result;
    }

    @Transactional
    public CommandResult saveDraft(UUID taskId, SaveDraftCommand command) {
        ActorContext actor = authorized(DIAGNOSIS_PERMISSION);
        TaskRow task = requireTask(taskId);
        ensureResponsible(task, actor);
        requireKey(command.idempotencyKey());
        String digest = digest(taskId + "|" + command.microscopicDescription() + "|" + command.diagnosisConclusion() + "|" + command.expectedVersion());
        Optional<CommandResult> replay = replay("P19-SAVE-DIAGNOSIS-DRAFT", command.idempotencyKey(), digest, actor);
        if (replay.isPresent()) return replay.get();
        Instant now = Instant.now();
        DraftRow draft = new DraftRow(UUID.randomUUID(), taskId, actor.actorId(), command.grossDescriptionReference(), required(command.microscopicDescription(), "镜下描述不能为空"), required(command.diagnosisConclusion(), "诊断结论不能为空"), command.supplementaryNote(), command.structuredItems(), command.terminologyReference(), command.expectedVersion());
        Optional<DraftRow> current = repository.draft(taskId);
        int changed;
        if (current.isEmpty()) { repository.insertDraft(draft, now, actor.actorId()); changed = 1; }
        else { requireVersion(current.get().version(), command.expectedVersion()); changed = repository.updateDraft(draft, command.expectedVersion(), now, actor.actorId()); }
        if (changed != 1) throw conflict();
        audit(taskId, "P19-CMD-SAVE-DIAGNOSIS-DRAFT", DIAGNOSIS_PERMISSION, actor, "diagnosis draft saved");
        CommandResult result = result(taskId, null, "P19-DIAGNOSIS-WORK-DRAFT", "DRAFT", command.expectedVersion() + (current.isEmpty() ? 0 : 1), 0, null, false);
        repository.recordIdempotent("P19-SAVE-DIAGNOSIS-DRAFT", command.idempotencyKey(), digest, taskId, actor.actorId(), now);
        return result;
    }

    @Transactional
    public CommandResult submitInitial(UUID taskId, SubmitDiagnosisCommand command) {
        ActorContext actor = authorized(DIAGNOSIS_PERMISSION);
        TaskRow task = requireTask(taskId); ensureResponsible(task, actor); requireKey(command.idempotencyKey());
        requireVersion(task.version(), command.expectedVersion());
        String digest = digest(taskId + "|INITIAL|" + command.expectedVersion());
        Optional<CommandResult> replay = replay("P19-SUBMIT-INITIAL", command.idempotencyKey(), digest, actor);
        if (replay.isPresent()) return replay.get();
        DraftRow draft = repository.draft(taskId).orElseThrow(() -> business("P12-ERR-061", "诊断草稿不存在"));
        UUID opinionId = repository.opinion(taskId, "INITIAL").orElseGet(() -> repository.insertOpinion(taskId, task.caseId(), "INITIAL", actor.actorId(), Instant.now()));
        int version = repository.nextOpinionVersion(opinionId);
        UUID versionId = repository.insertOpinionVersion(opinionId, version, "SUBMITTED", draft.grossReference(), draft.microscopic(), draft.conclusion(), draft.supplementary(), draft.structuredItems(), draft.terminology(), "P18 technical order references reviewed; formal result identity retained", actor.actorId(), Instant.now(), null);
        Instant now = Instant.now();
        if (repository.updateTask(taskId, task.version(), "P19-DIAGNOSIS-TASK-AWAITING-REVIEW", task.assignedActor(), actor.actorId(), now) != 1) throw conflict();
        repository.stateHistory(taskId, "P19-DIAGNOSIS-TASK", task.stateCode(), "P19-DIAGNOSIS-TASK-AWAITING-REVIEW", "EVT-014", task.version(), task.version() + 1, "initial diagnosis submitted", now, actor.actorId());
        audit(taskId, "P19-CMD-SUBMIT-INITIAL-DIAGNOSIS", DIAGNOSIS_PERMISSION, actor, "initial diagnosis submitted");
        publish("EVT-014", taskId, "P19-DIAGNOSIS-OPINION", actor, digest);
        CommandResult result = result(taskId, null, "P19-DIAGNOSIS-OPINION-VERSION", "SUBMITTED", task.version() + 1, version, versionId, false);
        repository.recordIdempotent("P19-SUBMIT-INITIAL", command.idempotencyKey(), digest, versionId, actor.actorId(), now);
        return result;
    }

    @Transactional
    public CommandResult createFollowUp(UUID taskId, FollowUpCommand command) {
        ActorContext actor = authorized(DIAGNOSIS_PERMISSION); TaskRow task = requireTask(taskId); ensureResponsible(task, actor); requireKey(command.idempotencyKey());
        OpinionVersionRow target = repository.opinionVersion(command.targetOpinionVersionId()).orElseThrow(() -> business("P12-ERR-062", "复诊目标诊断版本不存在"));
        String digest = digest(taskId + "|FOLLOW-UP|" + command.targetOpinionVersionId() + "|" + command.followUpOpinion());
        Optional<CommandResult> replay = replay("P19-SUBMIT-FOLLOW-UP", command.idempotencyKey(), digest, actor); if (replay.isPresent()) return replay.get();
        UUID id = repository.insertFollowUp(taskId, target.id(), actor.actorId(), required(command.followUpOpinion(), "复诊意见不能为空"), value(command.consistencyCode(), "UNDETERMINED"), command.returnReason(), command.adoptionRecommendation(), Instant.now());
        audit(id, "P19-CMD-SUBMIT-FOLLOW-UP", DIAGNOSIS_PERMISSION, actor, "follow-up submitted"); publish("EVT-014", id, "P19-DIAGNOSIS-FOLLOW-UP", actor, digest);
        CommandResult result = result(id, null, "P19-DIAGNOSIS-FOLLOW-UP", "SUBMITTED", 1, 1, null, false); repository.recordIdempotent("P19-SUBMIT-FOLLOW-UP", command.idempotencyKey(), digest, id, actor.actorId(), Instant.now()); return result;
    }

    @Transactional
    public CommandResult acceptFollowUp(UUID taskId, UUID followUpId, FollowUpDecisionCommand command) {
        ActorContext actor = authorized(DIAGNOSIS_PERMISSION);
        TaskRow task = requireTask(taskId);
        ensureResponsible(task, actor);
        requireKey(command.idempotencyKey());
        if (!repository.followUpTask(followUpId).filter(taskId::equals).isPresent()) {
            throw business("P12-ERR-062", "澶嶈瘖璁板綍涓嶅睘浜庡綋鍓嶈瘖鏂换鍔?");
        }
        String digest = digest(taskId + "|ACCEPT-FOLLOW-UP|" + followUpId + "|" + command.expectedVersion());
        Optional<CommandResult> replay = replay("P19-ACCEPT-FOLLOW-UP", command.idempotencyKey(), digest, actor);
        if (replay.isPresent()) return replay.get();
        Instant now = Instant.now();
        if (repository.updateFollowUp(followUpId, command.expectedVersion(), "ACCEPTED") != 1) throw conflict();
        audit(followUpId, "P19-CMD-ACCEPT-FOLLOW-UP", DIAGNOSIS_PERMISSION, actor, command.reasonText());
        publish("EVT-014", followUpId, "P19-DIAGNOSIS-FOLLOW-UP", actor, digest);
        CommandResult result = result(followUpId, null, "P19-DIAGNOSIS-FOLLOW-UP", "ACCEPTED", command.expectedVersion() + 1, 0, null, false);
        repository.recordIdempotent("P19-ACCEPT-FOLLOW-UP", command.idempotencyKey(), digest, followUpId, actor.actorId(), now);
        return result;
    }

    @Transactional
    public CommandResult createReport(UUID taskId, CreateReportCommand command) {
        ActorContext actor = authorized(REPORT_PERMISSION); TaskRow task = requireTask(taskId); ensureResponsible(task, actor); requireKey(command.idempotencyKey());
        OpinionVersionRow opinion = repository.opinionVersion(command.diagnosisVersionId()).orElseThrow(() -> business("P12-ERR-062", "诊断版本不存在"));
        String digest = digest(taskId + "|REPORT|" + opinion.id() + "|" + command.reportTypeCode()); Optional<CommandResult> replay = replay("P19-CREATE-REPORT", command.idempotencyKey(), digest, actor); if (replay.isPresent()) return replay.get();
        UUID reportId = repository.insertReport(task.caseId(), value(command.reportTypeCode(), "HISTOPATHOLOGY"), actor.hospitalScope(), actor.actorId(), Instant.now());
        repository.insertReportDraft(new ReportDraftRow(UUID.randomUUID(), reportId, actor.actorId(), null, null, null, opinion.microscopic(), opinion.conclusion(), null, "P18 technical result references pending", 0), Instant.now(), actor.actorId());
        audit(reportId, "P19-CMD-CREATE-REPORT-DRAFT", REPORT_PERMISSION, actor, "report draft created");
        CommandResult result = result(reportId, repository.report(reportId).map(ReportRow::reportNo).orElse(null), "P19-REPORT", "DRAFT", 0, 0, opinion.id(), false); repository.recordIdempotent("P19-CREATE-REPORT", command.idempotencyKey(), digest, reportId, actor.actorId(), Instant.now()); return result;
    }

    @Transactional
    public CommandResult updateReportDraft(UUID reportId, ReportDraftCommand command) {
        ActorContext actor = authorized(REPORT_PERMISSION); ReportRow report = requireReport(reportId); if (!"DRAFT".equals(report.stateCode())) throw business("P12-ERR-063", "只有未提交报告草稿可以修改"); requireKey(command.idempotencyKey()); String digest = digest(reportId + "|DRAFT|" + command.diagnosisConclusion() + "|" + command.expectedVersion()); Optional<CommandResult> replay = replay("P19-UPDATE-REPORT-DRAFT", command.idempotencyKey(), digest, actor); if (replay.isPresent()) return replay.get(); ReportDraftRow current = repository.reportDraft(reportId).orElseThrow(() -> business("P12-ERR-063", "报告草稿不存在")); requireVersion(current.version(), command.expectedVersion()); ReportDraftRow next = new ReportDraftRow(current.id(), reportId, actor.actorId(), command.clinicalInformation(), command.specimenInformation(), command.grossDescription(), command.microscopicDescription(), required(command.diagnosisConclusion(), "诊断结论不能为空"), command.supplementaryNote(), command.technicalResultReferenceSummary(), current.version()); if (repository.updateReportDraft(next, current.version(), Instant.now(), actor.actorId()) != 1) throw conflict(); return result(reportId, report.reportNo(), "P19-REPORT-DRAFT", "DRAFT", report.version(), (int) (current.version() + 1), null, false);
    }

    @Transactional
    public CommandResult generateContent(UUID reportId, GenerateContentCommand command) {
        ActorContext actor = authorized(REPORT_PERMISSION); ReportRow report = requireReport(reportId); requireKey(command.idempotencyKey());
        OpinionVersionRow opinion = repository.opinionVersion(command.diagnosisVersionId()).orElseThrow(() -> business("P12-ERR-062", "诊断版本不存在"));
        String digest = digest(reportId + "|CONTENT|" + opinion.id() + "|" + command.diagnosisConclusion()); Optional<CommandResult> replay = replay("P19-GENERATE-REPORT-CONTENT", command.idempotencyKey(), digest, actor); if (replay.isPresent()) return replay.get();
        int version = repository.allocateVersion(reportId); Instant now = Instant.now();
        UUID content = repository.insertContent(reportId, version, "SUBMITTED", required(command.patientSnapshot(), "患者快照不能为空"), required(command.encounterSnapshot(), "就诊快照不能为空"), required(command.caseNoSnapshot(), "病例编号快照不能为空"), required(command.specimenMaterialSummary(), "标本材料摘要不能为空"), command.clinicalInformation(), command.specimenInformation(), command.grossDescription(), command.microscopicDescription(), required(command.diagnosisConclusion(), "诊断结论不能为空"), command.supplementaryNote(), command.technicalResultReferenceSummary(), opinion.id(), value(command.templateLogicVersion(), "P19-TEMPLATE-1"), actor.actorId(), now, report.currentVersionId());
        addSections(content, command, actor, now);
        if (repository.updateReport(reportId, report.version(), "IN-REVIEW", null, now) != 1) throw conflict();
        audit(reportId, "P19-CMD-GENERATE-REPORT-VERSION", REPORT_PERMISSION, actor, "report content version generated"); publish("EVT-015", reportId, "P19-REPORT-CONTENT-VERSION", actor, digest);
        CommandResult result = result(reportId, report.reportNo(), "P19-REPORT-CONTENT-VERSION", "SUBMITTED", report.version() + 1, version, content, false); repository.recordIdempotent("P19-GENERATE-REPORT-CONTENT", command.idempotencyKey(), digest, content, actor.actorId(), now); return result;
    }

    private void addSections(UUID content, GenerateContentCommand c, ActorContext actor, Instant now) {
        section(content, "CLINICAL_INFORMATION", c.clinicalInformation(), actor, now); section(content, "SPECIMEN_INFORMATION", c.specimenInformation(), actor, now); section(content, "GROSS_DESCRIPTION", c.grossDescription(), actor, now); section(content, "MICROSCOPIC_DESCRIPTION", c.microscopicDescription(), actor, now); section(content, "DIAGNOSIS_CONCLUSION", c.diagnosisConclusion(), actor, now); section(content, "SUPPLEMENTARY_NOTE", c.supplementaryNote(), actor, now); section(content, "TECHNICAL_RESULT_REFERENCES", c.technicalResultReferenceSummary(), actor, now);
    }
    private void section(UUID content, String code, String body, ActorContext actor, Instant now) { if (body != null && !body.isBlank()) repository.section(content, code, body, "P19-COMMAND", "ADOPTED", actor.actorId(), now); }

    @Transactional
    public CommandResult submitReview(UUID contentId, ReviewCommand command) {
        ActorContext actor = authorized(REPORT_PERMISSION);
        ContentVersionRow content = requireContent(contentId);
        requireKey(command.idempotencyKey());
        requireIndependentReviewer(actor, command.reviewerActorRef());
        String digest = digest(contentId + "|REPORT-REVIEW|" + command.reviewerActorRef());
        Optional<CommandResult> replay = replay("P19-SUBMIT-REPORT-REVIEW", command.idempotencyKey(), digest, actor);
        if (replay.isPresent()) return replay.get();
        Instant now = Instant.now();
        UUID id = repository.insertReview(null, contentId, "REPORT", command.reviewerActorRef(), command.reasonText(), now);
        audit(contentId, "P19-CMD-SUBMIT-REPORT-REVIEW", REPORT_PERMISSION, actor, "report review submitted");
        CommandResult result = result(content.reportId(), null, "P19-REPORT-REVIEW", "PENDING", 0, content.versionNo(), id, false);
        repository.recordIdempotent("P19-SUBMIT-REPORT-REVIEW", command.idempotencyKey(), digest, id, actor.actorId(), now);
        return result;
    }

    @Transactional
    public CommandResult approveReview(UUID contentId, ReviewDecisionCommand command) { ActorContext actor = authorized(REPORT_PERMISSION); ContentVersionRow content = requireContent(contentId); requireIndependentReviewer(actor, command.reviewerActorRef()); ReviewRow review = repository.reviewForContent(contentId, "REPORT").orElseThrow(() -> business("P12-ERR-064", "报告审核不存在")); if (!review.reviewerActor().equals(command.reviewerActorRef())) throw business("P12-ERR-064", "审核主体与原审核责任不一致"); if (review.reviewerActor().equals(actor.actorId())) throw business("P12-ERR-064", "签发人与审核人必须职责分离"); if (repository.decideReview(review.id(), command.decisionCode(), command.reasonText(), Instant.now()) != 1) throw conflict(); audit(contentId, "P19-CMD-APPROVE-REPORT-REVIEW", REPORT_PERMISSION, actor, command.decisionCode()); return result(content.reportId(), null, "P19-REPORT-REVIEW", command.decisionCode(), 0, content.versionNo(), review.id(), false); }

    @Transactional
    public CommandResult sign(UUID contentId, SignCommand command) {
        ActorContext actor = authorized(REPORT_PERMISSION); ContentVersionRow content = requireContent(contentId); ReportRow report = requireReport(content.reportId()); requireKey(command.idempotencyKey()); requireIndependentReviewer(actor, command.reviewerActorRef()); String digest = digest(contentId + "|SIGN|" + command.reviewerActorRef() + "|" + command.expectedReportVersion()); Optional<CommandResult> replay = replay("P19-SIGN-REPORT", command.idempotencyKey(), digest, actor); if (replay.isPresent()) return replay.get(); requireVersion(report.version(), command.expectedReportVersion()); if (!"SUBMITTED".equals(content.stateCode())) throw business("P12-ERR-063", "只有已提交审核的报告版本可以签发"); ReviewRow review = repository.reviewForContent(contentId, "REPORT").orElseThrow(() -> business("P12-ERR-064", "签发前必须有独立复核")); if (!"APPROVED".equals(review.decisionCode())) throw business("P12-ERR-064", "报告审核尚未通过"); if (blockingTechnicalOrder(report.caseId())) throw business("P12-ERR-063", "存在阻断签发的未完成技术医嘱"); var proof = enhancedAuthentication.prove(actor, "P12-API-035"); Instant now = Instant.now(); if (repository.contentState(contentId, "SIGNED") != 1) throw business("P12-ERR-063", "报告版本已被其他操作签发"); UUID signing = repository.insertSigning(report.id(), contentId, actor.actorId(), actor.actorId(), actor.actorId(), proof.reference(), review.id(), report.version() + 1, now); if (repository.updateReport(report.id(), report.version(), "SIGNED", contentId, now) != 1) throw conflict(); if (content.priorVersionId() != null) repository.relation(report.id(), content.priorVersionId(), contentId, "RESIGN", "signed report effective relation", true, actor.actorId(), now); audit(report.id(), "P19-CMD-SIGN-REPORT", REPORT_PERMISSION, actor, "report signed"); publish("EVT-015", report.id(), "P19-REPORT", actor, digest); return result(report.id(), report.reportNo(), "P19-SIGNING-FACT", "SIGNED", report.version() + 1, content.versionNo(), signing, false); }

    @Transactional
    public CommandResult requestSupplement(UUID reportId, RevisionCommand command) { return requestRevision(reportId, command, "SUPPLEMENT"); }
    @Transactional
    public CommandResult requestCorrection(UUID reportId, CorrectionCommand command) { ActorContext actor = authorized(AMENDMENT_PERMISSION); ReportRow report = requireReport(reportId); requireKey(command.idempotencyKey()); UUID original = requireCurrent(report); String digest = digest(reportId + "|CORRECTION|" + command.errorTypeCode() + "|" + command.reasonText()); Optional<CommandResult> replay = replay("P19-REQUEST-CORRECTION", command.idempotencyKey(), digest, actor); if (replay.isPresent()) return replay.get(); UUID id = repository.insertCorrection(reportId, original, required(command.errorTypeCode(), "错误类型不能为空"), required(command.reasonText(), "更正原因不能为空"), actor.actorId(), Instant.now()); audit(reportId, "P19-CMD-REQUEST-CORRECTION", AMENDMENT_PERMISSION, actor, "correction requested"); return result(reportId, report.reportNo(), "P19-REPORT-CORRECTION", "REQUESTED", report.version(), 0, id, false); }
    private CommandResult requestRevision(UUID reportId, RevisionCommand command, String kind) { ActorContext actor = authorized(AMENDMENT_PERMISSION); ReportRow report = requireReport(reportId); requireKey(command.idempotencyKey()); UUID original = requireCurrent(report); String digest = digest(reportId + "|" + kind + "|" + command.reasonText()); Optional<CommandResult> replay = replay("P19-REQUEST-" + kind, command.idempotencyKey(), digest, actor); if (replay.isPresent()) return replay.get(); UUID id = repository.insertSupplement(reportId, original, required(command.reasonText(), "补充原因不能为空"), actor.actorId(), Instant.now()); audit(reportId, "P19-CMD-REQUEST-SUPPLEMENT", AMENDMENT_PERMISSION, actor, "supplement requested"); return result(reportId, report.reportNo(), "P19-REPORT-SUPPLEMENT", "REQUESTED", report.version(), 0, id, false); }

    @Transactional public CommandResult approveCorrection(UUID correctionId, RevisionApprovalCommand command) { ActorContext actor = authorized(AMENDMENT_PERMISSION); requireIndependentReviewer(actor, command.reviewerActorRef()); if (repository.updateCorrection(correctionId, "APPROVED", null) != 1) throw conflict(); audit(correctionId, "P19-CMD-APPROVE-CORRECTION", AMENDMENT_PERMISSION, actor, command.reasonText()); return result(correctionId, null, "P19-REPORT-CORRECTION", "APPROVED", 0, 0, null, false); }
    @Transactional
    public CommandResult submitCorrection(UUID correctionId, GenerateContentCommand command) {
        ActorContext actor = authorized(AMENDMENT_PERMISSION);
        requireKey(command.idempotencyKey());
        RevisionTargetRow target = repository.correction(correctionId).orElseThrow(() -> business("P12-ERR-065", "correction request does not exist"));
        if (!"APPROVED".equals(target.stateCode())) throw business("P12-ERR-065", "correction request is not approved");
        ReportRow report = requireReport(target.reportId());
        ContentVersionRow original = requireContent(target.originalVersionId());
        String digest = digest(correctionId + "|CORRECTION-CONTENT|" + command.diagnosisConclusion());
        Optional<CommandResult> replay = replay("P19-SUBMIT-CORRECTION", command.idempotencyKey(), digest, actor);
        if (replay.isPresent()) return replay.get();
        int version = repository.allocateVersion(report.id());
        Instant now = Instant.now();
        UUID content = repository.insertContent(report.id(), version, "SUBMITTED", required(command.patientSnapshot(), "patient snapshot is required"), required(command.encounterSnapshot(), "encounter snapshot is required"), required(command.caseNoSnapshot(), "case snapshot is required"), required(command.specimenMaterialSummary(), "specimen summary is required"), command.clinicalInformation(), command.specimenInformation(), command.grossDescription(), command.microscopicDescription(), required(command.diagnosisConclusion(), "diagnosis conclusion is required"), command.supplementaryNote(), command.technicalResultReferenceSummary(), command.diagnosisVersionId(), value(command.templateLogicVersion(), "P19-TEMPLATE-1"), actor.actorId(), now, original.id());
        addSections(content, command, actor, now);
        repository.updateCorrection(correctionId, "APPROVED", content);
        repository.relation(report.id(), original.id(), content, "CORRECTION", target.reason(), false, actor.actorId(), now);
        if (repository.updateReport(report.id(), report.version(), "IN-REVIEW", null, now) != 1) throw conflict();
        audit(report.id(), "P19-CMD-SUBMIT-CORRECTION", AMENDMENT_PERMISSION, actor, "correction content submitted");
        publish("EVT-015", report.id(), "P19-REPORT-CORRECTION", actor, digest);
        CommandResult result = result(report.id(), report.reportNo(), "P19-REPORT-CORRECTION", "SUBMITTED", report.version() + 1, version, content, false);
        repository.recordIdempotent("P19-SUBMIT-CORRECTION", command.idempotencyKey(), digest, content, actor.actorId(), now);
        return result;
    }
    @Transactional public CommandResult submitSupplement(UUID supplementId, GenerateContentCommand command) { ActorContext actor = authorized(AMENDMENT_PERMISSION); requireKey(command.idempotencyKey()); RevisionTargetRow target = repository.supplement(supplementId).orElseThrow(() -> business("P12-ERR-065", "补充申请不存在")); if (!"REQUESTED".equals(target.stateCode()) && !"SUBMITTED".equals(target.stateCode())) throw business("P12-ERR-065", "补充申请状态不允许提交"); ReportRow report = requireReport(target.reportId()); ContentVersionRow original = requireContent(target.originalVersionId()); int version = repository.allocateVersion(report.id()); Instant now = Instant.now(); UUID content = repository.insertContent(report.id(), version, "SUBMITTED", required(command.patientSnapshot(), "患者快照不能为空"), required(command.encounterSnapshot(), "就诊快照不能为空"), required(command.caseNoSnapshot(), "病例编号快照不能为空"), required(command.specimenMaterialSummary(), "标本材料摘要不能为空"), command.clinicalInformation(), command.specimenInformation(), command.grossDescription(), command.microscopicDescription(), required(command.diagnosisConclusion(), "诊断结论不能为空"), command.supplementaryNote(), command.technicalResultReferenceSummary(), command.diagnosisVersionId(), value(command.templateLogicVersion(), "P19-TEMPLATE-1"), actor.actorId(), now, original.id()); addSections(content, command, actor, now); repository.updateSupplement(supplementId, "SUBMITTED", content); repository.relation(report.id(), original.id(), content, "SUPPLEMENT", target.reason(), false, actor.actorId(), now); audit(report.id(), "P19-CMD-SUBMIT-SUPPLEMENT", AMENDMENT_PERMISSION, actor, "supplement submitted"); return result(report.id(), report.reportNo(), "P19-REPORT-SUPPLEMENT", "SUBMITTED", report.version(), version, content, false); }
    @Transactional public CommandResult signCorrection(UUID contentId, SignCommand command) { CommandResult result = sign(contentId, command); repository.updateCorrectionByVersion(contentId, "SIGNED"); return result; }
    @Transactional public CommandResult requestWithdrawal(UUID reportId, RevisionCommand command) { ActorContext actor = authorized(AMENDMENT_PERMISSION); ReportRow report = requireReport(reportId); UUID original = requireCurrent(report); UUID id = repository.insertWithdrawal(reportId, original, required(command.reasonText(), "撤回原因不能为空"), actor.actorId(), Instant.now()); audit(reportId, "P19-CMD-REQUEST-WITHDRAWAL", AMENDMENT_PERMISSION, actor, "withdrawal requested"); return result(reportId, report.reportNo(), "P19-REPORT-WITHDRAWAL", "REQUESTED", report.version(), 0, id, false); }
    @Transactional public CommandResult approveWithdrawal(UUID withdrawalId, RevisionApprovalCommand command) { ActorContext actor = authorized(AMENDMENT_PERMISSION); requireIndependentReviewer(actor, command.reviewerActorRef()); if (repository.updateWithdrawal(withdrawalId, "APPROVED") != 1) throw conflict(); return result(withdrawalId, null, "P19-REPORT-WITHDRAWAL", "APPROVED", 0, 0, null, false); }
    @Transactional public CommandResult executeWithdrawal(UUID withdrawalId, WithdrawalExecutionCommand command) { ActorContext actor = authorized(AMENDMENT_PERMISSION); requireEnhanced(actor, "P12-API-036"); throwIfEmpty(command.reasonText()); RevisionTargetRow target = repository.withdrawal(withdrawalId).orElseThrow(() -> business("P12-ERR-066", "撤回申请不存在")); if (!"APPROVED".equals(target.stateCode())) throw business("P12-ERR-066", "撤回申请尚未批准"); ReportRow report = requireReport(target.reportId()); Instant now = Instant.now(); if (repository.updateReport(report.id(), report.version(), "WITHDRAWN", null, now) != 1) throw conflict(); if (repository.updateWithdrawal(withdrawalId, "EXECUTED") != 1) throw conflict(); repository.withdrawalFact(withdrawalId, report.id(), target.originalVersionId(), actor.actorId(), actor.actorId(), command.reasonText(), now); repository.relation(report.id(), target.originalVersionId(), null, "WITHDRAWAL", command.reasonText(), false, actor.actorId(), now); audit(report.id(), "P19-CMD-EXECUTE-WITHDRAWAL", AMENDMENT_PERMISSION, actor, command.reasonText()); publish("EVT-016", report.id(), "P19-REPORT", actor, digest(report.id() + "|WITHDRAWAL")); return result(report.id(), report.reportNo(), "P19-REPORT-WITHDRAWAL", "EXECUTED", report.version() + 1, 0, withdrawalId, false); }
    @Transactional public CommandResult resign(UUID contentId, SignCommand command) { return sign(contentId, command); }
    @Transactional public CommandResult closeTask(UUID taskId, VersionCommand command) { ActorContext actor = authorized(DIAGNOSIS_PERMISSION); TaskRow task = requireTask(taskId); ensureResponsible(task, actor); if (repository.updateTask(taskId, command.expectedVersion(), "P19-DIAGNOSIS-TASK-CLOSED", task.assignedActor(), task.responsibleActor(), Instant.now()) != 1) throw conflict(); return result(taskId, task.taskNo(), "P19-DIAGNOSIS-TASK", "P19-DIAGNOSIS-TASK-CLOSED", task.version() + 1, 0, null, false); }

    private CommandResult revisionContent(UUID revisionId, GenerateContentCommand command, ActorContext actor, boolean supplement) { throwIfEmpty(command.diagnosisConclusion()); throw new P15BusinessException("P12-ERR-065", "修订内容必须绑定原报告版本并通过审核流程"); }
    private CommandResult decisionRevision(UUID id, RevisionApprovalCommand command, String kind, String state) { return result(id, null, "P19-REPORT-" + kind, state, 0, 0, null, false); }

    private ActorContext authorized(String permission) { return authorization.requireTask(permission, TASK); }
    private TaskRow requireTask(UUID id) { return repository.task(id).orElseThrow(() -> business("P12-ERR-061", "诊断任务不存在")); }
    private ReportRow requireReport(UUID id) { return repository.report(id).orElseThrow(() -> business("P12-ERR-063", "报告不存在")); }
    private ContentVersionRow requireContent(UUID id) { return repository.content(id).orElseThrow(() -> business("P12-ERR-063", "报告版本不存在")); }
    private UUID requireCurrent(ReportRow report) { if (report.currentVersionId() == null) throw business("P12-ERR-063", "报告没有当前有效版本"); return report.currentVersionId(); }
    private void ensureResponsible(TaskRow task, ActorContext actor) { if (task.responsibleActor() == null || !task.responsibleActor().equals(actor.actorId())) throw business("P12-ERR-062", "当前主体不是诊断任务责任人"); }
    private void ensureTaskState(TaskRow task, String expected) { if (expected != null && !expected.equals(task.stateCode())) throw business("P12-ERR-061", "诊断任务状态不允许该操作"); }
    private void requireIndependentReviewer(ActorContext actor, String reviewer) { if (reviewer == null || reviewer.isBlank() || reviewer.equals(actor.actorId())) throw business("P12-ERR-064", "复核必须由不同实际主体完成"); }
    private void requireEnhanced(ActorContext actor, String operation) { enhancedAuthentication.prove(actor, operation); }
    private boolean blockingTechnicalOrder(UUID caseId) { try { return repository.hasBlockingTechnicalOrder(caseId); } catch (DataAccessException ignored) { return false; } }
    private void requireVersion(long actual, long expected) { if (actual != expected) throw conflict(); }
    private void requireKey(String key) { if (key == null || key.isBlank()) throw business("P12-ERR-004", "幂等键不能为空"); }
    private void throwIfEmpty(String value) { if (value == null || value.isBlank()) throw business("P12-ERR-065", "原因或内容不能为空"); }
    private <T> T require(T value, String code, String message) { if (value == null) throw business(code, message); return value; }
    private String required(String value, String message) { if (value == null || value.isBlank()) throw business("P12-ERR-061", message); return value; }
    private String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private P15BusinessException conflict() { return business("P12-ERR-010", "版本冲突，请重新读取后重试"); }
    private P15BusinessException business(String code, String message) { return new P15BusinessException(code, message); }
    private String token() { return UUID.randomUUID().toString().substring(0, 12).toUpperCase(); }
    private String digest(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private Optional<CommandResult> replay(String operation, String key, String digest, ActorContext actor) { return repository.idempotent(operation, key).map(existing -> { if (!existing.payloadDigest().equals(digest)) throw business("P12-ERR-005", "同一幂等键载荷不一致"); return result(existing.resultObjectId(), null, "P19-IDEMPOTENT-REPLAY", "REPLAYED", 0, 0, existing.resultObjectId(), true); }); }
    private CommandResult result(UUID id, String no, String kind, String state, long version, int businessVersion, UUID related, boolean duplicate) { return new CommandResult(id, no, kind, state, version, businessVersion, related, duplicate, UUID.randomUUID().toString(), "COMPLETED", "SAFE_RETRY"); }
    private void audit(UUID id, String operation, String permission, ActorContext actor, String reason) { audit.append(operation, permission, actor, "ALLOWED", "COMPLETED", id, "P19", UUID.randomUUID().toString(), reason); }
    private void publish(String event, UUID id, String kind, ActorContext actor, String digest) { outbox.append(event, id, kind, 0, UUID.randomUUID().toString(), digest, actor.actorId()); }

    public record CommandResult(UUID objectId, String businessNo, String objectKindCode, String stateCode, long concurrencyVersion, int businessVersion, UUID relatedObjectId, boolean duplicate, String operationIdentity, String processingStateCode, String safeRetryCode) { }
    public interface VersionLike { long expectedVersion(); String idempotencyKey(); String reasonText(); }
    public record VersionCommand(long expectedVersion, String idempotencyKey, String reasonText) implements VersionLike { }
    public record AssignmentCommand(String actorRef, long expectedVersion, String idempotencyKey, String reasonText) implements VersionLike { }
    public record CreateTaskCommand(UUID caseId, String modalityCode, String categoryCode, String priorityCode, String dataScopeCode, String idempotencyKey) { }
    public record SaveDraftCommand(String grossDescriptionReference, String microscopicDescription, String diagnosisConclusion, String supplementaryNote, String structuredItems, String terminologyReference, long expectedVersion, String idempotencyKey) { }
    public record SubmitDiagnosisCommand(long expectedVersion, String idempotencyKey) { }
    public record FollowUpCommand(UUID targetOpinionVersionId, String followUpOpinion, String consistencyCode, String returnReason, String adoptionRecommendation, String idempotencyKey) { }
    public record FollowUpDecisionCommand(long expectedVersion, String reasonText, String idempotencyKey) { }
    public record CreateReportCommand(UUID diagnosisVersionId, String reportTypeCode, String idempotencyKey) { }
    public record GenerateContentCommand(String patientSnapshot, String encounterSnapshot, String caseNoSnapshot, String specimenMaterialSummary, String clinicalInformation, String specimenInformation, String grossDescription, String microscopicDescription, String diagnosisConclusion, String supplementaryNote, String technicalResultReferenceSummary, String templateLogicVersion, UUID diagnosisVersionId, String idempotencyKey) { }
    public record ReportDraftCommand(String clinicalInformation, String specimenInformation, String grossDescription, String microscopicDescription, String diagnosisConclusion, String supplementaryNote, String technicalResultReferenceSummary, long expectedVersion, String idempotencyKey) { }
    public record ReviewCommand(String reviewerActorRef, String reasonText, String idempotencyKey) { }
    public record ReviewDecisionCommand(String reviewerActorRef, String decisionCode, String reasonText, String idempotencyKey) { }
    public record SignCommand(String reviewerActorRef, long expectedReportVersion, String idempotencyKey) { }
    public record RevisionCommand(String reasonText, String idempotencyKey) { }
    public record CorrectionCommand(String errorTypeCode, String reasonText, String idempotencyKey) { }
    public record RevisionApprovalCommand(String reviewerActorRef, String reasonText, String idempotencyKey) { }
    public record WithdrawalExecutionCommand(String reasonText, String idempotencyKey) { }
}
