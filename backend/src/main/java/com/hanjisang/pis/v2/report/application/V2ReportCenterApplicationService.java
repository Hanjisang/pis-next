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
}
