package com.hanjisang.pis.v2.technical.domain;

import java.util.Objects;
import java.util.UUID;

public record TechnicalOrderItem(UUID id, UUID orderId, TechnicalProject project, int quantity,
        String parameters, String note, long version) {
    public TechnicalOrderItem {
        Objects.requireNonNull(id, "技术医嘱项目ID不能为空");
        Objects.requireNonNull(orderId, "技术医嘱ID不能为空");
        Objects.requireNonNull(project, "技术项目不能为空");
        if (quantity < 1) throw new IllegalArgumentException("技术医嘱项目数量必须为正数");
        if (version < 0) throw new IllegalArgumentException("技术医嘱项目版本不能为负数");
    }
}
