package com.hanjisang.pis.v2.material.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Block {

    public static final String ROUTINE = "ROUTINE";

    private final UUID id;
    private final UUID caseId;
    private final UUID grossingId;
    private final UUID specimenId;
    private String blockCode;
    private String blockType;
    private String samplingDescription;
    private final int quantity;
    private String note;
    private final boolean externalSource;
    private final String externalSourceReference;
    private Instant deletedAt;
    private String deletionReason;
    private long concurrencyVersion;

    private Block(UUID id, UUID caseId, UUID grossingId, UUID specimenId, String blockCode, String blockType,
            String samplingDescription, int quantity, String note, boolean externalSource,
            String externalSourceReference, Instant deletedAt, String deletionReason, long concurrencyVersion) {
        this.id = Objects.requireNonNull(id, "蜡块内部ID不能为空");
        this.caseId = Objects.requireNonNull(caseId, "蜡块病例ID不能为空");
        if (!externalSource && (grossingId == null || specimenId == null)) {
            throw new IllegalArgumentException("本院蜡块必须保留取材和标本来源");
        }
        this.grossingId = grossingId;
        this.specimenId = specimenId;
        this.blockCode = required(blockCode, "蜡块编号不能为空");
        this.blockType = required(blockType, "蜡块类型不能为空");
        this.samplingDescription = optional(samplingDescription);
        if (quantity != 1) throw new IllegalArgumentException("每个蜡块记录只能表示一个包埋盒身份");
        this.quantity = quantity;
        this.note = optional(note);
        if (externalSource && (externalSourceReference == null || externalSourceReference.isBlank())) {
            throw new IllegalArgumentException("外部蜡块必须保留来源引用");
        }
        this.externalSource = externalSource;
        this.externalSourceReference = optional(externalSourceReference);
        this.deletedAt = deletedAt;
        this.deletionReason = optional(deletionReason);
        if (concurrencyVersion < 0) throw new IllegalArgumentException("蜡块并发版本不能为负数");
        this.concurrencyVersion = concurrencyVersion;
    }

    public static Block create(UUID id, UUID caseId, UUID grossingId, UUID specimenId, String blockCode,
            String blockType) {
        return create(id, caseId, grossingId, specimenId, blockCode, blockType, null, null);
    }

    public static Block create(UUID id, UUID caseId, UUID grossingId, UUID specimenId, String blockCode,
            String blockType, String samplingDescription, String note) {
        return new Block(id, caseId, grossingId, specimenId, blockCode, blockType, samplingDescription, 1, note,
                false, null, null, null, 0);
    }

    public static Block createExternal(UUID id, UUID caseId, UUID grossingId, UUID specimenId, String blockCode,
            String blockType, String externalSourceReference) {
        return new Block(id, caseId, grossingId, specimenId, blockCode, blockType, null, 1, null, true,
                externalSourceReference, null, null, 0);
    }

    public static Block persisted(UUID id, UUID caseId, UUID grossingId, UUID specimenId, String blockCode,
            String blockType, boolean externalSource, String externalSourceReference, Instant deletedAt,
            String deletionReason, long concurrencyVersion) {
        return persisted(id, caseId, grossingId, specimenId, blockCode, blockType, null, 1, null, externalSource,
                externalSourceReference, deletedAt, deletionReason, concurrencyVersion);
    }

    public static Block persisted(UUID id, UUID caseId, UUID grossingId, UUID specimenId, String blockCode,
            String blockType, String samplingDescription, int quantity, String note, boolean externalSource,
            String externalSourceReference, Instant deletedAt, String deletionReason, long concurrencyVersion) {
        return new Block(id, caseId, grossingId, specimenId, blockCode, blockType, samplingDescription, quantity,
                note, externalSource, externalSourceReference, deletedAt, deletionReason, concurrencyVersion);
    }

    public void update(String blockCode, String blockType) {
        update(blockCode, blockType, samplingDescription, note);
    }

    public void update(String blockCode, String blockType, String samplingDescription, String note) {
        ensureActive();
        this.blockCode = required(blockCode, "蜡块编号不能为空");
        this.blockType = required(blockType, "蜡块类型不能为空");
        this.samplingDescription = optional(samplingDescription);
        this.note = optional(note);
        concurrencyVersion++;
    }

    public void softDelete(String reason, Instant deletedAt) {
        ensureActive();
        this.deletionReason = required(reason, "蜡块失效原因不能为空");
        this.deletedAt = Objects.requireNonNull(deletedAt, "蜡块失效时间不能为空");
        concurrencyVersion++;
    }

    public boolean isDeleted() { return deletedAt != null; }
    public UUID id() { return id; }
    public UUID caseId() { return caseId; }
    public UUID grossingId() { return grossingId; }
    public UUID specimenId() { return specimenId; }
    public String blockCode() { return blockCode; }
    public String blockType() { return blockType; }
    public String samplingDescription() { return samplingDescription; }
    public int quantity() { return quantity; }
    public String note() { return note; }
    public boolean externalSource() { return externalSource; }
    public String externalSourceReference() { return externalSourceReference; }
    public Instant deletedAt() { return deletedAt; }
    public String deletionReason() { return deletionReason; }
    public long concurrencyVersion() { return concurrencyVersion; }

    private void ensureActive() {
        if (isDeleted()) throw new IllegalStateException("已失效蜡块不能修改");
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
