package com.hanjisang.pis.v2.technical.domain;

import java.util.Objects;
import java.util.UUID;

public record TechnicalOrderTarget(UUID id, UUID itemId, UUID caseId, TechnicalTargetType targetType,
        UUID targetId, String displayCode) {
    public TechnicalOrderTarget {
        Objects.requireNonNull(id, "技术目标ID不能为空");
        Objects.requireNonNull(itemId, "技术医嘱项目ID不能为空");
        Objects.requireNonNull(caseId, "病例ID不能为空");
        Objects.requireNonNull(targetType, "技术目标类型不能为空");
        Objects.requireNonNull(targetId, "技术目标ID不能为空");
    }
}
