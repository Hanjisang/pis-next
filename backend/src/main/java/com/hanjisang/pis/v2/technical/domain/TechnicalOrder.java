package com.hanjisang.pis.v2.technical.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class TechnicalOrder {

    private final UUID id;
    private final String orderNo;
    private final UUID diagnosisId;
    private final UUID caseId;
    private final boolean requiredBeforeSignOut;
    private TechnicalOrderStatus status;
    private Instant cancelledAt;
    private String cancelledBy;
    private String cancellationReason;
    private long version;

    private TechnicalOrder(UUID id, String orderNo, UUID diagnosisId, UUID caseId, boolean requiredBeforeSignOut,
            TechnicalOrderStatus status, Instant cancelledAt, String cancelledBy, String cancellationReason,
            long version) {
        this.id = Objects.requireNonNull(id, "技术医嘱内部ID不能为空");
        this.orderNo = required(orderNo, "技术医嘱编号不能为空");
        this.diagnosisId = Objects.requireNonNull(diagnosisId, "诊断ID不能为空");
        this.caseId = Objects.requireNonNull(caseId, "病例ID不能为空");
        this.requiredBeforeSignOut = requiredBeforeSignOut;
        this.status = Objects.requireNonNull(status, "技术医嘱状态不能为空");
        this.cancelledAt = cancelledAt;
        this.cancelledBy = optional(cancelledBy);
        this.cancellationReason = optional(cancellationReason);
        if (version < 0) throw new IllegalArgumentException("技术医嘱并发版本不能为负数");
        this.version = version;
    }

    public static TechnicalOrder pending(UUID id, String orderNo, UUID diagnosisId, UUID caseId,
            boolean requiredBeforeSignOut) {
        return new TechnicalOrder(id, orderNo, diagnosisId, caseId, requiredBeforeSignOut,
                TechnicalOrderStatus.PENDING, null, null, null, 0);
    }

    public static TechnicalOrder persisted(UUID id, String orderNo, UUID diagnosisId, UUID caseId,
            boolean requiredBeforeSignOut, TechnicalOrderStatus status, Instant cancelledAt, String cancelledBy,
            String cancellationReason, long version) {
        return new TechnicalOrder(id, orderNo, diagnosisId, caseId, requiredBeforeSignOut, status, cancelledAt,
                cancelledBy, cancellationReason, version);
    }

    public void cancel(String actor, String reason, Instant now) {
        if (status == TechnicalOrderStatus.COMPLETED) throw new IllegalStateException("已完成技术医嘱不能取消");
        if (status == TechnicalOrderStatus.CANCELLED) return;
        this.status = TechnicalOrderStatus.CANCELLED;
        this.cancelledBy = required(actor, "取消人不能为空");
        this.cancellationReason = required(reason, "取消原因不能为空");
        this.cancelledAt = Objects.requireNonNull(now, "取消时间不能为空");
        this.version++;
    }

    public void syncStatus(TechnicalOrderStatus nextStatus) {
        if (status == TechnicalOrderStatus.CANCELLED) return;
        this.status = Objects.requireNonNull(nextStatus, "技术医嘱状态不能为空");
        this.version++;
    }

    public UUID id() { return id; }
    public String orderNo() { return orderNo; }
    public UUID diagnosisId() { return diagnosisId; }
    public UUID caseId() { return caseId; }
    public boolean requiredBeforeSignOut() { return requiredBeforeSignOut; }
    public TechnicalOrderStatus status() { return status; }
    public Instant cancelledAt() { return cancelledAt; }
    public String cancelledBy() { return cancelledBy; }
    public String cancellationReason() { return cancellationReason; }
    public long version() { return version; }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
