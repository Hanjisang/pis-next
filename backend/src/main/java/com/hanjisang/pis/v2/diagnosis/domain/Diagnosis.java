package com.hanjisang.pis.v2.diagnosis.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Diagnosis {

    private final UUID id;
    private final UUID caseId;
    private final DiagnosisContextType contextType;
    private final UUID contextId;
    private UUID templateVersionId;
    private String structuredData;
    private String microscopicDescription;
    private String diagnosisText;
    private String comment;
    private long version;
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;
    private String updatedBy;

    private Diagnosis(UUID id, UUID caseId, DiagnosisContextType contextType, UUID contextId,
            UUID templateVersionId, String structuredData, String microscopicDescription, String diagnosisText,
            String comment, long version, Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {
        this.id = Objects.requireNonNull(id, "诊断内部ID不能为空");
        this.caseId = Objects.requireNonNull(caseId, "诊断必须关联病例");
        this.contextType = Objects.requireNonNull(contextType, "诊断上下文类型不能为空");
        this.contextId = Objects.requireNonNull(contextId, "诊断上下文ID不能为空");
        if (contextType == DiagnosisContextType.CASE && !caseId.equals(contextId)) {
            throw new IllegalArgumentException("CASE诊断上下文ID必须等于病例ID");
        }
        this.templateVersionId = Objects.requireNonNull(templateVersionId, "诊断模板版本不能为空");
        this.structuredData = normalizeStructuredData(structuredData);
        this.microscopicDescription = optional(microscopicDescription);
        this.diagnosisText = optional(diagnosisText);
        this.comment = optional(comment);
        if (version < 0) {
            throw new IllegalArgumentException("诊断版本不能为负数");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "诊断创建时间不能为空");
        this.createdBy = required(createdBy, "诊断创建人不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "诊断更新时间不能为空");
        this.updatedBy = required(updatedBy, "诊断更新人不能为空");
    }

    public static Diagnosis create(UUID id, UUID caseId, UUID templateVersionId, String structuredData,
            String microscopicDescription, String diagnosisText, String comment, Instant now, String actorRef) {
        return new Diagnosis(id, caseId, DiagnosisContextType.CASE, caseId, templateVersionId, structuredData,
                microscopicDescription, diagnosisText, comment, 0, now, actorRef, now, actorRef);
    }

    public static Diagnosis createForContext(UUID id, UUID caseId, DiagnosisContextType contextType, UUID contextId,
            UUID templateVersionId, String structuredData, String microscopicDescription, String diagnosisText,
            String comment, Instant now, String actorRef) {
        return new Diagnosis(id, caseId, contextType, contextId, templateVersionId, structuredData,
                microscopicDescription, diagnosisText, comment, 0, now, actorRef, now, actorRef);
    }

    public static Diagnosis persisted(UUID id, UUID caseId, DiagnosisContextType contextType, UUID contextId,
            UUID templateVersionId, String structuredData, String microscopicDescription, String diagnosisText,
            String comment, long version, Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {
        return new Diagnosis(id, caseId, contextType, contextId, templateVersionId, structuredData,
                microscopicDescription, diagnosisText, comment, version, createdAt, createdBy, updatedAt, updatedBy);
    }

    public void updateContent(UUID templateVersionId, String structuredData, String microscopicDescription,
            String diagnosisText, String comment, long expectedVersion, Instant now, String actorRef) {
        if (version != expectedVersion) {
            throw new IllegalStateException("诊断版本冲突");
        }
        this.templateVersionId = Objects.requireNonNull(templateVersionId, "诊断模板版本不能为空");
        this.structuredData = normalizeStructuredData(structuredData);
        this.microscopicDescription = optional(microscopicDescription);
        this.diagnosisText = optional(diagnosisText);
        this.comment = optional(comment);
        this.version++;
        this.updatedAt = Objects.requireNonNull(now, "诊断更新时间不能为空");
        this.updatedBy = required(actorRef, "诊断更新人不能为空");
    }

    public UUID id() { return id; }
    public UUID caseId() { return caseId; }
    public DiagnosisContextType contextType() { return contextType; }
    public UUID contextId() { return contextId; }
    public UUID templateVersionId() { return templateVersionId; }
    public String structuredData() { return structuredData; }
    public String microscopicDescription() { return microscopicDescription; }
    public String diagnosisText() { return diagnosisText; }
    public String comment() { return comment; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Instant updatedAt() { return updatedAt; }
    public String updatedBy() { return updatedBy; }

    private static String normalizeStructuredData(String value) {
        if (value == null || value.isBlank()) {
            return "{}";
        }
        String trimmed = value.trim();
        if (!(trimmed.startsWith("{") && trimmed.endsWith("}"))) {
            throw new IllegalArgumentException("结构化诊断数据必须是JSON对象");
        }
        return trimmed;
    }

    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(message); }
        return value.trim();
    }
}
