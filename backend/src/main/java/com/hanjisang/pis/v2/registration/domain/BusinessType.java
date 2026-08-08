package com.hanjisang.pis.v2.registration.domain;

import java.util.Objects;
import java.util.UUID;

public final class BusinessType {

    private final UUID id;
    private final String code;
    private final String displayName;
    private final String modalityCode;
    private final boolean active;
    private final int configurationVersion;

    private BusinessType(UUID id, String code, String displayName, String modalityCode, boolean active,
            int configurationVersion) {
        this.id = Objects.requireNonNull(id, "业务类型内部ID不能为空");
        this.code = required(code, "业务类型编码不能为空");
        this.displayName = required(displayName, "业务类型名称不能为空");
        this.modalityCode = required(modalityCode, "模态编码不能为空");
        if (configurationVersion < 1) {
            throw new IllegalArgumentException("业务类型配置版本必须为正数");
        }
        this.active = active;
        this.configurationVersion = configurationVersion;
    }

    public static BusinessType define(UUID id, String code, String displayName, String modalityCode, boolean active,
            int configurationVersion) {
        return new BusinessType(id, code, displayName, modalityCode, active, configurationVersion);
    }

    public boolean acceptsNewCase() {
        return active;
    }

    public UUID id() { return id; }
    public String code() { return code; }
    public String displayName() { return displayName; }
    public String modalityCode() { return modalityCode; }
    public boolean active() { return active; }
    public int configurationVersion() { return configurationVersion; }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
