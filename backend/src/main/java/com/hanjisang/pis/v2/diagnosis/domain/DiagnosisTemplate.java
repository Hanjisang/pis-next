package com.hanjisang.pis.v2.diagnosis.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class DiagnosisTemplate {

    private final UUID id;
    private final String code;
    private String name;
    private final UUID businessTypeId;
    private final String scope;
    private boolean enabled;
    private long version;
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;
    private String updatedBy;

    private DiagnosisTemplate(UUID id, String code, String name, UUID businessTypeId, String scope, boolean enabled,
            long version, Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {
        this.id = Objects.requireNonNull(id, "诊断模板ID不能为空");
        this.code = required(code, "诊断模板编码不能为空");
        this.name = required(name, "诊断模板名称不能为空");
        this.businessTypeId = Objects.requireNonNull(businessTypeId, "诊断模板必须关联业务类型");
        this.scope = required(scope, "诊断模板范围不能为空");
        this.enabled = enabled;
        if (version < 0) { throw new IllegalArgumentException("模板版本不能为负数"); }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "模板创建时间不能为空");
        this.createdBy = required(createdBy, "模板创建人不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "模板更新时间不能为空");
        this.updatedBy = required(updatedBy, "模板更新人不能为空");
    }

    public static DiagnosisTemplate create(UUID id, String code, String name, UUID businessTypeId, String scope,
            Instant now, String actorRef) {
        return new DiagnosisTemplate(id, code, name, businessTypeId, scope, true, 0, now, actorRef, now, actorRef);
    }

    public static DiagnosisTemplate persisted(UUID id, String code, String name, UUID businessTypeId, String scope,
            boolean enabled, long version, Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {
        return new DiagnosisTemplate(id, code, name, businessTypeId, scope, enabled, version, createdAt, createdBy,
                updatedAt, updatedBy);
    }

    public void update(String name, boolean enabled, long expectedVersion, Instant now, String actorRef) {
        if (version != expectedVersion) { throw new IllegalStateException("诊断模板版本冲突"); }
        this.name = required(name, "诊断模板名称不能为空");
        this.enabled = enabled;
        this.version++;
        this.updatedAt = Objects.requireNonNull(now, "模板更新时间不能为空");
        this.updatedBy = required(actorRef, "模板更新人不能为空");
    }

    public UUID id() { return id; }
    public String code() { return code; }
    public String name() { return name; }
    public UUID businessTypeId() { return businessTypeId; }
    public String scope() { return scope; }
    public boolean enabled() { return enabled; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Instant updatedAt() { return updatedAt; }
    public String updatedBy() { return updatedBy; }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(message); }
        return value.trim();
    }
}
