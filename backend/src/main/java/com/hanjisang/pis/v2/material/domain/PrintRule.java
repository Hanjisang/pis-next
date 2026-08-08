package com.hanjisang.pis.v2.material.domain;

import java.util.Objects;
import java.util.UUID;

public record PrintRule(UUID id, UUID businessTypeId, String entityKindCode, String triggerCode,
        String printerProfileCode, boolean active) {

    public PrintRule {
        Objects.requireNonNull(id, "打印规则ID不能为空");
        required(entityKindCode, "打印实体类型不能为空");
        required(triggerCode, "打印触发条件不能为空");
        required(printerProfileCode, "打印机配置不能为空");
    }

    private static void required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
