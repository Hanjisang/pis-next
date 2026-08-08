package com.hanjisang.pis.v2.diagnosis.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ResponsibilityUnit {

    private final UUID id;
    private final UUID diagnosisId;
    private final ResponsibilityRole role;
    private final String doctorId;
    private final int sequence;
    private final Instant acceptedAt;
    private Instant completedAt;
    private Instant endedAt;
    private String endReason;
    private final AssignmentSource assignmentSource;
    private final String assignmentReason;
    private final Instant createdAt;
    private final String createdBy;
    private long version;

    private ResponsibilityUnit(UUID id, UUID diagnosisId, ResponsibilityRole role, String doctorId, int sequence,
            Instant acceptedAt, Instant completedAt, Instant endedAt, String endReason, AssignmentSource assignmentSource,
            String assignmentReason, Instant createdAt, String createdBy, long version) {
        this.id = Objects.requireNonNull(id, "责任节点ID不能为空");
        this.diagnosisId = Objects.requireNonNull(diagnosisId, "责任节点必须关联诊断");
        this.role = Objects.requireNonNull(role, "责任角色不能为空");
        this.doctorId = required(doctorId, "责任医生不能为空");
        if (sequence <= 0) { throw new IllegalArgumentException("责任序号必须大于0"); }
        this.sequence = sequence;
        this.acceptedAt = Objects.requireNonNull(acceptedAt, "责任接收时间不能为空");
        this.completedAt = completedAt;
        this.endedAt = endedAt;
        this.endReason = optional(endReason);
        this.assignmentSource = Objects.requireNonNull(assignmentSource, "责任来源不能为空");
        this.assignmentReason = optional(assignmentReason);
        this.createdAt = Objects.requireNonNull(createdAt, "责任创建时间不能为空");
        this.createdBy = required(createdBy, "责任创建人不能为空");
        if (version < 0) { throw new IllegalArgumentException("责任版本不能为负数"); }
        this.version = version;
    }

    public static ResponsibilityUnit assign(UUID id, UUID diagnosisId, ResponsibilityRole role, String doctorId,
            int sequence, AssignmentSource source, String reason, Instant now, String actorRef) {
        return new ResponsibilityUnit(id, diagnosisId, role, doctorId, sequence, now, null, null, null, source,
                reason, now, actorRef, 0);
    }

    public static ResponsibilityUnit persisted(UUID id, UUID diagnosisId, ResponsibilityRole role, String doctorId,
            int sequence, Instant acceptedAt, Instant completedAt, Instant endedAt, String endReason,
            AssignmentSource assignmentSource, String assignmentReason, Instant createdAt, String createdBy, long version) {
        return new ResponsibilityUnit(id, diagnosisId, role, doctorId, sequence, acceptedAt, completedAt, endedAt,
                endReason, assignmentSource, assignmentReason, createdAt, createdBy, version);
    }

    public void complete(long expectedVersion, Instant now) {
        ensureOpen(expectedVersion);
        completedAt = Objects.requireNonNull(now, "责任完成时间不能为空");
        version++;
    }

    public void end(long expectedVersion, String reason, Instant now) {
        ensureOpen(expectedVersion);
        endReason = required(reason, "责任结束原因不能为空");
        endedAt = Objects.requireNonNull(now, "责任结束时间不能为空");
        version++;
    }

    public boolean isCurrent() { return completedAt == null && endedAt == null; }
    public UUID id() { return id; }
    public UUID diagnosisId() { return diagnosisId; }
    public ResponsibilityRole role() { return role; }
    public String doctorId() { return doctorId; }
    public int sequence() { return sequence; }
    public Instant acceptedAt() { return acceptedAt; }
    public Instant completedAt() { return completedAt; }
    public Instant endedAt() { return endedAt; }
    public String endReason() { return endReason; }
    public AssignmentSource assignmentSource() { return assignmentSource; }
    public String assignmentReason() { return assignmentReason; }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public long version() { return version; }

    private void ensureOpen(long expectedVersion) {
        if (version != expectedVersion) { throw new IllegalStateException("责任节点版本冲突"); }
        if (!isCurrent()) { throw new IllegalStateException("已结束责任节点不可再次处理"); }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(message); }
        return value.trim();
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
