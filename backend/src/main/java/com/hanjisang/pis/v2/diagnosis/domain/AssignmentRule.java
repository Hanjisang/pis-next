package com.hanjisang.pis.v2.diagnosis.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AssignmentRule(UUID id, String organizationReference, String campus, String businessTypeCode,
        String department, String site, String diagnosisGroup, String doctorId, int priority, boolean enabled,
        long version, Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {

    public AssignmentRule {
        Objects.requireNonNull(id, "分派规则ID不能为空");
        required(organizationReference, "组织范围不能为空");
        required(campus, "院区不能为空");
        required(businessTypeCode, "业务类型不能为空");
        required(department, "科室不能为空");
        required(site, "部位不能为空");
        required(diagnosisGroup, "诊断组不能为空");
        if (priority < 0) { throw new IllegalArgumentException("分派优先级不能为负数"); }
        if (version < 0) { throw new IllegalArgumentException("分派规则版本不能为负数"); }
        Objects.requireNonNull(createdAt, "分派规则创建时间不能为空");
        required(createdBy, "分派规则创建人不能为空");
        Objects.requireNonNull(updatedAt, "分派规则更新时间不能为空");
        required(updatedBy, "分派规则更新人不能为空");
    }

    private static void required(String value, String message) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(message); }
    }
}
