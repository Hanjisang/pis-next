package com.hanjisang.pis.v2.material.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Slide {

    public static final String INITIAL = "INITIAL";
    public static final String TECHNICAL_ORDER = "TECHNICAL_ORDER";
    public static final String FROZEN_ROUND = "FROZEN_ROUND";
    public static final String CYTOLOGY = "CYTOLOGY";
    public static final String EXTERNAL = "EXTERNAL";

    private final UUID id;
    private final UUID caseId;
    private final UUID blockId;
    private final UUID specimenId;
    private String slideCode;
    private final String slideType;
    private final String sourceContextType;
    private final UUID sourceContextId;
    private final String ruleCode;
    private final int occurrenceNo;
    private final boolean required;
    private Instant completedAt;
    private String completedBy;
    private Instant deletedAt;
    private String deletionReason;
    private long concurrencyVersion;

    private Slide(UUID id, UUID caseId, UUID blockId, UUID specimenId, String slideCode, String slideType,
            String sourceContextType, UUID sourceContextId, String ruleCode, int occurrenceNo, boolean required,
            Instant completedAt, String completedBy, Instant deletedAt, String deletionReason,
            long concurrencyVersion) {
        this.id = Objects.requireNonNull(id, "切片内部ID不能为空");
        this.caseId = Objects.requireNonNull(caseId, "切片病例ID不能为空");
        if (blockId == null && specimenId == null) {
            throw new IllegalArgumentException("切片必须关联蜡块或标本来源");
        }
        this.blockId = blockId;
        this.specimenId = specimenId;
        this.slideCode = required(slideCode, "切片编号不能为空");
        this.slideType = required(slideType, "切片类型不能为空");
        this.sourceContextType = required(sourceContextType, "切片来源上下文不能为空");
        this.sourceContextId = sourceContextId;
        this.ruleCode = required(ruleCode, "切片规则编码不能为空");
        if (occurrenceNo < 1) {
            throw new IllegalArgumentException("切片规则序号必须为正数");
        }
        this.occurrenceNo = occurrenceNo;
        this.required = required;
        this.completedAt = completedAt;
        this.completedBy = optional(completedBy);
        this.deletedAt = deletedAt;
        this.deletionReason = optional(deletionReason);
        if (concurrencyVersion < 0) {
            throw new IllegalArgumentException("切片并发版本不能为负数");
        }
        this.concurrencyVersion = concurrencyVersion;
    }

    public static Slide initialFromBlock(UUID id, UUID caseId, UUID blockId, String slideCode, String slideType,
            UUID grossingId, String ruleCode, int occurrenceNo, boolean required) {
        return new Slide(id, caseId, blockId, null, slideCode, slideType, INITIAL, grossingId, ruleCode,
                occurrenceNo, required, null, null, null, null, 0);
    }

    public static Slide fromBlockContext(UUID id, UUID caseId, UUID blockId, String slideCode, String slideType,
            String sourceContextType, UUID sourceContextId, String ruleCode, int occurrenceNo, boolean required) {
        return new Slide(id, caseId, blockId, null, slideCode, slideType, sourceContextType, sourceContextId,
                ruleCode, occurrenceNo, required, null, null, null, null, 0);
    }

    public static Slide technicalFromTarget(UUID id, UUID caseId, UUID blockId, UUID specimenId, String slideCode,
            String slideType, UUID orderItemId, String projectCode, int occurrenceNo, boolean required) {
        return new Slide(id, caseId, blockId, specimenId, slideCode, slideType, TECHNICAL_ORDER, orderItemId,
                projectCode, occurrenceNo, required, null, null, null, null, 0);
    }

    public static Slide persisted(UUID id, UUID caseId, UUID blockId, UUID specimenId, String slideCode,
            String slideType, String sourceContextType, UUID sourceContextId, String ruleCode, int occurrenceNo,
            boolean required, Instant completedAt, String completedBy, Instant deletedAt, String deletionReason,
            long concurrencyVersion) {
        return new Slide(id, caseId, blockId, specimenId, slideCode, slideType, sourceContextType, sourceContextId,
                ruleCode, occurrenceNo, required, completedAt, completedBy, deletedAt, deletionReason,
                concurrencyVersion);
    }

    public void renameCode(String slideCode) {
        ensureActive();
        this.slideCode = required(slideCode, "切片编号不能为空");
        concurrencyVersion++;
    }

    public void complete(String completedBy, Instant completedAt) {
        ensureActive();
        if (this.completedAt != null) {
            return;
        }
        this.completedBy = required(completedBy, "切片完成人不能为空");
        this.completedAt = Objects.requireNonNull(completedAt, "切片完成时间不能为空");
        concurrencyVersion++;
    }

    public void softDelete(String reason, Instant deletedAt) {
        ensureActive();
        this.deletionReason = required(reason, "切片失效原因不能为空");
        this.deletedAt = Objects.requireNonNull(deletedAt, "切片失效时间不能为空");
        concurrencyVersion++;
    }

    public boolean isCompleted() { return completedAt != null; }
    public boolean isDeleted() { return deletedAt != null; }
    public UUID id() { return id; }
    public UUID caseId() { return caseId; }
    public UUID blockId() { return blockId; }
    public UUID specimenId() { return specimenId; }
    public String slideCode() { return slideCode; }
    public String slideType() { return slideType; }
    public String sourceContextType() { return sourceContextType; }
    public UUID sourceContextId() { return sourceContextId; }
    public String ruleCode() { return ruleCode; }
    public int occurrenceNo() { return occurrenceNo; }
    public boolean required() { return required; }
    public Instant completedAt() { return completedAt; }
    public String completedBy() { return completedBy; }
    public Instant deletedAt() { return deletedAt; }
    public String deletionReason() { return deletionReason; }
    public long concurrencyVersion() { return concurrencyVersion; }

    private void ensureActive() {
        if (isDeleted()) {
            throw new IllegalStateException("已失效切片不能修改");
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
