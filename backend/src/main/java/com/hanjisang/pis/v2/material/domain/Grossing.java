package com.hanjisang.pis.v2.material.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class Grossing {

    public static final String INITIAL = "INITIAL";
    public static final String TECHNICAL_ORDER = "TECHNICAL_ORDER";
    public static final String FROZEN_CONTEXT = "FROZEN_CONTEXT";
    public static final String OTHER = "OTHER";

    private static final Set<String> SOURCE_TYPES = Set.of(INITIAL, TECHNICAL_ORDER, FROZEN_CONTEXT, OTHER);

    private final UUID id;
    private final UUID caseId;
    private final String grossingNo;
    private final String sourceType;
    private final UUID sourceReferenceId;
    private String grossDescription;
    private String grossingInstruction;
    private String grossingDoctorId;
    private String recorderId;
    private Instant startedAt;
    private Instant completedAt;
    private String completedBy;
    private Instant deletedAt;
    private String deletionReason;
    private long concurrencyVersion;

    private Grossing(UUID id, UUID caseId, String grossingNo, String sourceType, UUID sourceReferenceId,
            String grossDescription, String grossingInstruction, String grossingDoctorId, String recorderId,
            Instant startedAt, Instant completedAt, String completedBy, Instant deletedAt, String deletionReason,
            long concurrencyVersion) {
        this.id = Objects.requireNonNull(id, "取材内部ID不能为空");
        this.caseId = Objects.requireNonNull(caseId, "取材病例ID不能为空");
        this.grossingNo = required(grossingNo, "取材业务编号不能为空");
        this.sourceType = required(sourceType, "取材来源类型不能为空");
        if (!SOURCE_TYPES.contains(this.sourceType)) {
            throw new IllegalArgumentException("取材来源类型不受支持");
        }
        if (sourceReferenceId == null && !INITIAL.equals(this.sourceType)) {
            throw new IllegalArgumentException("非初始取材必须保留来源引用");
        }
        this.sourceReferenceId = sourceReferenceId;
        this.grossDescription = required(grossDescription, "取材描述不能为空");
        this.grossingInstruction = optional(grossingInstruction);
        this.grossingDoctorId = required(grossingDoctorId, "取材医生不能为空");
        this.recorderId = required(recorderId, "取材记录人不能为空");
        this.startedAt = Objects.requireNonNull(startedAt, "取材开始时间不能为空");
        this.completedAt = completedAt;
        this.completedBy = optional(completedBy);
        this.deletedAt = deletedAt;
        this.deletionReason = optional(deletionReason);
        if (concurrencyVersion < 0) {
            throw new IllegalArgumentException("取材并发版本不能为负数");
        }
        this.concurrencyVersion = concurrencyVersion;
    }

    public static Grossing open(UUID id, UUID caseId, String grossingNo, String sourceType, UUID sourceReferenceId,
            String grossDescription, String grossingInstruction, String grossingDoctorId, String recorderId,
            Instant startedAt) {
        return new Grossing(id, caseId, grossingNo, sourceType, sourceReferenceId, grossDescription,
                grossingInstruction, grossingDoctorId, recorderId, startedAt, null, null, null, null, 0);
    }

    public static Grossing persisted(UUID id, UUID caseId, String grossingNo, String sourceType,
            UUID sourceReferenceId, String grossDescription, String grossingInstruction, String grossingDoctorId,
            String recorderId, Instant startedAt, Instant completedAt, String completedBy, Instant deletedAt,
            String deletionReason, long concurrencyVersion) {
        return new Grossing(id, caseId, grossingNo, sourceType, sourceReferenceId, grossDescription,
                grossingInstruction, grossingDoctorId, recorderId, startedAt, completedAt, completedBy, deletedAt,
                deletionReason, concurrencyVersion);
    }

    public void updateDetails(String grossDescription, String grossingInstruction, String grossingDoctorId,
            String recorderId) {
        ensureEditable();
        this.grossDescription = required(grossDescription, "取材描述不能为空");
        this.grossingInstruction = optional(grossingInstruction);
        this.grossingDoctorId = required(grossingDoctorId, "取材医生不能为空");
        this.recorderId = required(recorderId, "取材记录人不能为空");
        concurrencyVersion++;
    }

    public void complete(Instant completedAt, String completedBy) {
        ensureActive();
        if (this.completedAt != null) {
            throw new IllegalStateException("取材已经完成");
        }
        this.completedAt = Objects.requireNonNull(completedAt, "取材完成时间不能为空");
        this.completedBy = required(completedBy, "取材完成人不能为空");
        concurrencyVersion++;
    }

    public void reopen(Instant reopenedAt) {
        ensureActive();
        if (completedAt == null) {
            throw new IllegalStateException("未完成取材不能重新打开");
        }
        concurrencyVersion++;
        Objects.requireNonNull(reopenedAt, "重开时间不能为空");
    }

    public void correctCompletedDetails(String grossDescription, String grossingInstruction,
            String grossingDoctorId, String recorderId) {
        ensureActive();
        if (completedAt == null) throw new IllegalStateException("只有已完成取材可以执行授权修正");
        this.grossDescription = required(grossDescription, "取材描述不能为空");
        this.grossingInstruction = optional(grossingInstruction);
        this.grossingDoctorId = required(grossingDoctorId, "取材医生不能为空");
        this.recorderId = required(recorderId, "取材记录人不能为空");
        concurrencyVersion++;
    }

    public void softDelete(String reason, Instant deletedAt) {
        ensureActive();
        if (completedAt != null) {
            throw new IllegalStateException("已完成取材不能直接软删除");
        }
        deletionReason = required(reason, "取材失效原因不能为空");
        this.deletedAt = Objects.requireNonNull(deletedAt, "取材失效时间不能为空");
        concurrencyVersion++;
    }

    public boolean isCompleted() { return completedAt != null; }
    public boolean isDeleted() { return deletedAt != null; }
    public boolean isEditable() { return !isDeleted() && !isCompleted(); }
    public UUID id() { return id; }
    public UUID caseId() { return caseId; }
    public String grossingNo() { return grossingNo; }
    public String sourceType() { return sourceType; }
    public UUID sourceReferenceId() { return sourceReferenceId; }
    public String grossDescription() { return grossDescription; }
    public String grossingInstruction() { return grossingInstruction; }
    public String grossingDoctorId() { return grossingDoctorId; }
    public String recorderId() { return recorderId; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
    public String completedBy() { return completedBy; }
    public Instant deletedAt() { return deletedAt; }
    public String deletionReason() { return deletionReason; }
    public long concurrencyVersion() { return concurrencyVersion; }

    private void ensureActive() {
        if (isDeleted()) {
            throw new IllegalStateException("已失效取材不能修改");
        }
    }

    private void ensureEditable() {
        ensureActive();
        if (isCompleted()) {
            throw new IllegalStateException("已完成取材需要先授权重开");
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
