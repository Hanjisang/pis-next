package com.hanjisang.pis.v2.material.domain;

import java.util.Objects;
import java.util.UUID;

public record SlideRule(UUID id, UUID businessTypeId, String ruleCode, String sourceContextType,
        String triggerCode, String slideType, String stainCode, int copies, boolean active) {

    public SlideRule {
        Objects.requireNonNull(id, "切片规则ID不能为空");
        Objects.requireNonNull(businessTypeId, "切片规则业务类型不能为空");
        required(ruleCode, "切片规则编码不能为空");
        required(sourceContextType, "切片规则来源上下文不能为空");
        required(triggerCode, "切片规则触发条件不能为空");
        required(slideType, "切片类型不能为空");
        required(stainCode, "染色编码不能为空");
        if (copies < 1) {
            throw new IllegalArgumentException("切片规则份数必须为正数");
        }
    }

    public String slideCode(String blockCode, int occurrenceNo) {
        String suffix = copies == 1 ? stainCode : stainCode + "-" + occurrenceNo;
        return blockCode + "-" + suffix;
    }

    private static void required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
