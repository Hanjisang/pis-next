package com.hanjisang.pis.v2.diagnosis.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class DiagnosisTemplateVersion {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";

    private final UUID id;
    private final UUID templateId;
    private final int versionNo;
    private final String schemaDefinition;
    private String status;
    private Instant publishedAt;
    private String publishedBy;
    private final Instant createdAt;
    private final String createdBy;
    private final long version;

    private DiagnosisTemplateVersion(UUID id, UUID templateId, int versionNo, String schemaDefinition, String status,
            Instant publishedAt, String publishedBy, Instant createdAt, String createdBy, long version) {
        this.id = Objects.requireNonNull(id, "模板版本ID不能为空");
        this.templateId = Objects.requireNonNull(templateId, "模板版本必须关联模板");
        if (versionNo <= 0) { throw new IllegalArgumentException("模板版本号必须大于0"); }
        this.versionNo = versionNo;
        this.schemaDefinition = required(schemaDefinition, "模板Schema不能为空");
        if (!DRAFT.equals(status) && !PUBLISHED.equals(status)) {
            throw new IllegalArgumentException("模板版本状态不合法");
        }
        this.status = status;
        this.publishedAt = publishedAt;
        this.publishedBy = publishedBy;
        this.createdAt = Objects.requireNonNull(createdAt, "模板版本创建时间不能为空");
        this.createdBy = required(createdBy, "模板版本创建人不能为空");
        this.version = version;
    }

    public static DiagnosisTemplateVersion draft(UUID id, UUID templateId, int versionNo, String schemaDefinition,
            Instant now, String actorRef) {
        return new DiagnosisTemplateVersion(id, templateId, versionNo, schemaDefinition, DRAFT, null, null, now,
                actorRef, 0);
    }

    public static DiagnosisTemplateVersion persisted(UUID id, UUID templateId, int versionNo, String schemaDefinition,
            String status, Instant publishedAt, String publishedBy, Instant createdAt, String createdBy, long version) {
        return new DiagnosisTemplateVersion(id, templateId, versionNo, schemaDefinition, status, publishedAt,
                publishedBy, createdAt, createdBy, version);
    }

    public void publish(Instant now, String actorRef) {
        if (!DRAFT.equals(status)) { throw new IllegalStateException("已发布模板版本不可重复发布或修改"); }
        status = PUBLISHED;
        publishedAt = Objects.requireNonNull(now, "模板发布时间不能为空");
        publishedBy = required(actorRef, "模板发布人不能为空");
    }

    public UUID id() { return id; }
    public UUID templateId() { return templateId; }
    public int versionNo() { return versionNo; }
    public String schemaDefinition() { return schemaDefinition; }
    public String status() { return status; }
    public Instant publishedAt() { return publishedAt; }
    public String publishedBy() { return publishedBy; }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public long version() { return version; }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(message); }
        return value.trim();
    }
}
