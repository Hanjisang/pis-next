package com.hanjisang.pis.v2.registration.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Specimen {

    private final UUID id;
    private final UUID caseId;
    private final String specimenNo;
    private String specimenCode;
    private String specimenKindCode;
    private String sourceKindCode;
    private String sourceReference;
    private String collectionSite;
    private String collectionMethodCode;
    private String labelCode;
    private Instant deletedAt;
    private String deletionReason;
    private long concurrencyVersion;

    private Specimen(UUID id, UUID caseId, String specimenNo, String specimenCode, String specimenKindCode,
            String sourceKindCode, String sourceReference, String collectionSite, String collectionMethodCode,
            String labelCode, Instant deletedAt, String deletionReason, long concurrencyVersion) {
        this.id = Objects.requireNonNull(id, "标本内部ID不能为空");
        this.caseId = Objects.requireNonNull(caseId, "标本所属病例不能为空");
        this.specimenNo = required(specimenNo, "标本编号不能为空");
        this.specimenCode = required(specimenCode, "标本代码不能为空");
        this.specimenKindCode = required(specimenKindCode, "标本类型不能为空");
        this.sourceKindCode = required(sourceKindCode, "标本来源类型不能为空");
        this.sourceReference = required(sourceReference, "标本来源引用不能为空");
        this.collectionSite = required(collectionSite, "标本来源部位不能为空");
        this.collectionMethodCode = required(collectionMethodCode, "标本采集方式不能为空");
        this.labelCode = optional(labelCode);
        this.deletedAt = deletedAt;
        this.deletionReason = optional(deletionReason);
        if (deletedAt == null && this.deletionReason != null) {
            throw new IllegalArgumentException("未软删除标本不能存在删除原因");
        }
        if (concurrencyVersion < 0) {
            throw new IllegalArgumentException("标本并发版本不能为负数");
        }
        this.concurrencyVersion = concurrencyVersion;
    }

    public static Specimen register(UUID id, UUID caseId, String specimenNo, String specimenCode,
            String specimenKindCode, String sourceKindCode, String sourceReference, String collectionSite,
            String collectionMethodCode, String labelCode) {
        return new Specimen(id, caseId, specimenNo, specimenCode, specimenKindCode, sourceKindCode, sourceReference,
                collectionSite, collectionMethodCode, labelCode, null, null, 0);
    }

    public static Specimen persisted(UUID id, UUID caseId, String specimenNo, String specimenCode,
            String specimenKindCode, String sourceKindCode, String sourceReference, String collectionSite,
            String collectionMethodCode, String labelCode, Instant deletedAt, String deletionReason,
            long concurrencyVersion) {
        return new Specimen(id, caseId, specimenNo, specimenCode, specimenKindCode, sourceKindCode,
                sourceReference, collectionSite, collectionMethodCode, labelCode, deletedAt, deletionReason,
                concurrencyVersion);
    }

    public void updateDetails(String specimenCode, String specimenKindCode, String sourceKindCode,
            String sourceReference, String collectionSite, String collectionMethodCode, String labelCode,
            Instant updatedAt) {
        ensureNotDeleted();
        this.specimenCode = required(specimenCode, "标本代码不能为空");
        this.specimenKindCode = required(specimenKindCode, "标本类型不能为空");
        this.sourceKindCode = required(sourceKindCode, "标本来源类型不能为空");
        this.sourceReference = required(sourceReference, "标本来源引用不能为空");
        this.collectionSite = required(collectionSite, "标本来源部位不能为空");
        this.collectionMethodCode = required(collectionMethodCode, "标本采集方式不能为空");
        this.labelCode = optional(labelCode);
        Objects.requireNonNull(updatedAt, "标本修改时间不能为空");
        concurrencyVersion++;
    }

    public void softDelete(String reason, Instant deletedAt) {
        ensureNotDeleted();
        this.deletionReason = required(reason, "标本软删除原因不能为空");
        this.deletedAt = Objects.requireNonNull(deletedAt, "标本软删除时间不能为空");
        concurrencyVersion++;
    }

    public boolean deleted() { return deletedAt != null; }
    public UUID id() { return id; }
    public UUID caseId() { return caseId; }
    public String specimenNo() { return specimenNo; }
    public String specimenCode() { return specimenCode; }
    public String specimenKindCode() { return specimenKindCode; }
    public String sourceKindCode() { return sourceKindCode; }
    public String sourceReference() { return sourceReference; }
    public String collectionSite() { return collectionSite; }
    public String collectionMethodCode() { return collectionMethodCode; }
    public String labelCode() { return labelCode; }
    public Instant deletedAt() { return deletedAt; }
    public String deletionReason() { return deletionReason; }
    public long concurrencyVersion() { return concurrencyVersion; }

    private void ensureNotDeleted() {
        if (deleted()) {
            throw new IllegalStateException("已软删除标本不能继续修改");
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
