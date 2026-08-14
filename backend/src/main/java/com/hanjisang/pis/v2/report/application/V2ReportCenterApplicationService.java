package com.hanjisang.pis.v2.report.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.report.infrastructure.JdbcV2ReportCenterRepository;
import com.hanjisang.pis.v2.report.infrastructure.JdbcV2ReportCenterRepository.DelayRow;
import com.hanjisang.pis.v2.report.infrastructure.JdbcV2ReportCenterRepository.QueueRow;
import com.hanjisang.pis.v2.report.infrastructure.JdbcV2ReportCenterRepository.ReportAccessRow;

@Service
public class V2ReportCenterApplicationService {

    private static final Set<String> DELAY_REASONS = Set.of(
            "TECHNICAL_WORK", "CONSULTATION", "MATERIAL_PENDING", "CLINICAL_INFORMATION", "OTHER");

    private final JdbcV2ReportCenterRepository repository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;

    public V2ReportCenterApplicationService(JdbcV2ReportCenterRepository repository,
            P15AuthorizationService authorization, JdbcAuditEventRepository audit) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ReportCenterResult get() {
        var actor = authorization.require(V2ReportApplicationService.REPORT_QUERY);
        Instant now = Instant.now();
        List<QueueItem> items = repository.find(actor.hospitalScope()).stream()
                .map(row -> queueItem(row, now))
                .toList();
        return new ReportCenterResult(items,
                new Counts(count(items, "WAITING_SIGN"), count(items, "SIGNED"), count(items, "WITHDRAWN"),
                        count(items, "SUPPLEMENTAL"), items.stream().filter(item -> "SIGNED".equals(item.queueCode()))
                                .limit(20).count(),
                        countTat(items, "WARNING"), countTat(items, "OVERDUE"),
                        items.stream().filter(item -> item.delay() != null).count()),
                now);
    }

    @Transactional
    public List<ClinicianReportResult> clinicianQuery(ClinicianQueryCommand command) {
        ActorContext actor = authorization.require(V2ReportApplicationService.REPORT_QUERY);
        String reportNo = optionalCriterion(command.reportNo(), 64, "报告号");
        String pathologyNo = optionalCriterion(command.pathologyNo(), 128, "病理号");
        String patientReference = optionalCriterion(command.patientReference(), 128, "患者引用");
        if (reportNo == null && pathologyNo == null && patientReference == null) {
            throw reject("V2-REPORT-CLINICIAN-CRITERIA", "至少输入报告号、病理号或患者引用之一");
        }
        List<ReportAccessRow> rows = repository.searchEffectiveReports(actor.hospitalScope(), reportNo,
                pathologyNo, patientReference);
        audit.append("PIS-V2-REPORT-CLINICIAN-QUERY", V2ReportApplicationService.REPORT_QUERY, actor, "ALLOWED",
                "COMPLETED", rows.isEmpty() ? UUID.randomUUID() : rows.get(0).reportId(), "V2-REPORT-QUERY",
                UUID.randomUUID().toString(), "effective report query; resultCount=" + rows.size());
        return rows.stream().map(row -> new ClinicianReportResult(row.reportId(), row.reportNo(), row.caseId(),
                row.pathologyNo(), row.patientReference(), row.reportNature(), row.signedAt(),
                row.pdfContentHash())).toList();
    }

    @Transactional
    public List<PatientReportResult> patientQuery(PatientQueryCommand command) {
        ActorContext actor = authorization.require(V2ReportApplicationService.REPORT_QUERY);
        String reportNo = requiredCriterion(command.reportNo(), 64, "报告号");
        String pathologyNo = requiredCriterion(command.pathologyNo(), 128, "病理号");
        String identityReference = requiredCriterion(command.identityReference(), 128, "身份核验凭据");
        String terminalReference = requiredCriterion(command.terminalReference(), 128, "终端标识");
        List<ReportAccessRow> rows = repository.searchEffectiveReports(actor.hospitalScope(), reportNo,
                pathologyNo, identityReference);
        if (rows.isEmpty()) {
            audit.appendDenied("PIS-V2-REPORT-PATIENT-QUERY", V2ReportApplicationService.REPORT_QUERY, actor,
                    UUID.randomUUID().toString(), "terminal=" + terminalReference + "; criteria did not match");
            throw reject("V2-REPORT-PATIENT-QUERY-NOT-MATCHED", "身份信息或报告查询条件不匹配");
        }
        audit.append("PIS-V2-REPORT-PATIENT-QUERY", V2ReportApplicationService.REPORT_QUERY, actor, "ALLOWED",
                "COMPLETED", rows.get(0).reportId(), "V2-REPORT-PATIENT-QUERY", UUID.randomUUID().toString(),
                "terminal=" + terminalReference + "; verified report query; resultCount=" + rows.size());
        return rows.stream().map(row -> new PatientReportResult(row.reportId(), row.reportNo(), row.caseId(),
                row.pathologyNo(), row.reportNature(), row.signedAt(), row.pdfContentHash())).toList();
    }

    @Transactional
    public DelayResult declareDelay(DeclareDelayCommand command) {
        ActorContext actor = authorization.require(V2ReportApplicationService.REPORT_SIGN_OUT);
        require(command.diagnosisId(), "诊断不能为空");
        requireText(command.idempotencyKey(), "幂等键不能为空", 128);
        requireText(command.reasonDetail(), "延迟说明不能为空", 1000);
        if (!DELAY_REASONS.contains(command.reasonCode())) {
            throw reject("V2-REPORT-DELAY-REASON", "延迟原因代码无效");
        }
        Instant now = Instant.now();
        if (command.expectedSignAt() == null || !command.expectedSignAt().isAfter(now)
                || command.expectedSignAt().isAfter(now.plus(Duration.ofDays(365)))) {
            throw reject("V2-REPORT-DELAY-EXPECTED-AT", "预计签发时间必须晚于当前时间且不超过一年");
        }
        DelayResult replay = replay(command, actor);
        if (replay != null) return replay;
        var context = repository.findTatContextForUpdate(command.diagnosisId(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-REPORT-DELAY-DIAGNOSIS-NOT-FOUND", "诊断不存在或不在当前数据范围"));
        replay = replay(command, actor);
        if (replay != null) return replay;
        if (context.signed()) throw reject("V2-REPORT-DELAY-ALREADY-SIGNED", "报告已签发，不能登记延迟");
        if (!context.readyForSign()) throw reject("V2-REPORT-DELAY-NOT-READY", "诊断尚未完成审核，不能登记报告延迟");
        if (context.policyId() == null || context.targetMinutes() == null || context.policyVersion() == null) {
            throw reject("V2-REPORT-TAT-POLICY-REQUIRED", "当前业务类型尚未启用报告时效策略");
        }
        if (repository.findActiveDelay(command.diagnosisId(), actor.hospitalScope()).isPresent()) {
            throw reject("V2-REPORT-DELAY-ACTIVE", "该诊断已有未关闭的延迟登记");
        }
        UUID id = UUID.randomUUID();
        Instant dueAt = context.tatStartedAt().plus(Duration.ofMinutes(context.targetMinutes()));
        if (!command.expectedSignAt().isAfter(dueAt)) {
            throw reject("V2-REPORT-DELAY-EXPECTED-AT", "预计签发时间必须晚于当前报告目标时间");
        }
        repository.insertDelay(id, actor.hospitalScope(), context, dueAt, command.reasonCode(),
                command.reasonDetail().trim(), command.expectedSignAt(), command.idempotencyKey(), now,
                actor.actorId());
        DelayRow created = repository.findActiveDelay(command.diagnosisId(), actor.hospitalScope()).orElseThrow();
        audit.append("PIS-V2-REPORT-DELAY-DECLARE", V2ReportApplicationService.REPORT_SIGN_OUT, actor, "ALLOWED",
                "COMPLETED", id, "V2-REPORT-DELAY", UUID.randomUUID().toString(),
                "diagnosisId=" + command.diagnosisId() + "; reason=" + command.reasonCode());
        return delayResult(created, false);
    }

    @Transactional
    public DelayResult resolveDelay(UUID delayId, ResolveDelayCommand command) {
        ActorContext actor = authorization.require(V2ReportApplicationService.REPORT_SIGN_OUT);
        requireText(command.idempotencyKey(), "幂等键不能为空", 128);
        requireText(command.resolutionNote(), "关闭说明不能为空", 1000);
        DelayRow delay = repository.findDelayForUpdate(delayId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-REPORT-DELAY-NOT-FOUND", "延迟登记不存在或不在当前数据范围"));
        if (delay.resolvedAt() != null) {
            if (command.idempotencyKey().equals(delay.resolutionIdempotencyKey())) return delayResult(delay, true);
            throw reject("V2-REPORT-DELAY-ALREADY-RESOLVED", "延迟登记已经关闭");
        }
        if (!repository.resolveDelay(delayId, actor.hospitalScope(), delay.concurrencyVersion(),
                command.resolutionNote().trim(), command.idempotencyKey(), Instant.now(), actor.actorId())) {
            throw reject("V2-REPORT-DELAY-CONFLICT", "延迟登记发生并发更新，请刷新后重试");
        }
        DelayRow resolved = repository.findDelayForUpdate(delayId, actor.hospitalScope()).orElseThrow();
        audit.append("PIS-V2-REPORT-DELAY-RESOLVE", V2ReportApplicationService.REPORT_SIGN_OUT, actor, "ALLOWED",
                "COMPLETED", delayId, "V2-REPORT-DELAY", UUID.randomUUID().toString(),
                command.resolutionNote().trim());
        return delayResult(resolved, false);
    }

    private static QueueItem queueItem(QueueRow row, Instant now) {
        Instant warningAt = row.warningMinutes() == null ? null
                : row.tatStartedAt().plus(Duration.ofMinutes(row.warningMinutes()));
        Instant dueAt = row.targetMinutes() == null ? null
                : row.tatStartedAt().plus(Duration.ofMinutes(row.targetMinutes()));
        boolean finalReport = !"WAITING_SIGN".equals(row.queueCode()) && !"SUPPLEMENTAL".equals(row.queueCode());
        Instant end = finalReport && row.occurredAt() != null ? row.occurredAt() : now;
        long elapsedMinutes = Math.max(0, Duration.between(row.tatStartedAt(), end).toMinutes());
        String tatStatus;
        if (dueAt == null || warningAt == null) tatStatus = "UNCONFIGURED";
        else if ("SUPPLEMENTAL".equals(row.queueCode())) tatStatus = "NOT_APPLICABLE";
        else if (finalReport) tatStatus = end.isAfter(dueAt) ? "COMPLETED_OVERDUE" : "COMPLETED_ON_TIME";
        else if (now.isAfter(dueAt)) tatStatus = "OVERDUE";
        else if (!now.isBefore(warningAt)) tatStatus = "WARNING";
        else tatStatus = "NORMAL";
        DelaySummary delay = row.delayId() == null ? null : new DelaySummary(row.delayId(), row.delayReasonCode(),
                row.delayReasonDetail(), row.expectedSignAt(), row.delayDeclaredAt());
        return new QueueItem(row.diagnosisId(), row.caseId(), row.pathologyNo(), row.patientReference(),
                row.businessTypeCode(), row.queueCode(), row.reportId(), row.reportNo(), row.statusCode(),
                row.occurredAt(), target(row.queueCode()), tatStatus, elapsedMinutes, warningAt, dueAt,
                row.policyVersion(), delay);
    }

    private static long count(List<QueueItem> items, String queueCode) {
        return items.stream().filter(item -> queueCode.equals(item.queueCode())).count();
    }

    private static long countTat(List<QueueItem> items, String tatStatus) {
        return items.stream().filter(item -> tatStatus.equals(item.tatStatus())).count();
    }

    private static String target(String queueCode) {
        return "WAITING_SIGN".equals(queueCode) ? "待签发" : "打开病例报告上下文";
    }

    private static DelayResult delayResult(DelayRow row, boolean duplicate) {
        return new DelayResult(row.id(), row.diagnosisId(), row.caseId(), row.policyVersion(), row.tatDueAt(),
                row.reasonCode(), row.reasonDetail(), row.expectedSignAt(), row.declaredAt(), row.declaredByRef(),
                row.resolvedAt(), row.resolvedByRef(), row.resolutionNote(), row.concurrencyVersion(), duplicate);
    }

    private DelayResult replay(DeclareDelayCommand command, ActorContext actor) {
        DelayRow replay = repository.findDelayByIdempotency(actor.hospitalScope(), command.idempotencyKey()).orElse(null);
        if (replay == null) return null;
        if (!replay.diagnosisId().equals(command.diagnosisId())
                || !replay.reasonCode().equals(command.reasonCode())
                || !replay.reasonDetail().equals(command.reasonDetail().trim())
                || Math.abs(Duration.between(replay.expectedSignAt(), command.expectedSignAt()).toMillis()) > 0) {
            throw reject("V2-REPORT-DELAY-IDEMPOTENCY-CONFLICT", "幂等键已用于不同的延迟登记");
        }
        return delayResult(replay, true);
    }

    private static void require(Object value, String message) {
        if (value == null) throw reject("V2-REPORT-DELAY-INVALID", message);
    }

    private static void requireText(String value, String message, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw reject("V2-REPORT-DELAY-INVALID", message);
        }
    }

    private static String optionalCriterion(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw reject("V2-REPORT-QUERY-INVALID", label + "长度超限");
        return normalized;
    }

    private static String requiredCriterion(String value, int maxLength, String label) {
        String normalized = optionalCriterion(value, maxLength, label);
        if (normalized == null) throw reject("V2-REPORT-QUERY-INVALID", label + "不能为空");
        return normalized;
    }

    private static P15BusinessException reject(String code, String message) {
        return new P15BusinessException(code, message, 422);
    }

    public record ReportCenterResult(List<QueueItem> items, Counts counts, Instant refreshedAt) { }
    public record Counts(long waitingSign, long signed, long withdrawn, long supplemental, long recentSigned,
            long warning, long overdue, long delayed) { }
    public record QueueItem(UUID diagnosisId, UUID caseId, String pathologyNo, String patientReference,
            String businessTypeCode, String queueCode, UUID reportId, String reportNo, String statusCode,
            Instant occurredAt, String targetLabel, String tatStatus, long elapsedMinutes, Instant warningAt,
            Instant dueAt, Integer policyVersion, DelaySummary delay) { }
    public record DelaySummary(UUID delayId, String reasonCode, String reasonDetail, Instant expectedSignAt,
            Instant declaredAt) { }
    public record DeclareDelayCommand(UUID diagnosisId, String reasonCode, String reasonDetail, Instant expectedSignAt,
            String idempotencyKey) { }
    public record ResolveDelayCommand(String resolutionNote, String idempotencyKey) { }
    public record DelayResult(UUID delayId, UUID diagnosisId, UUID caseId, int policyVersion, Instant tatDueAt,
            String reasonCode, String reasonDetail, Instant expectedSignAt, Instant declaredAt, String declaredBy,
            Instant resolvedAt, String resolvedBy, String resolutionNote, long concurrencyVersion,
            boolean duplicate) { }
    public record ClinicianQueryCommand(String reportNo, String pathologyNo, String patientReference) { }
    public record PatientQueryCommand(String reportNo, String pathologyNo, String identityReference,
            String terminalReference) { }
    public record ClinicianReportResult(UUID reportId, String reportNo, UUID caseId, String pathologyNo,
            String patientReference, String reportNature, Instant signedAt, String pdfContentHash) { }
    public record PatientReportResult(UUID reportId, String reportNo, UUID caseId, String pathologyNo,
            String reportNature, Instant signedAt, String pdfContentHash) { }
}
